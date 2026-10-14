package com.v2ray.ang

import com.v2ray.ang.dto.ServerConfig
import com.v2ray.ang.util.AngConfigManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionImportTest {
    @Test
    fun largeSubscriptionSavesOnceAndKeepsOrderAndGroup() {
        val links = (0 until 5000).joinToString("\r\n") { index ->
            "hy2://synthetic-password@server-$index.example.test:443#Profile-$index"
        }
        var saves = 0
        var saved: List<ServerConfig> = emptyList()
        val count = AngConfigManager.importBatchConfig("\uFEFF $links\n", "test-group") { configs, subid ->
            saves++
            saved = configs
            assertEquals("test-group", subid)
        }
        assertEquals(5000, count)
        assertEquals(1, saves)
        assertEquals(5000, saved.size)
        saved.forEachIndexed { index, config ->
            assertEquals("Profile-$index", config.remarks)
            assertEquals("test-group", config.subscriptionId)
            assertEquals("server-$index.example.test", config.getProxyOutbound()?.getServerAddress())
        }
    }

    @Test
    fun emptyAndUnsupportedResponsesNeverReplaceExistingProfiles() {
        listOf(null, "", " \r\n\uFEFF ", "not-a-profile\nunsupported://example.test").forEach { response ->
            var saved = false
            assertEquals(0, AngConfigManager.importBatchConfig(response, "test-group") { _, _ ->
                saved = true
            })
            assertTrue(!saved)
        }
    }

    @Test
    fun mixedSubscriptionSkipsUnsupportedLinesWithoutDroppingValidProfiles() {
        val response = "unsupported://example.test\n" +
                "vless://00000000-0000-0000-0000-000000000001@vless.example.test:443?type=xhttp&security=reality&pbk=synthetic-key#VLESS\n" +
                "hy2://synthetic-password@hy2.example.test:443#HY2"
        var saved: List<ServerConfig> = emptyList()
        val count = AngConfigManager.importBatchConfig(response, "test-group") { configs, _ -> saved = configs }
        assertEquals(2, count)
        assertEquals(listOf("VLESS", "HY2"), saved.map { it.remarks })
        assertEquals("synthetic-key", saved[0].outboundBean?.streamSettings?.realitySettings?.publicKey)
    }
}
