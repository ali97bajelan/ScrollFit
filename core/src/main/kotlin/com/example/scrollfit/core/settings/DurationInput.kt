package com.example.scrollfit.core.settings

enum class DurationUnit(val milliseconds: Long) {
    MINUTES(60_000L),
    SECONDS(1_000L),
}

/** Parses whole, nonnegative duration amounts without allowing multiplication to overflow. */
object DurationInput {
    fun parse(text: String, unit: DurationUnit, positive: Boolean): Long? {
        val amount = SettingNumbers.wholeNumber(text) ?: return null
        if ((positive && amount == 0L) || amount > Long.MAX_VALUE / unit.milliseconds) return null
        return amount * unit.milliseconds
    }
}
