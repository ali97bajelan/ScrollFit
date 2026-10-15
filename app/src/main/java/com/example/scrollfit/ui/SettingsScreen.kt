package com.example.scrollfit.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.scrollfit.R
import com.example.scrollfit.ScrollFitApplication
import com.example.scrollfit.challenge.label
import com.example.scrollfit.core.data.CameraChoice
import com.example.scrollfit.core.exercise.Exercise
import com.example.scrollfit.core.exercise.RewardRule
import com.example.scrollfit.core.settings.SettingNumbers
import com.example.scrollfit.monitoring.MonitorRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun SettingsScreen(onSetup: () -> Unit, onWelcome: () -> Unit) {
    val context = LocalContext.current
    val persistence = (context.applicationContext as ScrollFitApplication).persistence
    val data = rememberProductData()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Int?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    fun perform(action: suspend () -> Unit) {
        if (busy) return
        busy = true; failed = false; message = null
        scope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
            finally { busy = false }
        }
    }
    ProductPage(R.string.settings) {
        ProductContent(data) { snapshot ->
            val initial = snapshot.preferences
            var rules by rememberSaveable { mutableStateOf(initial.rewardRules.mapValues { (_, r) -> r.repetitions.toString() to r.rewardSeconds.toString() }) }
            var speech by rememberSaveable { mutableStateOf(initial.speechEnabled) }
            var camera by rememberSaveable { mutableStateOf(initial.camera) }
            var exercise by rememberSaveable { mutableStateOf(initial.lastExercise) }
            var originalRules by remember { mutableStateOf(initial.rewardRules) }
            var speechDirty by rememberSaveable { mutableStateOf(false) }
            var cameraDirty by rememberSaveable { mutableStateOf(false) }
            var exerciseDirty by rememberSaveable { mutableStateOf(false) }
            val valid = rules.values.all { SettingNumbers.positiveInt(it.first) != null && SettingNumbers.milliseconds(it.second, true) != null }
            MonitoringHealthInfo(initial.monitoringEnabled)
            Button(onClick = onSetup, enabled = !busy) { Text(stringResource(R.string.monitor_title)) }
            Button(enabled = !busy && initial.monitoringEnabled, onClick = { perform {
                MonitorRuntime.service?.pauseForDisable()
                persistence.preferences.update { it.copy(monitoringEnabled = false) }
                MonitorRuntime.configurationChanged(); data.refresh()
            } }) { Text(stringResource(R.string.monitor_disable)) }
            Text(stringResource(R.string.product_defaults_notice))
            Exercise.entries.forEach { item ->
                Text(stringResource(item.label()), style = MaterialTheme.typography.titleLarge)
                val rule = rules.getValue(item)
                NumberSetting(rule.first, { rules = rules + (item to (it to rule.second)); message = null },
                    R.string.product_repetitions, SettingNumbers.positiveInt(rule.first) != null, !busy)
                NumberSetting(rule.second, { rules = rules + (item to (rule.first to it)); message = null },
                    R.string.product_reward_seconds, SettingNumbers.milliseconds(rule.second, true) != null, !busy)
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(speech, enabled = !busy, role = Role.Switch,
                onValueChange = { speech = it; speechDirty = true; message = null }), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Switch(checked = speech, onCheckedChange = null, enabled = !busy)
                Text(stringResource(R.string.feedback_enabled))
            }
            Text(stringResource(R.string.product_camera), style = MaterialTheme.typography.titleMedium)
            Column(Modifier.selectableGroup()) { CameraChoice.entries.forEach { item ->
                SelectionRow(camera == item, !busy, stringResource(if (item == CameraChoice.FRONT) R.string.camera_front else R.string.camera_rear)) {
                    camera = item; cameraDirty = true; message = null
                }
            } }
            Text(stringResource(R.string.product_last_exercise), style = MaterialTheme.typography.titleMedium)
            Column(Modifier.selectableGroup()) { Exercise.entries.forEach { item ->
                SelectionRow(exercise == item, !busy, stringResource(item.label())) { exercise = item; exerciseDirty = true; message = null }
            } }
            if (!valid) Text(stringResource(R.string.product_correct_fields), color = MaterialTheme.colorScheme.error)
            Button(enabled = !busy && valid, onClick = {
                val savedRules = rules.mapValues { (_, value) -> RewardRule(
                    requireNotNull(SettingNumbers.positiveInt(value.first)),
                    requireNotNull(SettingNumbers.milliseconds(value.second, true)) / 1000) }
                val changedRules = savedRules.filter { (item, rule) -> originalRules[item] != rule }
                val savedCamera = camera; val savedExercise = exercise; val savedSpeech = speech
                val changeCamera = cameraDirty; val changeExercise = exerciseDirty; val changeSpeech = speechDirty
                perform {
                    persistence.preferences.update { latest -> latest.copy(rewardRules = latest.rewardRules + changedRules,
                        speechEnabled = if (changeSpeech) savedSpeech else latest.speechEnabled,
                        camera = if (changeCamera) savedCamera else latest.camera,
                        lastExercise = if (changeExercise) savedExercise else latest.lastExercise) }
                    originalRules = savedRules; speechDirty = false; cameraDirty = false; exerciseDirty = false
                    message = R.string.monitor_saved
                }
            }) { Text(stringResource(R.string.monitor_save)) }
            Text(stringResource(R.string.product_delete_explanation))
            OutlinedButton(onClick = { confirmDelete = true }, enabled = !busy) { Text(stringResource(R.string.product_delete)) }
            OutlinedButton(onClick = onWelcome, enabled = !busy) { Text(stringResource(R.string.product_about)) }
        }
        if (busy) CircularProgressIndicator()
        if (failed) Text(stringResource(R.string.product_save_error), color = MaterialTheme.colorScheme.error)
        message?.let { Text(stringResource(it)) }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.product_delete)) },
        text = { Text(stringResource(R.string.product_delete_explanation)) },
        confirmButton = { TextButton(onClick = {
            confirmDelete = false
            perform { persistence.repository().clearHistory(); message = R.string.product_deleted }
        }) { Text(stringResource(R.string.product_delete)) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.product_keep)) } })
}

@Composable
internal fun SelectionRow(selected: Boolean, enabled: Boolean, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
        .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RadioButton(selected, onClick = null, enabled = enabled)
        Text(label)
    }
}
