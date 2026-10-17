package com.example.scrollfit.core.pose

/** Presentation categories whose changes are useful to announce to a screen reader. */
enum class WorkoutTracking { INITIALIZING, SEARCHING, TRACKING, LOST, ERROR }

/** Deduplicates stable tracking transitions without requiring camera frames to keep arriving. */
class TrackingAnnouncementGate(private val stableForMs: Long = 1_000L) {
    private var announced: WorkoutTracking? = null
    private var pending: WorkoutTracking? = null
    private var pendingSinceMs: Long? = null

    init { require(stableForMs >= 0) }

    /** Returns the category to announce, or null until a new category is stable. */
    fun update(status: WorkoutTracking, nowMs: Long): WorkoutTracking? {
        // SEARCHING and LOST are two camera states for the same user-facing category.
        val category = if (status == WorkoutTracking.SEARCHING) WorkoutTracking.LOST else status
        if (category == WorkoutTracking.ERROR) {
            pending = null
            pendingSinceMs = null
            if (announced == category) return null
            announced = category
            return category
        }
        if (category == WorkoutTracking.INITIALIZING) {
            pending = null
            pendingSinceMs = null
            return null
        }
        if (category == announced) {
            pending = null
            pendingSinceMs = null
            return null
        }
        if (category != pending || pendingSinceMs == null || nowMs < pendingSinceMs!!) {
            pending = category
            pendingSinceMs = nowMs
            if (stableForMs == 0L) {
                announced = category
                pending = null
                pendingSinceMs = null
                return category
            }
            return null
        }
        if (nowMs - pendingSinceMs!! < stableForMs) return null
        announced = category
        pending = null
        pendingSinceMs = null
        return category
    }

    fun reset() {
        announced = null
        pending = null
        pendingSinceMs = null
    }
}
