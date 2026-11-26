package com.nimiyo.nativeapp.scraper

import com.nimiyo.nativeapp.core.HttpClientFactory
import com.nimiyo.nativeapp.data.MediaCategory
import com.nimiyo.nativeapp.data.PlatformId
import com.nimiyo.nativeapp.data.ScrapeResult
import com.nimiyo.nativeapp.data.ScraperDownloadItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.jsoup.Jsoup

class InstagramScraper(private val http: HttpClientFactory) {

    suspend fun scrape(url: String, server: String): ScrapeResult = withContext(Dispatchers.IO) {
        val cleanUrl = ScraperUtils.cleanUrl(url)
        val order = when (server) {
            "snapsave" -> listOf("snapsave", "direct", "indown")
            "indown" -> listOf("indown", "direct", "snapsave")
            else -> listOf("direct", "snapsave", "indown")
        }
        var lastError: Throwable? = null
        for (srv in order) {
            val res = runCatching {
                when (srv) {
                    "direct" -> scrapeDirectEmbed(cleanUrl)
                    "snapsave" -> scrapeSnapSave(cleanUrl)
                    else -> scrapeIndown(cleanUrl)
                }
            }
            if (res.isSuccess && res.getOrThrow().items.isNotEmpty()) {
                return@withContext res.getOrThrow()
            }
            lastError = res.exceptionOrNull()
        }
        throw IllegalStateException(lastError?.message ?: "Instagram scrape failed")
    }

    private fun extractShortcode(url: String): String? {
        val m = Regex("""instagram\.com/(?:p|reel|reels|tv)/([a-zA-Z0-9_-]+)""").find(url)
        return m?.groupValues?.get(1)
    }

