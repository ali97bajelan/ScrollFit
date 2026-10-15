package com.example.scrollfit.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.scrollfit.BuildConfig
import com.example.scrollfit.R
import com.example.scrollfit.ScrollFitApplication
import com.example.scrollfit.core.data.CameraChoice
import com.example.scrollfit.core.exercise.Exercise
import com.example.scrollfit.core.exercise.AlternatingKneeRaiseCounter
import com.example.scrollfit.core.exercise.JumpingJackCounter
import com.example.scrollfit.core.exercise.PushUpCounter
import com.example.scrollfit.core.exercise.SquatCounter
import com.example.scrollfit.core.challenge.ChallengeSession
import com.example.scrollfit.core.pose.PreviewProjection
import com.example.scrollfit.platform.pose.CameraPoseSession
import com.example.scrollfit.platform.pose.CameraPoseState
import com.example.scrollfit.platform.pose.CameraPoseStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun CameraScreen(exercise: Exercise, onCancel: () -> Unit, challenge: ChallengeSession? = null,
    onProgress: () -> Unit = {}, onInterrupted: () -> Unit = {}) {
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
    var resumed by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var state by remember { mutableStateOf(CameraPoseState(CameraPoseStatus.PAUSED)) }
    var overlay by remember { mutableStateOf(false) }
    var repetitions by remember(exercise) { mutableIntStateOf(0) }
    val preview = remember(context) { PreviewView(context).apply { layoutDirection = View.LAYOUT_DIRECTION_LTR } }
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

    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permission = hasCameraPermission(context)
                resumed = true
            } else if (event == Lifecycle.Event.ON_PAUSE) resumed = false
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(owner, preview, exercise, camera, permission, loaded, resumed, retry) {
        repetitions = challenge?.repetitions ?: 0
        state = CameraPoseState(if (!permission) CameraPoseStatus.PERMISSION_REQUIRED else CameraPoseStatus.PAUSED)
        var closing = false
        if (challenge != null && !permission) onInterrupted()
        val session = if (permission && loaded && resumed) {
            CameraPoseSession(context, owner, preview, camera, requiredFor(exercise),
                alternativeRequiredLandmarks = if (exercise == Exercise.PUSH_UP) setOf(12, 14, 16, 24) else emptySet(),
                counter = when (exercise) {
                    Exercise.PUSH_UP -> PushUpCounter()
                    Exercise.SQUAT -> SquatCounter()
                    Exercise.JUMPING_JACK -> JumpingJackCounter()
                    Exercise.ALTERNATING_KNEE_RAISE -> AlternatingKneeRaiseCounter()
                }) {
                state = it
                if (challenge == null && (it.status == CameraPoseStatus.PAUSED || it.status == CameraPoseStatus.INITIALIZING)) {
                    repetitions = 0
                }
                it.progress?.let { progress ->
                    if (challenge == null) repetitions = progress.repetitions
                    else if (!closing) {
                        it.timestampMs?.let { timestamp -> challenge.accept(progress, timestamp) }
                        repetitions = challenge.repetitions
                        onProgress()
                    }
                }
                if (challenge != null && !closing && it.status in setOf(CameraPoseStatus.PAUSED,
                        CameraPoseStatus.PERMISSION_REQUIRED, CameraPoseStatus.CAMERA_UNAVAILABLE,
                        CameraPoseStatus.CAMERA_ERROR, CameraPoseStatus.MODEL_ERROR, CameraPoseStatus.INFERENCE_ERROR)) {
                    onInterrupted()
                }
                if (it.status == CameraPoseStatus.PERMISSION_REQUIRED) permission = false
            }
        } else null
        onDispose { closing = true; session?.close() }
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

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.camera_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.camera_privacy))
        Text(stringResource(when (exercise) {
            Exercise.PUSH_UP -> R.string.push_up_counter_guidance
            Exercise.SQUAT -> R.string.squat_counter_guidance
            Exercise.JUMPING_JACK -> R.string.jumping_jack_counter_guidance
            Exercise.ALTERNATING_KNEE_RAISE -> R.string.knee_raise_counter_guidance
        }))
        Text(stringResource(if (challenge != null) R.string.challenge_active_notice else when (exercise) {
            Exercise.PUSH_UP -> R.string.push_up_trial_notice
            Exercise.SQUAT -> R.string.squat_trial_notice
            Exercise.JUMPING_JACK -> R.string.jumping_jack_trial_notice
            Exercise.ALTERNATING_KNEE_RAISE -> R.string.knee_raise_trial_notice
        }))
        val countLabel = when (exercise) {
            Exercise.PUSH_UP -> R.string.push_up_repetitions
            Exercise.SQUAT -> R.string.squat_repetitions
            Exercise.JUMPING_JACK -> R.string.jumping_jack_repetitions
            Exercise.ALTERNATING_KNEE_RAISE -> R.string.knee_raise_repetitions
        }
        Text(stringResource(countLabel, repetitions),
            style = MaterialTheme.typography.headlineSmall)
        if (preferenceError) {
            Text(stringResource(R.string.camera_preference_error))
            Button(onClick = { if (loaded) selectCamera(camera) else loadAttempt++ }, enabled = !saving) {
                Text(stringResource(R.string.camera_retry))
            }
        }
        if (permission && loaded) {
            Box(Modifier.fillMaxWidth().height(360.dp)) {
                AndroidView(factory = { preview }, modifier = Modifier.fillMaxSize())
                if (BuildConfig.DEBUG && overlay && state.sample != null) {
                    PoseOverlay(state, Modifier.fillMaxSize())
                }
            }
        }
        Text(stringResource(state.status.message()), style = MaterialTheme.typography.bodyLarge)
        if (!permission) {
            Button(onClick = { requestPermission.launch(Manifest.permission.CAMERA) }) {
                Text(stringResource(R.string.camera_grant))
            }
            OutlinedButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}")))
            }) { Text(stringResource(R.string.camera_settings)) }
        }
        if (permission && loaded) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { selectCamera(CameraChoice.REAR) },
                    enabled = !saving && camera != CameraChoice.REAR) { Text(stringResource(R.string.camera_rear)) }
                OutlinedButton(onClick = { selectCamera(CameraChoice.FRONT) },
                    enabled = !saving && camera != CameraChoice.FRONT) { Text(stringResource(R.string.camera_front)) }
            }
            Text(stringResource(if (camera == CameraChoice.FRONT) R.string.camera_selected_front else R.string.camera_selected_rear))
            if (state.status in setOf(CameraPoseStatus.MODEL_ERROR, CameraPoseStatus.CAMERA_UNAVAILABLE,
                    CameraPoseStatus.CAMERA_ERROR, CameraPoseStatus.INFERENCE_ERROR)) {
                Button(onClick = { if (challenge != null) onInterrupted(); retry++ }) { Text(stringResource(R.string.camera_retry)) }
            }
            if (BuildConfig.DEBUG) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Switch(checked = overlay, onCheckedChange = { overlay = it })
                    Text(stringResource(R.string.camera_debug_overlay))
                }
            }
        }
        Button(onClick = onCancel) { Text(stringResource(R.string.camera_cancel)) }
    }
}

