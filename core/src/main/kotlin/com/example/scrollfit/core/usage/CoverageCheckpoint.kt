package com.example.scrollfit.core.usage

object CoverageCheckpoint {
    const val IDLE_DELAY_MS = 60_000L

    fun nextDelayMs(nowElapsedMs: Long, lastProcessedElapsedMs: Long?, enabled: Boolean): Long? {
        if (!enabled || lastProcessedElapsedMs == null) return null
        return (IDLE_DELAY_MS - (nowElapsedMs - lastProcessedElapsedMs)).coerceAtLeast(0L)
    }
}
