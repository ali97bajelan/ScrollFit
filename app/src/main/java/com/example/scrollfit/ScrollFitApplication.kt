package com.example.scrollfit

import android.app.Application
import com.example.scrollfit.platform.data.ExerciseGatePersistence
import com.example.scrollfit.platform.data.LocalInstallIdentity
import com.example.scrollfit.platform.data.RestoreStateCleaner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class ScrollFitApplication : Application() {
    var localInstallIdentity: LocalInstallIdentity? = null
        private set
    var localInstallReady: Boolean = false
        private set
    val persistence: ExerciseGatePersistence by lazy {
        check(localInstallReady) { "Local app state is not ready" }
        ExerciseGatePersistence(this)
    }
    // Finishes cancellation writes even after a composable/Activity leaves the screen.
    val challengeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        try {
            val identity = LocalInstallIdentity.open(this)
            localInstallIdentity = identity
            if (identity.requiresRestoredStateClear) {
                RestoreStateCleaner.clear(this)
                identity.markMonitoringReadinessPrepared()
            }
            localInstallReady = true
        } catch (_: Exception) {
            // Keep restored state unavailable until a later initialization can clear it safely.
            localInstallReady = false
        }
    }

    fun markLocalInstallReady() { localInstallReady = true }
}
