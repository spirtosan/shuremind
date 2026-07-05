package com.shuremind.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.shuremind.ShuRemindApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BOOT_COMPLETED / ACTION_TIME_CHANGED / ACTION_TIMEZONE_CHANGED / MY_PACKAGE_REPLACED -> full
 * recompute + missed-fire recovery + re-arm (D-06/D-07/D-10). No directBoot handling (D-17).
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return
        val container = (context.applicationContext as ShuRemindApplication).container
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                container.recomputeAndRearm.run()
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED
        )
    }
}
