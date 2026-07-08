package com.shuremind.system

import com.shuremind.data.entity.TaskEntity
import com.shuremind.data.repo.TaskRepository
import com.shuremind.engine.CompletionAction
import com.shuremind.engine.RecurrenceAnchor
import com.shuremind.engine.RecurrenceFrequency
import com.shuremind.engine.TaskStatus
import com.shuremind.engine.TaskType
import com.shuremind.testutil.FakeCompletionRepository
import com.shuremind.testutil.FakeDeliveryWatermarkRepository
import com.shuremind.testutil.FakeReminderRuleRepository
import com.shuremind.testutil.FakeSettingsRepository
import com.shuremind.testutil.FakeTaskRepository
import com.shuremind.testutil.fixtureTask
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class RecomputeAndRearmTest {

    private val zone: ZoneId = ZoneId.of("Europe/Sofia")

    private class FakeAlarmArmer : AlarmArmer {
        var armedAt: Long? = null
        var armedExact: Boolean? = null
        var armedIsAlarm: Boolean? = null
        var cancelled: Boolean = false

        override fun arm(atEpochMillis: Long, exactAlarmsOptedIn: Boolean, isAlarm: Boolean) {
            armedAt = atEpochMillis
            armedExact = exactAlarmsOptedIn
            armedIsAlarm = isAlarm
            cancelled = false
        }

        override fun cancel() {
            cancelled = true
            armedAt = null
        }
    }

    private class FakeOverdueSummaryNotifier : OverdueSummaryNotifier {
        val postedCounts = mutableListOf<Int>()
        override fun postOverdueSummary(missedCount: Int) {
            postedCounts += missedCount
        }
    }

    /**
     * Simulates a pass stuck mid-read: the *first* observeActive() call captures its snapshot
     * (matching the real bug scenario — the in-flight pass already read the list) and then
     * suspends on [gate] before emitting it, so a "concurrent" write made while gated is invisible
     * to that first pass and only picked up by a subsequent one.
     */
    private class GatedTaskRepository(initial: List<TaskEntity>) : TaskRepository {
        private val tasks = initial.associateBy { it.id }.toMutableMap()
        val gate = CompletableDeferred<Unit>()
        private var observeActiveCalls = 0

        override suspend fun upsert(task: TaskEntity, now: Long) {
            tasks[task.id] = task
        }

        override suspend fun update(task: TaskEntity, now: Long) {
            tasks[task.id] = task
        }

        override suspend fun softDelete(id: String, now: Long) {
            tasks[id]?.let { tasks[id] = it.copy(deletedAt = now) }
        }

        override suspend fun getById(id: String): TaskEntity? = tasks[id]

        override suspend fun snooze(task: TaskEntity, until: Long, now: Long) {
            tasks[task.id] = task.copy(snoozedUntil = until)
        }

        override fun observeActive(excludedStatus: TaskStatus): Flow<List<TaskEntity>> = flow {
            observeActiveCalls++
            val snapshot = tasks.values.filter { it.deletedAt == null && it.status != excludedStatus }
            if (observeActiveCalls == 1) gate.await()
            emit(snapshot)
        }

        override fun observeByType(type: TaskType): Flow<List<TaskEntity>> =
            flow { emit(tasks.values.filter { it.type == type }) }

        override fun observeScheduled(): Flow<List<TaskEntity>> =
            flow { emit(tasks.values.filter { it.nextFireAt != null }) }
    }

    private fun useCase(
        taskRepository: TaskRepository,
        completionRepository: FakeCompletionRepository = FakeCompletionRepository(),
        reminderRuleRepository: FakeReminderRuleRepository = FakeReminderRuleRepository(),
        watermarkRepository: FakeDeliveryWatermarkRepository = FakeDeliveryWatermarkRepository(),
        alarmArmer: FakeAlarmArmer = FakeAlarmArmer(),
        notifier: FakeOverdueSummaryNotifier = FakeOverdueSummaryNotifier(),
        nowProvider: () -> ZonedDateTime = { ZonedDateTime.now(zone) }
    ) = RecomputeAndRearm(
        taskRepository = taskRepository,
        completionRepository = completionRepository,
        reminderRuleRepository = reminderRuleRepository,
        settingsRepository = FakeSettingsRepository(),
        deliveryWatermarkRepository = watermarkRepository,
        alarmArmer = alarmArmer,
        overdueSummaryNotifier = notifier,
        zone = zone,
        nowProvider = nowProvider
    )

    @Test
    fun `arms the alarm at the global-next instant and recomputes next_fire_at`() = runTest {
        val task = fixtureTask("t1", type = TaskType.EVENT)
            .copy(dueLocalDate = LocalDate.of(2026, 8, 20).toString(), dueLocalTime = LocalTime.of(14, 0).toString())
        val taskRepository = FakeTaskRepository(listOf(task))
        val armer = FakeAlarmArmer()
        val now = ZonedDateTime.of(2026, 6, 1, 9, 0, 0, 0, zone)

        useCase(taskRepository, alarmArmer = armer).run(now)

        val expected = ZonedDateTime.of(2026, 8, 20, 14, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(expected, armer.armedAt)
        assertEquals(expected, taskRepository.getById("t1")?.nextFireAt)
        assertEquals(false, armer.armedIsAlarm)
    }

    // --- D-42: alarm-mode arming branch ---

    @Test
    fun `arms via the alarm branch when the global-next fire belongs to an alarm-mode task`() = runTest {
        val alarmTask = fixtureTask("alarm-task", type = TaskType.EVENT, alarmMode = true)
            .copy(dueLocalDate = LocalDate.of(2026, 8, 20).toString(), dueLocalTime = LocalTime.of(14, 0).toString())
        val normalTask = fixtureTask("normal-task", type = TaskType.EVENT)
            .copy(dueLocalDate = LocalDate.of(2026, 9, 1).toString(), dueLocalTime = LocalTime.of(9, 0).toString())
        val armer = FakeAlarmArmer()
        val now = ZonedDateTime.of(2026, 6, 1, 9, 0, 0, 0, zone)

        useCase(FakeTaskRepository(listOf(alarmTask, normalTask)), alarmArmer = armer).run(now)

        assertEquals(ZonedDateTime.of(2026, 8, 20, 14, 0, 0, 0, zone).toInstant().toEpochMilli(), armer.armedAt)
        assertEquals(true, armer.armedIsAlarm)
    }

    @Test
    fun `arms via the normal branch when the global-next fire is not alarm-mode, even with an alarm-mode task later in the queue`() = runTest {
        val normalTask = fixtureTask("normal-task", type = TaskType.EVENT)
            .copy(dueLocalDate = LocalDate.of(2026, 8, 20).toString(), dueLocalTime = LocalTime.of(14, 0).toString())
        val alarmTask = fixtureTask("alarm-task", type = TaskType.EVENT, alarmMode = true)
            .copy(dueLocalDate = LocalDate.of(2026, 9, 1).toString(), dueLocalTime = LocalTime.of(9, 0).toString())
        val armer = FakeAlarmArmer()
        val now = ZonedDateTime.of(2026, 6, 1, 9, 0, 0, 0, zone)

        useCase(FakeTaskRepository(listOf(normalTask, alarmTask)), alarmArmer = armer).run(now)

        assertEquals(ZonedDateTime.of(2026, 8, 20, 14, 0, 0, 0, zone).toInstant().toEpochMilli(), armer.armedAt)
        assertEquals(false, armer.armedIsAlarm)
    }

    @Test
    fun `cancels the alarm when nothing is scheduled`() = runTest {
        val task = fixtureTask("t1", type = TaskType.SOMEDAY)
        val armer = FakeAlarmArmer()
        useCase(FakeTaskRepository(listOf(task)), alarmArmer = armer).run(ZonedDateTime.now(zone))
        assertTrue(armer.cancelled)
        assertNull(armer.armedAt)
    }

    @Test
    fun `DONE one-shot tasks are excluded from scheduling even though observeActive doesn't filter them`() = runTest {
        val task = fixtureTask("t1", type = TaskType.EVENT, status = TaskStatus.DONE)
            .copy(dueLocalDate = LocalDate.of(2026, 8, 20).toString())
        val taskRepository = FakeTaskRepository(listOf(task))
        val armer = FakeAlarmArmer()

        useCase(taskRepository, alarmArmer = armer).run(ZonedDateTime.of(2026, 6, 1, 9, 0, 0, 0, zone))

        assertTrue(armer.cancelled)
        assertNull(taskRepository.getById("t1")?.nextFireAt) // untouched, never even recomputed
    }

    @Test
    fun `posts an overdue summary and advances the watermark when something was missed`() = runTest {
        val task = fixtureTask("t1", type = TaskType.EVENT)
            .copy(dueLocalDate = LocalDate.of(2026, 6, 2).toString(), dueLocalTime = LocalTime.of(10, 0).toString())
        val watermarkRepository = FakeDeliveryWatermarkRepository(
            initial = ZonedDateTime.of(2026, 6, 1, 0, 0, 0, 0, zone).toInstant().toEpochMilli()
        )
        val notifier = FakeOverdueSummaryNotifier()
        val now = ZonedDateTime.of(2026, 6, 3, 0, 0, 0, 0, zone)

        useCase(FakeTaskRepository(listOf(task)), watermarkRepository = watermarkRepository, notifier = notifier).run(now)

        assertEquals(listOf(1), notifier.postedCounts)
        assertEquals(now.toInstant().toEpochMilli(), watermarkRepository.get())
    }

    @Test
    fun `advances the watermark without posting anything when nothing was missed`() = runTest {
        val task = fixtureTask("t1", type = TaskType.EVENT)
            .copy(dueLocalDate = LocalDate.of(2026, 9, 1).toString())
        val watermarkRepository = FakeDeliveryWatermarkRepository(
            initial = ZonedDateTime.of(2026, 6, 1, 0, 0, 0, 0, zone).toInstant().toEpochMilli()
        )
        val notifier = FakeOverdueSummaryNotifier()
        val now = ZonedDateTime.of(2026, 6, 3, 0, 0, 0, 0, zone)

        useCase(FakeTaskRepository(listOf(task)), watermarkRepository = watermarkRepository, notifier = notifier).run(now)

        assertTrue(notifier.postedCounts.isEmpty())
        assertEquals(now.toInstant().toEpochMilli(), watermarkRepository.get())
    }

    @Test
    fun `auto-skips missed CALENDAR occurrences before recomputing, leaving the latest one actionable`() = runTest {
        // Daily 08:00 RECURRING(CALENDAR), last known fire June 1, housekeeping runs June 4 midday:
        // June 1/2/3 should auto-skip, June 4 08:00 stays as the current actionable occurrence.
        val since = ZonedDateTime.of(2026, 6, 1, 8, 0, 0, 0, zone)
        val now = ZonedDateTime.of(2026, 6, 4, 12, 0, 0, 0, zone)
        val task = fixtureTask("t1", type = TaskType.RECURRING, nextFireAt = since.toInstant().toEpochMilli())
            .copy(
                recAnchor = RecurrenceAnchor.CALENDAR,
                recFreq = RecurrenceFrequency.DAILY,
                recTimesOfDay = LocalTime.of(8, 0).toString()
            )
        val completionRepository = FakeCompletionRepository()

        useCase(FakeTaskRepository(listOf(task)), completionRepository = completionRepository).run(now)

        assertEquals(3, completionRepository.recorded.size)
        assertTrue(completionRepository.recorded.all { it.taskId == "t1" && it.action == CompletionAction.SKIPPED })
        assertEquals(
            listOf("2026-06-01 08:00", "2026-06-02 08:00", "2026-06-03 08:00"),
            completionRepository.recorded.map { it.occurrenceLocal }.sorted()
        )
    }

    @Test
    fun `a write arriving while a pass is in flight triggers a rerun that picks it up`() = runTest {
        // Fixed daytime instant (not real wall-clock): urgentDue below is derived from this, so
        // pinning it keeps urgentDue's clock-of-day out of quiet hours (22:00-08:00) no matter when
        // this test actually runs — mirrors the fixed ZonedDateTimes in FireInstantEngineTest.
        val testNow = ZonedDateTime.of(2026, 7, 7, 12, 0, 0, 0, zone)
        val farTask = fixtureTask("far", type = TaskType.EVENT)
            .copy(
                dueLocalDate = testNow.plusDays(60).toLocalDate().toString(),
                dueLocalTime = testNow.plusDays(60).toLocalTime().toString()
            )
        val taskRepository = GatedTaskRepository(listOf(farTask))
        val armer = FakeAlarmArmer()
        // The rerun loop re-reads its own nowProvider for each pass (real wall-clock in production,
        // so a rerun reflects genuinely current time) — pin it to testNow here so the assertion below
        // doesn't depend on the real date the test happens to run on (this is what made the test flaky).
        val recomputeAndRearm = useCase(taskRepository, alarmArmer = armer, nowProvider = { testNow })

        // Start a pass; it reads (and gates on) the task list before the "concurrent" write below.
        val firstPass = launch { recomputeAndRearm.run(testNow) }
        advanceUntilIdle()

        // Arrives while the first pass is stuck mid-read, due sooner than the far task.
        val urgentDue = testNow.plusHours(1)
        val urgentTask = fixtureTask("urgent", type = TaskType.EVENT)
            .copy(dueLocalDate = urgentDue.toLocalDate().toString(), dueLocalTime = urgentDue.toLocalTime().toString())
        taskRepository.upsert(urgentTask)
        recomputeAndRearm.run(testNow) // tryLock fails (first pass still holds it) -> flags a rerun, returns immediately

        taskRepository.gate.complete(Unit) // release the first pass
        firstPass.join()
        advanceUntilIdle()

        // The first pass alone would have armed at farTask's instant; the rerun it triggered picks
        // up urgentTask (added mid-pass) and re-arms at the earlier instant instead.
        assertEquals(urgentDue.toInstant().toEpochMilli(), armer.armedAt)
        assertNotNull(taskRepository.getById("urgent")?.nextFireAt)
    }
}
