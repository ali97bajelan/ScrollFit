package com.example.scrollfit.core.challenge

import com.example.scrollfit.core.budget.*
import com.example.scrollfit.core.data.*
import com.example.scrollfit.core.exercise.*
import com.example.scrollfit.core.pose.CountingProgress
import com.example.scrollfit.core.time.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import kotlin.coroutines.*

class ChallengeSessionTest {
    private val clock = object : WallClock, MonotonicClock {
        var day = LocalDate.of(2026, 10, 15)
        var elapsed = 10_000L
        override fun localDate() = day
        override fun epochMillis() = 1000L
        override fun elapsedRealtimeMillis() = elapsed
    }
    private val store = MemoryStore()
    private val repo = DurableRepository(store, clock)
    private var prefs = AppPreferences(rewardRules = Exercise.entries.associateWith { RewardRule(2, 120) })
    private val engine = BudgetEngine(store, clock, clock, "boot") { prefs }
    private suspend fun session(mode: ChallengeMode = ChallengeMode.ADVANCE,
        exercise: Exercise = Exercise.PUSH_UP, id: String = "one"): ChallengeSession {
        repo.saveTarget(TargetConfig("a"))
        val offer = (engine.offer(if (mode == ChallengeMode.EXERCISE_ONLY) null else "a", exercise, mode)
            as OfferResult.Available).offer
        val challenge = (engine.start(id, offer) as StartResult.Started).challenge
        return ChallengeSession(challenge, engine, clock, clock)
    }
    private fun progress(session: ChallengeSession, count: Int, tracked: Boolean = true, advance: Long = 100) {
        clock.elapsed += advance
        session.accept(CountingProgress(count, tracked), clock.elapsed)
    }

    @Test fun everyExerciseCompletesBothEarningPathsOnceAndRecordsActivityAtomically() = run {
        for (mode in listOf(ChallengeMode.ADVANCE, ChallengeMode.JUST_IN_TIME)) {
            for (exercise in Exercise.entries) {
                val s = session(mode, exercise, "$mode-$exercise")
                progress(s, 0); progress(s, 1); progress(s, 2)
                val result = s.complete() as CompletionResult.Awarded
                assertEquals(120_000L, result.award.amountMs)
                assertEquals(result, s.complete())
            }
        }
        assertEquals(8, store.awards.size)
        assertEquals(8, repo.activities().size)
        assertTrue(repo.activities().all { it.repetitions == 2 && it.activeDurationMs == 200L })
        assertEquals(960_000L, engine.budget("a").earnedRemainingMs)
    }

    @Test fun trackingLossPreservesCompletedCountsButExcludesDurationAndDoesNotFinish() = run {
        val s = session()
        progress(s, 0); progress(s, 1); progress(s, 1, false)
        progress(s, 1, false, 5000); progress(s, 1); progress(s, 2)
        assertEquals(2, s.repetitions)
        assertEquals(200L, s.activeDurationMs)
        assertTrue(s.complete() is CompletionResult.Awarded)
    }

    @Test fun lostTrackingCumulativeGoalNeedsFreshTrackingBeforeCompletion() = run {
        val s = session(); progress(s, 2, false)
        assertEquals(CompletionResult.Refused(RewardBlock.INCOMPLETE), s.complete())
        progress(s, 2)
        assertTrue(s.complete() is CompletionResult.Awarded)
    }

    @Test fun interruptionAndLateCallbacksCannotComplete() = run {
        val s = session(); progress(s, 1); s.interrupt(); progress(s, 2)
        assertEquals(CompletionResult.Refused(RewardBlock.INACTIVE), s.complete())
        assertTrue(store.awards.isEmpty()); assertTrue(repo.activities().isEmpty())
    }

    @Test fun interruptionDuringSuspendedCompletionReadRollsBackAward() = run {
        val s = session(); progress(s, 2)
        store.onUsageRead = { s.interrupt() }
        assertEquals(CompletionResult.Refused(RewardBlock.INACTIVE), s.complete())
        assertTrue(store.awards.isEmpty()); assertTrue(repo.activities().isEmpty())
    }

    @Test fun interruptionAfterAwardWriteRollsBackAllWrites() = run {
        val s = session(); progress(s, 2)
        store.onActivityWrite = { s.interrupt() }
        assertEquals(CompletionResult.Refused(RewardBlock.INACTIVE), s.complete())
        assertTrue(store.awards.isEmpty()); assertTrue(repo.activities().isEmpty())
        assertEquals(0L, repo.dailyUsage("a").awardedMs)
        assertEquals(ChallengeStatus.ACTIVE, repo.challenge("one")!!.status)
    }

    @Test fun failedActivityWriteRollsBackWalletAndStatusThenSameIdentityRetries() = run {
        val s = session(); progress(s, 2); store.failNextActivity = true
        try { s.complete(); fail() } catch (_: IllegalStateException) { }
        assertTrue(store.awards.isEmpty()); assertTrue(repo.activities().isEmpty())
        assertEquals(ChallengeStatus.ACTIVE, repo.challenge("one")!!.status)
        assertTrue(s.complete() is CompletionResult.Awarded)
        assertEquals(1, store.awards.size); assertEquals(1, repo.activities().size)
    }

