package com.example.scrollfit.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.scrollfit.BuildConfig
import com.example.scrollfit.R
import com.example.scrollfit.ScrollFitApplication
import com.example.scrollfit.core.data.CameraChoice
import com.example.scrollfit.core.exercise.Exercise
import com.example.scrollfit.core.challenge.ChallengeSession
import com.example.scrollfit.challenge.WorkoutContent
import com.example.scrollfit.challenge.WorkoutUiState
import com.example.scrollfit.feedback.AudioStatus
import com.example.scrollfit.platform.pose.CameraPoseState
import com.example.scrollfit.platform.pose.CameraPoseStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Standalone temporary trial and compatibility wrapper; the active challenge host owns its own layout. */
@Composable
fun CameraScreen(exercise: Exercise, onCancel: () -> Unit, challenge: ChallengeSession? = null,
    onProgress: () -> Unit = {}, onInterrupted: () -> Unit = {}, onTracking: (Boolean) -> Unit = {},
    onModelError: () -> Unit = {}) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val preferences = remember(context) { (context.applicationContext as ScrollFitApplication).persistence.preferences }
    val scope = rememberCoroutineScope()
    var camera by remember { mutableStateOf(CameraChoice.REAR) }
    var loaded by remember { mutableStateOf(false) }
    var preferenceError by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var loadAttempt by remember { mutableIntStateOf(0) }
    var retry by remember { mutableIntStateOf(0) }
    var permission by remember { mutableStateOf(hasCameraPermission(context)) }
    var state by remember { mutableStateOf(CameraPoseState(CameraPoseStatus.PAUSED)) }
    var overlay by remember { mutableStateOf(false) }
    var repetitions by remember(exercise, camera, permission, retry, challenge?.challenge?.id) {
        mutableIntStateOf(challenge?.repetitions ?: 0)
    }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permission = it
        retry++
    }

    LaunchedEffect(preferences, loadAttempt) {
        try {
            camera = preferences.values.first().camera
            loaded = true
            preferences.update { it.copy(lastExercise = exercise) }
            preferenceError = false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            preferenceError = true
        }
    }

    DisposableEffect(owner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permission = hasCameraPermission(context)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    fun selectCamera(choice: CameraChoice) {
        if (challenge != null) onInterrupted()
        camera = choice
        saving = true
        scope.launch {
            try {
                preferences.update { it.copy(camera = choice, lastExercise = exercise) }
                preferenceError = false
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                preferenceError = true
            } finally {
                saving = false
            }
        }
    }

    val countLabel = when (exercise) {
        Exercise.PUSH_UP -> R.string.push_up_repetitions
        Exercise.SQUAT -> R.string.squat_repetitions
        Exercise.JUMPING_JACK -> R.string.jumping_jack_repetitions
        Exercise.ALTERNATING_KNEE_RAISE -> R.string.knee_raise_repetitions
    }
    val instruction = when (exercise) {
        Exercise.PUSH_UP -> R.string.push_up_counter_guidance
        Exercise.SQUAT -> R.string.squat_counter_guidance
        Exercise.JUMPING_JACK -> R.string.jumping_jack_counter_guidance
        Exercise.ALTERNATING_KNEE_RAISE -> R.string.knee_raise_counter_guidance
    }
    val trialNotice = when (exercise) {
        Exercise.PUSH_UP -> R.string.push_up_trial_notice
        Exercise.SQUAT -> R.string.squat_trial_notice
        Exercise.JUMPING_JACK -> R.string.jumping_jack_trial_notice
        Exercise.ALTERNATING_KNEE_RAISE -> R.string.knee_raise_trial_notice
    }
    val runKey = "$exercise:$camera:$permission:$retry:$loaded:${challenge?.challenge?.id.orEmpty()}"

    WorkoutContent(
        modifier = Modifier.fillMaxSize().systemBarsPadding(),
        state = WorkoutUiState(runKey, repetitions, null, countLabel, state.status),
        speechEnabled = false, speechLoaded = false, audioStatus = AudioStatus.MUTED,
        touchExplorationEnabled = false, speechError = false,
        onSpeechChanged = {}, onSpeechRetry = {}, onEnd = onCancel,
        showAudioControls = false, endLabel = R.string.camera_cancel, sheetCloseLabel = R.string.camera_cancel,
        scrollExtraControls = true,
        header = {
            Text(stringResource(R.string.camera_title), style = MaterialTheme.typography.titleMedium)
            if (challenge == null) Text(stringResource(R.string.camera_trial_disclosure),
                style = MaterialTheme.typography.bodySmall)
        },
        extraControls = {
            if (!permission) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { requestPermission.launch(Manifest.permission.CAMERA) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.camera_grant)) }
                    OutlinedButton(onClick = { openAppSettings(context) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.camera_settings)) }
                }
            }
            if (preferenceError) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.camera_preference_error), modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { if (loaded) selectCamera(camera) else loadAttempt++ }, enabled = !saving) {
                        Text(stringResource(R.string.camera_retry))
                    }
                }
            }
            if (state.status in setOf(CameraPoseStatus.MODEL_ERROR, CameraPoseStatus.CAMERA_UNAVAILABLE,
                    CameraPoseStatus.CAMERA_ERROR, CameraPoseStatus.INFERENCE_ERROR)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { if (challenge != null) onInterrupted(); retry++ },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.camera_retry)) }
                    if (state.status == CameraPoseStatus.MODEL_ERROR) {
                        OutlinedButton(onClick = { openAppSettings(context) },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.camera_app_info)) }
                    }
                }
            }
        },
        helpContent = {
            Text(stringResource(R.string.camera_privacy))
            Text(stringResource(instruction))
            Text(stringResource(if (challenge != null) R.string.challenge_active_notice else trialNotice))
            Text(stringResource(if (camera == CameraChoice.FRONT) R.string.camera_selected_front else R.string.camera_selected_rear))
            Text(stringResource(R.string.camera_choose_lens), style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { selectCamera(CameraChoice.REAR) },
                    enabled = loaded && !saving && camera != CameraChoice.REAR,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.camera_rear)) }
                OutlinedButton(onClick = { selectCamera(CameraChoice.FRONT) },
                    enabled = loaded && !saving && camera != CameraChoice.FRONT,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.camera_front)) }
            }
            if (BuildConfig.DEBUG) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = overlay, onCheckedChange = { overlay = it })
                    Text(stringResource(R.string.camera_debug_overlay))
                }
            }
        },
        preview = { previewModifier ->
            CameraViewport(exercise, camera, runKey, previewModifier,
                enabled = loaded, debugOverlay = overlay) { next ->
                state = next
                if (next.status == CameraPoseStatus.MODEL_ERROR && challenge != null) onModelError()
                onTracking(next.status == CameraPoseStatus.TRACKING && next.progress?.trackingAvailable == true)
                next.progress?.let { progress ->
                    if (challenge == null) repetitions = progress.repetitions
                    else {
                        val timestampMs = next.timestampMs
                        if (timestampMs != null) challenge.accept(progress, timestampMs)
                        repetitions = challenge.repetitions
                        onProgress()
                    }
                }
                if (challenge != null && next.status in setOf(CameraPoseStatus.PAUSED, CameraPoseStatus.PERMISSION_REQUIRED,
                        CameraPoseStatus.CAMERA_UNAVAILABLE, CameraPoseStatus.CAMERA_ERROR,
                        CameraPoseStatus.MODEL_ERROR, CameraPoseStatus.INFERENCE_ERROR)) onInterrupted()
            }
        },
    )
}

private fun hasCameraPermission(context: android.content.Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun openAppSettings(context: android.content.Context) {
    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
}
