package com.example.scrollfit.challenge

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.scrollfit.ScrollFitApplication
import com.example.scrollfit.core.budget.*
import com.example.scrollfit.core.challenge.ChallengeSession
import com.example.scrollfit.core.challenge.ChallengePhase
import com.example.scrollfit.core.data.*
import com.example.scrollfit.core.exercise.Exercise
import com.example.scrollfit.feedback.ChallengeAudio
import com.example.scrollfit.core.feedback.FeedbackAccessibility
import com.example.scrollfit.platform.pose.CameraPoseState
import com.example.scrollfit.platform.pose.CameraPoseStatus
import com.example.scrollfit.monitoring.MonitorRuntime
import com.example.scrollfit.monitoring.TargetApp
import com.example.scrollfit.monitoring.TargetApps
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.UUID

/** Main-thread UI owner. Database work survives navigation; eligibility does not. */
internal class ChallengeFlow(private val app: ScrollFitApplication, val mode: ChallengeMode,
    private val initialTarget: String?, private val initialExercise: Exercise?) {
    private val persistence = app.persistence
    val scope = app.challengeScope
    val monotonicClock = persistence.monotonicClock
    val audio = ChallengeAudio(app)
    private val feedbackAccessibility = FeedbackAccessibility(audio::setAudioEnabled)
    private var speechWrite: Job? = null
    private var speechRevision = 0L
    private val touchExplorationState = mutableStateOf(false)
    var speechEnabled by mutableStateOf(false); private set
    var speechLoaded by mutableStateOf(false); private set
    var speechError by mutableStateOf(false); private set
    val touchExplorationEnabled by touchExplorationState
    private var engine: BudgetEngine? = null
    private var generation = 0L
    private var refreshOnResume = false
    private var pendingStartedChallenge: Challenge? = null
    var targets by mutableStateOf<List<TargetApp>>(emptyList()); private set
    var target by mutableStateOf(initialTarget); private set
    var exercise by mutableStateOf(initialExercise ?: Exercise.PUSH_UP); private set
    var camera by mutableStateOf(CameraChoice.REAR); private set
    var offer by mutableStateOf<OfferResult?>(null); private set
    var session by mutableStateOf<ChallengeSession?>(null); private set
    var phase by mutableStateOf<ChallengePhase?>(null); private set
    var result by mutableStateOf<CompletionResult?>(null); private set
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf(false); private set
    var changed by mutableStateOf(false); private set
    var interrupted by mutableStateOf(false); private set
    var cameraModelError by mutableStateOf(false); private set
    var cancellationPending by mutableStateOf(false); private set
    var repetitions by mutableStateOf(0); private set

    fun load() {
        val ticket = ++generation
        val initialized = engine != null
        busy = true; error = false
        scope.launch {
            try {
                val repository = persistence.repository()
                val prefs = persistence.preferences.values.first()
                val available = if (mode == ChallengeMode.EXERCISE_ONLY) emptyList() else {
                    val selected = repository.targets().filter { it.selected }.map { it.packageName }.toSet()
                    withContext(Dispatchers.IO) { TargetApps.list(app).filter { it.packageName in selected } }
                }
                // This engine never spends usage; its cursor identity is not used.
                val policy = persistence.budgetEngine("challenge-ui")
                if (ticket != generation) return@launch
                engine = policy; targets = available; camera = prefs.camera
                if (!speechLoaded) {
                    speechEnabled = prefs.speechEnabled; speechLoaded = true
                    feedbackAccessibility.update(speechEnabled, touchExplorationEnabled)
                }
                if (!initialized) exercise = initialExercise ?: prefs.lastExercise
                target = (target ?: initialTarget)?.takeIf { wanted -> available.any { it.packageName == wanted } }
                refreshOffer(ticket)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (ticket == generation) error = true }
            finally { if (ticket == generation) busy = false }
        }
    }

    fun updateSpeechEnabled(value: Boolean) {
        if (!speechLoaded) return
        speechEnabled = value
        feedbackAccessibility.update(value, touchExplorationEnabled) // Muting takes effect before storage suspension.
        speechError = false
        val ticket = ++speechRevision
        speechWrite?.cancel()
        speechWrite = scope.launch {
            try { persistence.preferences.update { it.copy(speechEnabled = value) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (ticket == speechRevision) speechError = true }
        }
    }

    fun setTouchExplorationEnabled(value: Boolean) {
        touchExplorationState.value = value
        if (speechLoaded) feedbackAccessibility.update(speechEnabled, value)
    }

    fun chooseTarget(value: String) { target = value; updateOffer() }
    fun chooseExercise(value: Exercise) { exercise = value; updateOffer() }
    private fun updateOffer() {
        val ticket = ++generation
        offer = null; busy = true; error = false; changed = false
        scope.launch {
            try { refreshOffer(ticket) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (ticket == generation) error = true }
            finally { if (ticket == generation) busy = false }
        }
    }
    private suspend fun refreshOffer(ticket: Long) {
        val quote = engine?.offer(if (mode == ChallengeMode.EXERCISE_ONLY) null else target, exercise, mode)
        if (ticket == generation) offer = quote
    }

    fun chooseCamera(value: CameraChoice) {
        if (busy) return
        busy = true; error = false
        scope.launch {
            try { persistence.preferences.update { it.copy(camera = value) }; camera = value }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = true }
            finally { busy = false }
        }
    }

    /** Creates the persisted row after explicit Start, but leaves reps/audio inactive through countdown. */
    suspend fun startForGuide(): StartResult {
        val quote = (offer as? OfferResult.Available)?.offer
            ?: return StartResult.OfferChanged(offer ?: OfferResult.Refused(RewardBlock.INACTIVE))
        val policy = engine ?: return StartResult.OfferChanged(offer ?: OfferResult.Refused(RewardBlock.INACTIVE))
        if (busy || session != null || pendingStartedChallenge != null) {
            return StartResult.OfferChanged(offer ?: OfferResult.Refused(RewardBlock.INACTIVE))
        }
        busy = true; error = false; changed = false; interrupted = false
        val ticket = ++generation
        return try {
            persistence.preferences.update { it.copy(lastExercise = quote.exercise) }
            if (ticket != generation) {
                StartResult.OfferChanged(offer ?: OfferResult.Refused(RewardBlock.INACTIVE))
            } else {
                val started = policy.start(UUID.randomUUID().toString(), quote)
                if (ticket == generation) {
                    if (started is StartResult.Started) pendingStartedChallenge = started.challenge
                    else {
                        offer = (started as StartResult.OfferChanged).current
                        changed = true
                    }
                }
                started
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (ticket == generation) error = true
            throw failure
        } finally {
            if (ticket == generation) busy = false
        }
    }

    /** Activates only the exact row returned for the current countdown. */
    fun activateStartedChallenge(id: String) {
        val challenge = checkNotNull(pendingStartedChallenge?.takeIf { it.id == id }) {
            "The started challenge is no longer pending"
        }
        val policy = checkNotNull(engine) { "Challenge policy is unavailable" }
        pendingStartedChallenge = null
        session = ChallengeSession(challenge, policy, persistence.wallClock, persistence.monotonicClock)
        phase = ChallengePhase.RUNNING
        result = null
        repetitions = 0
        interrupted = false
        cameraModelError = false
        audio.controller.begin()
    }

    suspend fun cancelStartedChallenge(id: String) {
        persistence.repository().cancelChallenge(id)
        if (pendingStartedChallenge?.id == id) pendingStartedChallenge = null
    }

    fun challengeIsCurrent(challenge: Challenge): Boolean =
        challenge.day == persistence.wallClock.localDate().toString()

    private var tracking = false
    fun trackingChanged(available: Boolean) {
        tracking = available
        audio.controller.progress(session?.repetitions ?: 0, available)
    }

    /** Routes one state from the current camera generation into challenge progress and recovery. */
    fun cameraStateChanged(runKey: String, state: CameraPoseState) {
        val active = session ?: return
        if (active.challenge.id != runKey) return
        if (state.status == CameraPoseStatus.MODEL_ERROR) recordCameraModelError()
        if (phase != ChallengePhase.RUNNING) return

        trackingChanged(state.status == CameraPoseStatus.TRACKING && state.progress?.trackingAvailable == true)
        state.progress?.let { progress ->
            state.timestampMs?.let { active.accept(progress, it) }
            progressChanged()
        }
        if (phase != ChallengePhase.RUNNING) return
        if (state.status in setOf(CameraPoseStatus.TRACKING, CameraPoseStatus.SEARCHING, CameraPoseStatus.TRACKING_LOST)) {
            val trackingAvailable = state.status == CameraPoseStatus.TRACKING &&
                state.progress?.trackingAvailable == true
            val framingIssue = if (trackingAvailable) {
                state.framing?.issue ?: com.example.scrollfit.core.pose.FramingIssue.FRAMED
            } else {
                state.framing?.issue?.takeUnless { it == com.example.scrollfit.core.pose.FramingIssue.FRAMED }
                    ?: com.example.scrollfit.core.pose.FramingIssue.NO_RELIABLE_POSE
            }
            if (state.framing?.issue == com.example.scrollfit.core.pose.FramingIssue.FRAMED && !trackingAvailable) {
                audio.controller.updateGuide(framingIssue, ready = false, activeWorkout = true, allowPrompt = false)
            } else {
                audio.controller.updateGuide(framingIssue, ready = false, activeWorkout = true)
            }
        } else {
            audio.controller.clearGuide()
        }
        if (state.status in setOf(CameraPoseStatus.PAUSED, CameraPoseStatus.PERMISSION_REQUIRED,
                CameraPoseStatus.CAMERA_UNAVAILABLE, CameraPoseStatus.CAMERA_ERROR,
                CameraPoseStatus.MODEL_ERROR, CameraPoseStatus.INFERENCE_ERROR)) {
            interrupt()
        }
    }

    fun progressChanged() {
        val active = session ?: return
        repetitions = active.repetitions; phase = active.phase
        if (active.phase != ChallengePhase.INTERRUPTED) audio.controller.progress(repetitions, tracking)
        if (active.phase == ChallengePhase.READY && !busy) complete()
        if (active.phase == ChallengePhase.INTERRUPTED) interrupt()
    }

    fun complete() {
        val active = session ?: return
        if (busy) return
        busy = true; error = false; phase = ChallengePhase.SAVING
        scope.launch {
            try {
                val persisted = active.complete()
                // A committed award remains valid even if navigation occurred during delivery.
                result = persisted; phase = active.phase
                audio.controller.complete(persisted)
                if (persisted is CompletionResult.Awarded) MonitorRuntime.configurationChanged()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = true; phase = active.phase }
            finally { busy = false }
        }
    }

    fun checkDay() { if (session?.checkDay() == false) interrupt() }

    fun recordCameraModelError() { cameraModelError = true }

    fun reviewNewChallenge() {
        if (!interrupted || cancellationPending || error) return
        session = null
        phase = null
        result = null
        offer = null
        changed = false
        repetitions = 0
        interrupted = false
        cameraModelError = false
        refreshOnResume = false
        load()
    }

    fun retryInterruptionCancellation() {
        val active = session ?: return
        if (!interrupted || !error || cancellationPending) return
        persistInterruptionCancellation(active)
    }

    fun resume() {
        if (refreshOnResume && session == null) { refreshOnResume = false; load() }
    }

    fun interrupt() {
        audio.controller.stop()
        ++generation // Synchronous: also invalidates a start suspended in Room/DataStore.
        val active = session
        active?.interrupt()
        if (active == null) { refreshOnResume = true; busy = false; return }
        phase = active.phase
        if (active.phase == ChallengePhase.FINISHED) return
        if (interrupted) return
        interrupted = true
        persistInterruptionCancellation(active)
    }

    private fun persistInterruptionCancellation(active: ChallengeSession) {
        error = false
        cancellationPending = true
        scope.launch {
            try { persistence.repository().cancelChallenge(active.challenge.id) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = true }
            finally { cancellationPending = false }
        }
    }
}
