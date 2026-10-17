package com.example.scrollfit.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.scrollfit.R
import com.example.scrollfit.challenge.label
import com.example.scrollfit.core.outcomes.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

internal data class HistoryPresentation(
    val summary: OutcomeSummary,
    val activityDays: Set<String>,
    val appNames: Map<String, String>,
    val sourceRevision: Int = 0,
)

internal fun prepareHistory(snapshot: ProductSnapshot): HistoryPresentation {
    val history = requireNotNull(snapshot.history)
    val today = LocalDate.parse(snapshot.day)
    val zone = ZoneId.of(snapshot.zoneId)
    return HistoryPresentation(
        summary = OutcomeAggregator.summarize(history, today, zone),
        activityDays = history.activities.asSequence().map { it.day }.toSet(),
        appNames = snapshot.apps.associate { it.packageName to it.label },
    )
}

@Composable
internal fun HistoryScreen() {
    HistoryHost(rememberProductData(history = true), prepare = ::prepareHistory)
}

/** Owns per-snapshot background preparation and discards delivery from stale generations. */
@Composable
internal fun HistoryHost(
    data: ProductData,
    modifier: Modifier = Modifier,
    prepare: suspend (ProductSnapshot) -> HistoryPresentation,
) {
    val snapshot = data.snapshot
    val identity = SnapshotIdentity(snapshot)
    var presentation by remember(data.revision, identity) { mutableStateOf<HistoryPresentation?>(null) }
    var preparationFailed by remember(data.revision, identity) { mutableStateOf(false) }
    LaunchedEffect(data, data.revision, identity, data.loading, data.failed, prepare) {
        val source = snapshot ?: return@LaunchedEffect
        if (data.loading || data.failed) return@LaunchedEffect
        val revision = data.revision
        preparationFailed = false
        try {
            val result = withContext(Dispatchers.Default) { prepare(source) }.copy(sourceRevision = revision)
            if (data.revision == revision && data.snapshot === source && !data.loading && !data.failed) {
                presentation = result
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (data.revision == revision && data.snapshot === source && !data.loading && !data.failed) {
                preparationFailed = true
            }
        }
    }

    Column(modifier.fillMaxSize()) {
        if (data.loading || (snapshot != null && presentation == null && !data.failed && !preparationFailed)) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (data.failed || preparationFailed) {
            Text(stringResource(R.string.product_read_error), color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            Button(onClick = data::refresh, enabled = !data.loading,
                modifier = Modifier.padding(horizontal = 20.dp).heightIn(min = 48.dp)) {
                Text(stringResource(R.string.camera_retry))
            }
        }
        presentation?.takeIf {
            it.sourceRevision == data.revision && !data.loading && !data.failed
        }?.let { prepared ->
            key(identity) {
                HistoryContent(prepared, data.loading, data::refresh, Modifier.weight(1f))
            }
        }
    }
}

/** Reference identity avoids structural comparisons of large history snapshots in effect keys. */
private class SnapshotIdentity(private val snapshot: ProductSnapshot?) {
    override fun equals(other: Any?) = other is SnapshotIdentity && other.snapshot === snapshot
    override fun hashCode() = System.identityHashCode(snapshot)
}

@Composable
internal fun HistoryContent(presentation: HistoryPresentation, loading: Boolean, onRefresh: () -> Unit,
    modifier: Modifier = Modifier) {
    val summary = presentation.summary
    LazyColumn(modifier.fillMaxSize().testTag("history_list"),
        contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "history-title", contentType = "heading") {
            Text(stringResource(R.string.history), style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() })
        }
        item(key = "notice", contentType = "heading") {
            Text(stringResource(R.string.product_history_notice))
        }
        item(key = "week-title", contentType = "heading") {
            Text(stringResource(R.string.outcome_seven_day_title), style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() })
        }
        item(key = "week-totals", contentType = "summary") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                summary.recordedUsageMs?.let { Text(stringResource(R.string.outcome_recorded_total, duration(it))) }
                    ?: Text(stringResource(R.string.outcome_not_recorded))
                summary.recordedAwardedMs?.let { Text(stringResource(R.string.outcome_awarded_total, duration(it))) }
                    ?: Text(stringResource(R.string.outcome_awarded_unknown))
                Text(stringResource(R.string.outcome_coverage_note))
            }
        }
        item(key = "activity-title", contentType = "heading") {
            Text(stringResource(R.string.outcome_weekly_activity), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() })
        }
        item(key = "activity-summary", contentType = "summary") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                summary.activity.forEach { (exercise, outcome) ->
                    if (outcome.repetitions > 0 || outcome.activeDurationMs > 0) {
                        Text(stringResource(exercise.label()))
                        Text(stringResource(R.string.outcome_activity_detail,
                            number(outcome.repetitions), duration(outcome.activeDurationMs)))
                        if (outcome.exerciseOnlyRepetitions > 0 || outcome.exerciseOnlyDurationMs > 0) {
                            Text(stringResource(R.string.outcome_exercise_only,
                                number(outcome.exerciseOnlyRepetitions), duration(outcome.exerciseOnlyDurationMs)))
                        }
                    }
                }
            }
        }
        item(key = "daily-comparison", contentType = "summary") {
            Comparison(summary.dailyComparison, R.string.outcome_daily_comparison)
        }
        item(key = "weekly-comparison", contentType = "summary") {
            Comparison(summary.weeklyComparison, R.string.outcome_weekly_comparison)
        }
        items(summary.recentDays, key = { "day:${it.day}" }, contentType = { "day" }) { day ->
            Box(Modifier.testTag("history_card_${day.day}")) {
                DayCard(day, day.day.toString() in presentation.activityDays, presentation.appNames)
            }
        }
        if (summary.olderDays.isNotEmpty()) {
            item(key = "older-history", contentType = "heading") {
                Text(stringResource(R.string.outcome_older_history), style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() })
            }
            items(summary.olderDays, key = { "day:${it.day}" }, contentType = { "day" }) { day ->
                Box(Modifier.testTag("history_card_${day.day}")) {
                    DayCard(day, day.day.toString() in presentation.activityDays, presentation.appNames)
                }
            }
        }
        item(key = "refresh", contentType = "action") {
            OutlinedButton(onClick = onRefresh, enabled = !loading,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.product_refresh))
            }
        }
    }
}