    private fun scrapeDirectEmbed(url: String): ScrapeResult {
        val shortcode = extractShortcode(url) ?: throw IllegalStateException("Instagram shortcode not found")
        val embedUrl = "https://www.instagram.com/p/$shortcode/embed/captioned/"
        val res = http.get(
            embedUrl,
            mapOf(
                "User-Agent" to HttpClientFactory.SAFARI_MOBILE_UA,
                "Referer" to "https://www.instagram.com/",
            ),
        )
        ScraperUtils.ensureOkOrThrow(res, "IG Embed")
        val html = res.body
        val items = mutableListOf<ScraperDownloadItem>()
        var title = "Instagram Post ($shortcode)"
        var author = ""
        var thumb = ""

        val mediaMatch = Regex(""""shortcode_media"\s*:\s*(\{.*?\})\s*,\s*"extensions"""").find(html)
        if (mediaMatch != null) {
            val media = JSONObject(mediaMatch.groupValues[1])
            author = media.optJSONObject("owner")?.optString("username").orEmpty()
            val caption = media.optJSONObject("edge_media_to_caption")
                ?.optJSONArray("edges")
                ?.optJSONObject(0)
                ?.optJSONObject("node")
                ?.optString("text")
                .orEmpty()
            if (caption.isNotBlank()) title = caption.take(65)
            thumb = media.optString("display_url")

            val sidecar = media.optJSONObject("edge_sidecar_to_children")?.optJSONArray("edges")
            if (sidecar != null && sidecar.length() > 0) {
                for (i in 0 until sidecar.length()) {
                    val node = sidecar.getJSONObject(i).getJSONObject("node")
                    val isVid = node.optBoolean("is_video", false)
                    val dlUrl = if (isVid) node.optString("video_url") else node.optString("display_url")
                    if (dlUrl.startsWith("http")) {
                        items.add(
                            ScraperDownloadItem(
                                id = "ig_sc_$i",
                                title = "$title (${i + 1})",
                                label = if (isVid) "Video Slide ${i + 1}" else "Photo Slide ${i + 1}",
                                url = dlUrl,
                                category = if (isVid) MediaCategory.VIDEO else MediaCategory.IMAGE,
                                extension = if (isVid) "mp4" else "jpg",
                                thumbnail = node.optString("display_url").ifBlank { thumb },
                                author = author,
                            )
                        )
                    }
                }
            } else {
                val isVid = media.optBoolean("is_video", false)
                val dlUrl = if (isVid) media.optString("video_url") else media.optString("display_url")
                if (dlUrl.startsWith("http")) {
                    items.add(
                        ScraperDownloadItem(
                            id = "ig_single",
                            title = title,
                            label = if (isVid) "MP4 Video" else "Original Photo",
                            url = dlUrl,
                            category = if (isVid) MediaCategory.VIDEO else MediaCategory.IMAGE,
                            extension = if (isVid) "mp4" else "jpg",
                            thumbnail = thumb,
                            author = author,
                        )
                    )
                }
            }
        } else {
            val doc = Jsoup.parse(html)
            val videoSrc = doc.selectFirst("video source, video")?.attr("src").orEmpty()
            val imgSrc = doc.selectFirst("img.EmbeddedMediaImage")?.attr("src").orEmpty()
            if (videoSrc.startsWith("http")) {
                items.add(
                    ScraperDownloadItem(
                        id = "ig_dom_vid",
                        title = title,
                        label = "MP4 Video",
                        url = videoSrc,
                        category = MediaCategory.VIDEO,
                        extension = "mp4",
                        thumbnail = imgSrc,
                    )
                )
            } else if (imgSrc.startsWith("http")) {
                items.add(
                    ScraperDownloadItem(
                        id = "ig_dom_img",
                        title = title,
                        label = "Photo",
                        url = imgSrc,
                        category = MediaCategory.IMAGE,
                        extension = "jpg",
                        thumbnail = imgSrc,
                    )
                )
            }
        }

        return ScrapeResult(
            platform = PlatformId.INSTAGRAM,
            serverUsed = "Direct Embed",
            title = title,
            author = author,
            thumbnail = thumb,
            sourceUrl = url,
            items = items,
        )
    }

    private fun scrapeSnapSave(url: String): ScrapeResult {
        val initRes = http.get(
            "https://snapsave.app/id",
            mapOf("Origin" to "https://snapsave.app", "Referer" to "https://snapsave.app/id"),
        )
        val cookies = initRes.cookieString()
        val actionRes = http.postForm(
            "https://snapsave.app/action.php?lang=id",
            mapOf("url" to url),
            mapOf(
                "Origin" to "https://snapsave.app",
                "Referer" to "https://snapsave.app/id",
                "Cookie" to cookies,
            ),
        )
        val html = ScraperUtils.decodeSnapSaveResponse(actionRes.body)
        val doc = Jsoup.parse(html)
        val items = mutableListOf<ScraperDownloadItem>()
        doc.select(".download-items, a[href]").forEachIndexed { i, el ->
            val a = if (el.tagName() == "a") el else el.selectFirst("a[href]") ?: return@forEachIndexed
            val href = a.attr("href")
            if (!href.startsWith("http") || href.contains("snapsave.app")) return@forEachIndexed
            val label = a.text().trim()
            val isVid = label.contains("video", true) || href.contains(".mp4", true)
            items.add(
                ScraperDownloadItem(
                    id = "ig_ss_$i",
                    title = "Instagram Media ${i + 1}",
                    label = label.ifBlank { if (isVid) "Video" else "Photo" },
                    url = href,
                    category = if (isVid) MediaCategory.VIDEO else MediaCategory.IMAGE,
                    extension = if (isVid) "mp4" else "jpg",
                )
            )
        }
        return ScrapeResult(
            platform = PlatformId.INSTAGRAM,
            serverUsed = "SnapSave",
            title = "Instagram Media",
            sourceUrl = url,
            items = items,
        )
    }

    private fun scrapeIndown(url: String): ScrapeResult {
        val res = http.postForm(
            "https://indown.net/api/ajaxSearch",
            mapOf("q" to url, "vt" to "reel", "t" to "media", "lang" to "en", "v" to "v2"),
            mapOf("Origin" to "https://indown.net", "Referer" to "https://indown.net/"),
        )
        val json = JSONObject(res.body)
        val doc = Jsoup.parse(json.optString("data"))
        val items = mutableListOf<ScraperDownloadItem>()
        doc.select("a[href^=http]").forEachIndexed { i, a ->
            val href = a.attr("href")
            val text = a.text().trim()
            val isImg = text.contains("photo", true) || text.contains("image", true)
            items.add(
                ScraperDownloadItem(
                    id = "ig_in_$i",
                    title = "Instagram Media ${i + 1}",
                    label = text.ifBlank { "Download" },
                    url = href,
                    category = if (isImg) MediaCategory.IMAGE else MediaCategory.VIDEO,
                    extension = if (isImg) "jpg" else "mp4",
                )
            )
        }
        return ScrapeResult(
            platform = PlatformId.INSTAGRAM,
            serverUsed = "InDown",
            title = "Instagram Media",
            sourceUrl = url,
            items = items,
        )
    }
}

