package com.nimiyo.nativeapp.scraper

import com.nimiyo.nativeapp.core.HttpClientFactory
import com.nimiyo.nativeapp.data.MediaCategory
import com.nimiyo.nativeapp.data.PlatformId
import com.nimiyo.nativeapp.data.ScrapeResult
import com.nimiyo.nativeapp.data.ScraperDownloadItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.jsoup.Jsoup

class SpotifyScraper(private val http: HttpClientFactory) {

    suspend fun scrape(url: String, server: String): ScrapeResult = withContext(Dispatchers.IO) {
        var cleanUrl = ScraperUtils.cleanUrl(url)
        if (cleanUrl.contains("spotify.link") || cleanUrl.contains("spotify.com/s/")) {
            cleanUrl = resolveShortSpotifyUrl(cleanUrl)
        }

        val order = when (server) {
            "soundloaders" -> listOf("soundloaders", "spotidown", "spotisaver")
            "spotisaver" -> listOf("spotisaver", "spotidown", "soundloaders")
            else -> listOf("spotidown", "soundloaders", "spotisaver")
        }
        var lastError: Throwable? = null
        for (srv in order) {
            val res = runCatching {
                when (srv) {
                    "spotidown" -> scrapeSpotiDown(cleanUrl)
                    "soundloaders" -> scrapeSpotifyEmbedAndYouTube(cleanUrl, "Spotify (YT-GG)", preferMobi = false)
                    else -> scrapeSpotifyEmbedAndYouTube(cleanUrl, "Spotify (YT-Mobi)", preferMobi = true)
                }
            }
            if (res.isSuccess && res.getOrThrow().items.isNotEmpty()) {
                return@withContext res.getOrThrow()
            }
            lastError = res.exceptionOrNull()
        }
        throw IllegalStateException(lastError?.message ?: "Spotify scrape failed")
    }

