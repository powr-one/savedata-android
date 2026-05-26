package com.savedata.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.savedata.app.App

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = App.instance.repository

    val periodHours = MutableLiveData(repository.getPeriodHours())
    val showSystemApps = MutableLiveData(repository.isShowSystemApps())

    fun setPeriodHours(hours: Int) {
        repository.setPeriodHours(hours)
        periodHours.value = hours
    }

    fun setShowSystemApps(show: Boolean) {
        repository.setShowSystemApps(show)
        showSystemApps.value = show
    }
}
