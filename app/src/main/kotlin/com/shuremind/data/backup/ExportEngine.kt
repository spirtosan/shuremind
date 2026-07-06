package com.shuremind.data.backup

import com.shuremind.data.backup.dto.ExportFile
import kotlinx.serialization.json.Json

/** Pure Kotlin: [BackupSnapshot] -> [ExportFile] -> JSON string (DATA_MODEL.md "Export JSON", D-11). */
object ExportEngine {

    const val SCHEMA_VERSION = 1

    private val json = Json {
        prettyPrint = false
        encodeDefaults = true
    }

    fun buildExportFile(snapshot: BackupSnapshot, appVersion: String, exportedAt: Long): ExportFile = ExportFile(
        schemaVersion = SCHEMA_VERSION,
        appVersion = appVersion,
        exportedAt = exportedAt,
        tasks = snapshot.tasks.map { it.toDto() },
        reminderRules = snapshot.reminderRules.map { it.toDto() },
        tags = snapshot.tags.map { it.toDto() },
        taskTags = snapshot.taskTags.map { it.toDto() },
        completions = snapshot.completions.map { it.toDto() },
        meterReadings = snapshot.meterReadings.map { it.toDto() },
        settings = snapshot.settings
    )

    fun toJson(file: ExportFile): String = json.encodeToString(ExportFile.serializer(), file)
}
