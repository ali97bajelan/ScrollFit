package com.example.scrollfit.ui

import androidx.annotation.StringRes
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.scrollfit.R
import com.example.scrollfit.ScrollFitApplication
import com.example.scrollfit.challenge.label
import com.example.scrollfit.core.data.CameraChoice
import com.example.scrollfit.core.exercise.Exercise
import com.example.scrollfit.core.exercise.RewardRule
import com.example.scrollfit.core.settings.DurationInput
import com.example.scrollfit.core.settings.DurationUnit
import com.example.scrollfit.core.settings.SettingNumbers
import com.example.scrollfit.monitoring.MonitorRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private enum class SettingsAction { MONITORING, SAVE, DELETE_HISTORY }
private enum class SettingsSubmenu(@StringRes val title: Int) {
    MONITORING(R.string.settings_section_monitoring),
    REWARDS(R.string.settings_section_rewards),
    PREFERENCES(R.string.settings_section_preferences),
    DATA_PRIVACY(R.string.settings_section_data_privacy),
}

@Composable
internal fun SettingsScreen(onSetup: () -> Unit, onWelcome: () -> Unit) {
    val context = LocalContext.current
    val persistence = (context.applicationContext as ScrollFitApplication).persistence
    val data = rememberProductData()
    val scope = rememberCoroutineScope()
    var activeAction by remember { mutableStateOf<SettingsAction?>(null) }
    var monitoringFailed by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    var deleteFailed by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf<Int?>(null) }
    var deleteMessage by remember { mutableStateOf<Int?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var submenu by rememberSaveable { mutableStateOf<SettingsSubmenu?>(null) }
    val busy = activeAction != null
    BackHandler(enabled = submenu != null && !confirmDelete) {
        if (!busy) submenu = null
    }
    fun perform(type: SettingsAction, action: suspend () -> Unit) {
        if (activeAction != null) return
        activeAction = type
        monitoringFailed = false
        saveFailed = false
        deleteFailed = false
        saveMessage = null
        deleteMessage = null
        scope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                when (type) {
                    SettingsAction.MONITORING -> monitoringFailed = true
                    SettingsAction.SAVE -> saveFailed = true
                    SettingsAction.DELETE_HISTORY -> deleteFailed = true
                }
            }
            finally { activeAction = null }
        }
    }
    ProductPage(submenu?.title ?: R.string.settings, scrollResetKey = submenu?.title ?: R.string.settings) {
        if (submenu != null) {
            TextButton(onClick = { submenu = null }, enabled = !busy) {
                Text(stringResource(R.string.product_back))
            }
        }
        ProductContent(data) { snapshot ->
            val initial = snapshot.preferences
            val initialRewardDrafts = remember(initial.rewardRules) { initial.rewardRules.mapValues { (_, rule) ->
                DurationDraft.fromMilliseconds(Math.multiplyExact(rule.rewardSeconds, 1_000L))
            } }
            var rules by rememberSaveable { mutableStateOf(initial.rewardRules.mapValues { (item, rule) ->
                rule.repetitions.toString() to initialRewardDrafts.getValue(item).value
            }) }
            var rewardUnits by rememberSaveable { mutableStateOf(initialRewardDrafts.mapValues { (_, draft) -> draft.unit.name }) }
            var rewardPreserved by rememberSaveable { mutableStateOf(initialRewardDrafts.mapNotNull { (item, draft) ->
                draft.preservedMilliseconds?.let { item.name to it }
            }.toMap()) }
            var speech by rememberSaveable { mutableStateOf(initial.speechEnabled) }
            var camera by rememberSaveable { mutableStateOf(initial.camera) }
            var exercise by rememberSaveable { mutableStateOf(initial.lastExercise) }
            var originalRules by remember { mutableStateOf(initial.rewardRules) }
            var speechDirty by rememberSaveable { mutableStateOf(false) }
            var cameraDirty by rememberSaveable { mutableStateOf(false) }
            var exerciseDirty by rememberSaveable { mutableStateOf(false) }
            val valid = rules.all { (exercise, value) ->
                SettingNumbers.positiveInt(value.first) != null &&
                    (rewardPreserved[exercise.name]?.let { it > 0L } ?:
                        (DurationInput.parse(value.second, DurationUnit.valueOf(rewardUnits.getValue(exercise)), true) != null))
            }
            fun saveSettings() {
                val savedRules = rules.mapValues { (item, value) -> RewardRule(
                    requireNotNull(SettingNumbers.positiveInt(value.first)),
                    (rewardPreserved[item.name] ?: requireNotNull(DurationInput.parse(value.second,
                        DurationUnit.valueOf(rewardUnits.getValue(item)), true))) / 1000) }
                val changedRules = savedRules.filter { (item, rule) -> originalRules[item] != rule }
                val savedCamera = camera; val savedExercise = exercise; val savedSpeech = speech
                val changeCamera = cameraDirty; val changeExercise = exerciseDirty; val changeSpeech = speechDirty
                perform(SettingsAction.SAVE) {
                    persistence.preferences.update { latest -> latest.copy(rewardRules = latest.rewardRules + changedRules,
                        speechEnabled = if (changeSpeech) savedSpeech else latest.speechEnabled,
                        camera = if (changeCamera) savedCamera else latest.camera,
                        lastExercise = if (changeExercise) savedExercise else latest.lastExercise) }
                    originalRules = savedRules; speechDirty = false; cameraDirty = false; exerciseDirty = false
                    saveMessage = R.string.monitor_saved
                }
            }
            when (submenu) {
                null -> SettingsContentCard {
                    MonitoringHealthInfo(initial.monitoringEnabled)
                    SettingsMenuButton(SettingsSubmenu.MONITORING.title) { submenu = SettingsSubmenu.MONITORING }
                    SettingsMenuButton(SettingsSubmenu.REWARDS.title) { submenu = SettingsSubmenu.REWARDS }
                    SettingsMenuButton(SettingsSubmenu.PREFERENCES.title) { submenu = SettingsSubmenu.PREFERENCES }
                    SettingsMenuButton(SettingsSubmenu.DATA_PRIVACY.title) { submenu = SettingsSubmenu.DATA_PRIVACY }
                }
                SettingsSubmenu.MONITORING -> SettingsContentCard {
                    MonitoringHealthInfo(initial.monitoringEnabled)
                    Button(onClick = { submenu = null; onSetup() }, enabled = !busy) {
                        Text(stringResource(R.string.monitor_title))
                    }
                    if (initial.monitoringEnabled) {
                        Button(enabled = !busy, onClick = { perform(SettingsAction.MONITORING) {
                            MonitorRuntime.service?.pauseForDisable()
                            persistence.preferences.update { it.copy(monitoringEnabled = false) }
                            MonitorRuntime.configurationChanged(); data.refresh()
                        } }) { Text(stringResource(R.string.monitor_disable)) }
                    }
                    if (activeAction == SettingsAction.MONITORING) CircularProgressIndicator(Modifier.size(24.dp))
                    if (monitoringFailed) Text(stringResource(R.string.product_save_error), color = MaterialTheme.colorScheme.error)
                }
                SettingsSubmenu.REWARDS -> SettingsContentCard {
                    Text(stringResource(R.string.product_defaults_notice))
                    Exercise.entries.forEach { item ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(item.label()), style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.semantics { heading() })
                            val rule = rules.getValue(item)
                            val rewardUnit = DurationUnit.valueOf(rewardUnits.getValue(item))
                            NumberSetting(rule.first, { rules = rules + (item to (it to rule.second)); saveMessage = null },
                                R.string.product_repetitions, SettingNumbers.positiveInt(rule.first) != null, !busy)
                            DurationSetting(
                                value = rule.second,
                                unit = rewardUnit,
                                onValueChange = {
                                    rules = rules + (item to (rule.first to it))
                                    rewardPreserved = rewardPreserved - item.name
                                    saveMessage = null
                                },
                                onUnitChange = { nextUnit ->
                                    val milliseconds = rewardPreserved[item.name]
                                        ?: DurationInput.parse(rule.second, rewardUnit, positive = true)
                                    if (milliseconds != null && milliseconds % nextUnit.milliseconds == 0L) {
                                        val converted = (milliseconds / nextUnit.milliseconds).toString()
                                        rules = rules + (item to (rule.first to converted))
                                        rewardUnits = rewardUnits + (item to nextUnit.name)
                                        rewardPreserved = rewardPreserved - item.name
                                        saveMessage = null
                                    }
                                },
                                label = R.string.product_reward_seconds,
                                positive = true,
                                enabled = !busy,
                                preservedMilliseconds = rewardPreserved[item.name],
                            )
                        }
                    }
                    SettingsSaveAction(
                        valid = valid,
                        busy = busy,
                        saving = activeAction == SettingsAction.SAVE,
                        failed = saveFailed,
                        message = saveMessage,
                        onSave = { saveSettings() },
                    )
                }
                SettingsSubmenu.PREFERENCES -> SettingsContentCard {
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(speech, enabled = !busy, role = Role.Switch,
                        onValueChange = { speech = it; speechDirty = true; saveMessage = null }), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Switch(checked = speech, onCheckedChange = null, enabled = !busy)
                        Text(stringResource(R.string.feedback_enabled), modifier = Modifier.weight(1f))
                    }
                    Text(stringResource(R.string.product_camera), style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() })
                    Column(Modifier.selectableGroup()) { CameraChoice.entries.forEach { item ->
                        SelectionRow(camera == item, !busy, stringResource(if (item == CameraChoice.FRONT) R.string.camera_front else R.string.camera_rear)) {
                            camera = item; cameraDirty = true; saveMessage = null
                        }
                    } }
                    Text(stringResource(R.string.product_last_exercise), style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() })
                    Column(Modifier.selectableGroup()) { Exercise.entries.forEach { item ->
                        SelectionRow(exercise == item, !busy, stringResource(item.label())) {
                            exercise = item; exerciseDirty = true; saveMessage = null
                        }
                    } }
                    SettingsSaveAction(
                        valid = valid,
                        busy = busy,
                        saving = activeAction == SettingsAction.SAVE,
                        failed = saveFailed,
                        message = saveMessage,
                        invalidSettingsLabel = R.string.settings_section_rewards,
                        onFixInvalidSettings = { submenu = SettingsSubmenu.REWARDS },
                        onSave = { saveSettings() },
                    )
                }
                SettingsSubmenu.DATA_PRIVACY -> SettingsContentCard {
                    Text(stringResource(R.string.product_delete_explanation))
                    OutlinedButton(onClick = { confirmDelete = true }, enabled = !busy) {
                        Text(stringResource(R.string.product_delete))
                    }
                    if (activeAction == SettingsAction.DELETE_HISTORY) CircularProgressIndicator(Modifier.size(24.dp))
                    if (deleteFailed) Text(stringResource(R.string.product_save_error), color = MaterialTheme.colorScheme.error)
                    deleteMessage?.let { Text(stringResource(it)) }
                    OutlinedButton(onClick = { submenu = null; onWelcome() }, enabled = !busy) {
                        Text(stringResource(R.string.product_about))
                    }
                }
            }
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.product_delete)) },
        text = { Text(stringResource(R.string.product_delete_explanation)) },
        confirmButton = { TextButton(onClick = {
            confirmDelete = false
            perform(SettingsAction.DELETE_HISTORY) {
                persistence.repository().clearHistory()
                MonitorRuntime.historyCleared()
                deleteMessage = R.string.product_deleted
            }
        }) { Text(stringResource(R.string.product_delete)) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.product_keep)) } })
}

