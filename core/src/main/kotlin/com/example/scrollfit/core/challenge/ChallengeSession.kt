package com.example.scrollfit.core.challenge

import com.example.scrollfit.core.budget.BudgetEngine
import com.example.scrollfit.core.budget.CompletionResult
import com.example.scrollfit.core.budget.RewardBlock
import com.example.scrollfit.core.data.Challenge
import com.example.scrollfit.core.pose.CountingProgress
import com.example.scrollfit.core.time.MonotonicClock
import com.example.scrollfit.core.time.WallClock

enum class ChallengePhase { RUNNING, READY, SAVING, FINISHED, INTERRUPTED }

/** One camera run, one persisted identity. Never restore unfinished progress after interruption. */
class ChallengeSession(val challenge: Challenge, private val engine: BudgetEngine,
    private val wallClock: WallClock, private val clock: MonotonicClock) {
    @Volatile var phase = ChallengePhase.RUNNING; private set
    @Volatile var repetitions = 0; private set
    @Volatile var activeDurationMs = 0L; private set
    private var lastTimestamp = -1L
    private var previouslyTracked = false
    private var saved: CompletionResult? = null

    @Synchronized fun accept(progress: CountingProgress, timestampMs: Long) {
        if (phase != ChallengePhase.RUNNING || !checkDay()) return
        val now = clock.elapsedRealtimeMillis()
        if (timestampMs < 0 || timestampMs <= lastTimestamp || timestampMs > now || now - timestampMs > 750) return
        if (progress.repetitions < repetitions) { interrupt(); return } // A reset counter is a new run.
        if (previouslyTracked && progress.trackingAvailable && lastTimestamp >= 0) {
            val gap = timestampMs - lastTimestamp
            if (gap <= 750) activeDurationMs = Math.addExact(activeDurationMs, gap)
        }
        lastTimestamp = timestampMs
        previouslyTracked = progress.trackingAvailable
        repetitions = progress.repetitions.coerceIn(0, challenge.repetitionsRequired)
        if (repetitions == challenge.repetitionsRequired && progress.trackingAvailable) phase = ChallengePhase.READY
    }

    @Synchronized fun interrupt() {
        if (phase != ChallengePhase.FINISHED) phase = ChallengePhase.INTERRUPTED
        previouslyTracked = false
    }

    @Synchronized fun checkDay(): Boolean {
        if (phase == ChallengePhase.FINISHED) return true
        if (wallClock.localDate().toString() != challenge.day) interrupt()
        return phase != ChallengePhase.INTERRUPTED
    }

    suspend fun complete(): CompletionResult {
        val duration: Long
        val count: Int
        synchronized(this) {
            saved?.let { return it }
            if (!checkDay()) return CompletionResult.Refused(RewardBlock.INACTIVE)
            if (phase != ChallengePhase.READY) return CompletionResult.Refused(RewardBlock.INCOMPLETE)
            duration = activeDurationMs; count = repetitions
            phase = ChallengePhase.SAVING
        }
        return try {
            val result = engine.complete(challenge.id, count, duration) {
                phase == ChallengePhase.SAVING && wallClock.localDate().toString() == challenge.day
            }
            synchronized(this) {
                saved = result
                phase = if (result is CompletionResult.Refused) ChallengePhase.INTERRUPTED else ChallengePhase.FINISHED
            }
            result
        } catch (failure: Throwable) {
            synchronized(this) { if (phase == ChallengePhase.SAVING) phase = ChallengePhase.READY }
            throw failure
        }
    }
}
