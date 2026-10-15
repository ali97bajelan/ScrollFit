package com.example.scrollfit.core.feedback

import org.junit.Assert.*
import org.junit.Test

class OfflineVoiceTest {
    @Test fun selectsInstalledPersianVoiceAndRejectsNetworkAndUninstalledVoices() {
        val voices = listOf(OfflineVoice("net", "fa", "IR", true, true),
            OfflineVoice("download", "fa", "IR", false, false),
            OfflineVoice("local", "fa", "IR", false, true))
        assertEquals("local", selectOfflineVoice(voices, "fa", "IR")?.name)
    }
    @Test fun missingPersianVoiceDoesNotSelectAnotherLanguage() {
        assertNull(selectOfflineVoice(listOf(OfflineVoice("english", "en", "US", false, true)), "fa", "IR"))
    }
    @Test fun prefersExactRegionWithSameLanguageFallback() {
        val voices = listOf(OfflineVoice("us", "en", "US", false, true), OfflineVoice("gb", "en", "GB", false, true))
        assertEquals("gb", selectOfflineVoice(voices, "en", "GB")?.name)
        assertEquals("gb", selectOfflineVoice(voices, "en", "AU")?.name) // Stable name ordering.
    }
    @Test fun emptyVoiceListUsesFallback() { assertNull(selectOfflineVoice(emptyList(), "fr", "FR")) }
}
