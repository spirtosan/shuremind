package com.shuremind.ui.main

import com.shuremind.data.entity.MeterReadingEntity
import com.shuremind.engine.TaskStatus
import com.shuremind.engine.TaskType
import com.shuremind.testutil.FakeCompletionRepository
import com.shuremind.testutil.FakeSettingsRepository
import com.shuremind.testutil.FakeTagRepository
import com.shuremind.testutil.FakeTaskRepository
import com.shuremind.testutil.fakeMeterRepository
import com.shuremind.testutil.fixtureTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class MainViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val zone: ZoneId = ZoneOffset.UTC
    private val now: Long = ZonedDateTime.of(2026, 7, 5, 10, 0, 0, 0, zone).toInstant().toEpochMilli()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        tasks: List<com.shuremind.data.entity.TaskEntity> = emptyList(),
        taskRepository: FakeTaskRepository = FakeTaskRepository(tasks),
        tagRepository: FakeTagRepository = FakeTagRepository(),
        meterReadings: List<MeterReadingEntity> = emptyList()
    ) = MainViewModel(
        taskRepository = taskRepository,
        completionRepository = FakeCompletionRepository(),
        tagRepository = tagRepository,
        settingsRepository = FakeSettingsRepository(),
        meterRepository = fakeMeterRepository(meterReadings),
        zone = zone,
        nowProvider = { now }
    )

    @Test
    fun `sections tasks into overdue, today, upcoming and someday by next_fire_at`() = runTest(dispatcher) {
        val overdue = fixtureTask("overdue", type = TaskType.DEADLINE, nextFireAt = now - 3_600_000, impact = 2, urgency = 2)
        val today = fixtureTask("today", type = TaskType.EVENT, nextFireAt = now + 3_600_000, impact = 1, urgency = 1)
        val upcoming = fixtureTask("upcoming", type = TaskType.EVENT, nextFireAt = now + 3 * 86_400_000L, impact = 1, urgency = 1)
        val somedayByType = fixtureTask("someday-type", type = TaskType.SOMEDAY)
        val unscored = fixtureTask("unscored", type = TaskType.EVENT, nextFireAt = null)

        val vm = viewModel(tasks = listOf(overdue, today, upcoming, somedayByType, unscored))
        dispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(listOf("overdue"), state.overdue.map { it.task.id })
        assertEquals(listOf("today"), state.today.map { it.task.id })
        assertEquals(listOf("upcoming"), state.upcoming.map { it.task.id })
        assertEquals(setOf("someday-type", "unscored"), state.someday.map { it.task.id }.toSet())
        assertNull(state.someday.first().score)
        assertTrue(state.overdue.first().score!! > 0)
    }

    @Test
    fun `sorts each section by score descending, ties by earlier due date`() = runTest(dispatcher) {
        val highScore = fixtureTask("high", type = TaskType.DEADLINE, nextFireAt = now + 3_600_000, impact = 3, urgency = 3)
        val lowScore = fixtureTask("low", type = TaskType.EVENT, nextFireAt = now + 7_200_000, impact = 0, urgency = 0)

        val vm = viewModel(tasks = listOf(lowScore, highScore))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("high", "low"), vm.uiState.value.today.map { it.task.id })
    }

    @Test
    fun `excludes archived and soft-deleted tasks`() = runTest(dispatcher) {
        val archived = fixtureTask("archived", status = TaskStatus.ARCHIVED, nextFireAt = now + 1_000)
        val deleted = fixtureTask("deleted", nextFireAt = now + 1_000, deletedAt = now)
        val visible = fixtureTask("visible", nextFireAt = now + 1_000)

        val vm = viewModel(tasks = listOf(archived, deleted, visible))
        dispatcher.scheduler.advanceUntilIdle()

        val allIds = (vm.uiState.value.overdue + vm.uiState.value.today + vm.uiState.value.upcoming + vm.uiState.value.someday)
            .map { it.task.id }
        assertEquals(listOf("visible"), allIds)
    }

    @Test
    fun `tag filter shows only tasks carrying the selected tag, All clears it`() = runTest(dispatcher) {
        val shopTag = com.shuremind.data.entity.TagEntity(id = "tag-shop", name = "shop", color = null)
        val tagged = fixtureTask("eggs", nextFireAt = now + 1_000)
        val untagged = fixtureTask("other", nextFireAt = now + 1_000)
        val tagRepository = FakeTagRepository().apply { seedTag(shopTag, taskIds = listOf("eggs")) }

        val vm = viewModel(tasks = listOf(tagged, untagged), tagRepository = tagRepository)
        dispatcher.scheduler.advanceUntilIdle()

        vm.selectTag("tag-shop")
        dispatcher.scheduler.advanceUntilIdle()
        val filteredIds = (vm.uiState.value.overdue + vm.uiState.value.today + vm.uiState.value.upcoming + vm.uiState.value.someday)
            .map { it.task.id }
        assertEquals(listOf("eggs"), filteredIds)

        vm.selectTag(null)
        dispatcher.scheduler.advanceUntilIdle()
        val clearedIds = (vm.uiState.value.overdue + vm.uiState.value.today + vm.uiState.value.upcoming + vm.uiState.value.someday)
            .map { it.task.id }
            .toSet()
        assertEquals(setOf("eggs", "other"), clearedIds)
    }

    @Test
    fun `capture with blank title cannot be saved and does not create a task`() = runTest(dispatcher) {
        val taskRepository = FakeTaskRepository()
        val vm = viewModel(taskRepository = taskRepository)
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.capture.value.canSave)
        vm.saveCapture()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(taskRepository.tasks.isEmpty())
    }

    @Test
    fun `plain capture with just a title creates an active EVENT task with defaults`() = runTest(dispatcher) {
        val taskRepository = FakeTaskRepository()
        val vm = viewModel(taskRepository = taskRepository)
        dispatcher.scheduler.advanceUntilIdle()

        vm.setCaptureTitle("Buy eggs")
        vm.saveCapture()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, taskRepository.tasks.size)
        val saved = taskRepository.tasks.single()
        assertEquals("Buy eggs", saved.title)
        assertEquals(TaskType.EVENT, saved.type)
        assertEquals(TaskStatus.ACTIVE, saved.status)
        assertEquals(1, saved.impact)
        assertEquals(1, saved.urgency)
        assertNull(saved.dueLocalDate)
        // Title should reset for the next capture.
        assertEquals("", vm.capture.value.title)
    }

    // --- M6 part 1.5: quick-capture notes + tag toggle chips ---

    @Test
    fun `capture notes are saved trimmed, blank notes are stored as null`() = runTest(dispatcher) {
        val taskRepository = FakeTaskRepository()
        val vm = viewModel(taskRepository = taskRepository)
        dispatcher.scheduler.advanceUntilIdle()

        vm.setCaptureTitle("Buy eggs")
        vm.setCaptureNotes("  get the free-range ones  ")
        vm.saveCapture()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("get the free-range ones", taskRepository.tasks.single().notes)

        vm.setCaptureTitle("Another task")
        vm.setCaptureNotes("   ")
        vm.saveCapture()
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(taskRepository.tasks.single { it.title == "Another task" }.notes)
    }

    @Test
    fun `toggling an existing tag chip adds it, toggling again removes it`() = runTest(dispatcher) {
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        vm.toggleCaptureTag("shop")
        assertEquals(listOf("shop"), vm.capture.value.tags)

        vm.toggleCaptureTag("shop")
        assertTrue(vm.capture.value.tags.isEmpty())
    }

    // --- D-42: per-task alarm mode ---

    @Test
    fun `capture alarm mode defaults off and is saved on the created task when turned on`() = runTest(dispatcher) {
        val taskRepository = FakeTaskRepository()
        val vm = viewModel(taskRepository = taskRepository)
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.capture.value.alarmMode)

        vm.setCaptureTitle("Take medicine")
        vm.setCaptureAlarmMode(true)
        vm.saveCapture()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(taskRepository.tasks.single().alarmMode)
    }

    @Test
    fun `snooze only writes snoozed_until on the task repository`() = runTest(dispatcher) {
        val task = fixtureTask("water-flowers", nextFireAt = now)
        val taskRepository = FakeTaskRepository(listOf(task))
        val vm = viewModel(tasks = listOf(task), taskRepository = taskRepository)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onSnooze(task, presetMinutes = 60)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("water-flowers", taskRepository.lastSnoozedTaskId)
        assertEquals(now + 60 * 60_000L, taskRepository.lastSnoozeUntil)
        val updated = taskRepository.tasks.single { it.id == "water-flowers" }
        assertEquals(now + 60 * 60_000L, updated.snoozedUntil)
        // Snooze must not touch status or next_fire_at (alarms are M3).
        assertEquals(TaskStatus.ACTIVE, updated.status)
        assertEquals(now, updated.nextFireAt)
    }

    // --- D-27: meter-due (OR-logic) surfacing ---

    @Test
    fun `meter-due task surfaces in overdue even though its own time rule is not yet due`() = runTest(dispatcher) {
        val carOil = fixtureTask(
            "car-oil",
            type = TaskType.RECURRING,
            nextFireAt = now + 30L * 86_400_000L, // 30 days out by time — not due yet
            meterName = "car",
            meterInterval = 10_000.0,
            lastDoneMeter = 40_000.0
        )
        val reading = MeterReadingEntity(id = "r1", meterName = "car", value = 50_500.0, recordedAt = now) // crossed 10,000 km
        val vm = viewModel(tasks = listOf(carOil), meterReadings = listOf(reading))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("car-oil"), vm.uiState.value.overdue.map { it.task.id })
        assertTrue(vm.uiState.value.today.isEmpty())
        assertTrue(vm.uiState.value.upcoming.isEmpty())
    }

    @Test
    fun `meter task below threshold is not forced into overdue`() = runTest(dispatcher) {
        val carOil = fixtureTask(
            "car-oil",
            type = TaskType.RECURRING,
            nextFireAt = now + 30L * 86_400_000L,
            meterName = "car",
            meterInterval = 10_000.0,
            lastDoneMeter = 40_000.0
        )
        val reading = MeterReadingEntity(id = "r1", meterName = "car", value = 45_000.0, recordedAt = now) // only 5,000 km since last done
        val vm = viewModel(tasks = listOf(carOil), meterReadings = listOf(reading))
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.overdue.isEmpty())
        assertEquals(listOf("car-oil"), vm.uiState.value.upcoming.map { it.task.id })
    }
}
