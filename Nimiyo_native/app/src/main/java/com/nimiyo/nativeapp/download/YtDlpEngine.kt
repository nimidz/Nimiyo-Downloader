package com.nimiyo.nativeapp.download

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaScannerConnection
import com.nimiyo.nativeapp.core.AppSettings
import com.nimiyo.nativeapp.core.CookieVaultManager
import com.nimiyo.nativeapp.core.DownloadSubfolderMode
import com.nimiyo.nativeapp.core.HttpClientFactory
import com.nimiyo.nativeapp.core.YtDlpUpdateChannel
import com.nimiyo.nativeapp.data.DownloadTask
import com.nimiyo.nativeapp.data.MediaCategory
import com.nimiyo.nativeapp.data.MediaStoreRepository
import com.nimiyo.nativeapp.data.PlatformId
import com.nimiyo.nativeapp.data.ScrapeResult
import com.nimiyo.nativeapp.data.ScraperDownloadItem
import com.nimiyo.nativeapp.scraper.NimiyoContentGuard
import com.nimiyo.nativeapp.scraper.PlatformDetector
import com.nimiyo.nativeapp.scraper.PolicyViolationCategory
import com.nimiyo.nativeapp.scraper.PolicyViolationException
import com.nimiyo.nativeapp.scraper.RedNoteProbeResult
import com.nimiyo.nativeapp.scraper.ScraperUtils
import com.nimiyo.nativeapp.scraper.SocialPlatformScrapers
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

sealed class YtDlpUpdateResult {
    data class Updated(val version: String) : YtDlpUpdateResult()
    data class UpToDate(val version: String) : YtDlpUpdateResult()
    data class Failed(val message: String) : YtDlpUpdateResult()
}

