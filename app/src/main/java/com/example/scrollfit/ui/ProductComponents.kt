package com.example.scrollfit.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.example.scrollfit.R
import com.example.scrollfit.core.usage.MonitoringHealth
import com.example.scrollfit.monitoring.*
import androidx.compose.ui.platform.LocalContext
import java.text.NumberFormat

@Composable
internal fun ProductPage(@StringRes title: Int, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() })
        content()
    }
}

@Composable
internal fun ProductContent(data: ProductData, content: @Composable (ProductSnapshot) -> Unit) {
    if (data.loading) CircularProgressIndicator()
    if (data.failed) {
        Text(stringResource(R.string.product_read_error), color = MaterialTheme.colorScheme.error)
        Button(onClick = data::refresh, enabled = !data.loading) { Text(stringResource(R.string.camera_retry)) }
    } else data.snapshot?.let(content)
}

@Composable
internal fun number(value: Number): String = NumberFormat.getNumberInstance(LocalConfiguration.current.locales[0]).apply {
    maximumFractionDigits = 2
}.format(value)

@Composable
internal fun DurationMetric(@StringRes label: Int, milliseconds: Long) {
    Text(stringResource(R.string.product_metric, stringResource(label), duration(milliseconds)))
}

@Composable
internal fun duration(milliseconds: Long): String {
    val absolute = milliseconds.coerceAtLeast(0)
    val hours = absolute / 3_600_000
    val minutes = absolute / 60_000 % 60
    val seconds = (absolute % 60_000) / 1000.0
    return stringResource(R.string.product_duration,
        number(hours), number(minutes), number(seconds))
}

@Composable
internal fun NumberSetting(value: String, onChange: (String) -> Unit, @StringRes label: Int,
    valid: Boolean, enabled: Boolean = true, positive: Boolean = true) {
    OutlinedTextField(value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(label)) }, enabled = enabled, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
        isError = !valid, supportingText = {
            if (!valid) Text(stringResource(if (positive) R.string.product_positive_error else R.string.product_nonnegative_error))
        })
}

@Composable
internal fun MonitoringHealthInfo(enabled: Boolean) {
    val context = LocalContext.current
    val status by MonitorRuntime.status.collectAsState()
    val health = when {
        !enabled -> MonitoringHealth.DISABLED
        !MonitorAccess.usage(context) || !MonitorAccess.accessibility(context) -> MonitoringHealth.PERMISSION_MISSING
        MonitorRuntime.service == null -> MonitoringHealth.SERVICE_STOPPED
        else -> status.health
    }
    Text(stringResource(when (health) {
        MonitoringHealth.DISABLED -> R.string.monitoring_disabled
        MonitoringHealth.PERMISSION_MISSING -> R.string.monitor_permission_missing
        MonitoringHealth.SERVICE_STOPPED -> R.string.monitor_service_stopped
        MonitoringHealth.HEALTHY -> R.string.monitor_healthy
    }), style = MaterialTheme.typography.titleMedium)
    if (status.failure != MonitorFailure.NONE) Text(stringResource(status.failure.message()),
        color = MaterialTheme.colorScheme.error)
    if (status.hasUnmonitoredGap) Text(stringResource(R.string.monitor_gap))
}
