package com.example.scrollfit.core.feedback

/** Applies saved audio preference only when touch exploration is not using screen-reader speech. */
class FeedbackAccessibility(private val applyEnabled: (Boolean) -> Unit) {
    private var effectiveEnabled: Boolean? = null

    fun update(speechEnabled: Boolean, touchExplorationEnabled: Boolean) {
        val next = speechEnabled && !touchExplorationEnabled
        if (effectiveEnabled == next) return
        effectiveEnabled = next
        applyEnabled(next)
    }
}
