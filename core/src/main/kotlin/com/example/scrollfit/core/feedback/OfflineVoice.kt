package com.example.scrollfit.core.feedback

data class OfflineVoice(val name: String, val language: String, val country: String,
    val networkRequired: Boolean, val installed: Boolean)

/** Never fall back to an online or different-language voice. */
fun selectOfflineVoice(voices: List<OfflineVoice>, language: String, country: String): OfflineVoice? =
    voices.filter { it.language == language && !it.networkRequired && it.installed }
        .sortedWith(compareByDescending<OfflineVoice> { it.country == country }.thenBy { it.name })
        .firstOrNull()
