package com.example.scrollfit.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.view.View
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.scrollfit.BuildConfig
import com.example.scrollfit.core.data.CameraChoice
import com.example.scrollfit.core.exercise.AlternatingKneeRaiseCounter
import com.example.scrollfit.core.exercise.Exercise
import com.example.scrollfit.core.exercise.ExerciseGuides
import com.example.scrollfit.core.exercise.JumpingJackCounter
import com.example.scrollfit.core.exercise.PushUpCounter
import com.example.scrollfit.core.exercise.SquatCounter
import com.example.scrollfit.core.pose.FramingEvaluator
import com.example.scrollfit.core.pose.PreviewProjection
import com.example.scrollfit.platform.pose.CameraPoseSession
import com.example.scrollfit.platform.pose.CameraPoseState
import com.example.scrollfit.platform.pose.CameraPoseStatus
import androidx.compose.ui.unit.dp

/** Camera-session boundary that allows ownership to be tested without mutable global hooks. */
internal fun interface CameraRunFactory {
    fun open(context: Context, owner: LifecycleOwner, preview: PreviewView, exercise: Exercise,
        camera: CameraChoice, onState: (CameraPoseState) -> Unit): AutoCloseable
}

private data class PreviewGeometry(val width: Int, val height: Int, val mirrored: Boolean)

private object ProductionCameraRunFactory : CameraRunFactory {
    override fun open(context: Context, owner: LifecycleOwner, preview: PreviewView, exercise: Exercise,
        camera: CameraChoice, onState: (CameraPoseState) -> Unit): AutoCloseable {
        val groups = ExerciseGuides.forExercise(exercise).requiredLandmarkGroups
        return CameraPoseSession(context, owner, preview, camera, groups.first(),
            alternativeRequiredLandmarks = groups.drop(1).firstOrNull().orEmpty(),
            framingEvaluator = { sample, timestamp -> FramingEvaluator.evaluate(exercise, sample, timestamp) },
            counter = counterFor(exercise), onState = onState)
    }
}

/** Owns one preview and one fresh exercise counter for the lifetime of [runKey]. */
@Composable
internal fun CameraViewport(
    exercise: Exercise,
    camera: CameraChoice,
    runKey: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    debugOverlay: Boolean = false,
    runFactory: CameraRunFactory = ProductionCameraRunFactory,
    overlay: @Composable (CameraPoseState) -> Unit = {},
    onState: (CameraPoseState) -> Unit,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val preview = remember(context) {
        PreviewView(context).apply { layoutDirection = View.LAYOUT_DIRECTION_LTR }
    }
    var permission by remember(context) { mutableStateOf(hasCameraPermission(context)) }
    var resumed by remember(owner) {
        mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    resumed = true
                    permission = hasCameraPermission(context)
                }
                Lifecycle.Event.ON_PAUSE -> resumed = false
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    CameraViewportSurface(context, owner, preview, exercise, camera, runKey, modifier, enabled,
        permission, resumed, debugOverlay, runFactory, overlay, onState)
}

/** Session-owning surface with lifecycle inputs explicit for focused fake-camera tests. */
@Composable
internal fun CameraViewportSurface(
    context: Context,
    owner: LifecycleOwner,
    preview: PreviewView,
    exercise: Exercise,
    camera: CameraChoice,
    runKey: String,
    modifier: Modifier,
    enabled: Boolean,
    permission: Boolean,
    resumed: Boolean,
    debugOverlay: Boolean,
    runFactory: CameraRunFactory,
    overlay: @Composable (CameraPoseState) -> Unit = {},
    onState: (CameraPoseState) -> Unit,
) {
    val currentCallback by rememberUpdatedState(onState)
    var state by remember(runKey, exercise, camera) { mutableStateOf(CameraPoseState(CameraPoseStatus.INITIALIZING)) }
    var lastGeometry by remember(runKey, exercise, camera) { mutableStateOf<PreviewGeometry?>(null) }
    DisposableEffect(owner, preview, exercise, camera, runKey, enabled, permission, resumed, runFactory) {
        var active = true
        val deliver: (CameraPoseState) -> Unit = { next ->
            if (active) {
                if (next.imageWidth > 0 && next.imageHeight > 0) {
                    lastGeometry = PreviewGeometry(next.imageWidth, next.imageHeight, next.mirrored)
                }
                state = next
                currentCallback(next)
            }
        }
        val session = when {
            !enabled || !resumed -> {
                deliver(CameraPoseState(CameraPoseStatus.PAUSED))
                null
            }
            !permission -> {
                deliver(CameraPoseState(CameraPoseStatus.PERMISSION_REQUIRED))
                null
            }
            else -> runFactory.open(context, owner, preview, exercise, camera, deliver)
        }
        onDispose {
            active = false
            session?.close()
        }
    }

    Box(modifier) {
        if (enabled && resumed && permission) {
            AndroidView(factory = { preview }, modifier = Modifier.fillMaxSize())
            if (BuildConfig.DEBUG && debugOverlay && state.sample != null) PoseOverlay(state, Modifier.fillMaxSize())
            val overlayState = if (state.imageWidth <= 0 || state.imageHeight <= 0) {
                val geometry = lastGeometry
                if (geometry != null && state.status in setOf(CameraPoseStatus.SEARCHING, CameraPoseStatus.TRACKING_LOST)) {
                    state.copy(imageWidth = geometry.width, imageHeight = geometry.height, mirrored = geometry.mirrored)
                } else state
            } else state
            overlay(overlayState)
        }
    }
}

private fun counterFor(exercise: Exercise) = when (exercise) {
    Exercise.PUSH_UP -> PushUpCounter()
    Exercise.SQUAT -> SquatCounter()
    Exercise.JUMPING_JACK -> JumpingJackCounter()
    Exercise.ALTERNATING_KNEE_RAISE -> AlternatingKneeRaiseCounter()
}

private fun hasCameraPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

internal fun CameraPoseStatus.message(): Int = when (this) {
    CameraPoseStatus.INITIALIZING -> com.example.scrollfit.R.string.camera_initializing
    CameraPoseStatus.SEARCHING -> com.example.scrollfit.R.string.camera_searching
    CameraPoseStatus.TRACKING -> com.example.scrollfit.R.string.camera_tracking
    CameraPoseStatus.TRACKING_LOST -> com.example.scrollfit.R.string.camera_tracking_lost
    CameraPoseStatus.PAUSED -> com.example.scrollfit.R.string.camera_paused
    CameraPoseStatus.PERMISSION_REQUIRED -> com.example.scrollfit.R.string.camera_permission_required
    CameraPoseStatus.CAMERA_UNAVAILABLE -> com.example.scrollfit.R.string.camera_unavailable
    CameraPoseStatus.CAMERA_ERROR -> com.example.scrollfit.R.string.camera_error
    CameraPoseStatus.MODEL_ERROR -> com.example.scrollfit.R.string.camera_model_error
    CameraPoseStatus.INFERENCE_ERROR -> com.example.scrollfit.R.string.camera_inference_error
}

@Composable
private fun PoseOverlay(state: CameraPoseState, modifier: Modifier) {
    Canvas(modifier) {
        state.sample?.landmarks?.forEach { landmark ->
            if (landmark.visibility >= 0.5f && landmark.presence >= 0.5f && landmark.x in 0f..1f && landmark.y in 0f..1f) {
                val point = PreviewProjection.point(landmark.x, landmark.y, state.imageWidth, state.imageHeight,
                    size.width, size.height, state.mirrored)
                drawCircle(Color.Green, 4.dp.toPx(), Offset(point.first, point.second))
            }
        }
    }
}
