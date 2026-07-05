package com.shuremind.data.repo

import com.shuremind.data.entity.TaskEntity
import com.shuremind.engine.TaskSchedule
import com.shuremind.engine.TaskType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** Types whose Task stays ACTIVE and gets a recomputed next_fire_at after Done/Skip (DATA_MODEL.md "status"). */
val RECURRING_TYPES: Set<TaskType> = setOf(
    TaskType.ANNIVERSARY, TaskType.WINDOW, TaskType.NAG, TaskType.RECURRING, TaskType.CONSUMABLE
)

private val DAY_ABBREVIATIONS = mapOf(
    DayOfWeek.MONDAY to "MO",
    DayOfWeek.TUESDAY to "TU",
    DayOfWeek.WEDNESDAY to "WE",
    DayOfWeek.THURSDAY to "TH",
    DayOfWeek.FRIDAY to "FR",
    DayOfWeek.SATURDAY to "SA",
    DayOfWeek.SUNDAY to "SU"
)
private val ABBREVIATION_TO_DAY = DAY_ABBREVIATIONS.entries.associate { (day, abbr) -> abbr to day }

fun Set<DayOfWeek>.toCsv(): String? = takeIf { it.isNotEmpty() }?.joinToString(",") { DAY_ABBREVIATIONS.getValue(it) }

fun String?.parseDaysOfWeekCsv(): Set<DayOfWeek> =
    this?.split(",")?.filter { it.isNotBlank() }?.mapNotNull { ABBREVIATION_TO_DAY[it.trim()] }?.toSet() ?: emptySet()

fun List<LocalTime>.toCsv(): String? = takeIf { it.isNotEmpty() }?.joinToString(",") { it.toString() }

fun String?.parseTimesOfDayCsv(): List<LocalTime> =
    this?.split(",")?.filter { it.isNotBlank() }?.map { LocalTime.parse(it.trim()) } ?: emptyList()

/** Bridges the Room entity to the engine's pure-Kotlin scheduling type (engine has no Android/Room imports). */
fun TaskEntity.toSchedule(): TaskSchedule = TaskSchedule(
    type = type,
    dueLocalDate = dueLocalDate?.let(LocalDate::parse),
    dueLocalTime = dueLocalTime?.let(LocalTime::parse),
    notBefore = notBefore?.let(LocalDate::parse),
    recFreq = recFreq,
    recInterval = recInterval,
    recAnchor = recAnchor,
    recDaysOfWeek = recDaysOfWeek.parseDaysOfWeekCsv(),
    recDayOfMonth = recDayOfMonth,
    recTimesOfDay = recTimesOfDay.parseTimesOfDayCsv(),
    recEndDate = recEndDate?.let(LocalDate::parse),
    nagIntervalHours = nagIntervalHours,
    stockQty = stockQty,
    dosePerIntake = dosePerIntake,
    restockLeadDays = restockLeadDays,
    stockRecordedAt = stockRecordedAt?.let(LocalDate::parse),
    meterInterval = meterInterval,
    lastDoneMeter = lastDoneMeter
)
