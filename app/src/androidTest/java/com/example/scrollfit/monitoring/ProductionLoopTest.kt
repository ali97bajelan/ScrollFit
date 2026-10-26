package com.example.scrollfit.monitoring

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.scrollfit.core.budget.*
import com.example.scrollfit.core.data.*
import com.example.scrollfit.core.data.DurableRepository
import com.example.scrollfit.core.exercise.Exercise
import com.example.scrollfit.core.exercise.RewardRule
import com.example.scrollfit.core.time.MonotonicClock
import com.example.scrollfit.core.time.WallClock
import com.example.scrollfit.platform.data.ExerciseGateDatabase
import com.example.scrollfit.platform.data.RoomStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.UUID

/** Room-backed accounting fixtures; service/device enforcement still requires I03 phone QA. */
@RunWith(AndroidJUnit4::class)
class ProductionLoopTest {
    private class TestClock : WallClock, MonotonicClock {
        var day = LocalDate.of(2026, 10, 26)
        var elapsed = 0L
        var epoch = 1_000L
        override fun localDate() = day
        override fun epochMillis() = epoch
        override fun elapsedRealtimeMillis() = elapsed
    }

    @Test fun perAppAndCombinedCeilingsPersistAcrossEngineRecreation() = runBlocking {
        withDatabase { database ->
            val clock = TestClock()
            val store = RoomStore(database)
            val repository = DurableRepository(store, clock)
            val preferences = AppPreferences(combinedCeilingMs = 1_500,
                rewardRules = AppPreferences().rewardRules.mapValues { RewardRule(it.value.repetitions, 2) })
            var engine = BudgetEngine(store, clock, clock, "i03-boot") { preferences }
            repository.saveTarget(TargetConfig("com.example.a", baseAllowanceMs = 2_000, dailyAwardCapMs = 10_000))
            repository.saveTarget(TargetConfig("com.example.b", baseAllowanceMs = 2_000, dailyAwardCapMs = 10_000))

            clock.elapsed = 1_000
            assertEquals(1_000L, engine.spend(UsageInterval("com.example.a", clock.day.toString(), 0, 1_000)).spentMs)
            clock.elapsed = 2_000
            assertEquals(500L, engine.spend(UsageInterval("com.example.b", clock.day.toString(), 1_000, 2_000)).spentMs)
            assertEquals(BudgetBlock.COMBINED_CEILING, engine.budget("com.example.a").blockedBy)
            assertEquals(1_500L, repository.combinedUsage().consumedMs)

            engine = BudgetEngine(store, clock, clock, "i03-boot") { preferences }
            assertEquals(SpendDisposition.DUPLICATE,
                engine.spend(UsageInterval("com.example.a", clock.day.toString(), 0, 2_000)).disposition)
            assertEquals(1_500L, repository.combinedUsage().consumedMs)
        }
    }

    @Test fun perAppBudgetBlocksBeforeSharedCeiling() = runBlocking {
        withDatabase { database ->
            val clock = TestClock()
            val store = RoomStore(database)
            val repository = DurableRepository(store, clock)
            val preferences = AppPreferences(combinedCeilingMs = 5_000)
            val engine = BudgetEngine(store, clock, clock, "i03-boot") { preferences }
            repository.saveTarget(TargetConfig("com.example.a", baseAllowanceMs = 1_000, dailyAwardCapMs = 5_000))
            repository.saveTarget(TargetConfig("com.example.b", baseAllowanceMs = 5_000, dailyAwardCapMs = 5_000))
            clock.elapsed = 1_500

            assertEquals(1_000L, engine.spend(
                UsageInterval("com.example.a", clock.day.toString(), 0, 1_500)).spentMs)
            assertEquals(BudgetBlock.APP_BUDGET, engine.budget("com.example.a").blockedBy)
            assertEquals(4_000L, engine.budget("com.example.b").combinedRemainingMs)
            assertEquals(1_000L, repository.combinedUsage().consumedMs)
        }
    }

