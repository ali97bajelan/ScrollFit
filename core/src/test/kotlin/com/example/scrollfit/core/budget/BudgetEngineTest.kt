package com.example.scrollfit.core.budget

import com.example.scrollfit.core.data.*
import com.example.scrollfit.core.exercise.Exercise
import com.example.scrollfit.core.exercise.RewardRule
import com.example.scrollfit.core.time.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.coroutines.*

class BudgetEngineTest {
    private val clock = object : WallClock, MonotonicClock {
        var day = LocalDate.of(2026, 10, 12)
        var elapsed = 1_000_000L
        var epoch = 1000L
        override fun localDate() = day
        override fun epochMillis() = epoch
        override fun elapsedRealtimeMillis() = elapsed
    }
    private val store = MemoryStore()
    private val repo = DurableRepository(store, clock)
    private var prefs = AppPreferences(combinedCeilingMs = 10_000,
        rewardRules = AppPreferences().rewardRules.mapValues { RewardRule(it.value.repetitions, 2) })
    private fun engine(boot: String = "boot") = BudgetEngine(store, clock, clock, boot) { prefs }
    private var engine = engine()
    private suspend fun setup(base: Long = 1000, cap: Long = 5000) {
        repo.saveTarget(TargetConfig("a", baseAllowanceMs = base, dailyAwardCapMs = cap))
        repo.saveTarget(TargetConfig("b", baseAllowanceMs = base, dailyAwardCapMs = cap))
    }
    private suspend fun offer(app: String? = "a", mode: ChallengeMode = ChallengeMode.ADVANCE): RewardOffer =
        (engine.offer(app, Exercise.PUSH_UP, mode) as OfferResult.Available).offer
    private suspend fun start(id: String = "one", app: String? = "a", mode: ChallengeMode = ChallengeMode.ADVANCE): Challenge {
        return (engine.start(id, offer(app, mode)) as StartResult.Started).challenge
    }
    private suspend fun earn(id: String = "one", app: String = "a") {
        start(id, app); assertTrue(engine.complete(id, 2) is CompletionResult.Awarded)
    }
    private suspend fun spend(start: Long, end: Long, app: String = "a", day: String = clock.day.toString()) =
        engine.spend(UsageInterval(app, day, start, end))

    @Test fun baseFirstExactBoundaryAndEarnedExhaustion() = run {
        setup(); earn()
        assertEquals(1000L, spend(0, 1000).baseSpentMs)
        assertEquals(2000L, spend(1000, 4000).earnedSpentMs)
        assertEquals(0L, engine.budget("a").usableMs)
        assertEquals(BudgetBlock.APP_BUDGET, engine.budget("a").blockedBy)
        assertEquals(3000L, repo.combinedUsage().consumedMs)
        assertEquals(3000L, repo.session("a").consumedMs)
        assertEquals(0L, spend(4000, 5000).spentMs)
    }

    @Test fun duplicateOverlapAndTwoAppSwitchingUseOneGlobalCursor() = run {
        setup(base = 10_000)
        spend(0, 1000)
        assertEquals(0L, spend(0, 1000, "b").spentMs)
        assertEquals(500L, spend(500, 1500, "b").spentMs)
        assertEquals(0L, spend(100, 700).spentMs)
        assertEquals(1000L, repo.dailyUsage("a").baseConsumedMs)
        assertEquals(500L, repo.dailyUsage("b").baseConsumedMs)
        assertEquals(1500L, repo.combinedUsage().consumedMs)
    }

    @Test fun combinedCeilingClipsBothWalletsAndRefusesRewardsWithoutDeletingCredit() = run {
        setup(base = 1000); prefs = prefs.copy(combinedCeilingMs = 1500); earn()
        spend(0, 1000); spend(1000, 2000, "b")
        assertEquals(2000L, engine.budget("a").earnedRemainingMs)
        assertEquals(BudgetBlock.COMBINED_CEILING, engine.budget("a").blockedBy)
        assertEquals(0L, spend(2000, 3000).spentMs)
        for (mode in listOf(ChallengeMode.ADVANCE, ChallengeMode.JUST_IN_TIME)) {
            assertEquals(RewardBlock.COMBINED_CEILING, (engine.offer("a", Exercise.PUSH_UP, mode) as OfferResult.Refused).reason)
        }
        start("only", null, ChallengeMode.EXERCISE_ONLY)
        assertEquals(CompletionResult.ExerciseOnlyCompleted, engine.complete("only", 2))
        assertEquals(2000L, repo.dailyUsage("a").awardedMs)
    }

