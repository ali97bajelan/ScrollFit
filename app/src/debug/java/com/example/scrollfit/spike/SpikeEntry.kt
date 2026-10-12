package com.example.scrollfit.spike

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.example.scrollfit.R
import com.example.scrollfit.core.spike.SpikeCountdown
import kotlinx.coroutines.delay

private data class SpikeScreenState(
    val selected: String?, val enabled: Boolean, val usage: Boolean, val accessibility: Boolean,
    val connected: Boolean, val remainingMillis: Long, val statusResource: Int,
)

private fun screenState(context: Context, store: SpikeStore): SpikeScreenState {
    val usage = SpikeAccess.hasUsageAccess(context)
    val accessibility = SpikeAccess.accessibilityEnabled(context)
    val service = SpikeRuntime.service
    val status = when {
        !store.enabled -> R.string.spike_disabled
        !usage -> R.string.spike_usage_missing
        !accessibility -> R.string.spike_accessibility_missing
        service == null -> R.string.spike_service_stopped
        service.errorResource != null -> requireNotNull(service.errorResource)
        service.blocked -> R.string.spike_blocked_title
        service.targetForeground -> R.string.spike_consuming
        else -> R.string.spike_waiting
    }
    return SpikeScreenState(
        store.targetPackage, store.enabled, usage, accessibility, service != null,
        service?.remainingMillis ?: (SpikeCountdown.ALLOWANCE_MILLIS - store.consumedMillis), status,
    )
}

@Composable
fun SpikeEntry() {
    val context = LocalContext.current
    val store = remember(context) { SpikeStore(context) }
    val lifecycle = remember(context) { context.lifecycleOwner().lifecycle }
    var resumed by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var state by remember { mutableStateOf(screenState(context, store)) }
    var apps by remember { mutableStateOf(SpikeApps.list(context)) }
    var actionError by remember { mutableStateOf<Int?>(null) }
    fun update() { state = screenState(context, store) }
    fun perform(action: () -> Unit) {
        actionError = null
        runCatching(action).onFailure { actionError = R.string.spike_action_failed }
        update()
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            resumed = event == Lifecycle.Event.ON_RESUME ||
                (event != Lifecycle.Event.ON_PAUSE && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(resumed) {
        if (resumed) {
            apps = SpikeApps.list(context)
            while (true) { update(); delay(1_000) }
        }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.spike_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.spike_explanation))
        Text(stringResource(state.statusResource), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.spike_remaining, (state.remainingMillis + 999) / 1_000))
        Text(stringResource(R.string.spike_access_status,
            stringResource(if (state.usage) R.string.spike_granted else R.string.spike_missing),
            stringResource(if (state.accessibility) R.string.spike_granted else R.string.spike_missing),
            stringResource(if (state.connected) R.string.spike_connected else R.string.spike_disconnected)))
        actionError?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
        TextButton(onClick = { perform { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) } }) {
            Text(stringResource(R.string.spike_usage_settings))
        }
        TextButton(onClick = { perform { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } }) {
            Text(stringResource(R.string.spike_accessibility_settings))
        }
        Text(stringResource(R.string.spike_restricted_settings))
        Text(stringResource(R.string.spike_select_target), style = MaterialTheme.typography.titleMedium)
        if (apps.isEmpty()) Text(stringResource(R.string.spike_no_apps))
        apps.forEach { app ->
            Row(Modifier.fillMaxWidth().clickable(enabled = !state.enabled) {
                perform { store.selectTarget(app.packageName) }
            }) {
                RadioButton(selected = state.selected == app.packageName,
                    onClick = { perform { store.selectTarget(app.packageName) } }, enabled = !state.enabled)
                Column(Modifier.padding(12.dp)) {
                    Text(app.label)
                    Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Button(
            enabled = !state.enabled && state.usage && state.accessibility && state.connected &&
                apps.any { it.packageName == state.selected },
            onClick = { perform {
                check(SpikeAccess.hasUsageAccess(context) && SpikeAccess.accessibilityEnabled(context) && SpikeRuntime.service != null)
                store.startNewTest()
            } },
        ) { Text(stringResource(R.string.spike_start)) }
        Text(stringResource(R.string.spike_reset_notice))
        Button(enabled = state.enabled, onClick = { perform { store.stop() } }) {
            Text(stringResource(R.string.spike_stop))
        }
        Button(enabled = state.enabled && apps.any { it.packageName == state.selected }, onClick = { perform {
            val launch = context.packageManager.getLaunchIntentForPackage(requireNotNull(state.selected))
            check(launch != null)
            context.startActivity(launch)
        } }) { Text(stringResource(R.string.spike_open_target)) }
    }
}

private fun Context.lifecycleOwner(): LifecycleOwner = when (this) {
    is LifecycleOwner -> this
    is ContextWrapper -> baseContext.lifecycleOwner()
    else -> error("The spike screen requires an Activity lifecycle")
}
