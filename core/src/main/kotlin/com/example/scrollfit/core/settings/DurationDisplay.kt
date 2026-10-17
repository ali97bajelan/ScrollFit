package com.example.scrollfit.core.settings

data class DurationParts(
    val hours: Long,
    val minutes: Int,
    val seconds: Int,
    val milliseconds: Int,
)

object DurationDisplay {
    fun parts(ms: Long): DurationParts {
        val remaining = ms.coerceAtLeast(0)
        val hours = remaining / MILLIS_PER_HOUR
        val afterHours = remaining % MILLIS_PER_HOUR
        val minutes = (afterHours / MILLIS_PER_MINUTE).toInt()
        val afterMinutes = afterHours % MILLIS_PER_MINUTE
        val seconds = (afterMinutes / MILLIS_PER_SECOND).toInt()
        val milliseconds = (afterMinutes % MILLIS_PER_SECOND).toInt()
        return DurationParts(hours, minutes, seconds, milliseconds)
    }

    private const val MILLIS_PER_HOUR = 3_600_000L
    private const val MILLIS_PER_MINUTE = 60_000L
    private const val MILLIS_PER_SECOND = 1_000L
}
