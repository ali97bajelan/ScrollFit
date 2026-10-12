package com.example.scrollfit.core.budget

import com.example.scrollfit.core.data.*
import com.example.scrollfit.core.exercise.Exercise
import java.time.LocalDate

enum class BudgetBlock { TARGET_UNAVAILABLE, COMBINED_CEILING, APP_BUDGET }

data class Budget(
    val packageName: String, val day: String, val baseRemainingMs: Long,
    val earnedRemainingMs: Long, val awardedMs: Long, val awardCapacityMs: Long,
    val combinedRemainingMs: Long, val usableMs: Long, val blockedBy: BudgetBlock?,
)

/** Caller splits at local midnight and supplies only eligible foreground intervals. */
data class UsageInterval(val packageName: String, val day: String, val startElapsedMs: Long, val endElapsedMs: Long) {
    init {
        require(packageName.isNotBlank()); LocalDate.parse(day)
        require(startElapsedMs >= 0 && endElapsedMs >= startElapsedMs)
    }
}
enum class SpendDisposition { ACCOUNTED, DUPLICATE, WRONG_DAY, TARGET_UNAVAILABLE }
data class SpendResult(val baseSpentMs: Long = 0, val earnedSpentMs: Long = 0, val disposition: SpendDisposition) {
    val spentMs get() = Math.addExact(baseSpentMs, earnedSpentMs)
}

enum class RewardBlock { TARGET_UNAVAILABLE, COMBINED_CEILING, AWARD_CAP, INCOMPLETE, INACTIVE, MISSING }

data class RewardOffer(
    val packageName: String?, val day: String, val exercise: Exercise, val mode: ChallengeMode,
    val repetitionsRequired: Int, val rewardMs: Long, val configuredRewardMs: Long,
    val awardCapacityMs: Long, val combinedRemainingMs: Long,
) {
    val reduced get() = rewardMs < configuredRewardMs
}
sealed interface OfferResult {
    data class Available(val offer: RewardOffer) : OfferResult
    data class Refused(val reason: RewardBlock) : OfferResult
}
sealed interface StartResult {
    data class Started(val challenge: Challenge) : StartResult
    data class OfferChanged(val current: OfferResult) : StartResult
}
sealed interface CompletionResult {
    data class Awarded(val award: Award) : CompletionResult
    data object ExerciseOnlyCompleted : CompletionResult
    data class Refused(val reason: RewardBlock) : CompletionResult
}
