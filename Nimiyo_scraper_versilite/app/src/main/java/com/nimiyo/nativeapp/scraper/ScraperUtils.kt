package com.nimiyo.nativeapp.scraper

import android.util.Base64
import com.nimiyo.nativeapp.core.HttpResponseData
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object ScraperUtils {
    fun urlEncode(v: String): String = URLEncoder.encode(v, "UTF-8")

    fun urlDecode(v: String): String = runCatching { URLDecoder.decode(v, "UTF-8") }.getOrDefault(v)

    fun sha256Hex(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun sha256Bytes(text: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(StandardCharsets.UTF_8))

    fun b64Decode(b64: String): String {
        val normalized = b64.replace("-", "+").replace("_", "/")
        return String(Base64.decode(normalized, Base64.DEFAULT), StandardCharsets.UTF_8)
    }

    fun b64EncodeUrlSafe(raw: String): String {
        return Base64.encodeToString(raw.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
            .replace("+", "-")
            .replace("/", "_")
            .replace("=", "")
    }

    fun cleanUrl(raw: String): String {
        val extracted = PlatformDetector.extractFirstUrl(raw) ?: raw.trim()
        return extracted.trim()
    }

    fun decodeHtmlEntities(str: String): String {
        if (str.isBlank()) return ""
        return str
            .replace("&#064;", "@")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&#x27;", "'")
            .replace("&nbsp;", " ")
    }

    fun ensureOkOrThrow(res: HttpResponseData, context: String) {
        if (res.status >= 400) {
            throw IllegalStateException("[$context] HTTP ${res.status}")
        }
        val trimmed = res.body.trimStart()
        if (res.headers["content-type"]?.any { it.contains("application/json", true) } == true &&
            (trimmed.startsWith("<!DOCTYPE", true) || trimmed.startsWith("<html", true))
        ) {
            throw IllegalStateException("[$context] Unexpected HTML challenge page")
        }
    }

    /**
     * Unpack Dean Edwards eval(function(p,a,c,k,e,d)...) commonly returned by SnapSave / SnapTik.
     */
    fun decodeSnapSaveResponse(rawBody: String): String {
        val trimmed = rawBody.trim()
        if (trimmed.contains("<table", ignoreCase = true) || trimmed.contains("download-items", ignoreCase = true)) {
            return extractInnerHtml(trimmed) ?: trimmed
        }
        // Try Dean Edwards p,a,c,k,e,d unpacker
        val regex = Regex("""\}\('(.*)',\s*(\d+),\s*(\d+),\s*'([^']*)'\.split\('\|'\)""", RegexOption.DOT_MATCHES_ALL)
        val match = regex.find(trimmed)
        if (match != null) {
            val payload = match.groupValues[1]
            val radix = match.groupValues[2].toIntOrNull() ?: 36
            val count = match.groupValues[3].toIntOrNull() ?: 0
            val dict = match.groupValues[4].split("|")
            val unpacked = unpackPackedJs(payload, radix, count, dict)
            return extractInnerHtml(unpacked) ?: unpacked
        }
        return extractInnerHtml(trimmed) ?: trimmed
    }

    private fun extractInnerHtml(script: String): String? {
        val m = Regex("""innerHTML\s*=\s*"((?:\\.|[^"\\])*)"""", RegexOption.DOT_MATCHES_ALL).find(script)
            ?: Regex("""innerHTML\s*=\s*'((?:\\.|[^'\\])*)'""", RegexOption.DOT_MATCHES_ALL).find(script)
            ?: return null
        return m.groupValues[1]
            .replace("\\\"", "\"")
            .replace("\\'", "'")
            .replace("\\/", "/")
            .replace("\\n", "\n")
            .replace("\\r", "")
            .replace("\\t", "\t")
            .replace("\\\\", "\\")
    }

    private fun unpackPackedJs(p: String, a: Int, c: Int, k: List<String>): String {
        var result = p
        for (i in c - 1 downTo 0) {
            val replacement = k.getOrNull(i).orEmpty()
            if (replacement.isNotEmpty()) {
                val token = encodeBaseRadix(i, a)
                result = result.replace(Regex("\\b${Regex.escape(token)}\\b"), Regex.escapeReplacement(replacement))
            }
        }
        return result
    }

    private fun encodeBaseRadix(num: Int, radix: Int): String {
        val first = if (num < radix) "" else encodeBaseRadix(num / radix, radix)
        val rem = num % radix
        val ch = if (rem > 35) (rem + 29).toChar().toString() else rem.toString(36)
        return first + ch
    }
}

