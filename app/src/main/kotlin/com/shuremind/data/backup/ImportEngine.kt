package com.shuremind.data.backup

import com.shuremind.data.backup.dto.ExportFile
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Pure Kotlin: JSON string -> [ExportFile], with typed validation (D-31). No Room/Android imports. */
object ImportEngine {

    private val json = Json {
        ignoreUnknownKeys = true // forward tolerance: newer exports may carry fields this version doesn't know
    }

    sealed interface ParseResult {
        data class Success(val file: ExportFile) : ParseResult
        data class UnsupportedSchemaVersion(val found: Int) : ParseResult
        data object Malformed : ParseResult
    }

    fun parse(jsonText: String): ParseResult {
        val file = try {
            json.decodeFromString(ExportFile.serializer(), jsonText)
        } catch (e: SerializationException) {
            return ParseResult.Malformed
        } catch (e: IllegalArgumentException) {
            return ParseResult.Malformed
        }
        if (file.schemaVersion != ExportEngine.SCHEMA_VERSION) {
            return ParseResult.UnsupportedSchemaVersion(file.schemaVersion)
        }
        return ParseResult.Success(file)
    }
}

data class ImportCounts(
    val tasks: Int,
    val reminderRules: Int,
    val tags: Int,
    val completions: Int,
    val meterReadings: Int
)

/** task_tags is an internal join table — not shown to the user in the D-31 confirmation dialog. */
fun ExportFile.entityCounts(): ImportCounts = ImportCounts(
    tasks = tasks.size,
    reminderRules = reminderRules.size,
    tags = tags.size,
    completions = completions.size,
    meterReadings = meterReadings.size
)
