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
    private val scope = app.challengeScope
    val audio = ChallengeAudio(app)
    private var speechWrite: Job? = null
    private var speechRevision = 0L
    var speechEnabled by mutableStateOf(false); private set
    var speechLoaded by mutableStateOf(false); private set
    var speechError by mutableStateOf(false); private set
    private var engine: BudgetEngine? = null
    private var generation = 0L
    private var refreshOnResume = false
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
                    audio.setEnabled(speechEnabled)
                }
                if (!initialized) exercise = initialExercise ?: prefs.lastExercise
                target = (target ?: initialTarget)?.takeIf { wanted -> available.any { it.packageName == wanted } }
                refreshOffer(ticket)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (ticket == generation) error = true }
            finally { if (ticket == generation) busy = false }
        }
    }

    fun setSpeechEnabled(value: Boolean) {
        if (!speechLoaded) return
        speechEnabled = value
        audio.setEnabled(value) // Muting takes effect before any storage suspension.
        speechError = false
        val ticket = ++speechRevision
        speechWrite?.cancel()
        speechWrite = scope.launch {
            try { persistence.preferences.update { it.copy(speechEnabled = value) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (ticket == speechRevision) speechError = true }
        }
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

    fun start() {
        val quote = (offer as? OfferResult.Available)?.offer ?: return
        val policy = engine ?: return
        if (busy || session != null) return
        busy = true; error = false; changed = false; interrupted = false
        val ticket = ++generation
        scope.launch {
            try {
                persistence.preferences.update { it.copy(lastExercise = quote.exercise) }
                if (ticket != generation) return@launch
                val started = policy.start(UUID.randomUUID().toString(), quote)
                if (started is StartResult.Started) {
                    if (ticket != generation) {
                        persistence.repository().cancelChallenge(started.challenge.id)
                        return@launch
                    }
                    session = ChallengeSession(started.challenge, policy, persistence.wallClock, persistence.monotonicClock)
                    phase = ChallengePhase.RUNNING
                    audio.controller.begin()
                } else if (ticket == generation) {
                    offer = (started as StartResult.OfferChanged).current; changed = true
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (ticket == generation) error = true }
            finally { if (ticket == generation) busy = false }
        }
    }

    private var tracking = false
    fun trackingChanged(available: Boolean) {
        tracking = available
        audio.controller.progress(session?.repetitions ?: 0, available)
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
        interrupted = true
        scope.launch {
            try { persistence.repository().cancelChallenge(active.challenge.id) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = true }
        }
    }
}
