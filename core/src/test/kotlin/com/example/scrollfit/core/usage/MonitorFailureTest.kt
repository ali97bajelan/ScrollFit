package com.example.scrollfit.core.usage

import com.example.scrollfit.core.budget.*
import org.junit.Assert.*
import org.junit.Test

class MonitorFailureTest {
    @Test fun failedRemovalAbortsReplacementRetainsHandleAndCanRetry() {
        var detachFails = false
        val attached = mutableListOf<String>()
        var failures = 0
        val windows = OverlayState<String>({ attached += it }, { if (detachFails) error("remove failed") }, { failures++ })
        assertTrue(windows.replace("A")); detachFails = true
        assertFalse(windows.replace("B"))
        assertEquals(listOf("A"), attached); assertEquals("A", windows.current); assertEquals(1, failures)
        detachFails = false; assertTrue(windows.dismiss()); assertNull(windows.current)
        assertTrue(windows.replace("B")); assertEquals(listOf("A", "B"), attached)
    }

    @Test fun failureCallbackDismissCannotRecurseOrAttachReplacement() {
        var attached = 0
        var failures = 0
        lateinit var windows: OverlayState<String>
        windows = OverlayState({ attached++ }, { error("remove failed") }, {
            failures++; assertFalse(windows.dismiss())
        })
        windows.replace("A"); assertFalse(windows.replace("B"))
        assertEquals(1, failures); assertEquals(1, attached); assertEquals("A", windows.current)
    }

    @Test fun safeTransitionDismissesImmediatelyAndStaleStorageDecisionCannotRestoreBlocker() {
        var visible: String? = null
        val presentation = MonitorPresentation({ app, _ -> visible = app; true }, { visible = null; true })
        val targetTicket = presentation.transition("A", pause = false)
        val blocked = MonitorDecision(MonitoringHealth.HEALTHY, "A",
            Budget("A", "2026-10-13", 0, 0, 0, 0, 1000, 0, BudgetBlock.APP_BUDGET))
        assertTrue(presentation.present(targetTicket, blocked)); assertEquals("A", visible)
        // Simulate an outstanding worker decision, then Home/Settings/self before that read returns.
        val pending = presentation.transition("A", pause = false)
        presentation.transition("system", pause = true)
        assertNull(visible)
        assertFalse(presentation.present(pending, blocked)); assertNull(visible)
    }

    @Test fun permissionLossAndExplicitPauseInvalidatePendingPresentation() {
        var visible = false
        val presentation = MonitorPresentation({ _, _ -> visible = true; true }, { visible = false; true })
        val ticket = presentation.transition("A", pause = false)
        val blocked = MonitorDecision(MonitoringHealth.HEALTHY, "A",
            Budget("A", "2026-10-13", 0, 0, 0, 0, 1000, 0, BudgetBlock.COMBINED_CEILING))
        presentation.present(ticket, blocked); assertTrue(visible)
        assertTrue(presentation.pause()); assertFalse(visible)
        assertFalse(presentation.present(ticket, blocked))
    }

    @Test fun failedPreferenceStreamNeverUsesStaleEnabledValueUntilFreshRead() {
        val readiness = MonitoringReadiness()
        val signal = MonitorSignal(MonitorSample(0, 0, "UTC"), "A", true, true, true, true)
        readiness.updated(true); assertTrue(readiness.guard(signal).connected)
        readiness.failed()
        assertFalse(readiness.guard(signal).connected)
        assertFalse(readiness.available)
        readiness.updated(false)
        assertTrue(readiness.guard(signal).connected); assertFalse(readiness.guard(signal).enabled)
    }
}
