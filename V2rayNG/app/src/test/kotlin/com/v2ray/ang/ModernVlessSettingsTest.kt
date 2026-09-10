package com.v2ray.ang

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.v2ray.ang.dto.V2rayConfig
import com.v2ray.ang.util.AngConfigManager
import com.v2ray.ang.util.Utils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Test

class ModernVlessSettingsTest {
    @Test
    fun stripsBomAndWhitespaceBeforeSubscriptionBase64Decode() {
        assertEquals("dmxlc3M6Ly9leGFtcGxl", Utils.normalizeBase64Input(
                "\uFEFF  dmxlc3M6Ly9leGFtcGxl\r\n"))
    }

    @Test
    fun usesCompleteSubscriptionHostAsGroupName() {
        assertEquals("vpn.smth.more.example.com", Utils.subscriptionGroupName(
                "https://VPN.Smth.More.Example.com/path?token=secret"))
        assertEquals("", Utils.subscriptionGroupName("not a subscription URL"))
    }

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

    @Test
    fun parsesHysteria2AndHy2LinksIntoNativeXrayOutbound() {
        val config = AngConfigManager.parseHysteria2Config(
                "hy2://test%2Bpassword@hy2.example.test:443" +
                        "?sni=edge.example.test&insecure=1&obfs=salamander" +
                        "&obfs-password=mask%20secret&mport=20000-30000" +
                        "&hopInterval=15&upmbps=25&downmbps=100#Test%20HY2")

        assertNotNull(config)
        val outbound = config!!.outboundBean!!
        assertEquals("hysteria", outbound.protocol)
        assertEquals("hy2.example.test", outbound.getServerAddress())
        assertEquals(443, outbound.getServerPort())
        assertEquals("test+password", outbound.getPassword())
        assertEquals("Test HY2", config.remarks)

        val json = JsonParser().parse(Gson().toJson(outbound)).asJsonObject
        assertEquals(2, json["settings"].asJsonObject["version"].asInt)
        val stream = json["streamSettings"].asJsonObject
        assertEquals("hysteria", stream["network"].asString)
        assertEquals("tls", stream["security"].asString)
        assertEquals("test+password", stream["hysteriaSettings"].asJsonObject["auth"].asString)
        assertEquals("edge.example.test", stream["tlsSettings"].asJsonObject["serverName"].asString)
        assertTrue(stream["tlsSettings"].asJsonObject["allowInsecure"].asBoolean)
        val finalMask = stream["finalmask"].asJsonObject
        assertEquals("mask secret", finalMask["udp"].asJsonArray[0].asJsonObject
                .getAsJsonObject("settings")["password"].asString)
        assertEquals("25 mbps", finalMask["quicParams"].asJsonObject["brutalUp"].asString)
        assertEquals("100 mbps", finalMask["quicParams"].asJsonObject["brutalDown"].asString)
        assertEquals("20000-30000", finalMask["quicParams"].asJsonObject
                .getAsJsonObject("udpHop")["ports"].asString)
    }

    @Test
    fun rejectsUnsupportedHysteria2Obfuscation() {
        assertEquals(null, AngConfigManager.parseHysteria2Config(
                "hysteria2://password@hy2.example.test:443?obfs=unknown"))
    }
}
