package com.shuremind.ui.detail

import com.shuremind.data.entity.TaskEntity
import com.shuremind.engine.RecurrenceFrequency
import com.shuremind.engine.TaskStatus
import com.shuremind.engine.TaskType
import com.shuremind.testutil.FakeCompletionRepository
import com.shuremind.testutil.FakeReminderRuleRepository
import com.shuremind.testutil.FakeSettingsRepository
import com.shuremind.testutil.FakeTagRepository
import com.shuremind.testutil.FakeTaskRepository
import com.shuremind.testutil.FakeWindowConversionRepository
import com.shuremind.testutil.fixtureTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class TaskDetailViewModelTest {

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

    private fun windowTask(id: String = "residence") = TaskEntity(
        id = id,
        title = "Residence declaration",
        notes = null,
        type = TaskType.WINDOW,
        status = TaskStatus.ACTIVE,
        impact = 2,
        urgency = 1,
        estimatedCost = null,
        dueLocalDate = null,
        dueLocalTime = null,
        notBefore = null,
        recFreq = RecurrenceFrequency.MONTHLY,
        recInterval = 1,
        recAnchor = null,
        recDaysOfWeek = null,
        recDayOfMonth = 1,
        recTimesOfDay = null,
        recEndDate = null,
        nagIntervalHours = null,
        stockQty = null,
        dosePerIntake = null,
        restockLeadDays = null,
        stockRecordedAt = null,
        meterName = null,
        meterInterval = null,
        lastDoneMeter = null,
        windowHint = "usually Sep-Nov",
        snoozedUntil = null,
        nextFireAt = now - 86_400_000L, // stale check-cadence instant, pre-conversion
        createdAt = now - 30 * 86_400_000L,
        updatedAt = now - 30 * 86_400_000L,
        deletedAt = null,
        dirty = 1
    )

    private fun viewModel(
        taskRepository: FakeTaskRepository,
        reminderRuleRepository: FakeReminderRuleRepository = FakeReminderRuleRepository()
    ) = TaskDetailViewModel(
        taskRepository = taskRepository,
        tagRepository = FakeTagRepository(),
        reminderRuleRepository = reminderRuleRepository,
        completionRepository = FakeCompletionRepository(),
        settingsRepository = FakeSettingsRepository(),
        windowConversionRepository = FakeWindowConversionRepository(taskRepository, reminderRuleRepository, zone),
        zone = zone,
        nowProvider = { now }
    )

    // --- D-25: WINDOW -> DEADLINE conversion ---

    @Test
    fun `converting a WINDOW task to DEADLINE clears recurrence and stores the learned date`() = runTest(dispatcher) {
        val taskRepository = FakeTaskRepository(listOf(windowTask()))
        val vm = viewModel(taskRepository)

        vm.load("residence")
        dispatcher.scheduler.advanceUntilIdle()
        vm.setDateLearnedDate("2026-09-15")
        vm.convertWindowToDeadline()
        dispatcher.scheduler.advanceUntilIdle()

        val updated = taskRepository.getById("residence")!!
        assertEquals(TaskType.DEADLINE, updated.type)
        assertEquals("2026-09-15", updated.dueLocalDate)
        assertNull(updated.recFreq)
        assertNull(updated.recAnchor)
        assertNull(updated.recDaysOfWeek)
        assertNull(updated.recDayOfMonth)
        assertNull(updated.recTimesOfDay)
        assertNull(updated.recEndDate)
        assertNull(updated.windowHint)
    }

    @Test
    fun `conversion replaces reminder rules with the default DEADLINE offsets`() = runTest(dispatcher) {
        val taskRepository = FakeTaskRepository(listOf(windowTask()))
        val reminderRuleRepository = FakeReminderRuleRepository(initial = mapOf("residence" to listOf("P30D")))
        val vm = viewModel(taskRepository, reminderRuleRepository)

        vm.load("residence")
        dispatcher.scheduler.advanceUntilIdle()
        vm.setDateLearnedDate("2026-09-15")
        vm.convertWindowToDeadline()
        dispatcher.scheduler.advanceUntilIdle()

        val offsets = reminderRuleRepository.getForTask("residence").map { it.offsetIso }
        assertEquals(listOf("P14D", "P7D", "P1D"), offsets) // AppSettings.DEFAULT_REMINDER_OFFSETS[DEADLINE]
    }

    @Test
    fun `next fire instant switches from check-cadence to the lead-time reminder after conversion`() = runTest(dispatcher) {
        val taskRepository = FakeTaskRepository(listOf(windowTask()))
        val vm = viewModel(taskRepository)

        vm.load("residence")
        dispatcher.scheduler.advanceUntilIdle()
        vm.setDateLearnedDate("2026-09-15")
        vm.convertWindowToDeadline()
        dispatcher.scheduler.advanceUntilIdle()

        val updated = taskRepository.getById("residence")!!
        assertNotNull(updated.nextFireAt)
        // The DEADLINE's own occurrence is the due date itself; next_fire_at must land on/after it,
        // not on the old monthly check-cadence date (which was in the past relative to `now`).
        assertTrue(updated.nextFireAt!! >= ZonedDateTime.of(2026, 9, 15, 0, 0, 0, 0, zone).toInstant().toEpochMilli())
    }

    // --- D-26: CONSUMABLE restock ---

    private fun consumableTask(id: String = "meds") = fixtureTask(
        id = id,
        type = TaskType.CONSUMABLE,
        createdAt = ZonedDateTime.of(2026, 6, 1, 0, 0, 0, 0, zone).toInstant().toEpochMilli()
    ).copy(
        stockQty = 4.0,
        dosePerIntake = 1.0,
        restockLeadDays = 5,
        stockRecordedAt = "2026-06-01",
        recTimesOfDay = "08:00,20:00" // 2 intakes/day
    )

    @Test
    fun `restock adds the bought quantity to the remaining stock and stamps today as recorded-at`() = runTest(dispatcher) {
        // 2026-06-01 -> 2026-07-05 is 34 days elapsed at 2/day = 68 consumed, well past the 4 units on hand -> remaining clamps at 0.
        val taskRepository = FakeTaskRepository(listOf(consumableTask()))
        val vm = viewModel(taskRepository)

        vm.load("meds")
        dispatcher.scheduler.advanceUntilIdle()
        vm.setBoughtQuantity("30")
        vm.confirmRestock()
        dispatcher.scheduler.advanceUntilIdle()

        val updated = taskRepository.getById("meds")!!
        assertEquals(30.0, updated.stockQty)
        assertEquals("2026-07-05", updated.stockRecordedAt)
    }
}
