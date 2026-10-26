package com.example.scrollfit.core.usage

import com.example.scrollfit.core.budget.*
import com.example.scrollfit.core.data.*
import com.example.scrollfit.core.exercise.Exercise
import com.example.scrollfit.core.time.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*
import kotlin.coroutines.*

class MonitoringControllerTest {
    private var elapsed = 0L
    private var epoch = Instant.parse("2026-10-12T10:00:00Z").toEpochMilli()
    private val wall = object : WallClock {
        override fun localDate() = Instant.ofEpochMilli(epoch).atZone(ZoneOffset.UTC).toLocalDate()
        override fun epochMillis() = epoch
    }
    private val mono = object : MonotonicClock { override fun elapsedRealtimeMillis() = elapsed }
    private val store = MemoryStore()
    private val repo = DurableRepository(store, wall)
    private var prefs = AppPreferences(combinedCeilingMs = 5000)
    private fun engine(boot: String = "boot") = BudgetEngine(store, wall, mono, boot) { prefs }
    private var controller = MonitoringController(engine(), setOf("self", "system", "dialer"))
    private suspend fun setup(base: Long = 5000) {
        repo.saveTarget(TargetConfig("a", baseAllowanceMs = base))
        repo.saveTarget(TargetConfig("b", baseAllowanceMs = base))
    }
    private fun signal(app: String? = "a", interactive: Boolean = true, enabled: Boolean = true,
        access: Boolean = true, connected: Boolean = true, own: Boolean = false, known: Boolean = true) =
        MonitorSignal(MonitorSample(elapsed, epoch, "UTC"), app, enabled, access, connected, interactive, own, known)
    private fun advance(ms: Long) { elapsed += ms; epoch += ms }

    @Test fun transitionsCheckpointOneIntervalAcrossTwoAppsAndUnrestrictedTime() = run {
        setup(); controller.observe(signal()); advance(700); controller.observe(signal("b"))
        advance(800); controller.observe(signal("other")); advance(500); controller.observe(signal("a"))
        advance(400); controller.observe(signal("self", own = true))
        assertEquals(1100L, repo.dailyUsage("a").baseConsumedMs)
        assertEquals(800L, repo.dailyUsage("b").baseConsumedMs)
        assertEquals(1900L, repo.combinedUsage().consumedMs)
    }

    @Test fun screenOffLockOwnUiCameraAndBlockerDoNotConsume() = run {
        setup(base = 500); controller.observe(signal()); advance(200); controller.observe(signal(interactive = false))
        advance(1000); controller.observe(signal("self", own = true)); advance(1000); controller.observe(signal("a"))
        advance(300); val blocked = controller.observe(signal())
        assertEquals(BlockReason.APP_BUDGET, blocked.blockedBy)
        advance(1000); controller.observe(signal())
        assertEquals(500L, repo.dailyUsage("a").baseConsumedMs)
        assertEquals(500L, repo.combinedUsage().consumedMs)
    }

    @Test fun combinedCeilingHasDistinctReasonAndSwitchingCannotBypassIt() = run {
        setup(); prefs = prefs.copy(combinedCeilingMs = 1000)
        controller.observe(signal()); advance(700); controller.observe(signal("b")); advance(700)
        assertEquals(BlockReason.COMBINED_CEILING, controller.observe(signal("b")).blockedBy)
        advance(500); assertEquals(BlockReason.COMBINED_CEILING, controller.observe(signal()).blockedBy)
        assertEquals(1000L, repo.combinedUsage().consumedMs)
    }

    @Test fun healthLossPausesAndPermissionRecoveryNeedsFreshForegroundObservation() = run {
        setup(); controller.observe(signal()); advance(300)
        assertEquals(MonitoringHealth.PERMISSION_MISSING, controller.observe(signal(access = false)).health)
        advance(1000); controller.observe(signal(access = false)); advance(500)
        assertEquals(MonitoringHealth.SERVICE_STOPPED, controller.observe(signal(known = false)).health)
        advance(500); controller.observe(signal()); advance(200)
        assertEquals(MonitoringHealth.DISABLED, controller.observe(signal(enabled = false)).health)
        assertEquals(500L, repo.combinedUsage().consumedMs)
        assertEquals(MonitoringHealth.SERVICE_STOPPED, controller.observe(signal(connected = false)).health)
    }

