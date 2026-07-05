package com.shuremind.system

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
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
        var cancelled: Boolean = false

        override fun arm(atEpochMillis: Long, exactAlarmsOptedIn: Boolean) {
            armedAt = atEpochMillis
            armedExact = exactAlarmsOptedIn
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

    private fun useCase(
        taskRepository: FakeTaskRepository,
        completionRepository: FakeCompletionRepository = FakeCompletionRepository(),
        reminderRuleRepository: FakeReminderRuleRepository = FakeReminderRuleRepository(),
        watermarkRepository: FakeDeliveryWatermarkRepository = FakeDeliveryWatermarkRepository(),
        alarmArmer: FakeAlarmArmer = FakeAlarmArmer(),
        notifier: FakeOverdueSummaryNotifier = FakeOverdueSummaryNotifier()
    ) = RecomputeAndRearm(
        taskRepository = taskRepository,
        completionRepository = completionRepository,
        reminderRuleRepository = reminderRuleRepository,
        settingsRepository = FakeSettingsRepository(),
        deliveryWatermarkRepository = watermarkRepository,
        alarmArmer = alarmArmer,
        overdueSummaryNotifier = notifier,
        zone = zone
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
}
