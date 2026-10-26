package com.example.scrollfit.platform.data

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteStatement
import com.example.scrollfit.core.data.CombinedUsage
import com.example.scrollfit.core.data.CoverageInterval
import com.example.scrollfit.core.data.DailyUsage
import com.example.scrollfit.core.data.VisibleHistory
import java.time.LocalDate
import java.time.ZoneId

/** Deterministic large-history fixture shared by the I12 persistence measurement. */
internal object HistoryScaleSeed {
    private const val PACKAGE_COUNT = 20
    private val today = LocalDate.parse("2026-10-26")

    fun packageNames(): List<String> = (0 until PACKAGE_COUNT).map { index ->
        "fixture.pkg.${index.toString().padStart(2, '0')}"
    }

    fun seed(database: SupportSQLiteDatabase, dayCount: Int): VisibleHistory {
        require(dayCount == 7 || dayCount == 365 || dayCount == 1_095)
        val packages = packageNames()
        val firstDay = today.minusDays((dayCount - 1).toLong())
        val usage = mutableListOf<DailyUsage>()
        val combined = mutableListOf<CombinedUsage>()
        val coverage = mutableListOf<CoverageInterval>()

        val targetInsert = database.compileStatement(
            "INSERT INTO targets (packageName, selected, baseAllowanceMs, dailyAwardCapMs) VALUES (?, ?, ?, ?)",
        )
        val usageInsert = database.compileStatement(
            "INSERT INTO daily_usage (packageName, day, baseConsumedMs, earnedConsumedMs, awardedMs, historyVisible) VALUES (?, ?, ?, ?, ?, ?)",
        )
        val combinedInsert = database.compileStatement(
            "INSERT INTO combined_usage (day, consumedMs, historyVisible) VALUES (?, ?, ?)",
        )
        val coverageInsert = database.compileStatement(
            "INSERT INTO monitoring_coverage (id, recordingSessionId, timelineId, day, zoneId, startElapsedMs, endElapsedMs, startEpochMs, endEpochMs, historyVisible) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        )
        val coverageTargetInsert = database.compileStatement(
            "INSERT INTO monitoring_coverage_targets (coverageId, packageName) VALUES (?, ?)",
        )
        try {
            database.beginTransaction()
            packages.forEachIndexed { index, packageName ->
                targetInsert.bindString(1, packageName)
                targetInsert.bindLong(2, if (index == PACKAGE_COUNT - 1) 0 else 1) // final target was removed
                targetInsert.bindLong(3, 3_600_000)
                targetInsert.bindLong(4, 600_000)
                targetInsert.executeInsert()
                targetInsert.clearBindings()
            }

            repeat(dayCount) { offset ->
                val day = firstDay.plusDays(offset.toLong())
                val hidden = offset == 0
                val rows = packages.mapIndexed { index, packageName ->
                    DailyUsage(packageName, day.toString(), baseConsumedMs = 60_000L + index * 100L,
                        historyVisible = !hidden)
                }
                val combinedRow = CombinedUsage(day.toString(),
                    rows.sumOf { it.baseConsumedMs }, historyVisible = !hidden)
                rows.forEach { row ->
                    usageInsert.bindString(1, row.packageName)
                    usageInsert.bindString(2, row.day)
                    usageInsert.bindLong(3, row.baseConsumedMs)
                    usageInsert.bindLong(4, row.earnedConsumedMs)
                    usageInsert.bindLong(5, row.awardedMs)
                    usageInsert.bindLong(6, if (row.historyVisible) 1 else 0)
                    usageInsert.executeInsert()
                    usageInsert.clearBindings()
                }
                combinedInsert.bindString(1, combinedRow.day)
                combinedInsert.bindLong(2, combinedRow.consumedMs)
                combinedInsert.bindLong(3, if (combinedRow.historyVisible) 1 else 0)
                combinedInsert.executeInsert()
                combinedInsert.clearBindings()
                if (!hidden) {
                    usage += rows
                    combined += combinedRow
                }

                val mode = offset % 4 // complete, partial, sparse-scope, then unknown
                if (mode != 3) {
                    val zone = ZoneId.of(if (mode == 2) "Asia/Tehran" else "UTC")
                    val dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
                    val nextStart = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                    val fullDayMs = nextStart - dayStart
                    val observedMs = when (mode) {
                        0 -> fullDayMs
                        1 -> fullDayMs / 2
                        else -> minOf(fullDayMs, 3_600_000L)
                    }
                    val id = "fixture-coverage-$offset"
                    val selectedPackages = if (mode == 2) packages.take(5).toSet() else packages.toSet()
                    val row = CoverageInterval(id, "fixture-session", "fixture-${zone.id}", day.toString(), zone.id,
                        0, observedMs, dayStart, dayStart + observedMs, selectedPackages, historyVisible = !hidden)
                    coverageInsert.bindString(1, row.id)
                    coverageInsert.bindString(2, row.recordingSessionId)
                    coverageInsert.bindString(3, row.timelineId)
                    coverageInsert.bindString(4, row.day)
                    coverageInsert.bindString(5, row.zoneId)
                    coverageInsert.bindLong(6, row.startElapsedMs)
                    coverageInsert.bindLong(7, row.endElapsedMs)
                    coverageInsert.bindLong(8, row.startEpochMs)
                    coverageInsert.bindLong(9, row.endEpochMs)
                    coverageInsert.bindLong(10, if (row.historyVisible) 1 else 0)
                    coverageInsert.executeInsert()
                    coverageInsert.clearBindings()
                    selectedPackages.forEach { packageName ->
                        coverageTargetInsert.bindString(1, id)
                        coverageTargetInsert.bindString(2, packageName)
                        coverageTargetInsert.executeInsert()
                        coverageTargetInsert.clearBindings()
                    }
                    if (row.historyVisible) coverage += row
                }
            }

            database.execSQL(
                "INSERT INTO history_visibility (id, coverageHiddenThroughDay) VALUES (1, ?)",
                arrayOf(firstDay.toString()),
            )
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
            targetInsert.close()
            usageInsert.close()
            combinedInsert.close()
            coverageInsert.close()
            coverageTargetInsert.close()
        }

        return VisibleHistory(
            usage = usage.sortedWith(compareByDescending<DailyUsage> { it.day }.thenBy { it.packageName }),
            combined = combined.sortedByDescending { it.day },
            activities = emptyList(),
            coverage = coverage.sortedWith(compareBy<CoverageInterval> { it.day }.thenBy { it.startEpochMs }),
            hiddenUsageDays = setOf(firstDay.toString()),
            coverageHiddenThroughDay = firstDay.toString(),
        )
    }
}
