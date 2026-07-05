package com.shuremind.data.repo

import com.shuremind.data.entity.TagEntity
import kotlinx.coroutines.flow.Flow

/** UI/ViewModels talk only to this repo, never TagDao/TaskTagDao directly (from M2 on). Fakeable for ViewModel unit tests. */
interface TagRepository {

    fun observeAll(): Flow<List<TagEntity>>

    /** Task -> its tags, kept live so list/filter UI updates as tags are (un)assigned. */
    fun observeTaskTagMap(): Flow<Map<String, List<TagEntity>>>

    /** Tag picker is create-on-type: normalizes to lowercase, reuses an existing tag by name. */
    suspend fun getOrCreate(name: String): TagEntity

    suspend fun getTagIdsForTask(taskId: String): List<String>

    suspend fun getTagsForTask(taskId: String): List<TagEntity>

    suspend fun setTagsForTask(taskId: String, tagIds: Set<String>)
}
