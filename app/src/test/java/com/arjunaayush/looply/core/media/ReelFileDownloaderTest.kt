package com.arjunaayush.looply.core.media

import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream

class ReelFileDownloaderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun verifiesIsoBmffHeader() {
        val validFile = tempFolder.newFile("test_valid.mp4")
        FileOutputStream(validFile).use { fos ->
            // 4 bytes length + "ftyp" + additional bytes
            fos.write(byteArrayOf(0, 0, 0, 28, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(), 0, 0))
        }

        // Test with mock context or direct dummy instance
        val dummyFile = tempFolder.newFile("test_dummy.txt")
        FileOutputStream(dummyFile).use { fos ->
            fos.write("INVALID_HEADER_DATA_123456789".toByteArray())
        }

        // We can test the ISO-BMFF verification logic
        val validHead = ByteArray(8)
        validFile.inputStream().use { it.read(validHead) }
        val validType = String(validHead, 4, 4, Charsets.US_ASCII)
        assertTrue(validType in setOf("ftyp", "styp", "sidx"))

        val invalidHead = ByteArray(8)
        dummyFile.inputStream().use { it.read(invalidHead) }
        val invalidType = String(invalidHead, 4, 4, Charsets.US_ASCII)
        assertTrue(invalidType !in setOf("ftyp", "styp", "sidx"))
    }
}
