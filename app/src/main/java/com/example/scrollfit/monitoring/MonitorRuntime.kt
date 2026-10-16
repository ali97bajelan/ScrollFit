package com.example.scrollfit.monitoring

import com.example.scrollfit.core.usage.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    var ownUiVisible = false
        private set
    fun connected(value: MonitoringService) { reference = WeakReference(value) }
    fun disconnected(value: MonitoringService) {
        if (service === value) { reference.clear(); publish(MonitorStatus(hasUnmonitoredGap = true)) }
        ownership.release(value)
    }
    fun publish(value: MonitorStatus) {
        mutableStatus.value = value.copy(hasUnmonitoredGap = value.hasUnmonitoredGap || mutableStatus.value.hasUnmonitoredGap)
    }
    fun activityResumed() { ownUiVisible = true; service?.ownActivityResumed() }
    fun activityPaused() { ownUiVisible = false; service?.requestReconciliation() }
    fun configurationChanged() { service?.reloadConfiguration() }
    fun historyCleared() { service?.historyCleared() }
}
