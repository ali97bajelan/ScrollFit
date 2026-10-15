package com.example.scrollfit.monitoring

import android.accessibilityservice.AccessibilityService
import android.app.AppOpsManager
import android.app.KeyguardManager
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.*
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.example.scrollfit.R
import com.example.scrollfit.ScrollFitApplication
import com.example.scrollfit.challenge.ChallengeActivity
import com.example.scrollfit.core.exercise.Exercise
import com.example.scrollfit.core.budget.*
import com.example.scrollfit.core.data.AppPreferences
import com.example.scrollfit.core.usage.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import java.time.ZoneId

/** Main-source package-only monitor. No content access, polling of history or background launches. */
class MonitoringService : AccessibilityService() {
    private sealed interface Command
    private data class Observation(val signal: MonitorSignal, val ticket: Long, val generation: Long) : Command
    private data class SessionCommand(val access: SessionAccess, val action: SessionAction) : Command
    private var sessionActionQueued = false
    private lateinit var budgetEngine: BudgetEngine
    private lateinit var scope: CoroutineScope
    private lateinit var observations: Channel<Command>
    private lateinit var controller: MonitoringController
    private lateinit var overlay: BudgetOverlay
    private lateinit var presentation: MonitorPresentation
    private val readiness = MonitoringReadiness()
    private val persistence get() = (application as ScrollFitApplication).persistence
    private var connected = false
    private var ready = false
    private var foreground: String? = null
    private var foregroundKnown = false
    private var safePackages = emptySet<String>()
    private var overflow = false
    private var tick: Job? = null
    private var reconciliation: Job? = null
    private var preferenceJob: Job? = null
    private var leaseHeld = false
    private var lastFailure = MonitorFailure.NONE
    private var generation = 0L

