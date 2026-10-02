package com.arjunaayush.looply.core.network.instagram.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeEventTest {

    @Test
    fun parsePayloadEvent() {
        val raw = """{"type":"payload","url":"https://instagram.com/api/v1/clips/","body":"{}"}"""
        val event = BridgeEvent.parse(raw)
        assertTrue(event is BridgeEvent.Payload)
        val payload = event as BridgeEvent.Payload
        assertEquals("https://instagram.com/api/v1/clips/", payload.url)
        assertEquals("{}", payload.body)
    }

    @Test
    fun parseTemplateEvent() {
        val raw = """{"type":"template","url":"https://instagram.com/api/v1/clips/"}"""
        val event = BridgeEvent.parse(raw)
        assertTrue(event is BridgeEvent.Template)
        assertEquals("https://instagram.com/api/v1/clips/", (event as BridgeEvent.Template).url)
    }

    @Test
    fun parsePageEvent() {
        val raw = """{"type":"page","reqId":"uuid-123","status":200,"body":"{}"}"""
        val event = BridgeEvent.parse(raw)
        assertTrue(event is BridgeEvent.Page)
        val page = event as BridgeEvent.Page
        assertEquals("uuid-123", page.reqId)
        assertEquals(200, page.status)
    }

    @Test
    fun parseFailureEvent() {
        val raw = """{"type":"failure","reqId":"uuid-456","code":"NO_TEMPLATE","message":"err"}"""
        val event = BridgeEvent.parse(raw)
        assertTrue(event is BridgeEvent.Failure)
        val failure = event as BridgeEvent.Failure
        assertEquals("uuid-456", failure.reqId)
        assertEquals("NO_TEMPLATE", failure.code)
    }

    @Test
    fun parseRedirectEvent() {
        val raw = """{"type":"redirect","shortcode":"Dd796AMhx_x","url":"https://www.instagram.com/reels/Dd796AMhx_x/","videoUrl":"https://cdn.instagram.com/v.mp4","author":"creator123"}"""
        val event = BridgeEvent.parse(raw)
        assertTrue(event is BridgeEvent.ReelRedirect)
        val redirect = event as BridgeEvent.ReelRedirect
        assertEquals("Dd796AMhx_x", redirect.shortcode)
        assertEquals("https://www.instagram.com/reels/Dd796AMhx_x/", redirect.url)
        assertEquals("https://cdn.instagram.com/v.mp4", redirect.videoUrl)
        assertEquals("creator123", redirect.author)
    }

    @Test
    fun parseInvalidReturnsNull() {
        assertNull(BridgeEvent.parse("not json"))
        assertNull(BridgeEvent.parse("""{"type":"unknown"}"""))
    }
}
