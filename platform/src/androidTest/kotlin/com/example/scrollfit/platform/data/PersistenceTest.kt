package com.example.scrollfit.platform.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.scrollfit.core.data.*
import com.example.scrollfit.core.exercise.Exercise
import com.example.scrollfit.core.time.WallClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.UUID

/** File-backed Room checks. Must run on Android; the JVM adapter is not a substitute. */
@RunWith(AndroidJUnit4::class)
class PersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "t03-${UUID.randomUUID()}.db"
    private val day = "2026-10-12"
    private val clock = object : WallClock {
        override fun localDate(): LocalDate = LocalDate.parse(day)
        override fun epochMillis() = 1234L
    }
    private lateinit var database: ExerciseGateDatabase
    private lateinit var repo: DurableRepository
    @Before fun open() { database = ExerciseGateDatabase.open(context, name); repo = repository() }
    @After fun close() { database.close(); context.deleteDatabase(name) }
    private fun repository() = DurableRepository(RoomStore(database), clock)
    private suspend fun prepare() {
        repo.saveTarget(TargetConfig("app"))
        repo.startChallenge(Challenge("one", "app", day, Exercise.PUSH_UP, ChallengeMode.ADVANCE, 2, 120_000, 1000))
    }

    @Test fun reopenRetainsAwardUsageConfigurationAndSession() = runBlocking {
        prepare()
        val award = repo.awardCompletedChallenge("one", 2)
        repo.recordUsage("app", day, 1000, 2000)
        repo.saveSession(repo.session("app").copy(promptPending = true))
        database.close()
        database = ExerciseGateDatabase.open(context, name); repo = repository()
        assertEquals(TargetConfig("app"), repo.targets().single())
        assertEquals(award, repo.awardCompletedChallenge("one", 2))
        assertEquals(DailyUsage("app", day, 1000, 2000, 120_000), repo.dailyUsage("app"))
        assertEquals(3000L, repo.combinedUsage().consumedMs)
        assertEquals(SessionState("app", day, 3000, true), repo.session("app"))
    }

    @Test fun concurrentCallbacksProduceOneLedgerRowAndWalletIncrement() = runBlocking {
        prepare()
        val awards = coroutineScope {
            (1..24).map { async(Dispatchers.Default) { repo.awardCompletedChallenge("one", 2) } }.awaitAll()
        }
        assertNotNull(awards.first())
        assertEquals(1, awards.toSet().size)
        assertEquals(120_000L, repo.dailyUsage("app").awardedMs)
        val count = database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM award_ledger").use {
            it.moveToFirst(); it.getLong(0)
        }
        assertEquals(1L, count)
    }

    @Test fun failureAfterLedgerInsertRollsBackEveryRecord() = runBlocking {
        prepare()
        try {
            RoomStore(database).transaction {
                insertAward(Award("one", "app", day, 120_000, 1234))
                putUsage(DailyUsage("app", day, awardedMs = 120_000))
                putChallenge(requireNotNull(challenge("one")).copy(status = ChallengeStatus.COMPLETED))
                error("injected transaction failure")
            }
            fail("transaction must throw")
        } catch (expected: IllegalStateException) { assertEquals("injected transaction failure", expected.message) }
        assertNull(database.records().award("one"))
        assertEquals(0L, repo.dailyUsage("app").awardedMs)
        assertEquals(ChallengeStatus.ACTIVE, repo.challenge("one")!!.status)
        assertNotNull(repo.awardCompletedChallenge("one", 2))
    }

    @Test fun reAddSettingsAndClearHistoryDoNotReplenishToday() = runBlocking {
        prepare()
        val award = repo.awardCompletedChallenge("one", 2)
        repo.recordUsage("app", day, 1000, 2000)
        repo.recordActivity(ExerciseActivity("activity", "one", day, Exercise.PUSH_UP, 2, 10_000, 1234))
        repo.removeTarget("app")
        repo.saveTarget(TargetConfig("app", baseAllowanceMs = 0, dailyAwardCapMs = 0))
        repo.clearHistory()
        database.close(); database = ExerciseGateDatabase.open(context, name); repo = repository()
        assertTrue(repo.activities().isEmpty())
        assertEquals(120_000L, repo.dailyUsage("app").awardedMs)
        assertEquals(2000L, repo.dailyUsage("app").earnedConsumedMs)
        assertEquals(3000L, repo.combinedUsage().consumedMs)
        assertEquals(3000L, repo.session("app").consumedMs)
        assertFalse(repo.dailyUsage("app").historyVisible)
        assertEquals(0L, repo.targets().single().dailyAwardCapMs)
        assertEquals(award, repo.awardCompletedChallenge("one", 2))
    }

    @Test fun duplicateChallengeInsertCannotOverwriteSnapshot() = runBlocking {
        prepare()
        try {
            repo.startChallenge(requireNotNull(repo.challenge("one")).copy(rewardMs = 1))
            fail("primary key must reject duplicate")
        } catch (_: android.database.sqlite.SQLiteConstraintException) { }
        assertEquals(120_000L, repo.challenge("one")!!.rewardMs)
    }

    @Test fun restartRecoveryCancelsIncompleteAndRetainsCompletedAward() = runBlocking {
        prepare()
        val award = repo.awardCompletedChallenge("one", 2)
        repo.startChallenge(Challenge("two", "app", day, Exercise.SQUAT, ChallengeMode.JUST_IN_TIME, 10, 120_000, 1000))
        database.close(); database = ExerciseGateDatabase.open(context, name); repo = repository()
        repo.cancelInterruptedChallenges()
        assertEquals(ChallengeStatus.CANCELLED, repo.challenge("two")!!.status)
        assertNull(repo.awardCompletedChallenge("two", 10))
        assertEquals(award, repo.awardCompletedChallenge("one", 2))
    }

    @Test fun unsupportedDatabaseVersionFailsWithoutDeletingBudget() = runBlocking {
        prepare()
        repo.recordUsage("app", day, 1000, 0)
        database.openHelper.writableDatabase.execSQL("PRAGMA user_version = 3")
        database.close(); database = ExerciseGateDatabase.open(context, name); repo = repository()
        try {
            repo.dailyUsage("app")
            fail("missing migration must fail")
        } catch (_: IllegalStateException) { }
        database.close()
        android.database.sqlite.SQLiteDatabase.openDatabase(
            context.getDatabasePath(name).path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY,
        ).use { raw ->
            raw.rawQuery("SELECT baseConsumedMs FROM daily_usage WHERE packageName = 'app'", null).use {
                assertTrue(it.moveToFirst()); assertEquals(1000L, it.getLong(0))
            }
        }
    }
}
