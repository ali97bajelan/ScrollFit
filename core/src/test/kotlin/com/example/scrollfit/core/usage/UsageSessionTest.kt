package com.example.scrollfit.core.usage

import com.example.scrollfit.core.budget.*
import com.example.scrollfit.core.data.*
import com.example.scrollfit.core.exercise.Exercise
import com.example.scrollfit.core.time.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.coroutines.*

class UsageSessionTest {
    private var elapsed = 0L
    private var epoch = Instant.parse("2026-10-16T12:00:00Z").toEpochMilli()
    private val wall = object : WallClock {
        override fun localDate() = Instant.ofEpochMilli(epoch).atZone(ZoneOffset.UTC).toLocalDate()
        override fun epochMillis() = epoch
    }
    private val mono = object : MonotonicClock { override fun elapsedRealtimeMillis() = elapsed }
    private val store = MemoryStore()
    private val repo = DurableRepository(store, wall)
    private var prefs = AppPreferences(combinedCeilingMs = 20_000, sessionBoundaryMs = 1000)
    private fun engine(boot: String = "boot") = BudgetEngine(store, wall, mono, boot) { prefs }
    private var engine = engine()
    private var controller = MonitoringController(engine, setOf("self", "home"))
    private suspend fun setup(base: Long = 10_000) {
        repo.saveTarget(TargetConfig("a", baseAllowanceMs = base))
        repo.saveTarget(TargetConfig("b", baseAllowanceMs = base))
    }
    private fun advance(ms: Long) { elapsed += ms; epoch += ms }
    private fun signal(app: String? = "a", interactive: Boolean = true, own: Boolean = false) =
        MonitorSignal(MonitorSample(elapsed, epoch, "UTC"), app, true, true, true, interactive, own)
    private suspend fun spend(start: Long, end: Long, app: String = "a") =
        engine.spend(UsageInterval(app, wall.localDate().toString(), start, end))

    @Test fun delayedCheckpointClipsWalletAndCombinedAtSessionBoundaryAndLatchesPrompt() = run {
        setup(); advance(4000)
        assertEquals(1000L, spend(0, 4000).spentMs)
        assertEquals(SessionState("a", wall.localDate().toString(), 1000, true), repo.session("a"))
        assertEquals(1000L, repo.dailyUsage("a").baseConsumedMs)
        assertEquals(1000L, repo.combinedUsage().consumedMs)
    }

    @Test fun waitingAtPromptDoesNotConsumeCreditOrSessionTime() = run {
        setup(); advance(1000); spend(0, 1000); advance(4000)
        assertEquals(0L, spend(1000, 5000).spentMs)
        assertEquals(1000L, repo.session("a").consumedMs)
        assertEquals(9000L, engine.budget("a").usableMs)
    }

    @Test fun appSwitchingAndRestartKeepIndependentAccumulatedWindows() = run {
        setup(); controller.observe(signal()); advance(600); controller.observe(signal("b"))
        advance(300); controller.observe(signal("home")); advance(2000)
        controller = MonitoringController(engine(), setOf("home", "self"))
        assertEquals(400L, controller.observe(signal()).tickDelayMs)
        advance(700); val prompt = controller.observe(signal())
        assertEquals(BlockReason.SESSION_BOUNDARY, prompt.blockedBy)
        assertEquals(1000L, repo.session("a").consumedMs)
        assertEquals(300L, repo.session("b").consumedMs)
    }

    @Test fun pendingPromptSurvivesRebootHistoryDeletionAndRemoveReAdd() = run {
        setup(); controller.observe(signal()); advance(1000); controller.observe(signal())
        repo.clearHistory(); repo.removeTarget("a"); repo.saveTarget(TargetConfig("a"))
        elapsed = 0; engine = engine("next-boot")
        controller = MonitoringController(engine, emptySet())
        assertEquals(BlockReason.SESSION_BOUNDARY, controller.observe(signal()).blockedBy)
        advance(700); controller.observe(signal())
        assertEquals(1000L, repo.combinedUsage().consumedMs)
    }

