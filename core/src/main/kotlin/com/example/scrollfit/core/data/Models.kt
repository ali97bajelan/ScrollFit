package com.example.scrollfit.core.data

import com.example.scrollfit.core.exercise.Exercise
import com.example.scrollfit.core.exercise.ProductDefaults
import com.example.scrollfit.core.exercise.RewardRule
import java.time.LocalDate

/** ISO local calendar dates identify budgets; all persisted durations use integer milliseconds. */
data class TargetConfig(
    val packageName: String,
    val selected: Boolean = true,
    val baseAllowanceMs: Long = ProductDefaults.BASE_ALLOWANCE_SECONDS * 1000,
    val dailyAwardCapMs: Long = ProductDefaults.DAILY_AWARD_CAP_SECONDS * 1000,
) {
    init { require(packageName.isNotBlank()); require(baseAllowanceMs >= 0); require(dailyAwardCapMs >= 0) }
}

data class DailyUsage(
    val packageName: String, val day: String,
    val baseConsumedMs: Long = 0, val earnedConsumedMs: Long = 0,
    val awardedMs: Long = 0, val historyVisible: Boolean = true,
) {
    init {
        require(packageName.isNotBlank())
        LocalDate.parse(day)
        require(baseConsumedMs >= 0 && earnedConsumedMs >= 0 && awardedMs >= earnedConsumedMs)
        Math.addExact(baseConsumedMs, earnedConsumedMs)
    }
}

data class CombinedUsage(val day: String, val consumedMs: Long = 0, val historyVisible: Boolean = true) {
    init { LocalDate.parse(day); require(consumedMs >= 0) }
}

enum class ChallengeMode { ADVANCE, JUST_IN_TIME, EXERCISE_ONLY }
enum class ChallengeStatus { ACTIVE, COMPLETED, CANCELLED }

/** Reward and repetition terms are immutable snapshots, never read back from current settings. */
data class Challenge(
    val id: String, val packageName: String?, val day: String, val exercise: Exercise,
    val mode: ChallengeMode, val repetitionsRequired: Int, val rewardMs: Long,
    val startedAtEpochMs: Long, val status: ChallengeStatus = ChallengeStatus.ACTIVE,
) {
    init {
        require(id.isNotBlank()); LocalDate.parse(day); require(repetitionsRequired > 0)
        if (mode == ChallengeMode.EXERCISE_ONLY) require(packageName == null && rewardMs == 0L)
        else require(!packageName.isNullOrBlank() && rewardMs > 0)
    }
}

/** One immutable identity per challenge, retained even after history is cleared. */
data class Award(val challengeId: String, val packageName: String, val day: String, val amountMs: Long, val awardedAtEpochMs: Long) {
    init { require(challengeId.isNotBlank() && packageName.isNotBlank()); LocalDate.parse(day); require(amountMs > 0) }
}

data class ExerciseActivity(
    val id: String, val challengeId: String?, val day: String, val exercise: Exercise,
    val repetitions: Int, val activeDurationMs: Long, val recordedAtEpochMs: Long,
) {
    init { require(id.isNotBlank()); LocalDate.parse(day); require(repetitions >= 0 && activeDurationMs >= 0) }
}

data class SessionState(val packageName: String, val day: String, val consumedMs: Long = 0, val promptPending: Boolean = false) {
    init { require(packageName.isNotBlank()); LocalDate.parse(day); require(consumedMs >= 0) }
}

/** One global high-water mark per device boot, retained through history deletion. */
data class UsageCursor(val timelineId: String, val throughElapsedMs: Long) {
    init { require(timelineId.isNotBlank() && throughElapsedMs >= 0) }
}

enum class CameraChoice { REAR, FRONT }

data class AppPreferences(
    val monitoringEnabled: Boolean = false,
    val combinedCeilingMs: Long = ProductDefaults.COMBINED_CEILING_SECONDS * 1000,
    val zeroBaseEnabled: Boolean = ProductDefaults.ZERO_BASE_ENABLED,
    val speechEnabled: Boolean = ProductDefaults.SPEECH_ENABLED,
    val camera: CameraChoice = CameraChoice.REAR,
    val lastExercise: Exercise = Exercise.PUSH_UP,
    val sessionBoundaryMs: Long = ProductDefaults.SESSION_SECONDS * 1000,
    val rewardRules: Map<Exercise, RewardRule> = ProductDefaults.rewardRules,
) {
    init {
        require(combinedCeilingMs > 0 && sessionBoundaryMs > 0)
        require(rewardRules.keys == Exercise.entries.toSet())
        rewardRules.values.forEach { require(it.repetitions > 0 && it.rewardSeconds > 0); Math.multiplyExact(it.rewardSeconds, 1000) }
    }
}
