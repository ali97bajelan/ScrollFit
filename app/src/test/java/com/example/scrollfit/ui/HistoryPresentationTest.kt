package com.example.scrollfit.ui

import com.example.scrollfit.core.data.*
import com.example.scrollfit.core.exercise.Exercise
import com.example.scrollfit.core.outcomes.OutcomeAggregator
import com.example.scrollfit.monitoring.TargetApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class HistoryPresentationTest {
    @Test fun preparationUsesSnapshotDayAndKeepsZeroActivityPresence() {
        val day = LocalDate.parse("2026-10-16")
        val today = day.plusDays(1)
        val activity = ExerciseActivity("visible-zero", null, day.toString(), Exercise.SQUAT, 0, 0, 0)
        val history = VisibleHistory(emptyList(), emptyList(), listOf(activity))
        val snapshot = ProductSnapshot(today.toString(), "UTC", AppPreferences(), emptyList(),
            listOf(TargetApp("removed.package", "Removed app")), emptyMap(),
            CombinedUsage(today.toString()), emptyMap(), emptyMap(), history)

        val result = prepareHistory(snapshot)

        assertEquals(OutcomeAggregator.summarize(history, today, ZoneId.of("UTC")), result.summary)
        assertEquals(setOf(day.toString()), result.activityDays)
        assertEquals(mapOf("removed.package" to "Removed app"), result.appNames)
    }
}
