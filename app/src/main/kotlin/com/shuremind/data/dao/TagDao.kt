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

    @Query("SELECT * FROM tags ORDER BY name")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE name = :name")
    suspend fun getByName(name: String): TagEntity?
}
