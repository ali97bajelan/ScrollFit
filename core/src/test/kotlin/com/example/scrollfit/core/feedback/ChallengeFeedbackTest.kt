package com.example.scrollfit.core.feedback

import com.example.scrollfit.core.budget.CompletionResult
import com.example.scrollfit.core.budget.RewardBlock
import com.example.scrollfit.core.data.Award
import com.example.scrollfit.core.pose.FramingIssue
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
    private fun guide(issue: FramingIssue, allowPrompt: Boolean = true) =
        feedback.updateGuide(issue, ready = false, activeWorkout = true, allowPrompt = allowPrompt)
    private fun started() { feedback.playbackStarted(played.last().first) }
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
    @Test fun guideNeedsTwoSecondsAndCooldownStartsAtActualPlayback() {
        begin(); guide(FramingIssue.NO_RELIABLE_POSE); now = 1999; feedback.tick(); assertTrue(played.isEmpty())
        now = 2000; feedback.tick()
        assertEquals(FeedbackMessage.Guide(GuidePrompt(FramingIssue.NO_RELIABLE_POSE, false)), played.single().second)
        started(); done()
        now = 11999; feedback.tick(); assertEquals(1, played.size)
        now = 12000; feedback.tick(); assertEquals(2, played.size)
    }
    @Test fun briefLossDoesNotAnnounceAnUnresolvedIssue() {
        begin(); guide(FramingIssue.NO_RELIABLE_POSE); now = 1500; guide(FramingIssue.FRAMED)
        assertEquals(FeedbackMessage.Guide(GuidePrompt(null, true)), played.single().second)
        started(); done()
        now = 3000; guide(FramingIssue.NO_RELIABLE_POSE); now = 5000; feedback.tick()
        assertEquals(listOf(FeedbackMessage.Guide(GuidePrompt(null, true))), played.map { it.second })
    }
    @Test fun trackingRecoveryStopsAnObsoletePositioningPrompt() {
        begin(); guide(FramingIssue.NO_RELIABLE_POSE); now = 2000; feedback.tick(); started()
        val before = stops; guide(FramingIssue.FRAMED); assertTrue(stops > before)
    }
    @Test fun queuedIssuePromptIsReplacedByRecoveryCue() {
        begin(); feedback.progress(1, true); guide(FramingIssue.NO_RELIABLE_POSE); now = 2000; feedback.tick()
        guide(FramingIssue.FRAMED); done()
        assertEquals(listOf(
            FeedbackMessage.Count(1),
            FeedbackMessage.Guide(GuidePrompt(null, true)),
        ), played.map { it.second })
    }
    @Test fun aStillRelevantPositioningPromptDoesNotExpireBehindACount() {
        begin(); feedback.progress(1, true); guide(FramingIssue.NO_RELIABLE_POSE)
        now = 2000; feedback.tick(); now = 3001; done()
        assertEquals(FeedbackMessage.Guide(GuidePrompt(FramingIssue.NO_RELIABLE_POSE, false)), played.last().second)
    }
    @Test fun cooldownStartsWhenPositionPromptActuallyPlays() {
        begin(); feedback.progress(1, true); guide(FramingIssue.NO_RELIABLE_POSE)
        now = 2000; feedback.tick(); now = 2500; done(); started(); done()
        now = 12499; feedback.tick(); assertEquals(2, played.size)
        now = 12500; feedback.tick(); assertEquals(3, played.size)
    }
    @Test fun canceledQueuedPromptCanBeOfferedAgainAfterEvidenceRecovers() {
        begin(); feedback.progress(1, true); guide(FramingIssue.NO_RELIABLE_POSE)
        now = 2000; feedback.tick()
        guide(FramingIssue.NO_RELIABLE_POSE, allowPrompt = false)
        now = 2001; guide(FramingIssue.NO_RELIABLE_POSE)
        now = 4001; feedback.tick()
        assertEquals(FeedbackMessage.Guide(GuidePrompt(FramingIssue.NO_RELIABLE_POSE, false)), played.last().second)
    }
    @Test fun reenabledPromptMustSettleForAnotherTwoSeconds() {
        begin(); guide(FramingIssue.NO_RELIABLE_POSE, allowPrompt = false)
        now = 5000; guide(FramingIssue.NO_RELIABLE_POSE)
        now = 6999; feedback.tick(); assertTrue(played.isEmpty())
        now = 7000; feedback.tick(); assertEquals(1, played.size)
    }
    @Test fun countdownPreemptsPositionAndGuidanceCannotEmitWorkoutFeedback() {
        feedback.setEnabled(true); feedback.beginGuidance()
        feedback.updateGuide(FramingIssue.MOVE_BACK, ready = false, activeWorkout = false)
        now = 2000
        feedback.updateGuide(FramingIssue.MOVE_BACK, ready = false, activeWorkout = false)
        feedback.countdown(3); feedback.progress(1, tracking = true); feedback.complete(award())
        assertEquals(listOf(
            FeedbackMessage.Guide(GuidePrompt(FramingIssue.MOVE_BACK, false)),
            FeedbackMessage.Countdown(3),
        ), played.map { it.second })
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
        guide(FramingIssue.NO_RELIABLE_POSE); now = 3000; feedback.tick()
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