    @Test fun settingsIncreaseDoesNotDismissAnAlreadyPendingPrompt() = run {
        setup(); controller.observe(signal()); advance(1000); controller.observe(signal())
        prefs = prefs.copy(sessionBoundaryMs = 5000)
        assertEquals(BlockReason.SESSION_BOUNDARY, controller.observe(signal()).blockedBy)
        advance(500); controller.observe(signal())
        assertEquals(1000L, repo.session("a").consumedMs)
    }

    @Test fun loweringBoundaryPreservesUsageAndRequiresPromptImmediately() = run {
        setup(); controller.observe(signal()); advance(600); controller.observe(signal())
        prefs = prefs.copy(sessionBoundaryMs = 500)
        assertEquals(BlockReason.SESSION_BOUNDARY, controller.observe(signal()).blockedBy)
        assertTrue(repo.session("a").promptPending)
        assertEquals(600L, repo.combinedUsage().consumedMs)
    }

    @Test fun bankedCreditStopsAtSessionBoundaryWithoutRequiringAnotherExercise() = run {
        setup(base = 0)
        val offer = (engine.offer("a", Exercise.PUSH_UP, ChallengeMode.ADVANCE) as OfferResult.Available).offer
        engine.start("award", offer); engine.complete("award", 2)
        controller.observe(signal()); advance(1800)
        assertEquals(BlockReason.SESSION_BOUNDARY, controller.observe(signal()).blockedBy)
        assertEquals(119_000L, repo.dailyUsage("a").awardedMs - repo.dailyUsage("a").earnedConsumedMs)
        assertEquals(0L, repo.dailyUsage("a").baseConsumedMs)
        assertEquals(1, store.awards.size)
    }

    @Test fun continueResetsOnlySessionAndSpendsExistingEarnedCredit() = run {
        setup(base = 0)
        val offer = (engine.offer("a", Exercise.PUSH_UP, ChallengeMode.ADVANCE) as OfferResult.Available).offer
        engine.start("one", offer); engine.complete("one", 2)
        advance(1000); spend(0, 1000)
        val displayed = engine.sessionAccess("a")
        val before = repo.dailyUsage("a")
        advance(2000)
        assertEquals(SessionActionResult.APPLIED, engine.resolveSession(displayed, SessionAction.CONTINUE))
        assertEquals(before, repo.dailyUsage("a"))
        assertEquals(SessionState("a", wall.localDate().toString()), repo.session("a"))
        // A queued pre-tap observation cannot charge prompt time after the reset.
        assertEquals(0L, spend(1000, 3000).spentMs)
        advance(500); assertEquals(500L, spend(3000, 3500).earnedSpentMs)
        assertEquals(1, store.awards.size)
        assertEquals(120_000L, repo.dailyUsage("a").awardedMs)
    }

    @Test fun duplicateOrPreviousWindowTapCannotResetNewProgressOrPrompt() = run {
        setup(); advance(1000); spend(0, 1000)
        val first = engine.sessionAccess("a")
        assertEquals(SessionActionResult.APPLIED, engine.resolveSession(first, SessionAction.CONTINUE))
        assertEquals(SessionActionResult.STALE, engine.resolveSession(first, SessionAction.CONTINUE))
        advance(500); spend(1000, 1500)
        assertEquals(SessionActionResult.STALE, engine.resolveSession(first, SessionAction.FINISH))
        assertEquals(500L, repo.session("a").consumedMs)
        advance(500); spend(1500, 2000)
        assertEquals(first.session, engine.sessionAccess("a").session)
        assertEquals(SessionActionResult.STALE, engine.resolveSession(first, SessionAction.CONTINUE))
        assertTrue(repo.session("a").promptPending)
    }

    @Test fun finishKeepsBothWalletsAndOtherAppWindowUnchanged() = run {
        setup(); advance(200); spend(0, 200, "b"); advance(1000); spend(200, 1200)
        val before = repo.dailyUsage("a"); val combined = repo.combinedUsage()
        val other = repo.session("b")
        advance(3000)
        assertEquals(SessionActionResult.APPLIED, engine.resolveSession(engine.sessionAccess("a"), SessionAction.FINISH))
        assertEquals(before, repo.dailyUsage("a")); assertEquals(combined, repo.combinedUsage())
        assertEquals(other, repo.session("b")); assertEquals(0L, repo.session("a").consumedMs)
        assertEquals(0L, spend(1200, 4200).spentMs)
    }

