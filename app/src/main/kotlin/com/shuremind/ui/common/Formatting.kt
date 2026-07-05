package com.shuremind.ui.common

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import com.shuremind.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** All list/detail date-time display goes through java.time with the current app locale (CLAUDE.md i18n rule). */
@Composable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

fun formatEpochMillis(epochMillis: Long, zone: ZoneId, locale: Locale): String {
    val zdt = Instant.ofEpochMilli(epochMillis).atZone(zone)
    val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
    return "${dateFormatter.format(zdt.toLocalDate())} ${timeFormatter.format(zdt.toLocalTime())}"
}

fun formatLocalDate(date: LocalDate, locale: Locale): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(date)

fun formatLocalTime(time: LocalTime, locale: Locale): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(time)

/** "in 1 hour" / "in 4 hours" / "in 1 day" — plurals per CLAUDE.md (mandatory for Russian). */
fun formatSnoozeDuration(context: Context, minutes: Long): String = when {
    minutes % 1440 == 0L && minutes >= 1440 -> context.resources.getQuantityString(
        R.plurals.snooze_days, (minutes / 1440).toInt(), (minutes / 1440).toInt()
    )
    minutes % 60 == 0L && minutes >= 60 -> context.resources.getQuantityString(
        R.plurals.snooze_hours, (minutes / 60).toInt(), (minutes / 60).toInt()
    )
    else -> context.resources.getQuantityString(R.plurals.snooze_minutes, minutes.toInt(), minutes.toInt())
}