@Composable
private fun SettingsContentCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            content()
        }
    }
}

@Composable
private fun SettingsMenuButton(@StringRes title: Int, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun SettingsSaveAction(
    valid: Boolean,
    busy: Boolean,
    saving: Boolean,
    failed: Boolean,
    message: Int?,
    @StringRes invalidSettingsLabel: Int? = null,
    onFixInvalidSettings: (() -> Unit)? = null,
    onSave: () -> Unit,
) {
    if (!valid) {
        Text(stringResource(R.string.product_correct_fields), color = MaterialTheme.colorScheme.error)
        if (invalidSettingsLabel != null && onFixInvalidSettings != null) {
            TextButton(onClick = onFixInvalidSettings) { Text(stringResource(invalidSettingsLabel)) }
        }
    }
    Button(enabled = !busy && valid, onClick = onSave) { Text(stringResource(R.string.monitor_save)) }
    if (saving) CircularProgressIndicator(Modifier.size(24.dp))
    if (failed) Text(stringResource(R.string.product_save_error), color = MaterialTheme.colorScheme.error)
    message?.let { Text(stringResource(it)) }
}

@Composable
internal fun SelectionRow(selected: Boolean, enabled: Boolean, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
        .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RadioButton(selected, onClick = null, enabled = enabled)
        Text(label, modifier = Modifier.weight(1f))
    }
}
