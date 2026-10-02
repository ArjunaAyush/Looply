package com.arjunaayush.looply

import com.arjunaayush.looply.core.network.InstagramFeedClient
import com.arjunaayush.looply.features.instagram.InstagramDownloader
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramExtractionTest {

    @Test
    fun testExtractShortcode() {
        val urls = listOf(
            "https://www.instagram.com/reel/DFb8-R6oEJu/" to "DFb8-R6oEJu",
            "https://www.instagram.com/reel/DFb8-R6oEJu/?igsh=MWF5eTFxYnlv" to "DFb8-R6oEJu",
            "https://www.instagram.com/p/DFb8-R6oEJu/" to "DFb8-R6oEJu",
            "https://instagram.com/share/reel/DFb8-R6oEJu/" to "DFb8-R6oEJu",
            "https://www.instagram.com/reels/DFb8-R6oEJu" to "DFb8-R6oEJu"
        )

        for ((url, expected) in urls) {
            val extracted = InstagramDownloader.extractShortcode(url)
            assertEquals("Failed for URL: $url", expected, extracted)
        }
    }

    @Test
    fun testShortcodeToMediaId() {
        val shortcode = "DFb8-R6oEJu"
        val mediaId = InstagramDownloader.shortcodeToMediaId(shortcode)
        assertEquals("3556704493374554734", mediaId)

        // Verify roundtrip with pkToShortcode
        val roundtripCode = InstagramFeedClient.pkToShortcode(mediaId)
        assertEquals(shortcode, roundtripCode)
    }

    @Test
    fun testIsValidVideoStream() {
        assertTrue(InstagramDownloader.isValidVideoStream("https://instagram.fdel1-2.fna.fbcdn.net/v/t50.2886-16/video.mp4?efg=123"))
        assertTrue(InstagramDownloader.isValidVideoStream("https://scontent.cdninstagram.com/o1/v/t16/f1/m69/reel_stream.mp4"))

        assertFalse(InstagramDownloader.isValidVideoStream("https://instagram.fdel1-2.fna.fbcdn.net/v/t51.2885-15/photo.jpg"))
        assertFalse(InstagramDownloader.isValidVideoStream("https://scontent.cdninstagram.com/thumbnail.png"))
        assertFalse(InstagramDownloader.isValidVideoStream("https://instagram.com/image.webp"))
    }

    @Test
    fun testCleanCdnVideoUrl() {
        val raw = "https://instagram.com/video.mp4?bytestart=0&byteend=500000\\u0026tag=test&amp;key=val"
        val cleaned = InstagramDownloader.cleanCdnVideoUrl(raw)
        assertFalse(cleaned.contains("bytestart="))
        assertFalse(cleaned.contains("byteend="))
        assertFalse(cleaned.contains("\\u0026"))
        assertFalse(cleaned.contains("&amp;"))
        assertTrue(cleaned.contains("&tag=test"))
        assertTrue(cleaned.contains("&key=val"))
    }

    @Test
    fun testExtractCsrfToken() {
        val client = InstagramFeedClient(null)
        val cookies = "mid=xyz; csrftoken=a1b2c3d4e5f6; ds_user_id=12345; sessionid=67890%3Aabc"
        val csrf = client.extractCsrfToken(cookies)
        assertEquals("a1b2c3d4e5f6", csrf)
    }

    @Test
    fun testParseMediaObject() {
        val client = InstagramFeedClient(null)
        val jsonStr = """
        {
            "code": "DFb8-R6oEJu",
            "id": "3556704493374554734",
            "video_versions": [
                {
                    "url": "https://scontent.cdninstagram.com/video.mp4",
                    "width": 1080,
                    "height": 1920
                }
            ],
            "user": {
                "username": "looply_creator",
                "full_name": "Looply Creator"
            },
            "caption": {
                "text": "Check out this loop!"
            }
        }
        """.trimIndent()

        val parsed = client.parseMediaObject(JSONObject(jsonStr))
        assertNotNull(parsed)
        assertEquals("DFb8-R6oEJu", parsed?.shortcode)
        assertEquals("looply_creator", parsed?.creatorHandle)
        assertEquals("Check out this loop!", parsed?.title)
        assertEquals("https://scontent.cdninstagram.com/video.mp4", parsed?.videoUrl)
    }
}
