package com.example.scrollfit.core.data

/** The block must be serialized and rolled back completely if it throws (including cancellation). */
interface DurableStore {
    suspend fun <T> transaction(block: suspend DataTransaction.() -> T): T
}

interface DataTransaction {
    suspend fun target(packageName: String): TargetConfig?
    suspend fun targets(): List<TargetConfig>
    suspend fun putTarget(value: TargetConfig)
    suspend fun usage(packageName: String, day: String): DailyUsage?
    suspend fun putUsage(value: DailyUsage)
    suspend fun combined(day: String): CombinedUsage?
    suspend fun putCombined(value: CombinedUsage)
    suspend fun challenge(id: String): Challenge?
    suspend fun insertChallenge(value: Challenge)
    suspend fun putChallenge(value: Challenge)
    suspend fun cancelActiveChallenges()
    suspend fun cancelChallengesOutsideDay(day: String)
    suspend fun cursor(timelineId: String): UsageCursor?
    suspend fun putCursor(value: UsageCursor)
    suspend fun award(challengeId: String): Award?
    suspend fun insertAward(value: Award)
    suspend fun insertActivity(value: ExerciseActivity)
    suspend fun activities(): List<ExerciseActivity>
    suspend fun session(packageName: String, day: String): SessionState?
    suspend fun putSession(value: SessionState)
    suspend fun visibleUsages(): List<DailyUsage>
    suspend fun visibleCombined(): List<CombinedUsage>
    suspend fun coverageTail(recordingSessionId: String, day: String): CoverageInterval?
    suspend fun putCoverage(value: CoverageInterval)
    suspend fun visibleCoverage(): List<CoverageInterval>
    suspend fun hiddenUsageDays(): Set<String>
    suspend fun coverageHiddenThroughDay(): String?
    suspend fun putCoverageHiddenThroughDay(day: String)
    /** Hide accounting history, delete activity; retain enforcement counters and award identities. */
    suspend fun clearVisibleHistory()
}
