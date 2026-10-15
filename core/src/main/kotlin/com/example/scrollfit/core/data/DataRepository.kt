package com.example.scrollfit.core.data

/** Application-facing data operations. Spending and reward creation belong exclusively to BudgetEngine. */
interface DataRepository {
    suspend fun saveTarget(config: TargetConfig)
    suspend fun targets(): List<TargetConfig>
    suspend fun removeTarget(packageName: String)
    suspend fun dailyUsage(packageName: String, day: String? = null): DailyUsage
    suspend fun combinedUsage(day: String? = null): CombinedUsage
    suspend fun session(packageName: String, day: String? = null): SessionState
    suspend fun saveSession(value: SessionState)
    suspend fun challenge(id: String): Challenge?
    suspend fun cancelChallenge(id: String)
    suspend fun recordActivity(value: ExerciseActivity)
    suspend fun activities(): List<ExerciseActivity>
    suspend fun visibleHistory(): VisibleHistory
    suspend fun clearHistory()
}
