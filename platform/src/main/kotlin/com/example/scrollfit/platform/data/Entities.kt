package com.example.scrollfit.platform.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.example.scrollfit.core.data.*

@Entity(tableName = "targets", primaryKeys = ["packageName"])
data class TargetRow(@Embedded val value: TargetConfig)

@Entity(tableName = "daily_usage", primaryKeys = ["packageName", "day"], foreignKeys = [
    ForeignKey(entity = TargetRow::class, parentColumns = ["packageName"], childColumns = ["packageName"]),
], indices = [Index("day")])
data class UsageRow(@Embedded val value: DailyUsage)

@Entity(tableName = "combined_usage", primaryKeys = ["day"])
data class CombinedRow(@Embedded val value: CombinedUsage)

@Entity(tableName = "challenges", primaryKeys = ["id"], foreignKeys = [
    ForeignKey(entity = TargetRow::class, parentColumns = ["packageName"], childColumns = ["packageName"]),
], indices = [Index("packageName"), Index("day"), Index("status")])
data class ChallengeRow(@Embedded val value: Challenge)

@Entity(tableName = "award_ledger", primaryKeys = ["challengeId"], foreignKeys = [
    ForeignKey(entity = ChallengeRow::class, parentColumns = ["id"], childColumns = ["challengeId"]),
    ForeignKey(entity = TargetRow::class, parentColumns = ["packageName"], childColumns = ["packageName"]),
], indices = [Index(value = ["packageName", "day"])])
data class AwardRow(@Embedded val value: Award)

@Entity(tableName = "exercise_activity", primaryKeys = ["id"], foreignKeys = [
    ForeignKey(entity = ChallengeRow::class, parentColumns = ["id"], childColumns = ["challengeId"]),
], indices = [Index("challengeId"), Index("day")])
data class ActivityRow(@Embedded val value: ExerciseActivity)

@Entity(tableName = "sessions", primaryKeys = ["packageName", "day"], foreignKeys = [
    ForeignKey(entity = TargetRow::class, parentColumns = ["packageName"], childColumns = ["packageName"]),
])
data class SessionRow(@Embedded val value: SessionState)

@Entity(tableName = "usage_cursors", primaryKeys = ["timelineId"])
data class CursorRow(@Embedded val value: UsageCursor)

@Entity(tableName = "monitoring_coverage", primaryKeys = ["id"], indices = [Index(value = ["recordingSessionId", "day"]), Index("day")])
data class CoverageRow(
    val id: String, val recordingSessionId: String, val timelineId: String, val day: String, val zoneId: String,
    val startElapsedMs: Long, val endElapsedMs: Long, val startEpochMs: Long, val endEpochMs: Long,
    val historyVisible: Boolean,
) {
    fun value(packages: Set<String>) = CoverageInterval(id, recordingSessionId, timelineId, day, zoneId,
        startElapsedMs, endElapsedMs, startEpochMs, endEpochMs, packages, historyVisible)
    companion object { fun from(value: CoverageInterval) = CoverageRow(value.id, value.recordingSessionId,
        value.timelineId, value.day, value.zoneId, value.startElapsedMs, value.endElapsedMs, value.startEpochMs,
        value.endEpochMs, value.historyVisible) }
}

@Entity(tableName = "monitoring_coverage_targets", primaryKeys = ["coverageId", "packageName"], foreignKeys = [
    ForeignKey(entity = CoverageRow::class, parentColumns = ["id"], childColumns = ["coverageId"], onDelete = ForeignKey.CASCADE),
], indices = [Index("coverageId")])
data class CoverageTargetRow(val coverageId: String, val packageName: String)

@Entity(tableName = "history_visibility", primaryKeys = ["id"])
data class HistoryVisibilityRow(val id: Int = 1, val coverageHiddenThroughDay: String? = null)
