package com.nimiyo.nativeapp.scraper

import com.nimiyo.nativeapp.core.HttpClientFactory
import com.nimiyo.nativeapp.data.MediaCategory
import com.nimiyo.nativeapp.data.PlatformId
import com.nimiyo.nativeapp.data.ScrapeResult
import com.nimiyo.nativeapp.data.ScraperDownloadItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class YouTubeScraper(private val http: HttpClientFactory) {

    companion object {
        const val ANDROID_VR_UA =
            "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
    }

    fun extractVideoId(url: String): String? {
        val patterns = listOf(
            Regex("""[?&]v=([a-zA-Z0-9_-]{11})"""),
            Regex("""youtu\.be/([a-zA-Z0-9_-]{11})"""),
            Regex("""youtube\.com/(?:shorts|embed|live)/([a-zA-Z0-9_-]{11})"""),
        )
        for (p in patterns) {
            val m = p.find(url)
            if (m != null) return m.groupValues[1]
        }
        return null
    }

    fun extractPlaylistId(url: String): String? {
        val m = Regex("""[?&]list=([a-zA-Z0-9_-]+)""").find(url)
        return m?.groupValues?.get(1)
    }

    suspend fun scrape(url: String, server: String): ScrapeResult = withContext(Dispatchers.IO) {
        val cleanUrl = ScraperUtils.cleanUrl(url)
        val playlistId = extractPlaylistId(cleanUrl)
        val videoId = extractVideoId(cleanUrl)

        if (playlistId != null && (cleanUrl.contains("/playlist") || videoId == null)) {
            return@withContext scrapePlaylist(cleanUrl, playlistId)
        }

        val resolvedVideoId = videoId ?: throw IllegalArgumentException("Invalid YouTube video URL")
        val normalizedUrl = "https://www.youtube.com/watch?v=$resolvedVideoId"
        val meta = fetchMeta(normalizedUrl, resolvedVideoId)
        val title = meta.first
        val author = meta.second
        val thumb = "https://i.ytimg.com/vi/$resolvedVideoId/hqdefault.jpg"

        val order = when (server) {
            "mobi" -> listOf("mobi", "innertube", "gg")
            "gg" -> listOf("gg", "innertube", "mobi")
            else -> listOf("innertube", "gg", "mobi")
        }

        val items = mutableListOf<ScraperDownloadItem>()
        var actualServer = ""
        for (srv in order) {
            val found = runCatching {
                when (srv) {
                    "innertube" -> scrapeInnerTubeAndroidVr(resolvedVideoId, title, thumb, author)
                    "gg" -> scrapeConvert1s(normalizedUrl, title, thumb, author)
                    else -> scrapeYmcdn(resolvedVideoId, title, thumb, author)
                }
            }.getOrNull()
            if (!found.isNullOrEmpty()) {
                items.addAll(found)
                actualServer = when (srv) {
                    "innertube" -> "YouTube Direct (VR)"
                    "gg" -> "ytmp3.gg"
                    else -> "ytmp3.mobi"
                }
                break
            }
        }

        // Lazy fallback options so user always has playable download choices
        if (items.isEmpty()) {
            actualServer = "ytmp3.gg / ytmp3.mobi"
            items.add(
                ScraperDownloadItem(
                    id = "yt_mp4_lazy",
                    title = title,
                    label = "MP4 720p (Lazy Resolve)",
                    url = "ytmp3gg_resolve:$resolvedVideoId|||mp4|||720",
                    category = MediaCategory.VIDEO,
                    extension = "mp4",
                    thumbnail = thumb,
                    author = author,
                )
            )
            items.add(
                ScraperDownloadItem(
                    id = "yt_mp3_lazy",
                    title = title,
                    label = "MP3 128kbps (Lazy Resolve)",
                    url = "ytmp3gg_resolve:$resolvedVideoId|||mp3|||128",
                    category = MediaCategory.AUDIO,
                    extension = "mp3",
                    thumbnail = thumb,
                    author = author,
                )
            )
        }

        ScrapeResult(
            platform = PlatformId.YOUTUBE,
            serverUsed = actualServer,
            title = title,
            author = author,
            thumbnail = thumb,
            sourceUrl = normalizedUrl,
            items = items,
        )
    }

    private fun fetchMeta(videoUrl: String, videoId: String): Pair<String, String> {
        runCatching {
            val res = http.get("https://www.youtube.com/oembed?url=${ScraperUtils.urlEncode(videoUrl)}&format=json")
            if (res.status < 400 && res.body.trimStart().startsWith("{")) {
                val j = JSONObject(res.body)
                val t = j.optString("title").trim()
                val a = j.optString("author_name").trim()
                if (t.isNotBlank()) return t to a
            }
        }
        return "YouTube_$videoId" to "YouTube"
    }

    private data class Convert1sResult(
        val url: String,
        val selectedQuality: String,
    )

    private suspend fun scrapeConvert1s(
        videoUrl: String,
        title: String,
        thumb: String,
        author: String,
    ): List<ScraperDownloadItem> {
        val headers = mapOf(
            "Origin" to "https://media.ytmp3.gg",
            "Referer" to "https://media.ytmp3.gg/",
            "Accept" to "application/json, text/plain, */*",
        )
        val vid = extractVideoId(videoUrl)
        val out = mutableListOf<ScraperDownloadItem>()
        // Fast probe: single POST (no 24s 4K poll!) returns selectedQuality immediately in <1s
        val probedQuality = probeConvert1sMaxQuality(videoUrl, headers)
        if (!probedQuality.isNullOrBlank() && !vid.isNullOrBlank()) {
            val maxHeight = Regex("""(\d+)""").find(probedQuality)?.groupValues?.get(1)?.toIntOrNull() ?: 720
            val ladder = listOf(
                2160 to ("2160p" to "MP4 Video (2160p 4K)"),
                1440 to ("1440p" to "MP4 Video (1440p 2K)"),
                1080 to ("1080p" to "MP4 Video (1080p Full HD)"),
                720 to ("720p" to "MP4 Video (720p HD)"),
                480 to ("480p" to "MP4 Video (480p)"),
                360 to ("360p" to "MP4 Video (360p)"),
                240 to ("240p" to "MP4 Video (240p)"),
            )
            for ((h, pair) in ladder) {
                if (h > maxHeight) continue
                val (q, label) = pair
                out.add(
                    ScraperDownloadItem(
                        id = "gg_mp4_$q",
                        title = title,
                        label = label,
                        url = "ytmp3gg_resolve:$vid|||mp4|||$q",
                        category = MediaCategory.VIDEO,
                        extension = "mp4",
                        thumbnail = thumb,
                        author = author,
                        referer = "https://media.ytmp3.gg/",
                    )
                )
            }
            if (out.isEmpty()) {
                out.add(
                    ScraperDownloadItem(
                        id = "gg_mp4_max",
                        title = title,
                        label = "MP4 Video ($probedQuality)",
                        url = "ytmp3gg_resolve:$vid|||mp4|||$probedQuality",
                        category = MediaCategory.VIDEO,
                        extension = "mp4",
                        thumbnail = thumb,
                        author = author,
                        referer = "https://media.ytmp3.gg/",
                    )
                )
            }
            out.add(
                ScraperDownloadItem(
                    id = "gg_mp3",
                    title = title,
                    label = "MP3 Audio (128kbps)",
                    url = "ytmp3gg_resolve:$vid|||mp3|||128",
                    category = MediaCategory.AUDIO,
                    extension = "mp3",
                    thumbnail = thumb,
                    author = author,
                    referer = "https://media.ytmp3.gg/",
                )
            )
            return out
        }

        val mp3Res = requestConvert1sFormat(videoUrl, "audio", "mp3", "128", headers)
        if (mp3Res != null) {
            out.add(
                ScraperDownloadItem(
                    id = "gg_mp3",
                    title = title,
                    label = "MP3 Audio (128kbps)",
                    url = mp3Res.url,
                    category = MediaCategory.AUDIO,
                    extension = "mp3",
                    thumbnail = thumb,
                    author = author,
                    referer = "https://media.ytmp3.gg/",
                )
            )
        }
        if (out.none { it.category == MediaCategory.VIDEO } && !vid.isNullOrBlank()) {
            val vrVideos = runCatching {
                scrapeInnerTubeAndroidVr(vid, title, thumb, author)
                    .filter { it.category == MediaCategory.VIDEO }
            }.getOrDefault(emptyList())
            out.addAll(0, vrVideos)
        }
        return out
    }

    suspend fun resolveConvert1sSingle(videoUrl: String, format: String, quality: String): String {
        val headers = mapOf(
            "Origin" to "https://media.ytmp3.gg",
            "Referer" to "https://media.ytmp3.gg/",
            "Accept" to "application/json, text/plain, */*",
        )
        val type = if (format == "mp4") "video" else "audio"
        val normalizedQuality = if (format == "mp4" && quality.all { it.isDigit() }) "${quality}p" else quality
        val direct = requestConvert1sFormat(videoUrl, type, format, normalizedQuality, headers)
        if (direct != null) return direct.url
        val vid = extractVideoId(videoUrl)
        if (!vid.isNullOrBlank()) {
            resolveInnerTubeStream(vid, format)?.let { return it }
        }
        throw IllegalStateException("ytmp3.gg conversion failed")
    }

    private fun fetchInnerTubePlayerJson(videoId: String): JSONObject? {
        fun query(visitorData: String?): JSONObject? {
            val body = JSONObject().apply {
                put("videoId", videoId)
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "ANDROID_VR")
                        put("clientVersion", "1.60.19")
                        put("deviceMake", "Oculus")
                        put("deviceModel", "Quest 3")
                        put("osName", "Android")
                        put("osVersion", "12L")
                        put("androidSdkVersion", 32)
                        put("hl", "en")
                        put("gl", "US")
                        if (!visitorData.isNullOrBlank()) put("visitorData", visitorData)
                    })
                })
            }.toString()
            val hdrs = mutableMapOf("User-Agent" to ANDROID_VR_UA)
            if (!visitorData.isNullOrBlank()) hdrs["X-Goog-Visitor-Id"] = visitorData
            val res = http.postRaw(
                "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
                body,
                "application/json",
                hdrs,
            )
            if (res.status >= 400) return null
            return runCatching { JSONObject(res.body) }.getOrNull()
        }

        val first = query(null)
        if (first?.optJSONObject("playabilityStatus")?.optString("status") == "OK") return first

        val visitorData = runCatching {
            val embed = http.get("https://www.youtube.com/embed/$videoId").body
            Regex(""""visitorData":"([^"]+)"""").find(embed)?.groupValues?.get(1)
        }.getOrNull()
        if (!visitorData.isNullOrBlank()) {
            val second = query(visitorData)
            if (second != null) return second
        }
        return first
    }

    fun scrapeInnerTubeAndroidVr(
        videoId: String,
        fallbackTitle: String,
        thumb: String,
        fallbackAuthor: String,
    ): List<ScraperDownloadItem> {
        val json = fetchInnerTubePlayerJson(videoId) ?: return emptyList()
        if (json.optJSONObject("playabilityStatus")?.optString("status") != "OK") return emptyList()
        val details = json.optJSONObject("videoDetails")
        val title = details?.optString("title").orEmpty().ifBlank { fallbackTitle }
        val author = details?.optString("author").orEmpty().ifBlank { fallbackAuthor }
        val streaming = json.optJSONObject("streamingData") ?: return emptyList()

        val out = mutableListOf<ScraperDownloadItem>()
        val formats = streaming.optJSONArray("formats") ?: JSONArray()
        for (i in 0 until formats.length()) {
            val f = formats.optJSONObject(i) ?: continue
            val u = f.optString("url")
            if (!u.startsWith("http")) continue
            val qLabel = f.optString("qualityLabel").ifBlank { "360p" }
            out.add(
                ScraperDownloadItem(
                    id = "yt_vr_mp4_$i",
                    title = title,
                    label = "MP4 Video ($qLabel)",
                    url = u,
                    category = MediaCategory.VIDEO,
                    extension = "mp4",
                    thumbnail = thumb,
                    author = author,
                )
            )
        }

        val adaptive = streaming.optJSONArray("adaptiveFormats") ?: JSONArray()
        val audioCandidates = mutableListOf<Pair<Int, String>>()
        val videoCandidates = mutableListOf<Triple<Int, String, String>>() // height, label, url
        var maxAllVideoHeight = 0
        for (i in 0 until adaptive.length()) {
            val f = adaptive.optJSONObject(i) ?: continue
            val mime = f.optString("mimeType")
            val u = f.optString("url")
            if (!u.startsWith("http")) continue
            if (mime.startsWith("audio/mp4")) {
                audioCandidates.add(f.optInt("bitrate", 0) to u)
            } else if (mime.startsWith("video/")) {
                val h = f.optInt("height", 0)
                if (h > maxAllVideoHeight) maxAllVideoHeight = h
                if (mime.startsWith("video/mp4") && mime.contains("avc1", ignoreCase = true) && h in 144..4320) {
                    val qLabel = f.optString("qualityLabel").ifBlank { "${h}p" }
                    videoCandidates.add(Triple(h, qLabel, u))
                }
            }
        }
        val bestAudio = audioCandidates.maxByOrNull { it.first }
        if (bestAudio != null) {
            val addedHeights = HashSet<Int>()
            val hdItems = mutableListOf<ScraperDownloadItem>()
            var maxVrHeight = 0
            videoCandidates
                .sortedByDescending { it.first }
                .forEachIndexed { idx, (h, qLabel, vUrl) ->
                    if (addedHeights.add(h)) {
                        if (h > maxVrHeight) maxVrHeight = h
                        hdItems.add(
                            ScraperDownloadItem(
                                id = "yt_vr_hd_$idx",
                                title = title,
                                label = "MP4 Video ($qLabel)",
                                url = "ytmux_resolve:${ScraperUtils.urlEncode(vUrl)}|||${ScraperUtils.urlEncode(bestAudio.second)}",
                                category = MediaCategory.VIDEO,
                                extension = "mp4",
                                thumbnail = thumb,
                                author = author,
                            )
                        )
                    }
                }
            // If ANDROID_VR didn't return 1440p/2160p (e.g. vp9-only or capped), verify true max via fast convert1s probe
            var effectiveMaxHeight = maxAllVideoHeight
            if (effectiveMaxHeight < 2160) {
                val cHeaders = mapOf(
                    "Origin" to "https://media.ytmp3.gg",
                    "Referer" to "https://media.ytmp3.gg/",
                    "Accept" to "application/json, text/plain, */*",
                )
                val probed = runCatching {
                    probeConvert1sMaxQuality("https://www.youtube.com/watch?v=$videoId", cHeaders)
                }.getOrNull()
                val probedH = probed?.let { Regex("""(\d+)""").find(it)?.groupValues?.get(1)?.toIntOrNull() } ?: 0
                if (probedH > effectiveMaxHeight) effectiveMaxHeight = probedH
            }
            val extraHigh = buildList {
                if (effectiveMaxHeight >= 2160 && maxVrHeight < 2160) add("2160p" to "MP4 Video (2160p 4K)")
                if (effectiveMaxHeight >= 1440 && maxVrHeight < 1440) add("1440p" to "MP4 Video (1440p 2K)")
                if (effectiveMaxHeight >= 1080 && maxVrHeight < 1080) add("1080p" to "MP4 Video (1080p Full HD)")
            }.map { (q, label) ->
                ScraperDownloadItem(
                    id = "yt_gg_$q",
                    title = title,
                    label = label,
                    url = "ytmp3gg_resolve:$videoId|||mp4|||$q",
                    category = MediaCategory.VIDEO,
                    extension = "mp4",
                    thumbnail = thumb,
                    author = author,
                    referer = "https://media.ytmp3.gg/",
                )
            }
            out.addAll(0, extraHigh + hdItems)
            val kbps = (bestAudio.first / 1000).coerceAtLeast(64)
            out.add(
                ScraperDownloadItem(
                    id = "yt_vr_m4a",
                    title = title,
                    label = "M4A Audio (${kbps}kbps)",
                    url = bestAudio.second,
                    category = MediaCategory.AUDIO,
                    extension = "m4a",
                    thumbnail = thumb,
                    author = author,
                )
            )
        }
        return out
    }

    fun resolveInnerTubeStream(videoId: String, format: String): String? {
        val json = fetchInnerTubePlayerJson(videoId) ?: return null
        if (json.optJSONObject("playabilityStatus")?.optString("status") != "OK") return null
        val streaming = json.optJSONObject("streamingData") ?: return null
        if (format.equals("mp4", ignoreCase = true)) {
            val formats = streaming.optJSONArray("formats") ?: JSONArray()
            for (i in 0 until formats.length()) {
                val u = formats.optJSONObject(i)?.optString("url").orEmpty()
                if (u.startsWith("http")) return u
            }
        } else {
            val adaptive = streaming.optJSONArray("adaptiveFormats") ?: JSONArray()
            val candidates = mutableListOf<Pair<Int, String>>()
            for (i in 0 until adaptive.length()) {
                val f = adaptive.optJSONObject(i) ?: continue
                val mime = f.optString("mimeType")
                val u = f.optString("url")
                if (mime.startsWith("audio/mp4") && u.startsWith("http")) {
                    candidates.add(f.optInt("bitrate", 0) to u)
                }
            }
            return candidates.maxByOrNull { it.first }?.second
        }
        return null
    }

    private fun probeConvert1sMaxQuality(
        videoUrl: String,
        headers: Map<String, String>,
    ): String? {
        val bodyJson = JSONObject().apply {
            put("url", videoUrl)
            put("os", "windows")
            put("output", JSONObject().apply {
                put("type", "video")
                put("format", "mp4")
                put("quality", "2160p")
            })
            put("audio", JSONObject().apply { put("bitrate", "128k") })
        }.toString()
        val res = runCatching {
            http.postRaw(
                "https://hub.convert1s.com/api/download",
                bodyJson,
                "application/json",
                headers,
            )
        }.getOrNull() ?: return null
        if (res.status >= 400) return null
        val json = runCatching { JSONObject(res.body) }.getOrNull() ?: return null
        if (json.has("error") && !json.isNull("error") && json.optString("error").isNotBlank()) return null
        val statusUrl = json.optString("statusUrl")
        val dlUrl = json.optString("downloadUrl")
        if (statusUrl.isBlank() && !dlUrl.startsWith("http")) return null
        return json.optString("selectedQuality").ifBlank { "720p" }
    }

    private suspend fun requestConvert1sFormat(
        videoUrl: String,
        type: String,
        format: String,
        quality: String,
        headers: Map<String, String>,
    ): Convert1sResult? {
        val bodyJson = JSONObject().apply {
            put("url", videoUrl)
            put("os", "windows")
            put("output", JSONObject().apply {
                put("type", type)
                put("format", format)
                put("quality", quality)
            })
            put("audio", JSONObject().apply { put("bitrate", "128k") })
        }.toString()

        val res = http.postRaw(
            "https://hub.convert1s.com/api/download",
            bodyJson,
            "application/json",
            headers,
        )
        if (res.status >= 400) return null
        val json = runCatching { JSONObject(res.body) }.getOrNull() ?: return null
        val selectedQuality = json.optString("selectedQuality").ifBlank { quality }
        if (json.has("downloadUrl") && json.optString("downloadUrl").startsWith("http")) {
            return Convert1sResult(json.optString("downloadUrl"), selectedQuality)
        }
        val statusUrl = json.optString("statusUrl")
        if (statusUrl.isBlank()) return null

        repeat(60) {
            delay(1200L)
            val poll = http.get(statusUrl, headers)
            val pj = runCatching { JSONObject(poll.body) }.getOrNull()
            if (pj != null) {
                val dl = pj.optString("downloadUrl")
                if (dl.startsWith("http")) return Convert1sResult(dl, selectedQuality)
                val st = pj.optString("status")
                if (st == "error" || st == "failed") return null
            }
        }
        return null
    }

    suspend fun scrapeYmcdn(
        videoId: String,
        title: String,
        thumb: String,
        author: String,
    ): List<ScraperDownloadItem> {
        val headers = mapOf(
            "Origin" to "https://ytmp3.mobi",
            "Referer" to "https://ytmp3.mobi/en8/",
            "Accept" to "*/*",
        )
        val out = mutableListOf<ScraperDownloadItem>()
        for (fmt in listOf("mp4", "mp3")) {
            val dl = runCatching { resolveYmcdnSingle(videoId, fmt, headers) }.getOrNull()
            if (!dl.isNullOrBlank()) {
                out.add(
                    ScraperDownloadItem(
                        id = "mobi_$fmt",
                        title = title,
                        label = if (fmt == "mp4") "MP4 Video (YMCDN)" else "MP3 Audio (YMCDN)",
                        url = dl,
                        category = if (fmt == "mp4") MediaCategory.VIDEO else MediaCategory.AUDIO,
                        extension = fmt,
                        thumbnail = thumb,
                        author = author,
                        referer = "https://ytmp3.mobi/",
                    )
                )
            }
        }
        return out
    }

    suspend fun resolveYmcdnSingle(
        videoId: String,
        format: String,
        headers: Map<String, String> = mapOf(
            "Origin" to "https://ytmp3.mobi",
            "Referer" to "https://ytmp3.mobi/en8/",
        ),
    ): String? {
        val initRes = http.get(
            "https://a.ymcdn.org/api/v1/init?p=y&23=1llum1n471&_=${Math.random()}",
            headers,
        )
        val initJson = runCatching { JSONObject(initRes.body) }.getOrNull() ?: return null
        val convertUrl = initJson.optString("convertURL")
        if (convertUrl.isBlank()) return null

        val sep = if (convertUrl.contains("?")) "&" else "?"
        val convRes = http.get("$convertUrl${sep}v=$videoId&f=$format&_=${Math.random()}", headers)
        var convJson = runCatching { JSONObject(convRes.body) }.getOrNull() ?: return null
        if (convJson.optInt("error", 0) != 0) return null
        val redirectUrl = convJson.optString("redirectURL")
        if (redirectUrl.isNotBlank()) {
            val redirRes = http.get(redirectUrl, headers)
            convJson = runCatching { JSONObject(redirRes.body) }.getOrNull() ?: return null
            if (convJson.optInt("error", 0) != 0) return null
        }
        val dlUrl = convJson.optString("downloadURL")
        val progUrl = convJson.optString("progressURL")
        if (dlUrl.isBlank()) return null
        if (progUrl.isBlank()) return dlUrl

        repeat(20) {
            delay(1500L)
            val pr = runCatching { JSONObject(http.get(progUrl, headers).body) }.getOrNull() ?: return@repeat
            if (pr.optInt("progress", 0) >= 3) return dlUrl
            if (pr.optInt("error", 0) > 0) return null
        }
        return dlUrl
    }

    private fun scrapePlaylist(url: String, playlistId: String): ScrapeResult {
        var playlistTitle = "YouTube Playlist"
        val items = mutableListOf<ScraperDownloadItem>()

        // 1. Try yt-meta.convert1s.com playlist API
        runCatching {
            val apiRes = http.get("https://yt-meta.convert1s.com/playlist?id=$playlistId")
            if (apiRes.status < 400 && apiRes.body.trimStart().startsWith("{")) {
                val json = JSONObject(apiRes.body)
                playlistTitle = json.optString("title", playlistTitle)
                val vids = json.optJSONArray("items") ?: json.optJSONArray("videos") ?: JSONArray()
                for (i in 0 until vids.length()) {
                    val v = vids.getJSONObject(i)
                    val vid = v.optString("id").ifBlank { v.optString("videoId") }
                    val vTitle = v.optString("title", "Track ${i + 1}")
                    val vAuthor = v.optString("author", v.optString("channelTitle", ""))
                    if (vid.isNotBlank()) {
                        items.add(
                            ScraperDownloadItem(
                                id = "ytpl_mp3_$vid",
                                title = vTitle,
                                label = "MP3 Audio",
                                url = "ytmp3gg_resolve:$vid|||mp3|||128",
                                category = MediaCategory.AUDIO,
                                extension = "mp3",
                                thumbnail = "https://i.ytimg.com/vi/$vid/hqdefault.jpg",
                                author = vAuthor,
                            )
                        )
                    }
                }
            }
        }

        // 2. Fallback HTML scrape of youtube.com/playlist?list=...
        if (items.isEmpty()) {
            val html = http.get("https://www.youtube.com/playlist?list=$playlistId").body
            val titleMatch = Regex("<title>([^<]+)</title>", RegexOption.IGNORE_CASE).find(html)
            if (titleMatch != null) {
                playlistTitle = titleMatch.groupValues[1].replace(" - YouTube", "").trim()
            }
            val vidMatches = Regex("\"playlistVideoRenderer\":\\{\"videoId\":\"([a-zA-Z0-9_-]{11})\".*?\"title\":\\{\"runs\":\\[\\{\"text\":\"([^\"]+)\"", RegexOption.DOT_MATCHES_ALL).findAll(html)
            val seen = HashSet<String>()
            for (m in vidMatches) {
                val vid = m.groupValues[1]
                if (!seen.add(vid)) continue
                val vTitle = ScraperUtils.decodeHtmlEntities(m.groupValues[2])
                items.add(
                    ScraperDownloadItem(
                        id = "ytpl_mp3_$vid",
                        title = vTitle,
                        label = "MP3 Audio",
                        url = "ytmp3gg_resolve:$vid|||mp3|||128",
                        category = MediaCategory.AUDIO,
                        extension = "mp3",
                        thumbnail = "https://i.ytimg.com/vi/$vid/hqdefault.jpg",
                        author = "",
                    )
                )
            }
        }

        if (items.isEmpty()) {
            throw IllegalStateException("Could not extract YouTube playlist tracks")
        }

        return ScrapeResult(
            platform = PlatformId.YOUTUBE,
            serverUsed = "YouTube Playlist",
            title = playlistTitle,
            author = "YouTube",
            thumbnail = items.firstOrNull()?.thumbnail.orEmpty(),
            sourceUrl = url,
            items = items,
        )
    }
}

