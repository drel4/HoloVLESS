package com.v2ray.ang.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
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
import com.v2ray.ang.util.MmkvManager.KEY_ANG_CONFIGS
import kotlinx.coroutines.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val mainStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_MAIN, MMKV.MULTI_PROCESS_MODE) }
    private val serverRawStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_SERVER_RAW, MMKV.MULTI_PROCESS_MODE) }

    private var allServerList = MmkvManager.decodeServerList()
    private var serverFilter: String? = null
    private var hasAppliedServerFilter = false
    var serverList = mutableListOf<String>()
        private set
    val serversCache = ConcurrentHashMap<String, ServerConfig>()
    val isRunning by lazy { MutableLiveData<Boolean>() }
    val updateListAction by lazy { MutableLiveData<Int>() }
    val updateTestResultAction by lazy { MutableLiveData<String>() }

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
        allServerList = MmkvManager.decodeServerList()
        serversCache.keys.toList().forEach { guid ->
            if (!allServerList.contains(guid)) {
                serversCache.remove(guid)
            }
        }
        applyServerFilter(refreshVisible = true)
    }

    fun setServerFilter(subscriptionId: String?) {
        if (hasAppliedServerFilter && serverFilter == subscriptionId) return
        serverFilter = subscriptionId
        hasAppliedServerFilter = true
        applyServerFilter(refreshVisible = false)
    }

    fun removeServer(guid: String) {
        serverList.remove(guid)
        allServerList.remove(guid)
        MmkvManager.removeServer(guid)
    }

    fun appendCustomConfigServer(server: String) {
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
        Collections.swap(serverList, fromPosition, toPosition)
        if (serverFilter == null) {
            allServerList = serverList.toMutableList()
        } else {
            val filteredPositions = allServerList.indices.filter { index ->
                val guid = allServerList[index]
                MmkvManager.decodeServerSubscriptionId(guid) == serverFilter
            }
            filteredPositions.forEachIndexed { index, position ->
                allServerList[position] = serverList[index]
            }
        }
        mainStorage?.encode(KEY_ANG_CONFIGS, Gson().toJson(allServerList))
    }

    private fun applyServerFilter(refreshVisible: Boolean) {
        serverList = if (serverFilter == null) {
            allServerList.toMutableList()
        } else {
            allServerList.filter { guid ->
                MmkvManager.decodeServerSubscriptionId(guid) == serverFilter
            }.toMutableList()
        }
        serverList.forEach { guid ->
            if (refreshVisible) {
                serversCache.remove(guid)
            }
            getCachedServer(guid)
        }
        updateListAction.value = -1
    }

    private fun getCachedServer(guid: String): ServerConfig? {
        serversCache[guid]?.let { return it }
        return MmkvManager.decodeServerConfig(guid)?.also { serversCache[guid] = it }
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
