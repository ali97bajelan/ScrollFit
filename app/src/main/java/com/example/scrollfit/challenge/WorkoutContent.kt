package com.example.scrollfit.challenge

import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.scrollfit.R
import com.example.scrollfit.feedback.AudioStatus
import com.example.scrollfit.platform.pose.CameraPoseState
import com.example.scrollfit.platform.pose.CameraPoseStatus
import com.example.scrollfit.camera.message
import com.example.scrollfit.camera.rememberWorkoutStatusMessage
import kotlinx.coroutines.delay

internal data class WorkoutUiState(
    val runKey: String,
    val repetitions: Int,
    val required: Int?,
    @StringRes val countLabel: Int,
    val status: CameraPoseStatus,
)

/** Bounded workout/trial layout: the preview flexes while the count, status and actions stay visible. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkoutContent(
    state: WorkoutUiState,
    speechEnabled: Boolean,
    speechLoaded: Boolean,
    audioStatus: AudioStatus,
    touchExplorationEnabled: Boolean,
    speechError: Boolean,
    onSpeechChanged: (Boolean) -> Unit,
    onSpeechRetry: () -> Unit,
    onEnd: () -> Unit,
    helpContent: @Composable () -> Unit,
    preview: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    monotonicNowMs: () -> Long = { SystemClock.elapsedRealtime() },
    showAudioControls: Boolean = true,
    @StringRes endLabel: Int = R.string.workout_end,
    @StringRes sheetCloseLabel: Int = R.string.challenge_close,
    header: @Composable ColumnScope.() -> Unit = {},
    extraControls: @Composable ColumnScope.() -> Unit = {},
    scrollExtraControls: Boolean = false,
) {
    val statusMessage = rememberWorkoutStatusMessage(state.runKey, state.status, monotonicNowMs)

    var showingHelp by rememberSaveable(state.runKey) { mutableStateOf(false) }
    val helpFocusRequester = remember(state.runKey) { FocusRequester() }
    var helpWasOpen by remember(state.runKey) { mutableStateOf(false) }
    LaunchedEffect(showingHelp) {
        if (!showingHelp && helpWasOpen) {
            androidx.compose.runtime.withFrameNanos { }
            helpFocusRequester.requestFocus()
        }
        helpWasOpen = showingHelp
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val sideBySide = maxWidth >= 500.dp && maxHeight < 420.dp
        if (sideBySide) {
            Row(Modifier.fillMaxSize().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1.2f).fillMaxHeight().heightIn(min = 96.dp)) { preview(Modifier.fillMaxSize()) }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    header()
                    Counter(state)
                    TrackingStatus(state.status, statusMessage)
                    if (scrollExtraControls) {
                        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                            extraControls()
                        }
                    } else extraControls()
                    Spacer(Modifier.weight(1f))
                    if (showAudioControls) {
                        AudioControls(speechEnabled, speechLoaded, audioStatus, touchExplorationEnabled, onSpeechChanged)
                        if (speechError) SaveAudioError(onSpeechRetry)
                    }
                    WorkoutActions(onHelp = { showingHelp = true }, onEnd = onEnd,
                        focusRequester = helpFocusRequester, endLabel = endLabel)
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                header()
                Counter(state)
                TrackingStatus(state.status, statusMessage)
                if (scrollExtraControls) {
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                        extraControls()
                    }
                } else extraControls()
                if (showAudioControls) {
                    AudioControls(speechEnabled, speechLoaded, audioStatus, touchExplorationEnabled, onSpeechChanged)
                    if (speechError) SaveAudioError(onSpeechRetry)
                }
                Box(Modifier.weight(1f).fillMaxWidth().heightIn(min = 96.dp)) { preview(Modifier.fillMaxSize()) }
                WorkoutActions(onHelp = { showingHelp = true }, onEnd = onEnd,
                    focusRequester = helpFocusRequester, endLabel = endLabel)
            }
        }
    }
    if (showingHelp) {
        ModalBottomSheet(onDismissRequest = { showingHelp = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding()
                .navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                helpContent()
                OutlinedButton(onClick = { showingHelp = false }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.workout_help_close))
                }
                Button(onClick = onEnd, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(sheetCloseLabel))
                }
            }
        }
    }
}

@Composable
private fun Counter(state: WorkoutUiState) {
    val challengeDescription = state.required?.let { required ->
        stringResource(R.string.challenge_progress, state.repetitions, required)
    }
    val countDescription = challengeDescription ?: stringResource(state.countLabel, state.repetitions)
    Text(text = challengeDescription ?: countDescription,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics {
            stateDescription = challengeDescription ?: countDescription
            val target = state.required
            if (target != null && target > 0) {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    state.repetitions.coerceIn(0, target).toFloat(), 0f..target.toFloat(), 0,
                )
            }
        }.testTag("workout_count"))
}

@Composable
private fun TrackingStatus(status: CameraPoseStatus, message: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val color = when {
            status in cameraErrors -> MaterialTheme.colorScheme.error
            status == CameraPoseStatus.TRACKING -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.tertiary
        }
        Surface(Modifier.size(10.dp), shape = MaterialTheme.shapes.small, color = color) { }
        Text(stringResource(message), style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).semantics {
                liveRegion = LiveRegionMode.Polite
            }.testTag("workout_status"))
    }
}

@Composable
private fun AudioControls(enabled: Boolean, loaded: Boolean, status: AudioStatus,
    touchExplorationEnabled: Boolean, onChanged: (Boolean) -> Unit) {
    val label = stringResource(R.string.feedback_enabled)
    val effectiveLabel = stringResource(if (touchExplorationEnabled) R.string.feedback_screen_reader else when (status) {
        AudioStatus.CHECKING -> R.string.feedback_checking
        AudioStatus.VOICE -> R.string.feedback_voice
        AudioStatus.SOUNDS -> R.string.feedback_sounds
        AudioStatus.VISUAL -> R.string.feedback_visual
        AudioStatus.MUTED -> R.string.feedback_muted
    })
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
        value = enabled, enabled = loaded, role = Role.Switch, onValueChange = onChanged,
    ).semantics(mergeDescendants = true) { }.testTag("workout_audio"), verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = enabled, onCheckedChange = null, enabled = loaded, modifier = Modifier.clearAndSetSemantics { })
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(label)
            Text(effectiveLabel, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SaveAudioError(onRetry: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.feedback_save_error), modifier = Modifier.weight(1f))
        TextButton(onClick = onRetry) { Text(stringResource(R.string.feedback_retry)) }
    }
}

@Composable
private fun WorkoutActions(onHelp: () -> Unit, onEnd: () -> Unit, focusRequester: FocusRequester,
    @StringRes endLabel: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onHelp, modifier = Modifier.weight(1f).heightIn(min = 48.dp)
            .focusRequester(focusRequester).testTag("workout_help")) {
            Text(stringResource(R.string.workout_help))
        }
        Button(onClick = onEnd, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("workout_end")) {
            Text(stringResource(endLabel))
        }
    }
}

@Composable
internal fun WorkoutHelpCopy(@StringRes exerciseInstruction: Int) {
    Text(stringResource(R.string.camera_privacy))
    Text(stringResource(exerciseInstruction))
}

internal val cameraErrors = setOf(CameraPoseStatus.PERMISSION_REQUIRED, CameraPoseStatus.CAMERA_UNAVAILABLE,
    CameraPoseStatus.CAMERA_ERROR, CameraPoseStatus.MODEL_ERROR, CameraPoseStatus.INFERENCE_ERROR)
