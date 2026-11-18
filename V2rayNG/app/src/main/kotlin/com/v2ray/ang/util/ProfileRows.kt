package com.v2ray.ang.util

import com.google.gson.Gson
import com.v2ray.ang.dto.ServerConfig

/** List metadata never needs to retain or decode a full custom JSON document. */
object ProfileRows {
    private val gson = Gson()

    fun encode(config: ServerConfig): String = gson.toJson(config.copy(
            outboundBean = config.getProxyOutbound(), fullConfig = null, rawConfig = null))

    fun decode(json: String): ServerConfig = gson.fromJson(json, ServerConfig::class.java)
}
