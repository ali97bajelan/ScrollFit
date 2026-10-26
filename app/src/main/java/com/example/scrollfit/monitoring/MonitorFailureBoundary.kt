package com.example.scrollfit.monitoring

import kotlinx.coroutines.CancellationException

internal class MonitorBoundaryException(
    val category: MonitorFailure,
    val detail: MonitorFailureDetail,
    cause: Exception,
) : Exception(cause)

internal suspend fun <T> monitorBoundary(
    category: MonitorFailure,
    phase: MonitorFailurePhase,
    code: MonitorFailureCode,
    block: suspend () -> T,
): T = try {
    block()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: Exception) {
    throw MonitorBoundaryException(category, MonitorFailureDetail(phase, code), failure)
}

internal inline fun <T> monitorBoundarySync(
    category: MonitorFailure,
    phase: MonitorFailurePhase,
    code: MonitorFailureCode,
    block: () -> T,
): T = try {
    block()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: Exception) {
    throw MonitorBoundaryException(category, MonitorFailureDetail(phase, code), failure)
}

internal class MonitorRegistrations(
    private val registerUsageWatcher: () -> Unit,
    private val unregisterUsageWatcher: () -> Unit,
    private val registerScreenReceiver: () -> Unit,
    private val unregisterScreenReceiver: () -> Unit,
    private val onFailure: (MonitorFailureCode, Exception) -> Unit,
) {
    private var usageWatcherRegistered = false
    private var screenReceiverRegistered = false

    fun register(): Boolean {
        if (!usageWatcherRegistered) {
            try {
                registerUsageWatcher()
                usageWatcherRegistered = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                onFailure(MonitorFailureCode.USAGE_LISTENER_REGISTER, failure)
                unregisterAll()
                return false
            }
        }
        if (!screenReceiverRegistered) {
            try {
                registerScreenReceiver()
                screenReceiverRegistered = true
            } catch (cancelled: CancellationException) {
                try { unregisterAll() } catch (_: CancellationException) { /* Preserve the registration cancellation. */ }
                throw cancelled
            } catch (failure: Exception) {
                onFailure(MonitorFailureCode.SCREEN_RECEIVER_REGISTER, failure)
                unregisterAll()
                return false
            }
        }
        return true
    }

    fun unregisterAll() {
        var cancellation: CancellationException? = null
        if (screenReceiverRegistered) {
            screenReceiverRegistered = false
            try {
                unregisterScreenReceiver()
            } catch (cancelled: CancellationException) {
                cancellation = cancelled
            } catch (failure: Exception) {
                onFailure(MonitorFailureCode.SCREEN_RECEIVER_UNREGISTER, failure)
            }
        }
        if (usageWatcherRegistered) {
            usageWatcherRegistered = false
            try {
                unregisterUsageWatcher()
            } catch (cancelled: CancellationException) {
                if (cancellation == null) cancellation = cancelled
            } catch (failure: Exception) {
                onFailure(MonitorFailureCode.USAGE_LISTENER_UNREGISTER, failure)
            }
        }
        cancellation?.let { throw it }
    }
}
