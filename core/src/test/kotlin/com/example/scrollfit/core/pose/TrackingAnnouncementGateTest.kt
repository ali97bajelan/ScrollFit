package com.example.scrollfit.core.pose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackingAnnouncementGateTest {
    @Test fun lossAndRecoveryRequireOneSecondOfStableStatus() {
        val gate = TrackingAnnouncementGate()
        assertNull(gate.update(WorkoutTracking.LOST, 0))
        assertNull(gate.update(WorkoutTracking.LOST, 999))
        assertEquals(WorkoutTracking.LOST, gate.update(WorkoutTracking.LOST, 1_000))
        assertNull(gate.update(WorkoutTracking.LOST, 2_000))
        assertNull(gate.update(WorkoutTracking.TRACKING, 2_100))
        assertNull(gate.update(WorkoutTracking.TRACKING, 3_099))
        assertEquals(WorkoutTracking.TRACKING, gate.update(WorkoutTracking.TRACKING, 3_100))
        assertNull(gate.update(WorkoutTracking.TRACKING, 5_000))
    }

    @Test fun briefFlapsCancelPendingAnnouncementAndResetStartsFresh() {
        val gate = TrackingAnnouncementGate()
        assertNull(gate.update(WorkoutTracking.LOST, 0))
        assertNull(gate.update(WorkoutTracking.TRACKING, 500))
        assertNull(gate.update(WorkoutTracking.LOST, 900))
        assertNull(gate.update(WorkoutTracking.LOST, 1_899))
        assertEquals(WorkoutTracking.LOST, gate.update(WorkoutTracking.LOST, 1_900))

        gate.reset()
        assertNull(gate.update(WorkoutTracking.TRACKING, 10_000))
        assertEquals(WorkoutTracking.TRACKING, gate.update(WorkoutTracking.TRACKING, 11_000))
    }

    @Test fun errorsAnnounceImmediatelyAndRepeatedErrorsAreDeduplicated() {
        val gate = TrackingAnnouncementGate()
        assertEquals(WorkoutTracking.ERROR, gate.update(WorkoutTracking.ERROR, 0))
        assertNull(gate.update(WorkoutTracking.ERROR, 1))
        assertNull(gate.update(WorkoutTracking.TRACKING, 2))
        assertEquals(WorkoutTracking.TRACKING, gate.update(WorkoutTracking.TRACKING, 1_002))
        assertEquals(WorkoutTracking.ERROR, gate.update(WorkoutTracking.ERROR, 1_003))
    }

    @Test fun initializingCancelsPendingStableTransition() {
        val gate = TrackingAnnouncementGate()
        assertNull(gate.update(WorkoutTracking.LOST, 0))
        assertNull(gate.update(WorkoutTracking.INITIALIZING, 500))
        assertNull(gate.update(WorkoutTracking.LOST, 900))
        assertEquals(WorkoutTracking.LOST, gate.update(WorkoutTracking.LOST, 1_900))
    }

    @Test fun searchingAndLostShareOneStableAnnouncementCategory() {
        val gate = TrackingAnnouncementGate()
        assertNull(gate.update(WorkoutTracking.SEARCHING, 0))
        assertNull(gate.update(WorkoutTracking.LOST, 999))
        assertEquals(WorkoutTracking.LOST, gate.update(WorkoutTracking.SEARCHING, 1_000))
        assertNull(gate.update(WorkoutTracking.LOST, 1_001))
    }
}
