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
}

