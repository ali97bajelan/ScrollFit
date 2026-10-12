package com.example.scrollfit.platform.data

import android.content.Context
import com.example.scrollfit.core.data.DurableRepository
import com.example.scrollfit.core.time.MonotonicClock
import com.example.scrollfit.core.time.WallClock
import com.example.scrollfit.platform.time.AndroidMonotonicClock
import com.example.scrollfit.platform.time.AndroidWallClock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Application owner creates this once. T04/T05 consume these clocks and repositories. */
class ExerciseGatePersistence(
    context: Context,
    val wallClock: WallClock = AndroidWallClock(),
    val monotonicClock: MonotonicClock = AndroidMonotonicClock(),
) {
    private val database = ExerciseGateDatabase.open(context)
    private val durableRepository = DurableRepository(RoomStore(database), wallClock)
    val preferences = PreferencesRepository.open(context)
    private val initialization = Mutex()
    private var recovered = false

    /** Recovery finishes before any caller can complete a challenge from a previous process. */
    suspend fun repository(): DurableRepository = initialization.withLock {
        if (!recovered) {
            durableRepository.cancelInterruptedChallenges()
            recovered = true
        }
        durableRepository
    }
}