    @Test fun interruptionInFinalDailyCleanupStillRollsBack() = run {
        val s = session(); progress(s, 2)
        var reads = 0
        store.onDayReconciliation = { reads++; if (reads == 2) s.interrupt() }
        assertEquals(CompletionResult.Refused(RewardBlock.INACTIVE), s.complete())
        assertTrue(store.awards.isEmpty()); assertTrue(repo.activities().isEmpty())
    }

    @Test fun midnightInFinalDailyCleanupStillRollsBack() = run {
        val s = session(); progress(s, 2)
        var reads = 0
        store.onDayReconciliation = { reads++; if (reads == 2) clock.day = clock.day.plusDays(1) }
        assertEquals(CompletionResult.Refused(RewardBlock.INACTIVE), s.complete())
        assertTrue(store.awards.isEmpty()); assertTrue(repo.activities().isEmpty())
    }

    @Test fun midnightInvalidatesGoalAndDateReversalDoesNotReviveSession() = run {
        val s = session(); progress(s, 2); val old = clock.day
        clock.day = old.plusDays(1); assertFalse(s.checkDay()); clock.day = old
        assertEquals(CompletionResult.Refused(RewardBlock.INACTIVE), s.complete())
        assertTrue(store.awards.isEmpty())
    }

    @Test fun midnightDuringFinalWriteRollsBackAwardAndActivity() = run {
        val s = session(); progress(s, 2)
        store.onActivityWrite = { clock.day = clock.day.plusDays(1) }
        assertEquals(CompletionResult.Refused(RewardBlock.INACTIVE), s.complete())
        assertTrue(store.awards.isEmpty()); assertTrue(repo.activities().isEmpty())
    }

    @Test fun processRecoveryCancelsIncompleteButKeepsCommittedAwards() = run {
        val done = session(id = "done"); progress(done, 2); done.complete()
        val interrupted = session(id = "interrupted"); progress(interrupted, 2)
        repo.cancelInterruptedChallenges()
        assertEquals(CompletionResult.Refused(RewardBlock.INACTIVE), interrupted.complete())
        assertEquals(1, store.awards.size); assertEquals(1, repo.activities().size)
        assertEquals(120_000L, repo.dailyUsage("a").awardedMs)
    }

    @Test fun exerciseOnlyCompletesAtBothCapsWithoutWalletAndCannotConvert() = run {
        val s = session(ChallengeMode.EXERCISE_ONLY)
        repo.saveTarget(TargetConfig("a", dailyAwardCapMs = 0))
        store.transaction { putCombined(CombinedUsage(clock.day.toString(), prefs.combinedCeilingMs)) }
        progress(s, 2)
        assertEquals(CompletionResult.ExerciseOnlyCompleted, s.complete())
        assertEquals(CompletionResult.ExerciseOnlyCompleted, s.complete())
        assertTrue(store.awards.isEmpty()); assertEquals(1, repo.activities().size)
        assertNull(repo.challenge("one")!!.packageName)
    }

    @Test fun staleFutureAndDuplicateFramesCannotManufactureProgressOrDuration() = run {
        val s = session(); progress(s, 0)
        s.accept(CountingProgress(2, true), clock.elapsed)
        s.accept(CountingProgress(2, true), clock.elapsed + 1)
        clock.elapsed += 2000
        s.accept(CountingProgress(2, true), clock.elapsed - 1000)
        assertEquals(0, s.repetitions); assertEquals(0L, s.activeDurationMs)
        assertEquals(CompletionResult.Refused(RewardBlock.INCOMPLETE), s.complete())
    }

    @Test fun counterRegressionCancelsRatherThanMergingRestartedTrials() = run {
        val s = session(); progress(s, 1); progress(s, 0); progress(s, 2)
        assertEquals(CompletionResult.Refused(RewardBlock.INACTIVE), s.complete())
        assertTrue(store.awards.isEmpty())
    }

    @Test fun reducedOfferIsSnapshotAndOfferChangesNeedRedisplay() = run {
        repo.saveTarget(TargetConfig("a", dailyAwardCapMs = 30_000))
        val quote = (engine.offer("a", Exercise.SQUAT, ChallengeMode.ADVANCE) as OfferResult.Available).offer
        assertTrue(quote.reduced); assertEquals(30_000L, quote.rewardMs)
        prefs = prefs.copy(rewardRules = prefs.rewardRules + (Exercise.SQUAT to RewardRule(3, 120)))
        assertTrue(engine.start("stale", quote) is StartResult.OfferChanged)
        assertNull(repo.challenge("stale"))
    }

    @Test fun ledgerDuplicateAfterNewControllerDoesNotDuplicateActivity() = run {
        val s = session(); progress(s, 2); val awarded = s.complete()
        val duplicate = ChallengeSession(s.challenge, engine, clock, clock)
        progress(duplicate, 2)
        assertEquals(awarded, duplicate.complete())
        assertEquals(1, store.awards.size); assertEquals(1, repo.activities().size)
    }

    private fun run(block: suspend () -> Unit) {
        var completion: Result<Unit>? = null
        block.startCoroutine(object : Continuation<Unit> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<Unit>) { completion = result }
        })
        checkNotNull(completion).getOrThrow()
    }
}
