package com.arjunaayush.looply.core.media

import android.util.Xml
import java.io.StringReader
import org.xmlpull.v1.XmlPullParser

data class DashTracks(val videoUrl: String, val audioUrl: String?)

object DashManifestParser {

    private data class Rep(val isVideo: Boolean, val isAudio: Boolean, val bandwidth: Long, val height: Int, val url: String)

    fun parse(mpd: String, maxHeight: Int = 1920): DashTracks? {
        val reps = mutableListOf<Rep>()
        val p = Xml.newPullParser().apply { setInput(StringReader(mpd)) }

        var setMime = ""
        var repMime = ""
        var bandwidth = 0L
        var height = 0
        var inRep = false

        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "AdaptationSet" -> setMime = p.attr("mimeType") + p.attr("contentType")
                    "Representation" -> {
                        inRep = true
                        repMime = p.attr("mimeType")
                        bandwidth = p.attr("bandwidth").toLongOrNull() ?: 0L
                        height = p.attr("height").toIntOrNull() ?: 0
                    }
                    "BaseURL" -> if (inRep) {
                        val url = p.nextText().trim()
                        val mime = repMime + setMime
                        reps += Rep("video" in mime, "audio" in mime, bandwidth, height, url)
                    }
                }
                XmlPullParser.END_TAG -> if (p.name == "Representation") inRep = false
            }
        }
        val video = reps.filter { it.isVideo && it.height in 1..maxHeight }.maxByOrNull { it.bandwidth }
            ?: reps.filter { it.isVideo }.minByOrNull { it.height }
            ?: return null
        val audio = reps.filter { it.isAudio }.maxByOrNull { it.bandwidth }
        return DashTracks(video.url, audio?.url)
    }

    private fun XmlPullParser.attr(name: String): String = getAttributeValue(null, name).orEmpty()
}