@Composable
private fun DayCard(day: DayOutcome, hasActivity: Boolean, appNames: Map<String, String>) {
    val label = ProductFormatters.date(LocalContext.current, day.day, full = true)
    Card(Modifier.fillMaxWidth().testTag("history_day")) {
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
            day.perAppUsageMs.forEach { (pkg, ms) ->
                Text(stringResource(R.string.outcome_app_usage, appNames[pkg] ?: pkg, duration(ms)))
            }
            Text(stringResource(R.string.product_activity), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() })
            if (!hasActivity) Text(stringResource(R.string.product_no_activity))
            day.activity.forEach { (exercise, outcome) ->
                if (outcome.repetitions > 0 || outcome.activeDurationMs > 0) {
                    Text(stringResource(exercise.label()))
                    Text(stringResource(R.string.outcome_activity_detail,
                        number(outcome.repetitions), duration(outcome.activeDurationMs)))
                    if (outcome.exerciseOnlyRepetitions > 0 || outcome.exerciseOnlyDurationMs > 0) {
                        Text(stringResource(R.string.outcome_exercise_only,
                            number(outcome.exerciseOnlyRepetitions), duration(outcome.exerciseOnlyDurationMs)))
                    }
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() })
        val deltaMs = value.deltaMs
        if (value.blockedBy == null && deltaMs != null) {
            val change = when {
                deltaMs > 0 -> R.string.outcome_change_more
                deltaMs < 0 -> R.string.outcome_change_less
                else -> R.string.outcome_change_same
            }
            Text(stringResource(R.string.outcome_comparison_values,
                stringResource(R.string.outcome_previous), duration(value.previousMs!!),
                stringResource(R.string.outcome_current), duration(value.currentMs!!),
                stringResource(change, duration(kotlin.math.abs(deltaMs)))))
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
}
