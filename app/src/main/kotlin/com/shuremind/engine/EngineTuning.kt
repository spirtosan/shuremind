package com.shuremind.engine

import java.time.Duration
import java.time.LocalTime

/**
 * Single source of truth for every tunable constant used by the engine
 * (priority weights, escalation curve, snooze presets) per CLAUDE.md.
 */
object EngineTuning {

    // --- Priority engine (DATA_MODEL.md "Priority engine") ---
    // score = round(100 x (0.4*impact + 0.6*urgency_eff) / 3)
    const val IMPACT_WEIGHT: Double = 0.4
    const val URGENCY_WEIGHT: Double = 0.6
    const val PRIORITY_SCALE: Double = 100.0
    const val SCORE_DIVISOR: Double = 3.0
    const val MAX_URGENCY_EFF: Double = 3.0

    data class UrgencyBoostRule(val maxDaysToDue: Long, val boost: Double)

    /** Ascending by maxDaysToDue; first rule with daysToDue <= maxDaysToDue wins. */
    val URGENCY_BOOST_CURVE: List<UrgencyBoostRule> = listOf(
        UrgencyBoostRule(maxDaysToDue = 2, boost = 2.0),
        UrgencyBoostRule(maxDaysToDue = 7, boost = 1.5),
        UrgencyBoostRule(maxDaysToDue = 14, boost = 1.0),
        UrgencyBoostRule(maxDaysToDue = 30, boost = 0.5)
    )
    const val OVERDUE_URGENCY_BOOST: Double = 3.0
    const val DEFAULT_URGENCY_BOOST: Double = 0.0 // > 30 days out

    // --- Priority chip color bands (D-39): score is 0-100; upper bound of each band below RED. ---
    const val PRIORITY_BAND_NEUTRAL_MAX: Int = 39
    const val PRIORITY_BAND_BLUE_MAX: Int = 59
    const val PRIORITY_BAND_ORANGE_MAX: Int = 79

    // --- Deadline escalation curve (DEADLINE only, DATA_MODEL.md "Deadline escalation") ---
    data class EscalationRule(val maxDaysToDue: Long, val remindersPerDay: Int)

    /** Ascending by maxDaysToDue; first rule with daysToDue <= maxDaysToDue wins. */
    val DEADLINE_ESCALATION_CURVE: List<EscalationRule> = listOf(
        EscalationRule(maxDaysToDue = 2, remindersPerDay = 3),
        EscalationRule(maxDaysToDue = 7, remindersPerDay = 2),
        EscalationRule(maxDaysToDue = 14, remindersPerDay = 1)
    )
    val OVERDUE_ESCALATION_INTERVAL: Duration = Duration.ofHours(4)

    // Concrete escalation slot clock times (DATA_MODEL.md "Deadline escalation" / CLAUDE.md M3 spec).
    // <=14d uses DEFAULT_ALL_DAY_TIME (or the user's configured default-all-day-time from Settings);
    // <=7d and <=2d have fixed multi-slot clock times; overdue steps every OVERDUE_ESCALATION_INTERVAL
    // starting from the user's configured quiet-hours end (see DeadlineEscalationEngine).
    val ESCALATION_TIMES_WITHIN_7D: List<LocalTime> = listOf(LocalTime.of(9, 0), LocalTime.of(18, 0))
    val ESCALATION_TIMES_WITHIN_2D: List<LocalTime> =
        listOf(LocalTime.of(9, 0), LocalTime.of(14, 0), LocalTime.of(19, 0))

    // --- Snooze presets (Settings, DATA_MODEL.md) ---
    val SNOOZE_PRESETS: List<Duration> = listOf(
        Duration.ofHours(1),
        Duration.ofHours(4),
        Duration.ofDays(1)
    )

    // --- Settings defaults (DATA_MODEL.md "Settings") ---
    val DEFAULT_ALL_DAY_TIME: LocalTime = LocalTime.of(9, 0)
    val DEFAULT_QUIET_HOURS_START: LocalTime = LocalTime.of(22, 0)
    val DEFAULT_QUIET_HOURS_END: LocalTime = LocalTime.of(8, 0)
}
