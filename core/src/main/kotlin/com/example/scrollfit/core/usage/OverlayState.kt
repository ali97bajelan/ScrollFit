package com.example.scrollfit.core.usage

/** Retains a failed-to-remove window and prevents replacement or recursive failure callbacks. */
class OverlayState<T : Any>(private val attach: (T) -> Unit, private val detach: (T) -> Unit,
    private val failure: () -> Unit) {
    var current: T? = null
        private set
    private var removing = false
    fun replace(next: T): Boolean {
        if (!dismiss()) return false
        return try { attach(next); current = next; true }
        catch (_: RuntimeException) { failure(); false }
    }
    fun dismiss(): Boolean {
        val old = current ?: return true
        if (removing) return false
        removing = true
        return try { detach(old); current = null; true }
        catch (_: RuntimeException) { failure(); false }
        finally { removing = false }
    }
}
