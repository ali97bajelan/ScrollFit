package com.example.scrollfit.core.usage

import com.example.scrollfit.core.data.CoverageInterval
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicLong

class CoverageRecorder(
    private val timelineId: String,
    private val recordingSessionId: String,
    private val write: suspend (CoverageInterval, () -> Boolean) -> Unit,
) {
    private data class Anchor(val signal: MonitorSignal, val scope: Set<String>)
    @Volatile private var anchor: Anchor? = null
    private val generation = AtomicLong()
    private val stateLock = Any()

    suspend fun observe(
        signal: MonitorSignal,
        decision: MonitorDecision,
        selectedPackages: Set<String>,
        ownershipHeld: Boolean,
        eligible: () -> Boolean = { true },
    ) {
        val observedGeneration = generation.get()
        val now = Anchor(signal, selectedPackages.toSortedSet())
        val previous = anchor
        if (!healthy(signal, decision, now.scope, ownershipHeld, eligible)) {
            anchor = null
            return
        }
        if (previous == null) { setAnchorIfCurrent(observedGeneration, now, eligible); return }
        val before = previous.signal.sample
        val after = signal.sample
        if (after.elapsedMs <= before.elapsedMs) return
        val delta = after.elapsedMs - before.elapsedMs
        val drift = after.epochMs - (before.epochMs + delta)
        val continuity = delta in 1..MAX_GAP_MS && after.epochMs > before.epochMs && before.zoneId == after.zoneId &&
            previous.scope == now.scope && drift in -MAX_DRIFT_MS..MAX_DRIFT_MS
        if (!continuity) {
            setAnchorIfCurrent(observedGeneration, now, eligible)
            return
        }
        val allowed = { generation.get() == observedGeneration && eligible() }
        try {
            splitAcrossDays(before.epochMs, after.epochMs, before.elapsedMs, after.elapsedMs, before.zoneId)
                .forEach { (day, startElapsed, endElapsed, startEpoch, endEpoch) ->
                    write(CoverageInterval(
                        id = "$recordingSessionId-$day-$startElapsed", recordingSessionId = recordingSessionId,
                        timelineId = timelineId, day = day, zoneId = before.zoneId,
                        startElapsedMs = startElapsed, endElapsedMs = endElapsed,
                        startEpochMs = startEpoch, endEpochMs = endEpoch,
                        selectedPackages = now.scope,
                    ), allowed)
                }
            setAnchorIfCurrent(observedGeneration, now, eligible)
        } catch (failure: Throwable) {
            invalidate()
            throw failure
        }
    }

    fun invalidate() = synchronized(stateLock) {
        generation.incrementAndGet()
        anchor = null
    }

    private fun setAnchorIfCurrent(expectedGeneration: Long, value: Anchor, eligible: () -> Boolean) = synchronized(stateLock) {
        if (generation.get() == expectedGeneration && eligible()) anchor = value else anchor = null
    }

    private fun splitAcrossDays(
        startEpochMs: Long, endEpochMs: Long, startElapsedMs: Long, endElapsedMs: Long, zoneId: String,
    ): List<Span> {
        val zone = ZoneId.of(zoneId)
        val result = mutableListOf<Span>()
        var epoch = startEpochMs
        var elapsed = startElapsedMs
        while (epoch < endEpochMs) {
            val day = Instant.ofEpochMilli(epoch).atZone(zone).toLocalDate()
            val boundary = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val nextEpoch = minOf(endEpochMs, boundary)
            val nextElapsed = if (nextEpoch == endEpochMs) endElapsedMs else startElapsedMs + (nextEpoch - startEpochMs)
            if (nextElapsed > elapsed) result += Span(day.toString(), elapsed, nextElapsed, epoch, nextEpoch)
            epoch = nextEpoch
            elapsed = nextElapsed
        }
        return result
    }

    private data class Span(val day: String, val startElapsed: Long, val endElapsed: Long, val startEpoch: Long, val endEpoch: Long)

    private fun healthy(
        signal: MonitorSignal, decision: MonitorDecision, scope: Set<String>,
        ownershipHeld: Boolean, eligible: () -> Boolean,
    ) = signal.enabled && signal.usageAccess && signal.connected && signal.foregroundKnown &&
        decision.health == MonitoringHealth.HEALTHY && decision.issue == MonitorIssue.NONE &&
        decision.unmonitoredMs == 0L && ownershipHeld && scope.isNotEmpty() && eligible()

    companion object {
        const val MAX_GAP_MS = 90_000L
        const val MAX_DRIFT_MS = 2_000L
    }
}
