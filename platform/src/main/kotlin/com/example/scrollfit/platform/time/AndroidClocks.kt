package com.example.scrollfit.platform.time

import android.os.SystemClock
import com.example.scrollfit.core.time.MonotonicClock
import com.example.scrollfit.core.time.WallClock
import java.time.Clock
import java.time.LocalDate

class AndroidWallClock(private val clock: Clock = Clock.systemDefaultZone()) : WallClock {
    override fun localDate(): LocalDate = LocalDate.now(clock)
    override fun epochMillis(): Long = clock.millis()
}

class AndroidMonotonicClock : MonotonicClock {
    override fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()
}
