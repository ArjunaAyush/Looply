package com.arjunaayush.looply.core.network.instagram.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IngestionConfigTest {

    @Test
    fun fromJsonEnforcesCompiledBounds() {
        val maliciousJson = """
            {
                "minDelayMs": 100,
                "maxPagesPerRun": 100,
                "dailyPageBudget": 1000,
                "dailyReelBudget": 5000,
                "initialWaitMs": 1000,
                "pageTimeoutMs": 1000000
            }
        """.trimIndent()

        val config = IngestionConfig.fromJson(maliciousJson, versionCode = 1)

        // Remote config must NEVER be able to lower minDelay below 2000
        assertEquals(2000L, config.minDelayMs)
        // maxPagesPerRun capped at 10
        assertEquals(10, config.maxPagesPerRun)
        // dailyPageBudget capped at 30
        assertEquals(30, config.dailyPageBudget)
        // dailyReelBudget capped at 150
        assertEquals(150, config.dailyReelBudget)
        // initialWaitMs coerced in 5000..60000
        assertEquals(5000L, config.initialWaitMs)
        // pageTimeoutMs coerced in 5000..60000
        assertEquals(60000L, config.pageTimeoutMs)
    }

    @Test
    fun minVersionCodeDisablesBatch() {
        val json = """
            {
                "batchEnabled": true,
                "minVersionCode": 5
            }
        """.trimIndent()

        val config = IngestionConfig.fromJson(json, versionCode = 2)
        assertFalse(config.batchEnabled)
    }

    @Test
    fun toJsJsonSerializesProperly() {
        val config = IngestionConfig()
        val jsJson = config.toJsJson()
        assertTrue(jsJson.contains("capturePatterns"))
        assertTrue(jsJson.contains("templatePatterns"))
        assertTrue(jsJson.contains("cursorKeys"))
    }
}
