package com.example.scrollfit.core.pose

/** Tracks breaks in raw framing evidence before UI delivery can coalesce samples. */
class FramingContinuityTracker(private val maxAgeMs: Long = 250, private val maxGapMs: Long = 250) {
    private var serial = 0L
    private var lastTimestampMs: Long? = null

    init {
        require(maxAgeMs >= 0 && maxGapMs >= 0)
    }

    /** Returns the session-local serial that UI readiness must observe. */
    fun observe(observation: FramingObservation?, trackingAvailable: Boolean, nowMs: Long): Long {
        val timestamp = observation?.timestampMs
        val validTimestamp = timestamp != null && timestamp >= 0 && nowMs >= timestamp &&
            nowMs - timestamp <= maxAgeMs
        val previous = lastTimestampMs
        val increasing = timestamp != null && (previous == null || timestamp > previous)
        val gapIsValid = timestamp != null && (previous == null || timestamp - previous <= maxGapMs)
        val continuous = observation?.issue == FramingIssue.FRAMED && trackingAvailable &&
            validTimestamp && increasing && gapIsValid

        if (!continuous) serial++

        // A valid increasing sample starts the next possible continuity span even when
        // its issue/tracking result broke the previous span.
        if (timestamp != null && timestamp >= 0 && increasing) lastTimestampMs = timestamp
        return serial
    }

    /** Starts a new continuity span while keeping serials unique for this session. */
    fun reset(): Long {
        serial++
        lastTimestampMs = null
        return serial
    }
}
