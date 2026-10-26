package com.example.scrollfit.monitoring

import java.util.Collections

/** A closed set of monitor phases. Values never contain an event, package, or exception payload. */
enum class MonitorFailurePhase {
    BOOT,
    REGISTRATION,
    PREFERENCES,
    METADATA,
    RECONCILIATION,
    QUEUE,
    BUDGET,
    COVERAGE,
    OVERLAY,
    NAVIGATION,
}

/** Sanitized failure identifiers safe to retain in process-local diagnostics. */
enum class MonitorFailureCode {
    UNKNOWN,
    BOOT_IDENTITY,
    BUDGET_ENGINE_INITIALIZATION,
    PROTECTED_PACKAGE_LOOKUP,
    COVERAGE_INITIALIZATION,
    SAFE_PACKAGE_LOOKUP,
    USAGE_LISTENER_REGISTER,
    SCREEN_RECEIVER_REGISTER,
    PACKAGE_RECEIVER_REGISTER,
    SYSTEM_RECEIVER_REGISTER,
    USAGE_LISTENER_UNREGISTER,
    SCREEN_RECEIVER_UNREGISTER,
    PACKAGE_RECEIVER_UNREGISTER,
    SYSTEM_RECEIVER_UNREGISTER,
    PREFERENCE_STREAM,
    PREFERENCE_WRITE,
    FOREGROUND_RECONCILIATION,
    EVENT_PACKAGE_METADATA,
    SELECTED_PACKAGE_LOOKUP,
    BUDGET_OPERATION,
    COVERAGE_WRITE,
    COMMAND_PROCESSING,
    QUEUE_OVERFLOW,
    OVERLAY_PRESENT,
    OVERLAY_REMOVE,
    GLOBAL_ACTION,
    CHALLENGE_LAUNCH,
    APP_LAUNCH,
    SERVICE_INTERRUPTED,
    OWNER_ALREADY_CLAIMED,
    OVERLAY_FAILURE,
}

data class MonitorFailureDetail(val phase: MonitorFailurePhase, val code: MonitorFailureCode)

enum class MonitorDiagnosticKind { FAILURE, RETRY }
enum class MonitorRetryResult { STARTED, SUCCEEDED, FAILED }

/** One bounded, sanitized event. Optional fields are populated only for the matching kind. */
data class MonitorDiagnosticRecord(
    val elapsedRealtimeMs: Long,
    val kind: MonitorDiagnosticKind,
    val phase: MonitorFailurePhase? = null,
    val code: MonitorFailureCode? = null,
    val retryAttempt: Int? = null,
    val retryResult: MonitorRetryResult? = null,
)

data class MonitorLatencyAggregate(val count: Long, val totalMs: Long, val maxMs: Long)

/** Immutable diagnostic view. It contains no package names, event text, frames, or exceptions. */
data class MonitorDiagnosticSnapshot(
    val scope: String,
    val records: List<MonitorDiagnosticRecord>,
    val failureCountsByPhase: Map<MonitorFailurePhase, Long>,
    val retryAttempts: Int,
    val retrySuccesses: Int,
    val retryFailures: Int,
    val lastFailureElapsedRealtimeMs: Long?,
    val lastSuccessfulReconciliationAgeMs: Long?,
    val lastSelectedAppObservationAgeMs: Long?,
    val queueHighWaterMark: Int,
    val droppedCommands: Long,
    val processingLatency: MonitorLatencyAggregate,
)

/** Process-local diagnostics with fixed memory use and non-suspending write methods. */
class MonitorDiagnostics {
    private val lock = Any()
    private val ring = arrayOfNulls<MonitorDiagnosticRecord>(CAPACITY)
    private val failureCounts = LongArray(MonitorFailurePhase.entries.size)
    private var nextIndex = 0
    private var recordCount = 0
    private var retryAttempts = 0
    private var retrySuccesses = 0
    private var retryFailures = 0
    private var lastFailureElapsedRealtimeMs: Long? = null
    private var lastSuccessfulReconciliationElapsedRealtimeMs: Long? = null
    private var lastProcessedObservationElapsedRealtimeMs: Long? = null
    private var queueHighWaterMark = 0
    private var droppedCommands = 0L
    private var processingLatencyCount = 0L
    private var processingLatencyTotalMs = 0L
    private var processingLatencyMaxMs = 0L

    fun recordFailure(nowElapsedRealtimeMs: Long, detail: MonitorFailureDetail) = synchronized(lock) {
        val now = nowElapsedRealtimeMs.coerceAtLeast(0)
        failureCounts[detail.phase.ordinal] = incrementSaturated(failureCounts[detail.phase.ordinal])
        lastFailureElapsedRealtimeMs = now
        append(MonitorDiagnosticRecord(now, MonitorDiagnosticKind.FAILURE, detail.phase, detail.code))
    }

