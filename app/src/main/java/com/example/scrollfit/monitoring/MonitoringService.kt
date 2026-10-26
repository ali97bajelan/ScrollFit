package com.example.scrollfit.monitoring

import android.accessibilityservice.AccessibilityService
import android.app.AppOpsManager
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.*
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.example.scrollfit.R
import com.example.scrollfit.MainActivity
import com.example.scrollfit.challenge.ChallengeActivity
import com.example.scrollfit.core.exercise.Exercise
import com.example.scrollfit.core.budget.*
import com.example.scrollfit.core.data.AppPreferences
import com.example.scrollfit.core.usage.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import java.util.UUID

/** Main-source package-only monitor. Activities launch only after a tap on the visible blocker. */
class MonitoringService : AccessibilityService() {
    private sealed interface Command
    private data class Observation(val signal: MonitorSignal, val ticket: Long, val generation: Long) : Command
    private data class SessionCommand(val access: SessionAccess, val action: SessionAction) : Command
    private var sessionActionQueued = false
    private lateinit var budgetEngine: BudgetEngine
    private lateinit var scope: CoroutineScope
    private lateinit var observations: Channel<Command>
    private lateinit var controller: MonitoringController
    private lateinit var overlay: MonitorOverlay
    private lateinit var presentation: MonitorPresentation
    private lateinit var dependencies: MonitorDependencies
    private lateinit var initialization: MonitorLifecycle
    private val readiness = MonitoringReadiness()
    internal var dependenciesFactory: (MonitoringService) -> MonitorDependencies = { AndroidMonitorDependencies(it) }
    private var connected = false
    private val ready get() = ::initialization.isInitialized && initialization.state == MonitorInitState.READY
    private var foreground: String? = null
    private var foregroundKnown = false
    private var safePackages = emptySet<String>()
    private var overflow = false
    private var tick: Job? = null
    private var coverageCheckpoint: Job? = null
    private var reconciliation: Job? = null
    private var preferenceJob: Job? = null
    private var commandConsumer: Job? = null
    private var leaseHeld = false
    private var lastFailure = MonitorFailure.NONE
    private var generation = 0L
    private var coverageRecorder: CoverageRecorder? = null
    private var lastProcessedElapsedMs: Long? = null

