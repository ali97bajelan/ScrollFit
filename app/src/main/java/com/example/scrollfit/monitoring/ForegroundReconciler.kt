package com.example.scrollfit.monitoring

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

data class ForegroundResult(val packageName: String?, val known: Boolean)

/** Bounded transient metadata lookup. No package history is recorded or logged. */
class ForegroundReconciler(
    private val context: Context,
    private val wallTimeMs: () -> Long = { System.currentTimeMillis() },
) {
    @Suppress("DEPRECATION")
    fun current(): ForegroundResult {
        if (!MonitorAccess.usage(context)) return ForegroundResult(null, false)
        val end = wallTimeMs()
        return try {
            val events = requireNotNull(context.getSystemService(UsageStatsManager::class.java))
                .queryEvents((end - 5 * 60_000).coerceAtLeast(0), end) ?: return ForegroundResult(null, false)
            val event = UsageEvents.Event()
            var foreground: String? = null
            var known = false
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                when (event.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> { foreground = event.packageName; known = true }
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> if (foreground == event.packageName) foreground = null
                    UsageEvents.Event.SCREEN_NON_INTERACTIVE, UsageEvents.Event.DEVICE_SHUTDOWN -> { foreground = null; known = true }
                }
            }
            ForegroundResult(foreground, known)
        } catch (_: SecurityException) { ForegroundResult(null, false) }
    }
}
