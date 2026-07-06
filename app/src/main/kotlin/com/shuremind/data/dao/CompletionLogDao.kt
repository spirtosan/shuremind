package com.shuremind.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.shuremind.data.entity.CompletionLogEntity
import com.shuremind.engine.CompletionAction
import kotlinx.coroutines.flow.Flow

@Dao
internal interface CompletionLogDao {

    // Append-only per DATA_MODEL.md: no update/delete methods in normal app operation.
    @Insert
    suspend fun insert(entry: CompletionLogEntity)

    @Insert
    suspend fun insertAll(entries: List<CompletionLogEntity>)

    @Query("SELECT * FROM completion_log WHERE task_id = :taskId ORDER BY completed_at DESC")
    fun observeForTask(taskId: String): Flow<List<CompletionLogEntity>>

    @Query("SELECT * FROM completion_log WHERE task_id = :taskId AND action = :action ORDER BY completed_at DESC LIMIT 1")
    suspend fun getLastByAction(taskId: String, action: CompletionAction = CompletionAction.DONE): CompletionLogEntity?

    /** M5 export: every row (DATA_MODEL.md full-fidelity). */
    @Query("SELECT * FROM completion_log")
    suspend fun getAllForExport(): List<CompletionLogEntity>

    /** M5 import only (D-31 replace-all) — the only place this append-only log is ever wiped. */
    @Query("DELETE FROM completion_log")
    suspend fun deleteAll()
}
