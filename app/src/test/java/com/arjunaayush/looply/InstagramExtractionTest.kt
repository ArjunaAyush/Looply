package com.arjunaayush.looply

import com.arjunaayush.looply.core.network.InstagramFeedClient
import com.arjunaayush.looply.features.instagram.InstagramDownloader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
