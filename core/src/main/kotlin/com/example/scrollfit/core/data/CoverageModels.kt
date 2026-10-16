package com.example.scrollfit.core.data

import java.time.LocalDate
import java.time.ZoneId

data class CoverageInterval(
    val id: String,
    val recordingSessionId: String,
    val timelineId: String,
    val day: String,
    val zoneId: String,
    val startElapsedMs: Long,
    val endElapsedMs: Long,
    val startEpochMs: Long,
    val endEpochMs: Long,
    val selectedPackages: Set<String>,
    val historyVisible: Boolean = true,
) {
    init {
        require(id.isNotBlank() && recordingSessionId.isNotBlank() && timelineId.isNotBlank())
        LocalDate.parse(day)
        ZoneId.of(zoneId)
        require(startElapsedMs >= 0 && endElapsedMs > startElapsedMs)
        require(endEpochMs > startEpochMs)
        require(selectedPackages.none { it.isBlank() })
    }
}
