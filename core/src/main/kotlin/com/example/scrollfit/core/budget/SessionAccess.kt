package com.example.scrollfit.core.budget

import com.example.scrollfit.core.data.SessionState
import com.example.scrollfit.core.usage.BlockReason

/** Wallet and session read together. Total usage makes a displayed prompt unique within its day. */
data class SessionAccess(
    val budget: Budget,
    val session: SessionState,
    val boundaryMs: Long,
    val totalAppUsageMs: Long,
) {
    val sessionRemainingMs get() = if (session.promptPending || session.consumedMs >= boundaryMs) 0L
        else boundaryMs - session.consumedMs
    val usableMs get() = minOf(budget.usableMs, sessionRemainingMs)
    val blockedBy: BlockReason? get() = when (budget.blockedBy) {
        BudgetBlock.TARGET_UNAVAILABLE -> null
        BudgetBlock.COMBINED_CEILING -> BlockReason.COMBINED_CEILING
        BudgetBlock.APP_BUDGET -> BlockReason.APP_BUDGET
        null -> if (sessionRemainingMs == 0L) BlockReason.SESSION_BOUNDARY else null
    }
}

enum class SessionAction { CONTINUE, FINISH }
enum class SessionActionResult { APPLIED, STALE, BUDGET_BLOCKED }
