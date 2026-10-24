package com.example.scrollfit.guide

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.scrollfit.R
import com.example.scrollfit.camera.message
import com.example.scrollfit.core.data.CameraChoice
import com.example.scrollfit.core.exercise.Exercise
import com.example.scrollfit.core.pose.FramingIssue
import com.example.scrollfit.platform.pose.CameraPoseStatus

data class PositionUiState(
    val exercise: Exercise,
    val camera: CameraChoice,
    val issue: FramingIssue?,
    val ready: Boolean,
    val starting: Boolean,
    val status: CameraPoseStatus,
)

/** Position camera content. The caller owns the camera session and its image-aligned overlay. */
@Composable
fun PositionContent(
    state: PositionUiState,
    onStart: () -> Unit,
    onCameraChanged: (CameraChoice) -> Unit,
    onHelp: () -> Unit,
    onClose: () -> Unit,
    preview: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val shortLandscape = maxWidth >= 560.dp && maxHeight < 420.dp
        if (shortLandscape) {
            Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PositionPreview(Modifier.weight(1.15f).fillMaxHeight(), preview)
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PositionHeading(state.exercise)
                    PositionInformation(state, Modifier.weight(1f).verticalScroll(rememberScrollState()))
                    PositionCameraChoices(state.camera, onCameraChanged)
                    PositionActions(state, onStart, onHelp, onClose)
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PositionHeading(state.exercise)
                PositionPreview(Modifier.weight(0.9f).fillMaxWidth(), preview)
                PositionInformation(state,
                    Modifier.weight(0.8f, fill = false).fillMaxWidth().heightIn(max = 180.dp)
                        .verticalScroll(rememberScrollState()))
                PositionCameraChoices(state.camera, onCameraChanged)
                PositionActions(state, onStart, onHelp, onClose)
            }
        }
    }
}

@Composable
private fun PositionHeading(exercise: Exercise) {
    val resources = guideResources(exercise)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.position_title), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(resources.title), style = MaterialTheme.typography.titleLarge)
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(stringResource(resources.viewpoint), modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun PositionPreview(modifier: Modifier, preview: @Composable (Modifier) -> Unit) {
    Surface(modifier, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
        Box(Modifier.fillMaxSize()) { preview(Modifier.fillMaxSize()) }
    }
}

@Composable
private fun PositionInformation(state: PositionUiState, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PositionStatus(state)
        val resources = guideResources(state.exercise)
        Text(stringResource(resources.placement), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(resources.coverage), style = MaterialTheme.typography.bodySmall)
        if (state.starting) {
            Text(stringResource(R.string.position_starting), style = MaterialTheme.typography.bodySmall)
        } else if (state.canStart) {
            Text(stringResource(R.string.position_ready), style = MaterialTheme.typography.bodySmall)
        } else {
            Text(stringResource(R.string.position_start_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PositionStatus(state: PositionUiState) {
    val messageId = positionMessage(state)
    val message = stringResource(messageId)
    val error = state.status in setOf(CameraPoseStatus.PERMISSION_REQUIRED, CameraPoseStatus.CAMERA_UNAVAILABLE,
        CameraPoseStatus.CAMERA_ERROR, CameraPoseStatus.MODEL_ERROR, CameraPoseStatus.INFERENCE_ERROR)
    val color = if (error) MaterialTheme.colorScheme.error else if (state.canStart) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.tertiary
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top) {
            Text(if (state.canStart) "✓" else if (error) "!" else "•", color = color,
                style = MaterialTheme.typography.titleMedium, modifier = Modifier.clearAndSetSemantics {})
            Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
    }
}

@StringRes
private fun positionMessage(state: PositionUiState): Int {
    if (state.status in setOf(CameraPoseStatus.INITIALIZING, CameraPoseStatus.PAUSED,
            CameraPoseStatus.PERMISSION_REQUIRED, CameraPoseStatus.CAMERA_UNAVAILABLE,
            CameraPoseStatus.CAMERA_ERROR, CameraPoseStatus.MODEL_ERROR, CameraPoseStatus.INFERENCE_ERROR)) {
        return state.status.message()
    }
    return when (state.issue) {
        FramingIssue.NO_RELIABLE_POSE, null -> R.string.position_uncertain
        FramingIssue.SHOW_REQUIRED_PARTS -> R.string.position_show_parts
        FramingIssue.MOVE_BACK -> R.string.position_move_back
        FramingIssue.MOVE_CLOSER -> R.string.position_move_closer
        FramingIssue.CENTER_BODY -> R.string.position_center_body
        FramingIssue.FRAMED -> if (state.canStart) R.string.position_ready else R.string.position_hold_still
    }
}

@Composable
private fun PositionCameraChoices(camera: CameraChoice, onCameraChanged: (CameraChoice) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.camera_choose_lens), style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CameraChoiceOption(CameraChoice.FRONT, camera, R.string.camera_front, onCameraChanged,
                Modifier.weight(1f))
            CameraChoiceOption(CameraChoice.REAR, camera, R.string.camera_rear, onCameraChanged,
                Modifier.weight(1f))
        }
    }
}

@Composable
private fun CameraChoiceOption(
    choice: CameraChoice,
    selectedCamera: CameraChoice,
    @StringRes label: Int,
    onCameraChanged: (CameraChoice) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = choice == selectedCamera
    Surface(modifier.selectable(selected, onClick = { onCameraChanged(choice) }, role = Role.RadioButton),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RadioButton(selected = selected, onClick = null)
            Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PositionActions(
    state: PositionUiState,
    onStart: () -> Unit,
    onHelp: () -> Unit,
    onClose: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = onHelp, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.workout_help))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Text(stringResource(R.string.challenge_close))
            }
            Button(onClick = onStart, enabled = state.canStart && !state.starting,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Text(stringResource(R.string.position_start))
            }
        }
    }
}

private val PositionUiState.canStart: Boolean
    get() = ready && issue == FramingIssue.FRAMED && status == CameraPoseStatus.TRACKING
