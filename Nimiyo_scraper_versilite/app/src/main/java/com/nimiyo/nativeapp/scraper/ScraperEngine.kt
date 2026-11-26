package com.nimiyo.nativeapp.scraper

import com.nimiyo.nativeapp.core.AppSettings
import com.nimiyo.nativeapp.core.HttpClientFactory
import com.nimiyo.nativeapp.data.PlatformId
import com.nimiyo.nativeapp.data.ScrapeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ScraperEngine(
    private val settings: AppSettings,
    val http: HttpClientFactory,
) {
    private val youtube = YouTubeScraper(http)
    private val tiktok = TikTokScraper(http)
    private val instagram = InstagramScraper(http)
    private val spotify = SpotifyScraper(http)
    private val soundcloud = SoundCloudScraper(http)
    private val secondary = SecondaryScrapers(http)

    suspend fun analyze(rawUrl: String, overridePlatform: PlatformId? = null, overrideServer: String? = null): ScrapeResult {
        val clean = ScraperUtils.cleanUrl(rawUrl)
        val platform = overridePlatform ?: PlatformDetector.detect(clean)
            ?: throw IllegalArgumentException("Unsupported or unrecognized link")

        val server = overrideServer ?: settings.current.defaultServerFor(platform) ?: "direct"

        return when (platform) {
            PlatformId.YOUTUBE -> youtube.scrape(clean, server)
            PlatformId.TIKTOK -> tiktok.scrape(clean, server)
            PlatformId.INSTAGRAM -> instagram.scrape(clean, server)
            PlatformId.SPOTIFY -> spotify.scrape(clean, server)
            PlatformId.SOUNDCLOUD -> soundcloud.scrape(clean, server)
            PlatformId.TWITTER -> secondary.scrapeTwitter(clean, server)
            PlatformId.FACEBOOK -> secondary.scrapeFacebook(clean, server)
            PlatformId.PINTEREST -> secondary.scrapePinterest(clean, server)
            PlatformId.THREADS -> secondary.scrapeThreads(clean, server)
            PlatformId.PIXIV -> secondary.scrapePixiv(clean)
            PlatformId.APPLE_MUSIC -> secondary.scrapeAppleMusic(clean)
            PlatformId.BANDCAMP -> secondary.scrapeBandcamp(clean)
            PlatformId.BILIBILI -> secondary.scrapeBilibili(clean)
            PlatformId.DOUYIN -> secondary.scrapeDouyin(clean)
            PlatformId.REDNOTE -> secondary.scrapeRedNote(clean)
            PlatformId.SHOPEE -> secondary.scrapeShopee(clean, server)
        }
    }

    suspend fun resolveLazyDownloadUrl(rawUrl: String): String = withContext(Dispatchers.IO) {
        when {
            rawUrl.startsWith("ytmp3gg_resolve:") -> {
                val parts = rawUrl.removePrefix("ytmp3gg_resolve:").split("|||")
                val videoId = parts.getOrNull(0).orEmpty()
                val fmt = parts.getOrNull(1).orEmpty().ifBlank { "mp3" }
                val quality = parts.getOrNull(2).orEmpty().ifBlank { "128" }
                runCatching {
                    youtube.resolveConvert1sSingle("https://www.youtube.com/watch?v=$videoId", fmt, quality)
                }.getOrElse {
                    youtube.resolveYmcdnSingle(videoId, fmt)
                        ?: throw IllegalStateException("Both ytmp3.gg and ytmp3.mobi lazy resolve failed")
                }
            }
            rawUrl.startsWith("spotidown_resolve:") -> {
                val parts = rawUrl.removePrefix("spotidown_resolve:").split("|||")
                spotify.resolveSpotiDownTrack(parts.getOrNull(0).orEmpty(), parts.getOrNull(1).orEmpty())
            }
            rawUrl.startsWith("soundloaders_resolve:") -> {
                val parts = rawUrl.removePrefix("soundloaders_resolve:").split("|||")
                spotify.resolveSpotimateTrack(
                    parts.getOrNull(0).orEmpty(),
                    parts.getOrNull(1).orEmpty(),
                    parts.getOrNull(2).orEmpty(),
                    parts.getOrNull(3).orEmpty(),
                )
            }
            rawUrl.startsWith("spotisaver_resolve:") -> {
                val parts = rawUrl.removePrefix("spotisaver_resolve:").split("|||")
                spotify.resolveSpotisaverTrack(
                    parts.getOrNull(0).orEmpty(),
                    parts.getOrNull(1).orEmpty(),
                    parts.getOrNull(2).orEmpty().ifBlank { "0" },
                    parts.getOrNull(3).orEmpty(),
                )
            }
            rawUrl.startsWith("ytsearch_resolve:") -> {
                val query = ScraperUtils.urlDecode(rawUrl.removePrefix("ytsearch_resolve:"))
                val searchRes = http.get(
                    "https://www.youtube.com/results?search_query=${ScraperUtils.urlEncode("$query audio")}",
                )
                val videoId = findFirstOrganicYouTubeVideoId(searchRes.body)
                    ?: throw IllegalStateException("No matching YouTube audio found for: $query")
                runCatching {
                    youtube.resolveConvert1sSingle("https://www.youtube.com/watch?v=$videoId", "mp3", "128")
                }.getOrElse {
                    youtube.resolveYmcdnSingle(videoId, "mp3")
                        ?: throw IllegalStateException("Failed to resolve MP3 for $query")
                }
            }
            rawUrl.startsWith("ytsearch_mobi_resolve:") -> {
                val query = ScraperUtils.urlDecode(rawUrl.removePrefix("ytsearch_mobi_resolve:"))
                val searchRes = http.get(
                    "https://www.youtube.com/results?search_query=${ScraperUtils.urlEncode("$query audio")}",
                )
                val videoId = findFirstOrganicYouTubeVideoId(searchRes.body)
                    ?: throw IllegalStateException("No matching YouTube audio found for: $query")
                runCatching {
                    youtube.resolveYmcdnSingle(videoId, "mp3")
                }.getOrNull() ?: youtube.resolveConvert1sSingle("https://www.youtube.com/watch?v=$videoId", "mp3", "128")
            }
            rawUrl.startsWith("applemusic_resolve:") -> {
                secondary.resolveAppleMusicTrack(rawUrl.removePrefix("applemusic_resolve:"))
            }
            else -> rawUrl
        }
    }

    private fun findFirstOrganicYouTubeVideoId(html: String): String? {
        val dataMatch = Regex("""var\s+ytInitialData\s*=\s*(\{.*?\})\s*;\s*</script>""", RegexOption.DOT_MATCHES_ALL)
            .find(html)
            ?: Regex("""ytInitialData"\s*\]\s*=\s*(\{.*?\})\s*;""", RegexOption.DOT_MATCHES_ALL).find(html)
        if (dataMatch != null) {
            val parsed = runCatching { org.json.JSONObject(dataMatch.groupValues[1]) }.getOrNull()
            if (parsed != null) {
                findOrganicVideoRendererId(parsed)?.let { return it }
            }
        }
        return Regex(""""videoRenderer"\s*:\s*\{\s*"videoId"\s*:\s*"([a-zA-Z0-9_-]{11})"""")
            .find(html)?.groupValues?.getOrNull(1)
    }

    private fun findOrganicVideoRendererId(node: Any?): String? {
        when (node) {
            is org.json.JSONObject -> {
                if (node.has("adSlotRenderer") ||
                    node.has("promotedSparklesWebRenderer") ||
                    node.has("searchPyvRenderer") ||
                    node.has("inLineAdRenderer") ||
                    node.has("reelShelfRenderer")
                ) {
                    return null
                }
                val vr = node.optJSONObject("videoRenderer")
                if (vr != null) {
                    val id = vr.optString("videoId").trim()
                    if (id.length == 11) return id
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    if (k.contains("ad", ignoreCase = true) && k.contains("renderer", ignoreCase = true)) continue
                    findOrganicVideoRendererId(node.opt(k))?.let { return it }
                }
            }
            is org.json.JSONArray -> {
                for (i in 0 until node.length()) {
                    findOrganicVideoRendererId(node.opt(i))?.let { return it }
                }
            }
        }
        return null
    }
}

