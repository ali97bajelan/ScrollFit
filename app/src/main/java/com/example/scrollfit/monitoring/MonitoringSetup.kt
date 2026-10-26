package com.example.scrollfit.monitoring

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import com.example.scrollfit.ui.DurationDraft
import com.example.scrollfit.ui.DurationSetting
import com.example.scrollfit.ui.durationDraftMapSaver
import com.example.scrollfit.ui.durationDraftSaver
import com.example.scrollfit.ui.duration
import com.example.scrollfit.core.settings.TargetSettings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.scrollfit.R
import com.example.scrollfit.ScrollFitApplication
import com.example.scrollfit.core.budget.Budget
import com.example.scrollfit.core.data.*
import com.example.scrollfit.core.usage.MonitoringHealth
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect

private val stringSetSaver: Saver<Set<String>, ArrayList<String>> = Saver(
    save = { ArrayList(it) }, restore = { it.toSet() }
)
/** Editable production setup; counters and sessions stay in the transactional repository. */
@Composable
fun MonitoringSetup() {
    val context = LocalContext.current
    val persistence = remember(context) { (context.applicationContext as ScrollFitApplication).persistence }
    val scope = rememberCoroutineScope()
    val status by MonitorRuntime.status.collectAsState()
    var prefs by remember { mutableStateOf<AppPreferences?>(null) }
    var preferenceRetry by remember { mutableStateOf(0) }
    var preferenceError by remember { mutableStateOf(false) }
    var apps by remember { mutableStateOf(emptyList<TargetApp>()) }
    var configs by remember { mutableStateOf(emptyList<TargetConfig>()) }
    var selected by rememberSaveable(stateSaver = stringSetSaver) { mutableStateOf<Set<String>>(emptySet()) }
    var allowances by rememberSaveable(stateSaver = durationDraftMapSaver) { mutableStateOf<Map<String, DurationDraft>>(emptyMap()) }
    var caps by rememberSaveable(stateSaver = durationDraftMapSaver) { mutableStateOf<Map<String, DurationDraft>>(emptyMap()) }
    var query by rememberSaveable { mutableStateOf("") }
    var draftsInitialized by rememberSaveable { mutableStateOf(false) }
    var loadRetry by remember { mutableStateOf(0) }
    var loadFailed by remember { mutableStateOf(false) }
    var accessRevision by remember { mutableStateOf(0) }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) accessRevision++
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    var ceiling by rememberSaveable(stateSaver = durationDraftSaver) {
        mutableStateOf(DurationDraft.fromMilliseconds(3_600_000L))
    }
    var sessionBoundary by rememberSaveable(stateSaver = durationDraftSaver) {
        mutableStateOf(DurationDraft.fromMilliseconds(300_000L))
    }
    var zeroBase by rememberSaveable { mutableStateOf(false) }
    var budgets by remember { mutableStateOf(emptyMap<String, Budget>()) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    var saved by remember { mutableStateOf(false) }

    LaunchedEffect(persistence, preferenceRetry) {
        try {
            persistence.preferences.values.collect { prefs = it; preferenceError = false }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { preferenceError = true; error = R.string.monitor_storage_failed }
    }

    suspend fun refreshBudgets() {
        val engine = persistence.budgetEngine(MonitorAccess.bootIdentity(context))
        budgets = configs.filter { it.selected }.associate { it.packageName to engine.budget(it.packageName) }
    }
    LaunchedEffect(persistence, loadRetry) {
        loading = true; loadFailed = false
        try {
            val current = persistence.preferences.values.first()
            apps = withContext(Dispatchers.IO) { TargetApps.list(context) }
            configs = persistence.repository().targets()
            if (!draftsInitialized) {
                selected = configs.filter { it.selected }.map { it.packageName }.toSet()
                allowances = apps.associate { app -> app.packageName to DurationDraft.fromMilliseconds(
                    configs.find { it.packageName == app.packageName }?.baseAllowanceMs ?: 1_800_000L) }
                caps = apps.associate { app -> app.packageName to DurationDraft.fromMilliseconds(
                    configs.find { it.packageName == app.packageName }?.dailyAwardCapMs ?: 1_200_000L) }
                ceiling = DurationDraft.fromMilliseconds(current.combinedCeilingMs)
                zeroBase = current.zeroBaseEnabled
                sessionBoundary = DurationDraft.fromMilliseconds(current.sessionBoundaryMs)
                draftsInitialized = true
            }
            refreshBudgets()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { loadFailed = true; error = R.string.monitor_action_failed }
        finally { loading = false }
    }
    // Service status changes/resume trigger updates; no UI polling of usage events.
    LaunchedEffect(status, prefs?.monitoringEnabled, accessRevision) {
        if (!loading) try { refreshBudgets() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = R.string.monitor_action_failed }
    }

    LaunchedEffect(accessRevision, loading) {
        if (!loading) try {
            val refreshed = withContext(Dispatchers.IO) { TargetApps.list(context) }
            // Keep drafts; seed only fields for newly available apps.
            allowances = refreshed.associate { app -> app.packageName to DurationDraft.fromMilliseconds(
                configs.find { it.packageName == app.packageName }?.baseAllowanceMs ?: 1_800_000L) } + allowances
            caps = refreshed.associate { app -> app.packageName to DurationDraft.fromMilliseconds(
                configs.find { it.packageName == app.packageName }?.dailyAwardCapMs ?: 1_200_000L) } + caps
            apps = refreshed
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = R.string.monitor_action_failed }
    }

    fun perform(action: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null; saved = false
        scope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = R.string.monitor_action_failed }
            finally { busy = false }
        }
    }
    suspend fun save() {
        val ceilingMs = requireNotNull(ceiling.milliseconds(positive = true))
        val sessionBoundaryMs = requireNotNull(sessionBoundary.milliseconds(positive = true))
        val available = withContext(Dispatchers.IO) { TargetApps.list(context).map { it.packageName }.toSet() }
        val repository = persistence.repository()
        val selectedAvailable = selected.filter { it in available }
        val allowanceValues = selectedAvailable.associateWith {
            requireNotNull(allowances[it]?.milliseconds(positive = false))
        }
        val capValues = selectedAvailable.associateWith {
            requireNotNull(caps[it]?.milliseconds(positive = false))
        }
        val edits = TargetSettings.editsInMilliseconds(repository.targets(), selected, available, allowanceValues, capValues)
        try {
            for (name in edits.removed) repository.removeTarget(name)
            for (config in edits.saved) repository.saveTarget(config)
            persistence.preferences.update { it.copy(combinedCeilingMs = ceilingMs, zeroBaseEnabled = zeroBase,
                sessionBoundaryMs = sessionBoundaryMs) }
            configs = repository.targets(); refreshBudgets(); saved = true
        } finally {
            // Room and DataStore are separate stores: reconcile even after a partially saved edit.
            MonitorRuntime.configurationChanged()
        }
    }
    val fieldsValid = ceiling.milliseconds(positive = true) != null &&
        sessionBoundary.milliseconds(positive = true) != null &&
        selected.all { name -> apps.any { it.packageName == name } || configs.any { it.packageName == name && it.selected } } && selected.filter { name -> apps.any { it.packageName == name } }.all {
            allowances[it]?.milliseconds(positive = false) != null &&
                caps[it]?.milliseconds(positive = false) != null
        }
    // Resume changes are observed so returning from Android permission settings updates this state.
    val usageReady = MonitorAccess.usage(context)
    val accessibilityReady = MonitorAccess.accessibility(context)
    val serviceReady = MonitorRuntime.service != null
    val accessReady = usageReady && accessibilityReady && serviceReady
    val editable = !busy && !loading && !loadFailed && prefs != null && !preferenceError
    val currentHealth = when {
        prefs?.monitoringEnabled != true -> MonitoringHealth.DISABLED
        !MonitorAccess.usage(context) || !MonitorAccess.accessibility(context) -> MonitoringHealth.PERMISSION_MISSING
        MonitorRuntime.service == null -> MonitoringHealth.SERVICE_STOPPED
        else -> status.health
    }
    Column(Modifier.fillMaxSize()) {
      Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.monitor_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.monitor_explanation), style = MaterialTheme.typography.bodyMedium)
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(when (currentHealth) {
                    MonitoringHealth.DISABLED -> R.string.monitoring_disabled
                    MonitoringHealth.PERMISSION_MISSING -> R.string.monitor_permission_missing
                    MonitoringHealth.SERVICE_STOPPED -> R.string.monitor_service_stopped
                    MonitoringHealth.HEALTHY -> R.string.monitor_healthy
                }), style = MaterialTheme.typography.titleMedium)
                if (status.failure != MonitorFailure.NONE) Text(
                    stringResource(status.failure.message()), color = MaterialTheme.colorScheme.error)
                if (status.hasUnmonitoredGap) Text(stringResource(R.string.monitor_gap))
                Text(stringResource(R.string.monitor_reboot_notice), style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                ReadinessLine(R.string.monitor_usage_readiness, usageReady)
                ReadinessLine(R.string.monitor_accessibility_readiness, accessibilityReady)
                ReadinessLine(R.string.monitor_service_readiness, serviceReady)
                if (!usageReady) {
                    TextButton(enabled = !busy,
                        onClick = { perform { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) } }) {
                        Text(stringResource(R.string.monitor_usage_settings))
                    }
                }
                if (!accessibilityReady || !serviceReady) {
                    TextButton(enabled = !busy,
                        onClick = { perform { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } }) {
                        Text(stringResource(R.string.monitor_accessibility_settings))
                    }
                }
            }
        }
        if (preferenceError) TextButton(onClick = {
            preferenceRetry++; MonitorRuntime.configurationChanged()
        }) { Text(stringResource(R.string.monitor_retry)) }
        if (loading) CircularProgressIndicator()
        if (loadFailed) Button(onClick = { loadRetry++; preferenceRetry++ }) { Text(stringResource(R.string.camera_retry)) }
        DurationSetting(
            value = ceiling.value,
            unit = ceiling.unit,
            onValueChange = { ceiling = ceiling.copy(value = it, preservedMilliseconds = null); saved = false },
            onUnitChange = { unit -> ceiling.convertedTo(unit, positive = true)?.let { ceiling = it; saved = false } },
            label = R.string.monitor_ceiling_seconds,
            positive = true,
            enabled = editable,
            preservedMilliseconds = ceiling.preservedMilliseconds,
        )
        DurationSetting(
            value = sessionBoundary.value,
            unit = sessionBoundary.unit,
            onValueChange = { sessionBoundary = sessionBoundary.copy(value = it, preservedMilliseconds = null); saved = false },
            onUnitChange = { unit -> sessionBoundary.convertedTo(unit, positive = true)?.let { sessionBoundary = it; saved = false } },
            label = R.string.session_boundary_seconds,
            positive = true,
            enabled = editable,
            preservedMilliseconds = sessionBoundary.preservedMilliseconds,
        )
        Text(stringResource(R.string.session_settings_explanation))
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(zeroBase, enabled = editable, role = Role.Checkbox,
            onValueChange = { zeroBase = it; saved = false }), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = zeroBase, onCheckedChange = null, enabled = editable)
            Text(stringResource(R.string.monitor_zero_base))
        }
        Text(stringResource(R.string.monitor_select_apps))
        if (!loading && apps.isEmpty()) Text(stringResource(R.string.monitor_no_apps))
        OutlinedButton(onClick = { accessRevision++ }, enabled = !busy && !loading) { Text(stringResource(R.string.product_refresh)) }
        OutlinedTextField(query, { query = it }, label = { Text(stringResource(R.string.product_search)) },
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        val matches = apps.filter { it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }
        if (!loading && apps.isNotEmpty() && matches.isEmpty()) Text(stringResource(R.string.product_no_matches))
        selected.filter { name -> apps.none { it.packageName == name } }.forEach { unavailable ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(unavailable)
                    Text(stringResource(R.string.product_unavailable_target))
                    OutlinedButton(enabled = editable, onClick = {
                        selected = selected - unavailable; saved = false
                    }) { Text(stringResource(R.string.product_remove_target)) }
                }
            }
        }
        matches.forEach { app ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(app.packageName in selected, enabled = editable,
                        role = Role.Checkbox, onValueChange = {
                            selected = if (it) selected + app.packageName else selected - app.packageName; saved = false
                        }), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = app.packageName in selected, onCheckedChange = null, enabled = editable)
                        Text(app.label)
                    }
                    if (app.packageName in selected) {
                        val allowance = allowances[app.packageName] ?: DurationDraft.fromMilliseconds(1_800_000L)
                        DurationSetting(
                            value = allowance.value,
                            unit = allowance.unit,
                            onValueChange = { value -> allowances = allowances + (app.packageName to
                                allowance.copy(value = value, preservedMilliseconds = null)); saved = false },
                            onUnitChange = { unit -> allowance.convertedTo(unit, positive = false)?.let {
                                allowances = allowances + (app.packageName to it); saved = false
                            } },
                            label = R.string.monitor_allowance_seconds,
                            positive = false,
                            enabled = editable,
                            preservedMilliseconds = allowance.preservedMilliseconds,
                        )
                        val cap = caps[app.packageName] ?: DurationDraft.fromMilliseconds(1_200_000L)
                        DurationSetting(
                            value = cap.value,
                            unit = cap.unit,
                            onValueChange = { value -> caps = caps + (app.packageName to
                                cap.copy(value = value, preservedMilliseconds = null)); saved = false },
                            onUnitChange = { unit -> cap.convertedTo(unit, positive = false)?.let {
                                caps = caps + (app.packageName to it); saved = false
                            } },
                            label = R.string.product_cap_seconds,
                            positive = false,
                            enabled = editable,
                            preservedMilliseconds = cap.preservedMilliseconds,
                        )
                    }
                    budgets[app.packageName]?.let { budget -> Text(stringResource(R.string.monitor_budget,
                        duration(budget.baseRemainingMs), duration(budget.earnedRemainingMs), duration(budget.combinedRemainingMs))) }
                    TextButton(onClick = { perform {
                        val launch = requireNotNull(context.packageManager.getLaunchIntentForPackage(app.packageName))
                        context.startActivity(launch)
                    } }) { Text(stringResource(R.string.monitor_open_app)) }
                }
            }
        }
        Text(stringResource(R.string.product_cap_notice))
        Text(stringResource(R.string.monitor_limits_notice))
      }
      Surface(tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!fieldsValid) Text(stringResource(R.string.product_correct_fields), color = MaterialTheme.colorScheme.error)
            if (selected.none { name -> apps.any { it.packageName == name } }) Text(stringResource(R.string.no_targets))
            error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            if (saved) Text(stringResource(R.string.monitor_saved))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(modifier = Modifier.weight(1f), enabled = editable && fieldsValid,
                    onClick = { perform { save() } }) { Text(stringResource(R.string.monitor_save)) }
                Button(modifier = Modifier.weight(1f), enabled = editable && fieldsValid && accessReady &&
                    prefs?.monitoringEnabled == false && selected.any { name -> apps.any { it.packageName == name } },
                    onClick = { perform {
                        require(MonitorAccess.usage(context) && MonitorAccess.accessibility(context) && MonitorRuntime.service != null)
                        save(); persistence.preferences.update { it.copy(monitoringEnabled = true) }
                    } }) { Text(stringResource(R.string.monitor_enable)) }
            }
            if (prefs?.monitoringEnabled == true) {
                OutlinedButton(modifier = Modifier.fillMaxWidth(), enabled = !busy,
                    onClick = { perform {
                        MonitorRuntime.service?.pauseForDisable()
                        persistence.preferences.update { it.copy(monitoringEnabled = false) }
                        preferenceRetry++; MonitorRuntime.configurationChanged()
                    } }) { Text(stringResource(R.string.monitor_disable)) }
            }
        }
      }
    }
}

@Composable
private fun ReadinessLine(@androidx.annotation.StringRes label: Int, ready: Boolean) {
    Text(stringResource(R.string.monitor_readiness_line, stringResource(label),
        stringResource(if (ready) R.string.monitor_ready else R.string.monitor_not_ready)))
}

internal fun MonitorFailure.message(): Int = when (this) {
    MonitorFailure.NONE, MonitorFailure.STARTING -> R.string.monitor_starting
    MonitorFailure.OWNER_BUSY -> R.string.monitor_owner_busy
    MonitorFailure.BOOT_ID -> R.string.monitor_boot_failed
    MonitorFailure.STORAGE -> R.string.monitor_storage_failed
    MonitorFailure.OVERLAY -> R.string.monitor_overlay_failed
    MonitorFailure.QUEUE -> R.string.monitor_queue_failed
    MonitorFailure.INTERRUPTED -> R.string.monitor_interrupted
    MonitorFailure.METADATA -> R.string.monitor_metadata_failed
    MonitorFailure.REGISTRATION -> R.string.monitor_registration_failed
    MonitorFailure.NAVIGATION -> R.string.monitor_navigation_failed
}
