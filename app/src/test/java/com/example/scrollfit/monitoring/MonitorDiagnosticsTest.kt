package com.example.scrollfit.monitoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorDiagnosticsTest {
    @Test fun ringRetainsLast128SanitizedRecords() {
        val diagnostics = MonitorDiagnostics()
        repeat(200) { elapsed ->
            diagnostics.recordFailure(elapsed.toLong(), MonitorFailureDetail(
                MonitorFailurePhase.RECONCILIATION, MonitorFailureCode.FOREGROUND_RECONCILIATION))
        }

        val snapshot = diagnostics.snapshot(250)
        assertEquals(128, snapshot.records.size)
        assertEquals(72L, snapshot.records.first().elapsedRealtimeMs)
        assertEquals(199L, snapshot.records.last().elapsedRealtimeMs)
        assertEquals(200L, snapshot.failureCountsByPhase.getValue(MonitorFailurePhase.RECONCILIATION))
        assertEquals(199L, snapshot.lastFailureElapsedRealtimeMs)
        assertEquals(MonitorDiagnostics.PROCESS_SCOPE, snapshot.scope)
    }

    @Test fun snapshotSchemaDoesNotRetainPrivateEventPayloads() {
        val diagnostics = MonitorDiagnostics()
        diagnostics.recordFailure(10, MonitorFailureDetail(
            MonitorFailurePhase.METADATA, MonitorFailureCode.EVENT_PACKAGE_METADATA))
        diagnostics.retryStarted(11)
        diagnostics.retryFinished(12, 1, succeeded = false)

        val snapshot = diagnostics.snapshot(30)
        val rendered = snapshot.toString()
        assertFalse(rendered.contains("com.private.app"))
        assertFalse(rendered.contains("screen text"))
        val schema = MonitorDiagnosticRecord::class.java.declaredFields.map { it.name } +
            MonitorDiagnosticSnapshot::class.java.declaredFields.map { it.name }
        assertFalse(schema.any { name ->
            listOf("package", "event", "text", "frame", "camera", "stack", "exception", "payload")
                .any { privateField -> name.contains(privateField, ignoreCase = true) }
        })
        assertTrue(snapshot.records.all { it.phase != null || it.retryResult != null })
    }

    @Test fun observationAgeUsesMonotonicTimeAndBecomesUnknownAcrossReset() {
        val diagnostics = MonitorDiagnostics()
        diagnostics.processedObservation(8_000)
        diagnostics.successfulReconciliation(7_000)
        assertEquals(1_500L, diagnostics.snapshot(9_500).lastSelectedAppObservationAgeMs)
        assertEquals(2_500L, diagnostics.snapshot(9_500).lastSuccessfulReconciliationAgeMs)
        assertNull(diagnostics.snapshot(7_000).lastSelectedAppObservationAgeMs)
        assertNull(MonitorDiagnostics().snapshot(10_000).lastSelectedAppObservationAgeMs)
    }

    @Test fun queueDropsAndLatencyAggregateWithoutGrowingRecordHistory() {
        val diagnostics = MonitorDiagnostics()
        repeat(10_000) {
            diagnostics.queueDepth((it % 64) + 1)
            diagnostics.commandDropped()
            diagnostics.processingLatency((it % 4).toLong())
        }

        val snapshot = diagnostics.snapshot(20_000)
        assertEquals(0, snapshot.records.size)
        assertEquals(64, snapshot.queueHighWaterMark)
        assertEquals(10_000L, snapshot.droppedCommands)
        assertEquals(10_000L, snapshot.processingLatency.count)
        assertEquals(15_000L, snapshot.processingLatency.totalMs)
        assertEquals(3L, snapshot.processingLatency.maxMs)
    }

    @Test fun retriesAndResultsAreTypedBoundedEvents() {
        val diagnostics = MonitorDiagnostics()
        val attempt = diagnostics.retryStarted(1)
        diagnostics.retryFinished(2, attempt, succeeded = false)
        val retried = diagnostics.retryStarted(3)
        diagnostics.retryFinished(4, retried, succeeded = true)

        val snapshot = diagnostics.snapshot(5)
        assertEquals(2, snapshot.retryAttempts)
        assertEquals(1, snapshot.retryFailures)
        assertEquals(1, snapshot.retrySuccesses)
        assertEquals(listOf(MonitorRetryResult.STARTED, MonitorRetryResult.FAILED,
            MonitorRetryResult.STARTED, MonitorRetryResult.SUCCEEDED),
            snapshot.records.map { it.retryResult })
    }
}
