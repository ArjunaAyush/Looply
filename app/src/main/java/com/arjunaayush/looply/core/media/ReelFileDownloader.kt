package com.arjunaayush.looply.core.media

import android.content.Context
import android.webkit.WebSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class UrlExpiredException : Exception("CDN url expired")
class CorruptMediaException(msg: String) : Exception(msg)

@Singleton
class ReelFileDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val userAgent by lazy {
        try {
            WebSettings.getDefaultUserAgent(context)
        } catch (_: Exception) {
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
        }
    }

    suspend fun download(url: String, target: File, onBytes: (Long) -> Unit = {}): File = withContext(Dispatchers.IO) {
        val part = File(target.parentFile, target.name + ".part")
        var existing = if (part.exists()) part.length() else 0L

        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Referer", "https://www.instagram.com/")
            if (existing > 0) setRequestProperty("Range", "bytes=$existing-")
        }
        try {
            when (conn.responseCode) {
                200 -> existing = 0L                                   // server ignored Range: restart
                206 -> Unit
                403, 410 -> throw UrlExpiredException()
                else -> throw CorruptMediaException("HTTP ${conn.responseCode}")
            }
            val expectedTotal = conn.contentLengthLong.takeIf { it > 0 }?.plus(existing)
            RandomAccessFile(part, "rw").use { raf ->
                raf.setLength(existing)
                raf.seek(existing)
                conn.inputStream.use { input ->
                    val buf = ByteArray(64 * 1024)
                    var total = existing
                    while (true) {
                        ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        raf.write(buf, 0, n)
                        total += n
                        onBytes(total)
                    }
                }
            }
            if (expectedTotal != null && part.length() != expectedTotal) {
                throw CorruptMediaException("size ${part.length()} != $expectedTotal")
            }
            verifyIsoBmff(part)
            if (!part.renameTo(target)) throw CorruptMediaException("rename failed")
            target
        } finally {
            conn.disconnect()
        }
    }

    /** Every MP4/fMP4 starts with a box whose type (bytes 4..8) is ftyp, styp or sidx. */
    fun verifyIsoBmff(file: File) {
        val head = ByteArray(8)
        RandomAccessFile(file, "r").use { if (it.read(head) < 8) throw CorruptMediaException("too small") }
        val type = String(head, 4, 4, Charsets.US_ASCII)
        if (type !in setOf("ftyp", "styp", "sidx")) {
            file.delete()
            throw CorruptMediaException("not ISO-BMFF: $type")
        }
    }
}
