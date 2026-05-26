package com.savedata.app.ui.apps

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.savedata.app.App
import com.savedata.app.data.AppRule
import com.savedata.app.util.AppInfo
import com.savedata.app.util.AppInfoLoader
import com.savedata.app.util.TrafficMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AppsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = App.instance.repository
    val trafficMonitor = TrafficMonitor(viewModelScope)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _showSystem = MutableStateFlow(repository.isShowSystemApps())
    val showSystem: StateFlow<Boolean> = _showSystem.asStateFlow()

    private val _rawAppList = MutableStateFlow<List<AppInfo>>(emptyList())

    val appList: StateFlow<List<AppListItem>> = combine(
        _rawAppList,
        repository.allRules,
        trafficMonitor.traffic,
        _searchQuery,
        _showSystem
    ) { rawList, rules, traffic, query, showSys ->
        val ruleMap = rules.associateBy { it.packageName }
        rawList
            .filter { if (!showSys) !it.isSystem else true }
            .filter { query.isBlank() || it.appName.contains(query, ignoreCase = true) }
            .map { info ->
                val t = traffic[info.uid]
                AppListItem(
                    packageName = info.packageName,
                    appName = info.appName,
                    uid = info.uid,
                    icon = info.icon,
                    isSystem = info.isSystem,
                    isBlocked = ruleMap[info.packageName]?.blocked ?: false,
                    rxBytes = t?.rxSincePeriod ?: 0L,
                    txBytes = t?.txSincePeriod ?: 0L
                )
            }
            .sortedByDescending { it.totalBytes }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadApps()
        trafficMonitor.start()
    }

    private fun loadApps() {
        viewModelScope.launch(Dispatchers.IO) {
            _rawAppList.value = AppInfoLoader.loadApps(true)
        }
    }

    fun setBlocked(packageName: String, blocked: Boolean) {
        viewModelScope.launch {
            repository.setBlocked(packageName, blocked)
        }
    }

    fun setSearch(query: String) {
        _searchQuery.value = query
    }

    fun toggleShowSystem(show: Boolean) {
        _showSystem.value = show
        repository.setShowSystemApps(show)
    }

    fun resetTraffic() {
        trafficMonitor.resetBaselines()
        viewModelScope.launch {
            repository.resetAllTraffic()
        }
    }

    override fun onCleared() {
        trafficMonitor.stop()
        super.onCleared()
    }
}
