package com.example.scrollfit.spike

import java.lang.ref.WeakReference

/** UI-to-service bridge in the same process; Android owns the sole service instance. */
object SpikeRuntime {
    private var reference = WeakReference<SpikeAccessibilityService>(null)
    val service: SpikeAccessibilityService? get() = reference.get()
    fun connected(service: SpikeAccessibilityService) { reference = WeakReference(service) }
    fun disconnected(service: SpikeAccessibilityService) {
        if (reference.get() === service) reference.clear()
    }
    fun onActivityResume() { service?.onOwnActivityResumed() }
}
