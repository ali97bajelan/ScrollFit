package com.example.scrollfit.core.outcomes

import com.example.scrollfit.core.data.*
import com.example.scrollfit.core.exercise.Exercise
import java.time.LocalDate
import java.time.ZoneId

object OutcomeAggregator {
    fun summarize(history: VisibleHistory, today: LocalDate, currentZone: ZoneId): OutcomeSummary {
        val index = HistoryIndex(history)
        val days = index.recordedDays
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            .filter { it <= today }
            .distinct()
            .sortedDescending()
        val outcomes = mutableMapOf<LocalDate, DayOutcome>()
        fun outcome(day: LocalDate): DayOutcome = outcomes.getOrPut(day) {
            dayOutcome(day, index, today, currentZone)
        }

        val recent = (0..6).map { offset -> outcome(today.minusDays(offset.toLong())) }
        val older = days.filter { it < today.minusDays(6) }.map(::outcome)
        val allActivity = aggregateActivity(recent.flatMap { index.activities(it.day) }, history.activityModes)
        val completedRecent = recent.mapNotNull { it.combinedUsageMs }
        val completedAwards = recent.mapNotNull { it.awardedMs }
        return OutcomeSummary(recent, older,
            completedRecent.takeIf { it.isNotEmpty() }?.fold(0L, Math::addExact),
            completedAwards.takeIf { it.isNotEmpty() }?.fold(0L, Math::addExact), allActivity,
            compare(listOf(outcome(today.minusDays(2)), outcome(today.minusDays(1))), index, outcomes),
            weeklyComparison(index, today, currentZone, ::outcome, outcomes))
    }

    private fun dayOutcome(day: LocalDate, index: HistoryIndex, today: LocalDate, fallbackZone: ZoneId): DayOutcome {
        val key = day.toString()
        val usage = index.usage(key)
        val combinedRow = index.combined(key)
        val hidden = index.isHidden(key)
        val inProgress = day == today
        val measuredCoverage = CoverageAssessment.assess(day, index.coverage(key), fallbackZone, inProgress, hidden)
        val appTotals = usage.associate { it.packageName to Math.addExact(it.baseConsumedMs, it.earnedConsumedMs) }
        val appSum = appTotals.values.fold(0L, Math::addExact)
        val consistent = if (combinedRow == null) appSum == 0L else appSum == combinedRow.consumedMs
        val coverage = measuredCoverage.copy(comparable = measuredCoverage.comparable && consistent && !hidden)
        val combined = when {
            hidden -> null
            combinedRow != null -> combinedRow.consumedMs
            coverage.status == CoverageStatus.COMPLETE && appSum == 0L -> 0L
            else -> null
        }
        val perApp = appTotals.toMutableMap()
        if (coverage.status == CoverageStatus.COMPLETE && consistent) coverage.selectedPackages.forEach { perApp.putIfAbsent(it, 0L) }
        val awardSum = usage.takeIf { it.isNotEmpty() }?.map { it.awardedMs }?.fold(0L, Math::addExact)
        return DayOutcome(day, combined, perApp, awardSum,
            aggregateActivity(index.activities(key), index.activityModes), coverage, inProgress, consistent)
    }

    private fun compare(
        days: List<DayOutcome>, index: HistoryIndex, outcomes: Map<LocalDate, DayOutcome>,
    ): UsageComparison {
        if (days.size != 2) return UsageComparison(null, null, null, ComparisonBlock.INSUFFICIENT_COVERAGE)
        val (previous, current) = days
        if (listOf(previous.day, current.day).any { index.isHidden(it.toString()) })
            return UsageComparison(null, null, null, ComparisonBlock.HIDDEN_HISTORY)
        val relevant = listOf(previous.day, current.day)
        if (relevant.any { index.zones(it.toString()).size > 1 } || previous.coverage.zoneId != current.coverage.zoneId)
            return UsageComparison(null, null, null, ComparisonBlock.ZONE_CHANGED)
        if (relevant.any { index.scopes(it.toString()).size > 1 } || previous.coverage.selectedPackages != current.coverage.selectedPackages)
            return UsageComparison(null, null, null, ComparisonBlock.SCOPE_CHANGED)
        val previousOutcome = outcomes.getValue(previous.day)
        val currentOutcome = outcomes.getValue(current.day)
        val block = when {
            previous.coverage.status != CoverageStatus.COMPLETE || current.coverage.status != CoverageStatus.COMPLETE ||
                previous.combinedUsageMs == null || current.combinedUsageMs == null -> ComparisonBlock.INSUFFICIENT_COVERAGE
            !previousOutcome.accountingConsistent || !currentOutcome.accountingConsistent -> ComparisonBlock.INCONSISTENT_ACCOUNTING
            else -> null
        }
        if (block != null) return UsageComparison(null, null, null, block)
        return UsageComparison(previous.combinedUsageMs, current.combinedUsageMs,
            Math.subtractExact(current.combinedUsageMs!!, previous.combinedUsageMs!!), null)
    }

