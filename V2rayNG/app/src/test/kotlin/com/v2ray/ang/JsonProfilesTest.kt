package com.v2ray.ang

import com.google.gson.JsonParser
import com.v2ray.ang.util.AngConfigManager
import com.v2ray.ang.util.JsonProfiles
import com.v2ray.ang.util.ProfileSorting
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class JsonProfilesTest {
    private val example = """{"remarks":"Example","outbounds":[{"protocol":"vless","settings":{"vnext":[{"address":"example.test","port":443,"users":[{"id":"00000000-0000-0000-0000-000000000001"}]}]},"newCoreField":{"preserve":true}}],"burstObservatory":{"pingConfig":{"interval":"1m"}},"routing":{"balancers":[{"tag":"balance"}]}}"""

    @Test fun preservesUnknownFieldsAndExtractsMetadata() {
        val config = JsonProfiles.parse(example, "group").single()
        assertEquals("Example", config.remarks)
        assertEquals("group", config.subscriptionId)
        assertEquals("example.test:443", config.getV2rayPointDomainAndPort())
        assertTrue(JsonParser.parseString(example) == JsonParser.parseString(config.rawConfig))
    }

    @Test fun importsArraysInOneBatch() {
        var saves = 0
        val count = AngConfigManager.importBatchConfig("[$example,$example]", "group") { configs, group ->
            saves++
            assertEquals(2, configs.size)
            assertEquals("group", group)
        }
        assertEquals(2, count)
        assertEquals(1, saves)
    }

    @Test fun bridgeListenersPreserveTagsAndUnknownSettings() {
        val root = JsonParser.parseString(example).asJsonObject
        root.add("inbounds", JsonParser.parseString("""[{"tag":"original-socks","protocol":"socks","port":8888,"sniffing":{"enabled":true}},{"tag":"original-http","protocol":"http","port":8889}]"""))
        val result = JsonParser.parseString(JsonProfiles.forConnection(root.toString())).asJsonObject
        assertTrue(root["outbounds"] == result["outbounds"])
        assertTrue(root["burstObservatory"] == result["burstObservatory"])
        val inbound = result.getAsJsonArray("inbounds")[0].asJsonObject
        assertEquals(10808, inbound["port"].asInt)
        assertEquals("127.0.0.1", inbound["listen"].asString)
        assertEquals("original-socks", inbound["tag"].asString)
        assertTrue(inbound["sniffing"].asJsonObject["enabled"].asBoolean)
    }

    @Test fun addsMissingBridgeAndRejectsNonConfigurations() {
        val result = JsonParser.parseString(JsonProfiles.forConnection(example)).asJsonObject
        assertEquals(10808, result.getAsJsonArray("inbounds")[0].asJsonObject["port"].asInt)
        listOf("{}", "[]", "[{}]", "null").forEach { invalid ->
            assertTrue(runCatching { JsonProfiles.parse(invalid) }.isFailure)
        }
    }

    @Test fun editedHysteriaLinkChangesConnectionParameters() {
        val config = AngConfigManager.parseHysteria2Config("hy2://updated-password@edited.example.test:8443?sni=tls.example.test&obfs=salamander&obfs-password=mask#Edited")!!
        assertEquals("edited.example.test:8443", config.getV2rayPointDomainAndPort())
        assertEquals("updated-password", config.outboundBean!!.getPassword())
        assertEquals("tls.example.test", config.outboundBean!!.streamSettings!!.tlsSettings!!.serverName)
    }

    @Test fun sortsLatencyWithUntestedAndFailedLastAndKeepsTiesStable() {
        val entries = listOf(
                ProfileSorting.Entry("failed", "Z", "VLESS", -1, 1),
                ProfileSorting.Entry("slow", "B", "VLESS", 150, 2),
                ProfileSorting.Entry("untested", "C", "VLESS", 0, 3),
                ProfileSorting.Entry("fast", "a", "HYSTERIA2", 50, 4),
                ProfileSorting.Entry("tie", "A", "VLESS", 50, 5))
        assertEquals(listOf("fast", "tie", "slow", "untested", "failed"), ProfileSorting.sort(entries, 1))
        assertEquals(listOf("fast", "tie", "slow", "untested", "failed"), ProfileSorting.sort(entries, 0))
        assertEquals("tie", ProfileSorting.sort(entries, 3).first())
    }

    @Test fun suppliedJsonFixturePreservesEveryProfileWithoutLoggingCredentials() {
        val path = System.getenv("HOLOVLESS_JSON_FIXTURE")
        assumeTrue(!path.isNullOrEmpty())
        val text = File(path!!).readText()
        val input = JsonParser.parseString(text).asJsonArray
        val profiles = JsonProfiles.parse(text, "fixture")
        assertEquals(input.size(), profiles.size)
        assertTrue(profiles.isNotEmpty())
        profiles.forEachIndexed { index, config ->
            assertTrue(JsonParser.parseString(config.rawConfig) == input[index])
            val prepared = JsonParser.parseString(JsonProfiles.forConnection(config.rawConfig!!)).asJsonObject
            assertTrue(prepared["outbounds"] == input[index].asJsonObject["outbounds"])
        }
    }
}
