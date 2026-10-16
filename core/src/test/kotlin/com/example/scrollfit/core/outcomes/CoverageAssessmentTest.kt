package com.example.scrollfit.core.outcomes

import com.example.scrollfit.core.data.CoverageInterval
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class CoverageAssessmentTest {
    @Test fun dayLengthUsesZoneRulesAndOneMissingMillisecondIsPartial() {
        val zone = ZoneId.of("Europe/Paris")
        val spring = LocalDate.parse("2026-03-29")
        val springStart = spring.atStartOfDay(zone).toInstant().toEpochMilli()
        val springLength = 82_800_000L
        val covered = CoverageInterval("spring", "s", "b", spring.toString(), zone.id, 0, springLength,
            springStart, springStart + springLength, setOf("app"))
        assertEquals(CoverageStatus.COMPLETE, CoverageAssessment.assess(spring, listOf(covered), zone, false, false).status)
        val partial = covered.copy(id = "partial", endElapsedMs = springLength - 1, endEpochMs = springStart + springLength - 1)
        assertEquals(CoverageStatus.PARTIAL, CoverageAssessment.assess(spring, listOf(partial), zone, false, false).status)

        val autumn = LocalDate.parse("2026-10-25")
        val autumnStart = autumn.atStartOfDay(zone).toInstant().toEpochMilli()
        val autumnLength = 90_000_000L
        val fall = CoverageInterval("fall", "s", "b", autumn.toString(), zone.id, 0, autumnLength,
            autumnStart, autumnStart + autumnLength, setOf("app"))
        assertEquals(CoverageStatus.COMPLETE, CoverageAssessment.assess(autumn, listOf(fall), zone, false, false).status)
        assertEquals(autumnLength, CoverageAssessment.assess(autumn, listOf(fall), zone, false, false).expectedMs)
    }
}
