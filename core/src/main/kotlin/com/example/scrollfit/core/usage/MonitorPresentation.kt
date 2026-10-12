package com.example.scrollfit.core.usage

import com.example.scrollfit.core.budget.BudgetBlock

/** UI-thread adapter: immediate safe-surface dismissal and stale worker-result invalidation. */
class MonitorPresentation(private val show: (String, BudgetBlock) -> Boolean, private val hide: () -> Boolean) {
    private var revision = 0L
    private var target: String? = null
    fun transition(foreground: String?, pause: Boolean): Long {
        revision++
        if (pause || foreground != target) if (hide()) target = null
        return revision
    }
    fun pause(): Boolean {
        revision++
        val removed = hide()
        if (removed) target = null
        return removed
    }
    fun present(ticket: Long, decision: MonitorDecision): Boolean {
        if (ticket != revision) return false
        val app = decision.foreground
        val reason = decision.blockedBy
        if (app != null && reason != null) {
            if (!show(app, reason)) return false
            target = app
        } else {
            if (!hide()) return false
            target = null
        }
        return true
    }
}
