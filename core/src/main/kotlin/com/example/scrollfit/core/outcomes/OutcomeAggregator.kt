package com.example.scrollfit.core.outcomes

import com.example.scrollfit.core.data.*
import com.example.scrollfit.core.exercise.Exercise
import java.time.LocalDate
import java.time.ZoneId

object OutcomeAggregator {
    fun summarize(history: VisibleHistory, today: LocalDate, currentZone: ZoneId): OutcomeSummary {
        val byDay = (history.usage.map { it.day } + history.combined.map { it.day } + history.activities.map { it.day } +
            history.coverage.map { it.day }).toSet()
        val days = byDay.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            .filter { it <= today }.distinct().sortedDescending()
            .map { day -> dayOutcome(day, history, today, currentZone) }
        val recent = (0..6).map { d -> days.firstOrNull { it.day == today.minusDays(d.toLong()) }
            ?: dayOutcome(today.minusDays(d.toLong()), history, today, currentZone) }
        val older = days.filter { it.day < today.minusDays(6) }
        val allActivity = aggregateActivity(recent.flatMap { day ->
            history.activities.filter { it.day == day.day.toString() }
        }, history.activityModes)
        val completedRecent = recent.mapNotNull { it.combinedUsageMs }
        val completedAwards = recent.mapNotNull { it.awardedMs }
        return OutcomeSummary(recent, older,
            completedRecent.takeIf { it.isNotEmpty() }?.fold(0L, Math::addExact),
            completedAwards.takeIf { it.isNotEmpty() }?.fold(0L, Math::addExact), allActivity,
            compare(listOf(dayOutcome(today.minusDays(2), history, today, currentZone), dayOutcome(today.minusDays(1), history, today, currentZone)), history),
            weeklyComparison(history, today, currentZone))
    }

    private fun dayOutcome(day: LocalDate, history: VisibleHistory, today: LocalDate, zone: ZoneId): DayOutcome {
        val usage = history.usage.filter { it.day == day.toString() }
        val combinedRow = history.combined.firstOrNull { it.day == day.toString() }
        val hidden = day.toString() in history.hiddenUsageDays || (history.coverageHiddenThroughDay?.let { day.toString() <= it } == true)
        val inProgress = day == today
        val measuredCoverage = CoverageAssessment.assess(day, history.coverage, zone, inProgress, hidden)
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
            aggregateActivity(history.activities.filter { it.day == day.toString() }, history.activityModes), coverage, inProgress, consistent)
    }

    private fun compare(days: List<DayOutcome>, history: VisibleHistory): UsageComparison {
        if (days.size != 2) return UsageComparison(null, null, null, ComparisonBlock.INSUFFICIENT_COVERAGE)
        val (previous, current) = days
        if ((listOf(previous.day, current.day).any { isHidden(it, history) }))
            return UsageComparison(null, null, null, ComparisonBlock.HIDDEN_HISTORY)
        val relevant = listOf(previous.day, current.day)
        if (relevant.any { zonesFor(it, history).size > 1 } || previous.coverage.zoneId != current.coverage.zoneId)
            return UsageComparison(null, null, null, ComparisonBlock.ZONE_CHANGED)
        if (relevant.any { scopesFor(it, history).size > 1 } || previous.coverage.selectedPackages != current.coverage.selectedPackages)
            return UsageComparison(null, null, null, ComparisonBlock.SCOPE_CHANGED)
        val block = when {
            previous.coverage.status != CoverageStatus.COMPLETE || current.coverage.status != CoverageStatus.COMPLETE ||
                previous.combinedUsageMs == null || current.combinedUsageMs == null -> ComparisonBlock.INSUFFICIENT_COVERAGE
            !previous.accountingConsistent || !current.accountingConsistent -> ComparisonBlock.INCONSISTENT_ACCOUNTING
            else -> null
        }
        if (block != null) return UsageComparison(null, null, null, block)
        return UsageComparison(previous.combinedUsageMs, current.combinedUsageMs,
            Math.subtractExact(current.combinedUsageMs!!, previous.combinedUsageMs!!), null)
    }

    private fun weeklyComparison(history: VisibleHistory, today: LocalDate, zone: ZoneId): UsageComparison {
        val previous = (8L..14L).map { dayOutcome(today.minusDays(it), history, today, zone) }
        val current = (1L..7L).map { dayOutcome(today.minusDays(it), history, today, zone) }
        val all = previous + current
        if (all.any { isHidden(it.day, history) }) return UsageComparison(null, null, null, ComparisonBlock.HIDDEN_HISTORY)
        val relevantDays = all.map { it.day }
        val allZones = relevantDays.flatMap { zonesFor(it, history) }.toSet()
        if (allZones.size > 1) return UsageComparison(null, null, null, ComparisonBlock.ZONE_CHANGED)
        val allScopes = relevantDays.flatMap { scopesFor(it, history) }.toSet()
        if (allScopes.size > 1) return UsageComparison(null, null, null, ComparisonBlock.SCOPE_CHANGED)
        val incomplete = all.firstOrNull { it.coverage.status != CoverageStatus.COMPLETE || it.combinedUsageMs == null }
        if (incomplete != null) return UsageComparison(null, null, null, ComparisonBlock.INSUFFICIENT_COVERAGE)
        if (all.any { !it.accountingConsistent }) return UsageComparison(null, null, null, ComparisonBlock.INCONSISTENT_ACCOUNTING)
        val zones = all.map { it.coverage.zoneId }.toSet()
        if (zones.size != 1) return UsageComparison(null, null, null, ComparisonBlock.ZONE_CHANGED)
        val scopes = all.map { it.coverage.selectedPackages }.toSet()
        if (scopes.size != 1) return UsageComparison(null, null, null, ComparisonBlock.SCOPE_CHANGED)
        val before = previous.fold(0L) { sum, day -> Math.addExact(sum, day.combinedUsageMs!!) }
        val after = current.fold(0L) { sum, day -> Math.addExact(sum, day.combinedUsageMs!!) }
        return UsageComparison(before, after, Math.subtractExact(after, before), null)
    }

    private fun isHidden(day: LocalDate, history: VisibleHistory) = day.toString() in history.hiddenUsageDays ||
        (history.coverageHiddenThroughDay?.let { day.toString() <= it } == true)

    private fun zonesFor(day: LocalDate, history: VisibleHistory) = history.coverage.filter {
        it.day == day.toString() && it.historyVisible
    }.map { it.zoneId }.toSet()

    private fun scopesFor(day: LocalDate, history: VisibleHistory) = history.coverage.filter {
        it.day == day.toString() && it.historyVisible
    }.map { it.selectedPackages }.toSet()

    private fun aggregateActivity(rows: List<ExerciseActivity>, modes: Map<String, ChallengeMode>): Map<Exercise, ExerciseOutcome> =
        Exercise.entries.associateWith { exercise ->
            val selected = rows.filter { it.exercise == exercise }
            val exerciseOnly = selected.filter { it.challengeId?.let(modes::get) == ChallengeMode.EXERCISE_ONLY }
            ExerciseOutcome(selected.fold(0L) { total, item -> Math.addExact(total, item.repetitions.toLong()) },
                selected.fold(0L) { total, item -> Math.addExact(total, item.activeDurationMs) },
                exerciseOnly.fold(0L) { total, item -> Math.addExact(total, item.repetitions.toLong()) },
                exerciseOnly.fold(0L) { total, item -> Math.addExact(total, item.activeDurationMs) })
        }
}
