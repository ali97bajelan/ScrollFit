package com.example.scrollfit.monitoring

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

/** Minimal production controls needed for T05 phone testing; full product settings remain T14. */
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
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var allowances by remember { mutableStateOf(emptyMap<String, String>()) }
    var ceiling by remember { mutableStateOf("3600") }
    var sessionBoundary by remember { mutableStateOf("300") }
    var zeroBase by remember { mutableStateOf(false) }
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
    LaunchedEffect(persistence) {
        try {
            val current = persistence.preferences.values.first()
            apps = withContext(Dispatchers.IO) { TargetApps.list(context) }
            configs = persistence.repository().targets()
            selected = configs.filter { it.selected }.map { it.packageName }.toSet().intersect(apps.map { it.packageName }.toSet())
            allowances = apps.associate { app -> app.packageName to
                ((configs.find { it.packageName == app.packageName }?.baseAllowanceMs ?: 1_800_000L) / 1000).toString() }
            ceiling = (current.combinedCeilingMs / 1000).toString(); zeroBase = current.zeroBaseEnabled
            sessionBoundary = (current.sessionBoundaryMs / 1000).toString()
            refreshBudgets()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = R.string.monitor_action_failed }
        finally { loading = false }
    }
    // Service status changes/resume trigger updates; no UI polling of usage events.
    LaunchedEffect(status, prefs?.monitoringEnabled) {
        if (!loading) try { refreshBudgets() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = R.string.monitor_action_failed }
    }

    fun perform(action: suspend () -> Unit) {
        scope.launch {
            busy = true; error = null; saved = false
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
        require(selected.all { it in available })
        val values = selected.associateWith { secondsToMs(allowances.getValue(it), positive = false) }
        val repository = persistence.repository()
        val latest = repository.targets()
        for (old in latest) if (old.selected && old.packageName !in selected) repository.removeTarget(old.packageName)
        for ((name, duration) in values) {
            val old = latest.find { it.packageName == name } ?: TargetConfig(name)
            repository.saveTarget(old.copy(selected = true, baseAllowanceMs = duration))
        }
        persistence.preferences.update { it.copy(combinedCeilingMs = ceilingMs, zeroBaseEnabled = zeroBase,
            sessionBoundaryMs = sessionBoundaryMs) }
        configs = repository.targets(); refreshBudgets(); saved = true
        MonitorRuntime.configurationChanged()
    }
    val currentHealth = when {
        prefs?.monitoringEnabled != true -> MonitoringHealth.DISABLED
        !MonitorAccess.usage(context) || !MonitorAccess.accessibility(context) -> MonitoringHealth.PERMISSION_MISSING
        MonitorRuntime.service == null -> MonitoringHealth.SERVICE_STOPPED
        else -> status.health
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
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
        error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
        if (preferenceError) TextButton(onClick = {
            preferenceRetry++; MonitorRuntime.configurationChanged()
        }) { Text(stringResource(R.string.monitor_retry)) }
        if (saved) Text(stringResource(R.string.monitor_saved))
        if (loading) CircularProgressIndicator()
        OutlinedTextField(value = ceiling, onValueChange = { ceiling = it; saved = false },
            label = { Text(stringResource(R.string.monitor_ceiling_seconds)) }, enabled = !busy && !loading, singleLine = true)
        OutlinedTextField(value = sessionBoundary, onValueChange = { sessionBoundary = it; saved = false },
            label = { Text(stringResource(R.string.session_boundary_seconds)) }, enabled = !busy && !loading, singleLine = true)
        Text(stringResource(R.string.session_settings_explanation))
        Row { Checkbox(checked = zeroBase, onCheckedChange = { zeroBase = it }, enabled = !busy && !loading)
            Text(stringResource(R.string.monitor_zero_base), Modifier.padding(top = 12.dp)) }
        Text(stringResource(R.string.monitor_select_apps))
        if (!loading && apps.isEmpty()) Text(stringResource(R.string.monitor_no_apps))
        apps.forEach { app ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.clickable(enabled = !busy) {
                        selected = if (app.packageName in selected) selected - app.packageName else selected + app.packageName
                    }) {
                        Checkbox(checked = app.packageName in selected, onCheckedChange = {
                            selected = if (it) selected + app.packageName else selected - app.packageName
                        }, enabled = !busy)
                        Text(app.label, Modifier.padding(top = 12.dp))
                    }
                    if (app.packageName in selected) OutlinedTextField(
                        value = allowances[app.packageName] ?: "1800",
                        onValueChange = { allowances = allowances + (app.packageName to it); saved = false },
                        label = { Text(stringResource(R.string.monitor_allowance_seconds)) }, singleLine = true, enabled = !busy)
                    budgets[app.packageName]?.let { budget -> Text(stringResource(R.string.monitor_budget,
                        budget.baseRemainingMs / 1000, budget.earnedRemainingMs / 1000, budget.combinedRemainingMs / 1000)) }
                    TextButton(onClick = { perform {
                        val launch = requireNotNull(context.packageManager.getLaunchIntentForPackage(app.packageName))
                        context.startActivity(launch)
                    } }) { Text(stringResource(R.string.monitor_open_app)) }
                }
            }
        }
        Button(enabled = !busy && !loading, onClick = { perform { save() } }) {
            Text(stringResource(R.string.monitor_save))
        }
        Button(enabled = !busy && !loading && prefs?.monitoringEnabled == false && selected.isNotEmpty(), onClick = { perform {
            require(MonitorAccess.usage(context) && MonitorAccess.accessibility(context) && MonitorRuntime.service != null)
            save(); persistence.preferences.update { it.copy(monitoringEnabled = true) }
        } }) { Text(stringResource(R.string.monitor_enable)) }
        Button(enabled = !busy && prefs?.monitoringEnabled == true, onClick = { perform {
            MonitorRuntime.service?.pauseForDisable()
            persistence.preferences.update { it.copy(monitoringEnabled = false) }
            preferenceRetry++; MonitorRuntime.configurationChanged()
        } }) { Text(stringResource(R.string.monitor_disable)) }
        Text(stringResource(R.string.monitor_limits_notice))
    }
}

private fun secondsToMs(text: String, positive: Boolean): Long {
    // Accept Persian/Arabic digits from the Persian keyboard as well as ASCII.
    val normalized = text.trim().map { char -> if (char.isDigit()) Character.getNumericValue(char).digitToChar() else char }.joinToString("")
    val seconds = normalized.toLong()
    require(if (positive) seconds > 0 else seconds >= 0)
    return Math.multiplyExact(seconds, 1000)
}
private fun MonitorFailure.message(): Int = when (this) {
    MonitorFailure.NONE, MonitorFailure.STARTING -> R.string.monitor_starting
    MonitorFailure.OWNER_BUSY -> R.string.monitor_owner_busy
    MonitorFailure.BOOT_ID -> R.string.monitor_boot_failed
    MonitorFailure.STORAGE -> R.string.monitor_storage_failed
    MonitorFailure.OVERLAY -> R.string.monitor_overlay_failed
    MonitorFailure.QUEUE -> R.string.monitor_queue_failed
    MonitorFailure.INTERRUPTED -> R.string.monitor_interrupted
    MonitorFailure.METADATA -> R.string.monitor_metadata_failed
}
