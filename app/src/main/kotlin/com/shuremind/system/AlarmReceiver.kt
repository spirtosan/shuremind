package com.shuremind.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.shuremind.ShuRemindApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Fires on the single armed AlarmManager alarm (D-07/D-08). Idempotent if the same fire is
 * delivered twice: collectDueSinceWatermark() re-reads the (possibly already-advanced) watermark
 * each time, so a repeat delivery simply finds nothing new; per-occurrence notifications also use a
 * stable id (task+occurrence), so even a genuine duplicate post just replaces itself.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as ShuRemindApplication).container
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val zone = ZoneId.systemDefault()
                val now = ZonedDateTime.now(zone)

                val batch = container.recomputeAndRearm.collectDueSinceWatermark(now)
                batch.forEach { (task, fire) -> container.notificationCenter.postOccurrenceNotification(task, fire) }
                container.deliveryWatermarkRepository.advanceTo(now.toInstant().toEpochMilli())

                container.recomputeAndRearm.run(now)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
