package com.example.scrollfit.monitoring

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import com.example.scrollfit.ui.NumberSetting
import com.example.scrollfit.ui.duration
import com.example.scrollfit.core.settings.SettingNumbers
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
private val stringMapSaver: Saver<Map<String, String>, ArrayList<String>> = Saver(
    save = { values -> ArrayList(values.flatMap { listOf(it.key, it.value) }) },
    restore = { values -> values.chunked(2).mapNotNull { pair ->
        if (pair.size == 2) pair[0] to pair[1] else null
    }.toMap() }
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
    var selected by rememberSaveable(saver = stringSetSaver) { mutableStateOf<Set<String>>(emptySet()) }
    var allowances by rememberSaveable(saver = stringMapSaver) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var caps by rememberSaveable(saver = stringMapSaver) { mutableStateOf<Map<String, String>>(emptyMap()) }
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
    var ceiling by rememberSaveable { mutableStateOf("3600") }
    var sessionBoundary by rememberSaveable { mutableStateOf("300") }
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
                allowances = apps.associate { app -> app.packageName to
                    ((configs.find { it.packageName == app.packageName }?.baseAllowanceMs ?: 1_800_000L) / 1000).toString() }
                caps = apps.associate { app -> app.packageName to
                    ((configs.find { it.packageName == app.packageName }?.dailyAwardCapMs ?: 1_200_000L) / 1000).toString() }
                ceiling = (current.combinedCeilingMs / 1000).toString()
                zeroBase = current.zeroBaseEnabled
                sessionBoundary = (current.sessionBoundaryMs / 1000).toString()
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
            allowances = refreshed.associate { app -> app.packageName to
                ((configs.find { it.packageName == app.packageName }?.baseAllowanceMs ?: 1_800_000L) / 1000).toString() } + allowances
            caps = refreshed.associate { app -> app.packageName to
                ((configs.find { it.packageName == app.packageName }?.dailyAwardCapMs ?: 1_200_000L) / 1000).toString() } + caps
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
        val ceilingMs = secondsToMs(ceiling, positive = true)
        val sessionBoundaryMs = secondsToMs(sessionBoundary, positive = true)
        val available = withContext(Dispatchers.IO) { TargetApps.list(context).map { it.packageName }.toSet() }
        val repository = persistence.repository()
        val edits = TargetSettings.edits(repository.targets(), selected, available, allowances, caps)
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
    val fieldsValid = SettingNumbers.milliseconds(ceiling, true) != null &&
        SettingNumbers.milliseconds(sessionBoundary, true) != null &&
        selected.all { name -> apps.any { it.packageName == name } || configs.any { it.packageName == name && it.selected } } && selected.filter { name -> apps.any { it.packageName == name } }.all {
            SettingNumbers.milliseconds(allowances[it] ?: "", false) != null &&
                SettingNumbers.milliseconds(caps[it] ?: "", false) != null
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
      Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.monitor_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.monitor_explanation))
        Text(stringResource(when (currentHealth) {
            MonitoringHealth.DISABLED -> R.string.monitoring_disabled
            MonitoringHealth.PERMISSION_MISSING -> R.string.monitor_permission_missing
            MonitoringHealth.SERVICE_STOPPED -> R.string.monitor_service_stopped
            MonitoringHealth.HEALTHY -> R.string.monitor_healthy
        }), style = MaterialTheme.typography.titleMedium)
        if (status.failure != MonitorFailure.NONE) Text(stringResource(status.failure.message()))
        if (status.hasUnmonitoredGap) Text(stringResource(R.string.monitor_gap))
        Text(stringResource(R.string.monitor_reboot_notice))
        TextButton(onClick = { perform { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) } }) {
            Text(stringResource(R.string.monitor_usage_settings))
        }
        TextButton(onClick = { perform { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } }) {
            Text(stringResource(R.string.monitor_accessibility_settings))
        }
        if (preferenceError) TextButton(onClick = {
            preferenceRetry++; MonitorRuntime.configurationChanged()
        }) { Text(stringResource(R.string.monitor_retry)) }
        if (loading) CircularProgressIndicator()
        if (loadFailed) Button(onClick = { loadRetry++; preferenceRetry++ }) { Text(stringResource(R.string.camera_retry)) }
        NumberSetting(ceiling, { ceiling = it; saved = false }, R.string.monitor_ceiling_seconds,
            SettingNumbers.milliseconds(ceiling, true) != null, editable)
        Text(stringResource(R.string.product_seconds_hint), style = MaterialTheme.typography.bodySmall)
        NumberSetting(sessionBoundary, { sessionBoundary = it; saved = false }, R.string.session_boundary_seconds,
            SettingNumbers.milliseconds(sessionBoundary, true) != null, editable)
        Text(stringResource(R.string.product_seconds_hint), style = MaterialTheme.typography.bodySmall)
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
                        NumberSetting(allowances[app.packageName] ?: "1800",
                            { allowances = allowances + (app.packageName to it); saved = false },
                            R.string.monitor_allowance_seconds, SettingNumbers.milliseconds(allowances[app.packageName] ?: "", false) != null,
                            editable, positive = false)
                        Text(stringResource(R.string.product_seconds_hint), style = MaterialTheme.typography.bodySmall)
                        NumberSetting(caps[app.packageName] ?: "1200",
                            { caps = caps + (app.packageName to it); saved = false },
                            R.string.product_cap_seconds, SettingNumbers.milliseconds(caps[app.packageName] ?: "", false) != null,
                            editable, positive = false)
                        Text(stringResource(R.string.product_seconds_hint), style = MaterialTheme.typography.bodySmall)
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
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.monitor_enable_prerequisites), style = MaterialTheme.typography.bodySmall)
            ReadinessLine(R.string.monitor_usage_readiness, usageReady)
            ReadinessLine(R.string.monitor_accessibility_readiness, accessibilityReady)
            ReadinessLine(R.string.monitor_service_readiness, serviceReady)
            if (!fieldsValid) Text(stringResource(R.string.product_correct_fields), color = MaterialTheme.colorScheme.error)
            if (selected.none { name -> apps.any { it.packageName == name } }) Text(stringResource(R.string.no_targets))
            if (!accessReady) Text(stringResource(R.string.product_enable_access))
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
            Button(modifier = Modifier.fillMaxWidth(), enabled = !busy && prefs?.monitoringEnabled == true,
                onClick = { perform {
                    MonitorRuntime.service?.pauseForDisable()
                    persistence.preferences.update { it.copy(monitoringEnabled = false) }
                    preferenceRetry++; MonitorRuntime.configurationChanged()
                } }) { Text(stringResource(R.string.monitor_disable)) }
        }
      }
    }
}

@Composable
private fun ReadinessLine(@androidx.annotation.StringRes label: Int, ready: Boolean) {
    Text(stringResource(R.string.monitor_readiness_line, stringResource(label),
        stringResource(if (ready) R.string.monitor_ready else R.string.monitor_not_ready)))
}

private fun secondsToMs(text: String, positive: Boolean): Long =
    requireNotNull(SettingNumbers.milliseconds(text, positive))

internal fun MonitorFailure.message(): Int = when (this) {
    MonitorFailure.NONE, MonitorFailure.STARTING -> R.string.monitor_starting
    MonitorFailure.OWNER_BUSY -> R.string.monitor_owner_busy
    MonitorFailure.BOOT_ID -> R.string.monitor_boot_failed
    MonitorFailure.STORAGE -> R.string.monitor_storage_failed
    MonitorFailure.OVERLAY -> R.string.monitor_overlay_failed
    MonitorFailure.QUEUE -> R.string.monitor_queue_failed
    MonitorFailure.INTERRUPTED -> R.string.monitor_interrupted
    MonitorFailure.METADATA -> R.string.monitor_metadata_failed
}
