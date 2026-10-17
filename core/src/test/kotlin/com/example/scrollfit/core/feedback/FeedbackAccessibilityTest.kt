package com.example.scrollfit.core.feedback

import org.junit.Assert.assertEquals
import org.junit.Test

class FeedbackAccessibilityTest {
    @Test fun touchExplorationSuppressesAudioWithoutChangingSavedPreference() {
        val updates = mutableListOf<Boolean>()
        val policy = FeedbackAccessibility { updates.add(it) }
        policy.update(speechEnabled = true, touchExplorationEnabled = false)
        policy.update(speechEnabled = true, touchExplorationEnabled = true)
        policy.update(speechEnabled = false, touchExplorationEnabled = true)
        policy.update(speechEnabled = false, touchExplorationEnabled = false)
        policy.update(speechEnabled = true, touchExplorationEnabled = false)

        assertEquals(listOf(true, false, true), updates)
    }
}
