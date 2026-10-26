package com.example.scrollfit.monitoring

import android.accessibilityservice.AccessibilityService
import android.os.PowerManager
import android.os.SystemClock
import android.app.KeyguardManager
import com.example.scrollfit.ScrollFitApplication
import com.example.scrollfit.core.budget.BudgetEngine
import com.example.scrollfit.core.budget.SessionAccess
import com.example.scrollfit.core.budget.SessionAction
import com.example.scrollfit.core.data.AppPreferences
import com.example.scrollfit.core.exercise.Exercise
import com.example.scrollfit.core.usage.CoverageRecorder
import com.example.scrollfit.core.usage.BlockReason
import com.example.scrollfit.platform.data.ExerciseGatePersistence
import com.example.scrollfit.platform.data.LocalInstallIdentity
import com.example.scrollfit.platform.data.RestoreStateCleaner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.ZoneId

internal data class MonitorOverlayCallbacks(
    val home: () -> Unit,
    val openScrollFit: () -> Unit,
    val stop: () -> Unit,
    val failed: () -> Unit,
    val earn: (String, Exercise?) -> Unit,
    val sessionAction: (SessionAccess, SessionAction) -> Unit,
)

interface MonitorOverlay {
    val visible: Boolean
    fun isCurrent(access: SessionAccess): Boolean
    fun show(packageName: String, reason: BlockReason, access: SessionAccess?): Boolean
    fun dismiss(): Boolean
}

/** Android and persistence boundary owned by the monitoring service. */
internal interface MonitorDependencies {
    val preferences: Flow<AppPreferences>
    fun elapsedRealtimeMs(): Long
    fun wallTimeMs(): Long
    fun zoneId(): String
    fun bootIdentity(): String
    suspend fun prepareReadiness()
    fun usageAccess(): Boolean
    fun isInteractive(): Boolean
    fun isKeyguardLocked(): Boolean
    fun protectedPackages(): Set<String>
    suspend fun safePackages(): Set<String>
    suspend fun currentForeground(): ForegroundResult
    suspend fun budgetEngine(bootIdentity: String): BudgetEngine
    suspend fun coverageRecorder(bootIdentity: String, sessionId: String): CoverageRecorder
    suspend fun selectedPackages(): Set<String>
    suspend fun setMonitoringEnabled(enabled: Boolean)
    fun createOverlay(service: AccessibilityService, callbacks: MonitorOverlayCallbacks): MonitorOverlay
}

internal class AndroidMonitorDependencies(
    private val service: MonitoringService,
) : MonitorDependencies {
    private val app: ScrollFitApplication
        get() = service.application as ScrollFitApplication
    private val installIdentity: LocalInstallIdentity
        get() = app.localInstallIdentity ?: LocalInstallIdentity.open(service)
    private val persistence: ExerciseGatePersistence
        get() = app.persistence

    override val preferences: Flow<AppPreferences> get() = persistence.preferences.values
    override fun elapsedRealtimeMs() = SystemClock.elapsedRealtime()
    override fun wallTimeMs() = System.currentTimeMillis()
    override fun zoneId() = ZoneId.systemDefault().id
    override fun bootIdentity() = installIdentity.bootIdentity(MonitorAccess.bootCount(service))
    override suspend fun prepareReadiness() {
        if (!app.localInstallReady) {
            RestoreStateCleaner.clear(service)
            installIdentity.markMonitoringReadinessPrepared()
            app.markLocalInstallReady()
        }
        if (!installIdentity.requiresFreshMonitoringReadiness) return
        persistence.preferences.update { preferences ->
            if (preferences.monitoringEnabled) preferences.copy(monitoringEnabled = false) else preferences
        }
        installIdentity.markMonitoringReadinessPrepared()
    }
    override fun usageAccess() = MonitorAccess.usage(service)
    override fun isInteractive() = requireNotNull(service.getSystemService(PowerManager::class.java)).isInteractive
    override fun isKeyguardLocked() = requireNotNull(service.getSystemService(KeyguardManager::class.java)).isKeyguardLocked
    override fun protectedPackages() = TargetApps.protectedPackages(service)

    override suspend fun safePackages(): Set<String> = withContext(Dispatchers.IO) {
        TargetApps.list(service).map { it.packageName }.toSet()
    }

    override suspend fun currentForeground(): ForegroundResult = withContext(Dispatchers.IO) {
        ForegroundReconciler(service, ::wallTimeMs).current()
    }

    override suspend fun budgetEngine(bootIdentity: String) = persistence.budgetEngine(bootIdentity)
    override suspend fun coverageRecorder(bootIdentity: String, sessionId: String) =
        persistence.coverageRecorder(bootIdentity, sessionId)

    override suspend fun selectedPackages(): Set<String> = persistence.repository().targets()
        .filter { it.selected }.map { it.packageName }.toSet()

    override suspend fun setMonitoringEnabled(enabled: Boolean) {
        persistence.preferences.update { it.copy(monitoringEnabled = enabled) }
    }

    override fun createOverlay(service: AccessibilityService, callbacks: MonitorOverlayCallbacks): MonitorOverlay =
        BudgetOverlay(service, callbacks.home, callbacks.openScrollFit, callbacks.stop, callbacks.failed,
            callbacks.earn, callbacks.sessionAction)
}
