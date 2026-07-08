package com.shuremind.data.repo

import com.shuremind.data.entity.TagEntity
import com.shuremind.data.entity.TaskTagEntity
import com.shuremind.testutil.FakeTagDao
import com.shuremind.testutil.FakeTaskTagDao
import com.shuremind.testutil.NoOpTransactionRunner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** D-41: deleting a tag must remove it and every TaskTag row referencing it, leaving other tags/tasks untouched. */
class RoomTagRepositoryTest {

    @Test
    fun `deleteTag removes the tag row and all its task_tags cross-refs`() = runTest {
        val shop = TagEntity(id = "tag-shop", name = "shop", color = null)
        val home = TagEntity(id = "tag-home", name = "home", color = null)
        val tagDao = FakeTagDao(listOf(shop, home))
        val taskTagDao = FakeTaskTagDao(
            listOf(
                TaskTagEntity(taskId = "t1", tagId = "tag-shop"),
                TaskTagEntity(taskId = "t2", tagId = "tag-shop"),
                TaskTagEntity(taskId = "t1", tagId = "tag-home")
            )
        )
        val repository = RoomTagRepository(tagDao, taskTagDao, NoOpTransactionRunner)

        repository.deleteTag("tag-shop")

        assertEquals(listOf("home"), tagDao.observeAll().first().map { it.name })
        assertTrue(taskTagDao.observeAll().first().none { it.tagId == "tag-shop" })
        assertEquals(listOf("tag-home"), taskTagDao.getTagIdsForTask("t1"))
        assertTrue(taskTagDao.getTagIdsForTask("t2").isEmpty())
    }
}
