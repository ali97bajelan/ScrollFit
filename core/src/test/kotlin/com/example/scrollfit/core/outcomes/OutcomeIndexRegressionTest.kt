package com.example.scrollfit.core.outcomes

import com.example.scrollfit.core.data.*
import com.example.scrollfit.core.exercise.Exercise
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class OutcomeIndexRegressionTest {
    @Test fun summarizingManyDaysDoesNotRescanCompleteRecordLists() {
        val today = LocalDate.parse("2026-10-17")
        val zone = ZoneId.of("UTC")
        val usage = (1L..1_095).map { offset ->
            DailyUsage("app", today.minusDays(offset).toString(), baseConsumedMs = 1_000)
        }
        val combined = (1L..1_095).map { offset ->
            CombinedUsage(today.minusDays(offset).toString(), consumedMs = 1_000)
        }
        val activities = (1L..1_095).flatMap { offset ->
            val day = today.minusDays(offset)
            (0 until 20).map { index ->
                ExerciseActivity("$offset-$index", null, day.toString(), Exercise.SQUAT, 1, 500, day.toEpochDay())
            }
        }
        val coverage = (1L..1_095).map { offset ->
            val day = today.minusDays(offset)
            val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
            CoverageInterval("d$offset", "s$offset", "boot", day.toString(), zone.id, 0, 86_400_000,
                start, start + 86_400_000, setOf("app"))
        }
        val usageRows = CountingList(usage)
        val combinedRows = CountingList(combined)
        val activityRows = CountingList(activities)
        val coverageRows = CountingList(coverage)
        val history = VisibleHistory(usageRows, combinedRows, activityRows, coverageRows)

        val result = OutcomeAggregator.summarize(history, today, zone)

        assertTrue(result.olderDays.size > 1_000)
        assertTrue("usage rows visited ${usageRows.visits} times", usageRows.visits <= usage.size * 4)
        assertTrue("combined rows visited ${combinedRows.visits} times", combinedRows.visits <= combined.size * 4)
        assertTrue("activity rows visited ${activityRows.visits} times", activityRows.visits <= activities.size * 4)
        assertTrue("coverage rows visited ${coverageRows.visits} times", coverageRows.visits <= coverage.size * 4)
    }

    private class CountingList<T>(private val rows: List<T>) : AbstractList<T>() {
        var visits = 0
        override val size: Int get() = rows.size
        override fun get(index: Int): T {
            visits++
            return rows[index]
        }
    }
}
