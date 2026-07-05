package com.shuremind.data.repo

import com.shuremind.data.entity.TaskEntity
import com.shuremind.engine.OccurrenceEngine
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** Recomputes Task.next_fire_at (derived cache, D-04/D-07) via the engine. Called on create/edit/complete. */
object NextFireAtCalculator {

    fun compute(task: TaskEntity, zone: ZoneId, now: ZonedDateTime, lastDoneAt: ZonedDateTime?): Long? {
        val createdAt = Instant.ofEpochMilli(task.createdAt).atZone(zone)
        return OccurrenceEngine.nextOccurrence(task.toSchedule(), zone, now, createdAt, lastDoneAt)
            ?.toInstant()?.toEpochMilli()
    }
}
