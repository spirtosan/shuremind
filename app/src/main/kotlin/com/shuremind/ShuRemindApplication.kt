package com.shuremind

import android.app.Application
import com.shuremind.di.AppContainer
import com.shuremind.system.HousekeepingWorker
import com.shuremind.system.NotificationChannels

class ShuRemindApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationChannels.ensureCreated(this)
        HousekeepingWorker.schedule(this)
    }
}
