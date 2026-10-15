package com.example.scrollfit.core.feedback

import com.example.scrollfit.core.budget.CompletionResult
import com.example.scrollfit.core.budget.RewardBlock
import com.example.scrollfit.core.data.Award
import com.example.scrollfit.core.time.MonotonicClock
import org.junit.Assert.*
import org.junit.Test

class ChallengeFeedbackTest {
    private var now = 0L
    private val played = mutableListOf<Pair<Long, FeedbackMessage>>()
    private var stops = 0
    private val feedback = ChallengeFeedback(object : FeedbackOutput {
        override fun play(token: Long, message: FeedbackMessage) { played += token to message }
        override fun stop() { stops++ }
    }, object : MonotonicClock { override fun elapsedRealtimeMillis() = now })
    private fun begin(enabled: Boolean = true) { feedback.setEnabled(enabled); feedback.begin() }
    private fun done(token: Long = played.last().first) { feedback.playbackFinished(token) }
    private fun award(amount: Long = 45_500) = CompletionResult.Awarded(Award("one", "a", "2026-10-16", amount, 0))

    @Test fun enabledCountsOnlyNewAcceptedRepetitions() {
        begin(); feedback.progress(0, true); feedback.progress(1, true); feedback.progress(1, true)
        assertEquals(listOf(FeedbackMessage.Count(1)), played.map { it.second })
    }
    @Test fun mutedCountsAndCompletionStaySilent() {
        begin(false); feedback.progress(1, true); feedback.complete(award()); assertTrue(played.isEmpty())
    }
    @Test fun rapidCountsKeepOnlyTheLatestPendingCount() {
        begin(); (1..5).forEach { feedback.progress(it, true) }
        assertEquals(1, played.size); done()
        assertEquals(listOf(FeedbackMessage.Count(1), FeedbackMessage.Count(5)), played.map { it.second })
    }
    @Test fun pendingCountsExpireRatherThanSpeakLate() {
        begin(); feedback.progress(1, true); feedback.progress(2, true); now = 1001; done()
        assertEquals(1, played.size)
    }
    @Test fun muteStopsCurrentAndPendingAndUnmuteNeverReplays() {
        begin(); feedback.progress(1, true); feedback.progress(2, true); val old = played.last().first
        feedback.setEnabled(false); feedback.setEnabled(true); done(old)
        feedback.progress(2, true); assertEquals(1, played.size)
        feedback.progress(3, true); assertEquals(FeedbackMessage.Count(3), played.last().second)
    }
    @Test fun trackingWarningNeedsTwoSecondsAndHasTenSecondCooldown() {
        begin(); feedback.progress(0, false); now = 1999; feedback.tick(); assertTrue(played.isEmpty())
        now = 2000; feedback.tick(); assertEquals(FeedbackMessage.Position, played.single().second); done()
        now = 11999; feedback.tick(); assertEquals(1, played.size)
        now = 12000; feedback.tick(); assertEquals(2, played.size)
    }
    @Test fun briefLossAndFlappingDoNotSpamWarnings() {
        begin(); feedback.progress(0, false); now = 1500; feedback.progress(0, true)
        now = 3000; feedback.tick(); assertTrue(played.isEmpty())
        feedback.progress(0, false); now = 5000; feedback.tick(); done()
        feedback.progress(0, true); now = 6000; feedback.progress(0, false)
        now = 8000; feedback.tick(); assertEquals(1, played.size)
    }
    @Test fun trackingRecoveryStopsAnObsoletePositioningPrompt() {
        begin(); feedback.progress(0, false); now = 2000; feedback.tick()
        val before = stops; feedback.progress(0, true); assertTrue(stops > before)
    }
    @Test fun queuedPositioningPromptIsDiscardedOnRecovery() {
        begin(); feedback.progress(1, true); feedback.progress(1, false); now = 2000; feedback.tick()
        feedback.progress(1, true); done(); assertEquals(1, played.size)
    }
    @Test fun aStillRelevantPositioningPromptDoesNotExpireBehindACount() {
        begin(); feedback.progress(1, true); feedback.progress(1, false)
        now = 2000; feedback.tick(); now = 3001; done()
        assertEquals(FeedbackMessage.Position, played.last().second)
    }
    @Test fun cooldownStartsWhenPositionPromptActuallyPlays() {
        begin(); feedback.progress(1, true); feedback.progress(1, false)
        now = 2000; feedback.tick(); now = 2500; done(); done()
        now = 12000; feedback.tick(); assertEquals(2, played.size)
        now = 12500; feedback.tick(); assertEquals(3, played.size)
    }
    @Test fun cancellationOrBackgroundDropsPendingAndLateCallbacks() {
        begin(); feedback.progress(1, true); feedback.progress(2, true); val old = played.last().first
        feedback.stop(); done(old); now = 20000; feedback.tick(); feedback.progress(3, true); feedback.complete(award())
        assertEquals(1, played.size)
    }
    @Test fun savedReducedRewardPreemptsCountsAndIsAnnouncedOnce() {
        begin(); feedback.progress(1, true); feedback.progress(2, true); val old = played.last().first
        feedback.complete(award()); feedback.complete(award()); done(old)
        assertEquals(listOf(FeedbackMessage.Count(1), FeedbackMessage.Completed(45_500)), played.map { it.second })
    }
    @Test fun exerciseOnlyCompletionHasNoReward() {
        begin(); feedback.complete(CompletionResult.ExerciseOnlyCompleted)
        assertEquals(FeedbackMessage.Completed(null), played.single().second)
    }
    @Test fun refusedCompletionNeverAnnouncesSuccess() {
        begin(); feedback.complete(CompletionResult.Refused(RewardBlock.INACTIVE)); assertTrue(played.isEmpty())
    }
    @Test fun refusedCompletionDiscardsPendingCountsAndTrackingWarnings() {
        begin(); feedback.progress(1, true); feedback.progress(2, true); val old = played.last().first
        feedback.complete(CompletionResult.Refused(RewardBlock.INACTIVE)); done(old)
        feedback.progress(3, false); now = 3000; feedback.tick()
        assertEquals(1, played.size)
    }
    @Test fun latePlaybackCallbackCannotDrainANewerUtterance() {
        begin(); feedback.progress(1, true); val old = played.last().first; done()
        feedback.progress(2, true); feedback.progress(3, true); done(old); assertEquals(2, played.size)
        done(); assertEquals(FeedbackMessage.Count(3), played.last().second)
    }
    @Test fun stuckPlaybackTimesOutAndAcceptsFreshEvents() {
        begin(); feedback.progress(1, true); now = 4001; feedback.tick(); feedback.progress(2, true)
        assertEquals(FeedbackMessage.Count(2), played.last().second); assertEquals(2, played.size)
    }
    @Test fun completionWhileMutedNeverReplaysAfterEnabling() {
        begin(false); feedback.complete(award()); feedback.setEnabled(true); feedback.complete(award())
        assertTrue(played.isEmpty())
    }
}
