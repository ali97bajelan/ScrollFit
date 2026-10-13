package com.example.scrollfit.core.exercise

import com.example.scrollfit.core.pose.CountingProgress
import com.example.scrollfit.core.pose.Landmark
import com.example.scrollfit.core.pose.PoseSample
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class JumpingJackCounterTest {
    // Physical image-space geometry is preserved when normalized for each aspect.
    private fun pose(t: Long, arm: Double = 10.0, feet: Double = 0.4,
                     rightArm: Double = arm, footShift: Double = 0.0, aspect: Float = 1f): PoseSample {
        val joints = MutableList(33) { Landmark(0.5f, 0.5f, 0f, 0f, 0f) }
        fun point(x: Double, y: Double) = Landmark((0.5 + (x - 0.5) / aspect).toFloat(),
            y.toFloat(), 0f, 0.95f, 0.95f)
        for (side in 0..1) {
            val direction = if (side == 0) -1 else 1
            val x = 0.5 + direction * 0.04
            val radians = Math.toRadians(if (side == 0) arm else rightArm)
            joints[11 + side] = point(x, 0.35)
            joints[23 + side] = point(x, 0.55)
            joints[15 + side] = point(x + direction * 0.25 * sin(radians), 0.35 + 0.25 * cos(radians))
            joints[27 + side] = point(0.5 + footShift * 0.2 + direction * feet * 0.1, 0.9)
        }
        return PoseSample(t, joints, aspect)
    }

    private inner class Sequence(val counter: JumpingJackCounter = JumpingJackCounter(), val aspect: Float = 1f) {
        var time = 0L
        var progress = CountingProgress(0, false)
        fun hold(arm: Double = 10.0, feet: Double = 0.4, durationMs: Long = 700,
                 rightArm: Double = arm, footShift: Double = 0.0, transform: (PoseSample) -> PoseSample = { it }) {
            repeat((durationMs / 50).toInt()) {
                time += 50
                progress = counter.accept(transform(pose(time, arm, feet, rightArm, footShift, aspect)), time)
            }
        }
        fun closed(durationMs: Long = 700) = hold(durationMs = durationMs)
        fun open(durationMs: Long = 700) = hold(160.0, 2.2, durationMs)
        fun cycle() { closed(); open(); closed() }
        fun missing() { time += 50; progress = counter.accept(null, time) }
    }

    @Test fun completeCyclesCountOnceAndHoldsDoNotRepeat() {
        val s = Sequence()
        s.closed(); s.open(2000)
        assertEquals(0, s.progress.repetitions)
        s.closed(2000)
        assertEquals(1, s.progress.repetitions)
        s.open(); s.closed()
        assertEquals(2, s.progress.repetitions)
    }

    @Test fun startingOpenRequiresClosedThenAnotherCompleteCycle() {
        val s = Sequence()
        s.open(); s.closed()
        assertEquals(0, s.progress.repetitions)
        s.open(); s.closed()
        assertEquals(1, s.progress.repetitions)
    }

    @Test fun portraitAndLandscapeAspectsCount() {
        for (aspect in listOf(0.75f, 1f, 4f / 3f)) {
            val s = Sequence(aspect = aspect)
            s.cycle()
            assertEquals("aspect=$aspect", 1, s.progress.repetitions)
        }
    }

    @Test fun repeatedCyclesAtThirtyFpsCountWithShortEndpointHolds() {
        val c = JumpingJackCounter()
        var time = 0L
        fun hold(open: Boolean): CountingProgress {
            var progress = CountingProgress(0, false)
            repeat(12) { // 396 ms per endpoint, about 1.26 cycles/second.
                time += 33
                progress = c.accept(pose(time, if (open) 160.0 else 10.0,
                    if (open) 2.2 else 0.4), time)
            }
            return progress
        }
        hold(false)
        repeat(4) { cycle ->
            hold(true)
            assertEquals(cycle + 1, hold(false).repetitions)
        }
    }

    @Test fun mirroredTranslatedAndScaledPosesCount() {
        for (mirror in listOf(false, true)) {
            val s = Sequence()
            for ((arm, feet) in listOf(10.0 to 0.4, 160.0 to 2.2, 10.0 to 0.4)) {
                s.hold(arm, feet, transform = { p -> p.copy(landmarks = p.landmarks.map { l ->
                    l.copy(x = 0.45f + (l.x - 0.5f) * (if (mirror) -0.7f else 0.7f),
                        y = 0.4f + (l.y - 0.5f) * 0.7f)
                }) })
            }
            assertEquals(1, s.progress.repetitions)
        }
    }

    @Test fun cameraScaleChangeAloneDoesNotCount() {
        val s = Sequence()
        for (scale in listOf(1f, 0.6f, 0.9f, 0.7f, 1f)) s.hold(transform = { p ->
            p.copy(landmarks = p.landmarks.map { l ->
                l.copy(x = 0.5f + (l.x - 0.5f) * scale, y = 0.5f + (l.y - 0.5f) * scale)
            })
        })
        assertEquals(0, s.progress.repetitions)
    }

    @Test fun gradualArmAndLegMovementCounts() {
        val s = Sequence()
        s.closed()
        for ((arm, feet) in listOf(50.0 to 0.8, 90.0 to 1.2, 130.0 to 1.6, 160.0 to 2.2,
                130.0 to 1.6, 90.0 to 1.2, 50.0 to 0.8, 10.0 to 0.4)) s.hold(arm, feet, 350)
        s.closed()
        assertEquals(1, s.progress.repetitions)
    }

    @Test fun armsOnlyAndLegsOnlyCannotCount() {
        for (armsOnly in listOf(false, true)) {
            val s = Sequence()
            s.closed()
            repeat(3) { s.hold(if (armsOnly) 160.0 else 10.0, if (armsOnly) 0.4 else 2.2); s.closed() }
            assertEquals(0, s.progress.repetitions)
        }
    }

    @Test fun oneRaisedArmDoesNotQualifyAsOpen() {
        for (left in listOf(false, true)) {
            val s = Sequence()
            s.closed(); s.hold(if (left) 160.0 else 10.0, 2.2, rightArm = if (left) 10.0 else 160.0); s.closed()
            assertEquals(0, s.progress.repetitions)
        }
    }

    @Test fun oneOutwardFootDoesNotQualifyAsOpen() {
        for (shift in listOf(-0.9, 0.9)) {
            val s = Sequence()
            s.closed(); s.hold(160.0, 2.2, footShift = shift); s.closed()
            assertEquals(0, s.progress.repetitions)
        }
    }

    @Test fun partialArmsOrPartialFeetCannotCount() {
        for ((arm, feet) in listOf(100.0 to 2.2, 160.0 to 1.1)) {
            val s = Sequence()
            s.closed(); s.hold(arm, feet); s.closed()
            assertEquals(0, s.progress.repetitions)
        }
    }

    @Test fun incompleteReturnRequiresBothArmsAndFeetClosed() {
        for ((arm, feet) in listOf(60.0 to 0.4, 10.0 to 1.0)) {
            val s = Sequence()
            s.closed(); s.open(); s.hold(arm, feet)
            assertEquals(0, s.progress.repetitions)
            s.closed()
            assertEquals(1, s.progress.repetitions)
        }
    }

    @Test fun alternatingArmAndLegNoiseNeverFormsASimultaneousOpenEndpoint() {
        val s = Sequence(JumpingJackCounter(JumpingJackConfig(smoothingAlpha = 0.05, stablePhaseMs = 50)))
        s.closed()
        // First charge the arm EMA without spread feet, then lower raw arms
        // while spreading raw feet. Smoothing alone can falsely combine them.
        s.hold(170.0, 0.4, 4000)
        s.hold(139.0, 2.2, 2000)
        s.closed(4000)
        assertEquals(0, s.progress.repetitions)
    }

    @Test fun endpointJitterAndIsolatedOpenSpikeDoNotCount() {
        val s = Sequence()
        s.closed()
        repeat(20) { s.hold(139.0, 1.59, 50); s.hold(141.0, 1.61, 50) }
        s.closed(); s.open(50); s.closed()
        assertEquals(0, s.progress.repetitions)
    }

    @Test fun smoothingRejectsBriefOpenEvenWithShortDwell() {
        val s = Sequence(JumpingJackCounter(JumpingJackConfig(stablePhaseMs = 50, minTransitionMs = 50)))
        s.closed(); s.open(100); s.closed()
        assertEquals(0, s.progress.repetitions)
        s.open(); s.closed()
        assertEquals(1, s.progress.repetitions)
    }

    @Test fun unstableClosedCannotArmTheCycle() {
        val s = Sequence(JumpingJackCounter(JumpingJackConfig(smoothingAlpha = 1.0)))
        s.closed(50); s.open(); s.closed()
        assertEquals(0, s.progress.repetitions)
        s.open(); s.closed()
        assertEquals(1, s.progress.repetitions)
    }

    @Test fun tooFastOpeningOrClosingDiscardsMovement() {
        for (fastReturn in listOf(false, true)) {
            val c = JumpingJackCounter(JumpingJackConfig(smoothingAlpha = 1.0))
            fun feed(t: Long, open: Boolean) = c.accept(pose(t, if (open) 160.0 else 10.0,
                if (open) 2.2 else 0.4), t)
            feed(0, false); feed(100, false)
            if (fastReturn) {
                feed(300, true); feed(400, true); feed(450, false); feed(550, false)
            } else {
                feed(150, true); feed(250, true); feed(500, false); feed(600, false)
            }
            assertEquals(0, feed(700, false).repetitions)
        }
    }

    @Test fun lossRetainsCountsButDiscardsPartialMovement() {
        val s = Sequence()
        s.cycle(); s.open(); s.missing()
        assertFalse(s.progress.trackingAvailable)
        assertEquals(1, s.progress.repetitions)
        s.closed()
        assertEquals(1, s.progress.repetitions)
        s.open(); s.closed()
        assertEquals(2, s.progress.repetitions)
    }

    @Test fun reappearanceOpenCannotCompleteOldMovement() {
        val s = Sequence()
        s.closed(); s.missing(); s.open(); s.closed()
        assertEquals(0, s.progress.repetitions)
    }

    @Test fun silentSampleGapDiscardsOpenPhase() {
        val s = Sequence()
        s.closed(); s.open(); s.time += 501; s.closed()
        assertEquals(0, s.progress.repetitions)
    }

    @Test fun prolongedOpenHoldExpiresAndNewCycleRecovers() {
        val s = Sequence()
        s.closed(); s.open(6000); s.closed()
        assertEquals(0, s.progress.repetitions)
        s.open(); s.closed()
        assertEquals(1, s.progress.repetitions)
    }

    @Test fun everyRequiredLandmarkNeedsConfidenceAndFiniteInFrameCoordinates() {
        for (index in listOf(11, 12, 15, 16, 23, 24, 27, 28)) for (kind in 0..7) {
            val p = pose(100)
            val invalid = p.copy(landmarks = p.landmarks.mapIndexed { i, l ->
                if (i != index) l else when (kind) {
                    0 -> l.copy(visibility = 0.49f)
                    1 -> l.copy(presence = 0.49f)
                    2 -> l.copy(x = 1.1f)
                    3 -> l.copy(y = Float.NaN)
                    4 -> l.copy(z = Float.POSITIVE_INFINITY)
                    5 -> l.copy(visibility = 1.1f)
                    6 -> l.copy(presence = Float.NaN)
                    else -> l.copy(x = Float.NEGATIVE_INFINITY)
                }
            })
            assertFalse("joint=$index kind=$kind", JumpingJackCounter().accept(invalid, 100).trackingAvailable)
        }
    }

    @Test fun confidenceLossForEachJointDiscardsPartialCycle() {
        for (index in listOf(11, 12, 15, 16, 23, 24, 27, 28)) {
            val s = Sequence()
            s.cycle(); s.open()
            s.hold(160.0, 2.2, 50, transform = { p -> p.copy(landmarks = p.landmarks.mapIndexed { i, l ->
                if (i == index) l.copy(presence = 0f) else l
            }) })
            assertFalse(s.progress.trackingAvailable)
            s.closed()
            assertEquals("joint=$index", 1, s.progress.repetitions)
        }
    }

    @Test fun malformedAspectLandmarkCountAndDegenerateGeometryAreRejected() {
        val p = pose(100)
        for (aspect in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertFalse(JumpingJackCounter().accept(p.copy(imageAspectRatio = aspect), 100).trackingAvailable)
        }
        assertFalse(JumpingJackCounter().accept(p.copy(landmarks = p.landmarks.take(32)), 100).trackingAvailable)
        for ((index, duplicate) in listOf(11 to 23, 15 to 11, 12 to 11, 24 to 23, 27 to 23)) {
            val invalid = p.copy(landmarks = p.landmarks.mapIndexed { i, l ->
                if (i == index) p.landmarks[duplicate] else l
            })
            assertFalse("joint=$index", JumpingJackCounter().accept(invalid, 100).trackingAvailable)
        }
    }

    @Test fun invertedTorsoAndCrossedFeetAreRejected() {
        val p = pose(100)
        assertFalse(JumpingJackCounter().accept(p.copy(landmarks = p.landmarks.map {
            it.copy(y = 1f - it.y)
        }), 100).trackingAvailable)
        assertFalse(JumpingJackCounter().accept(p.copy(landmarks = p.landmarks.mapIndexed { i, l ->
            when (i) { 27 -> p.landmarks[28]; 28 -> p.landmarks[27]; else -> l }
        }), 100).trackingAvailable)
    }

    @Test fun staleDuplicateAndNegativeTimestampsDoNotMutateCycle() {
        val s = Sequence()
        s.closed(); s.open()
        for (t in listOf(-1L, 1L, s.time)) assertFalse(s.counter.accept(pose(t), t).trackingAvailable)
        s.closed()
        assertEquals(1, s.progress.repetitions)
    }

    @Test fun mismatchedTimestampDiscardsPartialMovement() {
        val s = Sequence()
        s.closed(); s.open()
        assertFalse(s.counter.accept(pose(s.time), s.time + 50).trackingAvailable)
        s.time += 50; s.closed()
        assertEquals(0, s.progress.repetitions)
    }

    @Test fun resetClearsCountsSmoothingAndPartialMovement() {
        val s = Sequence()
        s.cycle(); s.open(); s.counter.reset(); s.time = 0; s.closed()
        assertEquals(0, s.progress.repetitions)
        s.open(); s.closed()
        assertEquals(1, s.progress.repetitions)
    }

    @Test fun configurableThresholdsChangeAcceptedMovement() {
        val s = Sequence(JumpingJackCounter(JumpingJackConfig(openArmAngleDegrees = 170.0, openFeetTorsoRatio = 2.4)))
        s.cycle()
        assertEquals(0, s.progress.repetitions)
        s.hold(175.0, 2.6); s.closed()
        assertEquals(1, s.progress.repetitions)
    }

    @Test fun invalidConfigurationIsRejected() {
        for (config in listOf(JumpingJackConfig(closedArmAngleDegrees = -1.0),
                JumpingJackConfig(openArmAngleDegrees = 30.0), JumpingJackConfig(openArmAngleDegrees = Double.NaN),
                JumpingJackConfig(closedFeetTorsoRatio = 0.0), JumpingJackConfig(openFeetTorsoRatio = 0.5),
                JumpingJackConfig(openFeetTorsoRatio = Double.POSITIVE_INFINITY),
                JumpingJackConfig(minOutwardFootTorsoRatio = 0.0), JumpingJackConfig(smoothingAlpha = 0.0),
                JumpingJackConfig(minConfidence = Float.NaN), JumpingJackConfig(stablePhaseMs = 0),
                JumpingJackConfig(minTransitionMs = 0), JumpingJackConfig(maxCycleMs = 100),
                JumpingJackConfig(minTransitionMs = Long.MAX_VALUE), JumpingJackConfig(maxSampleGapMs = 0))) {
            assertThrows(IllegalArgumentException::class.java) { JumpingJackCounter(config) }
        }
    }
}
