package com.example.scrollfit.core.exercise

import com.example.scrollfit.core.pose.CountingProgress
import com.example.scrollfit.core.pose.Landmark
import com.example.scrollfit.core.pose.PoseSample
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.tan

class SquatCounterTest {
    // Feet stay planted, hips/shoulders descend together. Front-view knees may
    // remain collinear in 2D even while the real knees bend toward the camera.
    private fun pose(t: Long, height: Double = 2.0, angle: Double = 170.0,
                     aspect: Float = 1f, rightHeight: Double = height): PoseSample {
        val joints = MutableList(33) { Landmark(0.5f, 0.5f, 0f, 0f, 0f) }
        fun point(x: Double, y: Double) = Landmark(x.toFloat(), y.toFloat(), 0f, 0.95f, 0.95f)
        for (side in 0..1) {
            val x = if (side == 0) 0.35 else 0.65
            val span = 0.2 * if (side == 0) height else rightHeight
            val hipY = 0.9 - span
            joints[11 + side] = point(x, hipY - 0.2)
            joints[23 + side] = point(x, hipY)
            joints[25 + side] = point(x + span / (2 * tan(Math.toRadians(angle / 2))) / aspect,
                hipY + span / 2)
            joints[27 + side] = point(x, 0.9)
        }
        return PoseSample(t, joints, aspect)
    }

    private inner class Sequence(val counter: SquatCounter = SquatCounter(),
                                 val angle: Double = 170.0, val aspect: Float = 1f) {
        var time = 0L
        var progress = CountingProgress(0, false)
        fun hold(height: Double, durationMs: Long = 800, kneeAngle: Double = angle,
                 rightHeight: Double = height, transform: (PoseSample) -> PoseSample = { it }) {
            repeat((durationMs / 50).toInt()) {
                time += 50
                progress = counter.accept(transform(pose(time, height, kneeAngle, aspect, rightHeight)), time)
            }
        }
        fun cycle() { hold(2.0); hold(1.2); hold(2.0) }
        fun missing() { time += 50; progress = counter.accept(null, time) }
    }

    @Test fun fullCyclesCountOnceAndEndpointHoldsDoNotRepeat() {
        val s = Sequence()
        s.hold(2.0); s.hold(1.2, 2000)
        assertEquals(0, s.progress.repetitions)
        s.hold(2.0, 2000)
        assertEquals(1, s.progress.repetitions)
        s.hold(1.2); s.hold(2.0)
        assertEquals(2, s.progress.repetitions)
    }

    @Test fun frontViewCountsEvenWhenProjectedKneesRemainStraight() {
        val s = Sequence(angle = 180.0)
        s.cycle()
        assertEquals(1, s.progress.repetitions)
    }

    @Test fun slightAngleAndPortraitLandscapeSamplesCount() {
        for (aspect in listOf(0.75f, 1f, 4f / 3f)) {
            val s = Sequence(aspect = aspect)
            s.hold(2.0); s.hold(1.2, kneeAngle = 100.0); s.hold(2.0)
            assertEquals("aspect=$aspect", 1, s.progress.repetitions)
        }
    }

    @Test fun mirroringTranslationAndUniformScalePreserveCycle() {
        for (mirror in listOf(false, true)) {
            val s = Sequence()
            for (height in listOf(2.0, 1.2, 2.0)) s.hold(height, transform = { p ->
                p.copy(landmarks = p.landmarks.map { l ->
                    l.copy(x = 0.5f + (if (mirror) 0.5f - l.x else l.x - 0.5f) * 0.7f,
                        y = 0.4f + (l.y - 0.5f) * 0.7f)
                })
            })
            assertEquals(1, s.progress.repetitions)
        }
    }

    @Test fun cameraScaleChangeAloneDoesNotCount() {
        val s = Sequence()
        s.hold(2.0)
        for (scale in listOf(0.6f, 0.9f, 0.7f, 1f)) s.hold(2.0, transform = { p ->
            p.copy(landmarks = p.landmarks.map { l ->
                l.copy(x = 0.5f + (l.x - 0.5f) * scale, y = 0.5f + (l.y - 0.5f) * scale)
            })
        })
        assertEquals(0, s.progress.repetitions)
    }

    @Test fun gradualDescentAndReturnCountWithUnequalLegReferences() {
        val s = Sequence(angle = 180.0)
        s.hold(2.0, rightHeight = 1.8)
        for (height in listOf(1.9, 1.7, 1.5, 1.3, 1.2, 1.3, 1.5, 1.7, 1.9, 2.0)) {
            s.hold(height, 300, rightHeight = height * 0.9)
        }
        s.hold(2.0, rightHeight = 1.8)
        assertEquals(1, s.progress.repetitions)
    }

    @Test fun stableStandingCalibratesToDifferentBodyProportions() {
        for (standing in listOf(1.6, 2.4)) {
            val s = Sequence()
            s.hold(standing); s.hold(standing * 0.6); s.hold(standing)
            assertEquals("standing=$standing", 1, s.progress.repetitions)
        }
    }

