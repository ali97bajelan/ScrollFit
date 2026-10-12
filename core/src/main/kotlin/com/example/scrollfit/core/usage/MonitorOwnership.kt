package com.example.scrollfit.core.usage

/** Identity-based lease shared by production and experimental monitors in one process. */
class MonitorOwnership {
    private var owner: Any? = null
    @Synchronized fun acquire(candidate: Any): Boolean {
        if (owner != null && owner !== candidate) return false
        owner = candidate
        return true
    }
    @Synchronized fun release(candidate: Any) { if (owner === candidate) owner = null }
}
