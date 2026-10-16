package com.example.scrollfit.platform.data

import android.content.Context
import com.example.scrollfit.core.data.DurableRepository
import com.example.scrollfit.core.data.DataRepository
import com.example.scrollfit.core.budget.BudgetEngine
import com.example.scrollfit.core.time.MonotonicClock
import com.example.scrollfit.core.time.WallClock
import com.example.scrollfit.core.usage.CoverageRecorder
import com.example.scrollfit.platform.time.AndroidMonotonicClock
import com.example.scrollfit.platform.time.AndroidWallClock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.first

/** Application owner creates this once. T04/T05 consume these clocks and repositories. */
class ExerciseGatePersistence(
    context: Context,
    val wallClock: WallClock = AndroidWallClock(),
    val monotonicClock: MonotonicClock = AndroidMonotonicClock(),
) {
    private val database = ExerciseGateDatabase.open(context)
    private val store = RoomStore(database)
    private val durableRepository = DurableRepository(store, wallClock)
    val preferences = PreferencesRepository.open(context)
    private val initialization = Mutex()
    private var recovered = false

    /** Recovery finishes before any caller can complete a challenge from a previous process. */
    suspend fun repository(): DataRepository = initialization.withLock {
        if (!recovered) {
            durableRepository.cancelInterruptedChallenges()
            recovered = true
        }
        applicationRepository
    }

    // Delegation returns a facade, so even a cast cannot recover raw T03 policy-writing methods.
    private val applicationRepository = object : DataRepository by durableRepository {}

    /** T05 supplies one stable device-boot identity and owns the eligible interval stream. */
    suspend fun budgetEngine(timelineId: String): BudgetEngine {
        repository() // Recovery must precede any challenge completion.
        return BudgetEngine(store, wallClock, monotonicClock, timelineId) { preferences.values.first() }
    }

    suspend fun coverageRecorder(timelineId: String, recordingSessionId: String): CoverageRecorder {
        repository()
        return CoverageRecorder(timelineId, recordingSessionId, durableRepository::recordCoverage)
    }
}
