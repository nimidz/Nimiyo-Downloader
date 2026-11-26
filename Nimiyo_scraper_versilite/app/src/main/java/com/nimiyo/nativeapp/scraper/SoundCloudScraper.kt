package com.nimiyo.nativeapp.scraper

import com.nimiyo.nativeapp.core.HttpClientFactory
import com.nimiyo.nativeapp.data.MediaCategory
import com.nimiyo.nativeapp.data.PlatformId
import com.nimiyo.nativeapp.data.ScrapeResult
import com.nimiyo.nativeapp.data.ScraperDownloadItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class SoundCloudScraper(private val http: HttpClientFactory) {

    @Volatile
    private var cachedClientId: String? = null

    private val clientIds = listOf(
        "3GKD969NJWD1eB6gvQJN0GnueYbcWszv",
        "2t9loNfh0ekOfjhUJywOcvwPwsqPvdca",
        "a3e059563d7fd3372b49b37f00a00bcf",
    )

    private fun fetchDynamicClientId(): String? {
        cachedClientId?.let { return it }
        return runCatching {
            val home = http.get("https://soundcloud.com/")
            val jsUrls = Regex("""https://a-v2\.sndcdn\.com/assets/[^"']+\.js""").findAll(home.body)
                .map { it.value }
                .toList()
                .takeLast(6)
                .reversed()
            for (jsUrl in jsUrls) {
                val js = runCatching { http.get(jsUrl).body }.getOrNull() ?: continue
                val m = Regex("""client_id:"([a-zA-Z0-9]{20,40})"""").find(js)
                if (m != null) {
                    val cid = m.groupValues[1]
                    cachedClientId = cid
                    return@runCatching cid
                }
            }
            null
        }.getOrNull()
    }

    suspend fun scrape(url: String, server: String): ScrapeResult = withContext(Dispatchers.IO) {
        var cleanUrl = ScraperUtils.cleanUrl(url)
        if (cleanUrl.contains("on.soundcloud.com") || cleanUrl.contains("goo.gl")) {
            runCatching {
                val res = http.get(cleanUrl, mapOf("User-Agent" to HttpClientFactory.WHATSAPP_UA))
                if (res.finalUrl.contains("soundcloud.com") && !res.finalUrl.contains("on.soundcloud")) {
                    cleanUrl = res.finalUrl
                } else {
                    val m = Regex("""https://soundcloud\.com/[a-zA-Z0-9_.-]+/[a-zA-Z0-9_.-]+""").find(res.body)
                    if (m != null) cleanUrl = m.value
                }
            }
        }
        cleanUrl = cleanUrl.substringBefore("?")
            .replace("://m.soundcloud.com/", "://soundcloud.com/", ignoreCase = true)

        val order = when (server) {
            "forhub" -> listOf("forhub", "direct", "klickaud")
            "klickaud" -> listOf("klickaud", "direct", "forhub")
            else -> listOf("direct", "forhub", "klickaud")
        }

        var primaryError: Throwable? = null
        var lastError: Throwable? = null
        for ((idx, srv) in order.withIndex()) {
            val res = runCatching {
                when (srv) {
                    "direct" -> scrapeDirectApiV2(cleanUrl)
                    "forhub" -> scrapeForHub(cleanUrl)
                    else -> scrapeKlickAud(cleanUrl)
                }
            }
            if (res.isSuccess && res.getOrThrow().items.isNotEmpty()) {
                return@withContext res.getOrThrow()
            }
            if (idx == 0) primaryError = res.exceptionOrNull()
            lastError = res.exceptionOrNull()
        }
        throw IllegalStateException((primaryError ?: lastError)?.message ?: "SoundCloud scrape failed")
    }

    private fun scrapeDirectApiV2(url: String): ScrapeResult {
        val dynamicCid = fetchDynamicClientId()
        val allCids = if (dynamicCid != null) listOf(dynamicCid) + clientIds else clientIds
        for (cid in allCids.distinct()) {
            val res = runCatching {
                http.get(
                    "https://api-v2.soundcloud.com/resolve?url=${ScraperUtils.urlEncode(url)}&client_id=$cid",
                    mapOf("Referer" to "https://soundcloud.com/"),
                )
            }.getOrNull() ?: continue
            if (res.status >= 400 || !res.body.trimStart().startsWith("{")) continue

            val json = JSONObject(res.body)
            val title = json.optString("title").ifBlank { "SoundCloud Audio" }
            val author = json.optJSONObject("user")?.optString("username").orEmpty()
            val thumb = json.optString("artwork_url").replace("-large", "-t500x500")
            val transcodings = json.optJSONObject("media")?.optJSONArray("transcodings") ?: continue

            var progressiveUrl = ""
            for (i in 0 until transcodings.length()) {
                val tc = transcodings.getJSONObject(i)
                val proto = tc.optJSONObject("format")?.optString("protocol")
                if (proto == "progressive") {
                    val mp3Url = runCatching {
                        val streamRes = http.get("${tc.optString("url")}?client_id=$cid")
                        JSONObject(streamRes.body).optString("url")
                    }.getOrNull().orEmpty()
                    if (mp3Url.startsWith("http")) {
                        progressiveUrl = mp3Url
                        break
                    }
                }
            }

            if (progressiveUrl.isNotBlank()) {
                return ScrapeResult(
                    platform = PlatformId.SOUNDCLOUD,
                    serverUsed = "SoundCloud Direct API",
                    title = title,
                    author = author,
                    thumbnail = thumb,
                    sourceUrl = url,
                    items = listOf(
                        ScraperDownloadItem(
                            id = "sc_direct_mp3",
                            title = if (author.isNotBlank()) "$author - $title" else title,
                            label = "MP3 Audio (Direct)",
                            url = progressiveUrl,
                            category = MediaCategory.AUDIO,
                            extension = "mp3",
                            thumbnail = thumb,
                            author = author,
                        )
                    ),
                )
            }

            if (title.isNotBlank() && title != "SoundCloud Audio") {
                val query = if (author.isNotBlank()) "$author - $title" else title
                return ScrapeResult(
                    platform = PlatformId.SOUNDCLOUD,
                    serverUsed = "SoundCloud Direct + YT Match",
                    title = title,
                    author = author,
                    thumbnail = thumb,
                    sourceUrl = url,
                    items = listOf(
                        ScraperDownloadItem(
                            id = "sc_yt_match",
                            title = query,
                            label = "MP3 Audio (YT Match)",
                            url = "ytsearch_resolve:${ScraperUtils.urlEncode(query)}",
                            category = MediaCategory.AUDIO,
                            extension = "mp3",
                            thumbnail = thumb,
                            author = author,
                        )
                    ),
                )
            }
        }
        // Fallback: parse window.__sc_hydration directly from SoundCloud HTML page
        val hydResult = runCatching {
            val pageHtml = http.get(url, mapOf("User-Agent" to HttpClientFactory.CHROME_DESKTOP_UA)).body
            val hydMatch = Regex("""window\.__sc_hydration\s*=\s*(\[[\s\S]*?\]);\s*</script>""").find(pageHtml)
            var found: ScrapeResult? = null
            if (hydMatch != null) {
                val hydArr = org.json.JSONArray(hydMatch.groupValues[1])
                for (i in 0 until hydArr.length()) {
                    val entry = hydArr.optJSONObject(i)
                    if (entry != null && entry.optString("hydratable") == "sound") {
                        val d = entry.optJSONObject("data")
                        val title = d?.optString("title").orEmpty()
                        if (title.isNotBlank()) {
                            val author = d?.optJSONObject("user")?.optString("username").orEmpty()
                            val thumb = d?.optString("artwork_url").orEmpty().replace("-large", "-t500x500")
                            val query = if (author.isNotBlank()) "$author - $title" else title
                            found = ScrapeResult(
                                platform = PlatformId.SOUNDCLOUD,
                                serverUsed = "SoundCloud Direct + YT Match",
                                title = title,
                                author = author,
                                thumbnail = thumb,
                                sourceUrl = url,
                                items = listOf(
                                    ScraperDownloadItem(
                                        id = "sc_hyd_yt",
                                        title = query,
                                        label = "MP3 Audio (YT Match)",
                                        url = "ytsearch_resolve:${ScraperUtils.urlEncode(query)}",
                                        category = MediaCategory.AUDIO,
                                        extension = "mp3",
                                        thumbnail = thumb,
                                        author = author,
                                    )
                                ),
                            )
                            break
                        }
                    }
                }
            }
            found
        }.getOrNull()
        if (hydResult != null) return hydResult

        // Fallback 2: official SoundCloud oEmbed API (always returns title/author/artwork without auth)
        val oembedResult = runCatching {
            val oeRes = http.get("https://soundcloud.com/oembed?format=json&url=${ScraperUtils.urlEncode(url)}")
            val oe = JSONObject(oeRes.body)
            val rawTitle = oe.optString("title").trim()
            val author = oe.optString("author_name").trim()
            val title = if (author.isNotBlank() && rawTitle.endsWith(" by $author")) {
                rawTitle.removeSuffix(" by $author").trim()
            } else {
                rawTitle
            }
            val thumb = oe.optString("thumbnail_url").trim()
            if (title.isNotBlank()) {
                val query = if (author.isNotBlank()) "$author - $title" else title
                ScrapeResult(
                    platform = PlatformId.SOUNDCLOUD,
                    serverUsed = "SoundCloud Direct + YT Match",
                    title = title,
                    author = author,
                    thumbnail = thumb,
                    sourceUrl = url,
                    items = listOf(
                        ScraperDownloadItem(
                            id = "sc_oembed_yt",
                            title = query,
                            label = "MP3 Audio (YT Match)",
                            url = "ytsearch_resolve:${ScraperUtils.urlEncode(query)}",
                            category = MediaCategory.AUDIO,
                            extension = "mp3",
                            thumbnail = thumb,
                            author = author,
                        )
                    ),
                )
            } else null
        }.getOrNull()
        if (oembedResult != null) return oembedResult

        throw IllegalStateException("Direct SoundCloud API could not resolve stream")
    }

    private suspend fun scrapeForHub(url: String): ScrapeResult {
        val baseDomain = "https://www.forhub.io"
        val homeUrl = "$baseDomain/soundcloud/en18/"
        val csrfRes = http.get(
            "$baseDomain/csrf-token-endpoint.php",
            mapOf("Referer" to homeUrl, "Accept" to "application/json"),
        )
        val cookies = csrfRes.cookieString()
        val token = runCatching { JSONObject(csrfRes.body).optString("csrf_token") }.getOrDefault("")
            .ifBlank {
                Regex("""([a-zA-Z0-9._-]{16,})""").find(csrfRes.body)?.groupValues?.get(1).orEmpty()
            }
        if (token.isBlank()) throw IllegalStateException("ForHub CSRF token not available")

        val pageRes = http.postForm(
            "$baseDomain/download.php",
            mapOf("formurl" to url, "value" to url, "csrf_token" to token),
            mapOf(
                "Origin" to baseDomain,
                "Referer" to homeUrl,
                "Cookie" to cookies,
            ),
        )
        val apiToken = Regex("""const\s+FORHUB_API_TOKEN\s*=\s*["']([^"']+)["']""")
            .find(pageRes.body)?.groupValues?.get(1).orEmpty()
        if (apiToken.isBlank()) {
            throw IllegalStateException("ForHub could not authorize this track conversion")
        }

        val dlHeaders = mapOf(
            "X-Forhub-Api-Token" to apiToken,
            "Accept" to "application/json, text/plain, */*",
            "Referer" to "$baseDomain/download.php",
            "Origin" to baseDomain,
        )
        var dlRes = http.get(
            "https://dl.forhub.io/?url=${ScraperUtils.urlEncode(url)}&async=1",
            dlHeaders,
        )
        var data = runCatching { JSONObject(dlRes.body) }.getOrNull()
            ?: throw IllegalStateException("ForHub returned invalid response")

        var attempts = 0
        while ((dlRes.status == 202 || data.optBoolean("pending", false)) &&
            data.optString("pollToken").isNotBlank() &&
            attempts < 15
        ) {
            kotlinx.coroutines.delay(2000L)
            val pollRes = http.postRaw(
                "https://dl.forhub.io/status",
                JSONObject().put("token", data.optString("pollToken")).toString(),
                "application/json",
                mapOf(
                    "Accept" to "application/json",
                    "Referer" to "$baseDomain/download.php",
                    "Origin" to baseDomain,
                ),
            )
            data = runCatching { JSONObject(pollRes.body) }.getOrNull() ?: break
            attempts++
            if (data.optBoolean("success", false) && data.optString("downloadUrl").startsWith("http")) break
        }

        val dlUrl = data.optString("downloadUrl")
        if (!dlUrl.startsWith("http")) {
            throw IllegalStateException(data.optString("message").ifBlank { "ForHub did not return MP3 URL" })
        }
        val meta = data.optJSONObject("metadata")
        val resolvedTitle = meta?.optString("title").orEmpty().ifBlank { "SoundCloud Track" }
        val resolvedThumb = meta?.optString("thumbnail").orEmpty()
        return ScrapeResult(
            platform = PlatformId.SOUNDCLOUD,
            serverUsed = "ForHub",
            title = resolvedTitle,
            thumbnail = resolvedThumb,
            sourceUrl = url,
            items = listOf(
                ScraperDownloadItem(
                    id = "sc_forhub",
                    title = resolvedTitle,
                    label = "MP3 (128kbps)",
                    url = dlUrl,
                    category = MediaCategory.AUDIO,
                    extension = "mp3",
                    thumbnail = resolvedThumb,
                    referer = "$baseDomain/download.php",
                )
            ),
        )
    }

    private fun scrapeKlickAud(url: String): ScrapeResult {
        val baseDomain = "https://www.klickaud.org"
        val homeReferer = "$baseDomain/en17/"
        val csrfRes = http.get(
            "$baseDomain/csrf-token-endpoint.php",
            mapOf("Referer" to homeReferer, "Accept" to "application/json"),
        )
        var cookies = csrfRes.cookieString()
        val token = runCatching { JSONObject(csrfRes.body).optString("csrf_token") }.getOrDefault("")
        if (token.isBlank()) throw IllegalStateException("KlickAud CSRF token not available")

        val postRes = http.postForm(
            "$baseDomain/download.php",
            mapOf("value" to url, "csrf_token" to token),
            mapOf(
                "Origin" to baseDomain,
                "Referer" to homeReferer,
                "Cookie" to cookies,
            ),
        )
        if (postRes.cookieString().isNotBlank()) {
            cookies = listOf(cookies, postRes.cookieString()).filter { it.isNotBlank() }.joinToString("; ")
        }
        val html = postRes.body
        val downloadMode = Regex("""const\s+downloadMode\s*=\s*["']([^"']+)["']""").find(html)?.groupValues?.get(1).orEmpty()
        val directUrl = Regex("""const\s+directDownloadUrl\s*=\s*["']([^"']*)["']""").find(html)?.groupValues?.get(1).orEmpty()
        val defaultFileName = Regex("""const\s+defaultFileName\s*=\s*["']([^"']+)["']""").find(html)?.groupValues?.get(1)
            ?: "SoundCloud Track"
        val sseGrant = Regex("""const\s+sseGrant\s*=\s*["']([^"']+)["']""").find(html)?.groupValues?.get(1).orEmpty()

        val cleanDefaultTitle = defaultFileName
            .replace(Regex("_forhub_soundcloud_to_mp3", RegexOption.IGNORE_CASE), "")
            .replace(Regex("_KLICKAUD", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\.mp3$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("_+"), " ")
            .trim()
            .ifBlank { "SoundCloud Track" }

        if (downloadMode == "direct" && directUrl.startsWith("http")) {
            return ScrapeResult(
                platform = PlatformId.SOUNDCLOUD,
                serverUsed = "KlickAud",
                title = cleanDefaultTitle,
                sourceUrl = url,
                items = listOf(
                    ScraperDownloadItem(
                        id = "sc_klickaud",
                        title = cleanDefaultTitle,
                        label = "MP3 (128kbps)",
                        url = directUrl,
                        category = MediaCategory.AUDIO,
                        extension = "mp3",
                        referer = "$baseDomain/download.php",
                    )
                ),
            )
        }

        if (sseGrant.isBlank()) {
            throw IllegalStateException("KlickAud did not return download session grant")
        }

        val capRes = http.postRaw(
            "$baseDomain/sse_capability.php",
            JSONObject().put("grant", sseGrant).put("url", url).toString(),
            "application/json",
            mapOf(
                "Referer" to "$baseDomain/download.php",
                "Origin" to baseDomain,
                "Cookie" to cookies,
            ),
        )
        val capability = runCatching { JSONObject(capRes.body).optString("capability") }.getOrDefault("")
        if (capability.isBlank()) {
            throw IllegalStateException("KlickAud capability authorization failed")
        }

        val sseRes = http.get(
            "$baseDomain/worker_sse.php?url=${ScraperUtils.urlEncode(url)}&cap=${ScraperUtils.urlEncode(capability)}",
            mapOf(
                "Referer" to "$baseDomain/download.php",
                "Accept" to "text/event-stream",
                "Cookie" to cookies,
            ),
        )
        if (sseRes.body.contains("event: failed")) {
            throw IllegalStateException("KlickAud worker failed to process this track")
        }
        val readyMatch = Regex("""event:\s*ready[\r\n]+data:\s*([^\r\n]+)""", RegexOption.IGNORE_CASE).find(sseRes.body)
            ?: Regex("""data:\s*(\{[^\n]+download_url[^\n]+\})""", RegexOption.IGNORE_CASE).find(sseRes.body)
        val payload = readyMatch?.groupValues?.get(1)?.let { raw ->
            runCatching { JSONObject(raw) }.getOrNull()
        }
        val dlUrl = payload?.optString("download_url").orEmpty()
        if (!dlUrl.startsWith("http")) {
            throw IllegalStateException("KlickAud did not return MP3 URL")
        }
        val meta = payload?.optJSONObject("metadata")
        val finalTitle = meta?.optString("title").orEmpty().ifBlank { cleanDefaultTitle }
        val finalThumb = meta?.optString("thumbnail").orEmpty()
        return ScrapeResult(
            platform = PlatformId.SOUNDCLOUD,
            serverUsed = "KlickAud",
            title = finalTitle,
            thumbnail = finalThumb,
            sourceUrl = url,
            items = listOf(
                ScraperDownloadItem(
                    id = "sc_klickaud",
                    title = finalTitle,
                    label = "MP3 (128kbps)",
                    url = dlUrl,
                    category = MediaCategory.AUDIO,
                    extension = "mp3",
                    thumbnail = finalThumb,
                    referer = "$baseDomain/download.php",
                )
            ),
        )
    }
}

