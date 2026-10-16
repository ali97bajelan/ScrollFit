package com.example.scrollfit.platform.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.scrollfit.core.data.*
import com.example.scrollfit.core.time.WallClock
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CoveragePersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "t15-${UUID.randomUUID()}.db"
    private var today = LocalDate.parse("2026-10-16")
    private val clock = object : WallClock {
        override fun localDate() = today
        override fun epochMillis() = today.atStartOfDay(java.time.ZoneId.of("UTC")).toInstant().toEpochMilli()
    }
    private lateinit var database: ExerciseGateDatabase
    private fun repository() = DurableRepository(RoomStore(database), clock)
    @Before fun open() { database = ExerciseGateDatabase.open(context, name) }
    @After fun close() { database.close(); context.deleteDatabase(name) }
    private fun interval(start: Long, end: Long) = CoverageInterval("session-${today}-$start", "session", "boot",
        today.toString(), "UTC", start, end, clock.epochMillis() + start, clock.epochMillis() + end, setOf("removed.app"))

    @Test fun coverageAndScopeSurviveReopenAndMergeAdjacentIntervals() = runBlocking {
        repository().recordCoverage(interval(0, 1000))
        repository().recordCoverage(interval(1000, 2000))
        database.close(); database = ExerciseGateDatabase.open(context, name)
        val row = repository().visibleHistory().coverage.single()
        assertEquals(2000L, row.endElapsedMs)
        assertEquals(setOf("removed.app"), row.selectedPackages)
    }

    @Test fun failedScopeInsertRollsBackIntervalUpdate() = runBlocking {
        val repo = repository()
        repo.recordCoverage(interval(0, 1000))
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER t15_scope_failure BEFORE INSERT ON monitoring_coverage_targets BEGIN SELECT RAISE(ABORT, 'injected'); END")
        try { repo.recordCoverage(interval(1000, 2000)); fail("scope write should fail") }
        catch (_: android.database.sqlite.SQLiteException) { }
        assertEquals(1000L, repo.visibleHistory().coverage.single().endElapsedMs)
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER t15_scope_failure")
    }

    @Test fun deletionCutoffRejectsLateWriteAfterReopen() = runBlocking {
        val repo = repository()
        repo.saveTarget(TargetConfig("app"))
        repo.recordCoverage(interval(0, 1000))
        repo.clearHistory()
        database.close(); database = ExerciseGateDatabase.open(context, name)
        val reopened = repository()
        reopened.recordCoverage(interval(1000, 2000))
        reopened.recordUsage("app", today.toString(), 1000, 0)
        reopened.recordActivity(ExerciseActivity("late", null, today.toString(),
            com.example.scrollfit.core.exercise.Exercise.SQUAT, 10, 5000, clock.epochMillis()))
        assertTrue(reopened.visibleHistory().coverage.isEmpty())
        assertTrue(reopened.visibleHistory().usage.isEmpty())
        assertTrue(reopened.visibleHistory().combined.isEmpty())
        assertTrue(reopened.visibleHistory().activities.isEmpty())
        today = today.plusDays(1)
        reopened.recordCoverage(interval(2000, 3000))
        assertEquals(today.toString(), reopened.visibleHistory().coverage.single().day)
    }
}
