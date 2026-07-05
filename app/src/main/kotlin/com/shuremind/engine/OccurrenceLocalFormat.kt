package com.shuremind.engine

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Shared 'YYYY-MM-DD[ HH:MM]' occurrence-local formatting (DATA_MODEL.md CompletionLog.occurrence_local). */
object OccurrenceLocalFormat {
    private val FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun format(instant: ZonedDateTime): String = FORMATTER.format(instant)
}
