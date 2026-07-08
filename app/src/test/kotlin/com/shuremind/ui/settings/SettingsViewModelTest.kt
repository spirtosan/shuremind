package com.shuremind.ui.settings

import com.shuremind.data.entity.TagEntity
import com.shuremind.data.repo.LanguageRepository
import com.shuremind.testutil.FakeBackupFileWriter
import com.shuremind.testutil.FakeBackupRepository
import com.shuremind.testutil.FakeBackupSettingsRepository
import com.shuremind.testutil.FakeImportRepository
import com.shuremind.testutil.FakeSettingsRepository
import com.shuremind.testutil.FakeTagRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** D-32 first-enable flow: [SettingsViewModel.onFolderPicked] is the single place the folder-pick + toggle rule lives, so it's tested here directly rather than through the Compose UI. */
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        backupSettingsRepository: FakeBackupSettingsRepository,
        tagRepository: FakeTagRepository = FakeTagRepository()
    ) = SettingsViewModel(
        settingsRepository = FakeSettingsRepository(),
        languageRepository = LanguageRepository(),
        backupSettingsRepository = backupSettingsRepository,
        backupFileWriter = FakeBackupFileWriter(),
        backupRepository = FakeBackupRepository(),
        importRepository = FakeImportRepository(),
        tagRepository = tagRepository,
        appVersion = "test"
    )

    @Test
    fun `picking a folder from the toggle's first-enable flow also enables auto-backup`() = runTest(dispatcher) {
        val backupSettingsRepository = FakeBackupSettingsRepository()
        val viewModel = viewModel(backupSettingsRepository)

        viewModel.onFolderPicked("content://tree/abc", enableAfterPick = true)
        dispatcher.scheduler.advanceUntilIdle()

        val current = backupSettingsRepository.settings.first()
        assertEquals("content://tree/abc", current.folderUri)
        assertTrue(current.autoBackupEnabled)
    }

    @Test
    fun `picking a folder from the backup folder row does not change the toggle`() = runTest(dispatcher) {
        val backupSettingsRepository = FakeBackupSettingsRepository()
        val viewModel = viewModel(backupSettingsRepository)

        viewModel.onFolderPicked("content://tree/abc", enableAfterPick = false)
        dispatcher.scheduler.advanceUntilIdle()

        val current = backupSettingsRepository.settings.first()
        assertEquals("content://tree/abc", current.folderUri)
        assertFalse(current.autoBackupEnabled)
    }

    /** D-41: deleting a tag from Settings goes through the repository and drops out of the observed list. */
    @Test
    fun `deleteTag removes the tag from the observed tag list`() = runTest(dispatcher) {
        val tagRepository = FakeTagRepository().apply { seedTag(TagEntity(id = "tag-shop", name = "shop", color = null)) }
        val viewModel = viewModel(FakeBackupSettingsRepository(), tagRepository)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("shop"), viewModel.tags.value.map { it.name })

        viewModel.deleteTag("tag-shop")
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.tags.value.isEmpty())
    }
}
