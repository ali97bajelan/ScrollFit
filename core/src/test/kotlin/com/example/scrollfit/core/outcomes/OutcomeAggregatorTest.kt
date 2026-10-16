package com.example.scrollfit.core.outcomes

import com.example.scrollfit.core.data.*
import com.example.scrollfit.core.exercise.Exercise
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class OutcomeAggregatorTest {
    @Test fun sevenDatesPreserveUnknownAndCompleteZero() {
        val today = LocalDate.parse("2026-10-16")
        val day = today.minusDays(1)
        val start = day.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        val history = VisibleHistory(emptyList(), emptyList(), emptyList(), listOf(
            CoverageInterval("full", "session", "boot", day.toString(), "UTC", 0, 86_400_000,
                start, start + 86_400_000, setOf("app"))))
        val result = OutcomeAggregator.summarize(history, today, ZoneId.of("UTC"))
        assertEquals(7, result.recentDays.size)
        assertEquals(0L, result.recentDays[1].combinedUsageMs)
        assertNull(result.recentDays[0].combinedUsageMs)
        assertTrue(result.recentDays[0].inProgress)
    }

    @Test fun appRowsAndMissingCombinedRowAreInconsistent() {
        val day = LocalDate.parse("2026-10-15")
        val history = VisibleHistory(listOf(DailyUsage("app", day.toString(), 1000)), emptyList(), emptyList())
        val outcome = OutcomeAggregator.summarize(history, day.plusDays(1), ZoneId.of("UTC")).recentDays[1]
        assertEquals(1000L, outcome.perAppUsageMs["app"])
        assertNull(outcome.combinedUsageMs)
        assertFalse(outcome.accountingConsistent)
    }

    @Test fun usageActivityAwardsAndRemovedAppsStaySeparate() {
        val today = LocalDate.parse("2026-10-16")
        val day = today.minusDays(1)
        val start = day.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        val activity = ExerciseActivity("only-activity", "only", day.toString(), Exercise.ALTERNATING_KNEE_RAISE, 20, 30_000, start)
        val history = VisibleHistory(
            usage = listOf(DailyUsage("app", day.toString(), 30_000, 30_000, 120_000), DailyUsage("removed.app", day.toString(), 0)),
            combined = listOf(CombinedUsage(day.toString(), 60_000)), activities = listOf(activity),
            coverage = listOf(CoverageInterval("full", "session", "boot", day.toString(), "UTC", 0, 86_400_000,
                start, start + 86_400_000, setOf("app", "removed.app"))),
            activityModes = mapOf("only" to ChallengeMode.EXERCISE_ONLY),
        )
        val summary = OutcomeAggregator.summarize(history, today, ZoneId.of("UTC"))
        val outcome = summary.recentDays[1]
        assertEquals(60_000L, summary.recordedUsageMs)
        assertEquals(120_000L, outcome.awardedMs)
        assertEquals(0L, outcome.perAppUsageMs["removed.app"])
        assertEquals(20L, outcome.activity.getValue(Exercise.ALTERNATING_KNEE_RAISE).exerciseOnlyRepetitions)
        assertEquals(30_000L, outcome.activity.getValue(Exercise.ALTERNATING_KNEE_RAISE).exerciseOnlyDurationMs)
    }

    @Test fun weeklyComparisonRequiresFourteenMatchingCompleteDays() {
        val today = LocalDate.parse("2026-10-16")
        val zone = ZoneId.of("UTC")
        val coverage = (1L..14L).map { offset ->
            val day = today.minusDays(offset)
            val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
            CoverageInterval("d$offset", "s$offset", "boot", day.toString(), zone.id, 0, 86_400_000,
                start, start + 86_400_000, setOf("app"))
        }
        val combined = (1L..14L).map { offset -> CombinedUsage(today.minusDays(offset).toString(), 1000) }
        val usage = (1L..14L).map { offset -> DailyUsage("app", today.minusDays(offset).toString(), 1000) }
        val complete = OutcomeAggregator.summarize(VisibleHistory(usage, combined, emptyList(), coverage), today, zone)
        assertNull(complete.weeklyComparison.blockedBy)
        assertEquals(7_000L, complete.weeklyComparison.previousMs)
        assertEquals(7_000L, complete.weeklyComparison.currentMs)

        val changed = coverage.map { row -> if (row.day == today.minusDays(10).toString()) row.copy(selectedPackages = setOf("other")) else row }
        val mismatch = OutcomeAggregator.summarize(VisibleHistory(usage, combined, emptyList(), changed), today, zone)
        assertEquals(ComparisonBlock.SCOPE_CHANGED, mismatch.weeklyComparison.blockedBy)
    }

    @Test fun inconsistentTotalsDoNotInventPerAppZeroOrComparableCoverage() {
        val today = LocalDate.parse("2026-10-16")
        val day = today.minusDays(1)
        val zone = ZoneId.of("UTC")
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val history = VisibleHistory(emptyList(), listOf(CombinedUsage(day.toString(), 60_000)), emptyList(), listOf(
            CoverageInterval("full", "s", "boot", day.toString(), zone.id, 0, 86_400_000,
                start, start + 86_400_000, setOf("app"))))
        val outcome = OutcomeAggregator.summarize(history, today, zone).recentDays[1]
        assertFalse(outcome.perAppUsageMs.containsKey("app"))
        assertFalse(outcome.accountingConsistent)
        assertFalse(outcome.coverage.comparable)
    }

    @Test fun dailyComparisonAllowsZeroBaselineAndBlocksHiddenHistory() {
        val today = LocalDate.parse("2026-10-16")
        val zone = ZoneId.of("UTC")
        fun coverage(day: LocalDate, id: String): CoverageInterval {
            val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
            return CoverageInterval(id, id, "boot", day.toString(), zone.id, 0, 86_400_000,
                start, start + 86_400_000, setOf("app"))
        }
        val previous = today.minusDays(2); val current = today.minusDays(1)
        val history = VisibleHistory(
            usage = listOf(DailyUsage("app", current.toString(), 60_000)),
            combined = listOf(CombinedUsage(previous.toString(), 0), CombinedUsage(current.toString(), 60_000)),
            activities = emptyList(), coverage = listOf(coverage(previous, "p"), coverage(current, "c")),
        )
        val comparison = OutcomeAggregator.summarize(history, today, zone).dailyComparison
        assertEquals(0L, comparison.previousMs)
        assertEquals(60_000L, comparison.deltaMs)
        val deleted = history.copy(hiddenUsageDays = setOf(previous.toString()))
        assertEquals(ComparisonBlock.HIDDEN_HISTORY, OutcomeAggregator.summarize(deleted, today, zone).dailyComparison.blockedBy)

        val oneZoneChanged = history.copy(coverage = history.coverage.map { row ->
            if (row.day == current.toString()) row.copy(zoneId = "Europe/London") else row
        })
        assertEquals(ComparisonBlock.ZONE_CHANGED, OutcomeAggregator.summarize(oneZoneChanged, today, zone).dailyComparison.blockedBy)
    }

    @Test fun incompleteWeeklyWindowCannotCompare() {
        val today = LocalDate.parse("2026-10-16")
        val zone = ZoneId.of("UTC")
        val rows = (1L..14L).mapNotNull { offset ->
            if (offset == 5L) return@mapNotNull null
            val day = today.minusDays(offset)
            val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
            CoverageInterval("d$offset", "s$offset", "boot", day.toString(), zone.id, 0, 86_400_000,
                start, start + 86_400_000, setOf("app"))
        }
        val usage = (1L..14L).map { offset -> DailyUsage("app", today.minusDays(offset).toString(), 1000) }
        val combined = (1L..14L).map { offset -> CombinedUsage(today.minusDays(offset).toString(), 1000) }
        val result = OutcomeAggregator.summarize(VisibleHistory(usage, combined, emptyList(), rows), today, zone)
        assertEquals(ComparisonBlock.INSUFFICIENT_COVERAGE, result.weeklyComparison.blockedBy)
    }
}
