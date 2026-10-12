package com.example.scrollfit.core.spike

/** Experimental T02 clock, deliberately separate from the production daily budget engine. */
class SpikeCountdown(
    val allowanceMillis: Long = ALLOWANCE_MILLIS,
    consumedMillis: Long = 0,
) {
    init {
        require(allowanceMillis > 0)
        require(consumedMillis in 0..allowanceMillis)
    }

    var consumedMillis: Long = consumedMillis
        private set
    private var lastTimestampMillis: Long? = null
    private var charging = false

    val remainingMillis: Long get() = allowanceMillis - consumedMillis
    val exhausted: Boolean get() = remainingMillis == 0L

    /** Charges the preceding interval once, then sets the next interval's eligibility. */
    fun update(timestampMonotonicMillis: Long, chargeable: Boolean) {
        require(timestampMonotonicMillis >= 0)
        val previous = lastTimestampMillis
        if (previous != null && timestampMonotonicMillis < previous) {
            // A stale event must neither move the cursor backwards nor change eligibility.
            return
        }
        if (previous != null && charging) {
            consumedMillis += minOf(timestampMonotonicMillis - previous, remainingMillis)
        }
        lastTimestampMillis = timestampMonotonicMillis
        charging = chargeable && !exhausted
    }

    companion object { const val ALLOWANCE_MILLIS = 30_000L }
}