    @Test fun appBudgetAndSessionCollisionShowsBudgetBlockAndRefusesContinue() = run {
        setup(base = 1000); controller.observe(signal()); advance(1000)
        assertEquals(BlockReason.APP_BUDGET, controller.observe(signal()).blockedBy)
        val displayed = engine.sessionAccess("a")
        assertEquals(SessionActionResult.BUDGET_BLOCKED, engine.resolveSession(displayed, SessionAction.CONTINUE))
        assertTrue(repo.session("a").promptPending)
    }

    @Test fun globalBudgetAndSessionCollisionShowsGlobalBlockEvenWithEarnedCredit() = run {
        setup(); prefs = prefs.copy(combinedCeilingMs = 1000)
        val offer = (engine.offer("a", Exercise.PUSH_UP, ChallengeMode.ADVANCE) as OfferResult.Available).offer
        engine.start("one", offer); engine.complete("one", 2)
        controller.observe(signal()); advance(1000)
        assertEquals(BlockReason.COMBINED_CEILING, controller.observe(signal()).blockedBy)
        assertEquals(120_000L, engine.budget("a").earnedRemainingMs)
        assertEquals(SessionActionResult.BUDGET_BLOCKED,
            engine.resolveSession(engine.sessionAccess("a"), SessionAction.CONTINUE))
    }

    @Test fun budgetReducedWhilePromptVisibleRefusesContinueWithoutReset() = run {
        setup(); advance(1000); spend(0, 1000); val displayed = engine.sessionAccess("a")
        repo.saveTarget(TargetConfig("a", baseAllowanceMs = 500))
        assertEquals(SessionActionResult.BUDGET_BLOCKED, engine.resolveSession(displayed, SessionAction.CONTINUE))
        assertEquals(displayed.session, repo.session("a"))
        assertEquals(1000L, repo.combinedUsage().consumedMs)
    }

    @Test fun pendingPromptsAndPartialWindowsResetAtMidnightButOldTapCannotResetNewDay() = run {
        setup(); advance(1000); spend(0, 1000); val old = engine.sessionAccess("a")
        epoch += 86_400_000
        val fresh = engine.sessionAccess("a")
        assertEquals(0L, fresh.session.consumedMs); assertFalse(fresh.session.promptPending)
        assertNull(fresh.blockedBy)
        assertEquals(SessionActionResult.STALE, engine.resolveSession(old, SessionAction.CONTINUE))
        assertEquals(1000L, repo.session("a", old.session.day).consumedMs)
    }

    @Test fun boundaryWithinMidnightCheckpointStopsOldWindowAndStartsFreshDailyWindow() = run {
        epoch = Instant.parse("2026-10-16T23:59:59.500Z").toEpochMilli()
        setup(); prefs = prefs.copy(sessionBoundaryMs = 300)
        val old = wall.localDate().toString()
        controller.observe(signal()); advance(1000)
        assertEquals(BlockReason.SESSION_BOUNDARY, controller.observe(signal()).blockedBy)
        assertEquals(300L, repo.session("a", old).consumedMs)
        assertEquals(300L, repo.session("a").consumedMs)
        assertEquals(300L, repo.combinedUsage(old).consumedMs)
        assertEquals(300L, repo.combinedUsage().consumedMs)
    }

    @Test fun pausedScreenOwnUiAndLongGapsDoNotAdvanceSession() = run {
        setup(); controller.observe(signal()); advance(300); controller.observe(signal(interactive = false))
        advance(4000); controller.observe(signal("self", own = true)); advance(4000)
        controller.observe(signal()); advance(6000); controller.observe(signal())
        assertEquals(300L, repo.session("a").consumedMs)
        assertEquals(700L, controller.observe(signal()).tickDelayMs)
    }