    @Test fun criticalPackagesRemainUnchargeableEvenIfStoredAsSelected() = run {
        setup(); repo.saveTarget(TargetConfig("system", baseAllowanceMs = 0))
        assertNull(controller.observe(signal("system")).blockedBy)
        advance(500); assertNull(controller.observe(signal("dialer")).blockedBy)
        advance(500); assertFalse(controller.observe(signal("self")).shouldTick)
        assertEquals(0L, repo.combinedUsage().consumedMs)
    }

    @Test fun duplicateAndOutOfOrderCallbacksDoNotSpendOrRestoreStaleForeground() = run {
        setup(); controller.observe(signal()); advance(1000); controller.observe(signal())
        controller.observe(signal()); val stale = signal("b").copy(sample = MonitorSample(100, epoch - 900, "UTC"))
        controller.observe(stale); advance(500); controller.observe(signal())
        assertEquals(1500L, repo.dailyUsage("a").baseConsumedMs)
        assertEquals(0L, repo.dailyUsage("b").baseConsumedMs)
    }

    @Test fun processRestartAndRebootExcludeUnknownDowntimeAndKeepDurableBudget() = run {
        setup(); controller.observe(signal()); advance(600); controller.observe(signal())
        advance(1000); controller = MonitoringController(engine(), emptySet())
        controller.observe(signal()); advance(400); controller.observe(signal())
        assertEquals(1000L, repo.combinedUsage().consumedMs)
        elapsed = 0; controller = MonitoringController(engine("next-boot"), emptySet())
        controller.observe(signal()); advance(300); controller.observe(signal())
        assertEquals(1300L, repo.combinedUsage().consumedMs)
    }

    @Test fun midnightSplitsIntoOriginalDailyRowsAndExpiresOldRewardChallenge() = run {
        epoch = Instant.parse("2026-10-12T23:59:59.600Z").toEpochMilli()
        setup(); val old = wall.localDate().toString()
        val budget = engine(); val offer = (budget.offer("a", Exercise.PUSH_UP, ChallengeMode.ADVANCE) as OfferResult.Available).offer
        budget.start("old", offer)
        controller.observe(signal()); advance(1000); controller.observe(signal())
        assertEquals(400L, repo.dailyUsage("a", old).baseConsumedMs)
        assertEquals(600L, repo.dailyUsage("a").baseConsumedMs)
        assertEquals(400L, repo.combinedUsage(old).consumedMs)
        assertEquals(600L, repo.combinedUsage().consumedMs)
        assertEquals(ChallengeStatus.CANCELLED, repo.challenge("old")!!.status)
    }

    @Test fun clockAndTimeZoneDiscontinuitiesReportGapWithoutInventingConsumption() = run {
        setup(); controller.observe(signal()); advance(500); epoch += 60_000
        val jump = controller.observe(signal())
        assertEquals(MonitorIssue.CLOCK_CHANGED, jump.issue)
        assertEquals(500L, jump.unmonitoredMs)
        assertEquals(0L, repo.combinedUsage().consumedMs)
        advance(500); controller.observe(signal())
        advance(500); val zone = controller.observe(signal().copy(sample = MonitorSample(elapsed, epoch, "Asia/Tehran")))
        assertEquals(MonitorIssue.CLOCK_CHANGED, zone.issue)
        assertEquals(500L, repo.combinedUsage().consumedMs)
    }

    @Test fun schedulingGapIsUnmonitoredAndFreshEventRestartsWithoutBackfill() = run {
        setup(); controller.observe(signal()); advance(6000)
        val gap = controller.observe(signal())
        assertEquals(MonitorIssue.OBSERVATION_GAP, gap.issue)
        assertEquals(6000L, gap.unmonitoredMs)
        assertEquals(0L, repo.combinedUsage().consumedMs)
        controller.observe(signal()); advance(500); controller.observe(signal())
        assertEquals(500L, repo.combinedUsage().consumedMs)
    }

    @Test fun staleSamplesPauseWithoutSpendUntilFreshInterval() = run {
        setup(); controller.observe(signal()); advance(5001)
        val stale = controller.observe(signal())
        assertEquals(MonitorIssue.OBSERVATION_GAP, stale.issue)
        assertEquals(5001L, stale.unmonitoredMs)
        controller.abandon()
        assertEquals(0L, repo.combinedUsage().consumedMs)

        advance(1000); controller.observe(signal(known = false))
        advance(100); controller.observe(signal())
        advance(100); controller.observe(signal())
        assertEquals(100L, repo.combinedUsage().consumedMs)
    }

