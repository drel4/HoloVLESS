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
import com.google.gson.Gson
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AngApplication
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.EConfigType
import com.v2ray.ang.dto.ServerConfig
import com.v2ray.ang.dto.V2rayConfig
import com.v2ray.ang.extension.toast
import com.v2ray.ang.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val mainStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_MAIN, MMKV.MULTI_PROCESS_MODE) }
    private val serverRawStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_SERVER_RAW, MMKV.MULTI_PROCESS_MODE) }

    private var allServerList = mutableListOf<String>()
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
        applyServerFilter(refreshVisible = true)
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
        applyServerFilter(refreshVisible = false)
    }

    fun removeServer(guid: String) {
        loadListJob?.cancel()
        serversCache.remove(guid)
        serverList.remove(guid)
        allServerList.remove(guid)
        MmkvManager.removeServer(guid)
    }

    fun appendCustomConfigServer(server: String) {
        loadListJob?.cancel()
        val config = ServerConfig.create(EConfigType.CUSTOM)
        config.remarks = System.currentTimeMillis().toString()
        config.fullConfig = Gson().fromJson(server, V2rayConfig::class.java)
        val key = MmkvManager.encodeServerConfig("", config)
        serverRawStorage?.encode(key, server)
        allServerList.add(key)
        if (serverFilter == null || serverFilter == "") {
            serverList.add(key)
        }
        serversCache[key] = config
    }

    fun swapServer(fromPosition: Int, toPosition: Int) {
        loadListJob?.cancel()
        Collections.swap(serverList, fromPosition, toPosition)
        // Merge this visible group's order into the latest stored list. An import
        // completing during a drag must not lose its newly added profiles.
        MmkvManager.reorderServers(serverList)
    }

    private fun applyServerFilter(refreshVisible: Boolean) {
        loadListJob?.cancel()
        val filter = serverFilter
        loadListJob = viewModelScope.launch {
            val (all, visible, loaded) = withContext(Dispatchers.IO) {
                val all = MmkvManager.decodeServerList()
                val visible = mutableListOf<String>()
                val loaded = mutableMapOf<String, ServerConfig>()
                all.forEach { guid ->
                    ensureActive()
                    if (filter == null || MmkvManager.decodeServerSubscriptionId(guid) == filter) {
                        val config = (if (refreshVisible) null else serversCache[guid])
                                ?: MmkvManager.decodeServerConfig(guid)
                        if (config != null) {
                            visible.add(guid)
                            loaded[guid] = config
                        }
                    }
                }
                Triple(all, visible, loaded)
            }
            // Publish only after decoding finishes. Cancellation prevents an older
            // group load from replacing a newer selection during fast tab switches.
            allServerList = all
            val existing = all.toHashSet()
            serversCache.keys.toList().forEach { if (it !in existing) serversCache.remove(it) }
            serversCache.putAll(loaded)
            serverList = visible
            updateListAction.value = -1
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
