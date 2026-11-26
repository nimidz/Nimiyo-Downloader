package com.nimiyo.nativeapp.scraper

import android.util.Base64
import com.nimiyo.nativeapp.core.HttpClientFactory
import com.nimiyo.nativeapp.data.MediaCategory
import com.nimiyo.nativeapp.data.PlatformId
import com.nimiyo.nativeapp.data.ScrapeResult
import com.nimiyo.nativeapp.data.ScraperDownloadItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.nio.charset.StandardCharsets

class SecondaryScrapers(private val http: HttpClientFactory) {

    // ==================== X / TWITTER ====================
    suspend fun scrapeTwitter(url: String, server: String): ScrapeResult = withContext(Dispatchers.IO) {
        val cleanUrl = ScraperUtils.cleanUrl(url).substringBefore("?")
        val order = listOf(server) + listOf("direct", "tweeload", "tvd").filterNot { it == server }
        var lastError: Throwable? = null

        for (srv in order) {
            val res = runCatching {
                when (srv) {
                    "tweeload" -> scrapeTwitterTweeload(cleanUrl)
                    "tvd" -> scrapeTwitterTvd(cleanUrl)
                    else -> scrapeTwitterFxTwitter(cleanUrl)
                }
            }
            if (res.isSuccess) return@withContext res.getOrThrow()
            lastError = res.exceptionOrNull()
        }
        throw IllegalStateException(lastError?.message ?: "All Twitter servers failed")
    }

    private fun scrapeTwitterFxTwitter(cleanUrl: String): ScrapeResult {
        val idMatch = Regex("""status(?:es)?/(\d+)""", RegexOption.IGNORE_CASE).find(cleanUrl)
            ?: throw IllegalArgumentException("Invalid X / Twitter status URL")
        val tweetId = idMatch.groupValues[1]

        val res = http.get("https://api.fxtwitter.com/status/$tweetId")
        val json = JSONObject(res.body)
        if (json.optInt("code") != 200) {
            throw IllegalStateException(json.optString("message", "FxTwitter scrape failed"))
        }
        val tweet = json.getJSONObject("tweet")
        val author = tweet.optJSONObject("author")?.optString("screen_name").orEmpty()
        val text = tweet.optString("text").take(70).ifBlank { "X Post $tweetId" }
        val media = tweet.optJSONObject("media") ?: throw IllegalStateException("No downloadable media found in tweet")
        val items = mutableListOf<ScraperDownloadItem>()
        var thumb = ""

        val videos = media.optJSONArray("videos")
        if (videos != null) {
            for (i in 0 until videos.length()) {
                val v = videos.getJSONObject(i)
                val vUrl = v.optString("url")
                val vThumb = v.optString("thumbnail_url")
                if (thumb.isBlank()) thumb = vThumb
                if (vUrl.startsWith("http")) {
                    items.add(
                        ScraperDownloadItem(
                            id = "tw_vid_$i",
                            title = text,
                            label = "MP4 Video ${v.optInt("width")}x${v.optInt("height")}",
                            url = vUrl,
                            category = MediaCategory.VIDEO,
                            extension = "mp4",
                            thumbnail = vThumb,
                            author = author,
                        )
                    )
                }
            }
        }
        val photos = media.optJSONArray("photos")
        if (photos != null) {
            for (i in 0 until photos.length()) {
                val p = photos.getJSONObject(i)
                val pUrl = p.optString("url")
                if (thumb.isBlank()) thumb = pUrl
                if (pUrl.startsWith("http")) {
                    items.add(
                        ScraperDownloadItem(
                            id = "tw_img_$i",
                            title = "$text (${i + 1})",
                            label = "Photo ${i + 1}",
                            url = pUrl,
                            category = MediaCategory.IMAGE,
                            extension = "jpg",
                            thumbnail = pUrl,
                            author = author,
                        )
                    )
                }
            }
        }
        if (items.isEmpty()) throw IllegalStateException("FxTwitter returned no media")
        return ScrapeResult(PlatformId.TWITTER, "FxTwitter", text, author, thumb, cleanUrl, items)
    }

    private fun isTwitterInvalidOrPaywall(href: String, text: String): Boolean {
        val h = href.lowercase()
        val t = text.lowercase()
        return h.isBlank() ||
            h.contains("ko-fi.com") ||
            h.contains("premium") ||
            t.contains("go pro") ||
            t.contains("mobile app") ||
            h == "#"
    }

    private fun scrapeTwitterTweeload(cleanUrl: String): ScrapeResult {
        val twitterUrl = cleanUrl.replace(Regex("""https://(?:fixupx|fxtwitter|vxtwitter|nitter|twitter)\.com"""), "https://x.com")
        val init = http.get("https://tweeload.com/en")
        val res = http.postForm(
            "https://tweeload.com/en/download",
            mapOf("url" to twitterUrl),
            mapOf(
                "Origin" to "https://tweeload.com",
                "Referer" to "https://tweeload.com/en",
                "Cookie" to init.cookieString(),
            ),
        )
        ScraperUtils.ensureOkOrThrow(res, "Tweeload")
        val doc = Jsoup.parse(res.body)
        val name = doc.selectFirst(".download__item__info__user__name")?.text()?.trim().orEmpty()
        val handle = doc.selectFirst(".download__item__info__user__handle")?.text()?.trim().orEmpty()
        val thumb = doc.selectFirst(".download__item__preview img, .download__item img")?.attr("src").orEmpty()
        val title = if (name.isNotBlank()) "$name ($handle)".trim() else "Twitter/X Media"

        val items = mutableListOf<ScraperDownloadItem>()
        val seen = HashSet<String>()

        doc.select(".download__item__info__actions tbody tr").forEachIndexed { idx, tr ->
            val quality = tr.selectFirst("td")?.text()?.trim().orEmpty()
            var dlUrl = tr.selectFirst("a.download__item__info__actions__button")?.attr("href").orEmpty()
            if (dlUrl.startsWith("/")) dlUrl = "https://tweeload.com$dlUrl"
            if (dlUrl.startsWith("http") && !isTwitterInvalidOrPaywall(dlUrl, quality) && seen.add(dlUrl)) {
                val isImg = Regex("""\.(jpe?g|png|webp)(\?|$)""", RegexOption.IGNORE_CASE).containsMatchIn(dlUrl) ||
                    quality.contains("image", true) || quality.contains("photo", true)
                items.add(
                    ScraperDownloadItem(
                        id = "tw_tl_$idx",
                        title = title,
                        label = quality.ifBlank { if (isImg) "Photo" else "MP4 Video" },
                        url = dlUrl,
                        category = if (isImg) MediaCategory.IMAGE else MediaCategory.VIDEO,
                        extension = if (isImg) "jpg" else "mp4",
                        thumbnail = thumb,
                        author = handle,
                    )
                )
            }
        }

        if (items.isEmpty()) {
            doc.select("a.btn").forEachIndexed { idx, a ->
                val href = a.attr("href")
                val text = a.text().trim()
                if (href.startsWith("http") &&
                    (href.contains("downloads.acxcdn.com") || href.contains("twimg.com") || href.contains("tweeload")) &&
                    !isTwitterInvalidOrPaywall(href, text) && seen.add(href)
                ) {
                    val isImg = Regex("""\.(jpe?g|png|webp)(\?|$)""", RegexOption.IGNORE_CASE).containsMatchIn(href)
                    items.add(
                        ScraperDownloadItem(
                            id = "tw_tl_b_$idx",
                            title = title,
                            label = text.ifBlank { if (isImg) "Photo" else "MP4 Video" },
                            url = href,
                            category = if (isImg) MediaCategory.IMAGE else MediaCategory.VIDEO,
                            extension = if (isImg) "jpg" else "mp4",
                            thumbnail = thumb,
                            author = handle,
                        )
                    )
                }
            }
        }

        if (items.isEmpty()) throw IllegalStateException("Tweeload returned no links")
        return ScrapeResult(PlatformId.TWITTER, "Tweeload", title, handle, thumb, cleanUrl, items)
    }

    private fun scrapeTwitterTvd(cleanUrl: String): ScrapeResult {
        val twitterUrl = cleanUrl.replace(Regex("""https://(?:x|fixupx|fxtwitter|vxtwitter|nitter)\.com"""), "https://twitter.com")
        val init = http.get("https://twittervideodownloader.com/")
        ScraperUtils.ensureOkOrThrow(init, "TwitterVid Main")
        val doc1 = Jsoup.parse(init.body)
        val csrf = doc1.selectFirst("""input[name="csrfmiddlewaretoken"]""")?.attr("value").orEmpty()
        val gql = doc1.selectFirst("""input[name="gql"]""")?.attr("value").orEmpty()
        if (csrf.isBlank()) throw IllegalStateException("TwitterVid CSRF token not found")

        val dlRes = http.postForm(
            "https://twittervideodownloader.com/download",
            mapOf("tweet" to twitterUrl, "csrfmiddlewaretoken" to csrf, "gql" to gql),
            mapOf("Referer" to "https://twittervideodownloader.com/", "Cookie" to init.cookieString()),
        )
        ScraperUtils.ensureOkOrThrow(dlRes, "TwitterVid Download")
        val doc2 = Jsoup.parse(dlRes.body)
        val thumb = doc2.selectFirst("img[src*='twimg.com'], .card img")?.attr("src")
            ?: doc2.selectFirst("video")?.attr("poster").orEmpty()

        val items = mutableListOf<ScraperDownloadItem>()
        val seen = HashSet<String>()
        doc2.select(".card-body").forEach { card ->
            val qText = card.selectFirst(".card-text")?.text()?.trim().orEmpty()
            card.select("a.btn-download, a.btn").forEachIndexed { idx, btn ->
                val href = btn.attr("href")
                val bText = btn.text().trim()
                if (href.startsWith("http") && !isTwitterInvalidOrPaywall(href, "$bText $qText") && seen.add(href)) {
                    val isImg = Regex("""\.(jpe?g|png|webp)(\?|$)""", RegexOption.IGNORE_CASE).containsMatchIn(href)
                    items.add(
                        ScraperDownloadItem(
                            id = "tw_tvd_${items.size}_$idx",
                            title = "Twitter/X Video",
                            label = bText.ifBlank { qText.ifBlank { if (isImg) "Photo" else "MP4 Video" } },
                            url = href,
                            category = if (isImg) MediaCategory.IMAGE else MediaCategory.VIDEO,
                            extension = if (isImg) "jpg" else "mp4",
                            thumbnail = thumb,
                        )
                    )
                }
            }
        }
        if (items.isEmpty()) {
            doc2.select("""a[href*="video.twimg.com"], a[href*="twimg.com"]""").forEachIndexed { idx, a ->
                val href = a.attr("href")
                val text = a.text().trim()
                if (href.startsWith("http") && !isTwitterInvalidOrPaywall(href, text) && seen.add(href)) {
                    items.add(
                        ScraperDownloadItem(
                            id = "tw_tvd_d_$idx",
                            title = "Twitter/X Video",
                            label = text.ifBlank { "MP4 Video" },
                            url = href,
                            category = MediaCategory.VIDEO,
                            extension = "mp4",
                            thumbnail = thumb,
                        )
                    )
                }
            }
        }
        if (items.isEmpty()) throw IllegalStateException("TwitterVid returned no links")
        return ScrapeResult(PlatformId.TWITTER, "TwitterVid", "Twitter/X Video", thumbnail = thumb, sourceUrl = cleanUrl, items = items)
    }

