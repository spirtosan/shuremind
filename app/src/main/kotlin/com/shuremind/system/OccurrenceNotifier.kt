package com.shuremind.system

import com.shuremind.data.entity.TaskEntity
import com.shuremind.engine.FireInstantEngine.GlobalFire

/** Posts one actionable (Done/Snooze/+Skip) notification per due occurrence. Implemented in full in M3 STEP 6. */
fun interface OccurrenceNotifier {
    suspend fun postOccurrenceNotification(task: TaskEntity, fire: GlobalFire)
}
