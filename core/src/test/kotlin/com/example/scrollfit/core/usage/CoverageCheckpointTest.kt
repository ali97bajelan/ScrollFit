package com.example.scrollfit.core.usage

import org.junit.Assert.*
import org.junit.Test

class CoverageCheckpointTest {
    @Test fun idleCheckpointUsesLastProcessedObservation() {
        assertEquals(60_000L, CoverageCheckpoint.nextDelayMs(0, 0, true))
        assertEquals(1L, CoverageCheckpoint.nextDelayMs(59_999, 0, true))
        assertEquals(0L, CoverageCheckpoint.nextDelayMs(60_000, 0, true))
        assertEquals(0L, CoverageCheckpoint.nextDelayMs(60_001, 0, true))
        assertNull(CoverageCheckpoint.nextDelayMs(60_001, 0, false))
        assertNull(CoverageCheckpoint.nextDelayMs(60_001, null, true))
    }
}
