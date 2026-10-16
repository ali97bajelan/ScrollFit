package com.example.scrollfit.core.outcomes

import com.example.scrollfit.core.exercise.Exercise
import java.time.LocalDate

enum class CoverageStatus { COMPLETE, PARTIAL, UNKNOWN }
data class DayCoverage(val status: CoverageStatus, val recordedMs: Long, val expectedMs: Long,
    val zoneId: String?, val selectedPackages: Set<String>, val comparable: Boolean)
data class ExerciseOutcome(val repetitions: Long, val activeDurationMs: Long,
    val exerciseOnlyRepetitions: Long, val exerciseOnlyDurationMs: Long)
data class DayOutcome(val day: LocalDate, val combinedUsageMs: Long?, val perAppUsageMs: Map<String, Long>,
    val awardedMs: Long?, val activity: Map<Exercise, ExerciseOutcome>, val coverage: DayCoverage,
    val inProgress: Boolean, val accountingConsistent: Boolean)
enum class ComparisonBlock { INSUFFICIENT_COVERAGE, SCOPE_CHANGED, ZONE_CHANGED, HIDDEN_HISTORY, INCONSISTENT_ACCOUNTING }
data class UsageComparison(val previousMs: Long?, val currentMs: Long?, val deltaMs: Long?, val blockedBy: ComparisonBlock?)
data class OutcomeSummary(val recentDays: List<DayOutcome>, val olderDays: List<DayOutcome>,
    val recordedUsageMs: Long?, val recordedAwardedMs: Long?, val activity: Map<Exercise, ExerciseOutcome>,
    val dailyComparison: UsageComparison, val weeklyComparison: UsageComparison)
