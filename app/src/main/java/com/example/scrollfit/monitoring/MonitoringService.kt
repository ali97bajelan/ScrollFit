package com.example.scrollfit.monitoring

import android.accessibilityservice.AccessibilityService
import android.app.AppOpsManager
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.*
import android.os.Build
import android.os.SystemClock
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
import kotlinx.coroutines.flow.collect
import java.util.UUID

/** Main-source package-only monitor. Activities launch only after a tap on the visible blocker. */
class MonitoringService : AccessibilityService() {
    private sealed interface Command
    private data class Observation(
        val signal: MonitorSignal,
        val ticket: Long,
        val generation: Long,
        val safetyRevision: Long,
    ) : Command
    private data class SessionCommand(val access: SessionAccess, val action: SessionAction) : Command
    private var sessionActionQueued = false
    private lateinit var budgetEngine: BudgetEngine
    private lateinit var scope: CoroutineScope
    private lateinit var observations: MonitorCommandQueue<Command>
    private lateinit var controller: MonitoringController
    private lateinit var overlay: MonitorOverlay
    private lateinit var presentation: MonitorPresentation
    private lateinit var dependencies: MonitorDependencies
    private lateinit var initialization: MonitorLifecycle
    private lateinit var safetySnapshotRefresher: SafetySnapshotRefresher
    private var registrations: MonitorRegistrations? = null
    private val safetySnapshotState = SafetySnapshotState()
    private val readiness = MonitoringReadiness()
    internal var dependenciesFactory: (MonitoringService) -> MonitorDependencies = { AndroidMonitorDependencies(it) }
    private var connected = false
    private val ready get() = ::initialization.isInitialized && initialization.state == MonitorInitState.READY
    private var foreground: String? = null
    private var foregroundKnown = false
    private var overflow = false
    private var tick: Job? = null
    private var coverageCheckpoint: Job? = null
    private var reconciliation: Job? = null
    private var preferenceJob: Job? = null
    private var commandConsumer: Job? = null
    private var leaseHeld = false
    private var lastFailure = MonitorFailure.NONE
    private var lastFailureDetail: MonitorFailureDetail? = null
    private var generation = 0L
    private var coverageRecorder: CoverageRecorder? = null
    private var lastProcessedElapsedMs: Long? = null
    private var safetyRefreshGeneration = 0L
    private var safetyRefreshForegroundHint: String? = null
    private var safetyRefreshReconcileAfter = true
    private var safetyRefreshRetryAttempt: Int? = null
    private var controllerSafetyRevision = -1L
    private var controllerSafetyReady = false
    private var diagnosticAttempt: Int? = null
    private var failureInProgress = false
    private var nestedFailure: Pair<MonitorFailure, MonitorFailureDetail>? = null

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
    private val packageSafetyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (connected && ready) refreshSafetySnapshot(reconcileAfter = true)
        }
    }
    private val systemSafetyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (connected && ready) refreshSafetySnapshot(reconcileAfter = true)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (connected) cleanup()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        generation++
        observations = MonitorCommandQueue()
        connected = true
        dependencies = dependenciesFactory(this)
        readiness.failed(); foreground = null; foregroundKnown = false
        overflow = false; lastFailure = MonitorFailure.NONE
        lastFailureDetail = null; diagnosticAttempt = null
        overlay = dependencies.createOverlay(this, MonitorOverlayCallbacks(
            home = { navigate(GLOBAL_ACTION_HOME) },
            openScrollFit = ::openScrollFit,
            stop = ::disableMonitoring,
            failed = { fail(MonitorFailure.OVERLAY,
                MonitorFailureDetail(MonitorFailurePhase.OVERLAY, MonitorFailureCode.OVERLAY_FAILURE)) },
            earn = ::openChallenge,
            sessionAction = ::resolveSession,
        ))
        presentation = MonitorPresentation(overlay::show, overlay::dismiss)
        MonitorRuntime.connected(this)
        MonitorRuntime.publish(MonitorStatus(failure = MonitorFailure.STARTING))
        initialization = MonitorLifecycle(scope, ::initializationStateChanged,
            ::initializeMonitoring, ::initializationFailed)
        safetySnapshotRefresher = SafetySnapshotRefresher(
            scope = scope,
            state = safetySnapshotState,
            load = { revision -> monitorBoundary(MonitorFailure.METADATA, MonitorFailurePhase.METADATA,
                MonitorFailureCode.SAFE_PACKAGE_LOOKUP) { dependencies.safetySnapshot(revision) } },
            onInvalidated = ::safetySnapshotInvalidated,
            onPublished = ::safetySnapshotPublished,
            onFailure = ::safetySnapshotFailed,
        )
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT); addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        val packageFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED); addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        val systemFilter = IntentFilter().apply {
            addAction(Intent.ACTION_INPUT_METHOD_CHANGED)
            addAction(Intent.ACTION_CONFIGURATION_CHANGED)
        }
        registrations = MonitorRegistrations(
            registerUsageWatcher = {
                requireNotNull(getSystemService(AppOpsManager::class.java)).startWatchingMode(
                    AppOpsManager.OPSTR_GET_USAGE_STATS, packageName, usageListener)
            },
            unregisterUsageWatcher = {
                requireNotNull(getSystemService(AppOpsManager::class.java)).stopWatchingMode(usageListener)
            },
            registerScreenReceiver = {
                if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
                else { @Suppress("DEPRECATION") registerReceiver(screenReceiver, filter) }
                Unit
            },
            unregisterScreenReceiver = { unregisterReceiver(screenReceiver) },
            registerPackageReceiver = { registerReceiverCompat(packageSafetyReceiver, packageFilter) },
            unregisterPackageReceiver = { unregisterReceiver(packageSafetyReceiver) },
            registerSystemReceiver = { registerReceiverCompat(systemSafetyReceiver, systemFilter) },
            unregisterSystemReceiver = { unregisterReceiver(systemSafetyReceiver) },
            onFailure = { code, _ ->
                val detail = MonitorFailureDetail(MonitorFailurePhase.REGISTRATION, code)
                if (connected) fail(MonitorFailure.REGISTRATION, detail) else recordFailure(detail)
            },
        )
        if (!registerSystemListeners()) return
        startCommandConsumer()
        initialization.startOrRetry()
    }

    private fun registerSystemListeners(): Boolean {
        return try {
            registrations?.register() == true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            fail(MonitorFailure.REGISTRATION,
                MonitorFailureDetail(MonitorFailurePhase.REGISTRATION, MonitorFailureCode.UNKNOWN))
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun registerReceiverCompat(receiver: BroadcastReceiver, filter: IntentFilter) {
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(receiver, filter)
    }

    private suspend fun initializeMonitoring(attemptGeneration: Long) {
        val bootIdentity = monitorBoundarySync(MonitorFailure.BOOT_ID, MonitorFailurePhase.BOOT,
            MonitorFailureCode.BOOT_IDENTITY, dependencies::bootIdentity)
        ensureInitializationCurrent(attemptGeneration)

        monitorBoundary(MonitorFailure.STORAGE, MonitorFailurePhase.PREFERENCES,
            MonitorFailureCode.PREFERENCE_WRITE, dependencies::prepareReadiness)
        ensureInitializationCurrent(attemptGeneration)

        val engine = monitorBoundary(MonitorFailure.STORAGE, MonitorFailurePhase.BUDGET,
            MonitorFailureCode.BUDGET_ENGINE_INITIALIZATION) { dependencies.budgetEngine(bootIdentity) }
        ensureInitializationCurrent(attemptGeneration)

        val safetyRevision = safetySnapshotState.beginRefresh()
        val newSafetySnapshot = monitorBoundary(MonitorFailure.METADATA, MonitorFailurePhase.METADATA,
            MonitorFailureCode.SAFE_PACKAGE_LOOKUP) { dependencies.safetySnapshot(safetyRevision) }
        ensureInitializationCurrent(attemptGeneration)
        val newController = MonitoringController(engine, newSafetySnapshot.protectedPackages)

        val recorder = monitorBoundary(MonitorFailure.STORAGE, MonitorFailurePhase.COVERAGE,
            MonitorFailureCode.COVERAGE_INITIALIZATION) {
            dependencies.coverageRecorder(bootIdentity, UUID.randomUUID().toString())
        }
        ensureInitializationCurrent(attemptGeneration)

        // Publish the complete set together only after every boot, persistence and metadata step succeeds.
        if (!initialization.commitIfCurrent(attemptGeneration) {
                check(safetySnapshotState.publish(newSafetySnapshot))
                budgetEngine = engine
                controller = newController
                coverageRecorder = recorder
                controllerSafetyRevision = newSafetySnapshot.revision
                controllerSafetyReady = true
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
                if (diagnosticAttempt == null) diagnosticAttempt = MonitorRuntime.retryStarted(nowElapsedRealtimeMs())
                lastFailure = MonitorFailure.STARTING
                lastFailureDetail = null
                MonitorRuntime.publish(MonitorStatus(failure = MonitorFailure.STARTING, hasUnmonitoredGap = true))
            }
            MonitorInitState.READY -> {
                lastFailure = MonitorFailure.NONE
                lastFailureDetail = null
                finishDiagnosticAttempt(succeeded = true)
                if (connected) {
                    startPreferenceCollection()
                    requestReconciliation()
                }
            }
            MonitorInitState.FAILED -> finishDiagnosticAttempt(succeeded = false)
            MonitorInitState.STOPPED -> Unit
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
        val boundary = failure as? MonitorBoundaryException
        lastFailure = boundary?.category ?: MonitorFailure.STORAGE
        lastFailureDetail = boundary?.detail ?: MonitorFailureDetail(
            MonitorFailurePhase.BOOT, MonitorFailureCode.UNKNOWN)
        recordFailure(lastFailureDetail)
        finishDiagnosticAttempt(succeeded = false)
        if (pausePresentation()) releaseLease()
        MonitorRuntime.publish(MonitorStatus(failure = lastFailure, hasUnmonitoredGap = true,
            failureDetail = lastFailureDetail))
    }

    private fun startCommandConsumer() {
        if (commandConsumer?.isActive == true) return
        commandConsumer = scope.launch {
            for (command in observations.receiveChannel) {
                observations.received()
                val start = nowElapsedRealtimeMs()
                try {
                    processCommand(command)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: MonitorBoundaryException) {
                    fail(failure.category, failure.detail)
                } catch (_: Exception) {
                    fail(MonitorFailure.INTERRUPTED,
                        MonitorFailureDetail(MonitorFailurePhase.QUEUE, MonitorFailureCode.COMMAND_PROCESSING))
                } finally {
                    val end = nowElapsedRealtimeMs()
                    MonitorRuntime.processingLatency((end - start).coerceAtLeast(0))
                }
            }
        }
    }

    private suspend fun processCommand(command: Command) {
        if (!ready || !readiness.available) { sessionActionQueued = false; return }
        if (overflow) { overflow = false; fail(MonitorFailure.QUEUE,
            MonitorFailureDetail(MonitorFailurePhase.QUEUE, MonitorFailureCode.QUEUE_OVERFLOW)); return }
        if (command is SessionCommand) {
            try { applySessionAction(command) } finally { sessionActionQueued = false }
            return
        }
        val observation = command as Observation
        if (observation.generation != generation) return
        val nowElapsedMs = monitorBoundarySync(MonitorFailure.QUEUE, MonitorFailurePhase.QUEUE,
            MonitorFailureCode.COMMAND_PROCESSING, dependencies::elapsedRealtimeMs)
        if (!hasTrustedObservationAge(nowElapsedMs, observation.signal.sample.elapsedMs)) {
            fail(MonitorFailure.QUEUE,
                MonitorFailureDetail(MonitorFailurePhase.QUEUE, MonitorFailureCode.COMMAND_PROCESSING)); return
        }
        if (observation.signal.enabled && !MonitorRuntime.ownership.acquire(this@MonitoringService)) {
            fail(MonitorFailure.OWNER_BUSY,
                MonitorFailureDetail(MonitorFailurePhase.QUEUE, MonitorFailureCode.OWNER_ALREADY_CLAIMED)); return
        }
        if (observation.signal.enabled) leaseHeld = true
        val currentSafety = safetySnapshotState.current()
        if (controllerSafetyRevision != currentSafety.revision) {
            controller.abandon()
            controllerSafetyRevision = currentSafety.revision
            controllerSafetyReady = false
        }
        if (currentSafety.ready && !controllerSafetyReady) {
            controller.updateProtectedPackages(currentSafety.protectedPackages)
            controllerSafetyReady = true
        }
        val safetyMatches = currentSafety.ready && observation.safetyRevision == currentSafety.revision
        val signal = if (safetyMatches) observation.signal else observation.signal.copy(
            foreground = null, foregroundKnown = false)
        val startedGeneration = generation
        val result = monitorBoundary(MonitorFailure.STORAGE, MonitorFailurePhase.BUDGET,
            MonitorFailureCode.BUDGET_OPERATION) { controller.observe(signal) }
        if (generation != startedGeneration) { controller.abandon(); return }
        if (overflow) { overflow = false; fail(MonitorFailure.QUEUE,
            MonitorFailureDetail(MonitorFailurePhase.QUEUE, MonitorFailureCode.QUEUE_OVERFLOW)); return }
        val selectedScope = monitorBoundary(MonitorFailure.METADATA, MonitorFailurePhase.METADATA,
            MonitorFailureCode.SELECTED_PACKAGE_LOOKUP, dependencies::selectedPackages)
        if (generation != startedGeneration) { coverageRecorder?.invalidate(); return }
        monitorBoundary(MonitorFailure.STORAGE, MonitorFailurePhase.COVERAGE, MonitorFailureCode.COVERAGE_WRITE) {
            coverageRecorder?.observe(signal, result, selectedScope, leaseHeld) {
                connected && readiness.available && generation == startedGeneration && !overflow && leaseHeld &&
                    dependencies.usageAccess()
            }
        }
        lastProcessedElapsedMs = signal.sample.elapsedMs
        if (signal.enabled && signal.foreground in selectedScope) {
            MonitorRuntime.processedObservation(signal.sample.elapsedMs)
        }
        render(result, observation.ticket)
        scheduleCoverageCheckpoint(result)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!connected || event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        try {
            val eventPackage = monitorBoundarySync(MonitorFailure.METADATA, MonitorFailurePhase.METADATA,
                MonitorFailureCode.EVENT_PACKAGE_METADATA) { event.packageName?.toString() }
            // Never event text, source or screen nodes.
            if (eventPackage == packageName && overlay.visible && !MonitorRuntime.ownUiVisible) return
            reconciliation?.cancel()
            if (eventPackage == null) {
                refreshSafetySnapshot(reconcileAfter = true)
            } else if (eventPackage != foreground || !safetySnapshotState.current().ready) {
                // Re-evaluate dynamic home/dialer/keyboard roles on every foreground transition.
                refreshSafetySnapshot(foregroundHint = eventPackage, reconcileAfter = false)
            } else {
                enqueue()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: MonitorBoundaryException) {
            fail(failure.category, failure.detail)
        } catch (_: Exception) {
            fail(MonitorFailure.METADATA,
                MonitorFailureDetail(MonitorFailurePhase.METADATA, MonitorFailureCode.EVENT_PACKAGE_METADATA))
        }
    }

    fun ownActivityResumed() {
        if (!connected) return
        foreground = packageName; foregroundKnown = true; reconciliation?.cancel()
        pausePresentation(); startPreferenceCollection(); enqueue()
    }

    /** Only called by a user tap while our accessibility overlay is visible. */
    private fun openChallenge(target: String, exercise: Exercise?) {
        if (!connected) return
        try {
            if (!overlayVisible()) return
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
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: MonitorBoundaryException) {
            fail(failure.category, failure.detail)
            Toast.makeText(this, R.string.challenge_launch_error, Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            fail(MonitorFailure.NAVIGATION,
                MonitorFailureDetail(MonitorFailurePhase.NAVIGATION, MonitorFailureCode.CHALLENGE_LAUNCH))
            Toast.makeText(this, R.string.challenge_launch_error, Toast.LENGTH_LONG).show()
        }
    }

    /** Only called by a user tap on the live blocker. */
    private fun openScrollFit() {
        if (!connected) return
        try {
            if (!overlayVisible()) return
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
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: MonitorBoundaryException) {
            fail(failure.category, failure.detail)
            Toast.makeText(this, R.string.monitor_navigation_failed, Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            fail(MonitorFailure.NAVIGATION,
                MonitorFailureDetail(MonitorFailurePhase.NAVIGATION, MonitorFailureCode.APP_LAUNCH))
            Toast.makeText(this, R.string.monitor_navigation_failed, Toast.LENGTH_LONG).show()
        }
    }

    /** User taps and observations share one queue; no reset races a suspended checkpoint. */
    private fun resolveSession(access: SessionAccess, action: SessionAction) {
        if (!connected || sessionActionQueued) return
        try {
            if (!overlayIsCurrent(access)) return
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: MonitorBoundaryException) {
            fail(failure.category, failure.detail); return
        }
        sessionActionQueued = true
        tick?.cancel()
        if (!sendCommand(SessionCommand(access, action))) {
            sessionActionQueued = false
            fail(MonitorFailure.QUEUE,
                MonitorFailureDetail(MonitorFailurePhase.QUEUE, MonitorFailureCode.QUEUE_OVERFLOW))
        }
    }

    private suspend fun applySessionAction(command: SessionCommand) {
        val displayed = command.access
        val startedGeneration = generation
        fun eligible() = connected && readiness.monitoringEnabled && leaseHeld &&
            generation == startedGeneration && !overflow && dependencies.usageAccess() &&
            !MonitorRuntime.ownUiVisible &&
            dependencies.isInteractive() && !dependencies.isKeyguardLocked()
        if (!monitorBoundarySync(MonitorFailure.METADATA, MonitorFailurePhase.METADATA,
                MonitorFailureCode.EVENT_PACKAGE_METADATA, ::eligible) ||
            !overlayIsCurrent(displayed) || foreground != displayed.session.packageName) {
            enqueue(); return
        }
        if (command.action == SessionAction.FINISH) {
            // Keep the prompt/session if Home cannot be requested; never reveal the target on failure.
            val navigated = try {
                monitorBoundarySync(MonitorFailure.NAVIGATION, MonitorFailurePhase.NAVIGATION,
                    MonitorFailureCode.GLOBAL_ACTION) { performGlobalAction(GLOBAL_ACTION_HOME) }
            } catch (failure: MonitorBoundaryException) {
                fail(failure.category, failure.detail)
                false
            }
            if (!navigated) {
                if (lastFailureDetail == null) fail(MonitorFailure.NAVIGATION,
                    MonitorFailureDetail(MonitorFailurePhase.NAVIGATION, MonitorFailureCode.GLOBAL_ACTION))
                Toast.makeText(this, R.string.monitor_navigation_failed, Toast.LENGTH_LONG).show()
                enqueue(); return
            }
            foreground = null; foregroundKnown = false
        }
        val result = monitorBoundary(MonitorFailure.STORAGE, MonitorFailurePhase.BUDGET,
            MonitorFailureCode.BUDGET_OPERATION) {
            budgetEngine.resolveSession(displayed, command.action) {
                eligible() && (command.action == SessionAction.FINISH ||
                    (overlayIsCurrent(displayed) && foreground == displayed.session.packageName))
            }
        }
        // Pause and reconcile so prompt time and queued pre-tap samples cannot be charged.
        generation++
        controller.abandon()
        if (result == SessionActionResult.APPLIED) pausePresentation()
        enqueue()
        requestReconciliation()
    }

    /** Stop the live monitor immediately while the durable disable write completes. */
    fun pauseForDisable() {
        if (::initialization.isInitialized && initialization.state == MonitorInitState.INITIALIZING) {
            initialization.stop()
        }
        readiness.failed()
        fail(MonitorFailure.STARTING, null, stopInitialization = false)
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
            catch (_: Exception) {
                readiness.failed()
                fail(MonitorFailure.STORAGE,
                    MonitorFailureDetail(MonitorFailurePhase.PREFERENCES, MonitorFailureCode.PREFERENCE_STREAM))
            }
        }
    }

    fun requestReconciliation() {
        if (!connected || !ready) return
        val safetyRevision = safetySnapshotState.current().takeIf { it.ready }?.revision ?: return
        reconciliation?.cancel()
        reconciliation = scope.launch {
            try {
                delay(200) // Let Android publish the activity transition; event callbacks cancel this lookup.
                val current = monitorBoundary(MonitorFailure.METADATA, MonitorFailurePhase.RECONCILIATION,
                    MonitorFailureCode.FOREGROUND_RECONCILIATION) { dependencies.currentForeground() }
                val latestSafety = safetySnapshotState.current()
                if (!latestSafety.ready || latestSafety.revision != safetyRevision) return@launch
                if (!acceptForeground(if (MonitorRuntime.ownUiVisible) packageName else current.packageName,
                        MonitorRuntime.ownUiVisible || current.known)) return@launch
                if (current.known) MonitorRuntime.successfulReconciliation(nowElapsedRealtimeMs())
                enqueue()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: MonitorBoundaryException) {
                fail(failure.category, failure.detail)
            } catch (_: Exception) {
                fail(MonitorFailure.METADATA,
                    MonitorFailureDetail(MonitorFailurePhase.RECONCILIATION,
                        MonitorFailureCode.FOREGROUND_RECONCILIATION))
            }
        }
    }

    private fun acceptForeground(value: String?, known: Boolean): Boolean {
        // The event callback uses only the published snapshot; all PackageManager/role queries run elsewhere.
        if (value != foreground || !known) pausePresentation()
        foreground = value
        foregroundKnown = known && value != null && safetySnapshotState.current().ready
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
        diagnosticAttempt = MonitorRuntime.retryStarted(nowElapsedRealtimeMs())
        if (!registerSystemListeners()) {
            finishDiagnosticAttempt(succeeded = false)
            return
        }
        if (ready) refreshReadyConfiguration(diagnosticAttempt) else initialization.startOrRetry()
    }

    private fun refreshReadyConfiguration(retryAttempt: Int? = null) {
        generation++
        coverageRecorder?.invalidate()
        controller.abandon()
        controllerSafetyReady = false
        coverageCheckpoint?.cancel()
        startPreferenceCollection(restart = !readiness.available)
        refreshSafetySnapshot(reconcileAfter = true, retryAttempt = retryAttempt)
    }

    private fun refreshSafetySnapshot(
        foregroundHint: String? = null,
        reconcileAfter: Boolean,
        retryAttempt: Int? = null,
    ) {
        if (!connected || !ready) return
        reconciliation?.cancel()
        safetyRefreshForegroundHint = foregroundHint
        safetyRefreshReconcileAfter = reconcileAfter
        safetyRefreshRetryAttempt = retryAttempt
        safetySnapshotRefresher.refresh()
    }

    private fun safetySnapshotInvalidated(revision: Long) {
        generation++
        safetyRefreshGeneration = generation
        foreground = safetyRefreshForegroundHint
        foregroundKnown = false
        pausePresentation()
        enqueue()
    }

    private fun safetySnapshotPublished(snapshot: SafetySnapshot) {
        if (!connected || !ready || snapshot.revision != safetySnapshotState.current().revision ||
            generation != safetyRefreshGeneration) return
        if (safetyRefreshReconcileAfter) {
            requestReconciliation()
        } else {
            foreground = safetyRefreshForegroundHint
            foregroundKnown = foreground != null
            enqueue()
        }
        safetyRefreshRetryAttempt?.let { finishDiagnosticAttempt(succeeded = true, attempt = it) }
        safetyRefreshRetryAttempt = null
    }

    private fun safetySnapshotFailed(failure: Exception) {
        if (!connected || !ready || generation != safetyRefreshGeneration) return
        if (failure is MonitorBoundaryException) fail(failure.category, failure.detail)
        else fail(MonitorFailure.METADATA,
            MonitorFailureDetail(MonitorFailurePhase.METADATA, MonitorFailureCode.SAFE_PACKAGE_LOOKUP))
        safetyRefreshRetryAttempt?.let { finishDiagnosticAttempt(succeeded = false, attempt = it) }
        safetyRefreshRetryAttempt = null
    }

    private fun enqueue() {
        if (!connected || !ready) return
        try {
            tick?.cancel()
            coverageCheckpoint?.cancel()
            val interactive = monitorBoundarySync(MonitorFailure.METADATA, MonitorFailurePhase.METADATA,
                MonitorFailureCode.EVENT_PACKAGE_METADATA) { dependencies.isInteractive() && !dependencies.isKeyguardLocked() }
            val sample = MonitorSample(
                monitorBoundarySync(MonitorFailure.METADATA, MonitorFailurePhase.METADATA,
                    MonitorFailureCode.EVENT_PACKAGE_METADATA) { dependencies.elapsedRealtimeMs() },
                monitorBoundarySync(MonitorFailure.METADATA, MonitorFailurePhase.METADATA,
                    MonitorFailureCode.EVENT_PACKAGE_METADATA) { dependencies.wallTimeMs() },
                monitorBoundarySync(MonitorFailure.METADATA, MonitorFailurePhase.METADATA,
                    MonitorFailureCode.EVENT_PACKAGE_METADATA) { dependencies.zoneId() },
            )
            val safety = safetySnapshotState.current()
            val candidate = foreground?.takeIf { foregroundKnown && safety.allows(it) }
            val usageAccess = monitorBoundarySync(MonitorFailure.METADATA, MonitorFailurePhase.METADATA,
                MonitorFailureCode.EVENT_PACKAGE_METADATA) { dependencies.usageAccess() }
            val signal = readiness.guard(MonitorSignal(sample, candidate, false, usageAccess, connected,
                interactive, MonitorRuntime.ownUiVisible, foregroundKnown && safety.ready))
            // Hide on safe/system/self/access-loss signals now, independently of a suspended budget read.
            val beforePresentation = generation
            val ticket = monitorBoundarySync(MonitorFailure.OVERLAY, MonitorFailurePhase.OVERLAY,
                MonitorFailureCode.OVERLAY_FAILURE) {
                presentation.transition(candidate, pause = !signal.enabled || !signal.connected ||
                    !signal.usageAccess || !signal.interactive || signal.ownUiVisible || !signal.foregroundKnown ||
                    !safety.ready)
            }
            if (generation != beforePresentation) return
            if (!readiness.available) return
            if (!sendCommand(Observation(signal, ticket, generation, safety.revision))) {
                overflow = true
                fail(MonitorFailure.QUEUE,
                    MonitorFailureDetail(MonitorFailurePhase.QUEUE, MonitorFailureCode.QUEUE_OVERFLOW))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: MonitorBoundaryException) {
            fail(failure.category, failure.detail)
        } catch (_: Exception) {
            fail(MonitorFailure.INTERRUPTED,
                MonitorFailureDetail(MonitorFailurePhase.QUEUE, MonitorFailureCode.COMMAND_PROCESSING))
        }
    }

    private fun render(result: MonitorDecision, ticket: Long) {
        val presented = try {
            monitorBoundarySync(MonitorFailure.OVERLAY, MonitorFailurePhase.OVERLAY,
                MonitorFailureCode.OVERLAY_PRESENT) {
                if (result.blockedBy != null && !leaseHeld) { pausePresentation(); false }
                else presentation.present(ticket, result)
            }
        } catch (failure: MonitorBoundaryException) {
            fail(failure.category, failure.detail); return
        }
        if (!presented) return
        if (result.health == MonitoringHealth.DISABLED) releaseLease()
        if (result.health != MonitoringHealth.SERVICE_STOPPED) {
            lastFailure = MonitorFailure.NONE
            lastFailureDetail = null
        }
        MonitorRuntime.publish(MonitorStatus(result.health, result, lastFailure,
            hasUnmonitoredGap = result.unmonitoredMs > 0, failureDetail = lastFailureDetail))
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
        try {
            if (!monitorBoundarySync(MonitorFailure.NAVIGATION, MonitorFailurePhase.NAVIGATION,
                    MonitorFailureCode.GLOBAL_ACTION) { performGlobalAction(action) }) {
                fail(MonitorFailure.NAVIGATION,
                    MonitorFailureDetail(MonitorFailurePhase.NAVIGATION, MonitorFailureCode.GLOBAL_ACTION))
                Toast.makeText(this, R.string.monitor_navigation_failed, Toast.LENGTH_LONG).show()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: MonitorBoundaryException) {
            fail(failure.category, failure.detail)
            Toast.makeText(this, R.string.monitor_navigation_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun disableMonitoring() {
        pauseForDisable()
        scope.launch {
            try {
                dependencies.setMonitoringEnabled(false)
                startPreferenceCollection(restart = true)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                fail(MonitorFailure.STORAGE,
                    MonitorFailureDetail(MonitorFailurePhase.PREFERENCES, MonitorFailureCode.PREFERENCE_WRITE))
            }
        }
    }

    private fun releaseLease() { MonitorRuntime.ownership.release(this); leaseHeld = false }
    private fun fail(
        failure: MonitorFailure,
        detail: MonitorFailureDetail?,
        stopInitialization: Boolean = true,
    ) {
        if (!connected) return
        if (failureInProgress) {
            if (detail != null) {
                nestedFailure = failure to detail
                recordFailure(detail)
            }
            return
        }
        failureInProgress = true
        nestedFailure = null
        try {
        sessionActionQueued = false
        generation++ // Invalidate any decision suspended in storage when failure arrived.
        readiness.failed()
        coverageRecorder?.invalidate()
        lastFailure = failure
        lastFailureDetail = detail
        if (detail != null) recordFailure(detail)
        tick?.cancel(); coverageCheckpoint?.cancel(); reconciliation?.cancel()
        preferenceJob?.cancel(); preferenceJob = null
        if (stopInitialization && ::initialization.isInitialized && initialization.state == MonitorInitState.INITIALIZING) {
            initialization.stop()
        }
        if (::controller.isInitialized) controller.abandon()
        while (observations.tryReceive().isSuccess) {
            MonitorRuntime.commandDropped()
        }
        foreground = null; foregroundKnown = false
        // Retain exclusive ownership while an old window's removal cannot be confirmed.
        val removed = try {
            presentation.pause()
        } catch (_: Exception) {
            nestedFailure = MonitorFailure.OVERLAY to
                MonitorFailureDetail(MonitorFailurePhase.OVERLAY, MonitorFailureCode.OVERLAY_REMOVE)
            false
        }
        if (removed) releaseLease()
        val effective = nestedFailure ?: (failure to detail)
        lastFailure = effective.first
        lastFailureDetail = effective.second
        MonitorRuntime.publish(MonitorStatus(failure = lastFailure, hasUnmonitoredGap = true,
            failureDetail = lastFailureDetail))
        } finally {
            failureInProgress = false
            nestedFailure = null
        }
    }

    private fun pausePresentation(): Boolean = try {
        monitorBoundarySync(MonitorFailure.OVERLAY, MonitorFailurePhase.OVERLAY,
            MonitorFailureCode.OVERLAY_REMOVE) { presentation.pause() }
    } catch (failure: MonitorBoundaryException) {
        fail(failure.category, failure.detail)
        false
    }

    private fun overlayVisible(): Boolean = monitorBoundarySync(MonitorFailure.OVERLAY,
        MonitorFailurePhase.OVERLAY, MonitorFailureCode.OVERLAY_FAILURE) { overlay.visible }

    private fun overlayIsCurrent(access: SessionAccess): Boolean = monitorBoundarySync(MonitorFailure.OVERLAY,
        MonitorFailurePhase.OVERLAY, MonitorFailureCode.OVERLAY_FAILURE) { overlay.isCurrent(access) }

    override fun onInterrupt() {
        fail(MonitorFailure.INTERRUPTED,
            MonitorFailureDetail(MonitorFailurePhase.REGISTRATION, MonitorFailureCode.SERVICE_INTERRUPTED))
    }
    override fun onUnbind(intent: Intent): Boolean { cleanup(); return super.onUnbind(intent) }
    override fun onDestroy() { cleanup(); super.onDestroy() }
    private fun cleanup() {
        if (!connected) return
        // Last asynchronous checkpoint may be cancelled by Android; do not block the main thread.
        if (::initialization.isInitialized) initialization.stop()
        connected = false
        readiness.failed()
        tick?.cancel(); coverageCheckpoint?.cancel(); reconciliation?.cancel(); preferenceJob?.cancel()
        commandConsumer?.cancel(); commandConsumer = null
        coverageRecorder?.invalidate(); coverageRecorder = null
        if (::scope.isInitialized) scope.cancel()
        if (::safetySnapshotRefresher.isInitialized) safetySnapshotRefresher.cancel()
        if (::observations.isInitialized) observations.cancel()
        try { registrations?.unregisterAll() } catch (_: Exception) { /* Continue releasing other resources. */ }
        registrations = null
        try { if (overlay.dismiss()) releaseLease() }
        catch (_: Exception) { /* Keep the ownership lease when removal is uncertain. */ }
        MonitorRuntime.disconnected(this)
    }

    private fun sendCommand(command: Command): Boolean {
        if (observations.trySend(command) { depth ->
                MonitorRuntime.queueDepth(depth.coerceAtMost(observations.capacity))
            }) return true
        MonitorRuntime.commandDropped()
        return false
    }

    private fun recordFailure(detail: MonitorFailureDetail?) {
        if (detail == null) return
        MonitorRuntime.recordFailure(nowElapsedRealtimeMs(), detail)
    }

    private fun finishDiagnosticAttempt(succeeded: Boolean, attempt: Int? = diagnosticAttempt) {
        if (attempt == null) return
        MonitorRuntime.retryFinished(nowElapsedRealtimeMs(), attempt, succeeded)
        if (diagnosticAttempt == attempt) diagnosticAttempt = null
    }

    private fun nowElapsedRealtimeMs(): Long = try {
        if (::dependencies.isInitialized) dependencies.elapsedRealtimeMs() else SystemClock.elapsedRealtime()
    } catch (_: Exception) {
        SystemClock.elapsedRealtime()
    }
}
