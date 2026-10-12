package com.example.scrollfit.core.spike

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpikeCountdownTest {
    @Test fun targetIntervalsAccumulateAcrossSwitches() {
        val clock = SpikeCountdown()
        clock.update(100, true)
        clock.update(5_100, false)
        clock.update(20_100, true)
        clock.update(23_100, false)
        assertEquals(8_000L, clock.consumedMillis)
        assertEquals(22_000L, clock.remainingMillis)
    }

    @Test fun screenOffAndUnrestrictedIntervalsDoNotSpendAllowance() {
        val clock = SpikeCountdown()
        clock.update(0, true)
        clock.update(2_000, false)
        clock.update(62_000, false)
        clock.update(72_000, true)
        clock.update(75_000, false)
        assertEquals(5_000L, clock.consumedMillis)
    }

    @Test fun duplicateCallbacksAndTicksChargeEachIntervalOnce() {
        val clock = SpikeCountdown()
        clock.update(0, true)
        clock.update(1_000, true)
        clock.update(1_000, true)
        clock.update(2_000, true)
        clock.update(2_000, false)
        assertEquals(2_000L, clock.consumedMillis)
    }

    @Test fun exactDeadlineExhaustsAndBlockedTimeIsFree() {
        val clock = SpikeCountdown()
        clock.update(0, true)
        clock.update(29_999, true)
        assertFalse(clock.exhausted)
        clock.update(30_000, false)
        assertTrue(clock.exhausted)
        clock.update(90_000, false)
        assertEquals(30_000L, clock.consumedMillis)
    }

    @Test fun delayedTickClampsAtAllowanceInsteadOfOvercharging() {
        val clock = SpikeCountdown()
        clock.update(0, true)
        clock.update(Long.MAX_VALUE, true)
        assertEquals(0L, clock.remainingMillis)
        assertEquals(30_000L, clock.consumedMillis)
    }

    @Test fun restoreKeepsConsumptionAndDoesNotChargeUnknownDowntime() {
        val clock = SpikeCountdown(consumedMillis = 12_000)
        clock.update(200_000, false)
        clock.update(250_000, true)
        clock.update(251_000, false)
        assertEquals(13_000L, clock.consumedMillis)
    }

    @Test fun restoredExhaustionCannotGrantFreshTime() {
        val clock = SpikeCountdown(consumedMillis = 30_000)
        clock.update(0, true)
        clock.update(1_000, true)
        assertTrue(clock.exhausted)
    }

    @Test fun staleTimestampCannotDuplicateAnIntervalOrPauseNewerState() {
        val clock = SpikeCountdown()
        clock.update(1_000, true)
        clock.update(2_000, true)
        clock.update(1_500, false)
        clock.update(3_000, false)
        assertEquals(2_000L, clock.consumedMillis)
    }

    @Test fun permissionLossAndDisablePauseWithoutResettingConsumption() {
        val clock = SpikeCountdown()
        clock.update(0, true)
        clock.update(4_000, false)
        clock.update(40_000, false)
        assertEquals(4_000L, clock.consumedMillis)
        clock.update(41_000, true)
        clock.update(42_000, false)
        assertEquals(5_000L, clock.consumedMillis)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidRestoredConsumptionIsRejected() { SpikeCountdown(consumedMillis = -1) }
}
