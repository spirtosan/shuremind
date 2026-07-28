package com.shuremind.data.repo

import com.shuremind.testutil.FakeReminderRuleDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * D-43: setForTask must fire ScheduleChangeNotifier like every RoomTaskRepository write does —
 * previously it never did, so a newly-added/edited lead reminder didn't re-arm the alarm until some
 * unrelated trigger (app reopen/boot/housekeeping) ran RecomputeAndRearm afterward.
 */
class RoomReminderRuleRepositoryTest {

    @Test
    fun `setForTask writes the rules then fires ScheduleChangeNotifier`() = runTest {
        var notifiedCount = 0
        val notifier = ScheduleChangeNotifier { notifiedCount++ }
        val dao = FakeReminderRuleDao()
        val repository = RoomReminderRuleRepository(dao, notifier)

        repository.setForTask("t1", listOf("P1D", "PT2H"))

        assertEquals(listOf("P1D", "PT2H"), dao.getForTask("t1").map { it.offsetIso })
        assertEquals(1, notifiedCount)
    }

    @Test
    fun `setForTask replaces the previous rule set and still notifies once`() = runTest {
        var notifiedCount = 0
        val notifier = ScheduleChangeNotifier { notifiedCount++ }
        val dao = FakeReminderRuleDao()
        val repository = RoomReminderRuleRepository(dao, notifier)

        repository.setForTask("t1", listOf("P14D"))
        repository.setForTask("t1", listOf("P1D", "PT2H"))

        assertEquals(listOf("P1D", "PT2H"), dao.getForTask("t1").map { it.offsetIso })
        assertEquals(2, notifiedCount)
    }

    @Test
    fun `without a configured notifier, setForTask still writes the rules and does not throw`() = runTest {
        val dao = FakeReminderRuleDao()
        val repository = RoomReminderRuleRepository(dao)

        repository.setForTask("t1", listOf("P1D"))

        assertEquals(listOf("P1D"), dao.getForTask("t1").map { it.offsetIso })
    }
}
