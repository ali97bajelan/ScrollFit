package com.example.scrollfit.platform.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.scrollfit.core.budget.*
import com.example.scrollfit.core.data.*
import com.example.scrollfit.core.exercise.Exercise
import com.example.scrollfit.core.time.*
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.UUID

/** Prepared Android checks: JVM tests do not prove Room durability or migration compatibility. */
@RunWith(AndroidJUnit4::class)
class BudgetPersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "t04-${UUID.randomUUID()}.db"
    private val day = "2026-10-12"
    private val wall = object : WallClock {
        override fun localDate() = LocalDate.parse(day)
        override fun epochMillis() = 1000L
    }
    private val monotonic = object : MonotonicClock { override fun elapsedRealtimeMillis() = 10_000L }
    private lateinit var db: ExerciseGateDatabase
    private fun engine() = BudgetEngine(RoomStore(db), wall, monotonic, "same-boot") { AppPreferences() }
    private fun repo() = DurableRepository(RoomStore(db), wall)
    @Before fun open() { db = ExerciseGateDatabase.open(context, name) }
    @After fun close() { db.close(); context.deleteDatabase(name) }
    private suspend fun prepare() { repo().saveTarget(TargetConfig("app")) }
    private suspend fun start(id: String) {
        val engine = engine()
        val offer = (engine.offer("app", Exercise.PUSH_UP, ChallengeMode.ADVANCE) as OfferResult.Available).offer
        assertTrue(engine.start(id, offer) is StartResult.Started)
    }

    @Test fun reopenAndClearHistoryKeepCursorAndAllSpendingRecords() = runBlocking {
        prepare(); engine().spend(UsageInterval("app", day, 0, 1000)); repo().clearHistory()
        db.close(); db = ExerciseGateDatabase.open(context, name)
        assertEquals(0L, engine().spend(UsageInterval("app", day, 0, 1000)).spentMs)
        assertEquals(1000L, repo().dailyUsage("app").baseConsumedMs)
        assertEquals(1000L, repo().combinedUsage().consumedMs)
        assertEquals(1000L, repo().session("app").consumedMs)
        assertEquals(UsageCursor("same-boot", 1000), db.records().cursor("same-boot")!!.value)
    }

    @Test fun concurrentIntervalCallbacksSpendOnce() = runBlocking {
        prepare()
        val results = coroutineScope {
            (1..24).map { async(Dispatchers.Default) { engine().spend(UsageInterval("app", day, 0, 1000)) } }.awaitAll()
        }
        assertEquals(1000L, results.sumOf { it.spentMs })
        assertEquals(1000L, repo().combinedUsage().consumedMs)
    }

    @Test fun concurrentCompletionsReturnOneAward() = runBlocking {
        prepare(); start("one")
        val results = coroutineScope {
            (1..24).map { async(Dispatchers.Default) { engine().complete("one", 2) } }.awaitAll()
        }
        assertEquals(1, results.toSet().size)
        assertTrue(results.first() is CompletionResult.Awarded)
        assertEquals(120_000L, repo().dailyUsage("app").awardedMs)
    }

    @Test fun competingChallengesCannotExceedAppAwardCap() = runBlocking {
        prepare(); repo().saveTarget(TargetConfig("app", dailyAwardCapMs = 150_000))
        start("one"); start("two")
        val results = coroutineScope {
            listOf("one", "two").map { async(Dispatchers.Default) { engine().complete(it, 2) } }.awaitAll()
        }
        assertEquals(1, results.count { it is CompletionResult.Awarded })
        assertEquals(120_000L, repo().dailyUsage("app").awardedMs)
    }

    @Test fun sessionOverflowRollsBackCursorAndWallets() = runBlocking {
        prepare(); repo().saveSession(SessionState("app", day, Long.MAX_VALUE))
        try { engine().spend(UsageInterval("app", day, 0, 1000)); fail() } catch (_: ArithmeticException) { }
        assertNull(db.records().cursor("same-boot"))
        assertEquals(0L, repo().dailyUsage("app").baseConsumedMs)
        assertEquals(0L, repo().combinedUsage().consumedMs)
    }

    @Test fun v1ShapeFixtureMigrationPreservesBudgetAndAwardIdentity() = runBlocking {
        prepare(); start("one"); val award = engine().complete("one", 2)
        engine().spend(UsageInterval("app", day, 0, 1000))
        // v1 differs only by absence of usage_cursors. This fixture tests real migration
        // and Room v2 validation; authentic exported-v1 MigrationTestHelper awaits KSP.
        db.openHelper.writableDatabase.execSQL("DROP TABLE usage_cursors")
        db.openHelper.writableDatabase.execSQL("PRAGMA user_version = 1")
        db.close(); db = ExerciseGateDatabase.open(context, name)
        assertEquals(1000L, repo().combinedUsage().consumedMs)
        assertEquals(120_000L, repo().dailyUsage("app").awardedMs)
        assertEquals(award, engine().complete("one", 2))
        assertNull(db.records().cursor("same-boot"))
        assertEquals(1000L, engine().spend(UsageInterval("app", day, 1000, 2000)).spentMs)
    }
}
