package com.shuremind.ui.common

import java.time.Duration

/** D-37: unit choices for the structured reminder-offset picker. */
enum class OffsetUnit(val secondsPerUnit: Long) {
    MINUTES(60),
    HOURS(3_600),
    DAYS(86_400),
    WEEKS(604_800)
}

/** D-37: a reminder offset is either "at due" (stored as PT0S) or a positive amount of one unit before due. */
sealed interface ReminderOffsetValue {
    data object AtDue : ReminderOffsetValue
    data class Before(val amount: Int, val unit: OffsetUnit) : ReminderOffsetValue
}

/**
 * D-37: pure ISO-8601-duration <-> structured-picker conversion. Storage/export keep the ISO string
 * (DATA_MODEL.md ReminderRule.offset_iso) exactly as before; this is purely a display/edit-time mapping.
 * No Android/Compose imports so it is plain-JUnit testable without Robolectric.
 */
object ReminderOffsetFormat {

    /** Sane cap for new input (D-37): 365 days expressed in minutes. */
    const val MAX_MINUTES: Long = 365L * 24 * 60

    /**
     * Returns null for anything Duration can't parse, negative durations, or sub-minute remainders —
     * the caller must treat null as "unparseable stored value", never crash on it (D-37).
     */
    fun parse(iso: String): ReminderOffsetValue? {
        val duration = runCatching { Duration.parse(iso) }.getOrNull() ?: return null
        if (duration.isNegative) return null
        if (duration.nano != 0) return null
        if (duration.isZero) return ReminderOffsetValue.AtDue
        val totalSeconds = duration.seconds
        // Largest unit that divides evenly, so P14D displays as "2 weeks", not "14 days" (D-37).
        val unit = OffsetUnit.entries
            .sortedByDescending { it.secondsPerUnit }
            .firstOrNull { totalSeconds % it.secondsPerUnit == 0L }
            ?: return null
        val amount = totalSeconds / unit.secondsPerUnit
        if (amount <= 0 || amount > Int.MAX_VALUE) return null
        return ReminderOffsetValue.Before(amount.toInt(), unit)
    }

    /**
     * Days/weeks are emitted as PnD (not Duration.toString()'s PTnH form) so the stored string keeps
     * matching the existing DATA_MODEL.md examples ('P14D', 'P1D'); weeks map to P{n*7}D (D-37).
     */
    fun toIso(value: ReminderOffsetValue): String = when (value) {
        ReminderOffsetValue.AtDue -> "PT0S"
        is ReminderOffsetValue.Before -> when (value.unit) {
            OffsetUnit.MINUTES -> "PT${value.amount}M"
            OffsetUnit.HOURS -> "PT${value.amount}H"
            OffsetUnit.DAYS -> "P${value.amount}D"
            OffsetUnit.WEEKS -> "P${value.amount * 7}D"
        }
    }

    /** New-input validation only (D-37): integers >= 1 (0 is only meaningful via the separate "at due" option), capped. */
    fun isValidAmount(amount: Int, unit: OffsetUnit): Boolean {
        if (amount < 1) return false
        val totalMinutes = amount.toLong() * (unit.secondsPerUnit / 60)
        return totalMinutes <= MAX_MINUTES
    }
}
