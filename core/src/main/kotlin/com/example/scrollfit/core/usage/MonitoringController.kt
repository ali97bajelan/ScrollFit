package com.example.scrollfit.core.usage

import com.example.scrollfit.core.budget.*
import java.time.Instant
import java.time.ZoneId

data class MonitorSample(val elapsedMs: Long, val epochMs: Long, val zoneId: String) {
    init { require(elapsedMs >= 0); ZoneId.of(zoneId) }
    val day get() = Instant.ofEpochMilli(epochMs).atZone(ZoneId.of(zoneId)).toLocalDate()
}
data class MonitorSignal(
    val sample: MonitorSample, val foreground: String?, val enabled: Boolean,
    val usageAccess: Boolean, val connected: Boolean, val interactive: Boolean,
    val ownUiVisible: Boolean = false, val foregroundKnown: Boolean = true,
)
enum class MonitorIssue { NONE, UNKNOWN_FOREGROUND, OBSERVATION_GAP, CLOCK_CHANGED }
data class MonitorDecision(
    val health: MonitoringHealth, val foreground: String? = null, val budget: Budget? = null,
    val issue: MonitorIssue = MonitorIssue.NONE, val unmonitoredMs: Long = 0,
    val shouldTick: Boolean = false, val tickDelayMs: Long = 1000,
    val sessionAccess: SessionAccess? = null,
) {
    val blockedBy get() = sessionAccess?.blockedBy ?: when (budget?.blockedBy) {
        BudgetBlock.APP_BUDGET -> BlockReason.APP_BUDGET
        BudgetBlock.COMBINED_CEILING -> BlockReason.COMBINED_CEILING
        else -> null
    }
}

/** One serialized caller owns observations. Unknown process downtime is never backfilled. */
class MonitoringController(private val engine: BudgetEngine, private val protectedPackages: Set<String>) {
    private var previous: MonitorSample? = null
    private var chargeable: String? = null
    private var decision = MonitorDecision(MonitoringHealth.SERVICE_STOPPED)

    suspend fun observe(signal: MonitorSignal): MonitorDecision {
        val old = previous
        if (old != null && signal.sample.elapsedMs < old.elapsedMs) return decision
        var issue = MonitorIssue.NONE
        var gap = 0L
        if (old != null && chargeable != null) {
            val delta = signal.sample.elapsedMs - old.elapsedMs
            val predictedEpoch = Math.addExact(old.epochMs, delta)
            val drift = signal.sample.epochMs - predictedEpoch
            when {
                delta > MAX_OBSERVED_GAP_MS -> { issue = MonitorIssue.OBSERVATION_GAP; gap = delta }
                signal.sample.zoneId != old.zoneId || drift > 2000 || drift < -2000 -> {
                    issue = MonitorIssue.CLOCK_CHANGED; gap = delta
                }
                delta > 0 -> {
                    gap = chargeAcrossMidnight(requireNotNull(chargeable), old, signal.sample.elapsedMs)
                    if (gap > 0) issue = MonitorIssue.CLOCK_CHANGED
                }
            }
        }
        val health = when {
            !signal.enabled -> MonitoringHealth.DISABLED
            !signal.usageAccess -> MonitoringHealth.PERMISSION_MISSING
            !signal.connected || !signal.foregroundKnown -> MonitoringHealth.SERVICE_STOPPED
            else -> MonitoringHealth.HEALTHY
        }
        if (!signal.foregroundKnown && issue == MonitorIssue.NONE) issue = MonitorIssue.UNKNOWN_FOREGROUND
        val candidate = signal.foreground?.takeIf {
            health == MonitoringHealth.HEALTHY && signal.interactive && !signal.ownUiVisible && it !in protectedPackages
        }
        val access = candidate?.let { engine.sessionAccess(it) }
            ?.takeUnless { it.budget.blockedBy == BudgetBlock.TARGET_UNAVAILABLE }
        val budget = access?.budget
        // A blocked surface is never chargeable; re-read its budget on slow ticks for rollover/edits.
        chargeable = candidate?.takeIf { access != null && access.usableMs > 0 }
        previous = signal.sample
        decision = MonitorDecision(health, candidate?.takeIf { budget != null }, budget, issue, gap,
            shouldTick = budget != null,
            tickDelayMs = if (access != null && access.usableMs > 0) minOf(1000L, access.usableMs) else 1000,
            sessionAccess = access)
        return decision
    }

    /** Storage, queue or overlay failures discard the in-memory interval until fresh reconciliation. */
    fun abandon() { previous = null; chargeable = null; decision = MonitorDecision(MonitoringHealth.SERVICE_STOPPED) }

    private suspend fun chargeAcrossMidnight(app: String, start: MonitorSample, endElapsedMs: Long): Long {
        var cursor = start.elapsedMs
        var epoch = start.epochMs
        var unmonitored = 0L
        val zone = ZoneId.of(start.zoneId)
        while (cursor < endElapsedMs) {
            val day = Instant.ofEpochMilli(epoch).atZone(zone).toLocalDate()
            val midnight = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val length = minOf(endElapsedMs - cursor, midnight - epoch)
            check(length > 0)
            val result = engine.spendObserved(UsageInterval(app, day.toString(), cursor, cursor + length))
            if (result.disposition == SpendDisposition.WRONG_DAY) unmonitored += length
            cursor += length
            epoch += length
        }
        return unmonitored
    }

    companion object { const val MAX_OBSERVED_GAP_MS = 5000L }
}
