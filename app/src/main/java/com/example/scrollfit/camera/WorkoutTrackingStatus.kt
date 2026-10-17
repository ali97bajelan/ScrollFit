package com.example.scrollfit.camera

import android.os.SystemClock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import com.example.scrollfit.R
import com.example.scrollfit.core.pose.TrackingAnnouncementGate
import com.example.scrollfit.core.pose.WorkoutTracking
import com.example.scrollfit.platform.pose.CameraPoseStatus
import kotlinx.coroutines.delay

private val cameraErrors = setOf(CameraPoseStatus.PERMISSION_REQUIRED, CameraPoseStatus.CAMERA_UNAVAILABLE,
    CameraPoseStatus.CAMERA_ERROR, CameraPoseStatus.MODEL_ERROR, CameraPoseStatus.INFERENCE_ERROR)

@Composable
internal fun rememberWorkoutStatusMessage(runKey: String, status: CameraPoseStatus,
    monotonicNowMs: () -> Long = { SystemClock.elapsedRealtime() }): Int {
    val gate = remember(runKey) { TrackingAnnouncementGate() }
    var statusMessage by remember(runKey) { mutableIntStateOf(R.string.camera_initializing) }
    var announcedError by remember(runKey) { mutableStateOf<CameraPoseStatus?>(null) }
    LaunchedEffect(runKey, status) {
        if (status in cameraErrors) {
            if (announcedError != status) {
                announcedError = status
                gate.update(WorkoutTracking.ERROR, monotonicNowMs())
                statusMessage = status.message()
            }
            return@LaunchedEffect
        }
        announcedError = null
        val category = when (status) {
            CameraPoseStatus.TRACKING -> WorkoutTracking.TRACKING
            CameraPoseStatus.SEARCHING, CameraPoseStatus.TRACKING_LOST -> WorkoutTracking.LOST
            else -> WorkoutTracking.INITIALIZING
        }
        if (category == WorkoutTracking.INITIALIZING) {
            statusMessage = status.message()
            gate.update(category, monotonicNowMs())
            return@LaunchedEffect
        }
        gate.update(category, monotonicNowMs())?.let { statusMessage = it.message() }
        delay(1_000)
        gate.update(category, monotonicNowMs())?.let { statusMessage = it.message() }
    }
    return statusMessage
}

@Composable
internal fun WorkoutTrackingLabel(runKey: String, status: CameraPoseStatus, modifier: Modifier = Modifier) {
    val message = rememberWorkoutStatusMessage(runKey, status)
    Text(stringResource(message), style = MaterialTheme.typography.bodyMedium,
        modifier = modifier.semantics {
            liveRegion = LiveRegionMode.Polite
        })
}

private fun WorkoutTracking.message(): Int = when (this) {
    WorkoutTracking.TRACKING -> R.string.workout_tracking_ready
    WorkoutTracking.LOST, WorkoutTracking.SEARCHING -> R.string.workout_tracking_lost
    WorkoutTracking.INITIALIZING -> R.string.camera_initializing
    WorkoutTracking.ERROR -> R.string.camera_error
}
