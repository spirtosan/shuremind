package com.shuremind.data.backup

import com.shuremind.data.backup.dto.CompletionLogDto
import com.shuremind.data.backup.dto.MeterReadingDto
import com.shuremind.data.backup.dto.ReminderRuleDto
import com.shuremind.data.backup.dto.TagDto
import com.shuremind.data.backup.dto.TaskDto
import com.shuremind.data.backup.dto.TaskTagDto
import com.shuremind.data.entity.CompletionLogEntity
import com.shuremind.data.entity.MeterReadingEntity
import com.shuremind.data.entity.ReminderRuleEntity
import com.shuremind.data.entity.TagEntity
import com.shuremind.data.entity.TaskEntity
import com.shuremind.data.entity.TaskTagEntity

/** Entity <-> DTO mapping for export/import (M5). Pure, no Room/Android calls — full column fidelity. */

fun TaskEntity.toDto(): TaskDto = TaskDto(
    id = id,
    title = title,
    notes = notes,
    type = type,
    status = status,
    impact = impact,
    urgency = urgency,
    estimatedCost = estimatedCost,
    dueLocalDate = dueLocalDate,
    dueLocalTime = dueLocalTime,
    notBefore = notBefore,
    recFreq = recFreq,
    recInterval = recInterval,
    recAnchor = recAnchor,
    recDaysOfWeek = recDaysOfWeek,
    recDayOfMonth = recDayOfMonth,
    recTimesOfDay = recTimesOfDay,
    recEndDate = recEndDate,
    nagIntervalHours = nagIntervalHours,
    stockQty = stockQty,
    dosePerIntake = dosePerIntake,
    restockLeadDays = restockLeadDays,
    stockRecordedAt = stockRecordedAt,
    meterName = meterName,
    meterInterval = meterInterval,
    lastDoneMeter = lastDoneMeter,
    windowHint = windowHint,
    snoozedUntil = snoozedUntil,
    nextFireAt = nextFireAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    dirty = dirty
)

fun TaskDto.toEntity(): TaskEntity = TaskEntity(
    id = id,
    title = title,
    notes = notes,
    type = type,
    status = status,
    impact = impact,
    urgency = urgency,
    estimatedCost = estimatedCost,
    dueLocalDate = dueLocalDate,
    dueLocalTime = dueLocalTime,
    notBefore = notBefore,
    recFreq = recFreq,
    recInterval = recInterval,
    recAnchor = recAnchor,
    recDaysOfWeek = recDaysOfWeek,
    recDayOfMonth = recDayOfMonth,
    recTimesOfDay = recTimesOfDay,
    recEndDate = recEndDate,
    nagIntervalHours = nagIntervalHours,
    stockQty = stockQty,
    dosePerIntake = dosePerIntake,
    restockLeadDays = restockLeadDays,
    stockRecordedAt = stockRecordedAt,
    meterName = meterName,
    meterInterval = meterInterval,
    lastDoneMeter = lastDoneMeter,
    windowHint = windowHint,
    snoozedUntil = snoozedUntil,
    nextFireAt = nextFireAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    dirty = dirty
)

fun ReminderRuleEntity.toDto(): ReminderRuleDto = ReminderRuleDto(id = id, taskId = taskId, offsetIso = offsetIso)

fun ReminderRuleDto.toEntity(): ReminderRuleEntity = ReminderRuleEntity(id = id, taskId = taskId, offsetIso = offsetIso)

fun TagEntity.toDto(): TagDto = TagDto(id = id, name = name, color = color)

fun TagDto.toEntity(): TagEntity = TagEntity(id = id, name = name, color = color)

fun TaskTagEntity.toDto(): TaskTagDto = TaskTagDto(taskId = taskId, tagId = tagId)

fun TaskTagDto.toEntity(): TaskTagEntity = TaskTagEntity(taskId = taskId, tagId = tagId)

fun CompletionLogEntity.toDto(): CompletionLogDto = CompletionLogDto(
    id = id,
    taskId = taskId,
    occurrenceLocal = occurrenceLocal,
    action = action,
    completedAt = completedAt,
    meterValue = meterValue,
    note = note
)

fun CompletionLogDto.toEntity(): CompletionLogEntity = CompletionLogEntity(
    id = id,
    taskId = taskId,
    occurrenceLocal = occurrenceLocal,
    action = action,
    completedAt = completedAt,
    meterValue = meterValue,
    note = note
)

fun MeterReadingEntity.toDto(): MeterReadingDto = MeterReadingDto(id = id, meterName = meterName, value = value, recordedAt = recordedAt)

fun MeterReadingDto.toEntity(): MeterReadingEntity = MeterReadingEntity(id = id, meterName = meterName, value = value, recordedAt = recordedAt)
