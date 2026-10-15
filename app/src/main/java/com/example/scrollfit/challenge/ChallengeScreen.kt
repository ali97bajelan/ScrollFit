package com.example.scrollfit.challenge

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.scrollfit.R
import com.example.scrollfit.ScrollFitApplication
import com.example.scrollfit.camera.CameraScreen
import com.example.scrollfit.core.budget.*
import com.example.scrollfit.core.challenge.ChallengePhase
import com.example.scrollfit.core.data.*
import com.example.scrollfit.core.exercise.Exercise
import com.example.scrollfit.feedback.AudioStatus
import com.example.scrollfit.feedback.formatRewardSeconds
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun ChallengeScreen(mode: ChallengeMode, target: String? = null, initialExercise: Exercise? = null,
    onClose: () -> Unit, registerCancellation: ((() -> Unit)?) -> Unit = {}) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val flow = remember(mode, target, initialExercise) {
        ChallengeFlow(context.applicationContext as ScrollFitApplication, mode, target, initialExercise)
    }
    var permission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context,
        Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permission = it }
    var returnError by remember { mutableStateOf(false) }
    LaunchedEffect(flow) { flow.load() }
    LaunchedEffect(owner, flow) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) { delay(250); flow.checkDay(); flow.audio.controller.tick() }
        }
    }
    DisposableEffect(owner, flow) {
        registerCancellation(flow::interrupt)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) flow.interrupt()
            if (event == Lifecycle.Event.ON_RESUME) {
                flow.resume()
                permission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { flow.interrupt(); flow.audio.close(); registerCancellation(null); owner.lifecycle.removeObserver(observer) }
    }
    fun close() { flow.interrupt(); onClose() }
    BackHandler { close() }
    val session = flow.session
    if (session != null && flow.phase == ChallengePhase.RUNNING) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            FeedbackControls(flow)
            Text(stringResource(R.string.challenge_progress, flow.repetitions, session.challenge.repetitionsRequired),
                modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.headlineSmall)
            CameraScreen(session.challenge.exercise, onCancel = { close() }, challenge = session,
                onProgress = flow::progressChanged, onInterrupted = flow::interrupt, onTracking = flow::trackingChanged)
        }
        return
    }
    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(if (mode == ChallengeMode.EXERCISE_ONLY) R.string.challenge_only else R.string.challenge_advance),
            style = MaterialTheme.typography.headlineMedium)
        FeedbackControls(flow)
        val result = flow.result
        if (result is CompletionResult.Awarded || result == CompletionResult.ExerciseOnlyCompleted) {
            Text(if (result is CompletionResult.Awarded) stringResource(R.string.feedback_reward, formatRewardSeconds(result.award.amountMs, Locale.forLanguageTag(stringResource(R.string.feedback_language))))
                else stringResource(R.string.challenge_only_success))
            if (session != null) Text(stringResource(R.string.challenge_activity, session.repetitions, session.activeDurationMs / 1000))
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
            else if (result is CompletionResult.Refused) Text(stringResource(result.reason.message()))
            if (flow.error) {
                Text(stringResource(R.string.challenge_storage_error))
                if (!flow.interrupted) Button(onClick = flow::complete, enabled = !flow.busy) {
                    Text(stringResource(R.string.camera_retry))
                }
            }
        } else {
            if (mode != ChallengeMode.EXERCISE_ONLY) {
                Text(stringResource(R.string.challenge_target))
                if (!flow.busy && flow.targets.isEmpty()) Text(stringResource(R.string.challenge_no_targets))
                flow.targets.forEach { app ->
                    Row {
                        RadioButton(selected = flow.target == app.packageName, enabled = !flow.busy,
                            onClick = { flow.chooseTarget(app.packageName) })
                        Text(app.label)
                    }
                }
            }
            if (mode == ChallengeMode.EXERCISE_ONLY || flow.target != null) {
                Text(stringResource(R.string.challenge_exercise))
                Exercise.entries.forEach { exercise ->
                    Row {
                        RadioButton(selected = flow.exercise == exercise, enabled = !flow.busy,
                            onClick = { flow.chooseExercise(exercise) })
                        Text(stringResource(exercise.label()))
                    }
                }
                when (val quote = flow.offer) {
                    is OfferResult.Available -> {
                        Text(stringResource(R.string.challenge_terms, quote.offer.repetitionsRequired, quote.offer.rewardMs / 1000))
                        if (mode != ChallengeMode.EXERCISE_ONLY) Text(stringResource(R.string.challenge_capacity, quote.offer.awardCapacityMs / 1000))
                        if (quote.offer.reduced) Text(stringResource(R.string.challenge_reduced))
                        if (flow.changed) Text(stringResource(R.string.challenge_offer_changed))
                        Text(stringResource(R.string.challenge_active_notice))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { flow.chooseCamera(CameraChoice.REAR) }, enabled = !flow.busy) {
                                Text(stringResource(R.string.camera_rear))
                            }
                            OutlinedButton(onClick = { flow.chooseCamera(CameraChoice.FRONT) }, enabled = !flow.busy) {
                                Text(stringResource(R.string.camera_front))
                            }
                        }
                        Text(stringResource(if (flow.camera == CameraChoice.FRONT) R.string.camera_selected_front else R.string.camera_selected_rear))
                        if (!permission) {
                            Button(onClick = { request.launch(Manifest.permission.CAMERA) }) { Text(stringResource(R.string.camera_grant)) }
                            OutlinedButton(onClick = {
                                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    android.net.Uri.parse("package:${context.packageName}")))
                            }) { Text(stringResource(R.string.camera_settings)) }
                        }
                        Button(onClick = flow::start, enabled = permission && !flow.busy) { Text(stringResource(R.string.challenge_start)) }
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
            Switch(checked = flow.speechEnabled, onCheckedChange = flow::setSpeechEnabled,
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
            OutlinedButton(onClick = { flow.setSpeechEnabled(flow.speechEnabled) }) {
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
