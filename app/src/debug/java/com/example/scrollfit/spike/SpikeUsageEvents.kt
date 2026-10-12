package com.example.scrollfit.spike

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

/** Metadata only, transient lookup; package history is neither logged nor persisted. */
class SpikeUsageEvents(private val context: Context) {
    @Suppress("DEPRECATION")
    fun foregroundPackage(): String? {
        if (!SpikeAccess.hasUsageAccess(context)) return null
        val end = System.currentTimeMillis()
        return try {
            val events = requireNotNull(context.getSystemService(UsageStatsManager::class.java))
                .queryEvents((end - 5 * 60_000).coerceAtLeast(0), end) ?: return null
            val event = UsageEvents.Event()
            var foreground: String? = null
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                when (event.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> foreground = event.packageName
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> if (foreground == event.packageName) foreground = null
                }
            }
            foreground
        } catch (_: SecurityException) { null }
    }
}
