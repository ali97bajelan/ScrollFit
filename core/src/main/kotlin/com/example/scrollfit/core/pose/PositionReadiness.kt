package com.example.scrollfit.core.pose

/** Requires a continuous, fresh one-second run of complete framing observations. */
class PositionReadiness(
    private val requiredDurationMs: Long = 1_000,
    private val maxAgeMs: Long = 250,
    private val maxGapMs: Long = 250,
) {
    private var firstTimestampMs: Long? = null
    private var lastTimestampMs: Long? = null
    private var ready = false

    init {
        require(requiredDurationMs > 0 && maxAgeMs >= 0 && maxGapMs >= 0)
    }

    fun update(observation: FramingObservation?, trackingAvailable: Boolean, nowMs: Long): Boolean {
        val timestamp = observation?.timestampMs
        if (observation?.issue != FramingIssue.FRAMED || !trackingAvailable || timestamp == null ||
            timestamp < 0 || nowMs < timestamp || nowMs - timestamp > maxAgeMs) {
            reset()
            return false
        }

        val previous = lastTimestampMs
        if (previous != null) {
            if (timestamp <= previous) {
                reset()
                return false
            }
            if (timestamp - previous > maxGapMs) {
                // The current fresh sample begins a new window after a gap.
                firstTimestampMs = timestamp
                lastTimestampMs = timestamp
                ready = false
                return false
            }
        }

        if (firstTimestampMs == null) firstTimestampMs = timestamp
        lastTimestampMs = timestamp
        ready = timestamp - (firstTimestampMs ?: timestamp) >= requiredDurationMs
        return ready
    }

    /** Watchdog poll; it expires old evidence without inventing a sample. */
    fun isReady(nowMs: Long): Boolean {
        val last = lastTimestampMs ?: return false
        if (nowMs < last || nowMs - last > maxAgeMs) {
            reset()
            return false
        }
        return ready
    }

    fun reset() {
        firstTimestampMs = null
        lastTimestampMs = null
        ready = false
    }
}
