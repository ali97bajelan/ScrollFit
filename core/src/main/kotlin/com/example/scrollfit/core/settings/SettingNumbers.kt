package com.example.scrollfit.core.settings

/** Parses whole-number input from ASCII, Persian or Arabic keyboards; invalid edits never throw. */
object SettingNumbers {
    internal fun wholeNumber(text: String): Long? {
        val digits = text.trim()
        if (digits.isEmpty()) return null
        val normalized = buildString {
            for (char in digits) {
                val value = when (char) {
                    in '0'..'9' -> char - '0'
                    in '۰'..'۹' -> char - '۰'
                    in '٠'..'٩' -> char - '٠'
                    else -> return null
                }
                append(('0'.code + value).toChar())
            }
        }
        return normalized.toLongOrNull()
    }
    fun milliseconds(text: String, positive: Boolean): Long? {
        val seconds = wholeNumber(text) ?: return null
        if (positive && seconds == 0L || seconds > Long.MAX_VALUE / 1000) return null
        return seconds * 1000
    }
    fun positiveInt(text: String): Int? = wholeNumber(text)?.takeIf { it in 1..Int.MAX_VALUE.toLong() }?.toInt()
}