    @Test fun configurableMovementThresholdChangesAcceptedDepth() {
        val s = Sequence(SquatCounter(SquatConfig(downHeightFraction = 0.6)))
        s.hold(2.0); s.hold(1.3); s.hold(2.0)
        assertEquals(0, s.progress.repetitions)
        s.hold(1.0); s.hold(2.0)
        assertEquals(1, s.progress.repetitions)
    }

    @Test fun isolatedStandingHeightOutlierCannotPoisonCalibration() {
        val s = Sequence()
        s.hold(2.0); s.hold(3.0, 50); s.hold(2.0)
        s.hold(1.2); s.hold(2.0)
        assertEquals(1, s.progress.repetitions)
    }

    @Test fun initialStandingReferenceRejectsAnIsolatedHeightOutlier() {
        val s = Sequence()
        s.hold(2.0, 150); s.hold(3.0, 50); s.hold(2.0)
        s.hold(1.2); s.hold(2.0)
        assertEquals(1, s.progress.repetitions)
    }

    @Test fun sparseInitialFramesCannotCalibrateFromOneHeightSpike() {
        val c = SquatCounter()
        c.accept(pose(0, 2.0), 0)
        c.accept(pose(200, 3.0), 200)
        val s = Sequence(c)
        s.time = 450 // Next normal sample at 500 ms: sparse but within the allowed gap.
        s.hold(2.0); s.hold(1.2); s.hold(2.0)
        assertEquals(1, s.progress.repetitions)
    }

    @Test fun startingLoweredRequiresStandingAndAnotherFullCycle() {
        val s = Sequence()
        s.hold(1.2); s.hold(2.0)
        assertEquals(0, s.progress.repetitions)
        s.hold(1.2); s.hold(2.0)
        assertEquals(1, s.progress.repetitions)
    }

    @Test fun bentKneeCannotAcquireStandingBaseline() {
        val s = Sequence()
        s.hold(2.0, kneeAngle = 120.0); s.hold(1.2); s.hold(2.0)
        assertEquals(0, s.progress.repetitions)
        s.hold(1.2); s.hold(2.0)
        assertEquals(1, s.progress.repetitions)
    }

    @Test fun partialLoweringAndIncompleteReturnDoNotCount() {
        val s = Sequence()
        s.hold(2.0); s.hold(1.7); s.hold(2.0)
        assertEquals(0, s.progress.repetitions)
        s.hold(1.2); s.hold(1.65, 2000)
        assertEquals(0, s.progress.repetitions)
    }

    @Test fun singleLegMovementCannotCompleteASquat() {
        val s = Sequence()
        s.hold(2.0); s.hold(1.2, rightHeight = 2.0); s.hold(2.0)
        assertEquals(0, s.progress.repetitions)
    }

    @Test fun returnWithBentKneesDoesNotCompleteUntilStanding() {
        val s = Sequence()
        s.hold(2.0); s.hold(1.2); s.hold(2.0, kneeAngle = 120.0)
        assertEquals(0, s.progress.repetitions)
        s.hold(2.0)
        assertEquals(1, s.progress.repetitions)
    }

    @Test fun jitterAndIsolatedDeepSpikeDoNotCount() {
        val s = Sequence()
        s.hold(2.0)
        repeat(20) { s.hold(1.48, 50); s.hold(1.65, 50) }
        s.hold(2.0); s.hold(1.2, 50); s.hold(2.0)
        assertEquals(0, s.progress.repetitions)
    }

    @Test fun smoothingRejectsBriefDipEvenWithShortDwell() {
        val s = Sequence(SquatCounter(SquatConfig(stablePhaseMs = 50, minTransitionMs = 50)))
        s.hold(2.0); s.hold(1.2, 100); s.hold(2.0)
        assertEquals(0, s.progress.repetitions)
        s.hold(1.2); s.hold(2.0)
        assertEquals(1, s.progress.repetitions)
    }

    @Test fun tooFastDescentAndReturnInvalidateCycle() {
        for (fastReturn in listOf(false, true)) {
            val c = SquatCounter(SquatConfig(smoothingAlpha = 1.0, stablePhaseMs = 100))
            fun feed(t: Long, height: Double) = c.accept(pose(t, height), t)
            feed(0, 2.0); feed(100, 2.0)
            if (fastReturn) {
                feed(350, 1.2); feed(450, 1.2); feed(500, 2.0); feed(600, 2.0)
            } else {
                feed(150, 1.2); feed(250, 1.2); feed(500, 2.0); feed(600, 2.0)
            }
            assertEquals(0, feed(700, 2.0).repetitions)
        }
    }

    @Test fun trackingLossRetainsCountsButDiscardsPartialMovement() {
        val s = Sequence()
        s.cycle(); s.hold(1.2); s.missing()
        assertFalse(s.progress.trackingAvailable)
        assertEquals(1, s.progress.repetitions)
        s.hold(2.0)
        assertEquals(1, s.progress.repetitions)
        s.hold(1.2); s.hold(2.0)
        assertEquals(2, s.progress.repetitions)
    }