    @Test fun reducedRewardDisclosedAndAwardCapacityCountsConsumedAwards() = run {
        setup(cap = 2500); earn(); spend(0, 3000)
        val quote = offer()
        assertEquals(500L, quote.rewardMs); assertTrue(quote.reduced)
        engine.start("two", quote); engine.complete("two", 2)
        assertEquals(2500L, repo.dailyUsage("a").awardedMs)
        assertEquals(RewardBlock.AWARD_CAP, (engine.offer("a", Exercise.PUSH_UP, ChallengeMode.ADVANCE) as OfferResult.Refused).reason)
    }

    @Test fun changedOfferMustBeShownAgainBeforeStarting() = run {
        setup(); val old = offer()
        repo.saveTarget(TargetConfig("a", dailyAwardCapMs = 500))
        val changed = engine.start("one", old) as StartResult.OfferChanged
        assertEquals(500L, (changed.current as OfferResult.Available).offer.rewardMs)
        assertNull(repo.challenge("one"))
    }

    @Test fun snapshotSurvivesRewardEditsAndDuplicateCallbackAfterRestart() = run {
        setup(); start()
        prefs = prefs.copy(rewardRules = prefs.rewardRules + (Exercise.PUSH_UP to RewardRule(99, 9)))
        val result = engine.complete("one", 2) as CompletionResult.Awarded
        engine = engine()
        assertEquals(result.award, (engine.complete("one", 0) as CompletionResult.Awarded).award)
        assertEquals(2000L, repo.dailyUsage("a").awardedMs)
    }

    @Test fun bothEarningPathsApplySamePolicyAndAllExerciseRulesSnapshot() = run {
        setup()
        for (exercise in Exercise.entries) {
            val advance = (engine.offer("a", exercise, ChallengeMode.ADVANCE) as OfferResult.Available).offer
            val timely = (engine.offer("a", exercise, ChallengeMode.JUST_IN_TIME) as OfferResult.Available).offer
            assertEquals(advance.copy(mode = ChallengeMode.JUST_IN_TIME), timely)
            assertEquals(prefs.rewardRules.getValue(exercise).repetitions, timely.repetitionsRequired)
        }
    }

    @Test fun capLoweredDuringChallengeRefusesFullSnapshotAndKeepsExistingCredit() = run {
        setup(); earn(); start("two")
        repo.saveTarget(TargetConfig("a", dailyAwardCapMs = 2500))
        assertEquals(CompletionResult.Refused(RewardBlock.AWARD_CAP), engine.complete("two", 2))
        assertEquals(2000L, repo.dailyUsage("a").awardedMs)
        assertEquals(2000L, repo.challenge("two")!!.rewardMs)
    }

    @Test fun ceilingReachedDuringChallengeRefusesAward() = run {
        setup(base = 10_000); start(); spend(0, 10_000, "b")
        assertEquals(CompletionResult.Refused(RewardBlock.COMBINED_CEILING), engine.complete("one", 2))
        assertEquals(0L, repo.dailyUsage("a").awardedMs)
    }

    @Test fun partialCancelledAndProcessInterruptedChallengesDoNotAward() = run {
        setup(); start()
        assertEquals(CompletionResult.Refused(RewardBlock.INCOMPLETE), engine.complete("one", 1))
        repo.cancelChallenge("one")
        assertEquals(CompletionResult.Refused(RewardBlock.INACTIVE), engine.complete("one", 2))
        start("two"); repo.cancelInterruptedChallenges(); engine = engine()
        assertEquals(CompletionResult.Refused(RewardBlock.INACTIVE), engine.complete("two", 2))
        assertEquals(0L, repo.dailyUsage("a").awardedMs)
    }

    @Test fun midnightExpiresCreditResetsDailyCapacityAndCancelsBeforeDateReversal() = run {
        setup(); earn(); start("two"); spend(0, 500)
        val old = clock.day; clock.day = old.plusDays(1)
        assertEquals(1000L, engine.budget("a").baseRemainingMs)
        assertEquals(0L, engine.budget("a").earnedRemainingMs)
        assertEquals(5000L, engine.budget("a").awardCapacityMs)
        assertEquals(ChallengeStatus.CANCELLED, repo.challenge("two")!!.status)
        clock.day = old
        assertEquals(500L, engine.budget("a").baseRemainingMs)
        assertEquals(CompletionResult.Refused(RewardBlock.INACTIVE), engine.complete("two", 2))
    }

