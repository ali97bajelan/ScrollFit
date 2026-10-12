package com.example.scrollfit.core.data

import com.example.scrollfit.core.exercise.Exercise
import com.example.scrollfit.core.exercise.RewardRule
import com.example.scrollfit.core.time.WallClock
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import kotlin.coroutines.*

class DurableRepositoryTest {
    private val clock = object : WallClock {
        var day = LocalDate.of(2026, 10, 12)
        override fun localDate() = day
        override fun epochMillis() = 1234L
    }
    private val store = MemoryStore()
    private val repo = DurableRepository(store, clock)
    private val day = clock.day.toString()
    private fun challenge(id: String = "one", rewardMs: Long = 120_000) = Challenge(
        id, "app", day, Exercise.PUSH_UP, ChallengeMode.ADVANCE, 2, rewardMs, 1000,
    )
    private suspend fun prepare() {
        repo.saveTarget(TargetConfig("app"))
        repo.startChallenge(challenge())
    }

    @Test fun duplicateCompletionAndNewRepositoryReturnSameAward() = runSuspend {
        prepare()
        val award = repo.awardCompletedChallenge("one", 2)
        assertNotNull(award)
        assertEquals(award, DurableRepository(store, clock).awardCompletedChallenge("one", 50))
        assertEquals(120_000L, repo.dailyUsage("app").awardedMs)
        assertEquals(ChallengeStatus.COMPLETED, repo.challenge("one")!!.status)
    }

    @Test fun duplicateChallengeCannotReplaceTerms() = runSuspend {
        prepare()
        try { repo.startChallenge(challenge(rewardMs = 1)); fail("must reject duplicate") } catch (_: IllegalArgumentException) { }
        assertEquals(120_000L, repo.challenge("one")!!.rewardMs)
    }

    @Test fun incompleteCancelledAndInterruptedChallengesGrantNothing() = runSuspend {
        prepare()
        assertNull(repo.awardCompletedChallenge("one", 1))
        repo.cancelInterruptedChallenges()
        assertNull(repo.awardCompletedChallenge("one", 2))
        repo.startChallenge(challenge("two"))
        repo.cancelChallenge("two")
        assertNull(repo.awardCompletedChallenge("two", 2))
        assertEquals(0L, repo.dailyUsage("app").awardedMs)
    }

    @Test fun oldDayChallengeCancelsInsteadOfAwardingNewDay() = runSuspend {
        prepare()
        clock.day = clock.day.plusDays(1)
        assertNull(repo.awardCompletedChallenge("one", 2))
        assertEquals(ChallengeStatus.CANCELLED, repo.challenge("one")!!.status)
        assertEquals(0L, repo.dailyUsage("app").awardedMs)
    }

    @Test fun removeReAddAndSettingsChangeKeepAllCountersAndSession() = runSuspend {
        prepare()
        repo.awardCompletedChallenge("one", 2)
        repo.recordUsage("app", day, 1000, 2000)
        repo.saveSession(repo.session("app").copy(promptPending = true))
        repo.removeTarget("app")
        repo.saveTarget(TargetConfig("app", baseAllowanceMs = 0, dailyAwardCapMs = 0))
        assertEquals(DailyUsage("app", day, 1000, 2000, 120_000), repo.dailyUsage("app"))
        assertEquals(3000L, repo.combinedUsage().consumedMs)
        assertEquals(SessionState("app", day, 3000, true), repo.session("app"))
    }

    @Test fun historyDeletionKeepsBudgetIdentityAndCancelsNoCompletedAward() = runSuspend {
        prepare()
        val award = repo.awardCompletedChallenge("one", 2)
        repo.recordUsage("app", day, 1000, 2000)
        repo.recordActivity(ExerciseActivity("activity", "one", day, Exercise.PUSH_UP, 2, 8000, 1234))
        repo.clearHistory()
        assertTrue(repo.activities().isEmpty())
        assertEquals(3000L, repo.combinedUsage().consumedMs)
        assertFalse(repo.combinedUsage().historyVisible)
        assertEquals(2000L, repo.dailyUsage("app").earnedConsumedMs)
        assertFalse(repo.dailyUsage("app").historyVisible)
        assertEquals(award, repo.awardCompletedChallenge("one", 2))
        assertEquals(120_000L, repo.dailyUsage("app").awardedMs)
    }

