package com.example.scrollfit.core.exercise

import com.example.scrollfit.core.pose.CountingProgress
import com.example.scrollfit.core.pose.Landmark
import com.example.scrollfit.core.pose.PoseSample
import org.junit.Assert.*
import org.junit.Test

class AlternatingKneeRaiseCounterTest {
    private val required = listOf(11, 12, 23, 24, 25, 26, 27, 28)

    // Hip-knee drop / torso: 1.0 standing, 0.15 lifted, 0.6 partial.
    private fun pose(t: Long, left: Double = 1.0, right: Double = 1.0, aspect: Float = 1f): PoseSample {
        val joints = MutableList(33) { Landmark(0.5f, 0.5f, 0f, 0f, 0f) }
        fun point(x: Double, y: Double) = Landmark((0.5 + (x - 0.5) / aspect).toFloat(),
            y.toFloat(), 0f, 0.95f, 0.95f)
        for (side in 0..1) {
            val x = if (side == 0) 0.44 else 0.56
            val drop = if (side == 0) left else right
            joints[11 + side] = point(x, 0.25)
            joints[23 + side] = point(x, 0.45)
            joints[25 + side] = point(x + if (drop < 0.8) 0.04 else 0.0, 0.45 + 0.2 * drop)
            joints[27 + side] = point(x, if (drop < 0.8) 0.73 else 0.88)
        }
        return PoseSample(t, joints, aspect)
    }

    private inner class Sequence(val counter: AlternatingKneeRaiseCounter = AlternatingKneeRaiseCounter(),
                                 val aspect: Float = 1f) {
        var time = 0L
        var progress = CountingProgress(0, false)
        fun hold(left: Double = 1.0, right: Double = 1.0, duration: Long = 700,
                 transform: (PoseSample) -> PoseSample = { it }) {
            repeat((duration / 50).toInt()) {
                time += 50
                progress = counter.accept(transform(pose(time, left, right, aspect)), time)
            }
        }
        fun stand(duration: Long = 700) = hold(duration = duration)
        fun lift(left: Boolean, duration: Long = 700) =
            hold(if (left) 0.15 else 1.0, if (left) 1.0 else 0.15, duration)
        fun cycle(left: Boolean = true) { stand(); lift(left); stand() }
        fun missing() { time += 50; progress = counter.accept(null, time) }
    }

    @Test fun eachSideCountsOneLiftAndReturn() {
        for (left in listOf(true, false)) {
            val s = Sequence()
            s.stand(); s.lift(left)
            assertEquals(0, s.progress.repetitions)
            s.stand()
            assertEquals(1, s.progress.repetitions)
        }
    }

    @Test fun alternatingLeftRightSequencesCountEachReturn() {
        val s = Sequence()
        s.stand()
        for ((i, left) in listOf(true, false, true, false).withIndex()) {
            s.lift(left); assertEquals(i, s.progress.repetitions)
            s.stand(); assertEquals(i + 1, s.progress.repetitions)
        }
    }

    @Test fun sustainedLiftAndStandingDoNotRepeatCounts() {
        val s = Sequence()
        s.stand(); s.lift(true, 3000)
        assertEquals(0, s.progress.repetitions)
        s.stand(3000)
        assertEquals(1, s.progress.repetitions)
    }

    @Test fun sameSideFullCyclesStillCount() {
        val s = Sequence()
        repeat(3) { s.cycle(false) }
        assertEquals(3, s.progress.repetitions)
    }

    @Test fun startingRaisedMustStandAndStartFresh() {
        for (left in listOf(true, false)) {
            val s = Sequence()
            s.lift(left); s.stand()
            assertEquals(0, s.progress.repetitions)
            s.lift(left); s.stand()
            assertEquals(1, s.progress.repetitions)
        }
    }

    @Test fun partialLiftsOnEitherSideDoNotCount() {
        for (left in listOf(true, false)) {
            val s = Sequence()
            repeat(3) { s.stand(); s.hold(if (left) 0.6 else 1.0, if (left) 1.0 else 0.6); s.stand() }
            assertEquals(0, s.progress.repetitions)
        }
    }

