package com.example.scrollfit.ui

import android.content.Context
import android.text.BidiFormatter
import com.example.scrollfit.R
import com.example.scrollfit.core.settings.DurationDisplay
import java.text.NumberFormat
import java.time.LocalDate
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

internal object ProductFormatters {
    fun duration(context: Context, ms: Long): String {
        val locale = effectiveLocale(context)
        val parts = DurationDisplay.parts(ms)
        val components = buildList {
            if (parts.hours > 0) {
                add(unit(context, locale, R.plurals.ux_duration_hours, parts.hours,
                    integer(parts.hours, locale)))
            }
            if (parts.minutes > 0) {
                add(unit(context, locale, R.plurals.ux_duration_minutes, parts.minutes.toLong(),
                    integer(parts.minutes.toLong(), locale)))
            }
            if (isEmpty()) {
                if (ms > 0L) add(context.getString(R.string.ux_duration_under_minute))
                else add(unit(context, locale, R.plurals.ux_duration_minutes, 0, integer(0, locale)))
            }
        }

        val text = when (components.size) {
            1 -> components[0]
            2 -> context.getString(R.string.ux_duration_pair, components[0], components[1])
            else -> error("Duration formatting produces at most hours and minutes")
        }
        return BidiFormatter.getInstance(locale).unicodeWrap(text)
    }

    fun date(context: Context, day: LocalDate, full: Boolean = false): String {
        val locale = effectiveLocale(context)
        val style = if (full) FormatStyle.FULL else FormatStyle.MEDIUM
        val text = DateTimeFormatter.ofLocalizedDate(style)
            .withLocale(locale)
            .withChronology(IsoChronology.INSTANCE)
            .format(day)
        return BidiFormatter.getInstance(locale).unicodeWrap(text)
    }

    private fun unit(context: Context, locale: Locale, resource: Int, value: Long, formatted: String): String {
        val quantity = value.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
        val number = BidiFormatter.getInstance(locale).unicodeWrap(formatted)
        return context.resources.getQuantityString(resource, quantity, number)
    }

    private fun integer(value: Long, locale: Locale): String =
        NumberFormat.getIntegerInstance(locale).format(value)

    private fun effectiveLocale(context: Context): Locale {
        val locales = context.resources.configuration.locales
        return if (locales.isEmpty) Locale.getDefault() else locales[0]
    }
}
