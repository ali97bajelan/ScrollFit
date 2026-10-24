package com.example.scrollfit.core.feedback

import com.example.scrollfit.core.budget.CompletionResult
import com.example.scrollfit.core.pose.FramingIssue
import com.example.scrollfit.core.time.MonotonicClock

sealed interface FeedbackMessage {
    data class Count(val repetitions: Int) : FeedbackMessage
    /** Retained for callers that still submit the legacy neutral tracking cue. */
    data object Position : FeedbackMessage
    data class Guide(val prompt: GuidePrompt) : FeedbackMessage
    data class Countdown(val remaining: Int) : FeedbackMessage
    /** null means exercise-only; a reward always comes from the committed award. */
    data class Completed(val awardedMs: Long?) : FeedbackMessage
}

interface FeedbackOutput {
    fun play(token: Long, message: FeedbackMessage)
    fun stop()
}

/** Single owner thread. No delayed callback may restart a stopped challenge. */
class ChallengeFeedback(private val output: FeedbackOutput, private val clock: MonotonicClock) {
    private data class Delivery(
        val token: Long,
        val message: FeedbackMessage,
        val createdAt: Long,
        val guideRevision: Long? = null,
    )

    private var active = false
    private var guidanceScope = false
    private var countdownScope = false
    private var enabled = false
    private var completed = false
    private var repetitions = 0
    private var nextToken = 0L
    private var guideRevision = 0L
    private var currentIssue: FramingIssue? = null
    private var currentIssueReady = false
    private var currentIssueActiveWorkout = false
    private var currentGuideAllowed = true
    private var current: Delivery? = null
    private var pending: Delivery? = null
    private val guidePolicy = GuidePromptPolicy(clock)

    /** Opens the audio-only Position/Countdown scope. It can never authorize progress or awards. */
    fun beginGuidance() {
        clear()
        active = true
        guidanceScope = true
        countdownScope = false
        completed = false
        repetitions = 0
        resetGuideContext()
    }

    /** Opens normal workout feedback and discards any position/countdown output. */
    fun begin() {
        clear()
        active = true
        guidanceScope = false
        countdownScope = false
        completed = false
        repetitions = 0
        resetGuideContext()
    }

    fun setEnabled(value: Boolean) {
        if (enabled == value) return
        enabled = value
        if (!value) {
            clear()
            resetGuideContext()
        }
    }

    /** Feed current framing evidence so queued prompts can be checked again before playback. */
    fun updateGuide(issue: FramingIssue?, ready: Boolean, activeWorkout: Boolean, allowPrompt: Boolean = true) {
        if (!active || !enabled || completed || countdownScope) return
        if (guidanceScope == activeWorkout) return

        val normalizedIssue = issue ?: if (activeWorkout) FramingIssue.NO_RELIABLE_POSE else null
        val changed = normalizedIssue != currentIssue || activeWorkout != currentIssueActiveWorkout ||
            (normalizedIssue == FramingIssue.FRAMED && ready != currentIssueReady) ||
            allowPrompt != currentGuideAllowed
        if (changed) {
            currentIssue = normalizedIssue
            currentIssueReady = ready
            currentIssueActiveWorkout = activeWorkout
            guideRevision++
            invalidateObsoleteGuide()
        }
        currentGuideAllowed = allowPrompt

        guidePolicy.update(issue, ready, activeWorkout, allowPrompt)?.let(::guide)
    }

    /** Invalidates camera evidence during initialization, pause, errors, or an inactive stage. */
    fun clearGuide() {
        if (!active || countdownScope) return
        currentIssue = null
        currentIssueReady = false
        currentIssueActiveWorkout = false
        currentGuideAllowed = false
        guideRevision++
        guidePolicy.reset()
        invalidateObsoleteGuide()
    }

    fun guide(prompt: GuidePrompt) {
        if (!active || !enabled || completed || countdownScope) return
        if (guidanceScope == currentIssueActiveWorkout) return
        val now = clock.elapsedRealtimeMillis()
        val delivery = Delivery(++nextToken, FeedbackMessage.Guide(prompt), now, guideRevision)
        if (current == null) play(delivery)
        else if (current?.message !is FeedbackMessage.Completed && current?.message !is FeedbackMessage.Countdown) {
            pending = delivery
        }
    }

