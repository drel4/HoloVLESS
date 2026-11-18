package com.v2ray.ang

import com.v2ray.ang.util.JsonProfiles
import com.v2ray.ang.util.ProfileRows
import org.junit.Assert.*
import org.junit.Test

class ProfileRowsTest {
    @Test fun compactRowsRetainListFieldsWithoutFullJsonOrMutatingTheProfile() {
        val config = JsonProfiles.parse("""{"remarks":"Example","outbounds":[{"protocol":"vless","settings":{"vnext":[{"address":"example.test","port":443,"users":[]}]}}],"largeField":"${"x".repeat(100000)}"}""", "group").single()
        val original = config.rawConfig
        val encoded = ProfileRows.encode(config)
        val row = ProfileRows.decode(encoded)
        assertTrue(encoded.length < 2000)
        assertEquals("Example", row.remarks)
        assertEquals("group", row.subscriptionId)
        assertEquals(config.configType, row.configType)
        assertEquals(config.addedTime, row.addedTime)
        assertEquals("example.test:443", row.getV2rayPointDomainAndPort())
        assertNull(row.rawConfig)
        assertNull(row.fullConfig)
        assertEquals(original, config.rawConfig)
    }

    @Test fun editedMetadataProducesDifferentCacheContent() {
        val config = JsonProfiles.parse("""{"remarks":"Before","outbounds":[{"protocol":"freedom"}]}""").single()
        val before = ProfileRows.encode(config)
        config.remarks = "After"
        assertNotEquals(before, ProfileRows.encode(config))
        assertEquals("After", ProfileRows.decode(ProfileRows.encode(config)).remarks)
    }
}
