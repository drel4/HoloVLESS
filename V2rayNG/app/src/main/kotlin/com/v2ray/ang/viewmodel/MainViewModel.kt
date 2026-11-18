package com.v2ray.ang.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AngApplication
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.ServerConfig
import com.v2ray.ang.extension.toast
import com.v2ray.ang.util.*
import com.v2ray.ang.service.CoreTestService
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val mainStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_MAIN, MMKV.MULTI_PROCESS_MODE) }

    private var loadListJob: Job? = null
    private var serverFilter: String? = null
    private var hasAppliedServerFilter = false
    var serverList = mutableListOf<String>()
        private set
    val serversCache = ConcurrentHashMap<String, ServerConfig>()
    val isRunning by lazy { MutableLiveData<Boolean>() }
    val updateListAction by lazy { MutableLiveData<Int>() }
    val updateTestResultAction by lazy { MutableLiveData<String>() }
    val importResultAction by lazy { MutableLiveData<Int>() }
    val isTesting by lazy { MutableLiveData<Boolean>(false) }
    val isLoading = MutableLiveData(false)
    val insertedRows = MutableLiveData<Pair<Int, Int>>()
    private val rowJsonCache = ConcurrentHashMap<String, String>()
    private val importMutex = Mutex()

    private val tcpingTestScope by lazy { CoroutineScope(Dispatchers.IO) }

    fun startListenBroadcast() {
        isRunning.value = false
        getApplication<AngApplication>().registerReceiver(mMsgReceiver, IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY))
        MessageUtil.sendMsg2Service(getApplication(), AppConfig.MSG_REGISTER_CLIENT, "")
    }

    override fun onCleared() {
        getApplication<AngApplication>().unregisterReceiver(mMsgReceiver)
        tcpingTestScope.coroutineContext[Job]?.cancelChildren()
        Utils.closeAllTcpSockets()
        Log.i(AppConfig.ANG_PACKAGE, "Main ViewModel is cleared")
        super.onCleared()
    }

    fun reloadServerList() {
        applyServerFilter()
    }

    fun importBatchConfig(server: String?, subid: String) {
        viewModelScope.launch {
            val count = withContext(Dispatchers.IO) {
                importMutex.withLock {
                    var imported = AngConfigManager.importBatchConfig(server, subid)
                    if (imported <= 0 && !server.isNullOrBlank()) {
                        imported = AngConfigManager.importBatchConfig(Utils.decode(server), subid)
                    }
                    imported
                }
            }
            if (count > 0) reloadServerList()
            importResultAction.value = count
        }
    }

    fun setServerFilter(subscriptionId: String?) {
        if (hasAppliedServerFilter && serverFilter == subscriptionId) return
        serverFilter = subscriptionId
        hasAppliedServerFilter = true
        applyServerFilter()
    }

    fun removeServer(guid: String) {
        loadListJob?.cancel()
        serversCache.remove(guid)
        rowJsonCache.remove(guid)
        serverList.remove(guid)
        MmkvManager.removeServer(guid)
        // A deletion during a cold chunked load must not discard the remaining rows.
        reloadServerList()
    }

    fun sortProfiles(mode: Int) {
        val guids = serverList.toList()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val entries = guids.mapNotNull { guid ->
                    MmkvManager.decodeServerRowJson(guid)?.let(ProfileRows::decode)?.let { config ->
                        ProfileSorting.Entry(guid, config.remarks, config.configType.name,
                                MmkvManager.decodeServerAffiliationInfo(guid)?.testDelayMillis ?: 0L, config.addedTime)
                    }
                }
                MmkvManager.reorderServers(ProfileSorting.sort(entries, mode))
            }
            reloadServerList()
        }
    }

    fun checkProfiles(guids: List<String>) {
        if (guids.isEmpty()) return
        CoreTestService.start(getApplication(), guids)
        isTesting.value = true
    }

    fun cancelChecks() {
        val context = getApplication<AngApplication>()
        context.startService(Intent(context, CoreTestService::class.java).setAction(CoreTestService.CANCEL))
        isTesting.value = false
    }

    private fun applyServerFilter() {
        loadListJob?.cancel()
        val filter = serverFilter
        isLoading.value = true
        loadListJob = viewModelScope.launch {
            try {
                val (all, visible) = withContext(Dispatchers.IO) {
                    val all = MmkvManager.decodeServerList()
                    val visible = all.filter { guid ->
                        ensureActive()
                        filter == null || MmkvManager.decodeServerSubscriptionId(guid) == filter
                    }
                    all to visible
                }
                val existing = all.toHashSet()
                serversCache.keys.toList().filter { it !in existing }.forEach {
                    serversCache.remove(it)
                    rowJsonCache.remove(it)
                }
                // Warm groups appear immediately, without decoding every row again.
                val warm = visible.all { serversCache.containsKey(it) }
                if (warm) {
                    serverList = visible.toMutableList()
                    updateListAction.value = -1
                }
                val loadedIds = mutableListOf<String>()
                visible.chunked(64).forEachIndexed { chunkIndex, ids ->
                    val rows = withContext(Dispatchers.IO) {
                        ids.mapNotNull { guid ->
                            ensureActive()
                            val json = MmkvManager.decodeServerRowJson(guid) ?: return@mapNotNull null
                            val cached = serversCache[guid]
                            val config = if (cached != null && rowJsonCache[guid] == json) cached
                                    else ProfileRows.decode(json)
                            Triple(guid, json, config)
                        }
                    }
                    rows.forEach { (guid, json, config) ->
                        serversCache[guid] = config
                        rowJsonCache[guid] = json
                    }
                    loadedIds.addAll(rows.map { it.first })
                    if (!warm) {
                        val start = if (chunkIndex == 0) 0 else serverList.size
                        if (chunkIndex == 0) {
                            serverList = loadedIds.toMutableList()
                            updateListAction.value = -1
                        } else {
                            serverList.addAll(rows.map { it.first })
                            if (rows.isNotEmpty()) insertedRows.value = start to rows.size
                        }
                    }
                }
                // Also drops missing records and refreshes edited names/endpoints.
                serverList = loadedIds
                updateListAction.value = -1
            } finally {
                // A superseded group's cancellation must not clear the new load's flag.
                if (isActive) isLoading.value = false
            }
        }
    }

    fun testAllPing() {
        tcpingTestScope.coroutineContext[Job]?.cancelChildren()
        Utils.closeAllTcpSockets()
        MmkvManager.clearAllTestDelayResults()
        updateListAction.value = -1 // update all

        getApplication<AngApplication>().toast(R.string.connection_test_testing)
        for (guid in serverList) {
            serversCache.getOrElse(guid) { MmkvManager.decodeServerConfig(guid) }?.getProxyOutbound()?.let { outbound ->
                val serverAddress = outbound.getServerAddress()
                if (serverAddress != null) {
                    tcpingTestScope.launch {
                        val testResult = Utils.ping(serverAddress)
                        launch(Dispatchers.Main) {
                            MmkvManager.encodeServerTestDelayMillis(guid, testResult)
                            updateListAction.value = serverList.indexOf(guid)
                        }
                    }
                }
            }
        }
    }

    fun testPing(guid: String) {
        serversCache.getOrElse(guid) { MmkvManager.decodeServerConfig(guid) }
                ?.getProxyOutbound()?.let { outbound ->
                    val serverAddress = outbound.getServerAddress() ?: return
                    tcpingTestScope.launch {
                        val testResult = Utils.ping(serverAddress)
                        launch(Dispatchers.Main) {
                            MmkvManager.encodeServerTestDelayMillis(guid, testResult)
                            updateListAction.value = serverList.indexOf(guid)
                        }
                    }
                }
    }

    fun testCurrentServerRealPing() {
        val socksPort = 10808//Utils.parseInt(defaultDPreference.getPrefString(SettingsActivity.PREF_SOCKS_PORT, "10808"))
        GlobalScope.launch(Dispatchers.IO) {
            val result = Utils.testConnection(getApplication(), socksPort)
            launch(Dispatchers.Main) {
                updateTestResultAction.value = result
            }
        }
    }

    private val mMsgReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.getIntExtra("key", 0)) {
                CoreTestService.RESULT -> {
                    val guid = intent.getStringExtra("guid").orEmpty()
                    val index = serverList.indexOf(guid)
                    if (index >= 0) updateListAction.value = index
                    val done = intent.getBooleanExtra("done", false)
                    if (isTesting.value != !done) isTesting.value = !done
                    updateTestResultAction.value = if (done) {
                        getApplication<AngApplication>().getString(R.string.profiles_checked_result,
                                intent.getIntExtra("working", 0), intent.getIntExtra("total", 0))
                    } else {
                        getApplication<AngApplication>().getString(R.string.checks_progress,
                                intent.getIntExtra("completed", 0), intent.getIntExtra("total", 0))
                    }
                }
                AppConfig.MSG_STATE_RUNNING -> {
                    isRunning.value = true
                }
                AppConfig.MSG_STATE_NOT_RUNNING -> {
                    isRunning.value = false
                }
                AppConfig.MSG_STATE_START_SUCCESS -> {
                    getApplication<AngApplication>().toast(R.string.toast_services_success)
                    isRunning.value = true
                }
                AppConfig.MSG_STATE_START_FAILURE -> {
                    getApplication<AngApplication>().toast(R.string.toast_services_failure)
                    isRunning.value = false
                }
                AppConfig.MSG_STATE_STOP_SUCCESS -> {
                    isRunning.value = false
                }
            }
        }
    }
}
