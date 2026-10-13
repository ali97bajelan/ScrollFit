package com.example.scrollfit.core.exercise

import com.example.scrollfit.core.pose.Landmark
import com.example.scrollfit.core.pose.PoseSample
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class PushUpCounterTest {
    // Construct a projected side view with the requested elbow angle in pixel space.
    private fun pose(t: Long, angle: Double = 170.0, side: Int = 0, aspect: Float = 1f): PoseSample {
        val joints = MutableList(33) { Landmark(0.5f, 0.5f, 0f, 0f, 0f) }
        fun point(x: Float, y: Float) = Landmark(x, y, 0f, 0.95f, 0.95f)
        joints[11 + side] = point(0.5f, 0.3f)
        joints[13 + side] = point(0.5f, 0.5f)
        val radians = Math.toRadians(angle)
        joints[15 + side] = point(0.5f + (0.2 * sin(radians) / aspect).toFloat(),
            0.5f - (0.2 * cos(radians)).toFloat())
        joints[23 + side] = point(0.2f, 0.35f)
        return PoseSample(t, joints, aspect)
    }

    private class Sequence(val counter: PushUpCounter = PushUpCounter(), val side: Int = 0,
                           val aspect: Float = 1f) {
        var time = 0L
        var count = 0
        fun hold(angle: Double, durationMs: Long = 700) {
            repeat((durationMs / 50).toInt()) {
                time += 50
                count = counter.accept(PushUpCounterTest().pose(time, angle, side, aspect), time).repetitions
            }
        }
        fun cycle() { hold(170.0); hold(80.0); hold(170.0) }
        fun missing() { time += 50; count = counter.accept(null, time).repetitions }
    }

    @Test fun completeCycleCountsOnceAndHoldingUpDoesNotRepeat() {
        val s = Sequence()
        s.cycle()
        assertEquals(1, s.count)
        s.hold(170.0, 2000)
        assertEquals(1, s.count)
        s.hold(80.0); s.hold(170.0)
        assertEquals(2, s.count)
    }

    @Test fun rightSideAndPortraitLandscapeAspectRatiosCount() {
        for (side in 0..1) for (aspect in listOf(0.75f, 1f, 4f / 3f)) {
            val s = Sequence(side = side, aspect = aspect)
            s.cycle()
            assertEquals("side=$side aspect=$aspect", 1, s.count)
        }
    }

    @Test fun startingDownRequiresAFreshFullCycle() {
        val s = Sequence()
        s.hold(80.0); s.hold(170.0)
        assertEquals(0, s.count)
        s.hold(80.0); s.hold(170.0)
        assertEquals(1, s.count)
    }

    @Test fun partialAndNeutralMovementDoesNotCount() {
        val s = Sequence()
        s.hold(170.0); s.hold(125.0); s.hold(170.0)
        assertEquals(0, s.count)
        s.hold(80.0); s.hold(130.0, 2000)
        assertEquals(0, s.count)
    }

    @Test fun jitterAndIsolatedSpikesDoNotCount() {
        val s = Sequence()
        s.hold(170.0)
        repeat(20) { s.hold(150.0, 50); s.hold(160.0, 50) }
        s.hold(80.0, 50); s.hold(170.0)
        assertEquals(0, s.count)
        repeat(20) { s.hold(100.0, 50); s.hold(115.0, 50) }
        s.hold(170.0)
        assertEquals(0, s.count)
    }

    @Test fun implausiblyFastDownTransitionInvalidatesCycle() {
        val c = PushUpCounter(PushUpConfig(smoothingAlpha = 1.0))
        for (t in listOf(0L, 100L)) c.accept(pose(t), t)
        c.accept(pose(150, 80.0), 150)
        c.accept(pose(250, 80.0), 250)
        assertEquals(0, c.accept(pose(600), 600).repetitions)
        assertEquals(0, c.accept(pose(700), 700).repetitions)
        val s = Sequence(c)
        s.time = 700
        s.cycle()
        assertEquals(1, s.count)
    }

    @Test fun implausiblyFastUpTransitionDoesNotEarnCount() {
        val c = PushUpCounter(PushUpConfig(smoothingAlpha = 1.0))
        for (t in listOf(0L, 100L)) c.accept(pose(t), t)
        for (t in listOf(350L, 450L)) c.accept(pose(t, 80.0), t)
        c.accept(pose(500), 500)
        assertEquals(0, c.accept(pose(600), 600).repetitions)
        assertEquals(0, c.accept(pose(1000), 1000).repetitions)
    }

    @Test fun trackingLossDiscardsPartialButRetainsCompletedRepetitions() {
        val s = Sequence()
        s.cycle(); s.hold(80.0); s.missing(); s.hold(170.0)
        assertEquals(1, s.count)
        s.hold(80.0); s.hold(170.0)
        assertEquals(2, s.count)
    }

    @Test fun lossAndReappearanceDownCannotFinishOldCycle() {
        val s = Sequence()
        s.hold(170.0); s.missing(); s.hold(80.0); s.hold(170.0)
        assertEquals(0, s.count)
    }

    @Test fun frameGapDiscardsPartialEvenWithoutMissingCallback() {
        val s = Sequence()
        s.hold(170.0); s.hold(80.0); s.time += 501; s.hold(170.0)
        assertEquals(0, s.count)
    }

    @Test fun prolongedBottomHoldExpiresIncompleteCycle() {
        val s = Sequence()
        s.hold(170.0); s.hold(80.0, 11000); s.hold(170.0)
        assertEquals(0, s.count)
        s.hold(80.0); s.hold(170.0)
        assertEquals(1, s.count)
    }

    @Test fun changingVisibleSideDiscardsPartial() {
        val c = PushUpCounter()
        val s = Sequence(c)
        s.hold(170.0); s.hold(80.0)
        val right = Sequence(c, side = 1)
        right.time = s.time
        right.hold(170.0)
        assertEquals(0, right.count)
        right.hold(80.0); right.hold(170.0)
        assertEquals(1, right.count)
    }

    @Test fun bothVisibleSidesKeepSelectedSideUntilItIsLost() {
        val c = PushUpCounter()
        fun both(t: Long, left: Double, right: Double): PoseSample {
            val l = pose(t, left)
            val r = pose(t, right, 1)
            return l.copy(landmarks = l.landmarks.mapIndexed { i, p ->
                if (i in setOf(12, 14, 16, 24)) r.landmarks[i] else p
            })
        }
        var t = 0L
        var count = 0
        for (angle in listOf(170.0, 80.0, 170.0)) repeat(14) {
            t += 50
            count = c.accept(both(t, angle, 170.0), t).repetitions
        }
        assertEquals(1, count)
    }

    @Test fun requiredVisibilityPresenceAndInFrameChecksPauseCounting() {
        for (index in listOf(11, 13, 15, 23)) for (kind in 0..3) {
            val c = PushUpCounter()
            val p = pose(100)
            val changed = p.copy(landmarks = p.landmarks.mapIndexed { i, l ->
                if (i != index) l else when (kind) {
                    0 -> l.copy(visibility = 0.49f)
                    1 -> l.copy(presence = 0.49f)
                    2 -> l.copy(x = 1.1f)
                    else -> l.copy(y = Float.NaN)
                }
            })
            assertFalse("joint=$index kind=$kind", c.accept(changed, 100).trackingAvailable)
        }
    }

    @Test fun lowConfidenceAtBottomDiscardsCycleOnReappearance() {
        val s = Sequence()
        s.cycle(); s.hold(80.0)
        s.time += 50
        val p = pose(s.time, 80.0)
        val hiddenWrist = p.copy(landmarks = p.landmarks.mapIndexed { i, l ->
            if (i == 15) l.copy(visibility = 0.1f) else l
        })
        assertFalse(s.counter.accept(hiddenWrist, s.time).trackingAvailable)
        s.hold(170.0)
        assertEquals(1, s.count)
    }

    @Test fun incompleteSidesCannotCombineIntoOneTrackedArm() {
        val left = pose(100)
        val right = pose(100, side = 1)
        val mixed = left.copy(landmarks = left.landmarks.mapIndexed { i, l ->
            when (i) {
                15 -> l.copy(presence = 0f)
                16 -> right.landmarks[i]
                else -> l
            }
        })
        assertFalse(PushUpCounter().accept(mixed, 100).trackingAvailable)
    }

    @Test fun angleSmoothingRejectsBriefDeepDipEvenWithShortTimingThreshold() {
        val s = Sequence(PushUpCounter(PushUpConfig(minTransitionMs = 50)))
        s.hold(170.0); s.hold(80.0, 150); s.hold(170.0)
        assertEquals(0, s.count)
        s.hold(80.0); s.hold(170.0)
        assertEquals(1, s.count)
    }

    @Test fun shortDegenerateAndInvalidAspectSamplesAreRejected() {
        val p = pose(100)
        assertFalse(PushUpCounter().accept(p.copy(landmarks = p.landmarks.take(32)), 100).trackingAvailable)
        assertFalse(PushUpCounter().accept(p.copy(imageAspectRatio = 0f), 100).trackingAvailable)
        assertFalse(PushUpCounter().accept(p.copy(imageAspectRatio = Float.NaN), 100).trackingAvailable)
        val degenerate = p.copy(landmarks = p.landmarks.mapIndexed { i, l ->
            if (i == 11) p.landmarks[13] else l
        })
        assertFalse(PushUpCounter().accept(degenerate, 100).trackingAvailable)
    }

    @Test fun staleDuplicateAndMismatchedSamplesCannotCreateRepetitions() {
        val s = Sequence()
        s.hold(170.0); s.hold(80.0)
        assertFalse(s.counter.accept(pose(1), 1).trackingAvailable)
        assertFalse(s.counter.accept(pose(s.time), s.time).trackingAvailable)
        s.hold(170.0)
        assertEquals(1, s.count)
        s.hold(80.0)
        assertFalse(s.counter.accept(pose(s.time), s.time + 50).trackingAvailable)
        s.time += 50
        s.hold(170.0)
        assertEquals(1, s.count)
    }

    @Test fun explicitResetStartsNewSessionWithoutPriorCountOrPartial() {
        val s = Sequence()
        s.cycle(); s.hold(80.0); s.counter.reset(); s.hold(170.0)
        assertEquals(0, s.count)
        s.hold(80.0); s.hold(170.0)
        assertEquals(1, s.count)
    }

    @Test fun invalidConfigurationIsRejected() {
        for (config in listOf(PushUpConfig(upAngleDegrees = 90.0),
                PushUpConfig(smoothingAlpha = 0.0), PushUpConfig(minTransitionMs = 0),
                PushUpConfig(maxSampleGapMs = 0), PushUpConfig(minConfidence = Float.NaN))) {
            assertThrows(IllegalArgumentException::class.java) { PushUpCounter(config) }
        }
    }
}