    @Test fun incompleteReturnCannotCountUntilStableStanding() {
        val s = Sequence()
        s.stand(); s.lift(true); s.hold(0.6)
        assertEquals(0, s.progress.repetitions)
        s.stand()
        assertEquals(1, s.progress.repetitions)
    }

    @Test fun sideSwitchWithoutStandingDiscardsOldCycle() {
        for (left in listOf(true, false)) {
            val s = Sequence()
            s.stand(); s.lift(left); s.lift(!left); s.stand()
            assertEquals(0, s.progress.repetitions)
            s.lift(!left); s.stand()
            assertEquals(1, s.progress.repetitions)
        }
    }

    @Test fun bothKneesRaisedAndBilateralLoweringCannotCount() {
        for (drop in listOf(0.15, 0.6)) {
            val s = Sequence()
            s.stand(); s.hold(drop, drop); s.stand()
            assertEquals(0, s.progress.repetitions)
        }
        val s = Sequence()
        s.stand(); s.lift(true); s.hold(0.15, 0.15); s.stand()
        assertEquals(0, s.progress.repetitions)
    }

    @Test fun supportingLegMustRemainStanding() {
        val s = Sequence()
        s.stand(); s.hold(0.15, 0.6); s.stand()
        assertEquals(0, s.progress.repetitions)
    }

    @Test fun bentSupportingKneeCannotQualifyAsStanding() {
        val s = Sequence()
        s.stand()
        s.hold(0.15, transform = { p -> p.copy(landmarks = p.landmarks.mapIndexed { i, l ->
            if (i == 26) l.copy(x = 0.8f) else l
        }) })
        s.stand()
        assertEquals(0, s.progress.repetitions)
    }

    @Test fun gradualAlternatingMovementCounts() {
        val s = Sequence()
        s.stand()
        for (left in listOf(true, false)) {
            for (drop in listOf(0.8, 0.6, 0.4, 0.15, 0.4, 0.6, 0.8, 1.0)) {
                s.hold(if (left) drop else 1.0, if (left) 1.0 else drop, 350)
            }
            s.stand()
        }
        assertEquals(2, s.progress.repetitions)
    }

    @Test fun jitterAndOneFrameLiftDoNotCount() {
        val s = Sequence()
        s.stand()
        repeat(20) { s.hold(0.34, duration = 50); s.hold(0.36, duration = 50) }
        s.stand(); s.lift(true, 50); s.stand()
        assertEquals(0, s.progress.repetitions)
    }

    @Test fun noisyReturnDoesNotCompleteRaisedCycle() {
        val s = Sequence()
        s.stand(); s.lift(true)
        repeat(20) { s.hold(0.79, duration = 50); s.hold(0.81, duration = 50) }
        assertEquals(0, s.progress.repetitions)
        s.stand(); assertEquals(1, s.progress.repetitions)
    }

    @Test fun rawPartialReturnCannotBorrowStandingFromEma() {
        val s = Sequence(AlternatingKneeRaiseCounter(KneeRaiseConfig(smoothingAlpha = 0.05, stablePhaseMs = 150)))
        // Almost finish standing dwell, then move just below the raw return
        // threshold while the EMA still says standing. That tail must not count.
        s.stand(4000); s.lift(true, 3000); s.stand(1500); s.hold(0.79, duration = 1000)
        assertEquals(0, s.progress.repetitions)
    }

    @Test fun unstableInitialStandingDoesNotArm() {
        val s = Sequence(AlternatingKneeRaiseCounter(KneeRaiseConfig(smoothingAlpha = 1.0)))
        s.stand(50); s.lift(true); s.stand()
        assertEquals(0, s.progress.repetitions)
    }

