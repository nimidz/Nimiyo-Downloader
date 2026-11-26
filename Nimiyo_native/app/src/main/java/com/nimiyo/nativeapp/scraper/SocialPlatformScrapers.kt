package com.nimiyo.nativeapp.scraper

import com.nimiyo.nativeapp.core.HttpClientFactory
import com.nimiyo.nativeapp.core.HttpResponseData
import com.nimiyo.nativeapp.data.MediaCategory
import com.nimiyo.nativeapp.data.PlatformId
import com.nimiyo.nativeapp.data.ScrapeResult
import com.nimiyo.nativeapp.data.ScraperDownloadItem
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URI
import java.util.Locale

sealed class RedNoteProbeResult {
    data class Resolved(val result: ScrapeResult) : RedNoteProbeResult()
    data class FallbackToYtDlp(val normalizedUrl: String) : RedNoteProbeResult()
}

/**
 * Cookie-free direct platform scrapers & fallbacks for sites yt-dlp does not cover
 * (Pixiv, Threads, Shopee Video), regional redirectors (RedNote/XHS),
 * Spotify playlists/albums, and login-gated fallbacks (Instagram, Facebook).
 */
object SocialPlatformScrapers {

    private val pixivArtworksRegex = Regex("""/artworks/(\d+)""")
    private val pixivLegacyIdRegex = Regex("""[?&]illust_id=(\d+)""")
    private val initialStateRegex = Regex(
        """window\.__INITIAL_STATE__\s*=\s*(\{.*?\})\s*(?:;|</script>)""",
        setOf(RegexOption.DOT_MATCHES_ALL),
    )
    private val nextDataRegex = Regex(
        """<script[^>]*id=["']__NEXT_DATA__["'][^>]*>(.*?)</script>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )
    private val masterUrlJsonRegex = Regex(""""masterUrl"\s*:\s*"([^"]+)"""")
    private val originVideoKeyRegex = Regex(""""originVideoKey"\s*:\s*"([^"]+)"""")
    private val xhsNoteIdRegex = Regex("""/(?:explore|discovery/item)/([0-9a-fA-F]{16,32})""")

    private val spotifyPlaylistOrAlbumRegex = Regex(
        """open\.spotify\.com/(?:intl-[a-z-]+/)?(playlist|album)/([A-Za-z0-9]+)""",
        RegexOption.IGNORE_CASE,
    )

    private val shopeeVideoKeysRegex = Regex(
        """"(?:play_url|video_url|no_watermark_url|watermark_play_url|mp4_url)"\s*:\s*"(https?:[^"]+)"""",
        RegexOption.IGNORE_CASE,
    )
    private val mp4CdnRegex = Regex(
        """(https?:\\?/\\?/[^\s"'<>]+?\.mp4(?:\?[^\s"'<>]*)?)""",
        RegexOption.IGNORE_CASE,
    )

    // -------------------------------------------------------------------------
    // PIXIV
    // -------------------------------------------------------------------------

    fun analyzePixiv(http: HttpClientFactory, cleanUrl: String): ScrapeResult {
        val illustId = extractPixivIllustId(cleanUrl)
            ?: throw IllegalArgumentException("Link Pixiv tidak valid. Gunakan link artwork seperti pixiv.net/artworks/<ID>.")

        val headers = mapOf(
            "Referer" to "https://www.pixiv.net/",
            "User-Agent" to HttpClientFactory.CHROME_DESKTOP_UA,
            "Accept" to "application/json",
        )
        val illustRes = http.get("https://www.pixiv.net/ajax/illust/$illustId", headers)
        if (illustRes.status !in 200..299 || illustRes.body.isBlank()) {
            throw IllegalStateException("Gagal mengambil metadata Pixiv (HTTP ${illustRes.status}).")
        }

        val illustJson = JSONObject(illustRes.body)
        if (illustJson.optBoolean("error", false)) {
            val msg = illustJson.optString("message").ifBlank { "Artwork tidak tersedia atau dikunci akun." }
            throw IllegalStateException("Pixiv: $msg")
        }

        val body = illustJson.optJSONObject("body")
            ?: throw IllegalStateException("Pixiv: Metadata artwork kosong.")

        val pagesBody = runCatching {
            val pagesRes = http.get("https://www.pixiv.net/ajax/illust/$illustId/pages", headers)
            if (pagesRes.status in 200..299 && pagesRes.body.isNotBlank()) {
                val pj = JSONObject(pagesRes.body)
                if (!pj.optBoolean("error", false)) pj.optJSONArray("body") else null
            } else null
        }.getOrNull()

        return parsePixivJson(
            illustBody = body,
            pagesBody = pagesBody,
            illustId = illustId,
            sourceUrl = cleanUrl,
        )
    }

    internal fun parsePixivJson(
        illustBody: JSONObject,
        pagesBody: JSONArray?,
        illustId: String,
        sourceUrl: String,
    ): ScrapeResult {
        val xRestrict = illustBody.optInt("xRestrict", 0)
        if (xRestrict == 1) {
            throw PolicyViolationException(
                PolicyViolationCategory.NSFW_18,
                "Diblokir oleh Peraturan NIMIYO [Pornografi & NSFW (18+)]: Artwork Pixiv bertanda R-18.",
            )
        }
        if (xRestrict >= 2) {
            throw PolicyViolationException(
                PolicyViolationCategory.VIOLENCE_GORE,
                "Diblokir oleh Peraturan NIMIYO [Kekerasan & Kebrutalan]: Artwork Pixiv bertanda R-18G.",
            )
        }

        val title = illustBody.optString("title").ifBlank { "Pixiv #$illustId" }
        val author = illustBody.optString("userName")
        val description = Jsoup.parse(illustBody.optString("description")).text()
        val tags = extractPixivTags(illustBody)
        NimiyoContentGuard.checkTextCompliance(title, author, description, *tags.toTypedArray())

        val bodyUrls = illustBody.optJSONObject("urls")
        val thumbUrl = bodyUrls?.optString("regular")
            ?.ifBlank { bodyUrls.optString("small") }
            ?.ifBlank { bodyUrls.optString("thumb") }
            .orEmpty()

        val imageUrls = mutableListOf<String>()
        if (pagesBody != null) {
            for (i in 0 until pagesBody.length()) {
                val u = pagesBody.optJSONObject(i)?.optJSONObject("urls") ?: continue
                val orig = u.optString("original").ifBlank { u.optString("regular") }
                if (orig.startsWith("http", ignoreCase = true)) {
                    imageUrls.add(orig)
                }
            }
        }
        if (imageUrls.isEmpty() && bodyUrls != null) {
            val single = bodyUrls.optString("original").ifBlank { bodyUrls.optString("regular") }
            if (single.startsWith("http", ignoreCase = true)) {
                imageUrls.add(single)
            }
        }
        if (imageUrls.isEmpty()) {
            throw IllegalStateException("Pixiv: Gambar artwork tidak ditemukan atau memerlukan akun.")
        }

        val items = imageUrls.mapIndexed { idx, imgUrl ->
            val ext = inferMediaExtension(imgUrl, "png")
            val pageTitle = if (imageUrls.size > 1) "$title (Page ${idx + 1})" else title
            ScraperDownloadItem(
                id = "pixiv_${illustId}_${idx}_${imgUrl.hashCode()}",
                title = pageTitle,
                label = if (imageUrls.size > 1) "Page ${idx + 1} (${ext.uppercase(Locale.US)} Original)" else "Original Image (${ext.uppercase(Locale.US)})",
                url = imgUrl,
                category = MediaCategory.IMAGE,
                extension = ext,
                thumbnail = thumbUrl.ifBlank { imgUrl },
                author = author,
                referer = "https://www.pixiv.net/",
            )
        }

        return ScrapeResult(
            platform = PlatformId.PIXIV,
            serverUsed = "Nimiyo • Pixiv Direct",
            title = title,
            author = author,
            thumbnail = thumbUrl.ifBlank { imageUrls.first() },
            sourceUrl = sourceUrl,
            items = items,
            extractorName = "PixivDirect",
            isPlaylist = items.size > 1,
        )
    }

    // -------------------------------------------------------------------------
    // THREADS (cookie-free)
    // -------------------------------------------------------------------------

    fun analyzeThreads(http: HttpClientFactory, cleanUrl: String): ScrapeResult {
        val candidates = linkedSetOf(
            cleanUrl,
            cleanUrl.replace("threads.net", "threads.com", ignoreCase = true),
        )
        val trimmedPath = cleanUrl.substringBefore('?').trimEnd('/')
        if (trimmedPath.contains("/post/")) {
            candidates.add("$trimmedPath/embed")
            candidates.add("${trimmedPath.replace("threads.net", "threads.com", ignoreCase = true)}/embed")
        }

        val res = probeAndParseFirst(http, candidates.toList()) { httpRes, finalUrl ->
            parseThreadsHtml(httpRes.body, finalUrl)
        }
        if (res != null) return res
        throw IllegalStateException("Threads: Media tidak ditemukan atau postingan bersifat privat/hanya teks.")
    }

    internal fun parseThreadsHtml(html: String, resolvedUrl: String): ScrapeResult? {
        val doc = Jsoup.parse(html)
        var title = pickFirstMeta(doc, "meta[property=og:title]", "meta[name=og:title]").orEmpty()
        var desc = pickFirstMeta(doc, "meta[property=og:description]", "meta[name=og:description]", "meta[name=description]").orEmpty()
        var author = extractHandleFromTitleOrUrl(title, resolvedUrl)

        val videoUrls = LinkedHashSet<String>()
        val imageUrls = LinkedHashSet<String>()

        doc.select("meta[property=og:video], meta[property=og:video:url], meta[property=og:video:secure_url]").forEach { el ->
            normalizeMediaUrl(el.attr("content"), keepHttpIfOriginal = false)?.let { videoUrls.add(it) }
        }
        doc.select("video[src], video source[src]").forEach { el ->
            normalizeMediaUrl(el.attr("src"), keepHttpIfOriginal = false)?.let { videoUrls.add(it) }
        }
        doc.select("meta[property=og:image]").forEach { el ->
            normalizeMediaUrl(el.attr("content"), keepHttpIfOriginal = false)
                ?.takeUnless { isAvatarOrStaticLogoUrl(it) }
                ?.let { imageUrls.add(it) }
        }

        // Threads embeds thread_items JSON in <script data-sjs> / <script type="application/json"> / inline scripts
        val scripts = doc.select("script[data-sjs], script[type='application/json']").ifEmpty {
            doc.select("script")
        }
        scripts.forEach { script ->
            val raw = script.data()
            if (!raw.contains("video_versions") && !raw.contains("image_versions2")) return@forEach
            val parsedObj = runCatching { JSONObject(raw) }.getOrNull()
            if (parsedObj != null) {
                collectThreadsJsonMedia(parsedObj, videoUrls, imageUrls) { cap, user ->
                    if (desc.isBlank() && cap.isNotBlank()) desc = cap
                    if (author.isBlank() && user.isNotBlank()) author = user
                }
            }
        }

        // Regex fallback on unescaped JSON URLs inside HTML
        if (videoUrls.isEmpty()) {
            Regex(""""video_versions"\s*:\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL)
                .findAll(html)
                .forEach { block ->
                    Regex(""""url"\s*:\s*"(https?:[^"]+)"""").findAll(block.groupValues[1]).forEach { m ->
                        normalizeMediaUrl(m.groupValues[1])?.let { videoUrls.add(it) }
                    }
                }
        }

        if (videoUrls.isEmpty() && imageUrls.isEmpty()) return null

        val finalTitle = desc.ifBlank { title }.ifBlank { "Threads Media" }.take(120)
        NimiyoContentGuard.checkTextCompliance(finalTitle, title, desc, author, resolvedUrl)

        val thumb = imageUrls.firstOrNull().orEmpty()
        val items = mutableListOf<ScraperDownloadItem>()
        videoUrls.forEachIndexed { idx, vUrl ->
            items.add(
                ScraperDownloadItem(
                    id = "threads_vid_${idx}_${vUrl.hashCode()}",
                    title = finalTitle,
                    label = if (videoUrls.size > 1) "Threads Video #${idx + 1} (MP4)" else "Threads Video (MP4)",
                    url = vUrl,
                    category = MediaCategory.VIDEO,
                    extension = "mp4",
                    thumbnail = thumb,
                    author = author,
                    referer = "https://www.threads.com/",
                )
            )
        }
        if (videoUrls.isEmpty() || imageUrls.size > 1) {
            imageUrls.forEachIndexed { idx, imgUrl ->
                val ext = inferMediaExtension(imgUrl, "jpg")
                items.add(
                    ScraperDownloadItem(
                        id = "threads_img_${idx}_${imgUrl.hashCode()}",
                        title = if (imageUrls.size > 1) "$finalTitle (${idx + 1})" else finalTitle,
                        label = if (imageUrls.size > 1) "Photo ${idx + 1} (${ext.uppercase(Locale.US)})" else "Photo (${ext.uppercase(Locale.US)})",
                        url = imgUrl,
                        category = MediaCategory.IMAGE,
                        extension = ext,
                        thumbnail = imgUrl,
                        author = author,
                        referer = "https://www.threads.com/",
                    )
                )
            }
        }

        if (items.isEmpty()) return null
        return ScrapeResult(
            platform = PlatformId.THREADS,
            serverUsed = "Nimiyo • Threads Direct",
            title = finalTitle,
            author = author,
            thumbnail = thumb,
            sourceUrl = resolvedUrl,
            items = items,
            extractorName = "ThreadsDirect",
            isPlaylist = items.size > 1,
        )
    }

    // -------------------------------------------------------------------------
    // SHOPEE VIDEO (cookie-free)
    // -------------------------------------------------------------------------

    fun analyzeShopee(http: HttpClientFactory, cleanUrl: String): ScrapeResult {
        var hitAntiBot = false
        val parsed = probeAndParseFirst(http, listOf(cleanUrl)) { res, finalUrl ->
            val lowerBody = res.body.lowercase(Locale.US)
            if (res.status == 403 || lowerBody.contains("verify/captcha") || lowerBody.contains("captcha-verify")) {
                hitAntiBot = true
            }
            parseShopeeHtml(res.body, finalUrl)
        }
        if (parsed != null) return parsed
        if (hitAntiBot) {
            throw IllegalStateException("Shopee membatasi akses otomatis (CAPTCHA/403). Coba pakai link Share langsung dari Shopee Video.")
        }
        throw IllegalStateException("Shopee Video: Link video MP4 tidak ditemukan pada halaman ini.")
    }

    internal fun parseShopeeHtml(html: String, resolvedUrl: String): ScrapeResult? {
        val doc = Jsoup.parse(html)
        var title = pickFirstMeta(doc, "meta[property=og:title]", "meta[name=og:title]", "meta[name=twitter:title]").orEmpty()
        if (title.isBlank()) title = doc.title().trim()
        val desc = pickFirstMeta(doc, "meta[property=og:description]", "meta[name=description]").orEmpty()
        val author = ""

        val videoUrls = LinkedHashSet<String>()
        val imageUrls = LinkedHashSet<String>()

        doc.select("meta[property=og:video], meta[property=og:video:url], meta[property=og:video:secure_url]").forEach { el ->
            normalizeMediaUrl(el.attr("content"))?.let { videoUrls.add(it) }
        }
        doc.select("video[src], video source[src]").forEach { el ->
            normalizeMediaUrl(el.attr("src"))?.let { videoUrls.add(it) }
        }
        doc.select("meta[property=og:image]").forEach { el ->
            normalizeMediaUrl(el.attr("content"))?.let { imageUrls.add(it) }
        }

        // Check __NEXT_DATA__ and __INITIAL_STATE__
        nextDataRegex.find(html)?.let { m ->
            extractShopeeVideoLinks(m.groupValues[1], videoUrls)
        }
        initialStateRegex.find(html)?.let { m ->
            extractShopeeVideoLinks(m.groupValues[1], videoUrls)
        }
        extractShopeeVideoLinks(html, videoUrls)

        if (videoUrls.isEmpty()) return null

        val finalTitle = title.ifBlank { desc }.ifBlank { "Shopee Video" }.take(120)
        NimiyoContentGuard.checkTextCompliance(finalTitle, desc, resolvedUrl)

        val thumb = imageUrls.firstOrNull().orEmpty()
        val items = videoUrls.mapIndexed { idx, vUrl ->
            ScraperDownloadItem(
                id = "shopee_vid_${idx}_${vUrl.hashCode()}",
                title = finalTitle,
                label = if (videoUrls.size > 1) "Shopee Video #${idx + 1} (MP4)" else "Shopee Video (MP4)",
                url = vUrl,
                category = MediaCategory.VIDEO,
                extension = "mp4",
                thumbnail = thumb,
                author = author,
                referer = resolvedUrl,
            )
        }

        return ScrapeResult(
            platform = PlatformId.SHOPEE,
            serverUsed = "Nimiyo • Shopee Direct",
            title = finalTitle,
            author = author,
            thumbnail = thumb,
            sourceUrl = resolvedUrl,
            items = items,
            extractorName = "ShopeeDirect",
            isPlaylist = items.size > 1,
        )
    }

    // -------------------------------------------------------------------------
    // REDNOTE / XIAOHONGSHU (cookie-free)
    // -------------------------------------------------------------------------

    fun probeRedNote(http: HttpClientFactory, cleanUrl: String): RedNoteProbeResult {
        val candidateUrls = LinkedHashSet<String>()
        candidateUrls.add(cleanUrl)
        var bestFallbackUrl = rewriteRedNoteHostToXiaohongshu(cleanUrl)

        val userAgents = listOf(
            HttpClientFactory.CHROME_DESKTOP_UA,
            HttpClientFactory.SAFARI_MOBILE_UA,
            HttpClientFactory.WHATSAPP_UA,
        )

        val visited = mutableSetOf<String>()
        for (ua in userAgents) {
            val snapshot = candidateUrls.toList()
            for (target in snapshot) {
                if (!visited.add("$ua|$target")) continue
                val res = runCatching {
                    http.get(
                        url = target,
                        headers = mapOf(
                            "User-Agent" to ua,
                            "Accept-Language" to "en-US,en;q=0.9,zh-CN;q=0.8",
                        ),
                    )
                }.getOrNull() ?: continue

                val finalUrl = res.finalUrl.ifBlank { target }
                NimiyoContentGuard.checkUrlBeforeAnalyze(finalUrl)

                val redirectTarget = extractLoginRedirectTarget(finalUrl)
                if (!redirectTarget.isNullOrBlank()) {
                    candidateUrls.add(redirectTarget)
                    xhsNoteIdRegex.find(redirectTarget)?.groupValues?.getOrNull(1)?.let { nid ->
                        candidateUrls.add("https://www.xiaohongshu.com/discovery/item/$nid")
                        candidateUrls.add("https://www.rednote.com/discovery/item/$nid")
                    }
                    bestFallbackUrl = rewriteRedNoteHostToXiaohongshu(redirectTarget)
                } else {
                    bestFallbackUrl = rewriteRedNoteHostToXiaohongshu(finalUrl)
                }

                if (res.status in 200..299 && res.body.isNotBlank()) {
                    parseRedNoteHtml(res.body, finalUrl)?.let { parsed ->
                        return RedNoteProbeResult.Resolved(parsed)
                    }
                }
            }
        }

        return RedNoteProbeResult.FallbackToYtDlp(bestFallbackUrl)
    }

    internal fun parseRedNoteHtml(html: String, resolvedUrl: String): ScrapeResult? {
        if (resolvedUrl.contains("/login", ignoreCase = true)) return null
        val doc = Jsoup.parse(html)
        var title = pickFirstMeta(doc, "meta[property=og:title]", "meta[name=og:title]").orEmpty()
        var desc = pickFirstMeta(doc, "meta[property=og:description]", "meta[name=og:description]", "meta[name=description]").orEmpty()
        var author = ""
        val tags = mutableListOf<String>()
        val videoUrls = LinkedHashSet<String>()
        val imageUrls = LinkedHashSet<String>()

        doc.select("meta[property=og:video], meta[property=og:video:url], meta[property=og:video:secure_url], meta[name=og:video]")
            .forEach { el ->
                normalizeMediaUrl(el.attr("content"))?.let { videoUrls.add(it) }
            }
        doc.select("video[src], video source[src]").forEach { el ->
            normalizeMediaUrl(el.attr("src"))?.let { videoUrls.add(it) }
        }
        doc.select("meta[property=og:image], meta[name=og:image]").forEach { el ->
            normalizeMediaUrl(el.attr("content"))
                ?.takeUnless { isAvatarOrStaticLogoUrl(it) }
                ?.let { imageUrls.add(it) }
        }

        val stateMatch = initialStateRegex.find(html)
        if (stateMatch != null) {
            val normalizedJson = stateMatch.groupValues[1]
                .replace(Regex(""":\s*undefined\b"""), ":null")
                .replace(Regex(""",\s*undefined\b"""), ",null")
                .replace(Regex("""\[\s*undefined\b"""), "[null")
            runCatching { JSONObject(normalizedJson) }.getOrNull()?.let { root ->
                extractFromInitialState(root)?.let { note ->
                    if (title.isBlank()) title = note.optString("title").trim()
                    if (desc.isBlank()) desc = note.optString("desc").trim()
                    author = note.optJSONObject("user")?.optString("nickname").orEmpty().trim()

                    val tagArr = note.optJSONArray("tagList")
                    if (tagArr != null) {
                        for (i in 0 until tagArr.length()) {
                            val t = tagArr.optJSONObject(i)?.optString("name").orEmpty().trim()
                            if (t.isNotEmpty()) tags.add(t)
                        }
                    }

                    extractRedNoteVideoUrls(note.optJSONObject("video"), videoUrls)
                    extractRedNoteImageUrls(note.optJSONArray("imageList"), imageUrls)
                }
            }
        }

        if (videoUrls.isEmpty()) {
            masterUrlJsonRegex.findAll(html).forEach { m ->
                normalizeMediaUrl(m.groupValues[1])?.let { videoUrls.add(it) }
            }
            originVideoKeyRegex.findAll(html).forEach { m ->
                val key = unescapeJsonUrl(m.groupValues[1]).trimStart('/')
                if (key.isNotBlank() && !key.contains(" ")) {
                    videoUrls.add("https://sns-video-bd.xhscdn.com/$key")
                }
            }
        }

        if (videoUrls.isEmpty() && imageUrls.isEmpty()) {
            return null
        }

        val finalTitle = title.ifBlank { desc.lineSequence().firstOrNull()?.take(80).orEmpty() }
            .ifBlank { "RedNote Media" }
        NimiyoContentGuard.checkTextCompliance(finalTitle, author, desc, *tags.toTypedArray())

        val thumb = imageUrls.firstOrNull().orEmpty()
        val items = mutableListOf<ScraperDownloadItem>()
        videoUrls.forEachIndexed { idx, vUrl ->
            items.add(
                ScraperDownloadItem(
                    id = "xhs_vid_${idx}_${vUrl.hashCode()}",
                    title = finalTitle,
                    label = if (videoUrls.size > 1) "Video MP4 #${idx + 1}" else "Video MP4 (Watermark-Free)",
                    url = vUrl,
                    category = MediaCategory.VIDEO,
                    extension = "mp4",
                    thumbnail = thumb,
                    author = author,
                    referer = resolvedUrl,
                )
            )
        }
        if (videoUrls.isEmpty() || imageUrls.size > 1) {
            imageUrls.forEachIndexed { idx, imgUrl ->
                val ext = inferMediaExtension(imgUrl, "jpg")
                val imgTitle = if (imageUrls.size > 1) "$finalTitle (${idx + 1})" else finalTitle
                items.add(
                    ScraperDownloadItem(
                        id = "xhs_img_${idx}_${imgUrl.hashCode()}",
                        title = imgTitle,
                        label = if (imageUrls.size > 1) "Photo ${idx + 1} (${ext.uppercase(Locale.US)})" else "Photo (${ext.uppercase(Locale.US)})",
                        url = imgUrl,
                        category = MediaCategory.IMAGE,
                        extension = ext,
                        thumbnail = imgUrl,
                        author = author,
                        referer = resolvedUrl,
                    )
                )
            }
        }

        if (items.isEmpty()) return null
        return ScrapeResult(
            platform = PlatformId.REDNOTE,
            serverUsed = "Nimiyo • RedNote Direct",
            title = finalTitle,
            author = author,
            thumbnail = thumb,
            sourceUrl = resolvedUrl,
            items = items,
            extractorName = "RedNoteDirect",
            isPlaylist = items.size > 1,
        )
    }

    // -------------------------------------------------------------------------
    // SPOTIFY PLAYLIST / ALBUM EMBED (cookie-free)
    // -------------------------------------------------------------------------

    fun analyzeSpotifyPlaylistOrAlbumEmbed(http: HttpClientFactory, cleanUrl: String): ScrapeResult? {
        val m = spotifyPlaylistOrAlbumRegex.find(cleanUrl) ?: return null
        val kind = m.groupValues[1].lowercase(Locale.US)
        val id = m.groupValues[2]
        val embedUrl = "https://open.spotify.com/embed/$kind/$id"
        val res = runCatching {
            http.get(
                embedUrl,
                mapOf("User-Agent" to HttpClientFactory.CHROME_DESKTOP_UA),
            )
        }.getOrNull() ?: return null
        if (res.status !in 200..299 || res.body.isBlank()) return null

        return parseSpotifyEmbedHtml(res.body, cleanUrl, kind)
    }

    internal fun parseSpotifyEmbedHtml(html: String, sourceUrl: String, kind: String): ScrapeResult? {
        val nextMatch = nextDataRegex.find(html) ?: return null
        val root = runCatching { JSONObject(nextMatch.groupValues[1]) }.getOrNull() ?: return null
        val entity = root.optJSONObject("props")
            ?.optJSONObject("pageProps")
            ?.optJSONObject("state")
            ?.optJSONObject("data")
            ?.optJSONObject("entity")
            ?: return null

        val listTitle = entity.optString("name").ifBlank { entity.optString("title") }
            .ifBlank { "Spotify ${kind.replaceFirstChar { it.uppercase(Locale.US) }}" }
        val listAuthor = entity.optString("subtitle")
        val coverArr = entity.optJSONObject("visualIdentity")?.optJSONArray("image")
        val listThumb = coverArr?.optJSONObject(0)?.optString("url").orEmpty()

        NimiyoContentGuard.checkTextCompliance(listTitle, listAuthor)

        val trackList = entity.optJSONArray("trackList") ?: return null
        val items = mutableListOf<ScraperDownloadItem>()
        val maxTracks = trackList.length().coerceAtMost(200)
        for (i in 0 until maxTracks) {
            val t = trackList.optJSONObject(i) ?: continue
            val trackTitle = t.optString("title").trim()
            if (trackTitle.isBlank()) continue
            val trackArtist = t.optString("subtitle").trim()
            val durMs = t.optLong("duration", 0L)

            val safe = runCatching {
                NimiyoContentGuard.checkTextCompliance(trackTitle, trackArtist)
                true
            }.getOrDefault(false)
            if (!safe) continue

            val query = listOf(trackTitle, trackArtist).filter { it.isNotBlank() }.joinToString(" ")
            val ytTarget = "ytsearch1:$query audio"
            items.add(
                ScraperDownloadItem(
                    id = "sp_${i}_${ytTarget.hashCode()}",
                    title = trackTitle,
                    label = "${i + 1}. $trackTitle (MP3 320kbps)",
                    url = ytTarget,
                    category = MediaCategory.AUDIO,
                    extension = "mp3",
                    thumbnail = listThumb,
                    author = trackArtist,
                    durationMs = durMs,
                    ytdlpFormatSelector = "ba/best",
                    ytdlpAudioFormat = "mp3",
                    ytdlpAudioQuality = "320K",
                    ytdlpContainer = "mp3",
                    ytdlpTargetUrl = ytTarget,
                )
            )
        }

        if (items.isEmpty()) return null
        return ScrapeResult(
            platform = PlatformId.SPOTIFY,
            serverUsed = "Nimiyo • Spotify ${kind.replaceFirstChar { it.uppercase(Locale.US) }} -> YT Search",
            title = listTitle,
            author = listAuthor,
            thumbnail = listThumb,
            sourceUrl = sourceUrl,
            items = items,
            extractorName = "SpotifyEmbedPlaylist",
            isPlaylist = true,
        )
    }

    // -------------------------------------------------------------------------
    // TIKTOK PHOTO / CAROUSEL & SOCIAL PHOTO FALLBACKS
    // -------------------------------------------------------------------------

    fun analyzeTikTokPhoto(http: HttpClientFactory, cleanUrl: String): ScrapeResult {
        runCatching { analyzeTikTokDirectLocal(http, cleanUrl) }
            .getOrNull()
            ?.let { return it }

        val apiUrl = "https://www.tikwm.com/api/?url=${ScraperUtils.urlEncode(cleanUrl)}"
        val res = http.get(
            apiUrl,
            mapOf(
                "Accept" to "application/json",
                "User-Agent" to HttpClientFactory.CHROME_DESKTOP_UA,
            ),
        )
        if (res.status !in 200..299 || res.body.isBlank()) {
            throw IllegalStateException("TikWM API error (HTTP ${res.status})")
        }
        val root = JSONObject(res.body)
        if (root.optInt("code", -1) != 0) {
            throw IllegalStateException(root.optString("msg").ifBlank { "TikWM extraction failed" })
        }
        val data = root.optJSONObject("data") ?: throw IllegalStateException("TikWM returned empty data")
        val title = data.optString("title").trim().ifBlank { "TikTok Media" }
        val author = data.optJSONObject("author")?.optString("nickname").orEmpty()
            .ifBlank { data.optJSONObject("author")?.optString("unique_id").orEmpty() }
        val thumb = data.optString("cover").ifBlank { data.optString("origin_cover") }
        NimiyoContentGuard.checkTextCompliance(title, author, cleanUrl)

        val items = mutableListOf<ScraperDownloadItem>()
        val images = data.optJSONArray("images")
        if (images != null && images.length() > 0) {
            for (i in 0 until images.length()) {
                val imgUrl = images.optString(i).trim()
                if (!imgUrl.startsWith("http", ignoreCase = true)) continue
                val ext = inferMediaExtension(imgUrl, "jpg")
                items.add(
                    ScraperDownloadItem(
                        id = "tt_img_${i}_${imgUrl.hashCode()}",
                        title = "$title (${i + 1})",
                        label = "Photo ${i + 1} (${ext.uppercase(Locale.US)})",
                        url = imgUrl,
                        category = MediaCategory.IMAGE,
                        extension = ext,
                        thumbnail = imgUrl,
                        author = author,
                        referer = "https://www.tiktok.com/",
                    ),
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
                        id = "tt_hd_${hdPlay.hashCode()}",
                        title = title,
                        label = "Video HD (No Watermark)",
                        url = hdPlay,
                        category = MediaCategory.VIDEO,
                        extension = "mp4",
                        thumbnail = thumb,
                        author = author,
                        referer = "https://www.tiktok.com/",
                    ),
                )
            }
            if (play.startsWith("http", ignoreCase = true) && play != hdPlay) {
                items.add(
                    ScraperDownloadItem(
                        id = "tt_sd_${play.hashCode()}",
                        title = title,
                        label = "Video MP4 (No Watermark)",
                        url = play,
                        category = MediaCategory.VIDEO,
                        extension = "mp4",
                        thumbnail = thumb,
                        author = author,
                        referer = "https://www.tiktok.com/",
                    ),
                )
            }
        }
        if (music.startsWith("http", ignoreCase = true)) {
            items.add(
                ScraperDownloadItem(
                    id = "tt_mp3_${music.hashCode()}",
                    title = "$title Audio",
                    label = "Audio MP3",
                    url = music,
                    category = MediaCategory.AUDIO,
                    extension = "mp3",
                    thumbnail = thumb,
                    author = author,
                    referer = "https://www.tiktok.com/",
                ),
            )
        }
        if (items.isEmpty()) {
            throw IllegalStateException("TikWM returned no downloadable media")
        }
        return ScrapeResult(
            platform = PlatformId.TIKTOK,
            serverUsed = "Nimiyo • TikTok Photo/Direct",
            title = title,
            author = author,
            thumbnail = thumb.ifBlank { items.first().thumbnail },
            sourceUrl = cleanUrl,
            items = items,
            extractorName = "TikWM",
            isPlaylist = items.count { it.category == MediaCategory.IMAGE } > 1,
        )
    }

    private fun analyzeTikTokDirectLocal(http: HttpClientFactory, cleanUrl: String): ScrapeResult? {
        val res = http.get(
            cleanUrl,
            mapOf(
                "User-Agent" to HttpClientFactory.SAFARI_MOBILE_UA,
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                "Accept-Language" to "en-US,en;q=0.9,id;q=0.8",
            ),
        )
        if (res.status !in 200..299 || res.body.isBlank()) return null

        val match = Regex(
            """<script[^>]*id=["']__UNIVERSAL_DATA_FOR_REHYDRATION__["'][^>]*>(.*?)</script>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
        ).find(res.body) ?: return null

        val root = runCatching { JSONObject(match.groupValues[1]) }.getOrNull() ?: return null
        val scope = root.optJSONObject("__DEFAULT_SCOPE__") ?: return null
        val detail = scope.optJSONObject("webapp.reflow.video.detail")
            ?: scope.optJSONObject("webapp.video-detail")
            ?: return null
        val itemStruct = detail.optJSONObject("itemInfo")?.optJSONObject("itemStruct") ?: return null

        val title = itemStruct.optString("desc").trim().ifBlank { "TikTok Media" }
        val authorObj = itemStruct.optJSONObject("author")
        val author = authorObj?.optString("nickname").orEmpty()
            .ifBlank { authorObj?.optString("uniqueId").orEmpty() }
        NimiyoContentGuard.checkTextCompliance(title, author, cleanUrl)

        val imagePost = itemStruct.optJSONObject("imagePost")
        val imagesArr = imagePost?.optJSONArray("images")
        val coverThumb = imagePost?.optJSONObject("cover")
            ?.optJSONObject("imageURL")
            ?.optJSONArray("urlList")
            ?.optString(0)
            .orEmpty()
            .ifBlank { itemStruct.optJSONObject("video")?.optString("cover").orEmpty() }

        val items = mutableListOf<ScraperDownloadItem>()
        if (imagesArr != null && imagesArr.length() > 0) {
            for (i in 0 until imagesArr.length()) {
                val urlList = imagesArr.optJSONObject(i)
                    ?.optJSONObject("imageURL")
                    ?.optJSONArray("urlList")
                    ?: continue
                val imgUrl = (0 until urlList.length())
                    .map { urlList.optString(it).trim() }
                    .firstOrNull { it.startsWith("http", ignoreCase = true) }
                    ?: continue
                val ext = inferMediaExtension(imgUrl, "jpg")
                items.add(
                    ScraperDownloadItem(
                        id = "tt_loc_img_${i}_${imgUrl.hashCode()}",
                        title = "$title (${i + 1})",
                        label = "Photo ${i + 1} (${ext.uppercase(Locale.US)})",
                        url = imgUrl,
                        category = MediaCategory.IMAGE,
                        extension = ext,
                        thumbnail = imgUrl,
                        author = author,
                        referer = "https://www.tiktok.com/",
                    ),
                )
            }
        }

        val musicUrl = itemStruct.optJSONObject("music")?.optString("playUrl").orEmpty().trim()
        if (musicUrl.startsWith("http", ignoreCase = true)) {
            items.add(
                ScraperDownloadItem(
                    id = "tt_loc_mp3_${musicUrl.hashCode()}",
                    title = "$title Audio",
                    label = "Audio MP3",
                    url = musicUrl,
                    category = MediaCategory.AUDIO,
                    extension = "mp3",
                    thumbnail = coverThumb,
                    author = author,
                    referer = "https://www.tiktok.com/",
                ),
            )
        }

        if (items.none { it.category == MediaCategory.IMAGE }) return null

        val finalUrl = res.finalUrl.ifBlank { cleanUrl }
        return ScrapeResult(
            platform = PlatformId.TIKTOK,
            serverUsed = "Nimiyo • TikTok Direct (Local)",
            title = title,
            author = author,
            thumbnail = coverThumb.ifBlank { items.first().thumbnail },
            sourceUrl = finalUrl,
            items = items,
            extractorName = "TikTokDirectLocal",
            isPlaylist = items.count { it.category == MediaCategory.IMAGE } > 1,
        )
    }

    fun analyzeTwitterFallback(http: HttpClientFactory, cleanUrl: String): ScrapeResult? {
        val tweetId = Regex("""status(?:es)?/(\d+)""", RegexOption.IGNORE_CASE)
            .find(cleanUrl)?.groupValues?.getOrNull(1) ?: return null
        val res = runCatching {
            http.get("https://api.fxtwitter.com/status/$tweetId")
        }.getOrNull() ?: return null
        if (res.status !in 200..299 || res.body.isBlank()) return null
        val json = runCatching { JSONObject(res.body) }.getOrNull() ?: return null
        if (json.optInt("code") != 200) return null
        val tweet = json.optJSONObject("tweet") ?: return null
        val author = tweet.optJSONObject("author")?.optString("screen_name").orEmpty()
        val title = tweet.optString("text").take(80).ifBlank { "X Post $tweetId" }
        NimiyoContentGuard.checkTextCompliance(title, author, cleanUrl)
        val media = tweet.optJSONObject("media") ?: return null
        val items = mutableListOf<ScraperDownloadItem>()
        var thumb = ""

        val videos = media.optJSONArray("videos")
        if (videos != null) {
            for (i in 0 until videos.length()) {
                val v = videos.optJSONObject(i) ?: continue
                val vUrl = v.optString("url")
                val vThumb = v.optString("thumbnail_url")
                if (thumb.isBlank()) thumb = vThumb
                if (vUrl.startsWith("http", ignoreCase = true)) {
                    items.add(
                        ScraperDownloadItem(
                            id = "tw_vid_${i}_${vUrl.hashCode()}",
                            title = title,
                            label = "Video MP4 ${v.optInt("width")}x${v.optInt("height")}",
                            url = vUrl,
                            category = MediaCategory.VIDEO,
                            extension = "mp4",
                            thumbnail = vThumb,
                            author = author,
                        ),
                    )
                }
            }
        }
        val photos = media.optJSONArray("photos")
        if (photos != null) {
            for (i in 0 until photos.length()) {
                val p = photos.optJSONObject(i) ?: continue
                val pUrl = p.optString("url")
                if (thumb.isBlank()) thumb = pUrl
                if (pUrl.startsWith("http", ignoreCase = true)) {
                    val ext = inferMediaExtension(pUrl, "jpg")
                    items.add(
                        ScraperDownloadItem(
                            id = "tw_img_${i}_${pUrl.hashCode()}",
                            title = if (photos.length() > 1) "$title (${i + 1})" else title,
                            label = if (photos.length() > 1) "Photo ${i + 1} (${ext.uppercase(Locale.US)})" else "Photo (${ext.uppercase(Locale.US)})",
                            url = pUrl,
                            category = MediaCategory.IMAGE,
                            extension = ext,
                            thumbnail = pUrl,
                            author = author,
                        ),
                    )
                }
            }
        }
        if (items.isEmpty()) return null
        return ScrapeResult(
            platform = PlatformId.TWITTER,
            serverUsed = "Nimiyo • X Direct",
            title = title,
            author = author,
            thumbnail = thumb,
            sourceUrl = cleanUrl,
            items = items,
            extractorName = "FxTwitter",
            isPlaylist = items.size > 1,
        )
    }

    private fun extractJsonObjectAfterKey(text: String, key: String): JSONObject? {
        val idx = text.indexOf("\"$key\"")
        if (idx < 0) return null
        val braceStart = text.indexOf('{', idx + key.length + 2)
        if (braceStart < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in braceStart until text.length) {
            val ch = text[i]
            if (escaped) {
                escaped = false
                continue
            }
            if (ch == '\\') {
                escaped = true
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
                    if (depth == 0) {
                        return runCatching { JSONObject(text.substring(braceStart, i + 1)) }.getOrNull()
                    }
                }
            }
        }
        return null
    }

    fun analyzeInstagramFallback(http: HttpClientFactory, cleanUrl: String): ScrapeResult? {
        var targetUrl = cleanUrl
        if (targetUrl.contains("/share/", ignoreCase = true)) {
            val resolved = runCatching {
                http.get(
                    targetUrl,
                    mapOf("User-Agent" to HttpClientFactory.CHROME_DESKTOP_UA),
                ).finalUrl
            }.getOrNull()
            if (!resolved.isNullOrBlank()) targetUrl = resolved
        }
        val shortcode = Regex("""instagram\.com/(?:p|reel|reels|tv)/([A-Za-z0-9_-]+)""")
            .find(targetUrl)?.groupValues?.getOrNull(1) ?: return null

        val embedUrls = listOf(
            "https://www.instagram.com/p/$shortcode/embed/captioned/",
            "https://www.instagram.com/p/$shortcode/embed/",
        )
        var html = ""
        for (embedUrl in embedUrls) {
            val res = runCatching {
                http.get(
                    embedUrl,
                    mapOf(
                        "User-Agent" to HttpClientFactory.CHROME_DESKTOP_UA,
                        "Referer" to "https://www.instagram.com/",
                    ),
                )
            }.getOrNull()
            if (res != null && res.status in 200..299 && res.body.isNotBlank()) {
                html = res.body
                if (html.contains("shortcode_media") || html.contains("EmbeddedMediaImage")) break
            }
        }
        if (html.isBlank()) return null

        val unescapedHtml = html.replace("\\\"", "\"").replace("\\/", "/")
        val items = mutableListOf<ScraperDownloadItem>()
        var title = "Instagram ($shortcode)"
        var author = ""
        var thumb = ""

        val media = extractJsonObjectAfterKey(html, "shortcode_media")
            ?: extractJsonObjectAfterKey(unescapedHtml, "shortcode_media")
        if (media != null) {
            author = media.optJSONObject("owner")?.optString("username").orEmpty()
            val caption = media.optJSONObject("edge_media_to_caption")
                ?.optJSONArray("edges")
                ?.optJSONObject(0)
                ?.optJSONObject("node")
                ?.optString("text")
                .orEmpty()
            if (caption.isNotBlank()) title = caption.take(80)
            NimiyoContentGuard.checkTextCompliance(title, author, caption, targetUrl)
            thumb = media.optString("display_url")
            val sidecar = media.optJSONObject("edge_sidecar_to_children")?.optJSONArray("edges")
            if (sidecar != null && sidecar.length() > 0) {
                for (i in 0 until sidecar.length()) {
                    val node = sidecar.optJSONObject(i)?.optJSONObject("node") ?: continue
                    val isVid = node.optBoolean("is_video", false)
                    val dlUrl = if (isVid) node.optString("video_url") else node.optString("display_url")
                    if (dlUrl.startsWith("http", ignoreCase = true)) {
                        items.add(
                            ScraperDownloadItem(
                                id = "ig_sc_${i}_${dlUrl.hashCode()}",
                                title = "$title (${i + 1})",
                                label = if (isVid) "Video Slide ${i + 1}" else "Photo Slide ${i + 1}",
                                url = dlUrl,
                                category = if (isVid) MediaCategory.VIDEO else MediaCategory.IMAGE,
                                extension = if (isVid) "mp4" else "jpg",
                                thumbnail = node.optString("display_url").ifBlank { thumb },
                                author = author,
                            ),
                        )
                    }
                }
            } else {
                val isVid = media.optBoolean("is_video", false)
                val dlUrl = if (isVid) media.optString("video_url") else media.optString("display_url")
                if (dlUrl.startsWith("http", ignoreCase = true)) {
                    items.add(
                        ScraperDownloadItem(
                            id = "ig_single_${dlUrl.hashCode()}",
                            title = title,
                            label = if (isVid) "Video MP4" else "Photo (JPG)",
                            url = dlUrl,
                            category = if (isVid) MediaCategory.VIDEO else MediaCategory.IMAGE,
                            extension = if (isVid) "mp4" else "jpg",
                            thumbnail = thumb,
                            author = author,
                        ),
                    )
                }
            }
        }
        if (items.isEmpty()) {
            val doc = Jsoup.parse(html)
            val videoSrc = doc.selectFirst("video source, video")?.attr("src").orEmpty()
            val imgSrc = doc.selectFirst("img.EmbeddedMediaImage")?.attr("src").orEmpty()
            if (videoSrc.startsWith("http", ignoreCase = true)) {
                items.add(
                    ScraperDownloadItem(
                        id = "ig_dom_vid",
                        title = title,
                        label = "Video MP4",
                        url = videoSrc,
                        category = MediaCategory.VIDEO,
                        extension = "mp4",
                        thumbnail = imgSrc,
                    ),
                )
            } else if (imgSrc.startsWith("http", ignoreCase = true)) {
                items.add(
                    ScraperDownloadItem(
                        id = "ig_dom_img",
                        title = title,
                        label = "Photo (JPG)",
                        url = imgSrc,
                        category = MediaCategory.IMAGE,
                        extension = "jpg",
                        thumbnail = imgSrc,
                    ),
                )
            }
        }
        if (items.isEmpty()) return null
        return ScrapeResult(
            platform = PlatformId.INSTAGRAM,
            serverUsed = "Nimiyo • Instagram Direct",
            title = title,
            author = author,
            thumbnail = thumb.ifBlank { items.first().thumbnail },
            sourceUrl = targetUrl,
            items = items,
            extractorName = "InstagramDirect",
            isPlaylist = items.size > 1,
        )
    }

    fun analyzePinterestFallback(http: HttpClientFactory, cleanUrl: String): ScrapeResult? {
        val res = runCatching {
            http.get(
                cleanUrl,
                mapOf(
                    "User-Agent" to HttpClientFactory.CHROME_DESKTOP_UA,
                    "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                ),
            )
        }.getOrNull() ?: return null
        if (res.status !in 200..299 || res.body.isBlank()) return null
        val html = res.body
        val unescaped = html.replace("\\/", "/")
        val doc = Jsoup.parse(html)
        val title = pickFirstMeta(doc, "meta[property=og:title]", "meta[name=og:title]")
            ?.replace(" | Pinterest", "")
            ?.trim()
            ?.ifBlank { doc.title().replace(" | Pinterest", "").trim() }
            .orEmpty()
            .ifBlank { "Pinterest Pin" }
        NimiyoContentGuard.checkTextCompliance(title, cleanUrl)

        val ogImg = pickFirstMeta(doc, "meta[property=og:image]", "meta[name=og:image]")
            ?.takeUnless { isAvatarOrStaticLogoUrl(it) }
            .orEmpty()
        val origImg = if (ogImg.startsWith("http", ignoreCase = true)) {
            ogImg.replace(Regex("""/\d+x(?:_RS)?/"""), "/originals/")
        } else {
            ""
        }

        val items = mutableListOf<ScraperDownloadItem>()
        val seen = HashSet<String>()

        // 1. Direct MP4 video URLs from Pinterest HTML / __PWS_DATA__ / og:video
        val metaVideo = pickFirstMeta(
            doc,
            "meta[property=og:video:secure_url]",
            "meta[property=og:video]",
            "meta[name=og:video]",
        ).orEmpty()
        val mp4Candidates = buildList {
            if (metaVideo.contains(".mp4", ignoreCase = true)) add(metaVideo)
            Regex("""https://(?:v\d*|v)\.pinimg\.com/[^"'\s<>\\]+\.mp4(?:\?[^"'\s<>\\]*)?""")
                .findAll(unescaped)
                .forEach { add(it.value) }
        }.sortedByDescending { url ->
            when {
                url.contains("/720p/", ignoreCase = true) -> 3
                url.contains("/mc/", ignoreCase = true) -> 2
                else -> 1
            }
        }
        mp4Candidates.forEachIndexed { idx, vidUrl ->
            if (seen.add(vidUrl)) {
                items.add(
                    ScraperDownloadItem(
                        id = "pin_vid_$idx",
                        title = title,
                        label = if (idx == 0) "Video HD (MP4)" else "Video (MP4)",
                        url = vidUrl,
                        category = MediaCategory.VIDEO,
                        extension = "mp4",
                        thumbnail = origImg,
                        referer = "https://www.pinterest.com/",
                    ),
                )
            }
        }

        // 2. Fallback to HLS (.m3u8) only if no direct MP4 was found
        if (items.isEmpty()) {
            val hlsCandidates = buildList {
                if (metaVideo.contains(".m3u8", ignoreCase = true)) add(metaVideo)
                Regex("""https://(?:v\d*|v)\.pinimg\.com/[^"'\s<>\\]+\.m3u8(?:\?[^"'\s<>\\]*)?""")
                    .findAll(unescaped)
                    .forEach { add(it.value) }
            }
            hlsCandidates.firstOrNull()?.let { hlsUrl ->
                if (seen.add(hlsUrl)) {
                    items.add(
                        ScraperDownloadItem(
                            id = "pin_hls_0",
                            title = title,
                            label = "Video (HLS)",
                            url = hlsUrl,
                            category = MediaCategory.VIDEO,
                            extension = "mp4",
                            thumbnail = origImg,
                            referer = "https://www.pinterest.com/",
                        ),
                    )
                }
            }
        }

        // 3. Original image (as photo item or cover)
        if (origImg.isNotBlank() && seen.add(origImg)) {
            val ext = inferMediaExtension(origImg, "jpg")
            items.add(
                ScraperDownloadItem(
                    id = "pin_img_${origImg.hashCode()}",
                    title = title,
                    label = "Photo (${ext.uppercase(Locale.US)})",
                    url = origImg,
                    category = MediaCategory.IMAGE,
                    extension = ext,
                    thumbnail = origImg,
                    referer = "https://www.pinterest.com/",
                ),
            )
        }

        if (items.isEmpty()) return null
        return ScrapeResult(
            platform = PlatformId.PINTEREST,
            serverUsed = "Nimiyo • Pinterest Direct",
            title = title,
            thumbnail = origImg.ifBlank { items.first().thumbnail },
            sourceUrl = res.finalUrl.ifBlank { cleanUrl },
            items = items,
            extractorName = "PinterestDirect",
        )
    }

    // -------------------------------------------------------------------------
    // SHARED HELPERS
    // -------------------------------------------------------------------------

    internal fun extractPixivIllustId(rawUrl: String): String? {
        return pixivArtworksRegex.find(rawUrl)?.groupValues?.getOrNull(1)
            ?: pixivLegacyIdRegex.find(rawUrl)?.groupValues?.getOrNull(1)
    }

    internal fun rewriteRedNoteHostToXiaohongshu(rawUrl: String): String {
        return runCatching {
            val uri = URI(rawUrl)
            val host = uri.host?.lowercase(Locale.US).orEmpty()
            if (host.contains("rednote.com") || host.contains("rednoteshare.com")) {
                URI(
                    uri.scheme ?: "https",
                    uri.userInfo,
                    "www.xiaohongshu.com",
                    uri.port,
                    uri.rawPath,
                    uri.rawQuery,
                    uri.rawFragment,
                ).toString()
            } else {
                rawUrl
            }
        }.getOrDefault(rawUrl)
    }

    private inline fun <T> probeAndParseFirst(
        http: HttpClientFactory,
        urls: List<String>,
        parser: (HttpResponseData, String) -> T?,
    ): T? {
        val userAgents = listOf(
            HttpClientFactory.CHROME_DESKTOP_UA,
            HttpClientFactory.SAFARI_MOBILE_UA,
            HttpClientFactory.WHATSAPP_UA,
        )
        for (ua in userAgents) {
            for (u in urls) {
                val res = runCatching {
                    http.get(
                        url = u,
                        headers = mapOf(
                            "User-Agent" to ua,
                            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                            "Accept-Language" to "en-US,en;q=0.9,id;q=0.8",
                        ),
                    )
                }.getOrNull() ?: continue
                val finalUrl = res.finalUrl.ifBlank { u }
                NimiyoContentGuard.checkUrlBeforeAnalyze(finalUrl)
                if (res.body.isNotBlank() || res.status >= 400) {
                    val parsed = parser(res, finalUrl)
                    if (parsed != null) return parsed
                }
            }
        }
        return null
    }

    private fun collectThreadsJsonMedia(
        node: Any?,
        videoUrls: MutableSet<String>,
        imageUrls: MutableSet<String>,
        onMeta: (caption: String, username: String) -> Unit,
    ) {
        when (node) {
            is JSONObject -> {
                val captionText = node.optJSONObject("caption")?.optString("text").orEmpty().trim()
                val username = node.optJSONObject("user")?.optString("username").orEmpty().trim()
                if (captionText.isNotEmpty() || username.isNotEmpty()) {
                    onMeta(captionText, username)
                }
                node.optJSONArray("video_versions")?.let { vids ->
                    var bestUrl: String? = null
                    var bestScore = -1L
                    for (i in 0 until vids.length()) {
                        val vo = vids.optJSONObject(i) ?: continue
                        val url = normalizeMediaUrl(vo.optString("url")) ?: continue
                        val score = vo.optLong("width", 0L) * vo.optLong("height", 0L)
                        if (bestUrl == null || score > bestScore) {
                            bestUrl = url
                            bestScore = score
                        }
                    }
                    bestUrl?.let { videoUrls.add(it) }
                }
                node.optJSONObject("image_versions2")?.optJSONArray("candidates")?.let { imgs ->
                    val best = imgs.optJSONObject(0)?.optString("url").orEmpty()
                    normalizeMediaUrl(best)?.let { imageUrls.add(it) }
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    if (k == "reply_threads" || k == "related_threads" || k == "recommended_threads" || k == "replies") {
                        continue
                    }
                    collectThreadsJsonMedia(node.opt(k), videoUrls, imageUrls, onMeta)
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    collectThreadsJsonMedia(node.opt(i), videoUrls, imageUrls, onMeta)
                }
            }
        }
    }

    private fun extractShopeeVideoLinks(rawText: String, out: MutableSet<String>) {
        shopeeVideoKeysRegex.findAll(rawText).forEach { m ->
            normalizeMediaUrl(m.groupValues[1])?.let { out.add(it) }
        }
        mp4CdnRegex.findAll(rawText).forEach { m ->
            val url = normalizeMediaUrl(m.groupValues[1]) ?: return@forEach
            val lower = url.lowercase(Locale.US)
            if (lower.contains("shopee") || lower.contains("susercontent") || lower.contains("vod")) {
                out.add(url)
            }
        }
    }

    private fun pickFirstMeta(doc: Document, vararg selectors: String): String? {
        for (sel in selectors) {
            val content = doc.selectFirst(sel)?.attr("content")?.trim()
            if (!content.isNullOrBlank()) return content
        }
        return null
    }

    private fun extractHandleFromTitleOrUrl(title: String, url: String): String {
        val m = Regex("""@([A-Za-z0-9._]+)""").find("$url $title")
        return m?.groupValues?.getOrNull(1)?.let { "@$it" }.orEmpty()
    }

    private fun extractLoginRedirectTarget(finalUrl: String): String? {
        if (!finalUrl.contains("/login", ignoreCase = true)) return null
        val match = Regex("""[?&]redirectPath=([^&]+)""", RegexOption.IGNORE_CASE).find(finalUrl) ?: return null
        val decoded = ScraperUtils.urlDecode(match.groupValues[1]).trim()
        return decoded.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
    }

    private fun extractFromInitialState(root: JSONObject): JSONObject? {
        val noteObj = root.optJSONObject("note")
        val detailMap = noteObj?.optJSONObject("noteDetailMap")
        if (detailMap != null) {
            val keys = detailMap.keys()
            while (keys.hasNext()) {
                val entry = detailMap.optJSONObject(keys.next())
                val inner = entry?.optJSONObject("note")
                if (inner != null && inner.length() > 0) return inner
            }
        }
        val noteData = root.optJSONObject("noteData")?.optJSONObject("data")?.optJSONObject("noteData")
        if (noteData != null && noteData.length() > 0) return noteData
        return null
    }

    private fun extractRedNoteVideoUrls(videoObj: JSONObject?, out: MutableSet<String>) {
        if (videoObj == null) return
        val stream = videoObj.optJSONObject("media")?.optJSONObject("stream")
        if (stream != null) {
            listOf("h264", "h265", "av1").forEach { codec ->
                val arr = stream.optJSONArray(codec) ?: return@forEach
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    normalizeMediaUrl(item.optString("masterUrl"))?.let { out.add(it) }
                    val backups = item.optJSONArray("backupUrls")
                    if (backups != null) {
                        for (j in 0 until backups.length()) {
                            normalizeMediaUrl(backups.optString(j))?.let { out.add(it) }
                        }
                    }
                }
            }
        }
        val originKey = videoObj.optJSONObject("consumer")?.optString("originVideoKey").orEmpty().trim()
        if (originKey.isNotBlank()) {
            out.add("https://sns-video-bd.xhscdn.com/${originKey.trimStart('/')}")
        }
    }

    private fun extractRedNoteImageUrls(imageList: JSONArray?, out: MutableSet<String>) {
        if (imageList == null) return
        for (i in 0 until imageList.length()) {
            val img = imageList.optJSONObject(i) ?: continue
            var candidate = img.optString("urlDefault").ifBlank { img.optString("url") }
            if (candidate.isBlank()) {
                val infoList = img.optJSONArray("infoList")
                if (infoList != null && infoList.length() > 0) {
                    candidate = infoList.optJSONObject(infoList.length() - 1)?.optString("url").orEmpty()
                }
            }
            if (candidate.isBlank()) candidate = img.optString("urlPre")
            normalizeMediaUrl(candidate)?.let { out.add(it) }
        }
    }

    private fun extractPixivTags(illustBody: JSONObject): List<String> {
        val tagArr = illustBody.optJSONObject("tags")?.optJSONArray("tags") ?: return emptyList()
        return buildList {
            for (i in 0 until tagArr.length()) {
                val tag = tagArr.optJSONObject(i)?.optString("tag").orEmpty().trim()
                if (tag.isNotEmpty()) add(tag)
            }
        }
    }

    private fun unescapeJsonUrl(raw: String): String =
        raw.replace("\\u002F", "/", ignoreCase = true)
            .replace("\\/", "/")
            .replace("&amp;", "&")
            .trim()

    private fun normalizeMediaUrl(raw: String, keepHttpIfOriginal: Boolean = false): String? {
        val cleaned = unescapeJsonUrl(raw)
        if (cleaned.isBlank()) return null
        val withScheme = when {
            cleaned.startsWith("//") -> "https:$cleaned"
            cleaned.startsWith("http://", true) -> if (keepHttpIfOriginal) cleaned else "https://${cleaned.removePrefix("http://").removePrefix("HTTP://")}"
            cleaned.startsWith("https://", true) -> cleaned
            else -> return null
        }
        return withScheme.takeIf { it.startsWith("https://") || it.startsWith("http://") }
    }

    private fun inferMediaExtension(url: String, defaultExt: String): String {
        val path = url.substringBefore('?').substringBefore('#').lowercase(Locale.US)
        return when {
            path.endsWith(".png") -> "png"
            path.endsWith(".webp") -> "webp"
            path.endsWith(".gif") -> "gif"
            path.endsWith(".jpg") || path.endsWith(".jpeg") -> "jpg"
            path.endsWith(".mp4") -> "mp4"
            else -> defaultExt
        }
    }

    private fun isAvatarOrStaticLogoUrl(url: String): Boolean {
        val lower = url.lowercase(Locale.US)
        return lower.contains("t51.2885-19") ||
            lower.contains("s150x150") ||
            lower.contains("profile_pic") ||
            lower.contains("rsrc.php") ||
            lower.contains("static.cdninstagram") ||
            lower.contains("/favicon") ||
            lower.contains("logo") ||
            lower.contains("avatar")
    }
}
