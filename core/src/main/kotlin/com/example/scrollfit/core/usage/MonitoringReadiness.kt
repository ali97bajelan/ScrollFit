package com.example.scrollfit.core.usage

/** A failed settings stream cannot authorize monitoring from a previously observed value. */
class MonitoringReadiness {
    var available = false
        private set
    private var enabled = false
    fun updated(value: Boolean) { enabled = value; available = true }
    fun failed() { available = false }
    fun guard(signal: MonitorSignal) = signal.copy(enabled = enabled, connected = signal.connected && available)
}
