package com.example.scrollfit.monitoring

import com.example.scrollfit.core.usage.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference

enum class MonitorFailure { NONE, STARTING, OWNER_BUSY, BOOT_ID, STORAGE, OVERLAY, QUEUE, INTERRUPTED, METADATA }
data class MonitorStatus(val health: MonitoringHealth = MonitoringHealth.SERVICE_STOPPED,
    val decision: MonitorDecision? = null, val failure: MonitorFailure = MonitorFailure.NONE,
    val hasUnmonitoredGap: Boolean = false)

/** Same-process UI bridge; the system owns service lifetime. */
object MonitorRuntime {
    val ownership = MonitorOwnership()
    private var reference = WeakReference<MonitoringService>(null)
    val service get() = reference.get()
    private val mutableStatus = MutableStateFlow(MonitorStatus())
    val status: StateFlow<MonitorStatus> = mutableStatus
    private val mutableInitializationState = MutableStateFlow(MonitorInitState.STOPPED)
    internal val initializationState: StateFlow<MonitorInitState> = mutableInitializationState.asStateFlow()
    var ownUiVisible = false
        private set
    fun connected(value: MonitoringService) {
        reference = WeakReference(value)
        publishInitializationState(MonitorInitState.STOPPED)
    }
    fun disconnected(value: MonitoringService) {
        if (service === value) { reference.clear(); publish(MonitorStatus(hasUnmonitoredGap = true)) }
    }
    fun publish(value: MonitorStatus) {
        mutableStatus.value = value.copy(hasUnmonitoredGap = value.hasUnmonitoredGap || mutableStatus.value.hasUnmonitoredGap)
    }
    internal fun publishInitializationState(value: MonitorInitState) { mutableInitializationState.value = value }
    fun retryMonitoring() { service?.retryInitialization() }
    fun activityResumed() { ownUiVisible = true; service?.ownActivityResumed() }
    fun activityPaused() { ownUiVisible = false; service?.requestReconciliation() }
    fun configurationChanged() { service?.reloadConfiguration() }
    fun historyCleared() { service?.historyCleared() }
}
