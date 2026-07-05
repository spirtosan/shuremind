package com.shuremind.engine

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * Engine-facing scheduling fields for a task (subset of DATA_MODEL.md's Task
 * columns relevant to next-occurrence math). Plain Kotlin — no Room/Android.
 */
data class TaskSchedule(
    val type: TaskType,
    val dueLocalDate: LocalDate? = null,
    val dueLocalTime: LocalTime? = null,
    val notBefore: LocalDate? = null,
    val recFreq: RecurrenceFrequency? = null,
    val recInterval: Int = 1,
    val recAnchor: RecurrenceAnchor? = null,
    val recDaysOfWeek: Set<DayOfWeek> = emptySet(),
    val recDayOfMonth: Int? = null,
    val recTimesOfDay: List<LocalTime> = emptyList(),
    val recEndDate: LocalDate? = null,
    val nagIntervalHours: Double? = null,
    val stockQty: Double? = null,
    val dosePerIntake: Double? = null,
    val restockLeadDays: Int? = null
)
