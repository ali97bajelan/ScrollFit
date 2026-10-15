package com.example.scrollfit.ui

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.scrollfit.ScrollFitApplication
import com.example.scrollfit.core.budget.Budget
import com.example.scrollfit.core.data.*
import com.example.scrollfit.monitoring.MonitorRuntime
import com.example.scrollfit.monitoring.TargetApp
import com.example.scrollfit.monitoring.TargetApps
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect

internal data class ProductSnapshot(
    val day: String, val preferences: AppPreferences, val configs: List<TargetConfig>,
    val apps: List<TargetApp>, val budgets: Map<String, Budget>, val combined: CombinedUsage,
    val sessions: Map<String, SessionState>, val usage: Map<String, DailyUsage>, val history: VisibleHistory?,
)

internal class ProductData {
    var snapshot by mutableStateOf<ProductSnapshot?>(null)
    var failed by mutableStateOf(false)
    var loading by mutableStateOf(true)
    var revision by mutableStateOf(0)
    fun refresh() { revision++ }
}

/** Refresh on resume, configuration changes and midnight; never query foreground usage from UI. */
@Composable
internal fun rememberProductData(history: Boolean = false): ProductData {
    val context = LocalContext.current
    val persistence = (context.applicationContext as ScrollFitApplication).persistence
    val owner = LocalLifecycleOwner.current
    val data = remember(history) { ProductData() }
    val status by MonitorRuntime.status.collectAsState()
    DisposableEffect(owner, data) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) data.refresh()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(data, persistence) {
        // Preference changes include edits in other screens; errors stay visible until retry.
        try { persistence.preferences.values.collect { data.refresh() } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { data.failed = true }
    }
    LaunchedEffect(data, data.revision, status) {
        data.loading = true; data.failed = false
        try {
            val repo = persistence.repository()
            val prefs = persistence.preferences.values.first()
            val day = persistence.wallClock.localDate().toString()
            val configs = repo.targets().filter { it.selected }
            val apps = withContext(Dispatchers.IO) { TargetApps.list(context) }
            // Read-only UI engine: this identity never submits usage intervals.
            val engine = persistence.budgetEngine("product-ui")
            val budgets = configs.associate { it.packageName to engine.budget(it.packageName) }
            val combined = repo.combinedUsage(day)
            val sessions = configs.associate { it.packageName to repo.session(it.packageName, day) }
            val usage = configs.associate { it.packageName to repo.dailyUsage(it.packageName, day) }
            val records = if (history) repo.visibleHistory() else null
            check(persistence.wallClock.localDate().toString() == day)
            data.snapshot = ProductSnapshot(day, prefs, configs, apps, budgets, combined, sessions, usage, records)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { data.failed = true }
        finally { data.loading = false }
    }
    LaunchedEffect(data, owner) {
        while (true) {
            delay(1000)
            if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                data.snapshot?.day?.let { it != persistence.wallClock.localDate().toString() } == true && !data.loading && !data.failed) {
                data.refresh()
            }
        }
    }
    return data
}
