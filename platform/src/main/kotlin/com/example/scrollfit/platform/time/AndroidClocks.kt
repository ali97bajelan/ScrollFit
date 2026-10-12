package com.example.scrollfit.platform.time

import android.os.SystemClock
import com.example.scrollfit.core.time.MonotonicClock
import com.example.scrollfit.core.time.WallClock
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

class AndroidWallClock(
    private val clock: Clock = Clock.systemUTC(),
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) : WallClock {
    override fun localDate(): LocalDate = LocalDate.ofInstant(clock.instant(), zone())
    override fun epochMillis(): Long = clock.millis()
}

class AndroidMonotonicClock : MonotonicClock {
    override fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()
}