    @Test fun thirtyFpsAlternatingCyclesWithShortHoldsCount() {
        val c = AlternatingKneeRaiseCounter()
        var t = 0L
        fun hold(left: Double, right: Double): CountingProgress {
            var p = CountingProgress(0, false)
            repeat(12) { t += 33; p = c.accept(pose(t, left, right), t) }
            return p
        }
        hold(1.0, 1.0)
        repeat(4) { i ->
            hold(if (i % 2 == 0) 0.15 else 1.0, if (i % 2 == 0) 1.0 else 0.15)
            assertEquals(i + 1, hold(1.0, 1.0).repetitions)
        }
    }

    @Test fun fastLiftOrReturnDiscardsMovement() {
        for (fastReturn in listOf(true, false)) {
            val c = AlternatingKneeRaiseCounter(KneeRaiseConfig(smoothingAlpha = 1.0))
            fun feed(t: Long, raised: Boolean) = c.accept(pose(t, if (raised) 0.15 else 1.0), t)
            feed(0, false); feed(100, false)
            if (fastReturn) {
                feed(300, true); feed(400, true); feed(450, false); feed(550, false)
            } else {
                feed(150, true); feed(250, true); feed(500, false); feed(600, false)
            }
            assertEquals(0, feed(700, false).repetitions)
        }
    }

    @Test fun prolongedRaiseExpiresAndFreshCycleRecovers() {
        val s = Sequence()
        s.stand(); s.lift(true, 6000); s.stand()
        assertEquals(0, s.progress.repetitions)
        s.lift(false); s.stand(); assertEquals(1, s.progress.repetitions)
    }

    @Test fun lossRetainsCountsAndDiscardsEitherPartialSide() {
        for (left in listOf(true, false)) {
            val s = Sequence()
            s.cycle(); s.lift(left); s.missing()
            assertFalse(s.progress.trackingAvailable)
            assertEquals(1, s.progress.repetitions)
            s.stand(); assertEquals(1, s.progress.repetitions)
            s.lift(!left); s.stand(); assertEquals(2, s.progress.repetitions)
        }
    }

    @Test fun reappearanceRaisedCannotCompleteOldCycle() {
        val s = Sequence()
        s.stand(); s.missing(); s.lift(false); s.stand()
        assertEquals(0, s.progress.repetitions)
    }

    @Test fun silentGapClearsLift() {
        val s = Sequence()
        s.stand(); s.lift(true); s.time += 501; s.stand()
        assertEquals(0, s.progress.repetitions)
    }

    @Test fun eachRequiredJointNeedsConfidenceAndValidCoordinates() {
        for (index in required) for (kind in 0..7) {
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
            assertFalse("joint=$index kind=$kind", AlternatingKneeRaiseCounter().accept(invalid, 100).trackingAvailable)
        }
    }

    @Test fun confidenceLossAtEveryRequiredJointClearsLift() {
        for (index in required) {
            val s = Sequence()
            s.cycle(); s.lift(false)
            s.hold(1.0, 0.15, 50, transform = { p -> p.copy(landmarks = p.landmarks.mapIndexed { i, l ->
                if (i == index) l.copy(presence = 0f) else l
            }) })
            assertFalse(s.progress.trackingAvailable)
            s.stand(); assertEquals("joint=$index", 1, s.progress.repetitions)
        }
    }

    @Test fun malformedSampleAndDegenerateGeometryAreRejected() {
        val p = pose(100)
        for (aspect in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertFalse(AlternatingKneeRaiseCounter().accept(p.copy(imageAspectRatio = aspect), 100).trackingAvailable)
        }
        assertFalse(AlternatingKneeRaiseCounter().accept(p.copy(landmarks = p.landmarks.take(32)), 100).trackingAvailable)
        for ((index, duplicate) in listOf(11 to 23, 12 to 11, 24 to 23, 25 to 23, 27 to 25)) {
            assertFalse("joint=$index", AlternatingKneeRaiseCounter().accept(p.copy(landmarks = p.landmarks.mapIndexed { i, l ->
                if (i == index) p.landmarks[duplicate] else l
            }), 100).trackingAvailable)
        }
        assertFalse(AlternatingKneeRaiseCounter().accept(p.copy(landmarks = p.landmarks.map {
            it.copy(y = 1f - it.y)
        }), 100).trackingAvailable)
    }

