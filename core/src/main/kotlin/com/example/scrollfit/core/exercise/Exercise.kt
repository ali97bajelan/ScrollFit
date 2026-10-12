package com.example.scrollfit.core.exercise

enum class Exercise {
    PUSH_UP, SQUAT, JUMPING_JACK, ALTERNATING_KNEE_RAISE,
}

data class RewardRule(val repetitions: Int, val rewardSeconds: Long)

/** Configurable product proposals; no award or exercise logic is implemented in T01. */
object ProductDefaults {
    const val BASE_ALLOWANCE_SECONDS = 30L * 60
    const val COMBINED_CEILING_SECONDS = 60L * 60
    const val DAILY_AWARD_CAP_SECONDS = 20L * 60
    const val SESSION_SECONDS = 5L * 60
    const val ZERO_BASE_ENABLED = false
    const val SPEECH_ENABLED = true

    val rewardRules: Map<Exercise, RewardRule> = mapOf(
        Exercise.PUSH_UP to RewardRule(2, 120),
        Exercise.SQUAT to RewardRule(10, 120),
        Exercise.JUMPING_JACK to RewardRule(20, 120),
        Exercise.ALTERNATING_KNEE_RAISE to RewardRule(20, 120),
    )
}