    @Test fun eachExerciseAndEarningModeCommitsOneDurableAward() = runBlocking {
        withDatabase { database ->
            val clock = TestClock()
            val store = RoomStore(database)
            val repository = DurableRepository(store, clock)
            val preferences = AppPreferences(combinedCeilingMs = 100_000,
                rewardRules = Exercise.entries.associateWith { RewardRule(repetitions = 2, rewardSeconds = 2) })
            var engine = BudgetEngine(store, clock, clock, "i03-boot") { preferences }
            repository.saveTarget(TargetConfig("com.example.target", baseAllowanceMs = 1_000,
                dailyAwardCapMs = 100_000))

            for (exercise in Exercise.entries) {
                for (mode in listOf(ChallengeMode.ADVANCE, ChallengeMode.JUST_IN_TIME)) {
                    val id = "${exercise.name}-${mode.name}"
                    val offer = (engine.offer("com.example.target", exercise, mode) as OfferResult.Available).offer
                    assertTrue(engine.start(id, offer) is StartResult.Started)
                    val first = engine.complete(id, repetitions = 2) as CompletionResult.Awarded
                    val duplicate = engine.complete(id, repetitions = 0) as CompletionResult.Awarded
                    assertEquals(first.award, duplicate.award)
                }
            }
            assertEquals(16_000L, repository.dailyUsage("com.example.target").awardedMs)
            assertEquals(8, repository.activities().size)

            val exerciseOnlyOffer = (engine.offer(null, Exercise.SQUAT, ChallengeMode.EXERCISE_ONLY)
                as OfferResult.Available).offer
            assertTrue(engine.start("exercise-only", exerciseOnlyOffer) is StartResult.Started)
            assertEquals(CompletionResult.ExerciseOnlyCompleted, engine.complete("exercise-only", repetitions = 2))
            assertEquals(16_000L, repository.dailyUsage("com.example.target").awardedMs)
            assertEquals(9, repository.activities().size)

            repository.saveTarget(TargetConfig("com.example.target", baseAllowanceMs = 1_000,
                dailyAwardCapMs = 17_000))
            val reduced = (engine.offer("com.example.target", Exercise.PUSH_UP, ChallengeMode.ADVANCE)
                as OfferResult.Available).offer
            assertEquals(1_000L, reduced.rewardMs)
            assertTrue(reduced.reduced)
            engine.start("reduced", reduced)
            assertTrue(engine.complete("reduced", repetitions = 2) is CompletionResult.Awarded)
            assertEquals(RewardBlock.AWARD_CAP,
                (engine.offer("com.example.target", Exercise.SQUAT, ChallengeMode.ADVANCE) as OfferResult.Refused).reason)

            engine = BudgetEngine(store, clock, clock, "i03-boot") { preferences }
            assertEquals(17_000L, engine.budget("com.example.target").awardedMs)
        }
    }

    @Test fun sessionPromptWaitDoesNotSpendAndSecondResolutionIsStale() = runBlocking {
        withDatabase { database ->
            val clock = TestClock()
            val store = RoomStore(database)
            val repository = DurableRepository(store, clock)
            val preferences = AppPreferences(combinedCeilingMs = 10_000, sessionBoundaryMs = 1_000)
            val engine = BudgetEngine(store, clock, clock, "i03-boot") { preferences }
            repository.saveTarget(TargetConfig("com.example.target", baseAllowanceMs = 5_000,
                dailyAwardCapMs = 5_000))

            clock.elapsed = 1_000
            engine.spend(UsageInterval("com.example.target", clock.day.toString(), 0, 1_000))
            val prompt = engine.sessionAccess("com.example.target")
            assertTrue(prompt.session.promptPending)
            clock.elapsed = 2_000
            assertEquals(0L, engine.spend(
                UsageInterval("com.example.target", clock.day.toString(), 1_000, 2_000)).spentMs)
            assertEquals(SessionActionResult.APPLIED, engine.resolveSession(prompt, SessionAction.CONTINUE))
            assertEquals(SessionActionResult.STALE, engine.resolveSession(prompt, SessionAction.CONTINUE))
            assertEquals(1_000L, repository.combinedUsage().consumedMs)
        }
    }

    private suspend fun withDatabase(block: suspend (ExerciseGateDatabase) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "i03-${UUID.randomUUID()}.db"
        val database = ExerciseGateDatabase.open(context, name)
        try { block(database) }
        finally { database.close(); context.deleteDatabase(name) }
    }
}
