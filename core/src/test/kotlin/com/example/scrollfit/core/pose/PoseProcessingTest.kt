package com.example.scrollfit.core.pose

import org.junit.Assert.*
import org.junit.Test

class PoseProcessingTest {
    private fun pose(t: Long, x: Float = 0.2f) = PoseSample(t,
        List(33) { Landmark(x, 0.4f, -0.1f, 0.9f, 0.9f) })
    private fun filter() = PoseFilter(setOf(11, 12, 23, 24))

    @Test fun confidentLandmarksProduceTracking() {
        val result = filter().accept(pose(100), 100)
        assertEquals(TrackingState.TRACKING, result.tracking)
        assertEquals(33, result.sample!!.landmarks.size)
    }

    @Test fun lowRequiredConfidenceLosesTracking() {
        val f = filter()
        f.accept(pose(100), 100)
        val p = pose(120)
        val result = f.accept(p.copy(landmarks = p.landmarks.mapIndexed { i, l ->
            if (i == 11) l.copy(presence = 0.49f) else l
        }), 120)
        assertEquals(TrackingState.LOST, result.tracking)
        assertNull(result.sample)
    }

    @Test fun missingBodyBeginsSearching() {
        assertEquals(TrackingState.SEARCHING, filter().accept(null, 100).tracking)
    }

    @Test fun shortAndNonFiniteSamplesAreRejected() {
        val f = filter()
        val p = pose(100)
        assertNull(f.accept(p.copy(landmarks = p.landmarks.take(32)), 100).sample)
        assertNull(f.accept(pose(120).copy(landmarks = p.landmarks.map { it.copy(z = Float.NaN) }), 120).sample)
    }

    @Test fun outOfFrameRequiredLandmarkIsNotClampedIntoTracking() {
        val p = pose(100)
        assertNull(filter().accept(p.copy(landmarks = p.landmarks.map { it.copy(x = 1.1f) }), 100).sample)
    }

    @Test fun optionalLowConfidenceDoesNotHideValidBody() {
        val p = pose(100)
        assertNotNull(filter().accept(p.copy(landmarks = p.landmarks.mapIndexed { i, l ->
            if (i == 0) l.copy(visibility = 0f) else l
        }), 100).sample)
    }

    @Test fun smoothsGeometryButKeepsCurrentConfidence() {
        val f = filter()
        f.accept(pose(100, 0.2f), 100)
        val p = pose(120, 0.8f)
        val result = f.accept(p.copy(landmarks = p.landmarks.map { it.copy(visibility = 0.6f) }), 120)
        assertEquals(0.41f, result.sample!!.landmarks[11].x, 0.00001f)
        assertEquals(0.6f, result.sample.landmarks[11].visibility, 0f)
    }

    @Test fun trackingLossResetsSmoothingOnReappearance() {
        val f = filter()
        f.accept(pose(100, 0.2f), 100)
        f.accept(null, 120)
        assertEquals(0.8f, f.accept(pose(140, 0.8f), 140).sample!!.landmarks[11].x, 0f)
    }

    @Test fun longGapResetsSmoothingEvenWithConfidentBody() {
        val f = filter()
        f.accept(pose(100, 0.2f), 100)
        assertEquals(0.8f, f.accept(pose(601, 0.8f), 601).sample!!.landmarks[11].x, 0f)
    }

    @Test fun staleSampleCannotContaminateNextSample() {
        val f = filter()
        f.accept(pose(100, 0.2f), 100)
        assertNull(f.accept(pose(99, 0.9f), 99).sample)
        assertEquals(0.41f, f.accept(pose(120, 0.8f), 120).sample!!.landmarks[11].x, 0.00001f)
    }

    @Test fun resetStartsSearchingAgain() {
        val f = filter()
        f.accept(pose(100), 100)
        f.reset()
        assertEquals(TrackingState.SEARCHING, f.accept(null, 101).tracking)
    }

    @Test fun frameGateRejectsDuplicateOldFutureAndConcurrentFrames() {
        val gate = PoseSessionGate()
        val token = gate.start()
        assertFalse(gate.admit(token, 100, 351))
        assertFalse(gate.admit(token, 101, 100))
        assertTrue(gate.admit(token, 100, 100))
        assertFalse(gate.admit(token, 101, 101))
        gate.finish(token)
        assertFalse(gate.admit(token, 100, 102))
        assertTrue(gate.admit(token, 102, 102))
    }

    @Test fun stopInvalidatesInFlightResults() {
        val gate = PoseSessionGate()
        val token = gate.start()
        assertTrue(gate.admit(token, 100, 100))
        gate.stop()
        assertFalse(gate.isCurrent(token))
        assertFalse(gate.admit(token, 101, 101))
    }

    @Test fun priorSessionFinishCannotReleaseNewSessionFrame() {
        val gate = PoseSessionGate()
        val old = gate.start()
        gate.admit(old, 100, 100)
        val current = gate.start()
        assertTrue(gate.admit(current, 200, 200))
        gate.finish(old)
        assertFalse(gate.admit(current, 201, 201))
    }

    @Test fun fitCenterProjectionIncludesLetterboxAndFrontMirror() {
        val back = PreviewProjection.point(0.25f, 0.5f, 400, 200, 200f, 200f, false)
        val front = PreviewProjection.point(0.25f, 0.5f, 400, 200, 200f, 200f, true)
        assertEquals(50f, back.first, 0f)
        assertEquals(100f, back.second, 0f)
        assertEquals(150f, front.first, 0f)
        assertEquals(100f, front.second, 0f)
    }

    @Test fun portraitProjectionHasHorizontalLetterbox() {
        val p = PreviewProjection.point(0f, 0f, 200, 400, 200f, 200f, false)
        assertEquals(50f, p.first, 0f)
        assertEquals(0f, p.second, 0f)
    }

    @Test fun sideViewMayUseEitherCompleteArmAndHipGroup() {
        val f = PoseFilter(setOf(11, 13, 23), alternativeRequiredLandmarks = setOf(12, 14, 24))
        val p = pose(100)
        val rightOnly = p.copy(landmarks = p.landmarks.mapIndexed { i, l ->
            if (i in setOf(11, 13, 23)) l.copy(visibility = 0f) else l
        })
        assertEquals(TrackingState.TRACKING, f.accept(rightOnly, 100).tracking)
        val neither = pose(120).copy(landmarks = p.landmarks.mapIndexed { i, l ->
            if (i == 13 || i == 14) l.copy(visibility = 0f) else l
        })
        assertNull(f.accept(neither, 120).sample)
    }

    @Test fun delayedMainThreadCannotPublishFreshLookingStalePose() {
        val gate = PoseSessionGate()
        val token = gate.start()
        assertTrue(gate.admit(token, 100, 100))
        gate.finish(token)
        assertTrue(gate.isFresh(token, 100, 350))
        assertFalse(gate.isFresh(token, 100, 351))
        gate.stop()
        assertFalse(gate.isFresh(token, 100, 120))
    }
}
