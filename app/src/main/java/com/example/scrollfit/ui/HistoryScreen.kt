package com.example.scrollfit.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.scrollfit.R
import com.example.scrollfit.challenge.label
import com.example.scrollfit.core.outcomes.*
import java.time.LocalDate
import java.time.ZoneId

@Composable
internal fun HistoryScreen() {
    val data = rememberProductData(history = true)
    ProductPage(R.string.history) {
        ProductContent(data) { snapshot ->
            val records = requireNotNull(snapshot.history)
            val today = LocalDate.parse(snapshot.day)
            val summary = OutcomeAggregator.summarize(records, today, ZoneId.of(snapshot.zoneId))
            Text(stringResource(R.string.product_history_notice))
            Text(stringResource(R.string.outcome_seven_day_title), style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() })
            summary.recordedUsageMs?.let { Text(stringResource(R.string.outcome_recorded_total, duration(it))) }
                ?: Text(stringResource(R.string.outcome_not_recorded))
            summary.recordedAwardedMs?.let { Text(stringResource(R.string.outcome_awarded_total, duration(it))) }
                ?: Text(stringResource(R.string.outcome_awarded_unknown))
            Text(stringResource(R.string.outcome_coverage_note))
            Text(stringResource(R.string.outcome_weekly_activity), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() })
            summary.activity.forEach { (exercise, outcome) ->
                if (outcome.repetitions > 0 || outcome.activeDurationMs > 0) {
                    Text(stringResource(exercise.label()))
                    Text(stringResource(R.string.outcome_activity_detail, number(outcome.repetitions), duration(outcome.activeDurationMs)))
                    if (outcome.exerciseOnlyRepetitions > 0 || outcome.exerciseOnlyDurationMs > 0)
                        Text(stringResource(R.string.outcome_exercise_only, number(outcome.exerciseOnlyRepetitions), duration(outcome.exerciseOnlyDurationMs)))
                }
            }
            Comparison(summary.dailyComparison, R.string.outcome_daily_comparison)
            Comparison(summary.weeklyComparison, R.string.outcome_weekly_comparison)
            summary.recentDays.forEach { day -> DayCard(day, records, snapshot.apps.associate { it.packageName to it.label }) }
            if (summary.olderDays.isNotEmpty()) {
                Text(stringResource(R.string.outcome_older_history), style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() })
                summary.olderDays.forEach { day -> DayCard(day, records, snapshot.apps.associate { it.packageName to it.label }) }
            }
            OutlinedButton(onClick = data::refresh, enabled = !data.loading) { Text(stringResource(R.string.product_refresh)) }
        }
    }
}

@Composable
private fun DayCard(day: DayOutcome, history: com.example.scrollfit.core.data.VisibleHistory, appNames: Map<String, String>) {
    val label = ProductFormatters.date(LocalContext.current, day.day, full = true)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            if (day.inProgress) Text(stringResource(R.string.outcome_in_progress))
            Text(stringResource(when (day.coverage.status) {
                CoverageStatus.COMPLETE -> R.string.outcome_coverage_complete
                CoverageStatus.PARTIAL -> R.string.outcome_coverage_partial
                CoverageStatus.UNKNOWN -> R.string.outcome_coverage_unknown
            }))
            Text(stringResource(R.string.product_usage), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() })
            day.combinedUsageMs?.let { Text(stringResource(R.string.outcome_combined_usage, duration(it))) }
                ?: Text(stringResource(R.string.outcome_not_recorded))
            if (!day.accountingConsistent) Text(stringResource(R.string.outcome_accounting_inconsistent))
            day.perAppUsageMs.forEach { (pkg, ms) -> Text(stringResource(R.string.outcome_app_usage, appNames[pkg] ?: pkg, duration(ms))) }
            Text(stringResource(R.string.product_activity), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() })
            val activities = history.activities.filter { it.day == day.day.toString() }
            if (activities.isEmpty()) Text(stringResource(R.string.product_no_activity))
            day.activity.forEach { (exercise, outcome) ->
                if (outcome.repetitions > 0 || outcome.activeDurationMs > 0) {
                    Text(stringResource(exercise.label()))
                    Text(stringResource(R.string.outcome_activity_detail, number(outcome.repetitions), duration(outcome.activeDurationMs)))
                    if (outcome.exerciseOnlyRepetitions > 0 || outcome.exerciseOnlyDurationMs > 0)
                        Text(stringResource(R.string.outcome_exercise_only, number(outcome.exerciseOnlyRepetitions), duration(outcome.exerciseOnlyDurationMs)))
                }
            }
            Text(stringResource(R.string.product_awarded), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() })
            day.awardedMs?.let { Text(duration(it)) } ?: Text(stringResource(R.string.outcome_awarded_unknown))
        }
    }
}

@Composable
private fun Comparison(value: UsageComparison, title: Int) {
    Text(stringResource(title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
    if (value.blockedBy == null && value.deltaMs != null) {
        val change = when {
            value.deltaMs > 0 -> R.string.outcome_change_more
            value.deltaMs < 0 -> R.string.outcome_change_less
            else -> R.string.outcome_change_same
        }
        Text(stringResource(R.string.outcome_comparison_values,
            stringResource(R.string.outcome_previous), duration(value.previousMs!!),
            stringResource(R.string.outcome_current), duration(value.currentMs!!),
            stringResource(change, duration(kotlin.math.abs(value.deltaMs)))))
    } else {
        Text(stringResource(when (value.blockedBy) {
            ComparisonBlock.SCOPE_CHANGED -> R.string.outcome_scope_changed
            ComparisonBlock.ZONE_CHANGED -> R.string.outcome_zone_changed
            ComparisonBlock.HIDDEN_HISTORY -> R.string.outcome_hidden_history
            ComparisonBlock.INCONSISTENT_ACCOUNTING -> R.string.outcome_inconsistent
            else -> R.string.outcome_insufficient
        }))
    }
}
