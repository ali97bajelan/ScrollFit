package com.example.scrollfit.core.settings

import com.example.scrollfit.core.data.TargetConfig
import org.junit.Assert.*
import org.junit.Test

class TargetSettingsTest {
    private val current = listOf(TargetConfig("available"), TargetConfig("missing", dailyAwardCapMs = 42_000))
    @Test fun unrelatedSaveRetainsUnavailableSelectionAndTerms() {
        val edits = TargetSettings.edits(current, setOf("available", "missing"), setOf("available"),
            mapOf("available" to "۰"), mapOf("available" to "۰"))
        assertTrue(edits.removed.isEmpty())
        assertEquals(listOf(TargetConfig("available", baseAllowanceMs = 0, dailyAwardCapMs = 0)), edits.saved)
    }
    @Test fun unavailableSelectionRemovedOnlyByExplicitEdit() {
        val edits = TargetSettings.edits(current, setOf("available"), setOf("available"),
            mapOf("available" to "۱۸۰۰"), mapOf("available" to "۱۲۰۰"))
        assertEquals(setOf("missing"), edits.removed)
    }
    @Test fun newUnavailableAppCannotBeSelected() {
        try {
            TargetSettings.edits(current, setOf("new"), emptySet(), emptyMap(), emptyMap())
            fail("must recheck selectable apps")
        } catch (_: IllegalArgumentException) { }
    }
    @Test fun validateAllFieldsBeforeAnyEditCanBeApplied() {
        try {
            TargetSettings.edits(current, setOf("available"), setOf("available"),
                mapOf("available" to "1800"), mapOf("available" to "-1"))
            fail("invalid award cap")
        } catch (_: IllegalArgumentException) { }
        assertTrue(current.all { it.selected })
        assertEquals(42_000L, current.last().dailyAwardCapMs)
    }
}