    // ==================== FACEBOOK ====================
    suspend fun scrapeFacebook(url: String, server: String = "snapsave"): ScrapeResult = withContext(Dispatchers.IO) {
        val cleanUrl = ScraperUtils.cleanUrl(url)
        val order = listOf(server) + listOf("snapsave", "direct").filterNot { it == server }
        var lastError: Throwable? = null

        for (srv in order) {
            val res = runCatching {
                when (srv) {
                    "direct" -> scrapeFacebookDirect(cleanUrl)
                    else -> scrapeFacebookSnapSave(cleanUrl)
                }
            }
            if (res.isSuccess) return@withContext res.getOrThrow()
            lastError = res.exceptionOrNull()
        }
        throw IllegalStateException(lastError?.message ?: "All Facebook servers failed")
    }

    private fun scrapeFacebookSnapSave(url: String): ScrapeResult {
        val headers = mapOf("Origin" to "https://snapsave.app", "Referer" to "https://snapsave.app/id")
        val init = http.get("https://snapsave.app/id", headers)
        val act = http.postForm(
            "https://snapsave.app/action.php?lang=id",
            mapOf("url" to url),
            headers + mapOf("Cookie" to init.cookieString()),
        )
        val html = ScraperUtils.decodeSnapSaveResponse(act.body)
        val doc = Jsoup.parse(html)
        val items = mutableListOf<ScraperDownloadItem>()
        doc.select("table tbody tr").forEachIndexed { idx, tr ->
            val quality = tr.selectFirst("td.video-quality, td")?.text()?.trim().orEmpty().ifBlank { "HD MP4" }
            val link = tr.selectFirst("a[href^=http]")?.attr("href").orEmpty()
            if (link.startsWith("http")) {
                items.add(
                    ScraperDownloadItem(
                        id = "fb_$idx",
                        title = "Facebook Video",
                        label = quality,
                        url = link,
                        category = MediaCategory.VIDEO,
                        extension = "mp4",
                    )
                )
            }
        }
        if (items.isEmpty()) throw IllegalStateException("SnapSave returned no Facebook links")
        return ScrapeResult(PlatformId.FACEBOOK, "SnapSave", "Facebook Video", sourceUrl = url, items = items)
    }

