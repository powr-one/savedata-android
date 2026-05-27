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

enum class SortOrder { BY_DATA, BY_NAME }

class AppsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = App.instance.repository
    val trafficMonitor = TrafficMonitor(viewModelScope)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _showSystem = MutableStateFlow(repository.isShowSystemApps())
    val showSystem: StateFlow<Boolean> = _showSystem.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.BY_DATA)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _rawAppList = MutableStateFlow<List<AppInfo>>(emptyList())

    // Stage 1: filter + map (5 flows)
    private val filteredList: Flow<List<AppListItem>> = combine(
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
                    rxBytes   = t?.rxSincePeriod ?: 0L,
                    txBytes   = t?.txSincePeriod ?: 0L,
                    rxWifi    = t?.rxWifi   ?: 0L,
                    txWifi    = t?.txWifi   ?: 0L,
                    rxMobile  = t?.rxMobile ?: 0L,
                    txMobile  = t?.txMobile ?: 0L
                )
            }
    }

    // Stage 2: sort
    val appList: StateFlow<List<AppListItem>> = combine(filteredList, _sortOrder) { list, sort ->
        when (sort) {
            SortOrder.BY_DATA -> list.sortedByDescending { it.totalBytes }
            SortOrder.BY_NAME -> list.sortedBy { it.appName.lowercase() }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadApps()
        trafficMonitor.start()
    }

    private fun loadApps() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            val appsNoIcons = AppInfoLoader.loadApps()
            _rawAppList.value = appsNoIcons
            _isLoading.value = false
            // Phase 2: load icons in background
            val appsWithIcons = appsNoIcons.map { info ->
                info.copy(icon = AppInfoLoader.loadIconFor(info.packageName))
            }
            _rawAppList.value = appsWithIcons
        }
    }

    fun setBlocked(packageName: String, blocked: Boolean) {
        viewModelScope.launch { repository.setBlocked(packageName, blocked) }
    }

    fun setSearch(query: String) { _searchQuery.value = query }

    fun toggleShowSystem(show: Boolean) {
        _showSystem.value = show
        repository.setShowSystemApps(show)
    }

    fun setSortOrder(order: SortOrder) { _sortOrder.value = order }

    fun resetTraffic() {
        trafficMonitor.resetBaselines()
        viewModelScope.launch { repository.resetAllTraffic() }
    }

    override fun onCleared() {
        trafficMonitor.stop()
        super.onCleared()
    }
}
