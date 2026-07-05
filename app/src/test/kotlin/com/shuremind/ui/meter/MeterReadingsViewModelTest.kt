package com.shuremind.ui.meter

import com.shuremind.data.entity.MeterReadingEntity
import com.shuremind.engine.RecurrenceAnchor
import com.shuremind.engine.RecurrenceFrequency
import com.shuremind.engine.TaskType
import com.shuremind.testutil.FakeMeterSeedRepository
import com.shuremind.testutil.FakeSettingsRepository
import com.shuremind.testutil.FakeTaskRepository
import com.shuremind.testutil.fakeMeterRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class MeterReadingsViewModelTest {

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
        readings: List<MeterReadingEntity> = emptyList(),
        taskRepository: FakeTaskRepository = FakeTaskRepository(),
        meterSeedRepository: FakeMeterSeedRepository = FakeMeterSeedRepository()
    ) = MeterReadingsViewModel(
        meterRepository = fakeMeterRepository(readings),
        meterSeedRepository = meterSeedRepository,
        taskRepository = taskRepository,
        settingsRepository = FakeSettingsRepository(),
        zone = zone,
        nowProvider = { now }
    )

    @Test
    fun `first-ever reading for a meter seeds a monthly RECURRING CALENDAR task`() = runTest(dispatcher) {
        val taskRepository = FakeTaskRepository()
        val vm = viewModel(taskRepository = taskRepository)
        dispatcher.scheduler.advanceUntilIdle()

        vm.setMeterNameInput("car")
        vm.setValueInput("40000")
        vm.addReading(seedTaskTitle = "Log your car reading")
        dispatcher.scheduler.advanceUntilIdle()

        val seeded = taskRepository.tasks.single()
        assertEquals("Log your car reading", seeded.title)
        assertEquals(TaskType.RECURRING, seeded.type)
        assertEquals(RecurrenceFrequency.MONTHLY, seeded.recFreq)
        assertEquals(RecurrenceAnchor.CALENDAR, seeded.recAnchor)
        assertEquals(5, seeded.recDayOfMonth) // today's day-of-month (2026-07-05)
        assertNull(seeded.meterName) // the seeded reminder itself isn't meter-linked
    }

    @Test
    fun `seeding is idempotent - a second meter's-first reading after a delete does not reseed`() = runTest(dispatcher) {
        // Idempotency is keyed by the DataStore flag, not by whether a seeded task still exists.
        val taskRepository = FakeTaskRepository()
        val meterSeedRepository = FakeMeterSeedRepository(initialSeeded = setOf("car"))
        val vm = viewModel(taskRepository = taskRepository, meterSeedRepository = meterSeedRepository)
        dispatcher.scheduler.advanceUntilIdle()

        vm.setMeterNameInput("car")
        vm.setValueInput("40000")
        vm.addReading(seedTaskTitle = "Log your car reading")
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(taskRepository.tasks.isEmpty())
    }

    @Test
    fun `second reading for an already-seeded meter does not seed again`() = runTest(dispatcher) {
        val taskRepository = FakeTaskRepository()
        val vm = viewModel(taskRepository = taskRepository)
        dispatcher.scheduler.advanceUntilIdle()

        vm.setMeterNameInput("car")
        vm.setValueInput("40000")
        vm.addReading(seedTaskTitle = "Log your car reading")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, taskRepository.tasks.size)

        vm.setMeterNameInput("car")
        vm.setValueInput("40500")
        vm.addReading(seedTaskTitle = "Log your car reading")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, taskRepository.tasks.size) // still just the one seeded task
    }
}