    private fun weeklyComparison(
        index: HistoryIndex,
        today: LocalDate,
        zone: ZoneId,
        outcome: (LocalDate) -> DayOutcome,
        outcomes: MutableMap<LocalDate, DayOutcome>,
    ): UsageComparison {
        val previous = (8L..14L).map { outcome(today.minusDays(it)) }
        val current = (1L..7L).map { outcome(today.minusDays(it)) }
        val all = previous + current
        if (all.any { index.isHidden(it.day.toString()) }) return UsageComparison(null, null, null, ComparisonBlock.HIDDEN_HISTORY)
        val relevantDays = all.map { it.day }
        val allZones = relevantDays.flatMap { index.zones(it.toString()) }.toSet()
        if (allZones.size > 1) return UsageComparison(null, null, null, ComparisonBlock.ZONE_CHANGED)
        val allScopes = relevantDays.flatMap { index.scopes(it.toString()) }.toSet()
        if (allScopes.size > 1) return UsageComparison(null, null, null, ComparisonBlock.SCOPE_CHANGED)
        val incomplete = all.firstOrNull { it.coverage.status != CoverageStatus.COMPLETE || it.combinedUsageMs == null }
        if (incomplete != null) return UsageComparison(null, null, null, ComparisonBlock.INSUFFICIENT_COVERAGE)
        if (all.any { !outcomes.getValue(it.day).accountingConsistent })
            return UsageComparison(null, null, null, ComparisonBlock.INCONSISTENT_ACCOUNTING)
        val zones = all.map { it.coverage.zoneId }.toSet()
        if (zones.size != 1) return UsageComparison(null, null, null, ComparisonBlock.ZONE_CHANGED)
        val scopes = all.map { it.coverage.selectedPackages }.toSet()
        if (scopes.size != 1) return UsageComparison(null, null, null, ComparisonBlock.SCOPE_CHANGED)
        val before = previous.fold(0L) { sum, day -> Math.addExact(sum, day.combinedUsageMs!!) }
        val after = current.fold(0L) { sum, day -> Math.addExact(sum, day.combinedUsageMs!!) }
        return UsageComparison(before, after, Math.subtractExact(after, before), null)
    }

    private fun aggregateActivity(rows: List<ExerciseActivity>, modes: Map<String, ChallengeMode>): Map<Exercise, ExerciseOutcome> =
        Exercise.entries.associateWith { exercise ->
            val selected = rows.filter { it.exercise == exercise }
            val exerciseOnly = selected.filter { it.challengeId?.let(modes::get) == ChallengeMode.EXERCISE_ONLY }
            ExerciseOutcome(selected.fold(0L) { total, item -> Math.addExact(total, item.repetitions.toLong()) },
                selected.fold(0L) { total, item -> Math.addExact(total, item.activeDurationMs) },
                exerciseOnly.fold(0L) { total, item -> Math.addExact(total, item.repetitions.toLong()) },
                exerciseOnly.fold(0L) { total, item -> Math.addExact(total, item.activeDurationMs) })
        }

    private class HistoryIndex(private val history: VisibleHistory) {
        val usageByDay = history.usage.groupBy { it.day }
        private val combinedByDay = history.combined.groupBy { it.day }.mapValues { (_, rows) -> rows.first() }
        private val activitiesByDay = history.activities.groupBy { it.day }
        private val coverageByDay = history.coverage.groupBy { it.day }
        val recordedDays: Set<String> = buildSet {
            addAll(usageByDay.keys)
            addAll(combinedByDay.keys)
            addAll(activitiesByDay.keys)
            addAll(coverageByDay.keys)
        }
        val activityModes = history.activityModes

        fun usage(day: String) = usageByDay[day].orEmpty()
        fun combined(day: String) = combinedByDay[day]
        fun activities(day: LocalDate) = activities(day.toString())
        fun activities(day: String) = activitiesByDay[day].orEmpty()
        fun coverage(day: String) = coverageByDay[day].orEmpty()
        fun zones(day: String) = coverage(day).asSequence().filter { it.historyVisible }.map { it.zoneId }.toSet()
        fun scopes(day: String) = coverage(day).asSequence().filter { it.historyVisible }.map { it.selectedPackages }.toSet()

        fun isHidden(day: String): Boolean = day in history.hiddenUsageDays ||
            (history.coverageHiddenThroughDay?.let { day <= it } == true)
    }
}
