package com.example.scrollfit.core.settings

import org.junit.Assert.*
import org.junit.Test

class SettingNumbersTest {
    @Test fun acceptsLocalizedWholeSeconds() {
        for (text in listOf(" 120 ", "۱۲۰", "١٢٠", "1۲٠")) {
            assertEquals(120_000L, SettingNumbers.milliseconds(text, positive = true))
        }
    }
    @Test fun allowanceAndAwardCapAllowZero() {
        assertEquals(0L, SettingNumbers.milliseconds("۰", positive = false))
        assertNull(SettingNumbers.milliseconds("۰", positive = true))
    }
    @Test fun rejectsSignsFractionsGroupingAndOtherText() {
        for (text in listOf("", " ", "-1", "+1", "1.5", "۱٫۵", "1,000", "1 000", "1e3", "two", "²")) {
            assertNull(text, SettingNumbers.milliseconds(text, positive = false))
        }
    }
    @Test fun rejectsDurationOverflowWithoutThrowing() {
        assertNull(SettingNumbers.milliseconds(Long.MAX_VALUE.toString(), true))
        assertNull(SettingNumbers.milliseconds("9999999999999999999999999999999999", false))
        assertEquals(Long.MAX_VALUE / 1000 * 1000,
            SettingNumbers.milliseconds((Long.MAX_VALUE / 1000).toString(), true))
    }
    @Test fun repetitionTargetsMustBePositiveInt() {
        assertEquals(20, SettingNumbers.positiveInt("۲۰"))
        assertEquals(Int.MAX_VALUE, SettingNumbers.positiveInt(Int.MAX_VALUE.toString()))
        for (text in listOf("0", "-1", "+2", "1.5", "2147483648", "")) assertNull(SettingNumbers.positiveInt(text))
    }
}
