package com.example.scrollfit.challenge

import com.example.scrollfit.core.budget.StartResult
import com.example.scrollfit.core.data.Challenge
import com.example.scrollfit.core.data.ChallengeMode
import com.example.scrollfit.core.data.ChallengeStatus
import com.example.scrollfit.core.exercise.Exercise
import com.example.scrollfit.core.pose.CountingProgress
import com.example.scrollfit.core.pose.FramingIssue
import com.example.scrollfit.core.pose.FramingObservation
import com.example.scrollfit.core.time.MonotonicClock
import com.example.scrollfit.platform.pose.CameraPoseState
import com.example.scrollfit.platform.pose.CameraPoseStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GuideJourneyQualificationTest {
    private class TestClock : MonotonicClock {
        var elapsed = 0L
        override fun elapsedRealtimeMillis() = elapsed
    }

    @Test fun oldPositionRunCallbackCannotMakeNewRunReady() {
        val clock = TestClock()
        val journey = journey(clock)
        journey.continueToPosition()
        val oldRun = requireNotNull(journey.positionRunKey)
        journey.resetPosition()
        feedReady(journey, clock, oldRun)

        assertFalse(journey.ready)
        assertTrue(journey.positionRunKey != oldRun)
    }

    @Test fun permissionLossDuringCountdownCancelsStartedChallengeBeforeActivation() {
        val clock = TestClock()
        var cancellations = 0
        var activations = 0
        val challenge = challenge("permission-loss")
        val journey = journey(clock,
            start = { StartResult.Started(challenge) },
            cancel = { cancellations++ },
            activate = { activations++ },
        )
        journey.continueToPosition()
        feedReady(journey, clock, requireNotNull(journey.positionRunKey))
        journey.requestStart(allowed = true)
        assertEquals(GuideStage.COUNTDOWN, journey.stage)

        journey.permissionChanged(false)

        assertEquals(0, activations)
        assertEquals(1, cancellations)
        assertEquals(GuideStage.POSITION, journey.stage)
        assertNull(journey.countdownRemaining)
    }

    @Test fun failedExplicitStartReturnsToPositionWithoutStartingCountdown() {
        val clock = TestClock()
        var attempts = 0
        val journey = journey(clock, start = { attempts++; error("injected save failure") })
        journey.continueToPosition()
        feedReady(journey, clock, requireNotNull(journey.positionRunKey))
        journey.requestStart(allowed = true)

        assertEquals(1, attempts)
        assertEquals(GuideStage.POSITION, journey.stage)
        assertTrue(journey.startError)
        assertNull(journey.countdownRemaining)
    }

    private fun journey(
        clock: TestClock,
        start: suspend () -> StartResult = { StartResult.Started(challenge("guide-start")) },
        cancel: suspend (String) -> Unit = {},
        activate: (String) -> Unit = {},
    ) = GuideJourney(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        clock = clock,
        start = start,
        cancel = cancel,
        activate = activate,
        initialExercise = Exercise.SQUAT,
        initiallyPermissionGranted = true,
    )

    private fun feedReady(journey: GuideJourney, clock: TestClock, runKey: String) {
        for (time in 0L..1_000L step 100L) {
            clock.elapsed = time
            val framing = FramingObservation(time, FramingIssue.FRAMED)
            journey.observePosition(runKey,
                CameraPoseState(CameraPoseStatus.TRACKING,
                    progress = CountingProgress(0, trackingAvailable = true),
                    timestampMs = time, framing = framing), nowMs = time)
        }
    }

    private fun challenge(id: String) = Challenge(id, "com.example.target",
        LocalDate.of(2026, 10, 26).toString(), Exercise.SQUAT, ChallengeMode.ADVANCE,
        repetitionsRequired = 10, rewardMs = 60_000, startedAtEpochMs = 0,
        status = ChallengeStatus.ACTIVE)
}