    @Test fun reappearanceLoweredCannotFinishOldCycle() {
        val s = Sequence()
        s.hold(2.0); s.missing(); s.hold(1.2); s.hold(2.0)
        assertEquals(0, s.progress.repetitions)
    }

    @Test fun silentFrameGapDiscardsPartialCycle() {
        val s = Sequence()
        s.hold(2.0); s.hold(1.2); s.time += 501; s.hold(2.0)
        assertEquals(0, s.progress.repetitions)
    }

    @Test fun prolongedLoweredHoldExpiresAndRequiresNewCycle() {
        val s = Sequence()
        s.hold(2.0); s.hold(1.2, 11000); s.hold(2.0)
        assertEquals(0, s.progress.repetitions)
        s.hold(1.2); s.hold(2.0)
        assertEquals(1, s.progress.repetitions)
    }

    @Test fun everyRequiredJointNeedsConfidenceAndFiniteInFrameCoordinates() {
        for (index in listOf(11, 12, 23, 24, 25, 26, 27, 28)) for (kind in 0..5) {
            val p = pose(100)
            val invalid = p.copy(landmarks = p.landmarks.mapIndexed { i, l ->
                if (i != index) l else when (kind) {
                    0 -> l.copy(visibility = 0.49f)
                    1 -> l.copy(presence = 0.49f)
                    2 -> l.copy(x = 1.1f)
                    3 -> l.copy(y = Float.NaN)
                    4 -> l.copy(z = Float.POSITIVE_INFINITY)
                    else -> l.copy(visibility = 1.1f)
                }
            })
            assertFalse("joint=$index kind=$kind", SquatCounter().accept(invalid, 100).trackingAvailable)
        }
    }

    @Test fun lowConfidenceAndOneMissingLegDiscardPartialCycle() {
        for (index in listOf(23, 28)) {
            val s = Sequence()
            s.cycle(); s.hold(1.2)
            s.hold(1.2, 50, transform = { p -> p.copy(landmarks = p.landmarks.mapIndexed { i, l ->
                if (i == index) l.copy(presence = 0f) else l
            }) })
            assertFalse(s.progress.trackingAvailable)
            s.hold(2.0)
            assertEquals(1, s.progress.repetitions)
        }
    }

    @Test fun malformedAspectAndDegenerateTorsoOrLegsAreRejected() {
        val p = pose(100)
        for (aspect in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertFalse(SquatCounter().accept(p.copy(imageAspectRatio = aspect), 100).trackingAvailable)
        }
        assertFalse(SquatCounter().accept(p.copy(landmarks = p.landmarks.take(32)), 100).trackingAvailable)
        for ((index, duplicate) in listOf(11 to 23, 25 to 23, 27 to 25)) {
            val invalid = p.copy(landmarks = p.landmarks.mapIndexed { i, l ->
                if (i == index) p.landmarks[duplicate] else l
            })
            assertFalse(SquatCounter().accept(invalid, 100).trackingAvailable)
        }
    }

    @Test fun invertedBodyCannotAcquireBaseline() {
        val p = pose(100)
        val inverted = p.copy(landmarks = p.landmarks.map { it.copy(y = 1f - it.y) })
        assertFalse(SquatCounter().accept(inverted, 100).trackingAvailable)
    }

    @Test fun staleDuplicateAndNegativeTimestampsDoNotAlterCycle() {
        val s = Sequence()
        s.hold(2.0); s.hold(1.2)
        for (t in listOf(-1L, 1L, s.time)) {
            assertFalse(s.counter.accept(pose(t), t).trackingAvailable)
        }
        s.hold(2.0)
        assertEquals(1, s.progress.repetitions)
    }

    @Test fun mismatchedTimestampDiscardsPartialMovement() {
        val s = Sequence()
        s.hold(2.0); s.hold(1.2)
        assertFalse(s.counter.accept(pose(s.time), s.time + 50).trackingAvailable)
        s.time += 50; s.hold(2.0)
        assertEquals(0, s.progress.repetitions)
    }

    @Test fun explicitResetClearsCountsBaselineAndPartialMovement() {
        val s = Sequence()
        s.cycle(); s.hold(1.2); s.counter.reset(); s.time = 0; s.hold(2.0)
        assertEquals(0, s.progress.repetitions)
        s.hold(1.2); s.hold(2.0)
        assertEquals(1, s.progress.repetitions)
    }

    @Test fun configurationRejectsInvalidThresholdsAndTiming() {
        for (config in listOf(SquatConfig(downHeightFraction = 0.95),
                SquatConfig(upHeightFraction = 1.1), SquatConfig(minStandingHeightRatio = 0.0),
                SquatConfig(upAngleDegrees = Double.NaN), SquatConfig(smoothingAlpha = 0.0),
                SquatConfig(minConfidence = Float.NaN), SquatConfig(stablePhaseMs = 0),
                SquatConfig(minTransitionMs = 0), SquatConfig(maxCycleMs = 100),
                SquatConfig(maxSampleGapMs = 0))) {
            assertThrows(IllegalArgumentException::class.java) { SquatCounter(config) }
        }
    }
}
