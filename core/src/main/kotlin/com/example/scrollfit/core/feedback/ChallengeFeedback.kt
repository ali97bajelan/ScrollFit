package com.example.scrollfit.core.feedback

import com.example.scrollfit.core.budget.CompletionResult
import com.example.scrollfit.core.time.MonotonicClock

sealed interface FeedbackMessage {
    data class Count(val repetitions: Int) : FeedbackMessage
    data object Position : FeedbackMessage
    /** null means exercise-only; a reward always comes from the committed award. */
    data class Completed(val awardedMs: Long?) : FeedbackMessage
}

interface FeedbackOutput {
    fun play(token: Long, message: FeedbackMessage)
    fun stop()
}

/** Single owner thread. No delayed callback may restart a stopped challenge. */
class ChallengeFeedback(private val output: FeedbackOutput, private val clock: MonotonicClock) {
    private data class Delivery(val token: Long, val message: FeedbackMessage, val createdAt: Long)
    private var active = false
    private var enabled = false
    private var completed = false
    private var repetitions = 0
    private var lostSince: Long? = null
    private var lastWarning: Long? = null
    private var nextToken = 0L
    private var current: Delivery? = null
    private var pending: Delivery? = null

    fun begin() {
        clear()
        active = true; completed = false; repetitions = 0; lostSince = null; lastWarning = null
    }

    fun setEnabled(value: Boolean) {
        if (enabled == value) return
        enabled = value
        if (!value) clear()
    }

    fun progress(count: Int, tracking: Boolean) {
        if (!active || completed) return
        val now = clock.elapsedRealtimeMillis()
        if (tracking) {
            lostSince = null
            if (pending?.message == FeedbackMessage.Position) pending = null
            if (current?.message == FeedbackMessage.Position) {
                current = null; output.stop()
            }
        } else if (lostSince == null) lostSince = now
        if (count > repetitions) {
            repetitions = count
            if (tracking) submit(FeedbackMessage.Count(count), now)
        }
    }

    fun tick() {
        if (!active) return
        val now = clock.elapsedRealtimeMillis()
        val playing = current
        val timeout = if (playing?.message is FeedbackMessage.Completed) 12_000 else 4_000
        if (playing != null && now - playing.createdAt >= timeout) {
            current = null; output.stop(); drain(now)
        }
        if (!completed && enabled && lostSince?.let { now - it >= 2_000 } == true &&
            lastWarning?.let { now - it >= 10_000 } != false &&
            current?.message != FeedbackMessage.Position && pending?.message != FeedbackMessage.Position) {
            submit(FeedbackMessage.Position, now)
        }
    }

    fun complete(result: CompletionResult) {
        if (!active || completed) return
        if (result is CompletionResult.Refused) { stop(); return }
        completed = true
        clear()
        val reward = (result as? CompletionResult.Awarded)?.award?.amountMs
        submit(FeedbackMessage.Completed(reward), clock.elapsedRealtimeMillis())
    }

    fun playbackFinished(token: Long) {
        if (!active || current?.token != token) return
        current = null
        drain(clock.elapsedRealtimeMillis())
    }

    fun stop() { active = false; clear() }

    private fun clear() {
        current = null; pending = null
        output.stop()
    }

    private fun submit(message: FeedbackMessage, now: Long) {
        if (!enabled || !active) return
        val delivery = Delivery(++nextToken, message, now)
        if (current == null) play(delivery) else pending = delivery
    }

    private fun drain(now: Long) {
        val next = pending ?: return
        pending = null
        if (!enabled || !active) return
        if (next.message is FeedbackMessage.Count && now - next.createdAt > 1_000) return
        if (next.message == FeedbackMessage.Position && lostSince == null) return
        play(next.copy(createdAt = now))
    }

    private fun play(delivery: Delivery) {
        current = delivery // Establish identity before an output can synchronously report failure.
        if (delivery.message == FeedbackMessage.Position) lastWarning = clock.elapsedRealtimeMillis()
        output.play(delivery.token, delivery.message)
    }
}
