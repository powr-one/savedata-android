package com.savedata.app

import android.app.Application
import com.savedata.app.data.AppDatabase
import com.savedata.app.data.AppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class App : Application() {

    val applicationScope = CoroutineScope(SupervisorJob())

    val database by lazy { AppDatabase.getInstance(this) }
    val repository by lazy { AppRepository(database.appRuleDao(), database.trafficRecordDao(), this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