    @Test fun mismatchedTimestampClearsPartialCycle() {
        val s = Sequence()
        s.stand(); s.lift(true)
        assertFalse(s.counter.accept(pose(s.time), s.time + 50).trackingAvailable)
        s.time += 50; s.stand(); assertEquals(0, s.progress.repetitions)
    }

    @Test fun staleDuplicateNegativeTimestampsCannotMutateState() {
        val s = Sequence()
        s.stand(); s.lift(true)
        for (t in listOf(-1L, 1L, s.time)) assertFalse(s.counter.accept(pose(t), t).trackingAvailable)
        s.stand(); assertEquals(1, s.progress.repetitions)
    }

    @Test fun resetClearsCountsPartialStateAndTimestampHistory() {
        val s = Sequence()
        s.cycle(); s.lift(false); s.counter.reset(); s.time = 0; s.stand()
        assertEquals(0, s.progress.repetitions)
        s.lift(false); s.stand(); assertEquals(1, s.progress.repetitions)
    }

    @Test fun aspectsMirroringTranslationAndScalePreserveCounts() {
        for (aspect in listOf(0.75f, 1f, 4f / 3f)) for (mirror in listOf(false, true)) {
            val s = Sequence(aspect = aspect)
            for ((left, right) in listOf(1.0 to 1.0, 0.15 to 1.0, 1.0 to 1.0, 1.0 to 0.15, 1.0 to 1.0)) {
                s.hold(left, right, transform = { p -> p.copy(landmarks = p.landmarks.map { l ->
                    l.copy(x = 0.45f + (l.x - 0.5f) * (if (mirror) -0.7f else 0.7f),
                        y = 0.4f + (l.y - 0.5f) * 0.7f)
                }) })
            }
            assertEquals("aspect=$aspect mirror=$mirror", 2, s.progress.repetitions)
        }
    }

    @Test fun scaleChangesAloneDoNotCount() {
        val s = Sequence()
        for (scale in listOf(1f, 0.6f, 0.9f, 0.7f, 1f)) s.hold(transform = { p ->
            p.copy(landmarks = p.landmarks.map { l ->
                l.copy(x = 0.5f + (l.x - 0.5f) * scale, y = 0.5f + (l.y - 0.5f) * scale)
            })
        })
        assertEquals(0, s.progress.repetitions)
    }

    @Test fun customThresholdsChangeAcceptedLift() {
        val s = Sequence(AlternatingKneeRaiseCounter(KneeRaiseConfig(raisedKneeTorsoRatio = 0.1)))
        s.cycle(); assertEquals(0, s.progress.repetitions)
        s.hold(0.05); s.stand(); assertEquals(1, s.progress.repetitions)
    }

    @Test fun invalidConfigurationIsRejected() {
        for (config in listOf(KneeRaiseConfig(raisedKneeTorsoRatio = -1.0),
            KneeRaiseConfig(raisedKneeTorsoRatio = Double.NaN), KneeRaiseConfig(standingKneeTorsoRatio = 0.35),
            KneeRaiseConfig(standingKneeTorsoRatio = Double.POSITIVE_INFINITY),
            KneeRaiseConfig(standingKneeAngleDegrees = 90.0), KneeRaiseConfig(standingKneeAngleDegrees = Double.NaN),
            KneeRaiseConfig(smoothingAlpha = 0.0), KneeRaiseConfig(minConfidence = Float.NaN),
            KneeRaiseConfig(stablePhaseMs = 0), KneeRaiseConfig(minTransitionMs = Long.MAX_VALUE),
            KneeRaiseConfig(maxRaisedMs = 100), KneeRaiseConfig(maxSampleGapMs = 0))) {
            assertThrows(IllegalArgumentException::class.java) { AlternatingKneeRaiseCounter(config) }
        }
    }
}