    @Test fun oldDayCompletionAndDelayedIntervalNeverSpendOrAwardNewDay() = run {
        setup(); start(); val old = clock.day.toString(); clock.day = clock.day.plusDays(1)
        assertEquals(CompletionResult.Refused(RewardBlock.INACTIVE), engine.complete("one", 2))
        assertEquals(SpendDisposition.WRONG_DAY, spend(0, 500, day = old).disposition)
        assertEquals(0L, repo.combinedUsage().consumedMs)
        repo.recordActivity(ExerciseActivity("old", "one", old, Exercise.PUSH_UP, 2, 500, 1000))
        assertEquals(old, repo.activities().single().day)
    }

    @Test fun zeroBaseSettingsAndReAddPreserveConsumptionAndIndependentCredit() = run {
        setup(); earn(); spend(0, 500)
        prefs = prefs.copy(zeroBaseEnabled = true)
        assertEquals(0L, engine.budget("a").baseRemainingMs)
        assertEquals(0L, engine.budget("b").usableMs)
        assertEquals(500L, spend(500, 1000).earnedSpentMs)
        repo.removeTarget("a"); assertEquals(0L, engine.budget("a").usableMs)
        repo.saveTarget(TargetConfig("a", baseAllowanceMs = 1000, dailyAwardCapMs = 5000))
        prefs = prefs.copy(zeroBaseEnabled = false)
        assertEquals(500L, engine.budget("a").baseRemainingMs)
        assertEquals(1500L, engine.budget("a").earnedRemainingMs)
        assertEquals(1000L, repo.combinedUsage().consumedMs)
    }

    @Test fun restartHistoryDeletionAndRebootDoNotResetBudgetsOrIntervalIdentity() = run {
        setup(base = 10_000); spend(0, 1000); repo.clearHistory(); engine = engine()
        assertEquals(0L, spend(0, 1000).spentMs)
        engine = engine("next-boot"); clock.elapsed = 1000
        assertEquals(500L, spend(0, 500).spentMs)
        assertEquals(1500L, repo.combinedUsage().consumedMs)
        assertFalse(repo.combinedUsage().historyVisible)
    }

    @Test fun wallClockJumpsDoNotChangeMonotonicDurationAndFutureIntervalsReject() = run {
        setup(); clock.epoch = -999999
        assertEquals(500L, spend(0, 500).spentMs)
        clock.epoch = Long.MAX_VALUE
        assertEquals(500L, spend(500, 1000).spentMs)
        try { spend(1000, clock.elapsed + 1); fail() } catch (_: IllegalArgumentException) { }
    }

    @Test fun cursorWalletAndSessionRollbackTogether() = run {
        setup(); store.failNextUsage = true
        try { spend(0, 500); fail() } catch (_: IllegalStateException) { }
        assertEquals(0L, repo.combinedUsage().consumedMs)
        assertEquals(500L, spend(0, 500).spentMs)
        assertEquals(500L, repo.session("a").consumedMs)
    }

    @Test fun awardRollbackLeavesRetryableChallengeAndNoDuplicateLedger() = run {
        setup(); start(); store.failNextUsage = true
        try { engine.complete("one", 2); fail() } catch (_: IllegalStateException) { }
        assertTrue(store.awards.isEmpty())
        assertEquals(ChallengeStatus.ACTIVE, repo.challenge("one")!!.status)
        assertTrue(engine.complete("one", 2) is CompletionResult.Awarded)
        assertEquals(1, store.awards.size)
    }

    @Test fun unknownUnselectedTargetsAndZeroCapacityRefuseBothModes() = run {
        setup(cap = 0); repo.removeTarget("b")
        for (mode in listOf(ChallengeMode.ADVANCE, ChallengeMode.JUST_IN_TIME)) {
            assertEquals(RewardBlock.TARGET_UNAVAILABLE, (engine.offer("b", Exercise.SQUAT, mode) as OfferResult.Refused).reason)
            assertEquals(RewardBlock.TARGET_UNAVAILABLE, (engine.offer("missing", Exercise.SQUAT, mode) as OfferResult.Refused).reason)
            assertEquals(RewardBlock.AWARD_CAP, (engine.offer("a", Exercise.SQUAT, mode) as OfferResult.Refused).reason)
        }
        start("only", null, ChallengeMode.EXERCISE_ONLY)
        engine.complete("only", 2); engine.complete("only", 2)
        assertTrue(store.awards.isEmpty())
    }

    @Test fun midnightDuringSuspendedCompletionReadCancelsInsteadOfAwarding() = run {
        setup(); start()
        store.onUsageRead = { clock.day = clock.day.plusDays(1) }
        assertEquals(CompletionResult.Refused(RewardBlock.INACTIVE), engine.complete("one", 2))
        assertTrue(store.awards.isEmpty())
        assertEquals(ChallengeStatus.CANCELLED, repo.challenge("one")!!.status)
    }

