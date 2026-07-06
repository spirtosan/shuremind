package com.shuremind.system

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import com.shuremind.data.repo.BackupFileNaming
import com.shuremind.data.repo.BackupFileWriter
import java.io.File
import java.time.ZonedDateTime

/**
 * D-32: writes [com.shuremind.data.backup.ExportEngine] output to a SAF folder or a single
 * resolved document Uri, using raw [DocumentsContract] calls (D-16: no androidx.documentfile
 * dependency). Also used for "Back up now" and the D-31 pre-wipe safety export.
 */
class BackupManager(private val context: Context) : BackupFileWriter {

    override suspend fun writeToFolder(folderUri: String, json: String): Result<String> = runCatching {
        val treeUri = folderUri.toUri()
        val fileName = BackupFileNaming.fileName(ZonedDateTime.now())
        val treeDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        val newFileUri = DocumentsContract.createDocument(context.contentResolver, treeDocUri, MIME_TYPE, fileName)
            ?: error("Could not create the backup file in the selected folder")
        context.contentResolver.openOutputStream(newFileUri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            ?: error("Could not open the new backup file for writing")
        pruneFolder(treeUri)
        fileName
    }

    override suspend fun writeSafetyExport(folderUri: String?, json: String): Result<Unit> {
        if (folderUri != null && hasPersistedPermission(folderUri)) {
            val result = writeToFolder(folderUri, json)
            if (result.isSuccess) return Result.success(Unit)
        }
        return writeToCacheDir(json)
    }

    override suspend fun writeToDocument(documentUri: String, json: String): Result<Unit> = runCatching {
        context.contentResolver.openOutputStream(documentUri.toUri())?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            ?: error("Could not open the destination file for writing")
    }

    override suspend fun readDocument(documentUri: String): Result<String> = runCatching {
        context.contentResolver.openInputStream(documentUri.toUri())?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("Could not open the selected file for reading")
    }

    override fun hasPersistedPermission(folderUri: String): Boolean {
        val uri = folderUri.toUri()
        return context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }
    }

    private fun writeToCacheDir(json: String): Result<Unit> = runCatching {
        val fileName = BackupFileNaming.fileName(ZonedDateTime.now())
        File(context.cacheDir, fileName).writeText(json, Charsets.UTF_8)
    }

    private fun pruneFolder(treeUri: Uri) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        val entries = mutableListOf<Pair<String, Uri>>()
        context.contentResolver.query(
            childrenUri,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val docId = cursor.getString(idIndex)
                val name = cursor.getString(nameIndex)
                entries += name to DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            }
        }
        val toDelete = BackupRetention.namesToDelete(entries.map { it.first })
        entries.filter { it.first in toDelete }.forEach { (_, uri) ->
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
        }
    }

    companion object {
        private const val MIME_TYPE = "application/json"

        /** Best-effort human-readable folder name for the Settings "Backup folder" row. */
        fun folderDisplayName(context: Context, folderUri: String): String? = runCatching {
            val treeUri = folderUri.toUri()
            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
            context.contentResolver.query(docUri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
    }
}