    private fun resolveShortSpotifyUrl(shortUrl: String): String {
        runCatching {
            val res = http.get(shortUrl, mapOf("User-Agent" to HttpClientFactory.WHATSAPP_UA))
            val og = Regex("""<meta[^>]+property=["']og:url["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(res.body)
            if (og != null && og.groupValues[1].contains("open.spotify.com")) {
                return og.groupValues[1]
            }
            val openMatch = Regex("""https://open\.spotify\.com/(?:track|album|playlist)/[a-zA-Z0-9]+""").find(res.body)
            if (openMatch != null) return openMatch.value
            if (res.finalUrl.contains("open.spotify.com")) return res.finalUrl
        }
        return shortUrl
    }

    private fun scrapeSpotiDown(url: String): ScrapeResult {
        val home = http.get("https://spotidown.app/en7", mapOf("Referer" to "https://spotidown.app/"))
        val cookies = home.cookieString()
        val actionRes = http.postForm(
            "https://spotidown.app/action",
            mapOf("url" to url, "g-recaptcha-response" to "dummy_token"),
            mapOf(
                "Origin" to "https://spotidown.app",
                "Referer" to "https://spotidown.app/en7",
                "X-Requested-With" to "XMLHttpRequest",
                "Cookie" to cookies,
            ),
        )
        val json = JSONObject(actionRes.body)
        val html = json.optString("data", json.optString("html"))
        val doc = Jsoup.parse(html)
        val forms = doc.select("form[name=submitapurl], form[name=submitspurl], form[name=submiturl]")
        val items = mutableListOf<ScraperDownloadItem>()
        var mainTitle = doc.selectFirst("h3, .title")?.text()?.trim().orEmpty().ifBlank { "Spotify Track" }
        val mainThumb = doc.selectFirst("img")?.attr("src").orEmpty()

        if (forms.isEmpty()) {
            doc.select("a[href^=http]").forEachIndexed { i, a ->
                val href = a.attr("href")
                if (href.contains("spotidown.app/en")) return@forEachIndexed
                items.add(
                    ScraperDownloadItem(
                        id = "sd_dl_$i",
                        title = mainTitle,
                        label = a.text().trim().ifBlank { "MP3 Download" },
                        url = href,
                        category = MediaCategory.AUDIO,
                        extension = "mp3",
                        thumbnail = mainThumb,
                    )
                )
            }
        } else {
            val encodedCookie = ScraperUtils.urlEncode(cookies)
            forms.forEachIndexed { idx, f ->
                val pairs = f.select("input[name]").mapNotNull { inp ->
                    val n = inp.attr("name")
                    val v = inp.attr("value")
                    if (n.isNotBlank()) "${ScraperUtils.urlEncode(n)}=${ScraperUtils.urlEncode(v)}" else null
                }
                val payloadStr = pairs.joinToString("&")
                val dataB64 = f.selectFirst("input[name=data]")?.attr("value").orEmpty()
                var trackTitle = mainTitle
                var artist = ""
                var cover = mainThumb
                if (dataB64.isNotBlank()) {
                    runCatching {
                        val meta = JSONObject(ScraperUtils.b64Decode(dataB64))
                        val t = meta.optString("name", meta.optString("title"))
                        val a = meta.optString("artist", meta.optString("artists"))
                        if (t.isNotBlank()) trackTitle = if (a.isNotBlank()) "$a - $t" else t
                        if (a.isNotBlank()) artist = a
                        val c = meta.optString("image", meta.optString("cover"))
                        if (c.startsWith("http")) cover = c
                    }
                }
                if (idx == 0 && forms.size == 1) mainTitle = trackTitle
                items.add(
                    ScraperDownloadItem(
                        id = "sd_form_$idx",
                        title = trackTitle,
                        label = "MP3 Audio",
                        url = "spotidown_resolve:$payloadStr|||$encodedCookie",
                        category = MediaCategory.AUDIO,
                        extension = "mp3",
                        thumbnail = cover,
                        author = artist,
                    )
                )
            }
        }

        return ScrapeResult(
            platform = PlatformId.SPOTIFY,
            serverUsed = "SpotiDown",
            title = if (items.size > 1) "$mainTitle (${items.size} tracks)" else mainTitle,
            thumbnail = mainThumb,
            sourceUrl = url,
            items = items,
        )
    }

    private fun scrapeSpotimate(url: String): ScrapeResult {
        val home = http.get("https://spotimate.app/en1", mapOf("Referer" to "https://spotimate.app/"))
        val cookies = home.cookieString()
        val verify = http.postForm(
            "https://spotimate.app/api/userverify",
            mapOf("url" to url),
            mapOf(
                "Origin" to "https://spotimate.app",
                "Referer" to "https://spotimate.app/en1",
                "Cookie" to cookies,
            ),
        )
        val token = runCatching { JSONObject(verify.body).optString("token") }.getOrDefault("")
        val actionRes = http.postForm(
            "https://spotimate.app/action",
            mapOf("url" to url, "cf-turnstile-response" to token),
            mapOf(
                "Origin" to "https://spotimate.app",
                "Referer" to "https://spotimate.app/en1",
                "X-Requested-With" to "XMLHttpRequest",
                "Cookie" to cookies,
            ),
        )
        val json = JSONObject(actionRes.body)
        val html = json.optString("html", json.optString("data"))
        val doc = Jsoup.parse(html)
        val forms = doc.select("form[name=submitapurl], form[name=submitspurl], form[name=submiturl]")
        val items = mutableListOf<ScraperDownloadItem>()
        val encodedCookie = ScraperUtils.urlEncode(cookies)

        forms.forEachIndexed { idx, f ->
            val dataVal = f.selectFirst("input[name=data]")?.attr("value").orEmpty()
            val tokenVal = f.selectFirst("input[name=token]")?.attr("value").orEmpty()
            val baseVal = f.selectFirst("input[name=base]")?.attr("value").orEmpty().ifBlank { "https://open.spotify.com" }
            var title = "Track ${idx + 1}"
            var cover = ""
            if (dataVal.isNotBlank()) {
                runCatching {
                    val m = JSONObject(ScraperUtils.b64Decode(dataVal))
                    val t = m.optString("name", m.optString("title"))
                    val a = m.optString("artist")
                    if (t.isNotBlank()) title = if (a.isNotBlank()) "$a - $t" else t
                    cover = m.optString("image", m.optString("cover"))
                }
            }
            items.add(
                ScraperDownloadItem(
                    id = "sl_form_$idx",
                    title = title,
                    label = "MP3 Audio",
                    url = "soundloaders_resolve:$dataVal|||$tokenVal|||${ScraperUtils.urlEncode(baseVal)}|||$encodedCookie",
                    category = MediaCategory.AUDIO,
                    extension = "mp3",
                    thumbnail = cover,
                )
            )
        }

        return ScrapeResult(
            platform = PlatformId.SPOTIFY,
            serverUsed = "Spotimate",
            title = items.firstOrNull()?.title ?: "Spotify Media",
            thumbnail = items.firstOrNull()?.thumbnail.orEmpty(),
            sourceUrl = url,
            items = items,
        )
    }

    private fun scrapeSpotisaver(url: String): ScrapeResult {
        val m = Regex("""open\.spotify\.com/(track|playlist|album)/([a-zA-Z0-9]+)""").find(url)
            ?: throw IllegalStateException("Invalid Spotify URL")
        val type = m.groupValues[1]
        val id = m.groupValues[2]
        val home = http.get("https://spotisaver.net/en1")
        val cookies = home.cookieString()

        val listRes = http.get(
            "https://spotisaver.net/api/get_playlist.php?id=$id&type=$type&lang=en",
            mapOf("Referer" to "https://spotisaver.net/en1", "Cookie" to cookies),
        )
        val json = JSONObject(listRes.body)
        val tracks = json.optJSONArray("tracks") ?: throw IllegalStateException("No tracks from Spotisaver")
        val encodedCookie = ScraperUtils.urlEncode(cookies)
        val items = mutableListOf<ScraperDownloadItem>()
        for (i in 0 until tracks.length()) {
            val t = tracks.getJSONObject(i)
            val tId = t.optString("id")
            val name = t.optString("name", "Track ${i + 1}")
            val artist = t.optString("artists", "")
            val dur = t.optLong("duration_ms", 0L)
            val fullTitle = if (artist.isNotBlank()) "$artist - $name" else name
            items.add(
                ScraperDownloadItem(
                    id = "ssv_$i",
                    title = fullTitle,
                    label = "MP3 Audio",
                    url = "spotisaver_resolve:$tId|||${ScraperUtils.urlEncode(name)}|||$dur|||$encodedCookie",
                    category = MediaCategory.AUDIO,
                    extension = "mp3",
                    thumbnail = t.optString("cover"),
                    author = artist,
                    durationMs = dur,
                )
            )
        }
        return ScrapeResult(
            platform = PlatformId.SPOTIFY,
            serverUsed = "Spotisaver",
            title = json.optString("name", items.firstOrNull()?.title ?: "Spotify"),
            thumbnail = items.firstOrNull()?.thumbnail.orEmpty(),
            sourceUrl = url,
            items = items,
        )
    }

    private suspend fun scrapeSpotifyEmbedAndYouTube(
        url: String,
        serverLabel: String = "Spotify Direct + YT Match",
        preferMobi: Boolean = false,
    ): ScrapeResult {
        val m = Regex("""open\.spotify\.com/(track|playlist|album)/([a-zA-Z0-9]+)""").find(url)
            ?: throw IllegalStateException("Invalid Spotify URL")
        val type = m.groupValues[1]
        val id = m.groupValues[2]
        val resolvePrefix = if (preferMobi) "ytsearch_mobi_resolve:" else "ytsearch_resolve:"

        val embedRes = http.get("https://open.spotify.com/embed/$type/$id")
        val nextMatch = Regex("""<script[^>]+id=["']__NEXT_DATA__["'][^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
            .find(embedRes.body)
        val items = mutableListOf<ScraperDownloadItem>()
        var mainTitle = "Spotify $type"
        var mainThumb = ""

        if (nextMatch != null) {
            runCatching {
                val root = JSONObject(nextMatch.groupValues[1])
                val entity = root.optJSONObject("props")
                    ?.optJSONObject("pageProps")
                    ?.optJSONObject("state")
                    ?.optJSONObject("data")
                    ?.optJSONObject("entity")
                if (entity != null) {
                    mainTitle = entity.optString("name", entity.optString("title", mainTitle))
                    val imgArr = entity.optJSONObject("visualIdentity")?.optJSONArray("image")
                    if (imgArr != null && imgArr.length() > 0) {
                        mainThumb = imgArr.getJSONObject(0).optString("url")
                    }
                    if (type == "track") {
                        val artistsArr = entity.optJSONArray("artists")
                        val artist = if (artistsArr != null && artistsArr.length() > 0) {
                            artistsArr.getJSONObject(0).optString("name")
                        } else ""
                        val fullTitle = if (artist.isNotBlank()) "$artist - $mainTitle" else mainTitle
                        mainTitle = fullTitle
                        items.add(
                            ScraperDownloadItem(
                                id = "sp_embed_0",
                                title = fullTitle,
                                label = "MP3 Audio (YouTube Match)",
                                url = "$resolvePrefix${ScraperUtils.urlEncode(fullTitle)}",
                                category = MediaCategory.AUDIO,
                                extension = "mp3",
                                thumbnail = mainThumb,
                                author = artist,
                                durationMs = entity.optLong("duration", 0L),
                            )
                        )
                    } else {
                        val trackList = entity.optJSONArray("trackList")
                        if (trackList != null) {
                            val trackUris = (0 until trackList.length()).map { idx ->
                                val uri = trackList.getJSONObject(idx).optString("uri")
                                if (uri.startsWith("spotify:track:")) uri.substringAfter("spotify:track:") else ""
                            }
                            val perTrackThumbs: List<String> = if (type == "playlist") {
                                val sem = Semaphore(10)
                                coroutineScope {
                                    trackUris.map { tid ->
                                        async(Dispatchers.IO) {
                                            if (tid.isBlank()) return@async mainThumb
                                            sem.withPermit {
                                                runCatching {
                                                    val oe = http.get(
                                                        "https://open.spotify.com/oembed?url=${ScraperUtils.urlEncode("https://open.spotify.com/track/$tid")}"
                                                    )
                                                    val thumbUrl = JSONObject(oe.body).optString("thumbnail_url")
                                                    thumbUrl.ifBlank { mainThumb }
                                                }.getOrDefault(mainThumb)
                                            }
                                        }
                                    }.awaitAll()
                                }
                            } else {
                                List(trackList.length()) { mainThumb }
                            }

                            for (i in 0 until trackList.length()) {
                                val t = trackList.getJSONObject(i)
                                val tTitle = t.optString("title", "Track ${i + 1}")
                                val tArtist = t.optString("subtitle", "").replace('\u00A0', ' ').trim()
                                val full = if (tArtist.isNotBlank()) "$tArtist - $tTitle" else tTitle
                                items.add(
                                    ScraperDownloadItem(
                                        id = "sp_embed_$i",
                                        title = full,
                                        label = "MP3 Audio (YouTube Match)",
                                        url = "$resolvePrefix${ScraperUtils.urlEncode(full)}",
                                        category = MediaCategory.AUDIO,
                                        extension = "mp3",
                                        thumbnail = perTrackThumbs.getOrElse(i) { mainThumb },
                                        author = tArtist,
                                        durationMs = t.optLong("duration", 0L),
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        if (items.isEmpty()) {
            val oe = http.get("https://open.spotify.com/oembed?url=${ScraperUtils.urlEncode(url)}")
            val oj = JSONObject(oe.body)
            val t = oj.optString("title", "").ifBlank { throw IllegalStateException("Spotify Embed & oEmbed returned no metadata") }
            val thumb = oj.optString("thumbnail_url")
            mainTitle = t
            mainThumb = thumb
            items.add(
                ScraperDownloadItem(
                    id = "sp_oembed_0",
                    title = t,
                    label = "MP3 Audio (YouTube Match)",
                    url = "$resolvePrefix${ScraperUtils.urlEncode(t)}",
                    category = MediaCategory.AUDIO,
                    extension = "mp3",
                    thumbnail = thumb,
                )
            )
        }

        return ScrapeResult(
            platform = PlatformId.SPOTIFY,
            serverUsed = serverLabel,
            title = if (items.size > 1) "$mainTitle (${items.size} tracks)" else mainTitle,
            thumbnail = mainThumb,
            sourceUrl = url,
            items = items,
        )
    }

    fun resolveSpotiDownTrack(payload: String, encodedCookie: String): String {
        val cookie = ScraperUtils.urlDecode(encodedCookie)
        val res = http.postRaw(
            "https://spotidown.app/action/track",
            payload,
            "application/x-www-form-urlencoded; charset=UTF-8",
            mapOf(
                "X-Requested-With" to "XMLHttpRequest",
                "Referer" to "https://spotidown.app/",
                "Origin" to "https://spotidown.app",
                "Cookie" to cookie,
            ),
        )
        val html = runCatching { JSONObject(res.body).optString("data", res.body) }.getOrDefault(res.body)
        val doc = Jsoup.parse(html)
        for (a in doc.select("a[href^=http]")) {
            val href = a.attr("href")
            val text = a.text().lowercase()
            if (href.contains("premium.html") || href == "https://spotidown.app/" || text.contains("cover")) continue
            if (text.contains("mp3") || href.contains("rapid.spotidown.app") || href.contains("/v2?token=")) return href
        }
        return doc.selectFirst("a[href^=http]")?.attr("href")
            ?: throw IllegalStateException("Could not resolve SpotiDown track URL")
    }

    fun resolveSpotimateTrack(dataVal: String, tokenVal: String, baseVal: String, encodedCookie: String): String {
        val cookie = ScraperUtils.urlDecode(encodedCookie)
        val res = http.postForm(
            "https://spotimate.app/action/track",
            mapOf(
                "data" to dataVal,
                "token" to tokenVal,
                "base" to ScraperUtils.urlDecode(baseVal).ifBlank { "https://open.spotify.com" },
                "g-recaptcha-response" to "dummy_token",
            ),
            mapOf(
                "X-Requested-With" to "XMLHttpRequest",
                "Referer" to "https://spotimate.app/en1",
                "Origin" to "https://spotimate.app",
                "Cookie" to cookie,
            ),
        )
        val json = runCatching { JSONObject(res.body) }.getOrNull()
        val html = json?.optString("data")?.ifBlank { json.optString("html") } ?: res.body
        val doc = Jsoup.parse(html)
        for (a in doc.select("a[href^=http]")) {
            val href = a.attr("href")
            val text = a.text().lowercase()
            if (text.contains("cover") || href.contains("i.scdn.co") || href.contains("/en1")) continue
            if (href.contains("dl.spotimate.app") || href.contains("dl.soundloaders.app") || text.contains("mp3") || text.contains("download")) {
                return href
            }
        }
        throw IllegalStateException("Could not resolve Spotimate track URL")
    }

    fun resolveSpotisaverTrack(trackId: String, encodedName: String, duration: String, encodedCookie: String): String {
        val trackName = ScraperUtils.urlDecode(encodedName)
        val cookie = ScraperUtils.urlDecode(encodedCookie)
        val ctxJson = JSONObject().apply {
            put("lang", "en")
            put("id", trackId)
            put("name", trackName)
            put("duration_ms", duration)
        }.toString()
        val b64 = ScraperUtils.b64EncodeUrlSafe(ctxJson)
        val sigUrl = "https://spotisaver.net/api/get_signature.php?_t=455b447694e16f8ceacaa717f18ba309675dafb36687859a5d69d39798046221&_a=download_track&_c=$b64"
        val sigRes = http.get(sigUrl, mapOf("Referer" to "https://spotisaver.net/en1", "Cookie" to cookie))
        val sigJson = JSONObject(sigRes.body)
        if (!sigJson.has("token")) throw IllegalStateException("Spotisaver token denied")
        return "https://spotisaver.net/api/download_track.php?id=$trackId"
    }
}

