package com.example.scrollfit.camera

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import com.example.scrollfit.core.exercise.Exercise
import com.example.scrollfit.core.pose.FramingIssue
import com.example.scrollfit.guide.GuideStatusStage
import com.example.scrollfit.guide.GuideStatusState
import com.example.scrollfit.guide.rememberGuideStatusMessage
import com.example.scrollfit.platform.pose.CameraPoseStatus

@Composable
internal fun rememberWorkoutStatusMessage(
    runKey: String,
    status: CameraPoseStatus,
    exercise: Exercise = Exercise.PUSH_UP,
    issue: FramingIssue? = null,
    trackingAvailable: Boolean = status == CameraPoseStatus.TRACKING,
    monotonicNowMs: () -> Long = { android.os.SystemClock.elapsedRealtime() },
): Int = rememberGuideStatusMessage(
    GuideStatusState(
        runKey = runKey,
        exercise = exercise,
        status = status,
        issue = issue,
        ready = trackingAvailable,
        trackingAvailable = trackingAvailable,
        stage = GuideStatusStage.WORKOUT,
    ),
    monotonicNowMs,
)

@Composable
internal fun WorkoutTrackingLabel(
    runKey: String,
    status: CameraPoseStatus,
    modifier: Modifier = Modifier,
    exercise: Exercise = Exercise.PUSH_UP,
    issue: FramingIssue? = null,
    trackingAvailable: Boolean = status == CameraPoseStatus.TRACKING,
) {
    val message = rememberWorkoutStatusMessage(runKey, status, exercise, issue, trackingAvailable)
    Text(stringResource(message), style = MaterialTheme.typography.bodyMedium,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite })
}
