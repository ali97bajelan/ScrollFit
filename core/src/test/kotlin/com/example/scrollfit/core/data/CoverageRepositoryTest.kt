package com.example.scrollfit.core.data

import com.example.scrollfit.core.time.WallClock
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

class CoverageRepositoryTest {
    private var date = LocalDate.parse("2026-10-16")
    private val store = MemoryStore()
    private val clock = object : WallClock {
        override fun localDate() = date
        override fun epochMillis() = date.atStartOfDay(java.time.ZoneId.of("UTC")).toInstant().toEpochMilli()
    }
    private val repository = DurableRepository(store, clock)
    private fun interval(start: Long, end: Long, session: String = "session", scope: Set<String> = setOf("app"),
        day: String = date.toString()) = CoverageInterval("$session-$day-$start", session, "boot", day, "UTC",
        start, end, clock.epochMillis() + start, clock.epochMillis() + end, scope)

    @Test fun mergeAdjacentIntervalsWithoutReplayingDuration() = runSuspend {
        repository.recordCoverage(interval(0, 1000))
        repository.recordCoverage(interval(1000, 2000))
        repository.recordCoverage(interval(0, 1000))
        assertEquals(1, repository.visibleHistory().coverage.size)
        assertEquals(2000L, repository.visibleHistory().coverage.single().endElapsedMs)
        repository.recordCoverage(interval(3000, 4000))
        repository.recordCoverage(interval(4000, 5000, session = "other"))
        repository.recordCoverage(interval(5000, 6000, scope = setOf("other")))
        assertEquals(4, repository.visibleHistory().coverage.size)
    }

    @Test fun lateWritesAfterClearRemainHiddenAcrossRepositoryRestart() = runSuspend {
        repository.recordCoverage(interval(0, 1000))
        repository.clearHistory()
        repository.recordCoverage(interval(1000, 2000))
        val reopened = DurableRepository(store, clock)
        reopened.recordCoverage(interval(2000, 3000))
        assertTrue(reopened.visibleHistory().coverage.isEmpty())
        date = date.plusDays(1)
        reopened.recordCoverage(interval(3000, 4000, day = date.toString()))
        assertEquals(date.toString(), reopened.visibleHistory().coverage.single().day)
    }

    @Test fun invalidatedCoverageTransactionRollsBack() = runSuspend {
        try {
            repository.recordCoverage(interval(0, 1000)) { false }
            fail("expected invalidation")
        } catch (_: CoverageInvalidated) { }
        assertTrue(repository.visibleHistory().coverage.isEmpty())
    }

    @Test fun coverageDoesNotChangeEnforcementRecords() = runSuspend {
        repository.saveTarget(TargetConfig("app"))
        store.transaction { putCursor(UsageCursor("boot", 17)) }
        repository.recordCoverage(interval(0, 1000))
        assertEquals(0L, repository.dailyUsage("app").baseConsumedMs)
        assertEquals(0L, repository.combinedUsage().consumedMs)
        assertEquals(0L, repository.session("app").consumedMs)
        assertEquals(UsageCursor("boot", 17), store.transaction { cursor("boot") })
    }

    @Test fun lateAccountingAndActivityRemainHiddenAfterDeletion() = runSuspend {
        repository.saveTarget(TargetConfig("app"))
        repository.clearHistory()
        repository.recordUsage("app", date.toString(), 1000, 0)
        repository.recordActivity(ExerciseActivity("late", null, date.toString(),
            com.example.scrollfit.core.exercise.Exercise.SQUAT, 10, 5000, clock.epochMillis()))
        val reopened = DurableRepository(store, clock)
        assertTrue(reopened.visibleHistory().usage.isEmpty())
        assertTrue(reopened.visibleHistory().combined.isEmpty())
        assertTrue(reopened.visibleHistory().activities.isEmpty())
        assertEquals(1000L, reopened.dailyUsage("app").baseConsumedMs)
    }

    private fun runSuspend(block: suspend () -> Unit) {
        var failure: Throwable? = null
        block.startCoroutine(object : Continuation<Unit> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<Unit>) { failure = result.exceptionOrNull() }
        })
        failure?.let { throw it }
    }
}
