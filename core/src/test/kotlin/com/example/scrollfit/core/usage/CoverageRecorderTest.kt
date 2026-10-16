package com.example.scrollfit.core.usage

import com.example.scrollfit.core.data.CoverageInterval
import com.example.scrollfit.core.data.CoverageInvalidated
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

class CoverageRecorderTest {
    private var elapsed = 0L
    private var epoch = Instant.parse("2026-10-16T23:59:59.500Z").toEpochMilli()
    private val written = mutableListOf<CoverageInterval>()
    private val recorder = CoverageRecorder("boot", "session") { interval, eligible ->
        if (!eligible()) error("invalidated")
        written += interval
    }
    private fun signal() = MonitorSignal(MonitorSample(elapsed, epoch, "UTC"), null, true, true, true, false)
    private val ready = MonitorDecision(MonitoringHealth.HEALTHY)

    @Test fun confirmsIntervalsOnlyWithinGapAndSplitsMidnight() = runSuspend {
        recorder.observe(signal(), ready, setOf("app"), true)
        elapsed += 1000; epoch += 1000
        recorder.observe(signal(), ready, setOf("app"), true)
        assertEquals(listOf(500L, 500L), written.map { it.endElapsedMs - it.startElapsedMs })
        assertEquals(listOf("2026-10-16", "2026-10-17"), written.map { it.day })
        assertEquals(0L, written[0].endEpochMs % 86_400_000L)
    }

    @Test fun duplicateAndScopeChangeCannotExtendTheOldAnchor() = runSuspend {
        recorder.observe(signal(), ready, setOf("app"), true)
        recorder.observe(signal(), ready, setOf("app"), true)
        assertTrue(written.isEmpty())
        elapsed += 1000; epoch += 1000
        recorder.observe(signal(), ready, setOf("other"), true)
        assertTrue(written.isEmpty())
        elapsed += 1000; epoch += 1000
        recorder.observe(signal(), ready, setOf("other"), true)
        assertEquals(setOf("other"), written.single().selectedPackages)
    }

    @Test fun invalidatedPendingCoverageCannotCommit() = runBlocking {
        val started = CompletableDeferred<() -> Boolean>()
        val finishWrite = CompletableDeferred<Unit>()
        val pending = CoverageRecorder("boot", "pending") { _, eligible ->
            started.complete(eligible)
            finishWrite.await()
            if (!eligible()) throw CoverageInvalidated()
        }
        pending.observe(signal(), ready, setOf("app"), true)
        elapsed += 1000; epoch += 1000
        val task = async {
            try { pending.observe(signal(), ready, setOf("app"), true); null }
            catch (failure: CoverageInvalidated) { failure }
        }
        val stillEligible = started.await()
        pending.invalidate()
        finishWrite.complete(Unit)
        assertNotNull(task.await())
        assertFalse(stillEligible())
    }

    @Test fun confirmsMaximumGapAndDriftBoundaries() = runSuspend {
        suspend fun runPair(gap: Long, drift: Long): Int {
            var spans = 0
            val local = CoverageRecorder("boot", "s-$gap-$drift") { _, eligible ->
                if (eligible()) spans++
            }
            val startEpoch = Instant.parse("2026-10-16T10:00:00Z").toEpochMilli()
            fun sample(elapsed: Long, wall: Long) = MonitorSignal(
                MonitorSample(elapsed, wall, "UTC"), null, true, true, true, false)
            local.observe(sample(0, startEpoch), ready, setOf("app"), true)
            local.observe(sample(gap, startEpoch + gap + drift), ready, setOf("app"), true)
            return spans
        }
        assertEquals(1, runPair(60_000, 0))
        assertEquals(1, runPair(90_000, 0))
        assertEquals(0, runPair(90_001, 0))
        assertEquals(1, runPair(10_000, 2_000))
        assertEquals(1, runPair(10_000, -2_000))
        assertEquals(0, runPair(10_000, 2_001))
        assertEquals(0, runPair(10_000, -2_001))
    }

    @Test fun unhealthyOrAccountingGapSignalsBreakCoverageAnchor() = runSuspend {
        val base = Instant.parse("2026-10-16T10:00:00Z").toEpochMilli()
        fun at(offset: Long) = MonitorSignal(MonitorSample(offset, base + offset, "UTC"), null, true, true, true, false)
        recorder.observe(at(0), ready, setOf("app"), true)
        recorder.observe(at(1000), ready.copy(issue = MonitorIssue.OBSERVATION_GAP, unmonitoredMs = 6000), setOf("app"), true)
        recorder.observe(at(2000), ready, setOf("app"), true)
        recorder.observe(at(3000), ready.copy(health = MonitoringHealth.PERMISSION_MISSING), setOf("app"), true)
        recorder.observe(at(4000), ready, setOf("app"), true)
        recorder.observe(at(5000), ready, emptySet(), true)
        recorder.observe(at(6000), ready, setOf("app"), true)
        assertTrue(written.isEmpty())
        recorder.observe(at(7000), ready, setOf("app"), true)
        assertEquals(1000L, written.single().endElapsedMs - written.single().startElapsedMs)
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
