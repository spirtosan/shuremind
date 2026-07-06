package com.shuremind.data.repo

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * D-32: SAF-backed file I/O for backup/export/import. Defined here (not in `system`) so `ui`
 * ViewModels can depend on it without importing the `system` layer directly — AppContainer wires
 * the real Android/SAF implementation ([com.shuremind.system.BackupManager]).
 */
interface BackupFileWriter {

    /** Creates a new dated file under the SAF tree [folderUri], writes [json], prunes per D-32. Returns the file name written. */
    suspend fun writeToFolder(folderUri: String, json: String): Result<String>

    /** D-31 safety export: tries [folderUri] (if set and still permitted), else falls back to app-private cache storage. */
    suspend fun writeSafetyExport(folderUri: String?, json: String): Result<Unit>

    /** Writes [json] to an already-resolved destination (ACTION_CREATE_DOCUMENT result) for manual "Export data". */
    suspend fun writeToDocument(documentUri: String, json: String): Result<Unit>

    /** Reads the full text content of [documentUri] (ACTION_OPEN_DOCUMENT result) for "Import data". */
    suspend fun readDocument(documentUri: String): Result<String>

    /** True if [folderUri]'s persisted SAF write permission is still held (D-32: cancel auto-backup if revoked). */
    fun hasPersistedPermission(folderUri: String): Boolean
}

/** D-32 file naming: shuremind-backup-yyyyMMdd-HHmmss.json. Pure — shared by [BackupFileWriter] impls and the UI's suggested export name. */
object BackupFileNaming {
    private val FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    fun fileName(now: ZonedDateTime = ZonedDateTime.now()): String = "shuremind-backup-${FORMATTER.format(now)}.json"
}