    @Test fun loweringBoundaryBelowConsumedAndThenRaisingKeepsLatchedPrompt() = run {
        setup(); advance(700); spend(0, 700)
        prefs = prefs.copy(sessionBoundaryMs = 500)
        assertTrue(engine.sessionAccess("a").session.promptPending)
        prefs = prefs.copy(sessionBoundaryMs = 10_000)
        assertEquals(BlockReason.SESSION_BOUNDARY, engine.sessionAccess("a").blockedBy)
        assertEquals(SessionActionResult.APPLIED,
            engine.resolveSession(engine.sessionAccess("a"), SessionAction.CONTINUE))
        assertEquals(10_000L, engine.sessionAccess("a").sessionRemainingMs)
    }

    @Test fun midnightDuringActionReadCannotResetDisplayedWindow() = run {
        setup(); advance(1000); spend(0, 1000); val old = engine.sessionAccess("a")
        store.onUsageRead = { epoch += 86_400_000 }
        assertEquals(SessionActionResult.STALE, engine.resolveSession(old, SessionAction.CONTINUE))
        assertEquals(old.session, repo.session("a", old.session.day))
    }

    @Test fun lostUiEligibilityRollsBackResetAndCursorAfterSuspendedCleanup() = run {
        setup(); advance(1000); spend(0, 1000); val old = engine.sessionAccess("a")
        var eligible = true; var reads = 0
        store.onDayReconciliation = { if (++reads == 2) eligible = false }
        advance(2000)
        assertEquals(SessionActionResult.STALE, engine.resolveSession(old, SessionAction.CONTINUE) { eligible })
        store.onDayReconciliation = null
        assertEquals(old.session, repo.session("a"))
        assertEquals(1000L, store.transaction { cursor("boot") }!!.throughElapsedMs)
    }

    @Test fun removedTargetAndNonPromptTapCannotResetWindow() = run {
        setup(); advance(300); spend(0, 300)
        assertEquals(SessionActionResult.STALE, engine.resolveSession(engine.sessionAccess("a"), SessionAction.CONTINUE))
        advance(700); spend(300, 1000); val old = engine.sessionAccess("a")
        repo.removeTarget("a")
        assertEquals(SessionActionResult.STALE, engine.resolveSession(old, SessionAction.FINISH))
        assertEquals(old.session, repo.session("a"))
    }

    @Test fun sessionResetWriteFailureKeepsPromptAndRetryDoesNotChangeWallet() = run {
        setup(); advance(1000); spend(0, 1000); val old = engine.sessionAccess("a")
        val wallet = repo.dailyUsage("a")
        advance(1000); store.failNextSession = true
        try { engine.resolveSession(old, SessionAction.CONTINUE); fail("reset must fail") }
        catch (_: IllegalStateException) { }
        assertEquals(old.session, repo.session("a"))
        assertEquals(1000L, store.transaction { cursor("boot") }!!.throughElapsedMs)
        assertEquals(wallet, repo.dailyUsage("a"))
        assertEquals(SessionActionResult.APPLIED, engine.resolveSession(old, SessionAction.CONTINUE))
        assertEquals(wallet, repo.dailyUsage("a"))
    }

    @Test fun oversizedRetainedSessionLatchesWithoutOverflowOrSpending() = run {
        setup(); repo.saveSession(SessionState("a", wall.localDate().toString(), Long.MAX_VALUE))
        advance(1000)
        assertEquals(0L, spend(0, 1000).spentMs)
        assertEquals(BlockReason.SESSION_BOUNDARY, engine.sessionAccess("a").blockedBy)
        assertEquals(0L, repo.combinedUsage().consumedMs)
    }

    @Test fun concurrentContinueCallbacksResetOnceAndNeverAward() {
        var displayed: SessionAccess? = null
        run { setup(); advance(1000); spend(0, 1000); displayed = engine.sessionAccess("a") }
        val pool = Executors.newFixedThreadPool(4)
        try {
            val results = pool.invokeAll((1..16).map { Callable {
                var answer: SessionActionResult? = null
                run { answer = engine().resolveSession(checkNotNull(displayed), SessionAction.CONTINUE) }
                answer
            } }).map { it.get() }
            assertEquals(1, results.count { it == SessionActionResult.APPLIED })
            assertEquals(15, results.count { it == SessionActionResult.STALE })
            run {
                assertEquals(1000L, repo.combinedUsage().consumedMs)
                assertEquals(0L, repo.session("a").consumedMs)
                assertTrue(store.awards.isEmpty())
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