    private val usageListener = AppOpsManager.OnOpChangedListener { operation, _ ->
        if (operation == AppOpsManager.OPSTR_GET_USAGE_STATS && connected) {
            coverageRecorder?.invalidate()
            scope.launch {
                // Flush to detection time, then require new metadata for recovery.
                enqueue(); foreground = null; foregroundKnown = false
                requestReconciliation()
            }
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
        generation++
        observations = Channel(64)
        connected = true
        dependencies = dependenciesFactory(this)
        readiness.failed(); foreground = null; foregroundKnown = false
        safePackages = emptySet(); overflow = false; lastFailure = MonitorFailure.NONE
        overlay = dependencies.createOverlay(this, MonitorOverlayCallbacks(
            home = { navigate(GLOBAL_ACTION_HOME) },
            openScrollFit = ::openScrollFit,
            stop = ::disableMonitoring,
            failed = { fail(MonitorFailure.OVERLAY) },
            earn = ::openChallenge,
            sessionAction = ::resolveSession,
        ))
        presentation = MonitorPresentation(overlay::show, overlay::dismiss)
        MonitorRuntime.connected(this)
        MonitorRuntime.publish(MonitorStatus(failure = MonitorFailure.STARTING))
        initialization = MonitorLifecycle(scope, ::initializationStateChanged,
            ::initializeMonitoring, ::initializationFailed)
        requireNotNull(getSystemService(AppOpsManager::class.java)).startWatchingMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS, packageName, usageListener)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT); addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else { @Suppress("DEPRECATION") registerReceiver(screenReceiver, filter) }
        startCommandConsumer()
        initialization.startOrRetry()
    }

    private suspend fun initializeMonitoring(attemptGeneration: Long) {
        val bootIdentity = try { dependencies.bootIdentity() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { throw MonitorInitializationException(MonitorFailure.BOOT_ID, failure) }
        ensureInitializationCurrent(attemptGeneration)

        val engine = try { dependencies.budgetEngine(bootIdentity) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { throw MonitorInitializationException(MonitorFailure.STORAGE, failure) }
        ensureInitializationCurrent(attemptGeneration)

        val protected = try { dependencies.protectedPackages() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { throw MonitorInitializationException(MonitorFailure.METADATA, failure) }
        ensureInitializationCurrent(attemptGeneration)
        val newController = MonitoringController(engine, protected)

        val recorder = try { dependencies.coverageRecorder(bootIdentity, UUID.randomUUID().toString()) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { throw MonitorInitializationException(MonitorFailure.STORAGE, failure) }
        ensureInitializationCurrent(attemptGeneration)

        val newSafePackages = try { dependencies.safePackages() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { throw MonitorInitializationException(MonitorFailure.METADATA, failure) }
        // Publish the complete set together only after every boot, persistence and metadata step succeeds.
        if (!initialization.commitIfCurrent(attemptGeneration) {
                budgetEngine = engine
                controller = newController
                coverageRecorder = recorder
                safePackages = newSafePackages
            }) throw CancellationException("Monitoring initialization was stopped")
    }

    private fun ensureInitializationCurrent(attemptGeneration: Long) {
        if (!connected || !initialization.isCurrent(attemptGeneration)) {
            throw CancellationException("Monitoring initialization was stopped")
        }
    }

    private fun initializationStateChanged(state: MonitorInitState) {
        MonitorRuntime.publishInitializationState(state)
        when (state) {
            MonitorInitState.INITIALIZING -> {
                lastFailure = MonitorFailure.STARTING
                MonitorRuntime.publish(MonitorStatus(failure = MonitorFailure.STARTING, hasUnmonitoredGap = true))
            }
            MonitorInitState.READY -> {
                lastFailure = MonitorFailure.NONE
                if (connected) {
                    startPreferenceCollection()
                    requestReconciliation()
                }
            }
            MonitorInitState.STOPPED, MonitorInitState.FAILED -> Unit
        }
    }

    private fun initializationFailed(attemptGeneration: Long, failure: Exception) {
        if (!connected || !initialization.isLatest(attemptGeneration) ||
            initialization.state != MonitorInitState.FAILED) return
        generation++
        readiness.failed()
        coverageRecorder?.invalidate()
        if (::controller.isInitialized) controller.abandon()
        while (observations.tryReceive().isSuccess) { /* discard partial-start observations */ }
        sessionActionQueued = false
        foreground = null; foregroundKnown = false
        lastFailure = (failure as? MonitorInitializationException)?.failure ?: MonitorFailure.STORAGE
        presentation.pause()
        MonitorRuntime.publish(MonitorStatus(failure = lastFailure, hasUnmonitoredGap = true))
    }

    private fun startCommandConsumer() {
        if (commandConsumer?.isActive == true) return
        commandConsumer = scope.launch {
            for (command in observations) {
                try { processCommand(command) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { fail(MonitorFailure.STORAGE) }
            }
        }
    }

    private suspend fun processCommand(command: Command) {
        if (!ready || !readiness.available) { sessionActionQueued = false; return }
        if (overflow) { overflow = false; fail(MonitorFailure.QUEUE); return }
        if (command is SessionCommand) {
            try { applySessionAction(command) } finally { sessionActionQueued = false }
            return
        }
        val observation = command as Observation
        if (observation.generation != generation) return
        if (dependencies.elapsedRealtimeMs() - observation.signal.sample.elapsedMs > MonitoringController.MAX_OBSERVED_GAP_MS) {
            fail(MonitorFailure.QUEUE); return
        }
        if (observation.signal.enabled && !MonitorRuntime.ownership.acquire(this@MonitoringService)) {
            fail(MonitorFailure.OWNER_BUSY); return
        }
        if (observation.signal.enabled) leaseHeld = true
        val startedGeneration = generation
        val result = controller.observe(observation.signal)
        if (generation != startedGeneration) { controller.abandon(); return }
        if (overflow) { overflow = false; fail(MonitorFailure.QUEUE); return }
        val selectedScope = dependencies.selectedPackages()
        if (generation != startedGeneration) { coverageRecorder?.invalidate(); return }
        coverageRecorder?.observe(observation.signal, result, selectedScope, leaseHeld) {
            connected && readiness.available && generation == startedGeneration && !overflow && leaseHeld &&
                dependencies.usageAccess()
        }
        lastProcessedElapsedMs = observation.signal.sample.elapsedMs
        render(result, observation.ticket)
        scheduleCoverageCheckpoint(result)
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

    /** Only called by a user tap on the live blocker. */
    private fun openScrollFit() {
        if (!connected || !overlay.visible) return
        try {
            val intent = Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
            val creator = ActivityOptions.makeBasic()
            if (Build.VERSION.SDK_INT >= 35) creator.setPendingIntentCreatorBackgroundActivityStartMode(
                if (Build.VERSION.SDK_INT >= 36) ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE
                else ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
            )
            val pending = PendingIntent.getActivity(this, 1, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_ONE_SHOT,
                creator.toBundle())
            val sender = ActivityOptions.makeBasic()
            if (Build.VERSION.SDK_INT >= 34) sender.setPendingIntentBackgroundActivityStartMode(
                if (Build.VERSION.SDK_INT >= 36) ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE
                else ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
            )
            pending.send(this, 0, null, null, null, null, sender.toBundle())
        } catch (_: Exception) {
            Toast.makeText(this, R.string.monitor_navigation_failed, Toast.LENGTH_LONG).show()
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
            generation == startedGeneration && !overflow && dependencies.usageAccess() &&
            !MonitorRuntime.ownUiVisible &&
            dependencies.isInteractive() && !dependencies.isKeyguardLocked()
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
    fun pauseForDisable() {
        if (::initialization.isInitialized && initialization.state == MonitorInitState.INITIALIZING) {
            initialization.stop()
        }
        readiness.failed()
        fail(MonitorFailure.STARTING)
    }

    private fun startPreferenceCollection(restart: Boolean = false) {
        if (!connected || !ready) return
        if (restart) preferenceJob?.cancel()
        else if (preferenceJob?.isActive == true) return
        preferenceJob = scope.launch {
            try {
                dependencies.preferences.collect { prefs: AppPreferences ->
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
            val current = dependencies.currentForeground()
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
                if (value in dependencies.protectedPackages()) safePackages = safePackages - value
            } catch (_: Exception) { fail(MonitorFailure.METADATA); return false }
        }
        foreground = value; foregroundKnown = known
        return true
    }

    fun reloadConfiguration() {
        if (!connected) return
        if (!ready) { initialization.startOrRetry(); return }
        refreshReadyConfiguration()
    }

    /** Retry uses initialization only until READY; a ready service refreshes its live configuration. */
    fun retryInitialization() {
        if (!connected) return
        if (ready) refreshReadyConfiguration() else initialization.startOrRetry()
    }

    private fun refreshReadyConfiguration() {
        generation++
        coverageRecorder?.invalidate()
        controller.abandon()
        coverageCheckpoint?.cancel()
        startPreferenceCollection(restart = !readiness.available)
        scope.launch {
            try {
                safePackages = dependencies.safePackages()
                enqueue(); requestReconciliation()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { fail(MonitorFailure.METADATA) }
        }
    }

    private fun enqueue() {
        if (!connected || !ready) return
        tick?.cancel()
        coverageCheckpoint?.cancel()
        val interactive = dependencies.isInteractive() && !dependencies.isKeyguardLocked()
        val sample = MonitorSample(dependencies.elapsedRealtimeMs(), dependencies.wallTimeMs(), dependencies.zoneId())
        val candidate = foreground?.takeIf { it in safePackages }
        val signal = readiness.guard(MonitorSignal(sample, candidate, false, dependencies.usageAccess(), connected,
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

    private fun scheduleCoverageCheckpoint(result: MonitorDecision) {
        coverageCheckpoint?.cancel()
        if (!connected || !ready || !readiness.available || result.health != MonitoringHealth.HEALTHY || result.shouldTick) return
        val delayMs = CoverageCheckpoint.nextDelayMs(dependencies.elapsedRealtimeMs(), lastProcessedElapsedMs, true) ?: return
        coverageCheckpoint = scope.launch {
            delay(delayMs)
            if (connected && ready && readiness.available) requestReconciliation()
        }
    }

    fun historyCleared() {
        if (!connected || !ready) return
        coverageRecorder?.invalidate()
        enqueue()
        requestReconciliation()
    }

    private fun navigate(action: Int) {
        if (!performGlobalAction(action)) Toast.makeText(this, R.string.monitor_navigation_failed, Toast.LENGTH_LONG).show()
    }

    private fun disableMonitoring() {
        pauseForDisable()
        scope.launch {
            try {
                dependencies.setMonitoringEnabled(false)
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
        coverageRecorder?.invalidate()
        lastFailure = failure
        tick?.cancel(); coverageCheckpoint?.cancel(); reconciliation?.cancel()
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
        initialization.stop()
        connected = false
        readiness.failed()
        tick?.cancel(); coverageCheckpoint?.cancel(); reconciliation?.cancel(); preferenceJob?.cancel()
        commandConsumer?.cancel(); commandConsumer = null
        coverageRecorder?.invalidate(); coverageRecorder = null
        scope.cancel(); observations.close()
        requireNotNull(getSystemService(AppOpsManager::class.java)).stopWatchingMode(usageListener)
        runCatching { unregisterReceiver(screenReceiver) }
        if (overlay.dismiss()) releaseLease()
        MonitorRuntime.disconnected(this)
    }
}
