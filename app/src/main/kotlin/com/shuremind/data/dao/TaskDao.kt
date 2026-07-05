package com.shuremind.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.shuremind.data.entity.TaskEntity
import com.shuremind.engine.TaskStatus
import com.shuremind.engine.TaskType
import kotlinx.coroutines.flow.Flow

@Dao
internal interface TaskDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: TaskEntity)

    @Update
    suspend fun update(task: TaskEntity)

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE deleted_at IS NULL AND status != :excludedStatus")
    fun observeActive(excludedStatus: TaskStatus = TaskStatus.ARCHIVED): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE type = :type AND deleted_at IS NULL")
    fun observeByType(type: TaskType): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE deleted_at IS NULL AND next_fire_at IS NOT NULL ORDER BY next_fire_at ASC")
    fun observeScheduled(): Flow<List<TaskEntity>>

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun hardDelete(id: String)
}
