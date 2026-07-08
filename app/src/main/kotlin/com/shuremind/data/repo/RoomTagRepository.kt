package com.shuremind.data.repo

import com.shuremind.data.dao.TagDao
import com.shuremind.data.dao.TaskTagDao
import com.shuremind.data.entity.TagEntity
import com.shuremind.data.entity.TaskTagEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.util.UUID

internal class RoomTagRepository(
    private val tagDao: TagDao,
    private val taskTagDao: TaskTagDao,
    private val transactionRunner: TransactionRunner
) : TagRepository {

    override fun observeAll(): Flow<List<TagEntity>> = tagDao.observeAll()

    override fun observeTaskTagMap(): Flow<Map<String, List<TagEntity>>> =
        combine(tagDao.observeAll(), taskTagDao.observeAll()) { tags, crossRefs ->
            val tagsById = tags.associateBy { it.id }
            crossRefs.groupBy({ it.taskId }, { it.tagId })
                .mapValues { (_, tagIds) -> tagIds.mapNotNull { tagsById[it] } }
        }

    override suspend fun getOrCreate(name: String): TagEntity {
        val normalized = name.trim().lowercase()
        tagDao.getByName(normalized)?.let { return it }
        val tag = TagEntity(id = UUID.randomUUID().toString(), name = normalized, color = null)
        tagDao.upsert(tag)
        return tag
    }

    override suspend fun getTagIdsForTask(taskId: String): List<String> = taskTagDao.getTagIdsForTask(taskId)

    override suspend fun getTagsForTask(taskId: String): List<TagEntity> {
        val ids = taskTagDao.getTagIdsForTask(taskId).toSet()
        if (ids.isEmpty()) return emptyList()
        return observeAll().first().filter { it.id in ids }
    }

    override suspend fun setTagsForTask(taskId: String, tagIds: Set<String>) {
        val current = taskTagDao.getTagIdsForTask(taskId).toSet()
        (tagIds - current).forEach { taskTagDao.upsert(TaskTagEntity(taskId, it)) }
        (current - tagIds).forEach { taskTagDao.delete(TaskTagEntity(taskId, it)) }
    }

    /** D-41: explicit transaction rather than relying solely on the DB's FK cascade, so this is exercised by plain JVM unit tests against fake DAOs (Room's FK enforcement isn't simulated by the fakes). */
    override suspend fun deleteTag(tagId: String) {
        transactionRunner.runInTransaction {
            taskTagDao.deleteForTag(tagId)
            tagDao.deleteById(tagId)
        }
    }
}
