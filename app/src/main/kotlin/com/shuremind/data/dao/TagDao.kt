package com.shuremind.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.shuremind.data.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
internal interface TagDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(tag: TagEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tags: List<TagEntity>)

    @Query("SELECT * FROM tags ORDER BY name")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE name = :name")
    suspend fun getByName(name: String): TagEntity?

    /** M6 part 1.5 (D-41): single-tag delete; task_tags rows are removed separately in the same transaction. */
    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun deleteById(id: String)

    /** M5 import (D-31 replace-all). Tags have no soft delete, so observeAll() already covers export. */
    @Query("DELETE FROM tags")
    suspend fun deleteAll()
}
