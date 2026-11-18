package com.v2ray.ang.util

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.v2ray.ang.dto.EConfigType
import com.v2ray.ang.dto.ServerConfig
import com.v2ray.ang.dto.V2rayConfig

/** Keep full core JSON rather than round-tripping through the legacy DTO. */
object JsonProfiles {
    private val gson = Gson()

    fun parse(text: String, subscriptionId: String = ""): List<ServerConfig> {
        val root = JsonParser.parseString(text.trim { it.isWhitespace() || it == '\uFEFF' })
        val entries = if (root.isJsonArray) root.asJsonArray.toList() else listOf(root)
        require(entries.isNotEmpty())
        return entries.mapIndexed { index, entry ->
            require(entry.isJsonObject)
            val json = entry.asJsonObject
            val outbounds = json.getAsJsonArray("outbounds")
            require(outbounds != null && outbounds.size() > 0 && outbounds.all { it.isJsonObject })
            val proxy = outbounds.firstOrNull {
                it.asJsonObject.get("protocol")?.asString !in listOf("freedom", "blackhole", "dns", "loopback")
            }
            val metadata = proxy?.let {
                runCatching { gson.fromJson(it, V2rayConfig.OutboundBean::class.java) }.getOrNull()
            }
            ServerConfig(configType = EConfigType.CUSTOM, subscriptionId = subscriptionId,
                    remarks = json.get("remarks")?.takeIf { it.isJsonPrimitive }?.asString
                            ?.takeIf { it.isNotBlank() } ?: "JSON ${index + 1}",
                    outboundBean = metadata, rawConfig = json.toString())
        }
    }

    /** Bind the app's TUN bridge to its fixed local SOCKS port, keeping all other JSON fields. */
    fun forConnection(raw: String): String {
        val root = JsonParser.parseString(raw).asJsonObject
        val inbounds = root.getAsJsonArray("inbounds") ?: JsonArray().also { root.add("inbounds", it) }
        var hasSocks = false
        var hasHttp = false
        val normalized = JsonArray()
        inbounds.forEach { element ->
            val inbound = element.asJsonObject
            when (inbound.get("protocol")?.asString) {
                "socks", "mixed" -> {
                    if (hasSocks) return@forEach
                    hasSocks = true
                    inbound.addProperty("port", 10808)
                    inbound.addProperty("listen", "127.0.0.1")
                    inbound.add("settings", JsonObject().apply {
                        addProperty("auth", "noauth")
                        addProperty("udp", true)
                    })
                }
                "http" -> {
                    if (hasHttp) return@forEach
                    hasHttp = true
                    inbound.addProperty("port", 10809)
                    inbound.addProperty("listen", "127.0.0.1")
                    inbound.add("settings", JsonObject())
                }
            }
            normalized.add(inbound)
        }
        if (!hasSocks) normalized.add(JsonObject().apply {
            addProperty("tag", "holovless-socks")
            addProperty("listen", "127.0.0.1")
            addProperty("port", 10808)
            addProperty("protocol", "socks")
            add("settings", JsonObject().apply { addProperty("udp", true) })
        })
        root.add("inbounds", normalized)
        return root.toString()
    }
}
