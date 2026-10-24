package com.example.scrollfit.challenge

import android.Manifest
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import com.example.scrollfit.ui.SelectionRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.scrollfit.R
import com.example.scrollfit.ScrollFitApplication
import com.example.scrollfit.camera.CameraViewport
import com.example.scrollfit.guide.ExerciseGuideDetails
import com.example.scrollfit.guide.ExerciseGuideContent
import com.example.scrollfit.guide.GuideOverlay
import com.example.scrollfit.guide.PositionContent
import com.example.scrollfit.guide.PositionUiState
import com.example.scrollfit.core.budget.*
import com.example.scrollfit.core.challenge.ChallengePhase
import com.example.scrollfit.core.data.*
import com.example.scrollfit.core.exercise.Exercise
import com.example.scrollfit.platform.pose.CameraPoseState
import com.example.scrollfit.platform.pose.CameraPoseStatus
import androidx.lifecycle.LifecycleEventObserver
import com.example.scrollfit.feedback.AudioStatus
import androidx.compose.ui.platform.testTag
import com.example.scrollfit.ui.ProductFormatters
import kotlinx.coroutines.delay
import java.text.NumberFormat

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ChallengeScreen(mode: ChallengeMode, target: String? = null, initialExercise: Exercise? = null,
    onClose: () -> Unit, registerCancellation: ((() -> Unit)?) -> Unit = {}) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val owner = LocalLifecycleOwner.current
    val accessibility = remember(context) { context.getSystemService(AccessibilityManager::class.java) }
    val flow = remember(mode, target, initialExercise) {
        ChallengeFlow(context.applicationContext as ScrollFitApplication, mode, target, initialExercise)
    }
    var permission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context,
        Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var permissionRequestInFlight by remember { mutableStateOf(false) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionRequestInFlight = false
        permission = it
    }
    fun requestCameraPermission() {
        permissionRequestInFlight = true
        request.launch(Manifest.permission.CAMERA)
    }
    var returnError by remember { mutableStateOf(false) }
    val journey = remember(flow) {
        GuideJourney(flow.scope, flow.monotonicClock, flow::startForGuide, flow::cancelStartedChallenge,
            flow::activateStartedChallenge, flow::challengeIsCurrent, onChallengeExpired = flow::load,
            initiallyPermissionGranted = permission)
    }
    var exitAfterCancellation by remember(flow) { mutableStateOf(false) }
    var showingPositionHelp by remember(flow) { mutableStateOf(false) }
    LaunchedEffect(flow) { flow.load() }
    LaunchedEffect(permission, journey) { journey.permissionChanged(permission) }
    LaunchedEffect(journey.stage, journey.positionRunKey, journey.exercise) {
        if (journey.stage == GuideStage.POSITION && journey.positionRunKey != null) {
            flow.audio.setExercise(journey.exercise ?: flow.exercise)
            flow.audio.controller.beginGuidance()
        }
    }
    LaunchedEffect(journey.stage, journey.countdownRemaining) {
        if (journey.stage == GuideStage.COUNTDOWN) {
            journey.countdownRemaining?.let(flow.audio.controller::countdown)
        }
    }
    LaunchedEffect(owner, flow) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) { delay(100); flow.checkDay(); flow.audio.controller.tick(); journey.tick() }
        }
    }
    LaunchedEffect(flow.result) { if (flow.result != null) journey.showResult() }
    LaunchedEffect(exitAfterCancellation, journey.cancellationPending, journey.cancellationError,
        journey.startInFlight, flow.cancellationPending, flow.error, flow.interrupted) {
        val flowCancellationFailed = flow.interrupted && flow.error
        if (exitAfterCancellation && !journey.cancellationPending && !journey.cancellationError &&
            !journey.startInFlight && !flow.cancellationPending && !flowCancellationFailed) {
            exitAfterCancellation = false
            flow.audio.close()
            onClose()
        }
    }
    DisposableEffect(owner, accessibility, flow) {
        var listening = false
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { enabled ->
            flow.setTouchExplorationEnabled(enabled)
        }
        fun startListening() {
            if (accessibility != null && !listening) {
                accessibility.addTouchExplorationStateChangeListener(listener)
                listening = true
                flow.setTouchExplorationEnabled(accessibility.isTouchExplorationEnabled)
            }
        }
        fun stopListening() {
            if (accessibility != null && listening) {
                accessibility.removeTouchExplorationStateChangeListener(listener)
                listening = false
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) startListening()
            if (event == Lifecycle.Event.ON_PAUSE) stopListening()
        }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) startListening()
        onDispose { stopListening(); owner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(owner, flow) {
        fun interrupt() { journey.interrupt(); flow.interrupt() }
        registerCancellation(::interrupt)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && !permissionRequestInFlight) interrupt()
            if (event == Lifecycle.Event.ON_RESUME) {
                flow.resume()
                permission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { interrupt(); flow.audio.close(); registerCancellation(null); owner.lifecycle.removeObserver(observer) }
    }
    fun close() { journey.interrupt(); flow.interrupt(); onClose() }
    fun requestClose() {
        val journeyCancellation = journey.stage in setOf(GuideStage.STARTING, GuideStage.COUNTDOWN) ||
            journey.startInFlight || journey.cancellationPending || journey.cancellationError
        val flowCancellation = flow.interrupted && (flow.cancellationPending || flow.error)
        if (journeyCancellation || flowCancellation) {
            exitAfterCancellation = true
            if (!journey.cancellationError && !flow.error) {
                journey.interrupt()
                flow.interrupt()
            }
        } else close()
    }
    BackHandler { requestClose() }
    val session = flow.session
    if (journey.stage == GuideStage.LEARN) {
        ExerciseGuideContent(exercise = journey.exercise ?: flow.exercise,
            onContinue = {
                journey.continueToPosition()
                if (!permission) requestCameraPermission()
            }, onClose = { close() }, modifier = Modifier.fillMaxSize())
        return
    }
    if (journey.stage in setOf(GuideStage.POSITION, GuideStage.STARTING, GuideStage.COUNTDOWN)) {
        val positionRunKey = journey.positionRunKey ?: return
        var cameraState by remember(positionRunKey) {
            mutableStateOf(CameraPoseState(CameraPoseStatus.INITIALIZING))
        }
        val countdownText = journey.countdownRemaining?.let { remaining ->
            NumberFormat.getIntegerInstance(configuration.locales[0]).format(remaining)
        }
        val startAllowed = permission && flow.offer is OfferResult.Available && !flow.busy &&
            !flow.cancellationPending && !journey.cancellationPending && !journey.startInFlight
        PositionContent(
            state = PositionUiState(exercise = journey.exercise ?: flow.exercise, camera = flow.camera,
                issue = cameraState.framing?.issue, ready = journey.ready,
                starting = journey.stage == GuideStage.STARTING || journey.cancellationPending,
                status = cameraState.status, startAllowed = startAllowed,
                controlsEnabled = journey.stage == GuideStage.POSITION && !flow.busy && !journey.cancellationPending,
                countdownText = countdownText,
                trackingAvailable = cameraState.progress?.trackingAvailable == true,
                runKey = positionRunKey),
            onStart = { journey.requestStart(startAllowed) },
            onCameraChanged = { choice -> journey.resetPosition(); flow.chooseCamera(choice) },
            onHelp = { showingPositionHelp = true }, onClose = { requestClose() },
            onRequestPermission = { requestCameraPermission() },
            onOpenSettings = {
                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:${context.packageName}")))
            }, onRetryCamera = { journey.resetPosition() },
            recoveryContent = {
                if (journey.positionChanged) Text(stringResource(R.string.position_changed_retry),
                    color = MaterialTheme.colorScheme.primary)
                if (journey.startError || flow.error) Text(stringResource(R.string.challenge_storage_error),
                    color = MaterialTheme.colorScheme.error)
                if (journey.cancellationPending) Text(stringResource(R.string.challenge_cancel_pending))
                if (journey.cancellationError) {
                    Text(stringResource(R.string.challenge_storage_error), color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = journey::retryCancellation,
                        enabled = !journey.cancellationPending, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.camera_retry))
                    }
                }
            },
            helpOpen = showingPositionHelp,
            preview = { previewModifier ->
                Box(previewModifier, contentAlignment = Alignment.Center) {
                    CameraViewport(exercise = journey.exercise ?: flow.exercise, camera = flow.camera,
                        runKey = positionRunKey, modifier = Modifier.fillMaxSize(),
                        overlay = { overlayState ->
                            GuideOverlay(exercise = journey.exercise ?: flow.exercise,
                                imageWidth = overlayState.imageWidth, imageHeight = overlayState.imageHeight,
                                mirrored = overlayState.mirrored, visible = true, modifier = Modifier.fillMaxSize())
                        }) { next ->
                        cameraState = next
                        journey.observePosition(positionRunKey, next)
                        if (journey.stage in setOf(GuideStage.POSITION, GuideStage.STARTING)) {
                            flow.audio.controller.updateGuide(next.framing?.issue, journey.ready, activeWorkout = false)
                        }
                    }
                    countdownText?.let { number ->
                        Surface(shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)) {
                            Text(number, style = MaterialTheme.typography.displayLarge,
                                modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp))
                        }
                    }
                }
            }, modifier = Modifier.fillMaxSize(),
        )
        if (showingPositionHelp) {
            ModalBottomSheet(onDismissRequest = { showingPositionHelp = false }) {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.camera_privacy))
                    ExerciseGuideDetails(journey.exercise ?: flow.exercise)
                    OutlinedButton(onClick = { showingPositionHelp = false },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.workout_help_close))
                    }
                    Button(onClick = { requestClose() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.challenge_close))
                    }
                }
            }
        }
        return
    }
    if (journey.stage == GuideStage.INTERRUPTED && session == null) {
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.challenge_interrupted), style = MaterialTheme.typography.headlineSmall)
            if (journey.cancellationPending || journey.startInFlight) Text(stringResource(R.string.challenge_cancel_pending))
            if (journey.cancellationError) {
                Text(stringResource(R.string.challenge_storage_error), color = MaterialTheme.colorScheme.error)
                Button(onClick = journey::retryCancellation, enabled = !journey.cancellationPending) {
                    Text(stringResource(R.string.camera_retry))
                }
            } else if (!journey.cancellationPending && !journey.startInFlight) {
                Button(onClick = {
                    journey.returnToPrepare()
                    flow.load()
                }) { Text(stringResource(R.string.challenge_new_attempt)) }
            }
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = { close() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.challenge_close))
            }
        }
        return
    }
    if (journey.stage == GuideStage.EXERCISE && session != null && flow.phase in setOf(ChallengePhase.RUNNING, ChallengePhase.SAVING)) {
        val activeSession = session
        var cameraState by remember(activeSession.challenge.id) {
            mutableStateOf(CameraPoseState(CameraPoseStatus.INITIALIZING))
        }
        WorkoutContent(
            modifier = Modifier.fillMaxSize(),
            state = WorkoutUiState(activeSession.challenge.id, flow.repetitions,
                activeSession.challenge.repetitionsRequired, R.string.challenge_progress, cameraState.status,
                exercise = activeSession.challenge.exercise, framingIssue = cameraState.framing?.issue,
                trackingAvailable = cameraState.progress?.trackingAvailable == true),
            speechEnabled = flow.speechEnabled, speechLoaded = flow.speechLoaded,
            audioStatus = flow.audio.status, touchExplorationEnabled = flow.touchExplorationEnabled,
            speechError = flow.speechError, onSpeechChanged = flow::updateSpeechEnabled,
            onSpeechRetry = { flow.updateSpeechEnabled(flow.speechEnabled) }, onEnd = { close() },
            helpContent = {
                Text(stringResource(R.string.camera_privacy))
                ExerciseGuideDetails(activeSession.challenge.exercise)
                Text(stringResource(R.string.challenge_active_notice))
            },
            preview = { previewModifier ->
                CameraViewport(exercise = activeSession.challenge.exercise, camera = flow.camera,
                    runKey = activeSession.challenge.id, modifier = previewModifier,
                    overlay = { overlayState ->
                        GuideOverlay(exercise = activeSession.challenge.exercise,
                            imageWidth = overlayState.imageWidth, imageHeight = overlayState.imageHeight,
                            mirrored = overlayState.mirrored,
                            visible = overlayState.status != CameraPoseStatus.TRACKING ||
                                overlayState.progress?.trackingAvailable != true,
                            modifier = Modifier.fillMaxSize())
                    }) { next ->
                    cameraState = next
                    flow.cameraStateChanged(activeSession.challenge.id, next)
                }
            },
        )
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(if (mode == ChallengeMode.EXERCISE_ONLY) R.string.challenge_only else R.string.challenge_advance),
            style = MaterialTheme.typography.headlineMedium)
        FeedbackControls(flow)
        val result = flow.result
        if (result is CompletionResult.Awarded || result == CompletionResult.ExerciseOnlyCompleted) {
            Text(if (result is CompletionResult.Awarded) stringResource(R.string.feedback_reward,
                ProductFormatters.duration(context, result.award.amountMs))
                else stringResource(R.string.challenge_only_success),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }.testTag("challenge_completion"))
            if (session != null) Text(stringResource(R.string.challenge_activity, session.repetitions,
                ProductFormatters.duration(context, session.activeDurationMs)))
            if (result is CompletionResult.Awarded) {
                Button(onClick = {
                    try {
                        val launch = context.packageManager.getLaunchIntentForPackage(result.award.packageName)
                        if (launch != null) { context.startActivity(launch); close() }
                        else returnError = true
                    } catch (_: RuntimeException) { returnError = true }
                }) { Text(stringResource(R.string.challenge_return)) }
                if (returnError) Text(stringResource(R.string.challenge_return_error))
            }
        } else if (session != null) {
            if (flow.busy) Text(stringResource(R.string.challenge_saving))
            else if (flow.interrupted) Text(stringResource(R.string.challenge_interrupted))
            if (flow.interrupted && flow.cancellationPending) {
                Text(stringResource(R.string.challenge_cancel_pending))
            }
            else if (result is CompletionResult.Refused) Text(stringResource(result.reason.message()))
            if (flow.cameraModelError) {
                Text(stringResource(R.string.camera_model_error))
                OutlinedButton(onClick = {
                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.parse("package:${context.packageName}")))
                }) { Text(stringResource(R.string.camera_app_info)) }
            }
            if (flow.interrupted && !flow.error && !flow.cancellationPending) {
                Button(onClick = {
                    flow.reviewNewChallenge()
                    journey.returnToPrepare()
                }, enabled = !flow.busy) { Text(stringResource(R.string.challenge_new_attempt)) }
            }
            if (flow.error) {
                Text(stringResource(R.string.challenge_storage_error))
                if (flow.interrupted) Button(onClick = flow::retryInterruptionCancellation,
                    enabled = !flow.cancellationPending) {
                    Text(stringResource(R.string.camera_retry))
                } else Button(onClick = flow::complete, enabled = !flow.busy) {
                    Text(stringResource(R.string.camera_retry))
                }
            }
        } else {
            if (mode != ChallengeMode.EXERCISE_ONLY) {
                Text(stringResource(R.string.challenge_target))
                if (!flow.busy && flow.targets.isEmpty()) Text(stringResource(R.string.challenge_no_targets))
                flow.targets.forEach { app ->
                    SelectionRow(flow.target == app.packageName, !flow.busy, app.label) {
                        flow.chooseTarget(app.packageName)
                    }
                }
            }
            if (mode == ChallengeMode.EXERCISE_ONLY || flow.target != null) {
                Text(stringResource(R.string.challenge_exercise))
                Exercise.entries.forEach { exercise ->
                    SelectionRow(flow.exercise == exercise, !flow.busy, stringResource(exercise.label())) {
                        flow.chooseExercise(exercise)
                    }
                }
                when (val quote = flow.offer) {
                    is OfferResult.Available -> {
                        Text(stringResource(R.string.challenge_terms, quote.offer.repetitionsRequired, quote.offer.rewardMs / 1000))
                        if (mode != ChallengeMode.EXERCISE_ONLY) Text(stringResource(R.string.challenge_capacity, quote.offer.awardCapacityMs / 1000))
                        if (quote.offer.reduced) Text(stringResource(R.string.challenge_reduced))
                        if (flow.changed) Text(stringResource(R.string.challenge_offer_changed))
                        Text(stringResource(R.string.challenge_active_notice))
                        Button(onClick = { journey.showGuide(flow.exercise) },
                            enabled = !flow.busy && !flow.cancellationPending) {
                            Text(stringResource(R.string.position_review_guide))
                        }
                    }
                    is OfferResult.Refused -> Text(stringResource(quote.reason.message()))
                    null -> Text(stringResource(R.string.challenge_loading))
                }
            }
            if (flow.error) {
                Text(stringResource(R.string.challenge_storage_error))
                Button(onClick = flow::load, enabled = !flow.busy) { Text(stringResource(R.string.camera_retry)) }
            }
        }
        Button(onClick = { close() }) { Text(stringResource(R.string.challenge_close)) }
    }
}