    fun retryStarted(nowElapsedRealtimeMs: Long): Int = synchronized(lock) {
        retryAttempts = incrementSaturated(retryAttempts)
        append(MonitorDiagnosticRecord(nowElapsedRealtimeMs.coerceAtLeast(0), MonitorDiagnosticKind.RETRY,
            retryAttempt = retryAttempts, retryResult = MonitorRetryResult.STARTED))
        retryAttempts
    }

    fun retryFinished(nowElapsedRealtimeMs: Long, attempt: Int, succeeded: Boolean) = synchronized(lock) {
        if (succeeded) retrySuccesses = incrementSaturated(retrySuccesses)
        else retryFailures = incrementSaturated(retryFailures)
        append(MonitorDiagnosticRecord(nowElapsedRealtimeMs.coerceAtLeast(0), MonitorDiagnosticKind.RETRY,
            retryAttempt = attempt.coerceAtLeast(0),
            retryResult = if (succeeded) MonitorRetryResult.SUCCEEDED else MonitorRetryResult.FAILED))
    }

    fun processedObservation(elapsedRealtimeMs: Long) = synchronized(lock) {
        lastProcessedObservationElapsedRealtimeMs = elapsedRealtimeMs.takeIf { it >= 0 }
    }

    fun successfulReconciliation(elapsedRealtimeMs: Long) = synchronized(lock) {
        lastSuccessfulReconciliationElapsedRealtimeMs = elapsedRealtimeMs.takeIf { it >= 0 }
    }

    fun queueDepth(depth: Int) = synchronized(lock) {
        queueHighWaterMark = maxOf(queueHighWaterMark, depth.coerceAtLeast(0))
    }

    fun commandDropped() = synchronized(lock) {
        droppedCommands = incrementSaturated(droppedCommands)
    }

    fun processingLatency(durationMs: Long) = synchronized(lock) {
        val duration = durationMs.coerceAtLeast(0)
        processingLatencyCount = incrementSaturated(processingLatencyCount)
        processingLatencyTotalMs = addSaturated(processingLatencyTotalMs, duration)
        processingLatencyMaxMs = maxOf(processingLatencyMaxMs, duration)
    }

    fun snapshot(nowElapsedRealtimeMs: Long): MonitorDiagnosticSnapshot = synchronized(lock) {
        val oldestIndex = if (recordCount == CAPACITY) nextIndex else 0
        val records = ArrayList<MonitorDiagnosticRecord>(recordCount)
        repeat(recordCount) { offset ->
            ring[(oldestIndex + offset) % CAPACITY]?.let(records::add)
        }
        val counts = MonitorFailurePhase.entries.associateWith { failureCounts[it.ordinal] }
        val lastProcessed = lastProcessedObservationElapsedRealtimeMs
        MonitorDiagnosticSnapshot(
            scope = PROCESS_SCOPE,
            records = Collections.unmodifiableList(records),
            failureCountsByPhase = Collections.unmodifiableMap(counts),
            retryAttempts = retryAttempts,
            retrySuccesses = retrySuccesses,
            retryFailures = retryFailures,
            lastFailureElapsedRealtimeMs = lastFailureElapsedRealtimeMs,
            lastSuccessfulReconciliationAgeMs = age(nowElapsedRealtimeMs,
                lastSuccessfulReconciliationElapsedRealtimeMs),
            lastSelectedAppObservationAgeMs = lastProcessed?.let { observed ->
                nowElapsedRealtimeMs.takeIf { it >= observed }?.minus(observed)
            },
            queueHighWaterMark = queueHighWaterMark,
            droppedCommands = droppedCommands,
            processingLatency = MonitorLatencyAggregate(
                processingLatencyCount, processingLatencyTotalMs, processingLatencyMaxMs),
        )
    }

    private fun age(nowElapsedRealtimeMs: Long, observedElapsedRealtimeMs: Long?): Long? =
        observedElapsedRealtimeMs?.let { observed ->
            nowElapsedRealtimeMs.takeIf { it >= observed }?.minus(observed)
        }

    private fun append(record: MonitorDiagnosticRecord) {
        ring[nextIndex] = record
        nextIndex = (nextIndex + 1) % CAPACITY
        recordCount = minOf(recordCount + 1, CAPACITY)
    }

    private fun incrementSaturated(value: Long) = if (value == Long.MAX_VALUE) value else value + 1
    private fun incrementSaturated(value: Int) = if (value == Int.MAX_VALUE) value else value + 1
    private fun addSaturated(left: Long, right: Long) =
        if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right

    companion object {
        const val CAPACITY = 128
        const val PROCESS_SCOPE = "Since process start"
    }
}