@Composable
private fun PoseOverlay(state: CameraPoseState, modifier: Modifier) {
    Canvas(modifier) {
        state.sample?.landmarks?.forEach {
            if (it.visibility >= 0.5f && it.presence >= 0.5f && it.x in 0f..1f && it.y in 0f..1f) {
                val p = PreviewProjection.point(it.x, it.y, state.imageWidth, state.imageHeight,
                    size.width, size.height, state.mirrored)
                drawCircle(Color.Green, 4.dp.toPx(), Offset(p.first, p.second))
            }
        }
    }
}

private fun hasCameraPermission(context: android.content.Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun requiredFor(exercise: Exercise): Set<Int> = when (exercise) {
    Exercise.PUSH_UP -> setOf(11, 13, 15, 23)
    Exercise.SQUAT -> setOf(11, 12, 23, 24, 25, 26, 27, 28)
    Exercise.JUMPING_JACK -> setOf(11, 12, 15, 16, 23, 24, 27, 28)
    Exercise.ALTERNATING_KNEE_RAISE -> setOf(11, 12, 23, 24, 25, 26, 27, 28)
}

private fun CameraPoseStatus.message(): Int = when (this) {
    CameraPoseStatus.INITIALIZING -> R.string.camera_initializing
    CameraPoseStatus.SEARCHING -> R.string.camera_searching
    CameraPoseStatus.TRACKING -> R.string.camera_tracking
    CameraPoseStatus.TRACKING_LOST -> R.string.camera_tracking_lost
    CameraPoseStatus.PAUSED -> R.string.camera_paused
    CameraPoseStatus.PERMISSION_REQUIRED -> R.string.camera_permission_required
    CameraPoseStatus.CAMERA_UNAVAILABLE -> R.string.camera_unavailable
    CameraPoseStatus.CAMERA_ERROR -> R.string.camera_error
    CameraPoseStatus.MODEL_ERROR -> R.string.camera_model_error
    CameraPoseStatus.INFERENCE_ERROR -> R.string.camera_inference_error
}
