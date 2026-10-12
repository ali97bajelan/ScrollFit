package com.example.scrollfit

import android.app.Application
import com.example.scrollfit.platform.data.ExerciseGatePersistence

class ScrollFitApplication : Application() {
    val persistence: ExerciseGatePersistence by lazy { ExerciseGatePersistence(this) }
}
