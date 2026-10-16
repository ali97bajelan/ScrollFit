package com.example.scrollfit.core.outcomes

import com.example.scrollfit.core.data.CoverageInterval
import java.time.LocalDate
import java.time.ZoneId

internal object CoverageAssessment {
    fun assess(day: LocalDate, intervals: List<CoverageInterval>, fallbackZone: ZoneId, inProgress: Boolean,
        hidden: Boolean): DayCoverage {
        val rows = intervals.filter { it.day == day.toString() && it.historyVisible }
        if (rows.isEmpty()) return DayCoverage(CoverageStatus.UNKNOWN, 0, expected(day, fallbackZone), null, emptySet(), false)
        val zones = rows.map { it.zoneId }.toSet()
        val scopes = rows.map { it.selectedPackages }.toSet()
        if (zones.size != 1) return DayCoverage(CoverageStatus.PARTIAL, 0, expected(day, fallbackZone), null, emptySet(), false)
        val zone = ZoneId.of(zones.single())
        val expected = expected(day, zone)
        val scope = scopes.singleOrNull().orEmpty()
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val spans = rows.mapNotNull { row ->
            val a = maxOf(start, row.startEpochMs); val b = minOf(end, row.endEpochMs)
            if (b > a) a to b else null
        }.sortedBy { it.first }
        var covered = 0L; var left: Long? = null; var right = 0L
        for ((a, b) in spans) {
            if (left == null) { left = a; right = b }
            else if (a <= right) right = maxOf(right, b)
            else { covered = Math.addExact(covered, right - left!!); left = a; right = b }
        }
        if (left != null) covered = Math.addExact(covered, right - left!!)
        val complete = !hidden && !inProgress && scope.isNotEmpty() && covered >= expected
        val status = when { complete -> CoverageStatus.COMPLETE; covered > 0 -> CoverageStatus.PARTIAL; else -> CoverageStatus.UNKNOWN }
        return DayCoverage(status, covered, expected, zone.id, scope, complete)
    }

    private fun expected(day: LocalDate, zone: ZoneId) = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() -
        day.atStartOfDay(zone).toInstant().toEpochMilli()
}