    @Test fun midnightDuringSuspendedStartReadRequiresNewOffer() = run {
        setup(); val quote = offer()
        store.onUsageRead = { clock.day = clock.day.plusDays(1) }
        assertTrue(engine.start("one", quote) is StartResult.OfferChanged)
        assertNull(repo.challenge("one"))
    }

    @Test fun midnightDuringSpendingReadNeverUsesFreshDayBudget() = run {
        setup(); val old = clock.day.toString()
        store.onUsageRead = { clock.day = clock.day.plusDays(1) }
        assertEquals(SpendDisposition.WRONG_DAY, spend(0, 500, day = old).disposition)
        assertEquals(0L, repo.combinedUsage().consumedMs)
        assertEquals(0L, repo.combinedUsage(old).consumedMs)
    }

    @Test fun expandedBudgetDoesNotRetroactivelyChargeAlreadyObservedTail() = run {
        setup(base = 1000); assertEquals(1000L, spend(0, 5000).spentMs)
        repo.saveTarget(TargetConfig("a", baseAllowanceMs = 10_000))
        assertEquals(0L, spend(0, 5000).spentMs)
        assertEquals(500L, spend(4500, 5500).spentMs)
        assertEquals(1500L, repo.combinedUsage().consumedMs)
    }

    @Test fun advanceAndJustInTimeCompletionsUseSameWalletAndCap() = run {
        setup()
        start("advance", mode = ChallengeMode.ADVANCE); engine.complete("advance", 2)
        start("timely", mode = ChallengeMode.JUST_IN_TIME); engine.complete("timely", 2)
        assertEquals(4000L, engine.budget("a").earnedRemainingMs)
        assertEquals(1000L, engine.budget("a").awardCapacityMs)
        assertEquals(0L, engine.budget("b").earnedRemainingMs)
    }

    @Test fun budgetReadAcrossMidnightReturnsCurrentDayAndCancellationSurvivesRewind() = run {
        setup(); earn(); start("two"); val old = clock.day
        store.onUsageRead = { clock.day = old.plusDays(1) }
        val budget = engine.budget("a")
        assertEquals(clock.day.toString(), budget.day)
        assertEquals(0L, budget.earnedRemainingMs)
        assertEquals(ChallengeStatus.CANCELLED, repo.challenge("two")!!.status)
        clock.day = old
        assertEquals(CompletionResult.Refused(RewardBlock.INACTIVE), engine.complete("two", 2))
    }

    @Test fun offerReadAcrossMidnightReturnsCurrentTermsAndCancelsOldChallenge() = run {
        setup(cap = 2000); earn(); val old = clock.day
        repo.saveTarget(TargetConfig("a", dailyAwardCapMs = 4000)); start("two")
        store.onUsageRead = { clock.day = old.plusDays(1) }
        val quote = offer()
        assertEquals(clock.day.toString(), quote.day)
        assertEquals(4000L, quote.awardCapacityMs)
        assertEquals(ChallengeStatus.CANCELLED, repo.challenge("two")!!.status)
        clock.day = old
        assertEquals(CompletionResult.Refused(RewardBlock.INACTIVE), engine.complete("two", 2))
    }

    @Test fun sessionOverflowRollsBackEveryPriorSpendingWriteAndCursor() = run {
        setup(); repo.saveSession(SessionState("a", clock.day.toString(), Long.MAX_VALUE))
        try { spend(0, 500); fail() } catch (_: ArithmeticException) { }
        assertEquals(0L, repo.dailyUsage("a").baseConsumedMs)
        assertEquals(0L, repo.combinedUsage().consumedMs)
        assertNull(store.transaction { cursor("boot") })
        repo.saveSession(SessionState("a", clock.day.toString()))
        assertEquals(500L, spend(0, 500).spentMs)
    }

    @Test fun concurrentDuplicateCallbacksSpendAndAwardOnceUnderSerializedStore() {
        run { setup(base = 10_000); start() }
        val pool = Executors.newFixedThreadPool(8)
        try {
            val results = pool.invokeAll((1..24).map {
                Callable {
                    var awarded: CompletionResult? = null
                    var spent = 0L
                    run {
                        awarded = engine.complete("one", 2)
                        spent = spend(0, 1000).spentMs
                    }
                    awarded to spent
                }
            }).map { it.get() }
            assertEquals(1, results.map { it.first }.toSet().size)
            assertTrue(results.first().first is CompletionResult.Awarded)
            assertEquals(1000L, results.sumOf { it.second })
            run {
                assertEquals(2000L, repo.dailyUsage("a").awardedMs)
                assertEquals(1000L, repo.combinedUsage().consumedMs)
            }
        } finally { pool.shutdownNow() }
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
