package com.example.scrollfit.core.time

import java.time.LocalDate

interface WallClock {
    fun localDate(): LocalDate
    fun epochMillis(): Long
}

interface MonotonicClock {
    fun elapsedRealtimeMillis(): Long
}
