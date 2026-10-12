package com.example.scrollfit.platform.data

import androidx.room.withTransaction
import com.example.scrollfit.core.data.*

class RoomStore(private val database: ExerciseGateDatabase) : DurableStore {
    override suspend fun <T> transaction(block: suspend DataTransaction.() -> T): T = database.withTransaction {
        block(RoomTransaction(database.records()))
    }
}

private class RoomTransaction(private val dao: ExerciseGateDao) : DataTransaction {
    override suspend fun target(packageName: String) = dao.target(packageName)?.value
    override suspend fun targets() = dao.targets().map { it.value }
    override suspend fun putTarget(value: TargetConfig) = dao.putTarget(TargetRow(value))
    override suspend fun usage(packageName: String, day: String) = dao.usage(packageName, day)?.value
    override suspend fun putUsage(value: DailyUsage) = dao.putUsage(UsageRow(value))
    override suspend fun combined(day: String) = dao.combined(day)?.value
    override suspend fun putCombined(value: CombinedUsage) = dao.putCombined(CombinedRow(value))
    override suspend fun challenge(id: String) = dao.challenge(id)?.value
    override suspend fun insertChallenge(value: Challenge) = dao.insertChallenge(ChallengeRow(value))
    override suspend fun putChallenge(value: Challenge) = dao.putChallenge(ChallengeRow(value))
    override suspend fun cancelActiveChallenges() = dao.cancelActiveChallenges()
    override suspend fun award(challengeId: String) = dao.award(challengeId)?.value
    override suspend fun insertAward(value: Award) = dao.insertAward(AwardRow(value))
    override suspend fun insertActivity(value: ExerciseActivity) = dao.insertActivity(ActivityRow(value))
    override suspend fun activities() = dao.activities().map { it.value }
    override suspend fun session(packageName: String, day: String) = dao.session(packageName, day)?.value
    override suspend fun putSession(value: SessionState) = dao.putSession(SessionRow(value))
    override suspend fun clearVisibleHistory() {
        dao.deleteActivityHistory()
        dao.hideUsageHistory()
        dao.hideCombinedHistory()
    }
}
