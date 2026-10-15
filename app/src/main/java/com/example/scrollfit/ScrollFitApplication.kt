package com.example.scrollfit

import android.app.Application
import com.example.scrollfit.platform.data.ExerciseGatePersistence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class ScrollFitApplication : Application() {
    val persistence: ExerciseGatePersistence by lazy { ExerciseGatePersistence(this) }
    // Finishes cancellation writes even after a composable/Activity leaves the screen.
    val challengeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
}