class YtDlpEngine(
    private val appContext: Context,
    private val settings: AppSettings,
    private val http: HttpClientFactory,
    private val mediaRepo: MediaStoreRepository,
    private val cookieVault: CookieVaultManager? = null,
) {
    private val coreInitMutex = Mutex()
    private val toolsInitMutex = Mutex()
    private val updateMutex = Mutex()
    private val analyzeCache = ConcurrentHashMap<String, Pair<Long, ScrapeResult>>()
    private val activeAttemptProcIds = ConcurrentHashMap<String, String>()
    private val metaPrefs by lazy {
        appContext.getSharedPreferences("nimiyo_ytdlp_meta", Context.MODE_PRIVATE)
    }

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _initError = MutableStateFlow<String?>(null)
    val initError: StateFlow<String?> = _initError.asStateFlow()

    private val _ytDlpVersion = MutableStateFlow("")
    val ytDlpVersion: StateFlow<String> = _ytDlpVersion.asStateFlow()

    private val _isUpdating = MutableStateFlow(false)
    val isUpdating: StateFlow<Boolean> = _isUpdating.asStateFlow()

    private val _aria2cAvailable = MutableStateFlow(false)
    val aria2cAvailable: StateFlow<Boolean> = _aria2cAvailable.asStateFlow()

    suspend fun ensureCoreInitialized() = withContext(Dispatchers.IO) {
        if (_isInitialized.value) return@withContext
        coreInitMutex.withLock {
            if (_isInitialized.value) return@withContext
            try {
                _initError.value = null
                YoutubeDL.getInstance().init(appContext)
                val storedVer = readStoredVersion()
                if (storedVer.isNotBlank()) {
                    _ytDlpVersion.value = storedVer
                }
                _isInitialized.value = true
            } catch (e: Throwable) {
                _initError.value = e.message?.ifBlank { null } ?: e.javaClass.simpleName
                throw e
            }
        }
    }

    suspend fun ensureMediaToolsInitialized() = withContext(Dispatchers.IO) {
        if (_toolsInitialized) return@withContext
        toolsInitMutex.withLock {
            if (_toolsInitialized) return@withContext
            FFmpeg.getInstance().init(appContext)
            val ariaOk = runCatching {
                Aria2c.getInstance().init(appContext)
                true
            }.getOrDefault(false)
            _aria2cAvailable.value = ariaOk
            _toolsInitialized = true
        }
    }

    @Volatile
    private var _toolsInitialized: Boolean = false

    suspend fun ensureInitialized() {
        ensureCoreInitialized()
        ensureMediaToolsInitialized()
    }

    private fun readStoredVersion(): String {
        val dlVer = runCatching { YoutubeDL.getInstance().version(appContext).orEmpty().trim() }.getOrDefault("")
        if (dlVer.isNotBlank()) return dlVer
        val dlVerName = runCatching { YoutubeDL.getInstance().versionName(appContext).orEmpty().trim() }.getOrDefault("")
        if (dlVerName.isNotBlank()) return dlVerName
        return metaPrefs.getString("cached_version", "").orEmpty().trim()
    }

    private fun probeAndCacheVersion(): String {
        val probed = runCatching {
            val req = YoutubeDLRequest(emptyList<String>()).apply {
                addOption("--version")
            }
            YoutubeDL.getInstance().execute(req, "ytdlp_probe_ver", null)
                .out
                .orEmpty()
                .lineSequence()
                .map { it.trim() }
                .firstOrNull { it.isNotBlank() }
                .orEmpty()
        }.getOrDefault("")
        if (probed.isNotBlank()) {
            metaPrefs.edit().putString("cached_version", probed).apply()
            _ytDlpVersion.value = probed
        }
        return probed
    }

    suspend fun refreshVersion(): String = withContext(Dispatchers.IO) {
        runCatching {
            ensureCoreInitialized()
            val ver = readStoredVersion().ifBlank { probeAndCacheVersion() }
            if (ver.isNotBlank()) {
                _ytDlpVersion.value = ver
            }
            ver
        }.getOrDefault(_ytDlpVersion.value)
    }

    suspend fun updateYtDlp(
        channel: YtDlpUpdateChannel = settings.current.ytDlpChannel,
    ): YtDlpUpdateResult = withContext(Dispatchers.IO) {
        updateMutex.withLock {
            _isUpdating.value = true
            try {
                ensureCoreInitialized()
                val dlChannel = when (channel) {
                    YtDlpUpdateChannel.STABLE -> YoutubeDL.UpdateChannel.STABLE
                    YtDlpUpdateChannel.NIGHTLY -> YoutubeDL.UpdateChannel.NIGHTLY
                }
                val status = YoutubeDL.getInstance().updateYoutubeDL(appContext, dlChannel)
                val ver = readStoredVersion().ifBlank { probeAndCacheVersion() }
                if (ver.isNotBlank()) {
                    metaPrefs.edit().putString("cached_version", ver).apply()
                    _ytDlpVersion.value = ver
                }
                when (status) {
                    YoutubeDL.UpdateStatus.DONE -> YtDlpUpdateResult.Updated(ver)
                    YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> YtDlpUpdateResult.UpToDate(ver)
                    else -> YtDlpUpdateResult.Failed("Update returned unexpected status")
                }
            } catch (e: Throwable) {
                YtDlpUpdateResult.Failed(e.message?.ifBlank { null } ?: "Failed to update yt-dlp")
            } finally {
                _isUpdating.value = false
            }
        }
    }

    fun cancelProcess(processId: String) {
        activeAttemptProcIds.remove(processId)?.let { execId ->
            runCatching { YoutubeDL.getInstance().destroyProcessById(execId) }
        }
        runCatching {
            YoutubeDL.getInstance().destroyProcessById(processId)
        }
    }

    suspend fun analyze(
        rawUrl: String,
        overridePlatform: PlatformId? = null,
        processId: String = "analyze_${UUID.randomUUID()}",
    ): ScrapeResult = withContext(Dispatchers.IO) {
        val cleanUrl = ScraperUtils.cleanUrl(rawUrl)
        if (!cleanUrl.startsWith("http://", ignoreCase = true) &&
            !cleanUrl.startsWith("https://", ignoreCase = true)
        ) {
            throw IllegalArgumentException("Please enter a valid http/https link")
        }

        // Layer 1: Block prohibited adult / NSFW / extreme gore domains & paths before running yt-dlp
        NimiyoContentGuard.checkUrlBeforeAnalyze(cleanUrl)

        val cfg = settings.current
        val cacheKey = if (overridePlatform == null) {
            "$cleanUrl|${cfg.cookiesEnabled}|${cookieVault?.revision ?: 0L}|${cfg.customUserAgent}|${cfg.bypassSsl}|${cfg.forceIpv4}"
        } else null
        if (cacheKey != null) {
            val cached = analyzeCache[cacheKey]
            if (cached != null && (System.currentTimeMillis() - cached.first) < 120_000L) {
                return@withContext cached.second
            }
        }

        val detected = overridePlatform ?: PlatformDetector.detect(cleanUrl) ?: PlatformId.OTHER

        fun cacheAndReturn(res: ScrapeResult): ScrapeResult {
            if (cacheKey != null) {
                if (analyzeCache.size > 32) analyzeCache.clear()
                analyzeCache[cacheKey] = System.currentTimeMillis() to res
            }
            return res
        }

        // Direct scrapers for platforms yt-dlp does not support or redirects regionally
        when (detected) {
            PlatformId.PIXIV -> return@withContext cacheAndReturn(SocialPlatformScrapers.analyzePixiv(http, cleanUrl))
            PlatformId.THREADS -> return@withContext cacheAndReturn(SocialPlatformScrapers.analyzeThreads(http, cleanUrl))
            PlatformId.SHOPEE -> return@withContext cacheAndReturn(SocialPlatformScrapers.analyzeShopee(http, cleanUrl))
            PlatformId.PINTEREST -> {
                runCatching { SocialPlatformScrapers.analyzePinterestFallback(http, cleanUrl) }
                    .getOrNull()
                    ?.let { return@withContext cacheAndReturn(it) }
            }
            PlatformId.TIKTOK -> {
                if (cleanUrl.contains("/photo/", ignoreCase = true)) {
                    return@withContext cacheAndReturn(SocialPlatformScrapers.analyzeTikTokPhoto(http, cleanUrl))
                }
            }
            else -> Unit
        }
        var effectiveAnalyzeUrl = cleanUrl
        if (detected == PlatformId.REDNOTE) {
            when (val probe = SocialPlatformScrapers.probeRedNote(http, cleanUrl)) {
                is RedNoteProbeResult.Resolved -> return@withContext cacheAndReturn(probe.result)
                is RedNoteProbeResult.FallbackToYtDlp -> effectiveAnalyzeUrl = probe.normalizedUrl
            }
        }

        // Spotify & Apple Music links are DRM catalogs: extract track/playlist metadata and route via yt-dlp search
        if (detected == PlatformId.SPOTIFY) {
            return@withContext cacheAndReturn(analyzeSpotifyViaYtDlp(cleanUrl))
        }
        if (detected == PlatformId.APPLE_MUSIC) {
            return@withContext cacheAndReturn(analyzeAppleMusicViaYtDlp(cleanUrl))
        }

        ensureCoreInitialized()

        val request = YoutubeDLRequest(effectiveAnalyzeUrl).apply {
            addOption("--flat-playlist")
            addOption("--dump-single-json")
            addOption("--no-warnings")
            addOption("--no-check-formats")
            applyCommonNetworkOptions(this)
        }

        val response = try {
            YoutubeDL.getInstance().execute(request, processId, null)
        } catch (e: Throwable) {
            if (e is PolicyViolationException) throw e
            val mapped = mapAgeGateErrorIfNeeded(e)
            val errLower = mapped.message.orEmpty().lowercase(Locale.US)
            if (detected == PlatformId.INSTAGRAM) {
                if (!cleanUrl.contains("/stories/", ignoreCase = true)) {
                    runCatching { SocialPlatformScrapers.analyzeInstagramFallback(http, cleanUrl) }
                        .getOrNull()
                        ?.let { return@withContext cacheAndReturn(it) }
                }
                if (cleanUrl.contains("/stories/", ignoreCase = true) ||
                    errLower.contains("login") ||
                    errLower.contains("cookies") ||
                    errLower.contains("you need to log in")
                ) {
                    val hint = if (settings.current.cookiesEnabled) {
                        "Pastikan Anda sudah login akun instagram.com di Pengaturan Cookie."
                    } else {
                        "Aktifkan & login instagram.com di Pengaturan Cookie agar yt-dlp dapat mengunduhnya."
                    }
                    throw IllegalStateException("Instagram Story / konten privat memerlukan sesi login. $hint")
                }
            }
            if (detected == PlatformId.REDNOTE &&
                errLower.contains("unsupported url")
            ) {
                throw IllegalStateException(
                    "Postingan RedNote ini memerlukan sesi login di Pengaturan Cookie atau sedang dibatasi anti-bot."
                )
            }
            // Fallback for photo/carousel posts that yt-dlp cannot extract
            when (detected) {
                PlatformId.TIKTOK -> {
                    runCatching { SocialPlatformScrapers.analyzeTikTokPhoto(http, cleanUrl) }
                        .getOrNull()
                        ?.let { return@withContext cacheAndReturn(it) }
                }
                PlatformId.TWITTER -> {
                    runCatching { SocialPlatformScrapers.analyzeTwitterFallback(http, cleanUrl) }
                        .getOrNull()
                        ?.let { return@withContext cacheAndReturn(it) }
                }
                PlatformId.INSTAGRAM -> {
                    runCatching { SocialPlatformScrapers.analyzeInstagramFallback(http, cleanUrl) }
                        .getOrNull()
                        ?.let { return@withContext cacheAndReturn(it) }
                }
                PlatformId.PINTEREST -> {
                    runCatching { SocialPlatformScrapers.analyzePinterestFallback(http, cleanUrl) }
                        .getOrNull()
                        ?.let { return@withContext cacheAndReturn(it) }
                }
                else -> Unit
            }
            throw mapped
        }
        val rawOut = response.out.orEmpty()
        val jsonStart = rawOut.indexOf('{')
        val jsonEnd = rawOut.lastIndexOf('}')
        if (jsonStart < 0 || jsonEnd <= jsonStart) {
            throw IllegalStateException("yt-dlp did not return media metadata")
        }

        val json = JSONObject(rawOut.substring(jsonStart, jsonEnd + 1))
        // Layer 2 & 3: Enforce extractor blocklist, age_limit >= 18, over_18/is_nsfw, and 4-rule metadata scan
        NimiyoContentGuard.checkMetadata(json)

        val extractorKey = json.optString("extractor_key")
            .ifBlank { json.optString("extractor") }
            .ifBlank { detected.displayName }
        val resolvedPlatform = overridePlatform ?: mapExtractorToPlatform(extractorKey, cleanUrl)
        val engineLabel = "yt-dlp • $extractorKey"

        val entries = json.optJSONArray("entries")
        if (entries != null && entries.length() > 0) {
            return@withContext cacheAndReturn(
                buildPlaylistResult(
                    json = json,
                    entries = entries,
                    platform = resolvedPlatform,
                    extractorKey = extractorKey,
                    engineLabel = engineLabel,
                    sourceUrl = cleanUrl,
                )
            )
        }

        val singleResult = buildSingleMediaResult(
            json = json,
            platform = resolvedPlatform,
            extractorKey = extractorKey,
            engineLabel = engineLabel,
            sourceUrl = cleanUrl,
        )
        if (resolvedPlatform == PlatformId.TIKTOK &&
            (singleResult.sourceUrl.contains("/photo/", ignoreCase = true) ||
                singleResult.items.none { it.category == MediaCategory.VIDEO })
        ) {
            runCatching { SocialPlatformScrapers.analyzeTikTokPhoto(http, cleanUrl) }
                .getOrNull()
                ?.takeIf { it.items.any { item -> item.category == MediaCategory.IMAGE } }
                ?.let { return@withContext cacheAndReturn(it) }
        }
        cacheAndReturn(singleResult)
    }

    suspend fun download(
        task: DownloadTask,
        onProgress: (Int, Long, Long) -> Unit,
        isCancelled: () -> Boolean,
    ): Triple<String, String, Long> = withContext(Dispatchers.IO) {
        ensureInitialized()
        val cfg = settings.current
        var targetUrl = task.ytdlpTargetUrl.ifBlank { task.sourceUrl }.ifBlank { task.url }
        if (targetUrl.isBlank()) {
            throw IllegalStateException("Missing target URL for yt-dlp")
        }
        val isCatalogMatch = task.platform == PlatformId.SPOTIFY || task.platform == PlatformId.APPLE_MUSIC
        val catalogMeta = if (isCatalogMatch) {
            enrichCatalogMetadata(task.title, task.author, task.thumbnail)
        } else {
            CatalogMeta(task.author, "", task.thumbnail, 0L)
        }
        val resolvedArtist = catalogMeta.artist
        val resolvedAlbum = catalogMeta.album
        val resolvedThumb = catalogMeta.thumb
        if (isCatalogMatch && targetUrl.startsWith("ytsearch", ignoreCase = true)) {
            resolveOfficialYouTubeAudioUrl(task.title, resolvedArtist, catalogMeta.durationMs)?.let { officialUrl ->
                targetUrl = officialUrl
            }
        }
        NimiyoContentGuard.checkUrlBeforeAnalyze(targetUrl)
        NimiyoContentGuard.checkTextCompliance(task.title, resolvedArtist)

        val execProcId = "${task.id}_${System.nanoTime()}"
        activeAttemptProcIds[task.id] = execProcId
        val tempDir = File(appContext.cacheDir, "ytdlp_dl_$execProcId").apply { mkdirs() }
        try {
            val isSearchTarget = targetUrl.startsWith("ytsearch", ignoreCase = true)
            val request = YoutubeDLRequest(targetUrl).apply {
                addOption("--no-playlist")
                addOption("--age-limit", "17")
                addOption("-o", "${tempDir.absolutePath}/nimiyo_out.%(ext)s")
                addOption("--no-mtime")
                addOption("--no-warnings")
                addOption("--concurrent-fragments", "4")
                applyCommonNetworkOptions(this, includeCookies = !isSearchTarget)

                if (cfg.useAria2c && _aria2cAvailable.value) {
                    // Only use aria2c for plain HTTP; HLS/DASH segments must stay on yt-dlp native downloader to avoid corrupt/blank muxes
                    addOption("--downloader", "http:libaria2c")
                    addOption("--external-downloader-args", "http:aria2c:--summary-interval=1")
                }

                if (task.ytdlpAudioFormat.isNotBlank()) {
                    val fmt = task.ytdlpFormatSelector.ifBlank { "ba/best" }
                    addOption("-f", fmt)
                    addOption("-x")
                    addOption("--audio-format", task.ytdlpAudioFormat)
                    addOption("--audio-quality", task.ytdlpAudioQuality.ifBlank { "0" })
                    if (cfg.embedMetadata && !isCatalogMatch) addOption("--embed-metadata")
                    if (cfg.embedThumbnail && !isCatalogMatch) addOption("--embed-thumbnail")
                    if (isCatalogMatch && (resolvedArtist.isNotBlank() || resolvedAlbum.isNotBlank())) {
                        val safeArtist = resolvedArtist.replace("\"", "").trim()
                        val safeTitle = task.title.replace("\"", "").trim()
                        val safeAlbum = resolvedAlbum.replace("\"", "").trim()
                        val metaArgs = buildString {
                            append("ffmpeg:-metadata title=\"$safeTitle\"")
                            if (safeArtist.isNotEmpty()) append(" -metadata artist=\"$safeArtist\"")
                            if (safeAlbum.isNotEmpty()) append(" -metadata album=\"$safeAlbum\"")
                        }
                        addOption("--postprocessor-args", metaArgs)
                    }
                } else {
                    val fmt = task.ytdlpFormatSelector.ifBlank { buildVideoFormatSelector(null) }
                    val container = if (cfg.ytDlpVideoContainer.equals("mkv", ignoreCase = true)) {
                        "mkv"
                    } else {
                        task.ytdlpContainer.ifBlank { "mp4" }
                    }
                    addOption("-f", fmt)
                    addOption("-S", "vcodec:h264,res,acodec:aac")
                    addOption("--merge-output-format", container)
                    if (cfg.embedMetadata) addOption("--embed-metadata")
                    if (cfg.embedThumbnail) addOption("--embed-thumbnail")
                    if (cfg.ytDlpSubtitlesEnabled) {
                        val langs = cfg.ytDlpSubtitleLangs.trim()
                            .replace(Regex("[^a-zA-Z0-9,_*-]"), "")
                            .ifBlank { "id,en" }
                        addOption("--write-subs")
                        addOption("--write-auto-subs")
                        addOption("--sub-langs", langs)
                        addOption("--sub-format", "srt/best")
                        if (cfg.ytDlpEmbedSubtitles) {
                            addOption("--embed-subs")
                        }
                    }
                    if (cfg.ytDlpSponsorBlockEnabled) {
                        addOption("--sponsorblock-remove", "sponsor,selfpromo,interaction")
                    }
                    if (cfg.ytDlpSplitChapters) {
                        addOption("--split-chapters")
                    }
                }
                applySafeCustomArgs(this, cfg.ytDlpCustomArgs)
            }

            var lastReportedPct = -1
            val dlResponse = try {
                YoutubeDL.getInstance().execute(request, execProcId) { progress, _, _ ->
                    if (isCancelled()) {
                        cancelProcess(task.id)
                        return@execute
                    }
                    if (progress < 0f) return@execute
                    val pct = progress.toInt().coerceIn(0, 98)
                    if (pct > lastReportedPct) {
                        lastReportedPct = pct
                        onProgress(pct, 0L, -1L)
                    }
                }
            } catch (e: Throwable) {
                throw mapAgeGateErrorIfNeeded(e)
            }

            if (isCancelled()) throw InterruptedException("Cancelled by user")

            val outputFile = findDownloadedMediaFile(tempDir, task.extension)
                ?: run {
                    val combinedLog = "${dlResponse.out.orEmpty()}\n${dlResponse.err.orEmpty()}"
                    if (isAgeRestrictionSignal(combinedLog)) {
                        throw PolicyViolationException(
                            PolicyViolationCategory.NSFW_18,
                            "Diblokir oleh Peraturan NIMIYO [Pornografi & NSFW (18+)]: Video berbatas usia 18+ ditolak.",
                        )
                    }
                    throw IllegalStateException("yt-dlp finished but merged output file was not created")
                }

            if (task.category == MediaCategory.VIDEO) {
                validateVideoTrackReadable(outputFile)
            }

            val actualExt = outputFile.extension.lowercase(Locale.US).ifBlank { task.extension }
            if (isCatalogMatch &&
                actualExt == "mp3" &&
                (resolvedArtist.isNotBlank() || resolvedThumb.isNotBlank() || task.title.isNotBlank())
            ) {
                val (coverBytes, coverMime) = fetchCoverBytes(resolvedThumb)
                runCatching {
                    Mp3Id3Tagger.tagFileInPlaceIfNeeded(
                        mp3File = outputFile,
                        title = task.title,
                        artist = resolvedArtist,
                        album = resolvedAlbum,
                        coverBytes = coverBytes,
                        coverMime = coverMime,
                        forceOverwrite = true,
                    )
                }
            }
            val actualSize = outputFile.length()
            val baseName = task.filename.substringBeforeLast(".")
            val mime = resolveMimeType(task.category, actualExt)

            onProgress(99, actualSize, actualSize)
            val customSub = if (cfg.downloadSubfolderMode == DownloadSubfolderMode.BY_PLATFORM) {
                task.platform.displayName
            } else null
            val (localPath, uriStr) = outputFile.inputStream().use { inp ->
                mediaRepo.saveStreamToDownloads(
                    baseName = baseName,
                    extension = actualExt,
                    category = task.category,
                    mimeType = mime,
                    totalBytes = actualSize,
                    inputStream = inp,
                    overwriteMode = cfg.overwriteMode,
                    customTreeUri = cfg.effectiveTreeUriFor(task.category),
                    customSubfolder = customSub,
                    onProgress = { _, _ -> },
                    isCancelled = isCancelled,
                )
            }

            if (localPath.isNotBlank() && (task.category == MediaCategory.AUDIO || task.category == MediaCategory.VIDEO)) {
                runCatching {
                    MediaScannerConnection.scanFile(appContext, arrayOf(localPath), arrayOf(mime), null)
                }
            }

            onProgress(100, actualSize, actualSize)
            Triple(localPath, uriStr, actualSize)
        } finally {
            activeAttemptProcIds.remove(task.id, execProcId)
            runCatching { tempDir.deleteRecursively() }
        }
    }

    private fun applyCommonNetworkOptions(request: YoutubeDLRequest, includeCookies: Boolean = true) {
        val cfg = settings.current
        if (cfg.bypassSsl) request.addOption("--no-check-certificates")
        if (cfg.forceIpv4) request.addOption("--force-ipv4")
        request.addOption("--socket-timeout", cfg.timeoutSeconds.coerceIn(5, 180).toString())
        val customUa = cfg.customUserAgent.trim()
        if (customUa.isNotBlank()) {
            request.addOption("--user-agent", customUa)
        }
        if (includeCookies) {
            cookieVault?.activeCookiesFile(cfg.cookiesEnabled)?.let { cookieFile ->
                request.addOption("--cookies", cookieFile.absolutePath)
            }
        }
    }

    private fun applySafeCustomArgs(request: YoutubeDLRequest, rawArgs: String) {
        val tokens = tokenizeCustomArgs(rawArgs)
        val blockedWithVal = setOf(
            "-o", "--output", "-p", "--paths", "--exec", "--ffmpeg-location",
            "--config-location", "--batch-file", "-a", "--download-archive",
        )
        val blockedSolo = setOf(
            "--dump-single-json", "--dump-json", "-j", "--print-json", "--simulate", "-s",
        )
        var i = 0
        while (i < tokens.size) {
            val tok = tokens[i]
            val lower = tok.lowercase(Locale.US)
            val flagName = lower.substringBefore('=')
            when {
                flagName in blockedSolo -> {
                    i++
                }
                flagName in blockedWithVal -> {
                    i += if (!lower.contains('=') && i + 1 < tokens.size && !tokens[i + 1].startsWith("-")) 2 else 1
                }
                tok.startsWith("-") && i + 1 < tokens.size && !tokens[i + 1].startsWith("-") -> {
                    request.addOption(tok, tokens[i + 1])
                    i += 2
                }
                tok.startsWith("-") -> {
                    request.addOption(tok)
                    i++
                }
                else -> i++
            }
        }
    }

    private fun tokenizeCustomArgs(raw: String): List<String> {
        val result = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes: Char? = null
        for (ch in raw.trim()) {
            when {
                inQuotes != null -> {
                    if (ch == inQuotes) {
                        inQuotes = null
                    } else {
                        cur.append(ch)
                    }
                }
                ch == '"' || ch == '\'' -> {
                    inQuotes = ch
                }
                ch.isWhitespace() -> {
                    if (cur.isNotEmpty()) {
                        result.add(cur.toString())
                        cur.clear()
                    }
                }
                else -> cur.append(ch)
            }
        }
        if (cur.isNotEmpty()) result.add(cur.toString())
        return result
    }

    private fun buildPlaylistResult(
        json: JSONObject,
        entries: JSONArray,
        platform: PlatformId,
        extractorKey: String,
        engineLabel: String,
        sourceUrl: String,
    ): ScrapeResult {
        val playlistTitle = json.optString("title").ifBlank { "${platform.displayName} Playlist" }
        val playlistAuthor = json.optString("uploader")
            .ifBlank { json.optString("channel") }
        val playlistThumb = extractBestThumbnail(json)
        val isAudioSite = platform == PlatformId.SOUNDCLOUD || platform == PlatformId.BANDCAMP
        val items = mutableListOf<ScraperDownloadItem>()

        val maxEntries = entries.length().coerceAtMost(200)
        var filteredByPolicyCount = 0
        for (i in 0 until maxEntries) {
            val entry = entries.optJSONObject(i) ?: continue
            val entryTitle = entry.optString("title")
                .ifBlank { "Track ${i + 1}" }
            if (entryTitle == "[Deleted video]" || entryTitle == "[Private video]") continue
            if (!NimiyoContentGuard.checkPlaylistEntryCompliant(entry)) {
                filteredByPolicyCount++
                continue
            }

            val entryUrl = resolveEntryUrl(entry, extractorKey, sourceUrl) ?: continue
            val entryThumb = extractBestThumbnail(entry).ifBlank { playlistThumb }
            val entryAuthor = entry.optString("uploader")
                .ifBlank { entry.optString("artist") }
                .ifBlank { playlistAuthor }
            val durSec = entry.optDouble("duration", 0.0)
            val durMs = if (durSec > 0.0) (durSec * 1000).toLong() else 0L

            if (isAudioSite) {
                items.add(
                    ScraperDownloadItem(
                        id = "pl_${i}_mp3_${entryUrl.hashCode()}",
                        title = entryTitle,
                        label = "${i + 1}. $entryTitle (MP3)",
                        url = entryUrl,
                        category = MediaCategory.AUDIO,
                        extension = "mp3",
                        thumbnail = entryThumb,
                        author = entryAuthor,
                        durationMs = durMs,
                        ytdlpFormatSelector = "ba/best",
                        ytdlpAudioFormat = "mp3",
                        ytdlpContainer = "mp3",
                        ytdlpTargetUrl = entryUrl,
                    )
                )
            } else {
                items.add(
                    ScraperDownloadItem(
                        id = "pl_${i}_mp4_${entryUrl.hashCode()}",
                        title = entryTitle,
                        label = "${i + 1}. $entryTitle (MP4)",
                        url = entryUrl,
                        category = MediaCategory.VIDEO,
                        extension = "mp4",
                        thumbnail = entryThumb,
                        author = entryAuthor,
                        durationMs = durMs,
                        ytdlpFormatSelector = buildVideoFormatSelector(null),
                        ytdlpContainer = "mp4",
                        ytdlpTargetUrl = entryUrl,
                    )
                )
            }
        }

        if (items.isEmpty()) {
            if (filteredByPolicyCount > 0) {
                throw PolicyViolationException(
                    PolicyViolationCategory.NSFW_18,
                    "Diblokir oleh Peraturan NIMIYO: Semua item dalam playlist melanggar aturan konten (18+/NSFW/dilarang).",
                )
            }
            throw IllegalStateException("Playlist contains no downloadable entries")
        }

        return ScrapeResult(
            platform = platform,
            serverUsed = engineLabel,
            title = playlistTitle,
            author = playlistAuthor,
            thumbnail = playlistThumb,
            sourceUrl = sourceUrl,
            items = items,
            extractorName = extractorKey,
            isPlaylist = true,
        )
    }

    private fun buildSingleMediaResult(
        json: JSONObject,
        platform: PlatformId,
        extractorKey: String,
        engineLabel: String,
        sourceUrl: String,
    ): ScrapeResult {
        val title = json.optString("title")
            .ifBlank { json.optString("fulltitle") }
            .ifBlank { "${platform.displayName} Media" }
        val author = json.optString("artist")
            .ifBlank { json.optString("uploader") }
            .ifBlank { json.optString("channel") }
            .ifBlank { json.optString("creator") }
        val thumb = extractBestThumbnail(json)
        val webpageUrl = json.optString("webpage_url").ifBlank { sourceUrl }
        val durSec = json.optDouble("duration", 0.0)
        val durMs = if (durSec > 0.0) (durSec * 1000).toLong() else 0L

        val formats = json.optJSONArray("formats")
        var bestAudioSize = 0L
        val heightBestMap = LinkedHashMap<Int, JSONObject>()
        var hasAnyVideoStream = false
        var hasHdFormat = false
        var hasSdFormat = false

        if (formats != null) {
            for (i in 0 until formats.length()) {
                val f = formats.optJSONObject(i) ?: continue
                val fmtId = f.optString("format_id").lowercase(Locale.US)
                if (fmtId == "hd") hasHdFormat = true
                if (fmtId == "sd") hasSdFormat = true
                val vcodec = f.optString("vcodec", "none")
                val acodec = f.optString("acodec", "none")
                val size = f.optLong("filesize", 0L).let { if (it > 0L) it else f.optLong("filesize_approx", 0L) }

                if (vcodec == "none" && acodec != "none") {
                    if (size > bestAudioSize) bestAudioSize = size
                    continue
                }

                if (vcodec != "none") {
                    hasAnyVideoStream = true
                    val h = f.optInt("height", 0)
                    if (h > 0) {
                        val existing = heightBestMap[h]
                        if (existing == null || scoreVideoFormat(f) > scoreVideoFormat(existing)) {
                            heightBestMap[h] = f
                        }
                    }
                }
            }
        }

        val items = mutableListOf<ScraperDownloadItem>()
        if (hasHdFormat) {
            items.add(
                ScraperDownloadItem(
                    id = "vid_hd_${webpageUrl.hashCode()}",
                    title = title,
                    label = "Video HD (MP4)",
                    url = webpageUrl,
                    category = MediaCategory.VIDEO,
                    extension = "mp4",
                    thumbnail = thumb,
                    author = author,
                    durationMs = durMs,
                    ytdlpFormatSelector = "hd/sd/b[ext=mp4]/bv*[vcodec^=avc1]+ba/best",
                    ytdlpContainer = "mp4",
                    ytdlpTargetUrl = webpageUrl,
                )
            )
        }
        if (hasSdFormat) {
            items.add(
                ScraperDownloadItem(
                    id = "vid_sd_${webpageUrl.hashCode()}",
                    title = title,
                    label = "Video SD (MP4)",
                    url = webpageUrl,
                    category = MediaCategory.VIDEO,
                    extension = "mp4",
                    thumbnail = thumb,
                    author = author,
                    durationMs = durMs,
                    ytdlpFormatSelector = "sd/b[ext=mp4]/best",
                    ytdlpContainer = "mp4",
                    ytdlpTargetUrl = webpageUrl,
                )
            )
        }
        val sortedHeights = heightBestMap.keys.sortedDescending()

        for (h in sortedHeights) {
            val f = heightBestMap[h] ?: continue
            val fps = f.optDouble("fps", 0.0).toInt()
            val vSize = f.optLong("filesize", 0L).let { if (it > 0L) it else f.optLong("filesize_approx", 0L) }
            val hasAudio = f.optString("acodec", "none") != "none"
            val estTotal = when {
                vSize > 0L && hasAudio -> vSize
                vSize > 0L -> vSize + bestAudioSize
                else -> 0L
            }
            val qualityTag = when {
                h >= 2160 -> "${h}p 4K"
                h >= 1440 -> "${h}p 2K"
                h >= 1080 -> "${h}p Full HD"
                h >= 720 -> "${h}p HD"
                else -> "${h}p"
            }
            val details = buildList {
                if (fps >= 50) add("${fps}fps")
                if (estTotal > 0L) add("~${formatCompactSize(estTotal)}")
            }
            val detailStr = if (details.isNotEmpty()) " (${details.joinToString(" • ")})" else ""

            items.add(
                ScraperDownloadItem(
                    id = "vid_${h}_${webpageUrl.hashCode()}",
                    title = title,
                    label = "Video $qualityTag$detailStr",
                    url = webpageUrl,
                    category = MediaCategory.VIDEO,
                    extension = "mp4",
                    thumbnail = thumb,
                    author = author,
                    durationMs = durMs,
                    ytdlpFormatSelector = buildVideoFormatSelector(h),
                    ytdlpContainer = "mp4",
                    ytdlpTargetUrl = webpageUrl,
                )
            )
        }

        val isAudioPrimary = platform == PlatformId.SOUNDCLOUD ||
            platform == PlatformId.BANDCAMP ||
            (!hasAnyVideoStream && sortedHeights.isEmpty())

        if (!isAudioPrimary && items.isEmpty()) {
            items.add(
                ScraperDownloadItem(
                    id = "vid_best_${webpageUrl.hashCode()}",
                    title = title,
                    label = "Video Best Quality (Auto)",
                    url = webpageUrl,
                    category = MediaCategory.VIDEO,
                    extension = "mp4",
                    thumbnail = thumb,
                    author = author,
                    durationMs = durMs,
                    ytdlpFormatSelector = buildVideoFormatSelector(null),
                    ytdlpContainer = "mp4",
                    ytdlpTargetUrl = webpageUrl,
                )
            )
        }

        val audioItems = listOf(
            ScraperDownloadItem(
                id = "aud_mp3_${webpageUrl.hashCode()}",
                title = title,
                label = "Audio MP3 (Best Quality)",
                url = webpageUrl,
                category = MediaCategory.AUDIO,
                extension = "mp3",
                thumbnail = thumb,
                author = author,
                durationMs = durMs,
                ytdlpFormatSelector = "ba/best",
                ytdlpAudioFormat = "mp3",
                ytdlpContainer = "mp3",
                ytdlpTargetUrl = webpageUrl,
            ),
            ScraperDownloadItem(
                id = "aud_m4a_${webpageUrl.hashCode()}",
                title = title,
                label = "Audio M4A (AAC)",
                url = webpageUrl,
                category = MediaCategory.AUDIO,
                extension = "m4a",
                thumbnail = thumb,
                author = author,
                durationMs = durMs,
                ytdlpFormatSelector = "ba[ext=m4a]/ba/best",
                ytdlpAudioFormat = "m4a",
                ytdlpContainer = "m4a",
                ytdlpTargetUrl = webpageUrl,
            ),
            ScraperDownloadItem(
                id = "aud_opus_${webpageUrl.hashCode()}",
                title = title,
                label = "Audio OPUS",
                url = webpageUrl,
                category = MediaCategory.AUDIO,
                extension = "opus",
                thumbnail = thumb,
                author = author,
                durationMs = durMs,
                ytdlpFormatSelector = "ba/best",
                ytdlpAudioFormat = "opus",
                ytdlpContainer = "opus",
                ytdlpTargetUrl = webpageUrl,
            ),
        )

        if (isAudioPrimary) {
            items.addAll(0, audioItems)
        } else {
            items.addAll(audioItems)
        }

        return ScrapeResult(
            platform = platform,
            serverUsed = engineLabel,
            title = title,
            author = author,
            thumbnail = thumb,
            sourceUrl = webpageUrl,
            items = items,
            extractorName = extractorKey,
            isPlaylist = false,
        )
    }

    private suspend fun analyzeSpotifyViaYtDlp(cleanUrl: String): ScrapeResult = coroutineScope {
        SocialPlatformScrapers.analyzeSpotifyPlaylistOrAlbumEmbed(http, cleanUrl)?.let { return@coroutineScope it }

        val trackId = Regex("""open\.spotify\.com/(?:intl-[^/]+/)?track/([A-Za-z0-9]+)""", RegexOption.IGNORE_CASE)
            .find(cleanUrl)?.groupValues?.getOrNull(1).orEmpty()

        val oembedDeferred = async {
            runCatching {
                http.get("https://open.spotify.com/oembed?url=${ScraperUtils.urlEncode(cleanUrl)}")
            }.getOrNull()
        }
        val embedDeferred = async {
            if (trackId.isNotBlank()) {
                runCatching {
                    http.get(
                        "https://open.spotify.com/embed/track/$trackId",
                        mapOf("User-Agent" to HttpClientFactory.CHROME_DESKTOP_UA),
                    )
                }.getOrNull()
            } else null
        }
        val htmlDeferred = async {
            runCatching { http.get(cleanUrl) }.getOrNull()
        }
        val oembedRes = oembedDeferred.await()
        val embedRes = embedDeferred.await()
        val htmlRes = htmlDeferred.await()

        var title = ""
        var thumb = ""
        var artist = ""

        if (embedRes != null && embedRes.status in 200..299 && embedRes.body.isNotBlank()) {
            runCatching {
                val nextMatch = Regex("""<script[^>]*id=["']__NEXT_DATA__["'][^>]*>([\s\S]*?)</script>""", RegexOption.IGNORE_CASE)
                    .find(embedRes.body)
                if (nextMatch != null) {
                    val root = JSONObject(nextMatch.groupValues[1])
                    val entity = root.optJSONObject("props")
                        ?.optJSONObject("pageProps")
                        ?.optJSONObject("state")
                        ?.optJSONObject("data")
                        ?.optJSONObject("entity")
                    if (entity != null) {
                        title = entity.optString("name").ifBlank { entity.optString("title") }.trim()
                        val artistsArr = entity.optJSONArray("artists")
                        if (artistsArr != null && artistsArr.length() > 0) {
                            artist = (0 until artistsArr.length())
                                .mapNotNull { artistsArr.optJSONObject(it)?.optString("name")?.trim()?.takeIf { n -> n.isNotEmpty() } }
                                .joinToString(", ")
                        }
                        if (artist.isBlank()) {
                            artist = entity.optString("subtitle").trim()
                        }
                        val coverArr = entity.optJSONObject("visualIdentity")?.optJSONArray("image")
                        if (coverArr != null && coverArr.length() > 0) {
                            thumb = coverArr.optJSONObject(0)?.optString("url").orEmpty()
                        }
                    }
                }
            }
        }

        if (oembedRes != null && oembedRes.status in 200..299 && oembedRes.body.isNotBlank()) {
            runCatching {
                val obj = JSONObject(oembedRes.body)
                if (title.isBlank()) title = obj.optString("title")
                if (thumb.isBlank()) thumb = obj.optString("thumbnail_url")
            }
        }
        if (htmlRes != null && htmlRes.status in 200..299) {
            val doc = Jsoup.parse(htmlRes.body)
            if (title.isBlank()) {
                title = doc.selectFirst("meta[property=og:title]")?.attr("content").orEmpty()
            }
            if (thumb.isBlank()) {
                thumb = doc.selectFirst("meta[property=og:image]")?.attr("content").orEmpty()
            }
            if (artist.isBlank()) {
                val musician = doc.selectFirst("meta[name=music:musician_description]")?.attr("content").orEmpty().trim()
                if (musician.isNotBlank()) {
                    artist = musician
                } else {
                    val desc = doc.selectFirst("meta[property=og:description]")?.attr("content").orEmpty()
                    artist = desc
                        .replace(Regex("""^Listen to .+ on Spotify\.\s*""", RegexOption.IGNORE_CASE), "")
                        .substringBefore("·")
                        .substringBefore("•")
                        .trim()
                }
            }
        }
        val cleanTitle = title.ifBlank { "Spotify Track" }
        val sanitizedArtist = artist.replace('\u00A0', ' ').trim()
            .let { if (it.equals("Spotify", ignoreCase = true)) "" else it }
        val enriched = enrichCatalogMetadata(cleanTitle, sanitizedArtist, thumb)
        val enrichedArtist = enriched.artist
        val enrichedThumb = enriched.thumb
        NimiyoContentGuard.checkTextCompliance(cleanTitle, enrichedArtist, title)
        val searchQuery = listOf(enrichedArtist, cleanTitle).filter { it.isNotBlank() }.joinToString(" - ")
        val ytSearchTarget = "ytsearch1:$searchQuery Official Audio"

        ScrapeResult(
            platform = PlatformId.SPOTIFY,
            serverUsed = "yt-dlp • Spotify -> Official Audio Match",
            title = cleanTitle,
            author = enrichedArtist,
            thumbnail = enrichedThumb,
            sourceUrl = cleanUrl,
            items = buildAudioPresetsForTarget(cleanTitle, enrichedArtist, enrichedThumb, ytSearchTarget),
            extractorName = "Spotify",
        )
    }

    private fun analyzeAppleMusicViaYtDlp(cleanUrl: String): ScrapeResult {
        val htmlRes = http.get(cleanUrl)
        if (htmlRes.status >= 400) {
            throw IllegalStateException("Failed to fetch Apple Music metadata (HTTP ${htmlRes.status})")
        }
        val doc = Jsoup.parse(htmlRes.body)
        val rawTitle = doc.selectFirst("meta[property=og:title]")?.attr("content")
            ?: doc.title()
        val thumb = doc.selectFirst("meta[property=og:image]")?.attr("content").orEmpty()
        val cleanTitle = rawTitle
            .replace(Regex("""\s*-\s*Song by.*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*on Apple Music$""", RegexOption.IGNORE_CASE), "")
            .trim()
            .ifBlank { "Apple Music Track" }
        val artist = Regex("""Song by\s+([^|•-]+)""", RegexOption.IGNORE_CASE)
            .find(rawTitle)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        NimiyoContentGuard.checkTextCompliance(cleanTitle, artist, rawTitle)
        val searchQuery = listOf(cleanTitle, artist).filter { it.isNotBlank() }.joinToString(" ")
        val ytSearchTarget = "ytsearch1:$searchQuery audio"

        return ScrapeResult(
            platform = PlatformId.APPLE_MUSIC,
            serverUsed = "yt-dlp • Apple Music -> YouTube Search",
            title = cleanTitle,
            author = artist,
            thumbnail = thumb,
            sourceUrl = cleanUrl,
            items = buildAudioPresetsForTarget(cleanTitle, artist, thumb, ytSearchTarget),
            extractorName = "AppleMusic",
        )
    }

    private fun buildAudioPresetsForTarget(
        title: String,
        author: String,
        thumb: String,
        targetUrl: String,
    ): List<ScraperDownloadItem> = listOf(
        ScraperDownloadItem(
            id = "aud_mp3_${targetUrl.hashCode()}",
            title = title,
            label = "Audio MP3 (Best Quality)",
            url = targetUrl,
            category = MediaCategory.AUDIO,
            extension = "mp3",
            thumbnail = thumb,
            author = author,
            ytdlpFormatSelector = "ba/best",
            ytdlpAudioFormat = "mp3",
            ytdlpContainer = "mp3",
            ytdlpTargetUrl = targetUrl,
        ),
        ScraperDownloadItem(
            id = "aud_m4a_${targetUrl.hashCode()}",
            title = title,
            label = "Audio M4A (AAC)",
            url = targetUrl,
            category = MediaCategory.AUDIO,
            extension = "m4a",
            thumbnail = thumb,
            author = author,
            ytdlpFormatSelector = "ba[ext=m4a]/ba/best",
            ytdlpAudioFormat = "m4a",
            ytdlpContainer = "m4a",
            ytdlpTargetUrl = targetUrl,
        ),
        ScraperDownloadItem(
            id = "aud_opus_${targetUrl.hashCode()}",
            title = title,
            label = "Audio OPUS",
            url = targetUrl,
            category = MediaCategory.AUDIO,
            extension = "opus",
            thumbnail = thumb,
            author = author,
            ytdlpFormatSelector = "ba/best",
            ytdlpAudioFormat = "opus",
            ytdlpContainer = "opus",
            ytdlpTargetUrl = targetUrl,
        ),
    )

    private fun resolveEntryUrl(entry: JSONObject, extractorKey: String, fallbackUrl: String): String? {
        val webpage = entry.optString("webpage_url")
        if (webpage.startsWith("http", ignoreCase = true)) return webpage
        val rawUrl = entry.optString("url")
        if (rawUrl.startsWith("http", ignoreCase = true)) return rawUrl
        val id = entry.optString("id").ifBlank { rawUrl }
        val ie = entry.optString("ie_key").ifBlank { extractorKey }.lowercase(Locale.US)
        if (id.isNotBlank() && ie.contains("youtube")) {
            return "https://www.youtube.com/watch?v=$id"
        }
        return fallbackUrl.takeIf { it.startsWith("http", ignoreCase = true) }
    }

    private fun extractBestThumbnail(obj: JSONObject): String {
        val direct = obj.optString("thumbnail")
        if (direct.startsWith("http", ignoreCase = true)) return direct
        val thumbs = obj.optJSONArray("thumbnails") ?: return ""
        var best = ""
        for (i in thumbs.length() - 1 downTo 0) {
            val u = thumbs.optJSONObject(i)?.optString("url").orEmpty()
            if (u.startsWith("http", ignoreCase = true)) {
                best = u
                break
            }
        }
        return best
    }

    private fun scoreVideoFormat(f: JSONObject): Double {
        var score = 0.0
        val ext = f.optString("ext").lowercase(Locale.US)
        val vcodec = f.optString("vcodec").lowercase(Locale.US)
        if (ext == "mp4") score += 100.0
        if (vcodec.startsWith("avc1") || vcodec.startsWith("h264")) score += 50.0
        if (f.optString("acodec", "none") != "none") score += 20.0
        score += f.optDouble("tbr", 0.0) / 1000.0
        score += f.optDouble("fps", 0.0) / 10.0
        return score
    }

    private fun buildVideoFormatSelector(height: Int?): String {
        return if (height != null) {
            "bv*[height=$height][vcodec^=avc1][ext=mp4]+ba[acodec^=mp4a]/" +
                "bv*[height<=$height][vcodec^=avc1][ext=mp4]+ba[acodec^=mp4a]/" +
                "b[height=$height][ext=mp4]/" +
                "hd/sd/b[ext=mp4]/" +
                "bv*[height=$height][vcodec!^=av01]+ba/" +
                "bv*[height=$height]+ba/" +
                "b[height=$height]/" +
                "bv*[height<=$height]+ba/" +
                "b[height<=$height]/best"
        } else {
            "bv*[vcodec^=avc1][ext=mp4]+ba[acodec^=mp4a]/" +
                "hd/sd/b[ext=mp4]/" +
                "bv*[ext=mp4][vcodec!^=av01]+ba[ext=m4a]/" +
                "bv*+ba/b/best"
        }
    }

    private fun validateVideoTrackReadable(file: File) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            val mimes = mutableListOf<String>()
            var hasVideo = false
            for (i in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME).orEmpty()
                mimes.add(mime)
                if (mime.startsWith("video/", ignoreCase = true)) {
                    hasVideo = true
                }
            }
            if (!hasVideo) {
                throw IllegalStateException(
                    "Video file has no playable video track (${file.name}, ${file.length()}B, tracks=$mimes)"
                )
            }
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun findDownloadedMediaFile(dir: File, expectedExt: String): File? {
        val nonMediaExts = setOf("jpg", "jpeg", "png", "webp", "json", "description", "vtt", "srt")
        val fragmentPattern = Regex("""\.f\d+\.""")
        val files = dir.listFiles()?.filter { f ->
            f.isFile &&
                f.length() > 0L &&
                !f.name.endsWith(".part", ignoreCase = true) &&
                !f.name.endsWith(".ytdl", ignoreCase = true) &&
                !f.name.endsWith(".temp", ignoreCase = true)
        }.orEmpty()
        if (files.isEmpty()) return null

        val nonFragments = files.filter { !fragmentPattern.containsMatchIn(it.name) }
        return nonFragments.firstOrNull { it.name.equals("nimiyo_out.$expectedExt", ignoreCase = true) }
            ?: nonFragments.firstOrNull { it.extension.equals(expectedExt, ignoreCase = true) }
            ?: nonFragments.firstOrNull { it.extension.lowercase(Locale.US) !in nonMediaExts }
            ?: files.firstOrNull {
                val ext = it.extension.lowercase(Locale.US)
                ext !in nonMediaExts && !fragmentPattern.containsMatchIn(it.name)
            }
    }

    private data class CatalogMeta(
        val artist: String,
        val album: String,
        val thumb: String,
        val durationMs: Long,
    )

    /**
     * Enriches catalog track metadata (Artist, Album, Cover Art, Duration) via iTunes Search API
     * when Spotify/Apple embed only gives partial info, and ensures "Spotify" is never
     * written as Artist or Album.
     */
    private fun enrichCatalogMetadata(
        title: String,
        rawArtist: String,
        rawThumb: String,
    ): CatalogMeta {
        var artist = rawArtist.replace('\u00A0', ' ').trim()
            .let { if (it.equals("Spotify", true) || it.equals("Apple Music", true)) "" else it }
        var album = ""
        var thumb = rawThumb.trim()
        var durationMs = 0L

        val term = listOf(title, artist).filter { it.isNotBlank() }.joinToString(" ").trim()
        if (term.isBlank()) return CatalogMeta(artist, album, thumb, durationMs)

        runCatching {
            val apiUrl = "https://itunes.apple.com/search?term=${ScraperUtils.urlEncode(term)}&entity=song&limit=3"
            val res = http.get(apiUrl)
            if (res.status in 200..299 && res.body.isNotBlank()) {
                val results = JSONObject(res.body).optJSONArray("results")
                if (results != null && results.length() > 0) {
                    val normTitle = title.lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "")
                    var picked = results.optJSONObject(0)
                    for (i in 0 until results.length()) {
                        val cand = results.optJSONObject(i) ?: continue
                        val candTitle = cand.optString("trackName").lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "")
                        if (normTitle.isNotBlank() && (candTitle == normTitle || candTitle.contains(normTitle))) {
                            picked = cand
                            break
                        }
                    }
                    if (picked != null) {
                        if (artist.isBlank()) {
                            artist = picked.optString("artistName").trim()
                        }
                        album = picked.optString("collectionName").trim()
                        durationMs = picked.optLong("trackTimeMillis", 0L)
                        if (thumb.isBlank()) {
                            val art100 = picked.optString("artworkUrl100").trim()
                            if (art100.isNotBlank()) {
                                thumb = art100.replace("100x100bb", "600x600bb")
                            }
                        }
                    }
                }
            }
        }
        return CatalogMeta(artist, album, thumb, durationMs)
    }

    /**
     * Resolves a Spotify/Apple Music track to an official YouTube Music ("- Topic" / Official Audio)
     * video URL, rejecting ads, shorts, covers, karaoke, reactions, and duration mismatches.
     */
    private fun resolveOfficialYouTubeAudioUrl(
        title: String,
        artist: String,
        durationMs: Long,
    ): String? {
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) return null
        val query = listOf(artist.trim(), cleanTitle, "Topic").filter { it.isNotBlank() }.joinToString(" ")
        return runCatching {
            val searchUrl = "https://www.youtube.com/results?search_query=${ScraperUtils.urlEncode(query)}"
            val res = http.get(searchUrl, mapOf("User-Agent" to HttpClientFactory.CHROME_DESKTOP_UA))
            if (res.status !in 200..299 || res.body.isBlank()) return@runCatching null

            val marker = "var ytInitialData = "
            val start = res.body.indexOf(marker)
            if (start < 0) return@runCatching null
            val jsonStart = start + marker.length
            val end = res.body.indexOf(";</script>", jsonStart)
            if (end <= jsonStart) return@runCatching null

            val root = JSONObject(res.body.substring(jsonStart, end))
            val sections = root.optJSONObject("contents")
                ?.optJSONObject("twoColumnSearchResultsRenderer")
                ?.optJSONObject("primaryContents")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents") ?: return@runCatching null

            val titleLower = cleanTitle.lowercase(Locale.US)
            val rejectTokens = listOf(
                "cover", "karaoke", "reaction", "nightcore", "slowed", "reverb",
                "8d", "instrumental", "shorts", "promo", "teaser", "trailer", "live",
            ).filter { !titleLower.contains(it) }

            var bestVideoId: String? = null
            var bestScore = Int.MIN_VALUE

            for (s in 0 until sections.length()) {
                val items = sections.optJSONObject(s)
                    ?.optJSONObject("itemSectionRenderer")
                    ?.optJSONArray("contents") ?: continue
                for (i in 0 until items.length().coerceAtMost(12)) {
                    val vr = items.optJSONObject(i)?.optJSONObject("videoRenderer") ?: continue
                    val vid = vr.optString("videoId").trim()
                    if (vid.length != 11) continue
                    val lengthText = vr.optJSONObject("lengthText")?.optString("simpleText").orEmpty().trim()
                    if (lengthText.isBlank()) continue // skip livestreams

                    val vTitle = vr.optJSONObject("title")?.optJSONArray("runs")
                        ?.optJSONObject(0)?.optString("text").orEmpty().lowercase(Locale.US)
                    val owner = vr.optJSONObject("ownerText")?.optJSONArray("runs")
                        ?.optJSONObject(0)?.optString("text").orEmpty()
                    val ownerLower = owner.lowercase(Locale.US)

                    var score = 0
                    if (ownerLower.endsWith(" - topic") || ownerLower.endsWith("– topic")) score += 120
                    if (vTitle.contains("official audio") || vTitle.contains("provided to youtube")) score += 60
                    if (artist.isNotBlank() && (ownerLower.contains(artist.lowercase(Locale.US)) || vTitle.contains(artist.lowercase(Locale.US)))) {
                        score += 30
                    }
                    for (bad in rejectTokens) {
                        if (vTitle.contains(bad) || ownerLower.contains(bad)) score -= 100
                    }
                    if (durationMs > 0L) {
                        val candMs = parseDurationTextMs(lengthText)
                        if (candMs > 0L) {
                            val diffSec = kotlin.math.abs(candMs - durationMs) / 1000L
                            if (diffSec <= 15L) score += 50
                            else if (diffSec > 45L) score -= 120
                        }
                    }
                    if (score > bestScore) {
                        bestScore = score
                        bestVideoId = vid
                    }
                }
            }
            if (bestVideoId != null && bestScore > 0) {
                "https://www.youtube.com/watch?v=$bestVideoId"
            } else null
        }.getOrNull()
    }

    private fun parseDurationTextMs(text: String): Long {
        val parts = text.split(":").mapNotNull { it.trim().toLongOrNull() }
        return when (parts.size) {
            2 -> (parts[0] * 60L + parts[1]) * 1000L
            3 -> (parts[0] * 3600L + parts[1] * 60L + parts[2]) * 1000L
            else -> 0L
        }
    }

    private fun fetchCoverBytes(thumbnailUrl: String): Pair<ByteArray?, String> {
        if (!thumbnailUrl.startsWith("http", ignoreCase = true)) return null to "image/jpeg"
        return runCatching {
            val req = Request.Builder()
                .url(thumbnailUrl)
                .header("User-Agent", http.effectiveUserAgent())
                .build()
            http.downloadClient().newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@runCatching null to "image/jpeg"
                val bytes = res.body?.bytes()
                if (bytes == null || bytes.isEmpty() || bytes.size > 4 * 1024 * 1024) {
                    return@runCatching null to "image/jpeg"
                }
                val ct = res.header("Content-Type").orEmpty().lowercase(Locale.US)
                val mime = if (ct.contains("png")) "image/png" else "image/jpeg"
                bytes to mime
            }
        }.getOrDefault(null to "image/jpeg")
    }

    private fun resolveMimeType(category: MediaCategory, ext: String): String = when (category) {
        MediaCategory.VIDEO -> when (ext) {
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            else -> "video/mp4"
        }
        MediaCategory.AUDIO -> when (ext) {
            "m4a" -> "audio/mp4"
            "opus" -> "audio/opus"
            "ogg" -> "audio/ogg"
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            else -> "audio/mpeg"
        }
        MediaCategory.IMAGE -> when (ext) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }
    }

    private fun mapExtractorToPlatform(extractorKey: String, url: String): PlatformId {
        val k = extractorKey.lowercase(Locale.US)
        return when {
            k.contains("youtube") -> PlatformId.YOUTUBE
            k.contains("tiktok") -> PlatformId.TIKTOK
            k.contains("instagram") -> PlatformId.INSTAGRAM
            k.contains("twitter") || k == "x" -> PlatformId.TWITTER
            k.contains("facebook") -> PlatformId.FACEBOOK
            k.contains("pinterest") -> PlatformId.PINTEREST
            k.contains("threads") -> PlatformId.THREADS
            k.contains("bilibili") -> PlatformId.BILIBILI
            k.contains("douyin") -> PlatformId.DOUYIN
            k.contains("pixiv") -> PlatformId.PIXIV
            k.contains("xiaohongshu") || k.contains("rednote") -> PlatformId.REDNOTE
            k.contains("soundcloud") -> PlatformId.SOUNDCLOUD
            k.contains("bandcamp") -> PlatformId.BANDCAMP
            k.contains("shopee") -> PlatformId.SHOPEE
            else -> PlatformDetector.detect(url) ?: PlatformId.OTHER
        }
    }

    private fun mapAgeGateErrorIfNeeded(err: Throwable): Throwable {
        val msg = err.message.orEmpty()
        if (isAgeRestrictionSignal(msg)) {
            return PolicyViolationException(
                PolicyViolationCategory.NSFW_18,
                "Diblokir oleh Peraturan NIMIYO [Pornografi & NSFW (18+)]: Konten ini dibatasi usia 18+ / NSFW oleh platform sumber.",
            )
        }
        return err
    }

    private fun isAgeRestrictionSignal(text: String): Boolean {
        val lower = text.lowercase(Locale.US)
        return lower.contains("confirm your age") ||
            lower.contains("age-restricted") ||
            lower.contains("age restricted") ||
            lower.contains("age_limit") ||
            lower.contains("inappropriate for some users") ||
            lower.contains("nsfw")
    }

    private fun formatCompactSize(bytes: Long): String {
        if (bytes <= 0L) return ""
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            else -> String.format(Locale.US, "%.0f KB", kb)
        }
    }
}

