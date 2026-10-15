package com.example.scrollfit.core.data

import java.util.concurrent.Semaphore

/** Contract test adapter only: it does not prove SQLite/Room durability. */
class MemoryStore : DurableStore, DataTransaction {
    private val transactions = Semaphore(1)
    private var targets = mutableMapOf<String, TargetConfig>()
    private var usages = mutableMapOf<Pair<String, String>, DailyUsage>()
    private var combined = mutableMapOf<String, CombinedUsage>()
    private var challenges = mutableMapOf<String, Challenge>()
    var awards = mutableMapOf<String, Award>()
    private var activities = mutableMapOf<String, ExerciseActivity>()
    private var sessions = mutableMapOf<Pair<String, String>, SessionState>()
    private var cursors = mutableMapOf<String, UsageCursor>()
    var failNextUsage = false
    var onUsageRead: (() -> Unit)? = null
    var failNextActivity = false
    var onActivityWrite: (() -> Unit)? = null
    var onDayReconciliation: (() -> Unit)? = null
    override suspend fun <T> transaction(block: suspend DataTransaction.() -> T): T {
        transactions.acquire()
        val savedTargets = targets.toMutableMap(); val savedUsages = usages.toMutableMap()
        val savedCombined = combined.toMutableMap(); val savedChallenges = challenges.toMutableMap()
        val savedAwards = awards.toMutableMap(); val savedActivities = activities.toMutableMap()
        val savedSessions = sessions.toMutableMap()
        val savedCursors = cursors.toMutableMap()
        return try { block(this) } catch (failure: Throwable) {
            targets = savedTargets; usages = savedUsages; combined = savedCombined; challenges = savedChallenges
            awards = savedAwards; activities = savedActivities; sessions = savedSessions
            cursors = savedCursors
            throw failure
        } finally {
            transactions.release()
        }
    }
    override suspend fun target(packageName: String) = targets[packageName]
    override suspend fun targets() = targets.values.toList()
    override suspend fun putTarget(value: TargetConfig) { targets[value.packageName] = value }
    override suspend fun usage(packageName: String, day: String): DailyUsage? {
        onUsageRead?.let { hook -> onUsageRead = null; hook() }
        return usages[packageName to day]
    }
    override suspend fun putUsage(value: DailyUsage) {
        if (failNextUsage) { failNextUsage = false; error("injected failure") }
        usages[value.packageName to value.day] = value
    }
    override suspend fun combined(day: String) = combined[day]
    override suspend fun putCombined(value: CombinedUsage) { combined[value.day] = value }
    override suspend fun challenge(id: String) = challenges[id]
    override suspend fun insertChallenge(value: Challenge) { require(value.id !in challenges); challenges[value.id] = value }
    override suspend fun putChallenge(value: Challenge) { challenges[value.id] = value }
    override suspend fun cancelActiveChallenges() {
        challenges.replaceAll { _, value -> if (value.status == ChallengeStatus.ACTIVE) value.copy(status = ChallengeStatus.CANCELLED) else value }
    }
    override suspend fun cancelChallengesOutsideDay(day: String) {
        onDayReconciliation?.invoke()
        challenges.replaceAll { _, value ->
            if (value.status == ChallengeStatus.ACTIVE && value.day != day) value.copy(status = ChallengeStatus.CANCELLED) else value
        }
    }
    override suspend fun cursor(timelineId: String) = cursors[timelineId]
    override suspend fun putCursor(value: UsageCursor) { cursors[value.timelineId] = value }
    override suspend fun award(challengeId: String) = awards[challengeId]
    override suspend fun insertAward(value: Award) { require(value.challengeId !in awards); awards[value.challengeId] = value }
    override suspend fun insertActivity(value: ExerciseActivity) {
        if (failNextActivity) { failNextActivity = false; error("injected activity failure") }
        require(value.id !in activities); activities[value.id] = value
        onActivityWrite?.let { hook -> onActivityWrite = null; hook() }
    }
    override suspend fun activities() = activities.values.toList()
    override suspend fun session(packageName: String, day: String) = sessions[packageName to day]
    override suspend fun putSession(value: SessionState) { sessions[value.packageName to value.day] = value }
    override suspend fun clearVisibleHistory() {
        activities.clear()
        usages.replaceAll { _, value -> value.copy(historyVisible = false) }
        combined.replaceAll { _, value -> value.copy(historyVisible = false) }
    }
}
