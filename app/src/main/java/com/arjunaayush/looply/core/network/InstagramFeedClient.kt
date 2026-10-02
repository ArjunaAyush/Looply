package com.arjunaayush.looply.core.network

import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility helper for Instagram ID and shortcode conversions.
 * Direct HTTP API scraping has been eliminated in favor of WebViewReelSource.
 */
@Singleton
class InstagramFeedClient @Inject constructor() {

    companion object {
        private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

        fun pkToShortcode(pkStr: String): String {
            val cleanPk = pkStr.split("_")[0]
            var temp = cleanPk.toBigIntegerOrNull() ?: return ""
            val base = BigInteger.valueOf(64)
            val sb = StringBuilder()
            while (temp > BigInteger.ZERO) {
                val rem = temp.mod(base).toInt()
                sb.append(ALPHABET[rem])
                temp = temp.divide(base)
            }
            return sb.reverse().toString()
        }

        fun shortcodeToMediaId(shortcode: String): String {
            val cleanCode = if (shortcode.length > 28) shortcode.substring(0, shortcode.length - 28) else shortcode
            var id = BigInteger.ZERO
            val base = BigInteger.valueOf(64)
            for (ch in cleanCode) {
                val index = ALPHABET.indexOf(ch)
                if (index >= 0) {
                    id = id.multiply(base).add(BigInteger.valueOf(index.toLong()))
                }
            }
            return id.toString()
        }
    }
}
