package com.example.scrollfit.core.usage

/** An interactive foreground transition; null means no chargeable target. */
data class ForegroundTransition(val packageName: String?, val timestampMonotonicMs: Long)

enum class MonitoringHealth { DISABLED, PERMISSION_MISSING, SERVICE_STOPPED, HEALTHY }

interface ForegroundMonitor {
    fun start(onTransition: (ForegroundTransition) -> Unit)
    fun stop()
    fun health(): MonitoringHealth
}

enum class BlockReason { APP_BUDGET, COMBINED_CEILING, SESSION_BOUNDARY }

/** Android-supported surface adapter is evaluated on the intended device in T02. */
interface BlockingSurface {
    fun show(packageName: String, reason: BlockReason)
    fun dismiss()
}