    private val usageListener = AppOpsManager.OnOpChangedListener { operation, _ ->
        if (operation == AppOpsManager.OPSTR_GET_USAGE_STATS && connected) scope.launch {
            // Flush to detection time, then require new metadata for recovery.
            enqueue(); foreground = null; foregroundKnown = false
            requestReconciliation()
        }
    }
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!connected) return
            if (intent.action == Intent.ACTION_SCREEN_OFF || intent.action == Intent.ACTION_SCREEN_ON) {
                foreground = null; foregroundKnown = true; enqueue()
            } else {
                // Time/date/unlock changes reconcile rather than charge guessed downtime.
                enqueue(); foreground = null; foregroundKnown = false
                requestReconciliation()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (connected) cleanup()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        observations = Channel(64)
        connected = true
        ready = false; readiness.failed(); foreground = null; foregroundKnown = false
        safePackages = emptySet(); overflow = false; lastFailure = MonitorFailure.NONE
        overlay = BudgetOverlay(this, { navigate(GLOBAL_ACTION_HOME) },
            { navigate(GLOBAL_ACTION_QUICK_SETTINGS) }, { disableMonitoring() }, { fail(MonitorFailure.OVERLAY) },
            ::openChallenge, ::resolveSession)
        presentation = MonitorPresentation(overlay::show, overlay::dismiss)
        MonitorRuntime.connected(this)
        MonitorRuntime.publish(MonitorStatus(failure = MonitorFailure.STARTING))
        requireNotNull(getSystemService(AppOpsManager::class.java)).startWatchingMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS, packageName, usageListener)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT); addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else { @Suppress("DEPRECATION") registerReceiver(screenReceiver, filter) }

        scope.launch {
            val boot = try { MonitorAccess.bootIdentity(this@MonitoringService) }
            catch (_: Exception) { fail(MonitorFailure.BOOT_ID); return@launch }
            try {
                budgetEngine = persistence.budgetEngine(boot)
                controller = MonitoringController(budgetEngine, TargetApps.protectedPackages(this@MonitoringService))
                safePackages = withContext(Dispatchers.IO) { TargetApps.list(this@MonitoringService).map { it.packageName }.toSet() }
                ready = true
                startPreferenceCollection()
                requestReconciliation()
                for (command in observations) {
                    try {
                        if (!readiness.available) { sessionActionQueued = false; continue }
                        if (overflow) { overflow = false; fail(MonitorFailure.QUEUE); continue }
                        if (command is SessionCommand) {
                            try { applySessionAction(command) } finally { sessionActionQueued = false }
                            continue
                        }
                        val observation = command as Observation
                        if (observation.generation != generation) continue
                        if (SystemClock.elapsedRealtime() - observation.signal.sample.elapsedMs > MonitoringController.MAX_OBSERVED_GAP_MS) {
                            fail(MonitorFailure.QUEUE); continue
                        }
                        if (observation.signal.enabled && !MonitorRuntime.ownership.acquire(this@MonitoringService)) {
                            fail(MonitorFailure.OWNER_BUSY); continue
                        }
                        if (observation.signal.enabled) leaseHeld = true
                        val startedGeneration = generation
                        val result = controller.observe(observation.signal)
                        if (generation != startedGeneration) { controller.abandon(); continue }
                        if (overflow) { overflow = false; fail(MonitorFailure.QUEUE); continue }
                        render(result, observation.ticket)
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { fail(MonitorFailure.STORAGE) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { fail(MonitorFailure.STORAGE) }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!connected || event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val eventPackage = event.packageName?.toString() // Never event text, source or screen nodes.
        if (eventPackage == packageName && overlay.visible && !MonitorRuntime.ownUiVisible) return
        reconciliation?.cancel()
        if (!acceptForeground(eventPackage, eventPackage != null)) return
        enqueue()
    }

    fun ownActivityResumed() {
        if (!connected) return
        foreground = packageName; foregroundKnown = true; reconciliation?.cancel()
        presentation.pause(); startPreferenceCollection(); enqueue()
    }

    /** Only called by a user tap while our accessibility overlay is visible. */
    private fun openChallenge(target: String, exercise: Exercise?) {
        if (!connected || !overlay.visible) return
        try {
            val creator = ActivityOptions.makeBasic()
            if (Build.VERSION.SDK_INT >= 35) creator.setPendingIntentCreatorBackgroundActivityStartMode(
                if (Build.VERSION.SDK_INT >= 36) ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE
                else ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            val pending = PendingIntent.getActivity(this, 0, ChallengeActivity.intent(this, target, exercise),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_ONE_SHOT,
                creator.toBundle())
            val sender = ActivityOptions.makeBasic()
            if (Build.VERSION.SDK_INT >= 34) sender.setPendingIntentBackgroundActivityStartMode(
                if (Build.VERSION.SDK_INT >= 36) ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE
                else ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            pending.send(this, 0, null, null, null, null, sender.toBundle())
            // Keep the blocker until the Activity actually resumes: Android may silently reject BAL.
        } catch (_: Exception) {
            Toast.makeText(this, R.string.challenge_launch_error, Toast.LENGTH_LONG).show()
        }
    }

    /** User taps and observations share one queue; no reset races a suspended checkpoint. */
    private fun resolveSession(access: SessionAccess, action: SessionAction) {
        if (!connected || sessionActionQueued || !overlay.isCurrent(access)) return
        sessionActionQueued = true
        tick?.cancel()
        if (!observations.trySend(SessionCommand(access, action)).isSuccess) {
            sessionActionQueued = false
            fail(MonitorFailure.QUEUE)
        }
    }

    private suspend fun applySessionAction(command: SessionCommand) {
        val displayed = command.access
        val startedGeneration = generation
        fun eligible() = connected && readiness.monitoringEnabled && leaseHeld &&
            generation == startedGeneration && !overflow && MonitorAccess.usage(this) &&
            !MonitorRuntime.ownUiVisible &&
            requireNotNull(getSystemService(PowerManager::class.java)).isInteractive &&
            !requireNotNull(getSystemService(KeyguardManager::class.java)).isKeyguardLocked
        if (!eligible() || !overlay.isCurrent(displayed) || foreground != displayed.session.packageName) {
            enqueue(); return
        }
        if (command.action == SessionAction.FINISH) {
            // Keep the prompt/session if Home cannot be requested; never reveal the target on failure.
            if (!performGlobalAction(GLOBAL_ACTION_HOME)) {
                Toast.makeText(this, R.string.monitor_navigation_failed, Toast.LENGTH_LONG).show()
                enqueue(); return
            }
            foreground = null; foregroundKnown = false
        }
        val result = budgetEngine.resolveSession(displayed, command.action) {
            eligible() && (command.action == SessionAction.FINISH ||
                (overlay.isCurrent(displayed) && foreground == displayed.session.packageName))
        }
        // Pause and reconcile so prompt time and queued pre-tap samples cannot be charged.
        generation++
        controller.abandon()
        if (result == SessionActionResult.APPLIED) presentation.pause()
        enqueue()
        requestReconciliation()
    }

    /** Stop the live monitor immediately while the durable disable write completes. */
    fun pauseForDisable() { readiness.failed(); fail(MonitorFailure.STARTING) }

    private fun startPreferenceCollection(restart: Boolean = false) {
        if (!connected || !ready) return
        if (restart) preferenceJob?.cancel()
        else if (preferenceJob?.isActive == true) return
        preferenceJob = scope.launch {
            try {
                persistence.preferences.values.collect { prefs: AppPreferences ->
                    val resumedAfterGap = !readiness.available && prefs.monitoringEnabled
                    readiness.updated(prefs.monitoringEnabled)
                    if (resumedAfterGap) MonitorRuntime.publish(MonitorRuntime.status.value.copy(hasUnmonitoredGap = true))
                    enqueue()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { readiness.failed(); fail(MonitorFailure.STORAGE) }
        }
    }

    fun requestReconciliation() {
        if (!connected || !ready) return
        reconciliation?.cancel()
        reconciliation = scope.launch {
            delay(200) // Let Android publish the activity transition; event callbacks cancel this lookup.
            val current = withContext(Dispatchers.IO) { ForegroundReconciler(this@MonitoringService).current() }
            if (!acceptForeground(if (MonitorRuntime.ownUiVisible) packageName else current.packageName,
                    MonitorRuntime.ownUiVisible || current.known)) return@launch
            enqueue()
        }
    }

    private fun acceptForeground(value: String?, known: Boolean): Boolean {
        // Release the previous blocker before package/role metadata calls can suspend navigation.
        if (value != foreground || !known) presentation.pause()
        if (value != null && value in safePackages) {
            try {
                // Recheck dynamic home/dialer/keyboard roles on transitions, never on checkpoint ticks.
                if (value in TargetApps.protectedPackages(this)) safePackages = safePackages - value
            } catch (_: Exception) { fail(MonitorFailure.METADATA); return false }
        }
        foreground = value; foregroundKnown = known
        return true
    }

    fun reloadConfiguration() {
        if (!connected || !ready) return
        startPreferenceCollection(restart = !readiness.available)
        scope.launch {
            try {
                safePackages = withContext(Dispatchers.IO) { TargetApps.list(this@MonitoringService).map { it.packageName }.toSet() }
                enqueue(); requestReconciliation()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { fail(MonitorFailure.STORAGE) }
        }
    }

    private fun enqueue() {
        if (!connected || !ready) return
        tick?.cancel()
        val interactive = requireNotNull(getSystemService(PowerManager::class.java)).isInteractive &&
            !requireNotNull(getSystemService(KeyguardManager::class.java)).isKeyguardLocked
        val sample = MonitorSample(SystemClock.elapsedRealtime(), System.currentTimeMillis(), ZoneId.systemDefault().id)
        val candidate = foreground?.takeIf { it in safePackages }
        val signal = readiness.guard(MonitorSignal(sample, candidate, false, MonitorAccess.usage(this), connected,
            interactive, MonitorRuntime.ownUiVisible, foregroundKnown))
        // Hide on safe/system/self/access-loss signals now, independently of a suspended budget read.
        val beforePresentation = generation
        val ticket = presentation.transition(candidate, pause = !signal.enabled || !signal.connected ||
            !signal.usageAccess || !signal.interactive || signal.ownUiVisible || !signal.foregroundKnown)
        if (generation != beforePresentation) return
        if (!readiness.available) return
        if (!observations.trySend(Observation(signal, ticket, generation)).isSuccess) {
            overflow = true
            presentation.pause()
            MonitorRuntime.publish(MonitorStatus(failure = MonitorFailure.QUEUE, hasUnmonitoredGap = true))
        }
    }

    private fun render(result: MonitorDecision, ticket: Long) {
        if (result.blockedBy != null && !leaseHeld) { presentation.pause(); return }
        if (!presentation.present(ticket, result)) return
        if (result.health == MonitoringHealth.DISABLED) releaseLease()
        if (result.health != MonitoringHealth.SERVICE_STOPPED) lastFailure = MonitorFailure.NONE
        MonitorRuntime.publish(MonitorStatus(result.health, result, lastFailure, hasUnmonitoredGap = result.unmonitoredMs > 0))
        if (result.shouldTick && connected && ready && lastFailure == MonitorFailure.NONE) {
            tick = scope.launch { delay(result.tickDelayMs); enqueue() }
        }
    }

    private fun navigate(action: Int) {
        if (!performGlobalAction(action)) Toast.makeText(this, R.string.monitor_navigation_failed, Toast.LENGTH_LONG).show()
    }

    private fun disableMonitoring() {
        pauseForDisable()
        scope.launch {
            try {
                persistence.preferences.update { it.copy(monitoringEnabled = false) }
                startPreferenceCollection(restart = true)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { fail(MonitorFailure.STORAGE) }
        }
    }

    private fun releaseLease() { MonitorRuntime.ownership.release(this); leaseHeld = false }
    private fun fail(failure: MonitorFailure) {
        if (!connected) return
        sessionActionQueued = false
        generation++ // Invalidate any decision suspended in storage when failure arrived.
        lastFailure = failure
        tick?.cancel(); reconciliation?.cancel()
        if (::controller.isInitialized) controller.abandon()
        while (observations.tryReceive().isSuccess) { /* discard uncertain queue */ }
        foreground = null; foregroundKnown = false
        // Retain exclusive ownership while an old window's removal cannot be confirmed.
        if (presentation.pause()) releaseLease()
        MonitorRuntime.publish(MonitorStatus(failure = failure, hasUnmonitoredGap = true))
    }

    override fun onInterrupt() { fail(MonitorFailure.INTERRUPTED) }
    override fun onUnbind(intent: Intent): Boolean { cleanup(); return super.onUnbind(intent) }
    override fun onDestroy() { cleanup(); super.onDestroy() }
    private fun cleanup() {
        if (!connected) return
        // Last asynchronous checkpoint may be cancelled by Android; do not block the main thread.
        connected = false; ready = false
        readiness.failed()
        tick?.cancel(); reconciliation?.cancel(); preferenceJob?.cancel()
        scope.cancel(); observations.close()
        requireNotNull(getSystemService(AppOpsManager::class.java)).stopWatchingMode(usageListener)
        runCatching { unregisterReceiver(screenReceiver) }
        overlay.dismiss(); releaseLease(); MonitorRuntime.disconnected(this)
    }
}
