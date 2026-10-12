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
