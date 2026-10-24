package com.example.scrollfit.core.feedback

import com.example.scrollfit.core.pose.FramingIssue
import com.example.scrollfit.core.time.MonotonicClock

/** Chooses semantic guide prompts from stable framing evidence; localization belongs to the UI. */
class GuidePromptPolicy(private val clock: MonotonicClock) {
    private var currentIssue: FramingIssue? = null
    private var issueSinceMs: Long? = null
    private var lastPlaybackMs: Long? = null
    private var offered: GuidePrompt? = null
    private var recoveryPending = false
    private var promptAllowed = true

    fun update(issue: FramingIssue?, ready: Boolean, activeWorkout: Boolean, allowPrompt: Boolean = true): GuidePrompt? {
        val effectiveIssue = issue ?: if (activeWorkout) FramingIssue.NO_RELIABLE_POSE else null
        if (effectiveIssue != currentIssue) {
            val previous = currentIssue
            currentIssue = effectiveIssue
            issueSinceMs = clock.elapsedRealtimeMillis()
            offered = null
            recoveryPending = activeWorkout && effectiveIssue == FramingIssue.FRAMED &&
                previous != null && previous != FramingIssue.FRAMED
        }

        if (!allowPrompt) {
            // FeedbackOutput may invalidate a queued prompt when camera evidence becomes
            // ineligible. Do not leave that undelivered prompt marked as offered. Keep a
            // canceled recovery cue pending as well; played() clears offered only after
            // actual playback and its cooldown remains authoritative.
            if (offered?.recovered == true) recoveryPending = true
            offered = null
            promptAllowed = false
            return null
        }
        val now = clock.elapsedRealtimeMillis()
        if (!promptAllowed) {
            issueSinceMs = now
            promptAllowed = true
        }
        if (recoveryPending) {
            if (lastPlaybackMs?.let { now < it || now - it < PLAYBACK_COOLDOWN_MS } == true) return null
            return GuidePrompt(issue = null, recovered = true).also {
                offered = it
                recoveryPending = false
            }
        }
        if (effectiveIssue == null || effectiveIssue == FramingIssue.FRAMED) return null
        if (!activeWorkout && ready) return null

        val since = issueSinceMs ?: now.also { issueSinceMs = it }
        if (now < since || now - since < ISSUE_DWELL_MS) return null
        if (lastPlaybackMs?.let { now < it || now - it < PLAYBACK_COOLDOWN_MS } == true) return null

        val prompt = GuidePrompt(issue = effectiveIssue, recovered = false)
        if (offered == prompt) return null
        offered = prompt
        return prompt
    }

    /** Call only when speech or a local tone actually starts. */
    fun played(prompt: GuidePrompt) {
        if (offered != prompt) return
        lastPlaybackMs = clock.elapsedRealtimeMillis()
        offered = null
    }

    /** Resets issue dwell while preserving this audio owner's playback cooldown. */
    fun reset() {
        currentIssue = null
        issueSinceMs = null
        offered = null
        recoveryPending = false
        promptAllowed = true
    }

    private companion object {
        const val ISSUE_DWELL_MS = 2_000L
        const val PLAYBACK_COOLDOWN_MS = 10_000L
    }
}

/** A semantic instruction; it deliberately contains no localized text or camera coordinates. */
data class GuidePrompt(val issue: FramingIssue?, val recovered: Boolean)
