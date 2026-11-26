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
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class TikTokScraper(private val http: HttpClientFactory) {

    suspend fun scrape(url: String, server: String): ScrapeResult = withContext(Dispatchers.IO) {
        val cleanUrl = ScraperUtils.cleanUrl(url)
        val order = when (server) {
            "tiktokio" -> listOf("tiktokio", "tikwm", "snaptik", "ssstik", "tikdownloader")
            "ssstik" -> listOf("ssstik", "tikwm", "snaptik", "tiktokio", "tikdownloader")
            "tikdownloader" -> listOf("tikdownloader", "tikwm", "snaptik", "tiktokio", "ssstik")
            else -> if (cleanUrl.contains("/photo/", ignoreCase = true)) {
                listOf("tikwm", "snaptik", "tiktokio", "ssstik", "tikdownloader")
            } else {
                listOf("tikwm", "snaptik", "tiktokio", "ssstik", "tikdownloader")
            }
        }

        var lastError: Throwable? = null
        for (srv in order) {
            val res = runCatching {
                when (srv) {
                    "tikwm" -> scrapeTikWm(cleanUrl)
                    "snaptik" -> scrapeSnapTik(cleanUrl)
                    "tiktokio" -> scrapeTikTokIO(cleanUrl)
                    "ssstik" -> scrapeSssTik(cleanUrl)
                    else -> scrapeTikDownloader(cleanUrl)
                }
            }
            if (res.isSuccess && res.getOrThrow().items.isNotEmpty()) {
                return@withContext res.getOrThrow()
            }
            lastError = res.exceptionOrNull()
        }
        throw IllegalStateException(lastError?.message ?: "All TikTok servers failed")
    }

    private fun scrapeTikWm(url: String): ScrapeResult {
        val apiUrl = "https://www.tikwm.com/api/?url=${ScraperUtils.urlEncode(url)}"
        val res = http.get(
            apiUrl,
            mapOf(
                "Accept" to "application/json",
                "User-Agent" to HttpClientFactory.CHROME_DESKTOP_UA,
            ),
        )
        ScraperUtils.ensureOkOrThrow(res, "TikWM")
        val root = JSONObject(res.body)
        if (root.optInt("code", -1) != 0) {
            throw IllegalStateException(root.optString("msg").ifBlank { "TikWM extraction failed" })
        }
        val data = root.optJSONObject("data") ?: throw IllegalStateException("TikWM returned empty data")
        val title = data.optString("title").trim().ifBlank { "TikTok Media" }
        val author = data.optJSONObject("author")?.optString("nickname").orEmpty()
            .ifBlank { data.optJSONObject("author")?.optString("unique_id").orEmpty() }
        val thumb = data.optString("cover").ifBlank { data.optString("origin_cover") }
        val items = mutableListOf<ScraperDownloadItem>()

        val images = data.optJSONArray("images")
        if (images != null && images.length() > 0) {
            for (i in 0 until images.length()) {
                val imgUrl = images.optString(i).trim()
                if (!imgUrl.startsWith("http", ignoreCase = true)) continue
                val path = imgUrl.substringBefore('?').lowercase()
                val ext = when {
                    path.endsWith(".png") -> "png"
                    path.endsWith(".webp") -> "webp"
                    else -> "jpg"
                }
                items.add(
                    ScraperDownloadItem(
                        id = "tikwm_img_$i",
                        title = "$title (${i + 1})",
                        label = "Photo ${i + 1} (${ext.uppercase()})",
                        url = imgUrl,
                        category = MediaCategory.IMAGE,
                        extension = ext,
                        thumbnail = imgUrl,
                        author = author,
                        referer = "https://www.tiktok.com/",
                    )
                )
            }
        }

        val hdPlay = data.optString("hdplay").trim()
        val play = data.optString("play").trim()
        val music = data.optString("music").trim()
        if (items.isEmpty()) {
            if (hdPlay.startsWith("http", ignoreCase = true)) {
                items.add(
                    ScraperDownloadItem(
                        id = "tikwm_hd",
                        title = title,
                        label = "HD Video (No Watermark)",
                        url = hdPlay,
                        category = MediaCategory.VIDEO,
                        extension = "mp4",
                        thumbnail = thumb,
                        author = author,
                        referer = "https://www.tiktok.com/",
                    )
                )
            }
            if (play.startsWith("http", ignoreCase = true) && play != hdPlay) {
                items.add(
                    ScraperDownloadItem(
                        id = "tikwm_sd",
                        title = title,
                        label = "MP4 (No Watermark)",
                        url = play,
                        category = MediaCategory.VIDEO,
                        extension = "mp4",
                        thumbnail = thumb,
                        author = author,
                        referer = "https://www.tiktok.com/",
                    )
                )
            }
        }
        if (music.startsWith("http", ignoreCase = true)) {
            items.add(
                ScraperDownloadItem(
                    id = "tikwm_mp3",
                    title = "$title Audio",
                    label = "MP3 Sound",
                    url = music,
                    category = MediaCategory.AUDIO,
                    extension = "mp3",
                    thumbnail = thumb,
                    author = author,
                    referer = "https://www.tiktok.com/",
                )
            )
        }
        if (items.isEmpty()) throw IllegalStateException("TikWM returned no downloadable links")

        return ScrapeResult(
            platform = PlatformId.TIKTOK,
            serverUsed = "TikWM",
            title = title,
            author = author,
            thumbnail = thumb.ifBlank { items.first().thumbnail },
            sourceUrl = url,
            items = items,
        )
    }

    private fun scrapeSnapTik(url: String): ScrapeResult {
        val headers = mapOf(
            "Origin" to "https://snaptik.app",
            "Referer" to "https://snaptik.app/",
            "X-Requested-With" to "XMLHttpRequest",
        )
        val tokenRes = http.postRaw("https://snaptik.app/api/token", "{}", "application/json", headers)
        ScraperUtils.ensureOkOrThrow(tokenRes, "SnapTik Token")
        val tokenJson = JSONObject(tokenRes.body)
        val id = tokenJson.getString("id")
        val payloadB64 = tokenJson.getString("p")

        // Decrypt AES-256-CBC: key = SHA256("sn4pt1k_v3r1fy2026:" + id), iv = first 16 bytes of payload
        val keyBytes = ScraperUtils.sha256Bytes("sn4pt1k_v3r1fy2026:$id")
        val decodedPayload = android.util.Base64.decode(payloadB64, android.util.Base64.DEFAULT)
        if (decodedPayload.size <= 16) throw IllegalStateException("Invalid SnapTik encrypted payload")
        val ivBytes = decodedPayload.copyOfRange(0, 16)
        val encrypted = decodedPayload.copyOfRange(16, decodedPayload.size)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(keyBytes, "AES"),
            IvParameterSpec(ivBytes),
        )
        val challengeStr = String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
        val challenge = JSONObject(challengeStr)

        val (resVal, eVal, hVal) = solveSnapTikChallenge(challenge)
        val xVerify = "$id:$resVal:$eVal:$hVal"

        val extractHeaders = headers + mapOf("X-Verify" to xVerify)
        val extractRes = http.get(
            "https://snaptik.app/api/extract?url=${ScraperUtils.urlEncode(url)}",
            extractHeaders,
        )
        ScraperUtils.ensureOkOrThrow(extractRes, "SnapTik Extract")
        val root = JSONObject(extractRes.body)
        if (root.has("success") && !root.optBoolean("success", false)) {
            throw IllegalStateException(root.optString("message", "SnapTik extraction failed"))
        }
        val json = root.optJSONObject("data") ?: root

        val title = json.optString("title").ifBlank { "TikTok Video" }
        val authorObj = json.optJSONObject("author")
        val author = authorObj?.optString("nickname", authorObj.optString("name"))
            ?.ifBlank { json.optString("author", json.optString("nickname", "")) }
            ?: json.optString("author", json.optString("nickname", ""))
        val thumb = json.optString("cover", json.optString("thumbnail", ""))
        val items = mutableListOf<ScraperDownloadItem>()

        val photos = json.optJSONArray("photoUrls")
            ?: json.optJSONArray("photos")
            ?: json.optJSONArray("images")
            ?: json.optJSONArray("slides")

        if (photos != null && photos.length() > 0) {
            for (i in 0 until photos.length()) {
                val elem = photos.opt(i)
                val pUrl = when (elem) {
                    is String -> elem
                    is JSONObject -> elem.optString("url")
                        .ifBlank { elem.optString("src") }
                        .ifBlank { elem.optString("link") }
                        .ifBlank { elem.optString("downloadUrl") }
                    else -> ""
                }
                if (pUrl.startsWith("http")) {
                    val isPng = pUrl.contains(".png", ignoreCase = true) || pUrl.contains("format=png", ignoreCase = true)
                    items.add(
                        ScraperDownloadItem(
                            id = "snaptik_img_$i",
                            title = "$title (${i + 1})",
                            label = "Photo ${i + 1}",
                            url = pUrl,
                            category = MediaCategory.IMAGE,
                            extension = if (isPng) "png" else "jpg",
                            thumbnail = thumb.ifBlank { pUrl },
                            author = author,
                            referer = "https://snaptik.app/",
                        )
                    )
                }
            }
        }

        val rawHdUrl = json.optString("hdDownloadUrl")
        val hdUrl = when {
            rawHdUrl.startsWith("/api/hd") || rawHdUrl.contains("snaptik.app/api/hd") -> ""
            rawHdUrl.startsWith("http") -> rawHdUrl
            rawHdUrl.startsWith("/") -> "https://snaptik.app$rawHdUrl"
            else -> ""
        }
        val rawDlUrl = json.optString("downloadUrl")
        val dlUrl = when {
            rawDlUrl.startsWith("http") -> rawDlUrl
            rawDlUrl.startsWith("/") -> "https://snaptik.app$rawDlUrl"
            else -> ""
        }
        val audioUrl = json.optString("musicUrl").ifBlank { json.optString("audioUrl") }

        if (hdUrl.startsWith("http")) {
            items.add(
                ScraperDownloadItem(
                    id = "snaptik_hd",
                    title = title,
                    label = "HD Video (No Watermark)",
                    url = hdUrl,
                    category = MediaCategory.VIDEO,
                    extension = "mp4",
                    thumbnail = thumb,
                    author = author,
                    referer = "https://snaptik.app/",
                )
            )
        }
        if (dlUrl.startsWith("http") && dlUrl != hdUrl) {
            items.add(
                ScraperDownloadItem(
                    id = "snaptik_sd",
                    title = title,
                    label = "MP4 (No Watermark)",
                    url = dlUrl,
                    category = MediaCategory.VIDEO,
                    extension = "mp4",
                    thumbnail = thumb,
                    author = author,
                    referer = "https://snaptik.app/",
                )
            )
        }
        if (audioUrl.startsWith("http")) {
            items.add(
                ScraperDownloadItem(
                    id = "snaptik_mp3",
                    title = "$title Audio",
                    label = "MP3 Sound",
                    url = audioUrl,
                    category = MediaCategory.AUDIO,
                    extension = "mp3",
                    thumbnail = thumb,
                    author = author,
                    referer = "https://snaptik.app/",
                )
            )
        }

        if (items.isEmpty()) throw IllegalStateException("SnapTik returned no downloadable links")

        return ScrapeResult(
            platform = PlatformId.TIKTOK,
            serverUsed = "SnapTik",
            title = title,
            author = author,
            thumbnail = thumb,
            sourceUrl = url,
            items = items,
        )
    }

    private fun solveSnapTikChallenge(c: JSONObject): Triple<String, String, String> {
        val eVal = c.optString("_e")
        val hVal = c.optString("_h")
        val type = c.optString("t")
        val resultNum: Long = when (type) {
            "b" -> ((c.optLong("a") xor c.optLong("b")) shr c.optInt("s")) and 255L
            "r" -> {
                val arr = c.optJSONArray("n")
                var sum = 0L
                if (arr != null) {
                    for (i in 0 until arr.length()) sum += arr.optLong(i)
                }
                sum * 2L + 1L
            }
            "c" -> {
                val w = c.optString("w")
                val idx = c.optInt("i").coerceIn(0, (w.length - 1).coerceAtLeast(0))
                val code = if (w.isNotEmpty()) w[idx].code.toLong() else 0L
                code * c.optLong("m")
            }
            "m" -> ((c.optLong("a") + c.optLong("b")) % 100L) * c.optLong("c")
            "n" -> {
                val a = c.optLong("a")
                val b = c.optLong("b")
                val cc = c.optLong("c")
                a * b + b * cc + cc * a - a
            }
            else -> throw IllegalStateException("Unknown SnapTik challenge type: $type")
        }
        return Triple(resultNum.toString(), eVal, hVal)
    }

    private fun scrapeTikTokIO(url: String): ScrapeResult {
        val headers = mapOf(
            "Origin" to "https://tiktokio.com",
            "Referer" to "https://tiktokio.com/",
        )
        val payload = JSONObject().apply {
            put("vid", url)
            put("prefix", "tiktokio.com")
        }.toString()
        val res = http.postRaw("https://tiktokio.com/api/v1/tk/html", payload, "application/json", headers)
        ScraperUtils.ensureOkOrThrow(res, "TikTokIO")
        val html = if (res.body.trimStart().startsWith("{")) {
            JSONObject(res.body).optString("data", res.body)
        } else res.body

        val doc = Jsoup.parse(html)
        val title = doc.selectFirst("h3, h2")?.text()?.trim().orEmpty().ifBlank { "TikTok Video" }
        val thumb = doc.selectFirst("img")?.attr("src").orEmpty()
        val items = mutableListOf<ScraperDownloadItem>()

        doc.select(".image-item img, .photo-item img").forEachIndexed { i, img ->
            val src = img.attr("src")
            if (src.startsWith("http")) {
                items.add(
                    ScraperDownloadItem(
                        id = "tkio_img_$i",
                        title = "$title (${i + 1})",
                        label = "Photo ${i + 1}",
                        url = src,
                        category = MediaCategory.IMAGE,
                        extension = "jpg",
                        thumbnail = src,
                        referer = "https://tiktokio.com/",
                    )
                )
            }
        }

        doc.select("a.download-btn, a[href]").forEachIndexed { i, a ->
            val href = a.attr("href")
            val text = a.text().trim()
            if (!href.startsWith("http")) return@forEachIndexed
            if (href.contains("://tiktokio.com", true) || text.contains("another", true)) return@forEachIndexed
            val isMp3 = text.contains("mp3", true) || text.contains("audio", true)
            items.add(
                ScraperDownloadItem(
                    id = "tkio_dl_$i",
                    title = title,
                    label = text.ifBlank { if (isMp3) "MP3 Audio" else "MP4 Video" },
                    url = href,
                    category = if (isMp3) MediaCategory.AUDIO else MediaCategory.VIDEO,
                    extension = if (isMp3) "mp3" else "mp4",
                    thumbnail = thumb,
                    referer = "https://tiktokio.com/",
                )
            )
        }

        return ScrapeResult(
            platform = PlatformId.TIKTOK,
            serverUsed = "TikTokIO",
            title = title,
            thumbnail = thumb,
            sourceUrl = url,
            items = items,
        )
    }

    private fun scrapeSssTik(url: String): ScrapeResult {
        val pageRes = http.get("https://ssstik.io/id", mapOf("Referer" to "https://ssstik.io/"))
        val ttMatch = Regex("""s_tt\s*=\s*['"]([^'"]+)['"]""").find(pageRes.body)
        val tt = ttMatch?.groupValues?.get(1) ?: throw IllegalStateException("SSSTik token not found")
        val cookies = pageRes.cookieString()

        val dlRes = http.postForm(
            "https://ssstik.io/abc?url=dl",
            mapOf("id" to url, "locale" to "id", "tt" to tt),
            mapOf(
                "HX-Request" to "true",
                "HX-Target" to "target",
                "HX-Current-URL" to "https://ssstik.io/id",
                "Origin" to "https://ssstik.io",
                "Referer" to "https://ssstik.io/id",
                "Cookie" to cookies,
            ),
        )
        ScraperUtils.ensureOkOrThrow(dlRes, "SSSTik")
        val doc = Jsoup.parse(dlRes.body)
        val title = doc.selectFirst(".maintext, p")?.text()?.trim().orEmpty().ifBlank { "TikTok Video" }
        val items = mutableListOf<ScraperDownloadItem>()
        doc.select("a[href^=http]").forEachIndexed { i, a ->
            val href = a.attr("href")
            if (href.contains("ssstik.io")) return@forEachIndexed
            val label = a.text().trim()
            val isMp3 = label.contains("mp3", true) || label.contains("music", true)
            items.add(
                ScraperDownloadItem(
                    id = "ssstik_$i",
                    title = title,
                    label = label.ifBlank { "Download" },
                    url = href,
                    category = if (isMp3) MediaCategory.AUDIO else MediaCategory.VIDEO,
                    extension = if (isMp3) "mp3" else "mp4",
                    referer = "https://ssstik.io/",
                )
            )
        }
        return ScrapeResult(
            platform = PlatformId.TIKTOK,
            serverUsed = "SSSTik",
            title = title,
            sourceUrl = url,
            items = items,
        )
    }

    private fun scrapeTikDownloader(url: String): ScrapeResult {
        val res = http.postForm(
            "https://tikdownloader.io/api/ajaxSearch",
            mapOf("q" to url, "vt" to "id"),
            mapOf(
                "Origin" to "https://tikdownloader.io",
                "Referer" to "https://tikdownloader.io/id",
                "X-Requested-With" to "XMLHttpRequest",
            ),
        )
        ScraperUtils.ensureOkOrThrow(res, "TikDownloader")
        val json = JSONObject(res.body)
        val html = json.optString("data")
        val doc = Jsoup.parse(html)
        val title = doc.selectFirst("h3")?.text()?.trim().orEmpty().ifBlank { "TikTok Video" }
        val thumb = doc.selectFirst("img")?.attr("src").orEmpty()
        val items = mutableListOf<ScraperDownloadItem>()

        doc.select(".tik-button-dl, a[href^=http]").forEachIndexed { i, a ->
            val href = a.attr("href")
            if (!href.startsWith("http")) return@forEachIndexed
            val label = a.text().trim()
            val isMp3 = label.contains("mp3", true)
            items.add(
                ScraperDownloadItem(
                    id = "tikdl_$i",
                    title = title,
                    label = label.ifBlank { "Download" },
                    url = href,
                    category = if (isMp3) MediaCategory.AUDIO else MediaCategory.VIDEO,
                    extension = if (isMp3) "mp3" else "mp4",
                    thumbnail = thumb,
                    referer = "https://tikdownloader.io/",
                )
            )
        }
        return ScrapeResult(
            platform = PlatformId.TIKTOK,
            serverUsed = "TikDownloader",
            title = title,
            thumbnail = thumb,
            sourceUrl = url,
            items = items,
        )
    }
}

