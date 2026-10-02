package com.arjunaayush.looply.core.network.instagram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReelJsonNormalizerTest {

    private fun fixture(name: String) =
        requireNotNull(javaClass.classLoader?.getResource("fixtures/$name")).readText()

    @Test
    fun findsReelsUnderAnyWrapperKey() {
        val json = """
            {"items":[
              {"media":{"pk":"1","code":"A","video_versions":[{"url":"https://x/1.mp4?oe=6700AABB","width":720,"height":1280}]}},
              {"media_or_ad":{"pk":"2","code":"B","video_versions":[{"url":"https://x/2.mp4","width":720,"height":1280}]}},
              {"clip":{"media":{"pk":"3","code":"C","video_versions":[{"url":"https://x/3.mp4","width":720,"height":1280}]}}},
              {"pk":"4","code":"D","video_versions":[{"url":"https://x/4.mp4","width":720,"height":1280}]}
            ],"paging_info":{"max_id":"CURSOR","more_available":true}}
        """.trimIndent()
        val page = ReelJsonNormalizer.parse(json)
        assertEquals(listOf("1", "2", "3", "4"), page.items.map { it.mediaId })
        assertEquals("CURSOR", page.nextCursor)
        assertTrue(page.hasMore)
        assertEquals(0x6700AABBL, page.items.first().urlExpiresAtEpochSec)
    }

    @Test
    fun skipsAds() {
        val json = """{"items":[{"media_or_ad":{"pk":"9","ad_id":"x","video_versions":[{"url":"u"}]}}]}"""
        assertTrue(ReelJsonNormalizer.parse(json).items.isEmpty())
    }

    @Test
    fun ignoresNestedCommentCursors() {
        val json = """{"items":[{"media":{"pk":"1","video_versions":[{"url":"u"}],"comments":{"next_max_id":"WRONG","more_available":true}}}],"next_max_id":"RIGHT","more_available":true}"""
        assertEquals("RIGHT", ReelJsonNormalizer.parse(json).nextCursor)
    }

    @Test
    fun respectsMoreAvailableFalse() {
        val json = """{"items":[],"next_max_id":"Z","more_available":false}"""
        assertFalse(ReelJsonNormalizer.parse(json).hasMore)
    }

    @Test
    fun handlesAntiJsonPrefixAndMultiDocumentResponses() {
        val json = "for (;;);" + """{"a":{"pk":"5","video_versions":[{"url":"u","height":1280,"width":720}]}}""" +
            "\n" + """{"b":{"pk":"6","video_dash_manifest":"<MPD/>"}}"""
        assertEquals(setOf("5", "6"), ReelJsonNormalizer.parse(json).items.map { it.mediaId }.toSet())
    }

    @Test
    fun graphqlEndCursor() {
        val json = """{"data":{"xdt_api__v1__clips__home__connection_v2":{"edges":[{"node":{"media":{"pk":"7","video_versions":[{"url":"u"}]}}}],"page_info":{"end_cursor":"EC","has_next_page":true}}}}"""
        val page = ReelJsonNormalizer.parse(json)
        assertEquals("EC", page.nextCursor)
        assertEquals("7", page.items.single().mediaId)
    }

    @Test
    fun fixture202610ClipsHome() {
        val page = ReelJsonNormalizer.parse(fixture("clips_home_2026_10.json"))
        assertTrue(page.items.size >= 8)
        assertNotNull(page.nextCursor)
    }

    @Test
    fun responseClassifierVerdicts() {
        assertEquals(ResponseVerdict.RATE_LIMITED, ResponseClassifier.classify(429, ""))
        assertEquals(ResponseVerdict.RATE_LIMITED, ResponseClassifier.classify(403, ""))
        assertEquals(ResponseVerdict.RATE_LIMITED, ResponseClassifier.classify(200, "{\"message\":\"feedback_required\"}"))
        assertEquals(ResponseVerdict.CHALLENGE, ResponseClassifier.classify(200, "checkpoint_required"))
        assertEquals(ResponseVerdict.SESSION_EXPIRED, ResponseClassifier.classify(401, ""))
        assertEquals(ResponseVerdict.SESSION_EXPIRED, ResponseClassifier.classify(200, "login_required"))
        assertEquals(ResponseVerdict.TRANSIENT, ResponseClassifier.classify(500, ""))
        assertEquals(ResponseVerdict.OK, ResponseClassifier.classify(200, "{\"status\":\"ok\"}"))
    }
}
