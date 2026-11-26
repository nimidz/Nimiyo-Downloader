package com.nimiyo.nativeapp.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.util.Locale

data class CookiePlatformEntry(
    val domain: String,
    val isLoggedIn: Boolean = false,
    val cookieCount: Int = 0,
    val updatedAtMs: Long = 0L,
)

/**
 * Local-only vault managing per-platform login cookies (captured via built-in WebView)
 * and exporting a standard Netscape cookies.txt file for yt-dlp (--cookies).
 */
class CookieVaultManager(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("nimiyo_cookie_vault", Context.MODE_PRIVATE)
    private val cookiesDir = File(appContext.filesDir, "cookies").apply { mkdirs() }
    private val cookiesFile = File(cookiesDir, "nimiyo_cookies.txt")

    private val _entries = MutableStateFlow<List<CookiePlatformEntry>>(emptyList())
    val entries: StateFlow<List<CookiePlatformEntry>> = _entries.asStateFlow()

    @Volatile
    var revision: Long = 0L
        private set

    @Volatile
    private var hasActiveCookieLines: Boolean = false

    init {
        loadFromPrefs()
    }

    @Synchronized
    fun addPlatformDomain(rawInput: String): CookiePlatformEntry? {
        val domain = normalizeDomain(rawInput)
        if (domain.isBlank() || !domain.contains('.')) return null
        val current = _entries.value.toMutableList()
        val existing = current.firstOrNull { it.domain == domain }
        if (existing != null) return existing
        val entry = CookiePlatformEntry(domain = domain)
        current.add(0, entry)
        saveMetadata(current)
        _entries.value = current
        return entry
    }

    @Synchronized
    fun saveCookiesFromWebView(rawDomain: String, cookiesByUrl: Map<String, String>): CookiePlatformEntry? {
        val domain = normalizeDomain(rawDomain)
        if (domain.isBlank()) return null

        val netscapeLines = LinkedHashSet<String>()
        val rawPairs = LinkedHashMap<String, String>()
        val nowSec = System.currentTimeMillis() / 1000L

        for ((urlStr, cookieHeader) in cookiesByUrl) {
            if (cookieHeader.isBlank()) continue
            val host = extractHost(urlStr).ifBlank { domain }
            parseRawCookieHeaderToNetscapeLines(host, cookieHeader, nowSec).forEach { line ->
                netscapeLines.add(line)
            }
            cookieHeader.split(';').forEach { part ->
                val idx = part.indexOf('=')
                if (idx > 0) {
                    val k = part.substring(0, idx).trim()
                    val v = part.substring(idx + 1).trim()
                    if (k.isNotEmpty()) rawPairs[k] = v
                }
            }
        }

        if (netscapeLines.isEmpty()) return null

        val headerStr = rawPairs.entries.joinToString("; ") { "${it.key}=${it.value}" }
        prefs.edit()
            .putString("lines_$domain", netscapeLines.joinToString("\n"))
            .putString("header_$domain", headerStr)
            .apply()

        val updated = _entries.value.toMutableList()
        val idx = updated.indexOfFirst { it.domain == domain }
        val newEntry = CookiePlatformEntry(
            domain = domain,
            isLoggedIn = true,
            cookieCount = rawPairs.size,
            updatedAtMs = System.currentTimeMillis(),
        )
        if (idx >= 0) {
            updated[idx] = newEntry
        } else {
            updated.add(0, newEntry)
        }
        saveMetadata(updated)
        _entries.value = updated
        rebuildCookiesFile(updated)
        revision++
        return newEntry
    }

    @Synchronized
    fun clearPlatformCookies(rawDomain: String) {
        val domain = normalizeDomain(rawDomain)
        prefs.edit()
            .remove("lines_$domain")
            .remove("header_$domain")
            .apply()
        val updated = _entries.value.map {
            if (it.domain == domain) it.copy(isLoggedIn = false, cookieCount = 0, updatedAtMs = 0L) else it
        }
        saveMetadata(updated)
        _entries.value = updated
        rebuildCookiesFile(updated)
        revision++
    }

    @Synchronized
    fun removePlatform(rawDomain: String) {
        val domain = normalizeDomain(rawDomain)
        prefs.edit()
            .remove("lines_$domain")
            .remove("header_$domain")
            .apply()
        val updated = _entries.value.filterNot { it.domain == domain }
        saveMetadata(updated)
        _entries.value = updated
        rebuildCookiesFile(updated)
        revision++
    }

    @Synchronized
    fun activeCookiesFile(cookiesEnabled: Boolean): File? {
        if (!cookiesEnabled || !hasActiveCookieLines) return null
        if (!cookiesFile.exists() || cookiesFile.length() == 0L) return null
        val hasEntry = _entries.value.any { it.isLoggedIn }
        return cookiesFile.takeIf { hasEntry }
    }

    @Synchronized
    fun cookieHeaderForUrl(url: String, cookiesEnabled: Boolean): String? {
        if (!cookiesEnabled) return null
        if (!url.trim().startsWith("https://", ignoreCase = true)) return null
        val host = extractHost(url)
        if (host.isBlank()) return null
        val matched = _entries.value.filter { entry ->
            entry.isLoggedIn && matchesRelatedDomain(host, entry.domain)
        }
        if (matched.isEmpty()) return null
        val merged = matched.mapNotNull { entry ->
            prefs.getString("header_${entry.domain}", null)?.takeIf { it.isNotBlank() }
        }.joinToString("; ")
        return merged.ifBlank { null }
    }

    private fun loadFromPrefs() {
        val raw = prefs.getString("platforms_json", null)
        val parsed = if (!raw.isNullOrBlank()) {
            runCatching {
                val arr = JSONArray(raw)
                buildList {
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val d = normalizeDomain(obj.optString("domain"))
                        if (d.isBlank()) continue
                        add(
                            CookiePlatformEntry(
                                domain = d,
                                isLoggedIn = obj.optBoolean("isLoggedIn", false),
                                cookieCount = obj.optInt("cookieCount", 0),
                                updatedAtMs = obj.optLong("updatedAtMs", 0L),
                            )
                        )
                    }
                }
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        _entries.value = parsed
        rebuildCookiesFile(parsed)
    }

    private fun saveMetadata(list: List<CookiePlatformEntry>) {
        val arr = JSONArray()
        for (item in list) {
            arr.put(
                JSONObject().apply {
                    put("domain", item.domain)
                    put("isLoggedIn", item.isLoggedIn)
                    put("cookieCount", item.cookieCount)
                    put("updatedAtMs", item.updatedAtMs)
                }
            )
        }
        prefs.edit().putString("platforms_json", arr.toString()).apply()
    }

    private fun rebuildCookiesFile(list: List<CookiePlatformEntry>) {
        val allLines = LinkedHashSet<String>()
        for (item in list) {
            if (!item.isLoggedIn) continue
            val block = prefs.getString("lines_${item.domain}", null).orEmpty()
            block.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach {
                allLines.add(it)
            }
        }
        hasActiveCookieLines = allLines.isNotEmpty()
        val content = buildNetscapeCookieFileContent(allLines.toList())
        val tmpFile = File(cookiesDir, "nimiyo_cookies.txt.tmp")
        tmpFile.writeText(content, Charsets.UTF_8)
        if (!tmpFile.renameTo(cookiesFile)) {
            cookiesFile.writeText(content, Charsets.UTF_8)
            tmpFile.delete()
        }
    }

    companion object {
        fun normalizeDomain(rawInput: String): String {
            val cleaned = rawInput.trim().lowercase(Locale.US)
                .removePrefix("https://")
                .removePrefix("http://")
                .substringBefore('/')
                .substringBefore('?')
                .substringBefore('#')
                .substringBefore(':')
                .removePrefix("www.")
                .removePrefix("m.")
                .removePrefix(".")
            return cleaned
        }

        fun extractHost(url: String): String {
            val parsed = runCatching { URI(url).host }.getOrNull()
            val raw = parsed ?: Regex("""^https?://([^/:?#]+)""", RegexOption.IGNORE_CASE)
                .find(url)?.groupValues?.getOrNull(1).orEmpty()
            return normalizeDomain(raw)
        }

        private fun hostMatches(host: String, domain: String): Boolean =
            host == domain || host.endsWith(".$domain")

        fun matchesRelatedDomain(host: String, savedDomain: String): Boolean {
            val cleanHost = normalizeDomain(host)
            val cleanSaved = normalizeDomain(savedDomain)
            if (cleanHost.isBlank() || cleanSaved.isBlank()) return false
            if (hostMatches(cleanHost, cleanSaved)) return true
            if ((cleanSaved == "rednote.com" || cleanSaved == "xiaohongshu.com") &&
                (hostMatches(cleanHost, "rednote.com") || hostMatches(cleanHost, "xiaohongshu.com"))
            ) {
                return true
            }
            if ((cleanSaved == "twitter.com" || cleanSaved == "x.com") &&
                (hostMatches(cleanHost, "twitter.com") || hostMatches(cleanHost, "x.com"))
            ) {
                return true
            }
            return false
        }

        fun parseRawCookieHeaderToNetscapeLines(
            domain: String,
            rawCookieHeader: String,
            nowSec: Long = System.currentTimeMillis() / 1000L,
        ): List<String> {
            val cleanDomain = normalizeDomain(domain)
            if (cleanDomain.isBlank() || rawCookieHeader.isBlank()) return emptyList()
            val expiry = nowSec + 365L * 24L * 3600L
            val netscapeDomain = ".$cleanDomain"
            return rawCookieHeader.split(';')
                .mapNotNull { segment ->
                    val idx = segment.indexOf('=')
                    if (idx <= 0) return@mapNotNull null
                    val name = segment.substring(0, idx).trim()
                    val value = segment.substring(idx + 1).trim()
                    if (name.isEmpty() ||
                        name.contains('\t') || name.contains('\n') || name.contains('\r') ||
                        value.contains('\t') || value.contains('\n') || value.contains('\r')
                    ) {
                        return@mapNotNull null
                    }
                    "$netscapeDomain\tTRUE\t/\tTRUE\t$expiry\t$name\t$value"
                }
        }

        fun buildNetscapeCookieFileContent(lines: List<String>): String = buildString {
            append("# Netscape HTTP Cookie File\n")
            append("# Generated locally by NIMIYO Downloader\n\n")
            lines.forEach { line ->
                append(line).append('\n')
            }
        }
    }
}