    @Test fun awardFailureRollsBackLedgerBudgetAndChallenge() = runSuspend {
        prepare()
        store.failNextUsage = true
        try { repo.awardCompletedChallenge("one", 2); fail("expected storage failure") } catch (_: IllegalStateException) { }
        assertTrue(store.awards.isEmpty())
        assertEquals(0L, repo.dailyUsage("app").awardedMs)
        assertEquals(ChallengeStatus.ACTIVE, repo.challenge("one")!!.status)
        assertNotNull(repo.awardCompletedChallenge("one", 2))
    }

    @Test fun usageFailureRollsBackAppCombinedAndSession() = runSuspend {
        repo.saveTarget(TargetConfig("app"))
        repo.saveSession(SessionState("app", day, Long.MAX_VALUE))
        try { repo.recordUsage("app", day, 1, 0); fail("overflow must fail") } catch (_: ArithmeticException) { }
        assertEquals(0L, repo.dailyUsage("app").baseConsumedMs)
        assertEquals(0L, repo.combinedUsage().consumedMs)
        assertEquals(Long.MAX_VALUE, repo.session("app").consumedMs)
    }

    @Test fun spentAwardStillUsesCapAndLoweredCapKeepsCredit() = runSuspend {
        prepare()
        repo.saveTarget(TargetConfig("app", dailyAwardCapMs = 120_000))
        repo.awardCompletedChallenge("one", 2)
        repo.recordUsage("app", day, 0, 120_000)
        repo.startChallenge(challenge("two"))
        assertNull(repo.awardCompletedChallenge("two", 2))
        repo.saveTarget(TargetConfig("app", dailyAwardCapMs = 0))
        assertEquals(120_000L, repo.dailyUsage("app").awardedMs)
        assertNull(repo.awardCompletedChallenge("two", 2))
    }

    @Test fun exerciseOnlyActivityCannotProduceAward() = runSuspend {
        repo.startChallenge(Challenge("only", null, day, Exercise.SQUAT, ChallengeMode.EXERCISE_ONLY, 10, 0, 1000))
        repo.recordActivity(ExerciseActivity("activity", "only", day, Exercise.SQUAT, 10, 20_000, 1234))
        assertNull(repo.awardCompletedChallenge("only", 10))
        assertEquals(10, repo.activities().single().repetitions)
        assertTrue(store.awards.isEmpty())
    }

    @Test fun combinedUsageIncludesBothAppsAfterRemoval() = runSuspend {
        repo.saveTarget(TargetConfig("app")); repo.saveTarget(TargetConfig("other"))
        repo.recordUsage("app", day, 10, 0); repo.recordUsage("other", day, 20, 0)
        repo.removeTarget("app")
        assertEquals(30L, repo.combinedUsage().consumedMs)
        assertEquals(20L, repo.dailyUsage("other").baseConsumedMs)
    }

    @Test fun preferencesValidateEveryExerciseAndTimeUnitOverflow() {
        assertEquals(4, AppPreferences().rewardRules.size)
        assertFalse(AppPreferences().zeroBaseEnabled)
        assertTrue(AppPreferences().speechEnabled)
        try { AppPreferences(combinedCeilingMs = 0); fail() } catch (_: IllegalArgumentException) { }
        try { AppPreferences(rewardRules = mapOf(Exercise.PUSH_UP to RewardRule(2, 120))); fail() } catch (_: IllegalArgumentException) { }
        try { AppPreferences(rewardRules = AppPreferences().rewardRules + (Exercise.SQUAT to RewardRule(1, Long.MAX_VALUE))); fail() } catch (_: ArithmeticException) { }
    }

    private fun runSuspend(block: suspend () -> Unit) {
        var completion: Result<Unit>? = null
        block.startCoroutine(object : Continuation<Unit> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<Unit>) { completion = result }
        })
        checkNotNull(completion) { "Test adapter must complete synchronously" }.getOrThrow()
    }
}
