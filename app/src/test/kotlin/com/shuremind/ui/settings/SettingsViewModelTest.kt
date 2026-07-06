package com.shuremind.ui.settings

import com.shuremind.data.repo.LanguageRepository
import com.shuremind.testutil.FakeBackupFileWriter
import com.shuremind.testutil.FakeBackupRepository
import com.shuremind.testutil.FakeBackupSettingsRepository
import com.shuremind.testutil.FakeImportRepository
import com.shuremind.testutil.FakeSettingsRepository
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

    private fun viewModel(backupSettingsRepository: FakeBackupSettingsRepository) = SettingsViewModel(
        settingsRepository = FakeSettingsRepository(),
        languageRepository = LanguageRepository(),
        backupSettingsRepository = backupSettingsRepository,
        backupFileWriter = FakeBackupFileWriter(),
        backupRepository = FakeBackupRepository(),
        importRepository = FakeImportRepository(),
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
}
