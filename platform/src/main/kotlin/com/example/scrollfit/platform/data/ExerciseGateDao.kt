package com.example.scrollfit.platform.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert

/** Internal: callers go through RoomStore.transaction, never assemble independent wallet writes. */
@Dao
interface ExerciseGateDao {
    @Query("SELECT * FROM targets WHERE packageName = :packageName")
    suspend fun target(packageName: String): TargetRow?
    @Query("SELECT * FROM targets ORDER BY packageName")
    suspend fun targets(): List<TargetRow>
    @Upsert suspend fun putTarget(value: TargetRow)
    @Query("SELECT * FROM daily_usage WHERE packageName = :packageName AND day = :day")
    suspend fun usage(packageName: String, day: String): UsageRow?
    @Upsert suspend fun putUsage(value: UsageRow)
    @Query("SELECT * FROM combined_usage WHERE day = :day")
    suspend fun combined(day: String): CombinedRow?
    @Upsert suspend fun putCombined(value: CombinedRow)
    @Query("SELECT * FROM challenges WHERE id = :id")
    suspend fun challenge(id: String): ChallengeRow?
    @Insert suspend fun insertChallenge(value: ChallengeRow)
    @Update suspend fun putChallenge(value: ChallengeRow)
    @Query("UPDATE challenges SET status = 'CANCELLED' WHERE status = 'ACTIVE'")
    suspend fun cancelActiveChallenges()
    @Query("SELECT * FROM award_ledger WHERE challengeId = :challengeId")
    suspend fun award(challengeId: String): AwardRow?
    @Insert suspend fun insertAward(value: AwardRow)
    @Insert suspend fun insertActivity(value: ActivityRow)
    @Query("SELECT * FROM exercise_activity ORDER BY recordedAtEpochMs, id")
    suspend fun activities(): List<ActivityRow>
    @Query("SELECT * FROM sessions WHERE packageName = :packageName AND day = :day")
    suspend fun session(packageName: String, day: String): SessionRow?
    @Upsert suspend fun putSession(value: SessionRow)
    @Query("DELETE FROM exercise_activity") suspend fun deleteActivityHistory()
    @Query("UPDATE daily_usage SET historyVisible = 0") suspend fun hideUsageHistory()
    @Query("UPDATE combined_usage SET historyVisible = 0") suspend fun hideCombinedHistory()
}
