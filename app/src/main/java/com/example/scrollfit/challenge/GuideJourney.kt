package com.example.scrollfit.challenge

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.scrollfit.core.budget.StartResult
import com.example.scrollfit.core.data.Challenge
import com.example.scrollfit.core.exercise.Exercise
import com.example.scrollfit.core.pose.FramingIssue
import com.example.scrollfit.core.pose.PositionReadiness
import com.example.scrollfit.core.time.MonotonicClock
import com.example.scrollfit.platform.pose.CameraPoseState
import com.example.scrollfit.platform.pose.CameraPoseStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal enum class GuideStage { PREPARE, LEARN, POSITION, STARTING, COUNTDOWN, EXERCISE, RESULT, INTERRUPTED }

/** Owns the UI-only guide stages and keeps positioning/countdown camera state out of a challenge. */
internal class GuideJourney(
    private val scope: CoroutineScope,
    private val clock: MonotonicClock,
    private val start: suspend () -> StartResult,
    private val cancel: suspend (String) -> Unit,
    private val activate: (String) -> Unit,
    private val challengeIsCurrent: (Challenge) -> Boolean = { true },
    private val onChallengeExpired: () -> Unit = {},
    initialExercise: Exercise? = null,
    initiallyPermissionGranted: Boolean = false,
) {
    var stage by mutableStateOf(if (initialExercise == null) GuideStage.PREPARE else GuideStage.LEARN)
        private set
    var exercise by mutableStateOf(initialExercise)
        private set
    var positionRunKey by mutableStateOf<String?>(null)
        private set
    var ready by mutableStateOf(false)
        private set
    var countdownRemaining by mutableStateOf<Int?>(null)
        private set
    var positionChanged by mutableStateOf(false)
        private set
    var startError by mutableStateOf(false)
        private set
    var cancellationPending by mutableStateOf(false)
        private set
    var cancellationError by mutableStateOf(false)
        private set
    var startInFlight by mutableStateOf(false)
        private set

    private var generation = 0L
    private var positionGeneration = 0L
    private var positionReadiness = PositionReadiness()
    private var continuitySerial: Long? = null
    private var startContinuitySerial: Long? = null
    private var startRunKey: String? = null
    private var startInvalidated = false
    private var stageAfterLateStart = GuideStage.PREPARE
    private var permissionGranted = initiallyPermissionGranted
    private var pendingChallenge: Challenge? = null
    private var pendingCancellationId: String? = null
    private var cancellationDestination = GuideStage.POSITION
    private var countdownStartedAtMs: Long? = null
    private var countdownDeadlineMs: Long? = null

    fun showGuide(selectedExercise: Exercise) {
        if (stage != GuideStage.PREPARE) return
        generation++
        exercise = selectedExercise
        ready = false
        positionRunKey = null
        stage = GuideStage.LEARN
    }

    fun continueToPosition() {
        if (stage != GuideStage.LEARN || exercise == null) return
        positionChanged = false
        stage = GuideStage.POSITION
        startPositionRun()
    }

    /** A lens or exercise change creates a fresh, isolated position counter and readiness window. */
    fun resetPosition() {
        if (stage != GuideStage.POSITION) return
        startPositionRun()
    }

    fun permissionChanged(granted: Boolean) {
        val changed = permissionGranted != granted
        permissionGranted = granted
        if (!changed) return
        when (stage) {
            GuideStage.POSITION -> startPositionRun()
            GuideStage.STARTING -> invalidateStarting(GuideStage.POSITION)
            GuideStage.COUNTDOWN -> cancelPendingToPosition()
            else -> Unit
        }
    }

    fun observePosition(runKey: String, state: CameraPoseState, nowMs: Long = clock.elapsedRealtimeMillis()) {
        if (runKey != positionRunKey || stage !in setOf(GuideStage.POSITION, GuideStage.STARTING, GuideStage.COUNTDOWN)) return

        val previousSerial = continuitySerial
        val serialChanged = previousSerial != null && previousSerial != state.framingResetSerial
        continuitySerial = state.framingResetSerial
        if (serialChanged) {
            positionReadiness.reset()
            ready = false
            when (stage) {
                GuideStage.STARTING -> {
                    positionChanged = true
                    invalidateStarting(GuideStage.POSITION)
                }
                GuideStage.COUNTDOWN -> {
                    cancelPendingToPosition(showPositionChanged = true)
                    return
                }
                else -> Unit
            }
        }

        val tracking = state.status == CameraPoseStatus.TRACKING && state.progress?.trackingAvailable == true
        val framed = state.framing?.issue == FramingIssue.FRAMED
        val freshReady = positionReadiness.update(state.framing, tracking, nowMs)
        when (stage) {
            GuideStage.POSITION -> ready = freshReady
            GuideStage.STARTING -> if (!freshReady || !framed || !tracking || serialChanged) {
                positionChanged = true
                invalidateStarting(GuideStage.POSITION)
            }
            GuideStage.COUNTDOWN -> if (!freshReady || !framed || !tracking) {
                cancelPendingToPosition(showPositionChanged = true)
            }
            else -> Unit
        }
    }

    /** 100 ms watchdog tick; it expires old pose evidence without replaying camera observations. */
    fun tick(nowMs: Long = clock.elapsedRealtimeMillis()) {
        when (stage) {
            GuideStage.POSITION -> ready = positionReadiness.isReady(nowMs)
            GuideStage.STARTING -> {
                if (!permissionGranted || !positionReadiness.isReady(nowMs)) {
                    if (permissionGranted) positionChanged = true
                    invalidateStarting(GuideStage.POSITION)
                }
            }
            GuideStage.COUNTDOWN -> {
                val challenge = pendingChallenge
                if (challenge != null && !challengeIsCurrent(challenge)) {
                    onChallengeExpired()
                    cancelPending(GuideStage.PREPARE)
                    return
                }
                if (!permissionGranted || !positionReadiness.isReady(nowMs) || challenge == null) {
                    cancelPendingToPosition(showPositionChanged = permissionGranted &&
                        !positionReadiness.isReady(nowMs))
                    return
                }
                val deadline = countdownDeadlineMs ?: return
                val remainingMs = (deadline - nowMs).coerceAtLeast(0)
                if (remainingMs == 0L) activateWorkout(challenge)
                else countdownRemaining = ((remainingMs + 999L) / 1_000L).toInt()
            }
            else -> Unit
        }
    }

    fun requestStart(allowed: Boolean) {
        if (stage != GuideStage.POSITION || !ready || !allowed || startInFlight || cancellationPending ||
            pendingChallenge != null || !permissionGranted) return
        val runKey = positionRunKey ?: return
        val nowMs = clock.elapsedRealtimeMillis()
        if (!positionReadiness.isReady(nowMs)) { ready = false; return }

        val ticket = ++generation
        startContinuitySerial = continuitySerial
        startRunKey = runKey
        startInvalidated = false
        stageAfterLateStart = GuideStage.POSITION
        startError = false
        positionChanged = false
        stage = GuideStage.STARTING
        startInFlight = true
        scope.launch {
            try {
                when (val result = start()) {
                    is StartResult.Started -> {
                        val challengeIsCurrent = challengeIsCurrent(result.challenge)
                        val valid = ticket == generation && stage == GuideStage.STARTING && !startInvalidated &&
                            startRunKey == positionRunKey && startContinuitySerial == continuitySerial &&
                            permissionGranted && positionReadiness.isReady(clock.elapsedRealtimeMillis()) &&
                            challengeIsCurrent
                        if (valid) {
                            pendingChallenge = result.challenge
                            countdownStartedAtMs = clock.elapsedRealtimeMillis()
                            countdownDeadlineMs = (countdownStartedAtMs ?: 0L) + COUNTDOWN_DURATION_MS
                            countdownRemaining = COUNTDOWN_SECONDS
                            stage = GuideStage.COUNTDOWN
                        } else {
                            if (!challengeIsCurrent) {
                                stageAfterLateStart = GuideStage.PREPARE
                                onChallengeExpired()
                            }
                            cancelStarted(result.challenge, stageAfterLateStart)
                        }
                    }
                    is StartResult.OfferChanged -> {
                        if (ticket == generation && stage == GuideStage.STARTING) {
                            positionRunKey = null
                            ready = false
                            stage = GuideStage.PREPARE
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (ticket == generation && stage == GuideStage.STARTING) {
                    startError = true
                    stage = GuideStage.POSITION
                }
            } finally {
                startInFlight = false
                if (ticket != generation && stage == GuideStage.INTERRUPTED && pendingChallenge == null &&
                    pendingCancellationId == null && stageAfterLateStart == GuideStage.PREPARE) {
                    stage = GuideStage.PREPARE
                }
            }
        }
    }

    fun retryCancellation() {
        val challenge = pendingChallenge ?: return
        if (!cancellationError || cancellationPending) return
        cancelStarted(challenge, cancellationDestination)
    }

    /** Invalidates every unfinished stage synchronously before any suspending cancellation write. */
    fun interrupt() {
        when (stage) {
            GuideStage.COUNTDOWN -> {
                generation++
                countdownRemaining = null
                cancelPending(GuideStage.PREPARE)
            }
            GuideStage.STARTING -> {
                generation++
                stageAfterLateStart = GuideStage.PREPARE
                startInvalidated = true
                positionRunKey = null
                ready = false
                stage = GuideStage.INTERRUPTED
            }
            GuideStage.EXERCISE -> {
                generation++
                stage = GuideStage.INTERRUPTED
                positionRunKey = null
            }
            GuideStage.RESULT, GuideStage.INTERRUPTED -> Unit
            else -> {
                generation++
                positionRunKey = null
                ready = false
                stage = GuideStage.PREPARE
            }
        }
        positionReadiness.reset()
    }

    fun returnToPrepare() {
        if (cancellationPending || startInFlight) return
        generation++
        pendingChallenge = null
        pendingCancellationId = null
        cancellationError = false
        cancellationPending = false
        positionRunKey = null
        ready = false
        countdownRemaining = null
        positionChanged = false
        positionReadiness.reset()
        stage = GuideStage.PREPARE
        startError = false
    }

    fun showResult() {
        if (stage == GuideStage.EXERCISE) stage = GuideStage.RESULT
    }

    private fun invalidateStarting(destination: GuideStage) {
        startInvalidated = true
        stageAfterLateStart = destination
        ready = false
        positionReadiness.reset()
    }

    private fun cancelPendingToPosition(showPositionChanged: Boolean = false) {
        countdownRemaining = null
        if (showPositionChanged) positionChanged = true
        cancelPending(GuideStage.POSITION)
    }

    private fun cancelPending(destination: GuideStage) {
        val challenge = pendingChallenge
        if (challenge == null) {
            stage = destination
            positionRunKey = null
            return
        }
        cancelStarted(challenge, destination)
    }

    private fun cancelStarted(challenge: Challenge, destination: GuideStage) {
        if (pendingCancellationId == challenge.id) return
        pendingChallenge = challenge
        pendingCancellationId = challenge.id
        cancellationDestination = destination
        cancellationPending = true
        cancellationError = false
        countdownRemaining = null
        positionReadiness.reset()
        ready = false
        if (destination == GuideStage.POSITION) {
            stage = GuideStage.POSITION
            startPositionRun()
        } else {
            stage = GuideStage.INTERRUPTED
            positionRunKey = null
        }
        scope.launch {
            try {
                cancel(challenge.id)
                if (pendingCancellationId == challenge.id) {
                    pendingCancellationId = null
                    pendingChallenge = null
                    cancellationPending = false
                    cancellationError = false
                    if (destination == GuideStage.PREPARE && stage == GuideStage.INTERRUPTED) stage = GuideStage.PREPARE
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (pendingCancellationId == challenge.id) {
                    // Allow Retry to launch a new cancellation attempt for this retained challenge.
                    pendingCancellationId = null
                    cancellationPending = false
                    cancellationError = true
                    cancellationDestination = destination
                    stage = GuideStage.INTERRUPTED
                    positionRunKey = null
                }
            }
        }
    }

    private fun activateWorkout(challenge: Challenge) {
        if (stage != GuideStage.COUNTDOWN || pendingChallenge?.id != challenge.id ||
            !positionReadiness.isReady(clock.elapsedRealtimeMillis()) || !permissionGranted ||
            !challengeIsCurrent(challenge) || startContinuitySerial != continuitySerial) {
            if (!challengeIsCurrent(challenge)) {
                onChallengeExpired()
                cancelPending(GuideStage.PREPARE)
            } else cancelPendingToPosition()
            return
        }
        try {
            positionRunKey = null
            activate(challenge.id)
            pendingChallenge = null
            countdownRemaining = null
            stage = GuideStage.EXERCISE
        } catch (_: Exception) {
            cancelStarted(challenge, GuideStage.PREPARE)
            startError = true
        }
    }

    private fun startPositionRun() {
        positionGeneration++
        positionRunKey = "position:$positionGeneration"
        positionReadiness = PositionReadiness()
        continuitySerial = null
        startContinuitySerial = null
        startRunKey = null
        ready = false
        countdownRemaining = null
        startError = false
    }

    companion object {
        const val COUNTDOWN_SECONDS = 3
        const val COUNTDOWN_DURATION_MS = 3_000L
    }
}
