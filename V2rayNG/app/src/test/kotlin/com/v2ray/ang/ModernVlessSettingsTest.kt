package com.v2ray.ang

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.v2ray.ang.dto.V2rayConfig
import com.v2ray.ang.util.AngConfigManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModernVlessSettingsTest {
    @Test
    fun parsesRealityQueryParametersWithoutTruncatingValues() {
        val query = AngConfigManager.parseQueryParameters(
                "pbk=test-public-key&sid=0123456789abcdef&fp=chrome&spx=%2Ftest%3Fa%3Db&pqv=test-pq-key")

        assertEquals("test-public-key", query["pbk"])
        assertEquals("0123456789abcdef", query["sid"])
        assertEquals("chrome", query["fp"])
        assertEquals("/test?a=b", query["spx"])
        assertEquals("test-pq-key", query["pqv"])
    }

    @Test
    fun emitsPopulatedRealityAndXhttpSettings() {
        val streamSettings = V2rayConfig.OutboundBean.StreamSettingsBean()
        streamSettings.populateTransportSettings(
                "xhttp", null, "cdn.example.test", "/xhttp-path", null,
                null, null, "auto", null)
        streamSettings.populateTlsSettings(
                "reality", false, "origin.example.test", "chrome",
                "test-public-key", "0123456789abcdef", "/", "test-pq-key")

        val json = JsonParser().parse(Gson().toJson(streamSettings)).asJsonObject
        assertEquals("xhttp", json["network"].asString)
        assertEquals("reality", json["security"].asString)
        assertFalse(json.has("tlsSettings"))

        val xhttp = json["xhttpSettings"].asJsonObject
        assertEquals("cdn.example.test", xhttp["host"].asString)
        assertEquals("/xhttp-path", xhttp["path"].asString)
        assertEquals("auto", xhttp["mode"].asString)

        val reality = json["realitySettings"].asJsonObject
        assertEquals("origin.example.test", reality["serverName"].asString)
        assertEquals("chrome", reality["fingerprint"].asString)
        assertEquals("test-public-key", reality["publicKey"].asString)
        assertEquals("0123456789abcdef", reality["shortId"].asString)
        assertEquals("/", reality["spiderX"].asString)
        assertEquals("test-pq-key", reality["mldsa65Verify"].asString)
        assertTrue(reality.has("allowInsecure"))
    }
}
