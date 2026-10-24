package com.example.scrollfit.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.scrollfit.BuildConfig
import com.example.scrollfit.R
import com.example.scrollfit.ScrollFitApplication
import com.example.scrollfit.core.data.CameraChoice
import com.example.scrollfit.core.data.Challenge
import com.example.scrollfit.core.data.ChallengeMode
import com.example.scrollfit.core.exercise.Exercise
import com.example.scrollfit.core.budget.StartResult
import com.example.scrollfit.core.challenge.ChallengeSession
import com.example.scrollfit.challenge.GuideJourney
import com.example.scrollfit.challenge.GuideStage
import com.example.scrollfit.challenge.WorkoutContent
import com.example.scrollfit.challenge.WorkoutUiState
import com.example.scrollfit.guide.ExerciseGuideContent
import com.example.scrollfit.guide.ExerciseGuideDetails
import com.example.scrollfit.guide.GuideOverlay
import com.example.scrollfit.guide.PositionContent
import com.example.scrollfit.guide.PositionUiState
import com.example.scrollfit.feedback.AudioStatus
import com.example.scrollfit.platform.pose.CameraPoseState
import com.example.scrollfit.platform.pose.CameraPoseStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.UUID

/** Standalone temporary trial and compatibility wrapper; the active challenge host owns its own layout. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(exercise: Exercise, onCancel: () -> Unit, challenge: ChallengeSession? = null,
    onProgress: () -> Unit = {}, onInterrupted: () -> Unit = {}, onTracking: (Boolean) -> Unit = {},
    onModelError: () -> Unit = {}) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val owner = LocalLifecycleOwner.current
    val app = remember(context) { context.applicationContext as ScrollFitApplication }
    val preferences = remember(app) { app.persistence.preferences }
    val scope = rememberCoroutineScope()
    var camera by remember { mutableStateOf(CameraChoice.REAR) }
    var loaded by remember { mutableStateOf(false) }
    var preferenceError by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var loadAttempt by remember { mutableIntStateOf(0) }
    var retry by remember { mutableIntStateOf(0) }
    var permission by remember { mutableStateOf(hasCameraPermission(context)) }
    var permissionRequestInFlight by remember { mutableStateOf(false) }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionRequestInFlight = false
        permission = it
        retry++
    }
    fun requestCameraPermission() {
        permissionRequestInFlight = true
        requestPermission.launch(Manifest.permission.CAMERA)
    }
    var state by remember { mutableStateOf(CameraPoseState(CameraPoseStatus.PAUSED)) }
    var overlay by remember { mutableStateOf(false) }
    var activeTrialId by remember(exercise) { mutableStateOf<String?>(null) }
    val trialJourney = remember(exercise, challenge) {
        if (challenge != null) null else GuideJourney(
            scope = app.challengeScope,
            clock = app.persistence.monotonicClock,
            start = {
                StartResult.Started(Challenge(UUID.randomUUID().toString(), null,
                    app.persistence.wallClock.localDate().toString(), exercise, ChallengeMode.EXERCISE_ONLY,
                    repetitionsRequired = 1, rewardMs = 0L, startedAtEpochMs = app.persistence.wallClock.epochMillis()))
            },
            cancel = { _ -> Unit },
            activate = { id -> activeTrialId = id },
            challengeIsCurrent = { it.day == app.persistence.wallClock.localDate().toString() },
            initialExercise = exercise,
            initiallyPermissionGranted = permission,
        )
    }
    LaunchedEffect(permission, trialJourney) { trialJourney?.permissionChanged(permission) }
    LaunchedEffect(owner, trialJourney) {
        val journey = trialJourney ?: return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(100)
                journey.tick()
            }
        }
    }
    var showingPositionHelp by remember(exercise) { mutableStateOf(false) }
    var repetitions by remember(exercise, camera, permission, retry, challenge?.challenge?.id,
        trialJourney?.stage, activeTrialId) {
        mutableIntStateOf(challenge?.repetitions ?: 0)
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

    DisposableEffect(owner, context, trialJourney, challenge) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permission = hasCameraPermission(context)
            if (event == Lifecycle.Event.ON_PAUSE && !permissionRequestInFlight) {
                trialJourney?.interrupt()
                if (challenge != null) onInterrupted()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            trialJourney?.interrupt()
            if (challenge != null) onInterrupted()
            owner.lifecycle.removeObserver(observer)
        }
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
    val runKey = if (trialJourney?.stage == GuideStage.EXERCISE) "trial:${activeTrialId.orEmpty()}:$exercise:$camera:$permission:$retry"
        else "$exercise:$camera:$permission:$retry:$loaded:${challenge?.challenge?.id.orEmpty()}"

    fun end() {
        trialJourney?.interrupt()
        onCancel()
    }

    if (trialJourney?.stage == GuideStage.LEARN) {
        ExerciseGuideContent(exercise, onContinue = {
            trialJourney.continueToPosition()
            if (!permission) requestCameraPermission()
        }, onClose = ::end, headerContent = {
            Text(stringResource(R.string.camera_trial_disclosure), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }, modifier = Modifier.fillMaxSize().systemBarsPadding())
        return
    }
    if (trialJourney != null && trialJourney.stage in setOf(GuideStage.POSITION, GuideStage.STARTING, GuideStage.COUNTDOWN)) {
        val positionRunKey = trialJourney.positionRunKey ?: return
        var positionState by remember(positionRunKey) {
            mutableStateOf(CameraPoseState(CameraPoseStatus.INITIALIZING))
        }
        val countdownText = trialJourney.countdownRemaining?.let { remaining ->
            NumberFormat.getIntegerInstance(configuration.locales[0]).format(remaining)
        }
        PositionContent(
            state = PositionUiState(exercise, camera, positionState.framing?.issue, trialJourney.ready,
                starting = trialJourney.stage == GuideStage.STARTING || trialJourney.cancellationPending,
                status = positionState.status, startAllowed = permission && loaded && !saving &&
                    !trialJourney.cancellationPending && !trialJourney.startInFlight,
                controlsEnabled = trialJourney.stage == GuideStage.POSITION && loaded && !saving,
                countdownText = countdownText,
                trackingAvailable = positionState.progress?.trackingAvailable == true,
                runKey = positionRunKey),
            onStart = { trialJourney.requestStart(permission && loaded && !saving) },
            onCameraChanged = { choice -> trialJourney.resetPosition(); selectCamera(choice) },
            onHelp = { showingPositionHelp = true }, onClose = ::end,
            onRequestPermission = ::requestCameraPermission,
            onOpenSettings = { openAppSettings(context) }, onRetryCamera = { trialJourney.resetPosition(); retry++ },
            recoveryContent = {
                if (trialJourney.positionChanged) Text(stringResource(R.string.position_changed_retry),
                    color = MaterialTheme.colorScheme.primary)
                if (preferenceError) Text(stringResource(R.string.camera_preference_error),
                    color = MaterialTheme.colorScheme.error)
                if (trialJourney.startError) Text(stringResource(R.string.challenge_storage_error),
                    color = MaterialTheme.colorScheme.error)
                if (trialJourney.cancellationPending) Text(stringResource(R.string.challenge_cancel_pending))
                if (trialJourney.cancellationError) {
                    Text(stringResource(R.string.challenge_storage_error), color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = trialJourney::retryCancellation,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.camera_retry))
                    }
                }
            },
            headerContent = {
                Text(stringResource(R.string.camera_trial_disclosure), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            helpOpen = showingPositionHelp,
            preview = { previewModifier ->
                Box(previewModifier, contentAlignment = Alignment.Center) {
                    CameraViewport(exercise, camera, positionRunKey, modifier = Modifier.fillMaxSize(), enabled = loaded,
                        overlay = { overlayState ->
                            GuideOverlay(exercise, overlayState.imageWidth, overlayState.imageHeight,
                                overlayState.mirrored, visible = true, modifier = Modifier.fillMaxSize())
                        }) { next ->
                        positionState = next
                        trialJourney.observePosition(positionRunKey, next)
                    }
                    countdownText?.let { number ->
                        Surface(shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)) {
                            Text(number, style = MaterialTheme.typography.displayLarge,
                                modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp))
                        }
                    }
                }
            }, modifier = Modifier.fillMaxSize().systemBarsPadding(),
        )
        if (showingPositionHelp) {
            ModalBottomSheet(onDismissRequest = { showingPositionHelp = false }) {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.camera_privacy))
                    ExerciseGuideDetails(exercise)
                    OutlinedButton(onClick = { showingPositionHelp = false },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.workout_help_close))
                    }
                    Button(onClick = ::end, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.camera_cancel))
                    }
                }
            }
        }
        return
    }
    if (trialJourney != null && trialJourney.stage in setOf(GuideStage.PREPARE, GuideStage.INTERRUPTED, GuideStage.RESULT)) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.challenge_interrupted), style = MaterialTheme.typography.headlineSmall)
            Button(onClick = { trialJourney.returnToPrepare(); trialJourney.showGuide(exercise) }) {
                Text(stringResource(R.string.challenge_new_attempt))
            }
            OutlinedButton(onClick = ::end, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.camera_cancel))
            }
        }
        return
    }

    if (trialJourney != null && trialJourney.stage != GuideStage.EXERCISE) return

    WorkoutContent(
        modifier = Modifier.fillMaxSize().systemBarsPadding(),
        state = WorkoutUiState(runKey, repetitions, null, countLabel, state.status,
            exercise = exercise, framingIssue = state.framing?.issue,
            trackingAvailable = state.progress?.trackingAvailable == true),
        speechEnabled = false, speechLoaded = false, audioStatus = AudioStatus.MUTED,
        touchExplorationEnabled = false, speechError = false,
        onSpeechChanged = {}, onSpeechRetry = {}, onEnd = ::end,
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
                    Button(onClick = { requestCameraPermission() },
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
                if (trialJourney?.stage == GuideStage.EXERCISE && next.status in setOf(CameraPoseStatus.PAUSED,
                        CameraPoseStatus.PERMISSION_REQUIRED, CameraPoseStatus.CAMERA_UNAVAILABLE,
                        CameraPoseStatus.CAMERA_ERROR, CameraPoseStatus.MODEL_ERROR, CameraPoseStatus.INFERENCE_ERROR)) {
                    trialJourney.interrupt()
                }
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