    /** Countdown audio replaces an unfinished position prompt and any queued repetition cue. */
    fun countdown(remaining: Int) {
        if (!active || !enabled || !guidanceScope || remaining !in 1..3) return
        countdownScope = true
        guideRevision++
        currentIssue = null
        guidePolicy.reset()
        clear()
        play(Delivery(++nextToken, FeedbackMessage.Countdown(remaining), clock.elapsedRealtimeMillis()))
    }

    fun progress(count: Int, tracking: Boolean) {
        if (!active || guidanceScope || completed) return
        if (count > repetitions) {
            repetitions = count
            if (tracking) submit(FeedbackMessage.Count(count), clock.elapsedRealtimeMillis())
        }
    }

    fun tick() {
        if (!active) return
        val now = clock.elapsedRealtimeMillis()
        val playing = current
        val timeout = if (playing?.message is FeedbackMessage.Completed) 12_000 else 4_000
        if (playing != null && now - playing.createdAt >= timeout) {
            current = null
            output.stop()
            drain(now)
        }
        if (!countdownScope && !guidanceScope && enabled && !completed && currentIssueActiveWorkout) {
            guidePolicy.update(currentIssue, currentIssueReady, activeWorkout = true,
                allowPrompt = currentGuideAllowed)?.let(::guide)
        }
    }

    fun complete(result: CompletionResult) {
        if (!active || guidanceScope || completed) return
        if (result is CompletionResult.Refused) { stop(); return }
        completed = true
        clear()
        val reward = (result as? CompletionResult.Awarded)?.award?.amountMs
        submit(FeedbackMessage.Completed(reward), clock.elapsedRealtimeMillis())
    }

    /** Marks actual speech/tone start; queued or rejected output does not start the cooldown. */
    fun playbackStarted(token: Long) {
        if (!active) return
        val delivery = current?.takeIf { it.token == token } ?: return
        val message = delivery.message as? FeedbackMessage.Guide ?: return
        if (!isCurrentGuide(delivery)) {
            current = null
            output.stop()
            drain(clock.elapsedRealtimeMillis())
            return
        }
        guidePolicy.played(message.prompt)
    }

    fun playbackFinished(token: Long) {
        if (!active || current?.token != token) return
        current = null
        drain(clock.elapsedRealtimeMillis())
    }

    fun stop() {
        active = false
        guidanceScope = false
        countdownScope = false
        resetGuideContext()
        clear()
    }

    private fun resetGuideContext() {
        currentIssue = null
        currentIssueReady = false
        currentIssueActiveWorkout = false
        currentGuideAllowed = true
        guideRevision++
        guidePolicy.reset()
    }

    private fun clear() {
        current = null
        pending = null
        output.stop()
    }

    private fun submit(message: FeedbackMessage, now: Long) {
        if (!enabled || !active) return
        val delivery = Delivery(++nextToken, message, now)
        if (current == null) play(delivery)
        else if (message is FeedbackMessage.Count) {
            if (pending?.message !is FeedbackMessage.Guide && pending?.message !is FeedbackMessage.Countdown &&
                pending?.message !is FeedbackMessage.Completed) pending = delivery
        } else pending = delivery
    }

    private fun drain(now: Long) {
        val next = pending ?: return
        pending = null
        if (!enabled || !active) return
        if (next.message is FeedbackMessage.Count && now - next.createdAt > 1_000) return
        if (next.message is FeedbackMessage.Guide && !isCurrentGuide(next)) return
        play(next.copy(createdAt = now))
    }

    private fun play(delivery: Delivery) {
        if (delivery.message is FeedbackMessage.Guide && !isCurrentGuide(delivery)) return
        current = delivery
        output.play(delivery.token, delivery.message)
    }

    private fun isCurrentGuide(delivery: Delivery): Boolean {
        val message = delivery.message as? FeedbackMessage.Guide ?: return true
        if (delivery.guideRevision != guideRevision || countdownScope) return false
        val prompt = message.prompt
        return if (prompt.recovered) {
            currentIssueActiveWorkout && currentIssue == FramingIssue.FRAMED
        } else {
            prompt.issue != null && prompt.issue == currentIssue && prompt.issue != FramingIssue.FRAMED
        }
    }

    private fun invalidateObsoleteGuide() {
        if (pending?.message is FeedbackMessage.Guide && !isCurrentGuide(pending!!)) pending = null
        if (current?.message is FeedbackMessage.Guide && !isCurrentGuide(current!!)) {
            current = null
            output.stop()
            drain(clock.elapsedRealtimeMillis())
        }
    }
}