    private fun scrapeFacebookDirect(url: String): ScrapeResult {
        val res = http.get(
            url,
            mapOf(
                "User-Agent" to "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)",
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            ),
        )
        val html = res.body
        val title = extractMeta(html, "og:title").ifBlank { "Facebook Video" }
        val thumb = extractMeta(html, "og:image")
        val items = mutableListOf<ScraperDownloadItem>()
        val seen = HashSet<String>()

        fun addVid(rawUrl: String, label: String) {
            val clean = rawUrl.replace("\\u0025", "%").replace("\\/", "/").replace("&amp;", "&")
            if (clean.startsWith("http") && seen.add(clean)) {
                items.add(
                    ScraperDownloadItem(
                        id = "fb_dir_${items.size}",
                        title = title,
                        label = label,
                        url = clean,
                        category = MediaCategory.VIDEO,
                        extension = "mp4",
                        thumbnail = thumb,
                    )
                )
            }
        }

        Regex(""""browser_native_hd_url":"([^"]+)"""").find(html)?.groupValues?.get(1)?.let { addVid(it, "HD MP4") }
        Regex(""""playable_url_quality_hd":"([^"]+)"""").find(html)?.groupValues?.get(1)?.let { addVid(it, "HD Quality MP4") }
        Regex(""""browser_native_sd_url":"([^"]+)"""").find(html)?.groupValues?.get(1)?.let { addVid(it, "SD MP4") }
        Regex(""""playable_url":"([^"]+)"""").find(html)?.groupValues?.get(1)?.let { addVid(it, "MP4 Video") }
        val ogVid = extractMeta(html, "og:video:url").ifBlank { extractMeta(html, "og:video") }
        if (ogVid.startsWith("http") &&
            !ogVid.contains("facebook.com/plugins/", ignoreCase = true) &&
            (ogVid.contains("fbcdn.net", ignoreCase = true) || ogVid.contains(".mp4", ignoreCase = true))
        ) {
            addVid(ogVid, "Direct OG Video")
        }

        if (items.isEmpty()) {
            val vidId = Regex("""(?:v=|/videos/|/reel/)(\d+)""").find(url)?.groupValues?.get(1)
            if (!vidId.isNullOrBlank()) {
                runCatching {
                    val mb = http.get(
                        "https://mbasic.facebook.com/watch/?v=$vidId",
                        mapOf("User-Agent" to HttpClientFactory.SAFARI_MOBILE_UA),
                    ).body
                    Regex("""/video_redirect/\?src=([^"&\s]+)""").find(mb)?.groupValues?.get(1)?.let { enc ->
                        addVid(ScraperUtils.urlDecode(enc), "MP4 Video (mbasic)")
                    }
                    Regex("""https://video[^"'\s<>]+?\.mp4[^"'\s<>]*""").find(mb)?.value?.let {
                        addVid(it, "MP4 Video (CDN)")
                    }
                }
            }
        }

        if (items.isEmpty()) throw IllegalStateException("Facebook Direct OG returned no video links")
        return ScrapeResult(PlatformId.FACEBOOK, "Direct OG", title, thumbnail = thumb, sourceUrl = url, items = items)
    }

    // ==================== PINTEREST ====================
    suspend fun scrapePinterest(url: String, server: String = "direct"): ScrapeResult = withContext(Dispatchers.IO) {
        var targetUrl = ScraperUtils.cleanUrl(url)
        if (targetUrl.contains("pin.it")) {
            val exp = http.get(targetUrl)
            val canon = Regex("""<link[^>]+rel="canonical"[^>]+href="([^"]+)"""").find(exp.body)?.groupValues?.get(1)
            targetUrl = canon ?: exp.finalUrl
        }
        val order = listOf(server) + listOf("direct", "pindown").filterNot { it == server }
        var lastError: Throwable? = null

        for (srv in order) {
            val res = runCatching {
                when (srv) {
                    "pindown" -> scrapePinterestPinDown(targetUrl)
                    else -> scrapePinterestDirect(targetUrl)
                }
            }
            if (res.isSuccess) return@withContext res.getOrThrow()
            lastError = res.exceptionOrNull()
        }
        throw IllegalStateException(lastError?.message ?: "All Pinterest servers failed")
    }

    private fun scrapePinterestDirect(targetUrl: String): ScrapeResult {
        val res = http.get(targetUrl, mapOf("Accept" to "text/html,*/*"))
        val html = res.body
        val title = extractMeta(html, "og:title").replace(" | Pinterest", "").trim().ifBlank { "Pinterest Pin" }
        val items = mutableListOf<ScraperDownloadItem>()
        val seen = HashSet<String>()

        Regex("""https://(?:v1|7|v)\.pinimg\.com/[^"'\s<>]+\.mp4""").findAll(html).forEachIndexed { idx, m ->
            if (seen.add(m.value)) {
                items.add(
                    ScraperDownloadItem(
                        id = "pin_vid_$idx",
                        title = title,
                        label = "Pinterest Video (MP4)",
                        url = m.value,
                        category = MediaCategory.VIDEO,
                        extension = "mp4",
                    )
                )
            }
        }
        Regex("""https://i\.pinimg\.com/(?:originals|736x)/[a-zA-Z0-9/._-]+\.(?:jpg|jpeg|png|webp)""").findAll(html)
            .forEachIndexed { idx, m ->
                val u = m.value
                if (!u.contains("avatar") && !u.contains("75x75") && seen.add(u)) {
                    items.add(
                        ScraperDownloadItem(
                            id = "pin_img_$idx",
                            title = title,
                            label = "Original Image",
                            url = u,
                            category = MediaCategory.IMAGE,
                            extension = u.substringAfterLast(".", "jpg").lowercase(),
                            thumbnail = u,
                        )
                    )
                }
            }
        if (items.isEmpty()) throw IllegalStateException("No media found via Pinterest Direct")
        return ScrapeResult(PlatformId.PINTEREST, "Pinterest Direct", title, thumbnail = items.first().thumbnail, sourceUrl = targetUrl, items = items)
    }

    private fun scrapePinterestPinDown(targetUrl: String): ScrapeResult {
        val init = http.get("https://pindown.io/")
        ScraperUtils.ensureOkOrThrow(init, "PinDown Main")
        val cookies = init.cookieString()
        val doc1 = Jsoup.parse(init.body)
        val hidden = doc1.selectFirst("""input[type="hidden"]:not([name="lang"])""")
        val tokenName = hidden?.attr("name").orEmpty()
        val tokenValue = hidden?.attr("value").orEmpty()
        if (tokenName.isBlank() || tokenValue.isBlank()) throw IllegalStateException("PinDown token not found")

        val act = http.postForm(
            "https://pindown.io/action",
            mapOf("url" to targetUrl, tokenName to tokenValue, "lang" to "en"),
            mapOf(
                "X-Requested-With" to "XMLHttpRequest",
                "Origin" to "https://pindown.io",
                "Referer" to "https://pindown.io/",
                "Cookie" to cookies,
            ),
        )
        ScraperUtils.ensureOkOrThrow(act, "PinDown Action")
        val html = if (act.body.trimStart().startsWith("{")) JSONObject(act.body).optString("html") else act.body
        val doc2 = Jsoup.parse(html)
        val items = mutableListOf<ScraperDownloadItem>()
        doc2.select(".columns .column").forEachIndexed { idx, el ->
            val labelText = el.selectFirst(".is-size-6")?.text()?.trim().orEmpty()
            var dlUrl = el.selectFirst(".button")?.attr("href").orEmpty()
            if (dlUrl.contains("file=") && dlUrl.contains("http")) {
                Regex("""file=(https?%3A%2F%2F[^&]+|https?://[^&]+)""", RegexOption.IGNORE_CASE).find(dlUrl)?.groupValues?.get(1)?.let {
                    dlUrl = java.net.URLDecoder.decode(it, "UTF-8")
                }
            }
            if (dlUrl.startsWith("http")) {
                val lower = dlUrl.lowercase()
                val isVid = lower.contains(".mp4") || lower.contains("/videos/") || labelText.contains("video", true)
                items.add(
                    ScraperDownloadItem(
                        id = "pin_pd_$idx",
                        title = "Pinterest Pin",
                        label = labelText.ifBlank { if (isVid) "MP4 Video" else "Image" },
                        url = dlUrl,
                        category = if (isVid) MediaCategory.VIDEO else MediaCategory.IMAGE,
                        extension = if (isVid) "mp4" else "jpg",
                        thumbnail = if (isVid) "" else dlUrl,
                    )
                )
            }
        }
        if (items.isEmpty()) throw IllegalStateException("PinDown returned no links")
        return ScrapeResult(PlatformId.PINTEREST, "PinDown", "Pinterest Pin", thumbnail = items.firstOrNull()?.thumbnail.orEmpty(), sourceUrl = targetUrl, items = items)
    }

    // ==================== THREADS ====================
    suspend fun scrapeThreads(url: String, server: String = "direct"): ScrapeResult = withContext(Dispatchers.IO) {
        var resolvedUrl = ScraperUtils.cleanUrl(url)
        if (resolvedUrl.contains("/share/") || resolvedUrl.contains("l.threads.net")) {
            runCatching {
                val r = http.get(resolvedUrl)
                if (r.finalUrl.contains("/post/")) resolvedUrl = r.finalUrl
            }
        }
        val order = listOf(server) + listOf("direct", "threadster").filterNot { it == server }
        var lastError: Throwable? = null

        for (srv in order) {
            val res = runCatching {
                when (srv) {
                    "threadster" -> scrapeThreadsThreadster(resolvedUrl)
                    else -> scrapeThreadsDirectOg(resolvedUrl)
                }
            }
            if (res.isSuccess) return@withContext res.getOrThrow()
            lastError = res.exceptionOrNull()
        }
        throw IllegalStateException(lastError?.message ?: "All Threads servers failed")
    }

    private fun decodeJwtTokenUrl(href: String): String {
        val m = Regex("""[?&]token=([^&]+)""").find(href) ?: return href
        val parts = m.groupValues[1].split(".")
        if (parts.size < 2) return href
        return runCatching {
            val b64 = parts[1].replace('-', '+').replace('_', '/')
            val padded = b64.padEnd((b64.length + 3) / 4 * 4, '=')
            val payload = JSONObject(String(Base64.decode(padded, Base64.DEFAULT), StandardCharsets.UTF_8))
            payload.optString("url").ifBlank { href }
        }.getOrDefault(href)
    }

    private fun scrapeThreadsThreadster(url: String): ScrapeResult {
        val targetUrl = url.replace("threads.com", "threads.net", ignoreCase = true)
        val main = http.get("https://threadster.app/")
        val dl = http.postForm(
            "https://threadster.app/download",
            mapOf("url" to targetUrl),
            mapOf("Origin" to "https://threadster.app", "Referer" to "https://threadster.app/", "Cookie" to main.cookieString()),
        )
        ScraperUtils.ensureOkOrThrow(dl, "Threadster")
        val doc = Jsoup.parse(dl.body)
        val title = doc.selectFirst(".card-title, .video-title, h1, h2, h3")?.text()?.trim().orEmpty().ifBlank { "Threads Post" }
        val items = mutableListOf<ScraperDownloadItem>()
        val seen = HashSet<String>()

        doc.select("a.download__item__info_btn, a[href]").forEach { a ->
            val href = a.attr("href")
            if (!href.startsWith("http") || (href.contains("threadster.app") && !href.contains("token="))) return@forEach
            val finalUrl = if (href.contains("token=")) decodeJwtTokenUrl(href) else href
            if (!finalUrl.startsWith("http") || !seen.add(finalUrl)) return@forEach
            val lower = finalUrl.lowercase()
            val isImg = lower.contains(".jpg") || lower.contains(".jpeg") || lower.contains(".png") || lower.contains(".webp")
            val isVid = !isImg && (lower.contains(".mp4") || a.text().contains("video", true) || href.contains("token="))
            items.add(
                ScraperDownloadItem(
                    id = "th_${items.size}",
                    title = title,
                    label = if (isVid) "HD Video ${items.size + 1}" else "HD Photo ${items.size + 1}",
                    url = finalUrl,
                    category = if (isVid) MediaCategory.VIDEO else MediaCategory.IMAGE,
                    extension = if (isVid) "mp4" else "jpg",
                    thumbnail = finalUrl,
                )
            )
        }
        if (items.isEmpty()) throw IllegalStateException("Threadster returned no download links")
        return ScrapeResult(PlatformId.THREADS, "Threadster", title, thumbnail = items.first().thumbnail, sourceUrl = url, items = items)
    }

    private fun scrapeThreadsDirectOg(url: String): ScrapeResult {
        val comUrl = url.replace("threads.net", "threads.com", ignoreCase = true)
        val candidates = listOf(
            comUrl to HttpClientFactory.CHROME_DESKTOP_UA,
            comUrl to "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)",
            url to "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)",
        ).distinct()

        val items = mutableListOf<ScraperDownloadItem>()
        val seen = HashSet<String>()
        var title = "Threads Post"
        var thumb = ""

        for ((targetUrl, ua) in candidates) {
            val res = runCatching {
                http.get(
                    targetUrl,
                    mapOf(
                        "User-Agent" to ua,
                        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                        "Accept-Language" to "en-US,en;q=0.9",
                    ),
                )
            }.getOrNull() ?: continue
            val html = res.body
            val ogTitle = extractMeta(html, "og:title")
            if (ogTitle.isNotBlank() && title == "Threads Post") title = ogTitle
            val ogImg = extractMeta(html, "og:image").replace("&amp;", "&")
            if (ogImg.startsWith("http") && !ogImg.contains("static.cdninstagram.com") && thumb.isBlank()) {
                thumb = ogImg
            }

            // Parse Meta data-sjs / SSR video_versions and playable_url
            Regex(""""(?:video_versions|playable_url[^"]*)":\s*(?:\[\s*\{\s*"[^}]*"url"\s*:\s*"([^"]+)"|"([^"]+)")""")
                .findAll(html).forEach { m ->
                    val raw = (m.groupValues[1].ifBlank { m.groupValues[2] })
                        .replace("\\/", "/")
                        .replace("\\u0026", "&")
                    if (raw.startsWith("http") && (raw.contains(".mp4") || raw.contains("video")) && seen.add(raw)) {
                        items.add(
                            ScraperDownloadItem("th_sjs_${items.size}", title, "HD Video ${items.size + 1}", raw, MediaCategory.VIDEO, "mp4", thumbnail = thumb),
                        )
                    }
                }

            val ogVid = extractMeta(html, "og:video:url").ifBlank { extractMeta(html, "og:video") }.replace("&amp;", "&")
            if (ogVid.startsWith("http") && seen.add(ogVid)) {
                items.add(
                    ScraperDownloadItem("th_ogv_${items.size}", title, "HD Video", ogVid, MediaCategory.VIDEO, "mp4", thumbnail = thumb),
                )
            }
            if (items.isEmpty() && ogImg.startsWith("http") && !ogImg.contains("static.cdninstagram.com") && seen.add(ogImg)) {
                items.add(
                    ScraperDownloadItem("th_ogi_${items.size}", title, "Photo", ogImg, MediaCategory.IMAGE, "jpg", thumbnail = ogImg),
                )
            }
            if (items.isNotEmpty()) {
                return ScrapeResult(PlatformId.THREADS, "Direct SSR / OG", title, thumbnail = thumb.ifBlank { items.first().thumbnail }, sourceUrl = url, items = items)
            }
        }
        throw IllegalStateException("Threads Direct SSR/OG returned no media (post may be text-only or login-gated)")
    }

    // ==================== PIXIV ====================
    suspend fun scrapePixiv(url: String): ScrapeResult = withContext(Dispatchers.IO) {
        val idMatch = Regex("""(?:artworks/|illust_id=)(\d+)""").find(url)
            ?: throw IllegalArgumentException("Invalid Pixiv artwork URL")
        val illustId = idMatch.groupValues[1]
        val res = http.get(
            "https://www.pixiv.net/ajax/illust/$illustId?lang=en",
            mapOf("Referer" to "https://www.pixiv.net/"),
        )
        val body = JSONObject(res.body).getJSONObject("body")
        val title = body.optString("title", "Pixiv #$illustId")
        val author = body.optString("userName")
        val pagesRes = http.get(
            "https://www.pixiv.net/ajax/illust/$illustId/pages?lang=en",
            mapOf("Referer" to "https://www.pixiv.net/"),
        )
        val pages = JSONObject(pagesRes.body).optJSONArray("body") ?: JSONArray()
        val items = mutableListOf<ScraperDownloadItem>()
        for (i in 0 until pages.length()) {
            val urls = pages.getJSONObject(i).optJSONObject("urls") ?: continue
            val orig = urls.optString("original")
            val regular = urls.optString("regular")
            if (orig.startsWith("http")) {
                items.add(
                    ScraperDownloadItem(
                        id = "px_$i",
                        title = "$title (${i + 1})",
                        label = "Original Page ${i + 1}",
                        url = orig,
                        category = MediaCategory.IMAGE,
                        extension = orig.substringAfterLast(".", "jpg").substringBefore("?"),
                        thumbnail = regular.ifBlank { orig },
                        author = author,
                        referer = "https://www.pixiv.net/",
                    )
                )
            }
        }
        if (items.isEmpty()) throw IllegalStateException("Pixiv AJAX returned no pages")
        ScrapeResult(PlatformId.PIXIV, "Pixiv AJAX", title, author, items.firstOrNull()?.thumbnail.orEmpty(), url, items)
    }

    // ==================== APPLE MUSIC ====================
    suspend fun scrapeAppleMusic(url: String): ScrapeResult = withContext(Dispatchers.IO) {
        val aplmateRes = runCatching { scrapeAppleMusicAplmate(url) }
        if (aplmateRes.isSuccess && aplmateRes.getOrThrow().items.isNotEmpty()) {
            return@withContext aplmateRes.getOrThrow()
        }
        scrapeAppleMusicDirect(url)
    }

    private fun scrapeAppleMusicAplmate(url: String): ScrapeResult {
        val home = http.get("https://aplmate.com/")
        val cookies = home.cookieString()
        val verify = http.postForm(
            "https://aplmate.com/action/userverify",
            mapOf("url" to url),
            mapOf("X-Requested-With" to "XMLHttpRequest", "Referer" to "https://aplmate.com/", "Cookie" to cookies),
        )
        val token = JSONObject(verify.body).optString("token")
        if (token.isBlank()) throw IllegalStateException("Aplmate verification token missing")
        val act = http.postForm(
            "https://aplmate.com/action",
            mapOf("url" to url, "cf-turnstile-response" to token),
            mapOf("Referer" to "https://aplmate.com/", "Cookie" to cookies),
        )
        val doc2 = Jsoup.parse(JSONObject(act.body).optString("html"))
        val forms2 = doc2.select("""form[name="submitapurl"]""")
        val items = mutableListOf<ScraperDownloadItem>()
        val isMulti = forms2.size > 1
        var firstMetaTitle = ""
        var firstMetaArtist = ""

        forms2.forEachIndexed { idx, form ->
            val fields = linkedMapOf<String, String>()
            form.select("input").forEach { inp ->
                val n = inp.attr("name")
                if (n.isNotBlank()) fields[n] = inp.attr("value")
            }
            val payloadStr = fields.entries.joinToString("&") {
                "${ScraperUtils.urlEncode(it.key)}=${ScraperUtils.urlEncode(it.value)}"
            }
            val fb = fields["data"].orEmpty()
            var itemTitle = ""
            if (fb.isNotBlank()) {
                runCatching {
                    val dec = JSONObject(String(Base64.decode(fb, Base64.DEFAULT), StandardCharsets.UTF_8))
                    val name = dec.optString("name", dec.optString("title"))
                    val artist = dec.optString("artist", dec.optString("singer"))
                    if (idx == 0) {
                        firstMetaTitle = name
                        firstMetaArtist = artist
                    }
                    itemTitle = if (artist.isNotBlank() && name.isNotBlank()) "$artist - $name" else name
                }
            }
            val prefix = if (isMulti) "${(idx + 1).toString().padStart(2, '0')}. " else ""

            if (idx == 0 && !isMulti) {
                runCatching {
                    val trRes = http.postRaw(
                        "https://aplmate.com/action/track",
                        payloadStr,
                        "application/x-www-form-urlencoded",
                        mapOf("Referer" to "https://aplmate.com/", "Origin" to "https://aplmate.com", "Cookie" to cookies),
                    )
                    val trackHtml = if (trRes.body.trimStart().startsWith("{")) JSONObject(trRes.body).optString("data") else trRes.body
                    val doc3 = Jsoup.parse(trackHtml)
                    doc3.select("a").forEach { a ->
                        val href = a.attr("href")
                        val text = a.text().trim()
                        if (href.isNotBlank() && (href.contains("/dl?token=") || a.hasClass("abutton"))) {
                            if (href.contains("ko-fi.com") || href.contains("premium") ||
                                text.contains("another song", true) || text.contains("cover", true)
                            ) return@forEach
                            val abs = if (href.startsWith("http")) href else "https://aplmate.com$href"
                            items.add(
                                ScraperDownloadItem(
                                    id = "am_${items.size}",
                                    title = itemTitle.ifBlank { "Apple Music Track" },
                                    label = "$prefix${text.ifBlank { "Download MP3" }}",
                                    url = abs,
                                    category = MediaCategory.AUDIO,
                                    extension = "mp3",
                                )
                            )
                        }
                    }
                }
            }
            if (items.isEmpty() || isMulti) {
                items.add(
                    ScraperDownloadItem(
                        id = "am_lazy_$idx",
                        title = itemTitle.ifBlank { "Track ${idx + 1}" },
                        label = "$prefix${itemTitle.ifBlank { "Track ${idx + 1}" }} [MP3]",
                        url = "applemusic_resolve:$payloadStr",
                        category = MediaCategory.AUDIO,
                        extension = "mp3",
                    )
                )
            }
        }
        if (items.isEmpty()) throw IllegalStateException("Aplmate returned no track forms or links")
        val title = doc2.selectFirst(".hover-underline, h3")?.text()?.trim().orEmpty().ifBlank { firstMetaTitle.ifBlank { "Apple Music Content" } }
        val artist = doc2.selectFirst("p")?.text()?.trim().orEmpty().ifBlank { firstMetaArtist }
        val thumb = doc2.selectFirst("img")?.attr("src").orEmpty()
        return ScrapeResult(PlatformId.APPLE_MUSIC, "Aplmate", title, artist, thumb, url, items)
    }

    private fun scrapeAppleMusicDirect(url: String): ScrapeResult {
        val trackId = Regex("""[?&]i=(\d+)""").find(url)?.groupValues?.get(1)
            ?: Regex("""/(?:song|album)/[^/]+/(\d+)""").find(url)?.groupValues?.get(1)
        val items = mutableListOf<ScraperDownloadItem>()
        var mainTitle = "Apple Music"
        var mainArtist = ""
        var mainThumb = ""

        if (!trackId.isNullOrBlank()) {
            runCatching {
                val res = http.get("https://itunes.apple.com/lookup?id=$trackId&entity=song")
                val arr = JSONObject(res.body).optJSONArray("results")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        if (obj.optString("wrapperType") == "collection" && mainTitle == "Apple Music") {
                            mainTitle = obj.optString("collectionName", mainTitle)
                            mainArtist = obj.optString("artistName", mainArtist)
                            mainThumb = obj.optString("artworkUrl100").replace("100x100", "600x600")
                            continue
                        }
                        if (obj.optString("kind") == "song" || obj.optString("wrapperType") == "track") {
                            val tName = obj.optString("trackName")
                            val tArt = obj.optString("artistName")
                            val tThumb = obj.optString("artworkUrl100").replace("100x100", "600x600")
                            if (mainThumb.isBlank()) mainThumb = tThumb
                            if (mainArtist.isBlank()) mainArtist = tArt
                            val full = if (tArt.isNotBlank()) "$tArt - $tName" else tName
                            if (items.isEmpty()) mainTitle = full
                            items.add(
                                ScraperDownloadItem(
                                    id = "am_itunes_$i",
                                    title = full,
                                    label = "MP3 Audio (YouTube Match)",
                                    url = "ytsearch_resolve:${ScraperUtils.urlEncode(full)}",
                                    category = MediaCategory.AUDIO,
                                    extension = "mp3",
                                    thumbnail = tThumb.ifBlank { mainThumb },
                                    author = tArt,
                                    durationMs = obj.optLong("trackTimeMillis", 0L),
                                )
                            )
                        }
                    }
                }
            }
        }

        if (items.isEmpty()) {
            val html = http.get(url).body
            val ogTitle = extractMeta(html, "og:title")
                .replace(Regex("""\s*on Apple Music.*$""", RegexOption.IGNORE_CASE), "")
                .trim()
            val ogThumb = extractMeta(html, "og:image")
            if (ogTitle.isNotBlank()) {
                mainTitle = ogTitle
                mainThumb = ogThumb
                items.add(
                    ScraperDownloadItem(
                        id = "am_og_0",
                        title = ogTitle,
                        label = "MP3 Audio (YouTube Match)",
                        url = "ytsearch_resolve:${ScraperUtils.urlEncode(ogTitle)}",
                        category = MediaCategory.AUDIO,
                        extension = "mp3",
                        thumbnail = ogThumb,
                    )
                )
            }
        }

        if (items.isEmpty()) throw IllegalStateException("Apple Music lookup returned no tracks")
        return ScrapeResult(PlatformId.APPLE_MUSIC, "iTunes + YT Match", mainTitle, mainArtist, mainThumb, url, items)
    }

    fun resolveAppleMusicTrack(payloadStr: String): String {
        val home = http.get("https://aplmate.com/")
        val trRes = http.postRaw(
            "https://aplmate.com/action/track",
            payloadStr,
            "application/x-www-form-urlencoded",
            mapOf("Referer" to "https://aplmate.com/", "Origin" to "https://aplmate.com", "Cookie" to home.cookieString()),
        )
        val trackHtml = if (trRes.body.trimStart().startsWith("{")) JSONObject(trRes.body).optString("data") else trRes.body
        val doc = Jsoup.parse(trackHtml)
        for (a in doc.select("a")) {
            val href = a.attr("href")
            val text = a.text().trim()
            if (href.isNotBlank() && (href.contains("/dl?token=") || a.hasClass("abutton")) &&
                !href.contains("ko-fi.com") && !text.contains("cover", true) && !text.contains("another song", true)
            ) {
                return if (href.startsWith("http")) href else "https://aplmate.com$href"
            }
        }
        throw IllegalStateException("Apple Music track resolution failed")
    }

    // ==================== BANDCAMP ====================
    suspend fun scrapeBandcamp(url: String): ScrapeResult = withContext(Dispatchers.IO) {
        val home = http.get(
            "https://bandcampdownloader.app/",
            mapOf(
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                "User-Agent" to HttpClientFactory.CHROME_DESKTOP_UA,
            ),
        )
        ScraperUtils.ensureOkOrThrow(home, "BandcampDL")
        val cookies = home.cookieString()
        val doc1 = Jsoup.parse(home.body)
        val hidden = doc1.selectFirst("""form[name="submitbcurl"] input[type="hidden"]""")
        val csrfName = hidden?.attr("name").orEmpty()
        val csrfVal = hidden?.attr("value").orEmpty()
        if (csrfName.isBlank()) throw IllegalStateException("Bandcamp CSRF not found")

        val ajaxHeaders = mapOf(
            "Origin" to "https://bandcampdownloader.app",
            "Referer" to "https://bandcampdownloader.app/",
            "X-Requested-With" to "XMLHttpRequest",
            "Accept" to "application/json, text/javascript, */*; q=0.01",
            "User-Agent" to HttpClientFactory.CHROME_DESKTOP_UA,
            "Cookie" to cookies,
        )
        val act = http.postForm(
            "https://bandcampdownloader.app/action",
            mapOf("url" to url, csrfName to csrfVal),
            ajaxHeaders,
        )
        ScraperUtils.ensureOkOrThrow(act, "BandcampDL Action")
        val actJson = JSONObject(act.body)
        if (actJson.optBoolean("error", false) && actJson.optString("html").isBlank()) {
            throw IllegalStateException(actJson.optString("message").ifBlank { "Bandcamp track not found" })
        }
        val doc2 = Jsoup.parse(actJson.optString("html"))
        val forms = doc2.select("""form[name="submitapurl"]""")
        val items = mutableListOf<ScraperDownloadItem>()
        forms.forEachIndexed { idx, form ->
            val dataVal = form.selectFirst("input[name=data]")?.attr("value").orEmpty()
            val baseVal = form.selectFirst("input[name=base]")?.attr("value").orEmpty()
            val tokenVal = form.selectFirst("input[name=token]")?.attr("value").orEmpty()
            val tr = http.postForm(
                "https://bandcampdownloader.app/action/track",
                mapOf("data" to dataVal, "base" to baseVal, "token" to tokenVal),
                ajaxHeaders,
            )
            val dlDoc = Jsoup.parse(JSONObject(tr.body).optString("data"))
            val href = dlDoc.select("a[href^=http]").firstOrNull { a ->
                val h = a.attr("href")
                val txt = a.text()
                !h.contains("ko-fi.com", true) &&
                    !h.contains("premium", true) &&
                    !txt.contains("another", true) &&
                    (!h.contains("bandcampdownloader.app", true) || h.contains("/dl", true))
            }?.attr("href").orEmpty()
            if (href.startsWith("http")) {
                items.add(
                    ScraperDownloadItem(
                        id = "bc_$idx",
                        title = "Bandcamp Track ${idx + 1}",
                        label = "MP3 Download",
                        url = href,
                        category = MediaCategory.AUDIO,
                        extension = "mp3",
                        referer = "https://bandcampdownloader.app/",
                    )
                )
            }
        }
        if (items.isEmpty()) throw IllegalStateException("Bandcamp returned no tracks")
        ScrapeResult(PlatformId.BANDCAMP, "BandcampDL", "Bandcamp Audio", sourceUrl = url, items = items)
    }

    // ==================== BILIBILI ====================
    suspend fun scrapeBilibili(url: String): ScrapeResult = withContext(Dispatchers.IO) {
        var cleanUrl = ScraperUtils.cleanUrl(url)
        if (cleanUrl.contains("b23.tv", true) || cleanUrl.contains("bili.im", true)) {
            runCatching { cleanUrl = http.get(cleanUrl).finalUrl }
        }
        cleanUrl = cleanUrl.substringBefore("?")

        // 1. Bilibili.com BV video API
        val bvMatch = Regex("""(BV[a-zA-Z0-9]{10})""", RegexOption.IGNORE_CASE).find(cleanUrl)
        if (bvMatch != null) {
            val bvid = bvMatch.groupValues[1]
            var title = "Bilibili Video"
            var thumb = ""
            runCatching {
                val pageHtml = http.get("https://www.bilibili.com/video/$bvid").body
                extractMeta(pageHtml, "og:title").replace(Regex("""_哔哩哔哩_bilibili.*$"""), "").trim()
                    .takeIf { it.isNotBlank() }?.let { title = it }
                thumb = extractMeta(pageHtml, "og:image")
            }
            val plRes = http.get(
                "https://api.bilibili.com/x/player/pagelist?bvid=$bvid",
                mapOf("Referer" to "https://www.bilibili.com/video/$bvid"),
            )
            val plJson = JSONObject(plRes.body)
            val items = mutableListOf<ScraperDownloadItem>()
            if (plJson.optInt("code") == 0) {
                val arr = plJson.optJSONArray("data") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val p = arr.getJSONObject(i)
                    val cid = p.optString("cid")
                    val partName = p.optString("part", "Part ${i + 1}")
                    runCatching {
                        val playJson = JSONObject(
                            http.get(
                                "https://api.bilibili.com/x/player/playurl?bvid=$bvid&cid=$cid&qn=80&fnval=0",
                                mapOf("Referer" to "https://www.bilibili.com/video/$bvid"),
                            ).body
                        )
                        val durl = playJson.optJSONObject("data")?.optJSONArray("durl")
                        if (durl != null) {
                            for (j in 0 until durl.length()) {
                                val dUrl = durl.getJSONObject(j).optString("url")
                                if (dUrl.startsWith("http")) {
                                    items.add(
                                        ScraperDownloadItem(
                                            id = "bili_com_${i}_$j",
                                            title = title,
                                            label = if (arr.length() > 1) "$partName (720p HD)" else "720p HD MP4",
                                            url = dUrl,
                                            category = MediaCategory.VIDEO,
                                            extension = "mp4",
                                            thumbnail = thumb,
                                            referer = "https://www.bilibili.com/",
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (items.isNotEmpty()) {
                return@withContext ScrapeResult(PlatformId.BILIBILI, "Bilibili API", title, thumbnail = thumb, sourceUrl = cleanUrl, items = items)
            }
        }

        // 2. Bilibili.tv International / OGV API
        var aid: String? = null
        var epId: String? = null
        var seasonId: String? = null
        Regex("""/video/(\d+)""").find(cleanUrl)?.let { aid = it.groupValues[1] }
        Regex("""/play/(\d+)(?:/(\d+))?""").find(cleanUrl)?.let { m ->
            seasonId = m.groupValues[1]
            if (m.groupValues[2].isNotBlank()) epId = m.groupValues[2]
        }

        val pageHtml = runCatching { http.get(cleanUrl).body }.getOrDefault("")
        var title = extractMeta(pageHtml, "og:title").ifBlank { "Bilibili.tv Video" }
        var thumb = extractMeta(pageHtml, "og:image")
        val items = mutableListOf<ScraperDownloadItem>()

        if (epId == null && seasonId != null) {
            runCatching {
                val epData = JSONObject(
                    http.get("https://api.bilibili.tv/intl/gateway/web/v2/ogv/play/episodes?season_id=$seasonId&platform=web&s_locale=en_US").body
                )
                val sections = epData.optJSONObject("data")?.optJSONArray("sections")
                val firstEp = sections?.optJSONObject(0)?.optJSONArray("episodes")?.optJSONObject(0)
                if (firstEp != null) {
                    epId = firstEp.optString("episode_id").ifBlank { firstEp.optString("ep_id").ifBlank { firstEp.optString("id") } }
                    if (title == "Bilibili.tv Video") firstEp.optString("title_display").takeIf { it.isNotBlank() }?.let { title = it }
                    if (thumb.isBlank()) thumb = firstEp.optString("cover")
                }
            }
        }

        if (epId != null || seasonId != null) {
            runCatching {
                val param = if (!epId.isNullOrBlank()) "ep_id=$epId" else "season_id=$seasonId"
                val v2 = JSONObject(http.get("https://api.bilibili.tv/intl/gateway/v2/ogv/playurl?$param&platform=web&s_locale=en_US").body)
                val vInfo = v2.optJSONObject("data")?.optJSONObject("video_info")
                val streamList = vInfo?.optJSONArray("stream_list")
                if (streamList != null) {
                    for (i in 0 until streamList.length()) {
                        val s = streamList.getJSONObject(i)
                        val pUrl = s.optString("url")
                            .ifBlank { s.optJSONArray("url_list")?.optJSONObject(0)?.optString("url").orEmpty() }
                            .ifBlank { s.optJSONObject("dash_video")?.optString("base_url").orEmpty() }
                            .replace("http://", "https://")
                        if (pUrl.startsWith("https")) {
                            val q = s.optJSONObject("stream_info")?.optString("display_desc").orEmpty()
                                .ifBlank { s.optString("desc_words") }
                                .ifBlank { "HD ${i + 1}" }
                            items.add(ScraperDownloadItem("bili_ogv_$i", title, q, pUrl, MediaCategory.VIDEO, "mp4", thumbnail = thumb, referer = "https://www.bilibili.tv/"))
                        }
                    }
                }
            }
        }

        if (items.isEmpty() && !aid.isNullOrBlank()) {
            runCatching {
                val playJson = JSONObject(http.get("https://api.bilibili.tv/intl/gateway/web/v2/playurl?aid=$aid&platform=web&s_locale=en_US").body)
                val playUrlObj = playJson.optJSONObject("data")?.optJSONObject("playurl")
                val vList = playUrlObj?.optJSONArray("video") ?: playUrlObj?.optJSONArray("durl")
                if (vList != null) {
                    for (i in 0 until vList.length()) {
                        val v = vList.getJSONObject(i)
                        val vUrl = v.optString("url").ifBlank { v.optString("base_url") }.replace("http://", "https://")
                        if (vUrl.startsWith("https")) {
                            items.add(ScraperDownloadItem("bili_tv_$i", title, "HD ${v.optString("quality")}".trim(), vUrl, MediaCategory.VIDEO, "mp4", thumbnail = thumb, referer = "https://www.bilibili.tv/"))
                        }
                    }
                }
            }
        }

        if (items.isEmpty()) throw IllegalStateException("Bilibili API returned no playable streams (login/DRM may apply)")
        ScrapeResult(PlatformId.BILIBILI, "Bilibili API", title, thumbnail = thumb, sourceUrl = cleanUrl, items = items)
    }

    // ==================== DOUYIN ====================
    suspend fun scrapeDouyin(url: String): ScrapeResult = withContext(Dispatchers.IO) {
        val clean = ScraperUtils.cleanUrl(url)
        val mobileUa = "Mozilla/5.0 (iPhone; CPU iPhone OS 16_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.5 Mobile/15E148 Safari/604.1"
        var itemId = Regex("""(?:video|note|slides)/(\d{15,21})""").find(clean)?.groupValues?.get(1)
        var htmlBody = ""

        if (itemId == null) {
            val res = http.get(clean, mapOf("User-Agent" to mobileUa, "Accept-Language" to "zh-CN,zh;q=0.9,en;q=0.8"))
            htmlBody = res.body
            itemId = Regex("""(?:video|note|slides)/(\d{15,21})""").find(res.finalUrl)?.groupValues?.get(1)
                ?: Regex("""(?:item_ids=|aweme_id["']?\s*[:=]\s*["']?)(\d{15,21})""").find(htmlBody)?.groupValues?.get(1)
        }

        var itemObj: JSONObject? = null
        if (!itemId.isNullOrBlank()) {
            runCatching {
                val api = JSONObject(
                    http.get(
                        "https://www.iesdouyin.com/web/api/v2/aweme/iteminfo/?item_ids=$itemId",
                        mapOf("User-Agent" to mobileUa),
                    ).body
                )
                itemObj = api.optJSONArray("item_list")?.optJSONObject(0)
            }
        }

        if (itemObj == null) {
            val candidateBodies = mutableListOf<String>()
            if (htmlBody.isNotBlank()) candidateBodies.add(htmlBody)
            if (!itemId.isNullOrBlank()) {
                val sharePath = if (clean.contains("/note/")) "note" else "video"
                runCatching {
                    val shareRes = http.get(
                        "https://www.iesdouyin.com/share/$sharePath/$itemId/",
                        mapOf("User-Agent" to mobileUa, "Accept-Language" to "zh-CN,zh;q=0.9,en;q=0.8"),
                    )
                    if (shareRes.body.isNotBlank()) candidateBodies.add(shareRes.body)
                }
            }
            if (candidateBodies.isEmpty()) {
                candidateBodies.add(
                    http.get(clean, mapOf("User-Agent" to mobileUa, "Accept-Language" to "zh-CN,zh;q=0.9,en;q=0.8")).body
                )
            }
            for (bodyText in candidateBodies) {
                for (marker in listOf("window._ROUTER_DATA =", "window._SSR_DATA =", "window._RENDER_DATA =")) {
                    val idx = bodyText.indexOf(marker)
                    if (idx == -1) continue
                    val jsonText = extractBalancedJson(bodyText, idx + marker.length) ?: continue
                    runCatching {
                        val root = JSONObject(jsonText)
                        val loader = root.optJSONObject("loaderData")
                        if (loader != null) {
                            val keys = loader.keys()
                            while (keys.hasNext()) {
                                val sub = loader.optJSONObject(keys.next())
                                val candidate = sub?.optJSONObject("videoInfoRes")?.optJSONArray("item_list")?.optJSONObject(0)
                                if (candidate != null) {
                                    itemObj = candidate
                                    break
                                }
                            }
                        }
                        if (itemObj == null) {
                            itemObj = root.optJSONObject("aweme")?.optJSONObject("detail")
                                ?: root.optJSONArray("item_list")?.optJSONObject(0)
                                ?: root.optJSONObject("aweme_detail")
                        }
                    }
                    if (itemObj != null) break
                }
                if (itemObj != null) break
            }
        }

        val item = itemObj ?: throw IllegalStateException("Could not extract Douyin media data")
        val title = item.optString("desc").ifBlank { "Douyin Content" }
        val author = item.optJSONObject("author")?.optString("nickname").orEmpty().ifBlank { "Douyin User" }
        val thumb = item.optJSONObject("video")?.optJSONObject("cover")?.optJSONArray("url_list")?.optString(0).orEmpty()
            .ifBlank { item.optJSONArray("images")?.optJSONObject(0)?.optJSONArray("url_list")?.optString(0).orEmpty() }
            .replace("http://", "https://")
        val items = mutableListOf<ScraperDownloadItem>()

        val images = item.optJSONArray("images")
        if (images != null && images.length() > 0) {
            for (i in 0 until images.length()) {
                val imgObj = images.getJSONObject(i)
                val imgUrl = (imgObj.optJSONArray("url_list")?.optString(0)
                    ?: imgObj.optJSONArray("download_url_list")?.optString(0)).orEmpty().replace("http://", "https://")
                if (imgUrl.startsWith("http")) {
                    items.add(ScraperDownloadItem("dy_img_$i", "$title (${i + 1})", "Photo ${i + 1}", imgUrl, MediaCategory.IMAGE, "jpg", thumbnail = imgUrl, author = author))
                }
            }
        } else {
            val rawPlay = item.optJSONObject("video")?.optJSONObject("play_addr")?.optJSONArray("url_list")?.optString(0).orEmpty()
                .ifBlank { item.optJSONObject("video")?.optJSONObject("download_addr")?.optJSONArray("url_list")?.optString(0).orEmpty() }
                .replace("http://", "https://")
                .replace("playwm", "play")
            val vidId = Regex("""video_id=([^&]+)""").find(rawPlay)?.groupValues?.get(1)
            val noWm = if (!vidId.isNullOrBlank()) "https://aweme.snssdk.com/aweme/v1/play/?video_id=$vidId" else rawPlay
            if (noWm.startsWith("http")) {
                items.add(ScraperDownloadItem("dy_nowm", title, "Video (No Watermark)", noWm, MediaCategory.VIDEO, "mp4", thumbnail = thumb, author = author))
            }
            if (rawPlay.startsWith("http") && rawPlay != noWm) {
                items.add(ScraperDownloadItem("dy_wm", title, "Video (Mirror)", rawPlay, MediaCategory.VIDEO, "mp4", thumbnail = thumb, author = author))
            }
        }
        if (items.isEmpty()) throw IllegalStateException("Douyin returned no video/photo links")
        ScrapeResult(PlatformId.DOUYIN, "Douyin API / SSR", title, author, thumb, clean, items)
    }

    // ==================== REDNOTE / XHS ====================
    private fun isUrlReachable(url: String): Boolean = runCatching {
        val req = okhttp3.Request.Builder()
            .url(url)
            .head()
            .header("User-Agent", http.effectiveUserAgent())
            .build()
        http.scraperClient().newCall(req).execute().use { it.isSuccessful }
    }.getOrDefault(false)

    suspend fun scrapeRedNote(url: String): ScrapeResult = withContext(Dispatchers.IO) {
        val clean = ScraperUtils.cleanUrl(url)
        val resDesktop = http.get(
            clean,
            mapOf(
                "User-Agent" to HttpClientFactory.CHROME_DESKTOP_UA,
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                "Accept-Language" to "zh-CN,zh;q=0.9,en;q=0.8",
                "Referer" to "https://www.xiaohongshu.com/",
            ),
        )
        var html = resDesktop.body
        val finalClean = resDesktop.finalUrl.ifBlank { clean }

        fun cleanXhsUrl(raw: String): String {
            var u = raw.replace("\\u002F", "/")
                .replace("\\u0026", "&")
                .replace("\\/", "/")
                .trim()
            if (u.startsWith("//")) u = "https:$u"
            return u.replace("http://", "https://")
        }

        fun extractVideosFromHtml(srcHtml: String, title: String, thumb: String, author: String): List<ScraperDownloadItem> {
            val vids = mutableListOf<ScraperDownloadItem>()
            val seen = HashSet<String>()
            Regex(""""originVideoKey"\s*:\s*"([^"]+)"""").findAll(srcHtml).forEach { m ->
                val key = m.groupValues[1].replace("\\u002F", "/").replace("\\/", "/").trim('/')
                if (key.isNotBlank()) {
                    val u = "https://sns-video-bd.xhscdn.com/$key"
                    if (seen.add(u)) {
                        vids.add(ScraperDownloadItem("xhs_v_orig_${vids.size}", title, "Original HD Video (No Watermark)", u, MediaCategory.VIDEO, "mp4", thumbnail = thumb, author = author))
                    }
                }
            }
            Regex(""""masterUrl"\s*:\s*"([^"]+)"""").findAll(srcHtml).forEach { m ->
                val u = cleanXhsUrl(m.groupValues[1])
                if (u.startsWith("http") && (u.contains("xhscdn") || u.contains(".mp4")) && seen.add(u)) {
                    vids.add(ScraperDownloadItem("xhs_v_m_${vids.size}", title, "HD Video", u, MediaCategory.VIDEO, "mp4", thumbnail = thumb, author = author))
                }
            }
            Regex("""https?:\\?/\\?/sns-video-[^"'\s<>]+\.mp4[^"'\s<>]*""", RegexOption.IGNORE_CASE).findAll(srcHtml).forEach { m ->
                val u = cleanXhsUrl(m.value)
                if (u.startsWith("http") && seen.add(u)) {
                    vids.add(ScraperDownloadItem("xhs_v_rx_${vids.size}", title, "HD Video", u, MediaCategory.VIDEO, "mp4", thumbnail = thumb, author = author))
                }
            }
            return vids
        }

        if (!html.contains("originVideoKey") && !html.contains("masterUrl") && !html.contains("noteDetailMap")) {
            runCatching {
                val resMob = http.get(
                    finalClean,
                    mapOf(
                        "User-Agent" to HttpClientFactory.SAFARI_MOBILE_UA,
                        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                        "Accept-Language" to "zh-CN,zh;q=0.9,en;q=0.8",
                        "Referer" to "https://www.xiaohongshu.com/",
                    ),
                )
                if (resMob.body.length > html.length || resMob.body.contains("originVideoKey") || resMob.body.contains("noteDetailMap")) {
                    html = resMob.body
                }
            }
        }

        var rawStateJson: String? = null
        for (marker in listOf("window.__INITIAL_STATE__", "window.__INITIAL_DATA__", "window.__PRELOADED_STATE__")) {
            val idx = html.indexOf(marker)
            if (idx != -1) {
                rawStateJson = extractBalancedJson(html, idx + marker.length)
                if (rawStateJson != null) break
            }
        }
        if (rawStateJson != null) {
            runCatching {
                val rawJson = rawStateJson.replace(Regex(""":\s*undefined"""), ":null")
                val state = JSONObject(rawJson)
                var note: JSONObject? = null
                val map = state.optJSONObject("note")?.optJSONObject("noteDetailMap")
                if (map != null) {
                    val keys = map.keys()
                    while (keys.hasNext()) {
                        val entry = map.optJSONObject(keys.next())
                        val nd = entry?.optJSONObject("note") ?: entry
                        if (nd != null && (nd.has("imageList") || nd.has("video") || nd.has("title"))) {
                            note = nd
                            break
                        }
                    }
                }
                if (note == null) {
                    note = state.optJSONObject("noteData")?.optJSONObject("note")
                        ?: state.optJSONObject("note")?.optJSONObject("firstNote")
                }
                if (note != null) {
                    val title = note.optString("title").ifBlank { note.optString("desc").ifBlank { "RedNote Post" } }
                    val author = note.optJSONObject("user")?.optString("nickname").orEmpty().ifBlank { "RedNote Creator" }
                    val items = mutableListOf<ScraperDownloadItem>()
                    var thumb = ""

                    val imgArr = note.optJSONArray("imageList")
                    if (imgArr != null && imgArr.length() > 0) {
                        for (i in 0 until imgArr.length()) {
                            val imgObj = imgArr.getJSONObject(i)
                            var imgUrl = cleanXhsUrl(
                                imgObj.optString("urlOriginal").ifBlank { imgObj.optString("urlDefault") }.ifBlank { imgObj.optString("url") },
                            )
                            if (thumb.isBlank()) thumb = imgUrl
                            if (imgUrl.startsWith("http")) {
                                items.add(ScraperDownloadItem("xhs_img_$i", "$title (${i + 1})", "Photo ${i + 1}", imgUrl, MediaCategory.IMAGE, "jpg", thumbnail = imgUrl, author = author))
                            }
                        }
                    }

                    val videoObj = note.optJSONObject("video")
                    val originVideoKey = videoObj?.optJSONObject("consumer")?.optString("originVideoKey").orEmpty()
                        .ifBlank { videoObj?.optString("originVideoKey").orEmpty() }
                    var videoAddedCount = 0
                    if (originVideoKey.isNotBlank()) {
                        val originUrl = "https://sns-video-bd.xhscdn.com/${originVideoKey.trim('/')}"
                        items.add(0, ScraperDownloadItem("xhs_vid_orig", title, "Original HD Video (No Watermark)", originUrl, MediaCategory.VIDEO, "mp4", thumbnail = thumb, author = author))
                        videoAddedCount++
                    }

                    val streamObj = videoObj?.optJSONObject("media")?.optJSONObject("stream")
                        ?: videoObj?.optJSONObject("mediaV2")?.optJSONObject("stream")
                    if (streamObj != null) {
                        var vUrl = ""
                        for (codec in listOf("h264", "h265", "h266", "av1")) {
                            val arr = streamObj.optJSONArray(codec)
                            if (arr != null && arr.length() > 0) {
                                val st = arr.getJSONObject(0)
                                vUrl = st.optString("masterUrl")
                                    .ifBlank { st.optJSONArray("backupUrls")?.optString(0).orEmpty() }
                                    .ifBlank { st.optString("url") }
                                if (vUrl.isNotBlank()) break
                            }
                        }
                        vUrl = cleanXhsUrl(vUrl)
                        if (vUrl.startsWith("http")) {
                            items.add(videoAddedCount, ScraperDownloadItem("xhs_vid", title, "HD Video", vUrl, MediaCategory.VIDEO, "mp4", thumbnail = thumb, author = author))
                            videoAddedCount++
                        }
                    }
                    if (videoAddedCount == 0) {
                        val fallbackVids = extractVideosFromHtml(html, title, thumb, author)
                        if (fallbackVids.isNotEmpty()) {
                            items.addAll(0, fallbackVids)
                        }
                    }
                    if (items.isNotEmpty()) {
                        return@withContext ScrapeResult(PlatformId.REDNOTE, "RedNote SSR", title, author, thumb, clean, items)
                    }
                }
            }
        }

        // Regex + OpenGraph fallback (captures originVideoKey / CDN mp4 even if INITIAL_STATE JSON failed to parse)
        val title = extractMeta(html, "og:title").replace(Regex("""\s*-\s*(?:小红书|RedNote).*$"""), "").ifBlank { "RedNote Post" }
        val ogVid = cleanXhsUrl(extractMeta(html, "og:video:url").ifBlank { extractMeta(html, "og:video") })
        val ogImg = cleanXhsUrl(extractMeta(html, "og:image"))
        val items = mutableListOf<ScraperDownloadItem>()
        items.addAll(extractVideosFromHtml(html, title, ogImg, ""))
        if (ogVid.startsWith("http") && items.none { it.url == ogVid }) {
            items.add(ScraperDownloadItem("xhs_ogv", title, "HD Video", ogVid, MediaCategory.VIDEO, "mp4", thumbnail = ogImg))
        }
        if (ogImg.startsWith("http")) items.add(ScraperDownloadItem("xhs_ogi", title, "Image", ogImg, MediaCategory.IMAGE, "jpg", thumbnail = ogImg))
        if (items.isEmpty()) throw IllegalStateException("RedNote SSR/OG returned no media (link may require app)")
        ScrapeResult(PlatformId.REDNOTE, "RedNote SSR", title, thumbnail = ogImg, sourceUrl = clean, items = items)
    }

    // ==================== SHOPEE VIDEO ====================
    suspend fun scrapeShopee(url: String, server: String = "direct"): ScrapeResult = withContext(Dispatchers.IO) {
        val clean = ScraperUtils.cleanUrl(url)
        val order = listOf(server) + listOf("direct", "svxtract").filterNot { it == server }
        var lastError: Throwable? = null

        for (srv in order) {
            val res = runCatching {
                when (srv) {
                    "svxtract" -> scrapeShopeeSvXtract(clean)
                    else -> scrapeShopeeDirect(clean)
                }
            }
            if (res.isSuccess) return@withContext res.getOrThrow()
            lastError = res.exceptionOrNull()
        }
        throw IllegalStateException(lastError?.message ?: "All Shopee servers failed")
    }

    private fun scrapeShopeeDirect(url: String): ScrapeResult {
        var targetUrl = url
        if (targetUrl.contains("shp.ee", ignoreCase = true) ||
            targetUrl.contains("s.shopee.", ignoreCase = true) ||
            targetUrl.contains("/universal-link", ignoreCase = true)
        ) {
            runCatching {
                val redirRes = http.get(
                    targetUrl,
                    mapOf("Accept-Language" to "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7"),
                )
                if (redirRes.finalUrl.startsWith("http") &&
                    !redirRes.finalUrl.contains("shp.ee", ignoreCase = true)
                ) {
                    targetUrl = redirRes.finalUrl
                } else {
                    val canonical = Regex("""<link\s+rel=["']canonical["']\s+href=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                        .find(redirRes.body)?.groupValues?.get(1).orEmpty()
                    val ogUrl = extractMeta(redirRes.body, "og:url")
                    val redirParam = Regex("""redir=([^"'\s&]+)""", RegexOption.IGNORE_CASE)
                        .find(redirRes.body)?.groupValues?.get(1).orEmpty()
                    when {
                        canonical.startsWith("http") && !canonical.contains("icon") -> targetUrl = canonical
                        ogUrl.startsWith("http") -> targetUrl = ogUrl
                        redirParam.isNotBlank() -> targetUrl = ScraperUtils.urlDecode(redirParam).replace("&amp;", "&")
                    }
                }
            }
        }
        if (targetUrl.contains("redir=")) {
            Regex("""[?&]redir=([^&]+)""").find(targetUrl)?.groupValues?.get(1)?.let { enc ->
                val decoded = ScraperUtils.urlDecode(enc)
                if (decoded.startsWith("http")) targetUrl = decoded
            }
        }

        val res = http.get(
            targetUrl,
            mapOf("Accept-Language" to "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7"),
        )
        val html = res.body
        val seen = HashSet<String>()
        val items = mutableListOf<ScraperDownloadItem>()
        val wmSuffixRegex = Regex("""\.\d+(?:\.\d+)?\.mp4$""", RegexOption.IGNORE_CASE)

        val nextMatch = Regex("""<script\s+id="__NEXT_DATA__"[^>]*>([\s\S]*?)</script>""").find(html)
        if (nextMatch != null) {
            runCatching {
                val root = JSONObject(nextMatch.groupValues[1])
                val pp = root.optJSONObject("props")?.optJSONObject("pageProps")
                val mediaInfo = pp?.optJSONObject("mediaInfo")
                val video = mediaInfo?.optJSONObject("video") ?: pp?.optJSONObject("video")
                val userInfo = mediaInfo?.optJSONObject("userInfo") ?: video?.optJSONObject("userInfo")
                if (video != null) {
                    val user = userInfo?.optString("videoUserName").orEmpty()
                        .ifBlank { userInfo?.optString("username").orEmpty() }
                        .ifBlank { "Shopee Creator" }
                    val caption = video.optString("caption").ifBlank { "$user on Shopee Video" }
                    val thumb = video.optString("coverUrl")
                        .ifBlank { video.optString("watermarkCoverUrl") }
                        .ifBlank { video.optString("watermarkShareImageUrl") }
                        .substringBefore("@imgwm_")
                    val watermarkUrl = video.optString("watermarkVideoUrl")
                        .ifBlank { video.optString("watermarkUrl") }
                    val rawVideoUrl = video.optString("videoUrl")
                        .ifBlank { video.optString("noWatermarkUrl") }
                        .ifBlank { video.optString("playUrl") }

                    var noWmDefault = if (rawVideoUrl.startsWith("http") && rawVideoUrl != watermarkUrl) rawVideoUrl else ""
                    var noWmMaster = ""
                    val baseForStrip = watermarkUrl.ifBlank { rawVideoUrl }
                    if (baseForStrip.startsWith("http") && wmSuffixRegex.containsMatchIn(baseForStrip)) {
                        if (noWmDefault.isBlank()) {
                            noWmDefault = baseForStrip.replace(wmSuffixRegex, ".default.mp4")
                        }
                        noWmMaster = baseForStrip.replace(wmSuffixRegex, ".mp4")
                    }

                    if (noWmMaster.startsWith("http") && isUrlReachable(noWmMaster) && seen.add(noWmMaster)) {
                        items.add(ScraperDownloadItem("shp_master", caption, "Original Master HD (No Watermark)", noWmMaster, MediaCategory.VIDEO, "mp4", thumbnail = thumb, author = user))
                    }
                    if (noWmDefault.startsWith("http") && seen.add(noWmDefault)) {
                        items.add(ScraperDownloadItem("shp_nowm", caption, "HD Video (No Watermark)", noWmDefault, MediaCategory.VIDEO, "mp4", thumbnail = thumb, author = user))
                    }
                    if (watermarkUrl.startsWith("http") && seen.add(watermarkUrl)) {
                        items.add(ScraperDownloadItem("shp_wm", caption, "Video (With Watermark)", watermarkUrl, MediaCategory.VIDEO, "mp4", thumbnail = thumb, author = user))
                    }
                    if (items.isNotEmpty()) {
                        return ScrapeResult(PlatformId.SHOPEE, "Shopee Direct", caption, user, thumb, url, items)
                    }
                }
            }
        }

        val title = extractMeta(html, "og:title").ifBlank { "Shopee Video" }
        val thumb = extractMeta(html, "og:image").substringBefore("@imgwm_")
        Regex("""https?:\\?/\\?/[^\s"'<>]+\.vod\.susercontent\.com\\?/[^\s"'<>]+\.mp4""", RegexOption.IGNORE_CASE).findAll(html).forEach { m ->
            val vUrl = m.value.replace("\\/", "/")
            if (wmSuffixRegex.containsMatchIn(vUrl)) {
                val master = vUrl.replace(wmSuffixRegex, ".mp4")
                if (isUrlReachable(master) && seen.add(master)) {
                    items.add(ScraperDownloadItem("shp_m_${items.size}", title, "Original Master HD (No Watermark)", master, MediaCategory.VIDEO, "mp4", thumbnail = thumb))
                }
                val defaultNoWm = vUrl.replace(wmSuffixRegex, ".default.mp4")
                if (seen.add(defaultNoWm)) {
                    items.add(ScraperDownloadItem("shp_d_${items.size}", title, "HD Video (No Watermark)", defaultNoWm, MediaCategory.VIDEO, "mp4", thumbnail = thumb))
                }
            }
            if (seen.add(vUrl)) {
                val lbl = if (items.isEmpty()) "HD Video (Original)" else "Video (With Watermark)"
                items.add(ScraperDownloadItem("shp_o_${items.size}", title, lbl, vUrl, MediaCategory.VIDEO, "mp4", thumbnail = thumb))
            }
        }
        val ogVid = extractMeta(html, "og:video:url").ifBlank { extractMeta(html, "og:video") }
        if (ogVid.startsWith("http") && seen.add(ogVid)) {
            items.add(ScraperDownloadItem("shp_og", title, "Shopee Video", ogVid, MediaCategory.VIDEO, "mp4", thumbnail = thumb))
        }
        if (items.isEmpty()) throw IllegalStateException("Shopee Direct returned no video streams")
        return ScrapeResult(PlatformId.SHOPEE, "Shopee Direct", title, thumbnail = thumb, sourceUrl = url, items = items)
    }

    private fun scrapeShopeeSvXtract(url: String): ScrapeResult {
        val home = http.get("https://svxtract.com/")
        val html = home.body
        val csrf = Regex("""encodeURIComponent\(["']([a-f0-9]{32,64})["']\)""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
            ?: Regex("""csrf_token["']?\s*[:=]\s*["']?([^&"'\s]+)""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
            ?: throw IllegalStateException("SVXtract CSRF token not found")
        val apiRes = http.get(
            "https://svxtract.com/apiv3.php?url=${ScraperUtils.urlEncode(url)}&csrf_token=${ScraperUtils.urlEncode(csrf)}",
            mapOf("Referer" to "https://svxtract.com/", "Origin" to "https://svxtract.com", "Cookie" to home.cookieString(), "X-Requested-With" to "XMLHttpRequest"),
        )
        val json = JSONObject(apiRes.body)
        val stream = json.optJSONObject("stream") ?: throw IllegalStateException("SVXtract returned no stream")
        val user = json.optString("username", "Shopee Creator")
        val thumb = json.optString("preview")
        val items = mutableListOf<ScraperDownloadItem>()
        val keys = stream.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val entry = stream.opt(k)
            val sUrl = when (entry) {
                is JSONObject -> entry.optString("stream")
                is String -> entry
                else -> ""
            }
            if (sUrl.startsWith("http")) {
                items.add(ScraperDownloadItem("shp_sv_$k", "$user on Shopee Video", "$k (No Watermark)", sUrl, MediaCategory.VIDEO, "mp4", thumbnail = thumb, author = user, referer = "https://svxtract.com/"))
            }
        }
        if (items.isEmpty()) throw IllegalStateException("SVXtract returned empty streams")
        return ScrapeResult(PlatformId.SHOPEE, "SVXtract", "$user on Shopee Video", user, thumb, url, items)
    }

    private fun extractMeta(html: String, prop: String): String {
        val escaped = Regex.escape(prop)
        val m1 = Regex("""<meta[^>]+(?:property|name)=["']$escaped["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(html)
        if (m1 != null) return org.jsoup.parser.Parser.unescapeEntities(m1.groupValues[1], false)
        val m2 = Regex("""<meta[^>]+content=["']([^"']+)["'][^>]+(?:property|name)=["']$escaped["']""", RegexOption.IGNORE_CASE).find(html)
        return if (m2 != null) org.jsoup.parser.Parser.unescapeEntities(m2.groupValues[1], false) else ""
    }

    private fun extractBalancedJson(text: String, startFrom: Int): String? {
        val idxOpen = text.indexOf('{', startFrom)
        if (idxOpen == -1) return null
        var depth = 0
        var inString = false
        var escape = false
        for (i in idxOpen until text.length) {
            val ch = text[i]
            if (escape) {
                escape = false
                continue
            }
            if (ch == '\\' && inString) {
                escape = true
                continue
            }
            if (ch == '"') {
                inString = !inString
                continue
            }
            if (!inString) {
                if (ch == '{') depth++
                else if (ch == '}') {
                    depth--
                    if (depth == 0) return text.substring(idxOpen, i + 1)
                }
            }
        }
        return null
    }
}
