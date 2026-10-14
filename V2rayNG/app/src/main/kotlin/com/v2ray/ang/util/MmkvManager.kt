package com.v2ray.ang.util

import com.google.gson.Gson
import com.tencent.mmkv.MMKV
import com.v2ray.ang.dto.ServerAffiliationInfo
import com.v2ray.ang.dto.ServerConfig
import com.v2ray.ang.dto.SubscriptionItem

object MmkvManager {
    const val ID_MAIN = "MAIN"
    const val ID_SERVER_CONFIG = "SERVER_CONFIG"
    const val ID_SERVER_RAW = "SERVER_RAW"
    const val ID_SERVER_AFF = "SERVER_AFF"
    const val ID_SERVER_SUBSCRIPTION = "SERVER_SUBSCRIPTION"
    const val ID_SUB = "SUB"
    const val ID_SETTING = "SETTING"
    const val KEY_SELECTED_SERVER = "SELECTED_SERVER"
    const val KEY_SELECTED_GROUP = "SELECTED_GROUP"
    const val KEY_ANG_CONFIGS = "ANG_CONFIGS"

    private val mainStorage by lazy { MMKV.mmkvWithID(ID_MAIN, MMKV.MULTI_PROCESS_MODE) }
    private val serverStorage by lazy { MMKV.mmkvWithID(ID_SERVER_CONFIG, MMKV.MULTI_PROCESS_MODE) }
    private val serverAffStorage by lazy { MMKV.mmkvWithID(ID_SERVER_AFF, MMKV.MULTI_PROCESS_MODE) }
    private val serverSubscriptionStorage by lazy { MMKV.mmkvWithID(ID_SERVER_SUBSCRIPTION, MMKV.MULTI_PROCESS_MODE) }
    private val subStorage by lazy { MMKV.mmkvWithID(ID_SUB, MMKV.MULTI_PROCESS_MODE) }

    fun decodeServerList(): MutableList<String> {
        val json = mainStorage?.decodeString(KEY_ANG_CONFIGS)
        return if (json.isNullOrBlank()) {
            mutableListOf()
        } else {
            Gson().fromJson(json, Array<String>::class.java).toMutableList()
        }
    }

    fun decodeServerConfig(guid: String): ServerConfig? {
        if (guid.isBlank()) {
            return null
        }
        val json = serverStorage?.decodeString(guid)
        if (json.isNullOrBlank()) {
            return null
        }
        return Gson().fromJson(json, ServerConfig::class.java)
    }

    fun decodeServerSubscriptionId(guid: String): String? {
        if (guid.isBlank()) {
            return null
        }
        serverSubscriptionStorage?.decodeString(guid)?.let { return it }
        val subscriptionId = decodeServerConfig(guid)?.subscriptionId ?: return null
        serverSubscriptionStorage?.encode(guid, subscriptionId)
        return subscriptionId
    }

    @Synchronized
    fun encodeServerConfig(guid: String, config: ServerConfig): String {
        val key = guid.ifBlank { Utils.getUuid() }
        serverStorage?.encode(key, Gson().toJson(config))
        serverSubscriptionStorage?.encode(key, config.subscriptionId)
        val serverList= decodeServerList()
        if (!serverList.contains(key)) {
            serverList.add(key)
            mainStorage?.encode(KEY_ANG_CONFIGS, Gson().toJson(serverList))
            if (mainStorage?.decodeString(KEY_SELECTED_SERVER).isNullOrBlank()) {
                mainStorage?.encode(KEY_SELECTED_SERVER, key)
            }
        }
        return key
    }

    /** Saves a parsed import with one ID-list read/write, rather than one per profile. */
    fun importServerConfigs(configs: List<ServerConfig>, subid: String) {
        if (configs.isEmpty()) return
        val gson = Gson()
        // Stage expensive serialization outside the list-mutation lock so UI
        // actions do not wait for every profile in a large import.
        val imported = configs.map { config ->
            val key = Utils.getUuid()
            serverStorage?.encode(key, gson.toJson(config))
            serverSubscriptionStorage?.encode(key, config.subscriptionId)
            key to config
        }
        val removed = synchronized(this) {
            val serverList = decodeServerList()
            val removed = if (subid.isBlank()) emptySet() else serverList
                    .filter { decodeServerSubscriptionId(it) == subid }.toHashSet()
            val selected = mainStorage?.decodeString(KEY_SELECTED_SERVER).orEmpty()
            val selectedConfig = if (selected in removed) decodeServerConfig(selected) else null
            // Read the latest list at commit time so concurrent additions survive.
            serverList.removeAll(removed)
            serverList.addAll(imported.map { it.first })
            mainStorage?.encode(KEY_ANG_CONFIGS, gson.toJson(serverList))
            if (selected.isBlank() || selected in removed) {
                val replacement = selectedConfig?.let { previous ->
                    imported.firstOrNull { (_, config) ->
                        config.getProxyOutbound()?.getServerAddress() == previous.getProxyOutbound()?.getServerAddress() &&
                                config.getProxyOutbound()?.getServerPort() == previous.getProxyOutbound()?.getServerPort()
                    }?.first
                }
                // Respect a selection changed by the UI while publishing.
                if (mainStorage?.decodeString(KEY_SELECTED_SERVER).orEmpty() == selected) {
                    mainStorage?.encode(KEY_SELECTED_SERVER, replacement ?: imported.first().first)
                }
            }
            removed
        }
        removed.forEach { key ->
            serverStorage?.remove(key)
            serverAffStorage?.remove(key)
            serverSubscriptionStorage?.remove(key)
        }
    }

