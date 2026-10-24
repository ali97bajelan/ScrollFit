package com.example.scrollfit.guide

import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.scrollfit.R
import com.example.scrollfit.camera.message
import com.example.scrollfit.core.exercise.Exercise
import com.example.scrollfit.core.pose.FramingIssue
import com.example.scrollfit.platform.pose.CameraPoseStatus
import kotlinx.coroutines.delay

internal enum class GuideStatusStage { POSITION, COUNTDOWN, WORKOUT }

internal data class GuideStatusState(
    val runKey: String,
    val exercise: Exercise,
    val status: CameraPoseStatus,
    val issue: FramingIssue?,
    val ready: Boolean,
    val trackingAvailable: Boolean,
    val stage: GuideStatusStage,
    val startAllowed: Boolean = true,
    val starting: Boolean = false,
)

private val guideCameraErrors = setOf(
    CameraPoseStatus.PERMISSION_REQUIRED,
    CameraPoseStatus.CAMERA_UNAVAILABLE,
    CameraPoseStatus.CAMERA_ERROR,
    CameraPoseStatus.MODEL_ERROR,
    CameraPoseStatus.INFERENCE_ERROR,
)

@Composable
internal fun rememberGuideStatusMessage(
    state: GuideStatusState,
    monotonicNowMs: () -> Long = { SystemClock.elapsedRealtime() },
): Int {
    val target = resolveGuideStatusMessage(state)
    var settled by remember(state.runKey) { mutableIntStateOf(target) }
    LaunchedEffect(state.runKey, target) {
        val recoveryMessage = if (isWorkoutRecovery(state, target, settled)) {
            guideResources(state.exercise).trackingRestored
        } else null
        if (isImmediateGuideStatus(state, target)) {
            settled = target
        } else {
            val changedAt = monotonicNowMs()
            delay(STATUS_SETTLE_MS)
            if (monotonicNowMs() >= changedAt) settled = recoveryMessage ?: target
        }
    }

    // Revoke readiness immediately, but hold a neutral message while the actionable
    // reason settles so noisy camera evidence cannot churn the polite live region.
    if (settled == R.string.position_ready && !state.isFramedReady) {
        return if (isImmediateGuideStatus(state, target)) target else R.string.position_tracking_paused
    }
    return settled
}

@StringRes
internal fun resolveGuideStatusMessage(state: GuideStatusState): Int {
    if (state.status in guideCameraErrors) return state.status.message()
    if (state.stage == GuideStatusStage.WORKOUT && state.status == CameraPoseStatus.INITIALIZING) {
        return R.string.position_exercise_started
    }
    if (state.status == CameraPoseStatus.INITIALIZING || state.status == CameraPoseStatus.PAUSED) {
        return state.status.message()
    }

    if (state.stage == GuideStatusStage.COUNTDOWN) return R.string.position_countdown_started
    if (state.starting) return R.string.position_starting

    if (state.stage == GuideStatusStage.POSITION && state.isFramedReady && !state.startAllowed) {
        return R.string.position_start_unavailable
    }
    if (state.stage == GuideStatusStage.WORKOUT && state.issue == null &&
        state.status == CameraPoseStatus.TRACKING && state.trackingAvailable) {
        return R.string.workout_tracking_ready
    }

    if (state.issue == FramingIssue.FRAMED) {
        if (!state.trackingAvailable) return guideResources(state.exercise).startingCue
        if (state.stage == GuideStatusStage.POSITION) {
            return if (state.ready) R.string.position_ready else R.string.position_hold_still
        }
        return R.string.workout_tracking_ready
    }

    return when (state.issue) {
        FramingIssue.NO_RELIABLE_POSE, null -> when {
            state.stage == GuideStatusStage.WORKOUT -> R.string.workout_tracking_lost
            state.status in setOf(CameraPoseStatus.SEARCHING, CameraPoseStatus.TRACKING_LOST) ->
                R.string.position_tracking_paused
            else -> R.string.position_uncertain
        }
        FramingIssue.SHOW_REQUIRED_PARTS -> guideResources(state.exercise).coverage
        FramingIssue.MOVE_BACK -> R.string.position_move_back
        FramingIssue.MOVE_CLOSER -> R.string.position_move_closer
        FramingIssue.CENTER_BODY -> R.string.position_center_body
        FramingIssue.FRAMED -> error("FRAMED is handled above")
    }
}

private fun isImmediateGuideStatus(state: GuideStatusState, target: Int): Boolean =
    state.status in guideCameraErrors || state.status == CameraPoseStatus.INITIALIZING ||
        state.status == CameraPoseStatus.PAUSED || target == R.string.position_ready ||
        target == R.string.position_countdown_started || target == R.string.position_exercise_started

private fun isWorkoutRecovery(state: GuideStatusState, target: Int, previous: Int): Boolean =
    state.stage == GuideStatusStage.WORKOUT && target == R.string.workout_tracking_ready &&
        previous != R.string.workout_tracking_ready && previous != R.string.position_exercise_started

private val GuideStatusState.isFramedReady: Boolean
    get() = stage == GuideStatusStage.POSITION && ready && trackingAvailable && issue == FramingIssue.FRAMED &&
        status == CameraPoseStatus.TRACKING

private const val STATUS_SETTLE_MS = 500L
