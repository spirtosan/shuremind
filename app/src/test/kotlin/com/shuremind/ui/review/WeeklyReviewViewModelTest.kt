package com.shuremind.ui.review

import com.shuremind.engine.TaskStatus
import com.shuremind.engine.TaskType
import com.shuremind.testutil.FakeCompletionRepository
import com.shuremind.testutil.FakeSettingsRepository
import com.shuremind.testutil.FakeTaskRepository
import com.shuremind.testutil.fixtureTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class WeeklyReviewViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val zone: ZoneId = ZoneOffset.UTC
    private val now: Long = ZonedDateTime.of(2026, 7, 5, 10, 0, 0, 0, zone).toInstant().toEpochMilli()
    private val oneDayMillis = 86_400_000L

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(taskRepository: FakeTaskRepository) = WeeklyReviewViewModel(
        taskRepository = taskRepository,
        completionRepository = FakeCompletionRepository(),
        settingsRepository = FakeSettingsRepository(),
        zone = zone,
        nowProvider = { now }
    )

    // --- Stale threshold boundary (exactly 7 days) ---

    @Test
    fun `a task overdue by exactly 7 days counts as stale`() = runTest(dispatcher) {
        val exactlySevenDays = fixtureTask("exactly-7", type = TaskType.RECURRING, nextFireAt = now - 7 * oneDayMillis)
        val vm = viewModel(FakeTaskRepository(listOf(exactlySevenDays)))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("exactly-7"), vm.uiState.value.stale.map { it.id })
    }

    @Test
    fun `a task overdue by just under 7 days is not stale`() = runTest(dispatcher) {
        val almostSevenDays = fixtureTask("almost-7", type = TaskType.RECURRING, nextFireAt = now - 7 * oneDayMillis + 1)
        val vm = viewModel(FakeTaskRepository(listOf(almostSevenDays)))
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.stale.isEmpty())
    }

    @Test
    fun `SOMEDAY tasks are never counted as stale, only listed in their own section`() = runTest(dispatcher) {
        // SOMEDAY tasks never get a next_fire_at (the engine skips them entirely — see OccurrenceEngineTest),
        // so a fixture with no nextFireAt models it faithfully here.
        val someday = fixtureTask("shower-tray", type = TaskType.SOMEDAY, nextFireAt = null)
        val vm = viewModel(FakeTaskRepository(listOf(someday)))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("shower-tray"), vm.uiState.value.someday.map { it.id })
        assertTrue(vm.uiState.value.stale.isEmpty())
    }

    @Test
    fun `all active WINDOW tasks appear in the Windows section regardless of staleness`() = runTest(dispatcher) {
        val freshWindow = fixtureTask("residence", type = TaskType.WINDOW, nextFireAt = now + oneDayMillis)
        val staleWindow = fixtureTask("stale-window", type = TaskType.WINDOW, nextFireAt = now - 30 * oneDayMillis)
        val vm = viewModel(FakeTaskRepository(listOf(freshWindow, staleWindow)))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(setOf("residence", "stale-window"), vm.uiState.value.windows.map { it.id }.toSet())
    }

    @Test
    fun `archiving is only meaningful for SOMEDAY rows and sets status to ARCHIVED`() = runTest(dispatcher) {
        val someday = fixtureTask("shower-tray", type = TaskType.SOMEDAY, nextFireAt = null)
        val taskRepository = FakeTaskRepository(listOf(someday))
        val vm = viewModel(taskRepository)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onArchive(someday)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(TaskStatus.ARCHIVED, taskRepository.getById("shower-tray")?.status)
    }
}
