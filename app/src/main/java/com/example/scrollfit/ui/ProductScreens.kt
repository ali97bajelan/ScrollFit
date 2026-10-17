package com.example.scrollfit.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.scrollfit.R
import com.example.scrollfit.challenge.label
import com.example.scrollfit.core.budget.BudgetBlock
import com.example.scrollfit.core.exercise.Exercise
import com.example.scrollfit.monitoring.MonitorRuntime
import com.example.scrollfit.ScrollFitApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
internal fun DashboardScreen(onSetup: () -> Unit, onEarn: (String?) -> Unit, onOnly: () -> Unit) {
    val data = rememberProductData()
    val context = LocalContext.current
    var launchError by remember { mutableStateOf(false) }
    ProductPage(R.string.dashboard) {
        ProductContent(data) { snapshot ->
            MonitoringHealthInfo(snapshot.preferences.monitoringEnabled)
            Text(ProductFormatters.date(context, LocalDate.parse(snapshot.day)))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.product_combined_summary), style = MaterialTheme.typography.titleLarge)
                    DurationMetric(R.string.product_combined_limit, snapshot.preferences.combinedCeilingMs)
                    DurationMetric(R.string.product_combined_used, snapshot.combined.consumedMs)
                    DurationMetric(R.string.product_combined_left,
                        (snapshot.preferences.combinedCeilingMs - snapshot.combined.consumedMs).coerceAtLeast(0))
                }
            }
            Text(stringResource(R.string.product_recorded_notice))
            Button(onClick = onSetup) { Text(stringResource(R.string.monitor_title)) }
            if (snapshot.configs.isEmpty()) Text(stringResource(R.string.no_targets))
            snapshot.configs.forEach { config ->
                val budget = snapshot.budgets.getValue(config.packageName)
                val app = snapshot.apps.find { it.packageName == config.packageName }
                var expandedAwards by rememberSaveable(config.packageName) { mutableStateOf(false) }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(app?.label ?: config.packageName, style = MaterialTheme.typography.titleLarge)
                        val usage = snapshot.usage.getValue(config.packageName)
                        Text(stringResource(R.string.product_usable), style = MaterialTheme.typography.titleMedium)
                        Text(duration(budget.usableMs), style = MaterialTheme.typography.headlineMedium)
                        DurationMetric(R.string.product_app_used, usage.baseConsumedMs + usage.earnedConsumedMs)
                        DurationMetric(R.string.product_base_left, budget.baseRemainingMs)
                        DurationMetric(R.string.product_earned_left, budget.earnedRemainingMs)
                        TextButton(onClick = { expandedAwards = !expandedAwards }) {
                            Text(stringResource(if (expandedAwards) R.string.product_hide_awards else R.string.product_show_awards))
                        }
                        if (expandedAwards) {
                            DurationMetric(R.string.product_awarded, budget.awardedMs)
                            DurationMetric(R.string.product_award_limit, config.dailyAwardCapMs)
                            DurationMetric(R.string.product_award_left, budget.awardCapacityMs)
                        }
                        val blockedBy = budget.blockedBy
                        if (blockedBy != null) Text(stringResource(when (blockedBy) {
                            BudgetBlock.COMBINED_CEILING -> R.string.monitor_combined_exhausted
                            BudgetBlock.APP_BUDGET -> R.string.product_app_exhausted
                            BudgetBlock.TARGET_UNAVAILABLE -> R.string.challenge_unavailable
                        }))
                        if (snapshot.sessions[config.packageName]?.promptPending == true) Text(stringResource(R.string.product_session_pending))
                        val canEarn = app != null && budget.awardCapacityMs > 0 && budget.combinedRemainingMs > 0
                        if (!canEarn) Text(stringResource(when {
                            app == null -> R.string.challenge_unavailable
                            budget.combinedRemainingMs <= 0 -> R.string.challenge_ceiling
                            else -> R.string.challenge_cap
                        }))
                        Button(onClick = { onEarn(config.packageName) }, enabled = canEarn) {
                            Text(stringResource(R.string.challenge_advance))
                        }
                        OutlinedButton(enabled = app != null, onClick = {
                            try {
                                val launch = requireNotNull(context.packageManager.getLaunchIntentForPackage(config.packageName))
                                context.startActivity(launch)
                            } catch (_: RuntimeException) { launchError = true }
                        }) { Text(stringResource(R.string.monitor_open_app)) }
                    }
                }
            }
            if (launchError) Text(stringResource(R.string.challenge_return_error), color = MaterialTheme.colorScheme.error)
            Button(onClick = onOnly) { Text(stringResource(R.string.challenge_only)) }
            OutlinedButton(onClick = data::refresh, enabled = !data.loading) { Text(stringResource(R.string.product_refresh)) }
        }
    }
}

@Composable
internal fun ExerciseCatalog(onExercise: (Exercise) -> Unit, onEarn: () -> Unit) {
    val data = rememberProductData()
    ProductPage(R.string.exercises) {
        ProductContent(data) { snapshot ->
            Text(stringResource(R.string.product_defaults_notice))
            Exercise.entries.forEach { exercise ->
                val rule = snapshot.preferences.rewardRules.getValue(exercise)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(exercise.label()), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.product_rule, number(rule.repetitions), number(rule.rewardSeconds)))
                        Text(stringResource(when (exercise) {
                            Exercise.PUSH_UP -> R.string.push_up_guidance
                            Exercise.SQUAT -> R.string.squat_guidance
                            else -> R.string.front_guidance
                        }))
                        Button(onClick = { onExercise(exercise) }) { Text(stringResource(R.string.challenge_only)) }
                    }
                }
            }
            Button(onClick = onEarn) { Text(stringResource(R.string.challenge_advance)) }
        }
    }
}

@Composable
internal fun WelcomeScreen(onSetup: () -> Unit, onEnter: () -> Unit) {
    val context = LocalContext.current
    val persistence = (context.applicationContext as ScrollFitApplication).persistence
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    ProductPage(R.string.welcome) {
        Text(stringResource(R.string.welcome_body))
        Text(stringResource(R.string.monitor_service_description))
        Text(stringResource(R.string.product_onboarding_limits))
        Text(stringResource(R.string.product_defaults_notice))
        Text(stringResource(R.string.monitor_limits_notice))
        Button(onClick = onSetup, enabled = !busy) { Text(stringResource(R.string.monitor_title)) }
        Button(enabled = !busy, onClick = {
            busy = true; failed = false
            scope.launch {
                try { persistence.preferences.update { it.copy(onboardingCompleted = true) }; onEnter() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { failed = true }
                finally { busy = false }
            }
        }) { Text(stringResource(R.string.enter_dashboard)) }
        if (failed) Text(stringResource(R.string.product_save_error), color = MaterialTheme.colorScheme.error)
    }
}
