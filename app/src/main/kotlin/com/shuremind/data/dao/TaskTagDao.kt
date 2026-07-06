package com.shuremind.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.shuremind.data.entity.TaskTagEntity
import kotlinx.coroutines.flow.Flow

@Dao
internal interface TaskTagDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(crossRef: TaskTagEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(crossRefs: List<TaskTagEntity>)

    @Delete
    suspend fun delete(crossRef: TaskTagEntity)

    @Query("SELECT tag_id FROM task_tags WHERE task_id = :taskId")
    suspend fun getTagIdsForTask(taskId: String): List<String>

    @Query("SELECT task_id FROM task_tags WHERE tag_id = :tagId")
    suspend fun getTaskIdsForTag(tagId: String): List<String>

    @Query("SELECT * FROM task_tags")
    fun observeAll(): Flow<List<TaskTagEntity>>

    /** M5 import (D-31 replace-all). observeAll() already covers export (no soft delete on this table). */
    @Query("DELETE FROM task_tags")
    suspend fun deleteAll()
}