@Composable
private fun FeedbackControls(flow: ChallengeFlow) {
    val feedbackLabel = stringResource(R.string.feedback_enabled)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Switch(checked = flow.speechEnabled, onCheckedChange = flow::updateSpeechEnabled,
                enabled = flow.speechLoaded, modifier = Modifier.semantics {
                    contentDescription = feedbackLabel
                })
            Text(stringResource(R.string.feedback_enabled))
        }
        Text(stringResource(when (flow.audio.status) {
            AudioStatus.CHECKING -> R.string.feedback_checking
            AudioStatus.VOICE -> R.string.feedback_voice
            AudioStatus.SOUNDS -> R.string.feedback_sounds
            AudioStatus.VISUAL -> R.string.feedback_visual
            AudioStatus.MUTED -> R.string.feedback_muted
        }))
        if (flow.speechError) {
            Text(stringResource(R.string.feedback_save_error))
            OutlinedButton(onClick = { flow.updateSpeechEnabled(flow.speechEnabled) }) {
                Text(stringResource(R.string.feedback_retry))
            }
        }
    }
}

internal fun Exercise.label(): Int = when (this) {
    Exercise.PUSH_UP -> R.string.push_up
    Exercise.SQUAT -> R.string.squat
    Exercise.JUMPING_JACK -> R.string.jumping_jack
    Exercise.ALTERNATING_KNEE_RAISE -> R.string.knee_raise
}
internal fun RewardBlock.message(): Int = when (this) {
    RewardBlock.AWARD_CAP -> R.string.challenge_cap
    RewardBlock.COMBINED_CEILING -> R.string.challenge_ceiling
    RewardBlock.TARGET_UNAVAILABLE -> R.string.challenge_unavailable
    RewardBlock.INCOMPLETE -> R.string.challenge_incomplete
    RewardBlock.INACTIVE, RewardBlock.MISSING -> R.string.challenge_inactive
}