    @Synchronized
    fun reorderServers(orderedGuids: List<String>) {
        val current = decodeServerList()
        val existing = current.toHashSet()
        val ordered = orderedGuids.filter { it in existing }
        val reordered = ordered.toHashSet()
        var index = 0
        current.indices.forEach { position ->
            if (current[position] in reordered) current[position] = ordered[index++]
        }
        mainStorage?.encode(KEY_ANG_CONFIGS, Gson().toJson(current))
    }

    @Synchronized
    fun removeServer(guid: String) {
        if (guid.isBlank()) {
            return
        }
        if (mainStorage?.decodeString(KEY_SELECTED_SERVER) == guid) {
            mainStorage?.remove(KEY_SELECTED_SERVER)
        }
        val serverList= decodeServerList()
        serverList.remove(guid)
        mainStorage?.encode(KEY_ANG_CONFIGS, Gson().toJson(serverList))
        serverStorage?.remove(guid)
        serverAffStorage?.remove(guid)
        serverSubscriptionStorage?.remove(guid)
    }

    @Synchronized
    fun removeServerViaSubid(subid: String) {
        if (subid.isBlank()) {
            return
        }
        val serverList = decodeServerList()
        val removedServers = serverList.filter { decodeServerSubscriptionId(it) == subid }
        if (removedServers.isEmpty()) {
            return
        }
        if (removedServers.contains(mainStorage?.decodeString(KEY_SELECTED_SERVER))) {
            mainStorage?.remove(KEY_SELECTED_SERVER)
        }
        serverList.removeAll(removedServers)
        mainStorage?.encode(KEY_ANG_CONFIGS, Gson().toJson(serverList))
        removedServers.forEach { key ->
            serverStorage?.remove(key)
            serverAffStorage?.remove(key)
            serverSubscriptionStorage?.remove(key)
        }
    }

    fun decodeServerAffiliationInfo(guid: String): ServerAffiliationInfo? {
        if (guid.isBlank()) {
            return null
        }
        val json = serverAffStorage?.decodeString(guid)
        if (json.isNullOrBlank()) {
            return null
        }
        return Gson().fromJson(json, ServerAffiliationInfo::class.java)
    }

    fun encodeServerTestDelayMillis(guid: String, testResult: Long) {
        if (guid.isBlank()) {
            return
        }
        val aff = decodeServerAffiliationInfo(guid) ?: ServerAffiliationInfo()
        aff.testDelayMillis = testResult
        serverAffStorage?.encode(guid, Gson().toJson(aff))
    }

    fun clearAllTestDelayResults() {
        serverAffStorage?.allKeys()?.forEach { key ->
            decodeServerAffiliationInfo(key)?.let { aff ->
                aff.testDelayMillis = 0
                serverAffStorage?.encode(key, Gson().toJson(aff))
            }
        }
    }

    fun importUrlAsSubscription(url: String): Int {
        val subscriptions = decodeSubscriptions()
        subscriptions.forEach {
            if (it.second.url == url) {
                return 0
            }
        }
        val subItem = SubscriptionItem()
        subItem.remarks = "import sub"
        subItem.url = url
        subStorage?.encode(Utils.getUuid(), Gson().toJson(subItem))
        return 1
    }

    fun decodeSubscriptions(): List<Pair<String, SubscriptionItem>> {
        val subscriptions = mutableListOf<Pair<String, SubscriptionItem>>()
        subStorage?.allKeys()?.forEach { key ->
            val json = subStorage?.decodeString(key)
            if (!json.isNullOrBlank()) {
                subscriptions.add(Pair(key, Gson().fromJson(json, SubscriptionItem::class.java)))
            }
        }
        subscriptions.sortedBy { (_, value) -> value.addedTime }
        return subscriptions
    }

    fun removeSubscription(subid: String) {
        subStorage?.remove(subid)
        removeServerViaSubid(subid)
    }
}
