package com.shuremind.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.shuremind.data.entity.CompletionLogEntity
import com.shuremind.engine.CompletionAction
import kotlinx.coroutines.flow.Flow

@Dao
interface CompletionLogDao {

    // Append-only per DATA_MODEL.md: no update/delete methods.
    @Insert
    suspend fun insert(entry: CompletionLogEntity)

    @Query("SELECT * FROM completion_log WHERE task_id = :taskId ORDER BY completed_at DESC")
    fun observeForTask(taskId: String): Flow<List<CompletionLogEntity>>

    @Query("SELECT * FROM completion_log WHERE task_id = :taskId AND action = :action ORDER BY completed_at DESC LIMIT 1")
    suspend fun getLastByAction(taskId: String, action: CompletionAction = CompletionAction.DONE): CompletionLogEntity?
}
