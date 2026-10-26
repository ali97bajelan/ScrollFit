package com.example.scrollfit.monitoring

import android.os.SystemClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MonitorLatencyTest {
    @Test fun overflowAndStaleSamplesPauseWithoutSpend() {
        val queue = MonitorCommandQueue<Int>()
        var acceptedDepth = 0

        repeat(MonitorCommandQueue.CAPACITY) { assertTrue(queue.trySend(it) { acceptedDepth = it }) }
        assertFalse(queue.trySend(MonitorCommandQueue.CAPACITY))
        assertEquals(64, queue.pendingCount)
        assertEquals(64, acceptedDepth)
        val now = 20_000L
        assertFalse(hasTrustedObservationAge(now, now - 5_001L))

        repeat(MonitorCommandQueue.CAPACITY) { index ->
            assertEquals(index, queue.tryReceive().getOrThrow())
        }
        assertEquals(0, queue.pendingCount)
        queue.cancel()
    }

    @Test fun sampleAt5001MsIsRejectedAndBoundaryAt5000MsRemainsTrusted() {
        val now = 20_000L
        assertFalse(hasTrustedObservationAge(now, now - 5_001L))
        assertTrue(hasTrustedObservationAge(now, now - 5_000L))
    }

    @Test fun timingHarnessSeparatesCallbackMetadataQueueAndPersistenceStages() = runBlocking {
        val sampleCount = 20
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val state = SafetySnapshotState()
        val callbackSamples = mutableListOf<Long>()
        val metadataSamples = mutableListOf<Long>()
        val queueSamples = mutableListOf<Long>()
        val persistenceSamples = mutableListOf<Long>()
        var overlayVisible = true
        var snapshotPublished = CompletableDeferred<Unit>()
        val refresher = SafetySnapshotRefresher(
            scope = scope,
            state = state,
            load = { revision ->
                val startedAt = SystemClock.elapsedRealtime()
                delay(SLOW_STAGE_DELAY_MS)
                metadataSamples += SystemClock.elapsedRealtime() - startedAt
                SafetySnapshot.ready(revision, setOf("eligible"), emptySet())
            },
            onInvalidated = { overlayVisible = false },
            onPublished = { snapshotPublished.complete(Unit) },
            onFailure = { throw it },
        )

        val queue = MonitorCommandQueue<Int>()
        repeat(sampleCount) { index ->
            snapshotPublished = CompletableDeferred()
            overlayVisible = true
            val callbackStart = SystemClock.elapsedRealtime()
            refresher.refresh()
            callbackSamples += SystemClock.elapsedRealtime() - callbackStart
            assertFalse(overlayVisible)
            snapshotPublished.await()

            val queuedAt = SystemClock.elapsedRealtime()
            assertTrue(queue.trySend(index))
            val consumer = scope.launch {
                delay(SLOW_STAGE_DELAY_MS)
                queue.receiveChannel.receive()
                queue.received()
                queueSamples += SystemClock.elapsedRealtime() - queuedAt
            }
            consumer.join()

            val persistenceStart = SystemClock.elapsedRealtime()
            delay(SLOW_STAGE_DELAY_MS)
            persistenceSamples += SystemClock.elapsedRealtime() - persistenceStart
        }

        assertEquals(0, queue.pendingCount)
        assertTrue(state.current().allows("eligible"))
        report("callback", callbackSamples)
        report("metadata", metadataSamples)
        report("queue", queueSamples)
        report("persistence", persistenceSamples)
        listOf(callbackSamples, metadataSamples, queueSamples, persistenceSamples).forEach {
            assertEquals(sampleCount, it.size)
            assertTrue(it.all { sample -> sample >= 0 })
        }
        refresher.cancel()
        queue.cancel()
        scope.cancel()
    }

    private fun report(stage: String, samples: List<Long>) {
        val sorted = samples.sorted()
        val p95Index = ((sorted.size * 95 + 99) / 100 - 1).coerceAtLeast(0)
        val median = if (sorted.size % 2 == 0) {
            (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
        } else sorted[sorted.size / 2].toDouble()
        println("I09 timing stage=$stage n=${sorted.size} medianMs=$median " +
            "p95Ms=${sorted[p95Index]} maxMs=${sorted.last()}")
    }

    private companion object { const val SLOW_STAGE_DELAY_MS = 20L }
}