    @Test fun refreshedProtectedRoleStopsAndThenRestoresEligibilitySafely() = run {
        setup(); controller.observe(signal("a")); advance(500)
        controller.observe(signal("a", known = false))
        controller.updateProtectedPackages(setOf("a"))
        advance(500); controller.observe(signal("a"))
        assertEquals(500L, repo.combinedUsage().consumedMs)

        controller.updateProtectedPackages(emptySet())
        advance(100); controller.observe(signal("a"))
        advance(100); controller.observe(signal("a"))
        assertEquals(600L, repo.combinedUsage().consumedMs)
    }

    @Test fun storageFailureNeverResetsBudgetAndAbandonDiscardsUnknownInterval() = run {
        setup(); controller.observe(signal()); advance(500); store.failNextUsage = true
        try { controller.observe(signal()); fail() } catch (_: IllegalStateException) { }
        controller.abandon(); advance(500); controller.observe(signal()); advance(200); controller.observe(signal())
        assertEquals(200L, repo.combinedUsage().consumedMs)
    }

    @Test fun savedRemovalAndSettingsEditsRetainEnforcedTotals() = run {
        setup(); controller.observe(signal()); advance(500); controller.observe(signal())
        repo.removeTarget("a"); advance(500); controller.observe(signal())
        assertEquals(500L, repo.dailyUsage("a").baseConsumedMs)
        repo.saveTarget(TargetConfig("a", baseAllowanceMs = 600))
        controller.observe(signal()); advance(500)
        assertEquals(BlockReason.APP_BUDGET, controller.observe(signal()).blockedBy)
        assertEquals(600L, repo.combinedUsage().consumedMs)
    }

    @Test fun splitScreenAndPipSignalsChargeOnlyMostRecentForegroundPackage() = run {
        setup(); controller.observe(signal("a")); advance(500); controller.observe(signal("b"))
        advance(500); controller.observe(signal("launcher")); advance(500); controller.observe(signal("launcher"))
        assertEquals(500L, repo.dailyUsage("a").baseConsumedMs)
        assertEquals(500L, repo.dailyUsage("b").baseConsumedMs)
        assertEquals(1000L, repo.combinedUsage().consumedMs)
    }

    @Test fun tickIsOnlyNeededForObservedTargetsAndBoundedByRemainingBudget() = run {
        setup(base = 300)
        assertFalse(controller.observe(signal("other")).shouldTick)
        val active = controller.observe(signal())
        assertTrue(active.shouldTick); assertEquals(300L, active.tickDelayMs)
        advance(300); val blocked = controller.observe(signal())
        assertEquals(1000L, blocked.tickDelayMs)
        assertFalse(controller.observe(signal(interactive = false)).shouldTick)
    }

    @Test fun ownershipLeasePreventsTwoMonitorsAndStaleReleaseCannotUnlockNewOwner() {
        val owner = MonitorOwnership()
        val first = Any(); val second = Any()
        assertTrue(owner.acquire(first)); assertFalse(owner.acquire(second))
        assertTrue(owner.acquire(first)); owner.release(second); assertFalse(owner.acquire(second))
        owner.release(first); assertTrue(owner.acquire(second)); owner.release(first)
        assertFalse(owner.acquire(first))
    }

    @Test fun midnightDuringStorageStillAccountsObservedOriginalDaySegment() = run {
        epoch = Instant.parse("2026-10-12T23:59:58.800Z").toEpochMilli()
        setup(); val old = wall.localDate().toString()
        controller.observe(signal()); advance(1000)
        store.onUsageRead = { advance(1000) }
        val result = controller.observe(signal())
        assertEquals(1000L, repo.dailyUsage("a", old).baseConsumedMs)
        assertEquals(0L, repo.dailyUsage("a").baseConsumedMs)
        assertEquals(0L, result.unmonitoredMs)
    }

    @Test fun largeDateJumpDuringStorageReportsRejectedObservedSegmentAsGap() = run {
        setup(); controller.observe(signal()); advance(500)
        store.onUsageRead = { epoch += 2 * 86_400_000L }
        val result = controller.observe(signal())
        assertEquals(MonitorIssue.CLOCK_CHANGED, result.issue)
        assertEquals(500L, result.unmonitoredMs)
        assertEquals(0L, repo.combinedUsage().consumedMs)
    }

    private fun run(block: suspend () -> Unit) {
        var completed: Result<Unit>? = null
        block.startCoroutine(object : Continuation<Unit> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<Unit>) { completed = result }
        })
        checkNotNull(completed).getOrThrow()
    }
}
