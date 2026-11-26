package com.nimiyo.nativeapp.download

import android.content.Context
import android.media.AudioManager
import android.media.MediaScannerConnection
import android.media.ToneGenerator
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.nimiyo.nativeapp.core.AppSettings
import com.nimiyo.nativeapp.core.DownloadSubfolderMode
import com.nimiyo.nativeapp.core.FilenameTemplate
import com.nimiyo.nativeapp.core.HttpClientFactory
import com.nimiyo.nativeapp.data.DownloadStatus
import com.nimiyo.nativeapp.data.DownloadTask
import com.nimiyo.nativeapp.data.HistoryRecord
import com.nimiyo.nativeapp.data.MediaCategory
import com.nimiyo.nativeapp.data.MediaStoreRepository
import com.nimiyo.nativeapp.data.PlatformId
import com.nimiyo.nativeapp.data.ScraperDownloadItem
import com.nimiyo.nativeapp.scraper.ScraperEngine
import com.nimiyo.nativeapp.scraper.ScraperUtils
import com.nimiyo.nativeapp.scraper.YouTubeScraper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

class DownloadQueueManager(
    private val context: Context,
    private val settings: AppSettings,
    private val mediaRepo: MediaStoreRepository,
    private val scraper: ScraperEngine,
    private val notifHelper: NotificationHelper,
) {
    data class DownloadErrorEvent(
        val taskId: String,
        val title: String,
        val platform: PlatformId,
        val errorMessage: String,
        val canFallback: Boolean,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val cancelledIds = ConcurrentHashMap.newKeySet<String>()
    private val forceFallbackIds = ConcurrentHashMap.newKeySet<String>()
    private val enqueueOrder = ConcurrentHashMap<String, Long>()
    private val seqGenerator = AtomicLong(0L)
    private val historyMutex = Mutex()

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    private val _history = MutableStateFlow<List<HistoryRecord>>(emptyList())
    val history: StateFlow<List<HistoryRecord>> = _history.asStateFlow()

    private val _balloonExpanded = MutableStateFlow(false)
    val balloonExpanded: StateFlow<Boolean> = _balloonExpanded.asStateFlow()

    private val _latestDownloadError = MutableStateFlow<DownloadErrorEvent?>(null)
    val latestDownloadError: StateFlow<DownloadErrorEvent?> = _latestDownloadError.asStateFlow()

    fun dismissDownloadError() {
        _latestDownloadError.value = null
    }

    init {
        scope.launch {
            historyMutex.withLock {
                var loaded = mediaRepo.loadHistory()
                val cfg = settings.current
                if (cfg.autoClearHistoryDays > 0) {
                    val cutoff = System.currentTimeMillis() - cfg.autoClearHistoryDays.toLong() * 86_400_000L
                    val pruned = loaded.filter { it.createdAt >= cutoff }
                    if (pruned.size != loaded.size) {
                        loaded = pruned
                        mediaRepo.saveHistory(loaded)
                    }
                }
                if (cfg.historyLimit > 0 && loaded.size > cfg.historyLimit) {
                    loaded = loaded.take(cfg.historyLimit)
                    mediaRepo.saveHistory(loaded)
                }
                _history.value = loaded
            }
        }
    }

    fun setBalloonExpanded(expanded: Boolean) {
        _balloonExpanded.value = expanded
    }

    fun refreshHistory() {
        scope.launch {
            historyMutex.withLock {
                _history.value = mediaRepo.loadHistory()
            }
        }
    }

    fun deleteHistoryItem(id: String) {
        scope.launch {
            historyMutex.withLock {
                val next = _history.value.filterNot { it.id == id }
                _history.value = next
                mediaRepo.saveHistory(next)
            }
        }
    }

    fun clearAllHistory() {
        scope.launch {
            historyMutex.withLock {
                _history.value = emptyList()
                mediaRepo.saveHistory(emptyList())
            }
        }
    }

    fun enqueue(
        items: List<ScraperDownloadItem>,
        platform: PlatformId,
        sourceUrl: String,
        fromQuickSave: Boolean = false,
        serverId: String = "",
    ) {
        val cfg = settings.current
        val dateStamp = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val added = items.map { item ->
            val taskId = UUID.randomUUID().toString()
            enqueueOrder[taskId] = seqGenerator.incrementAndGet()
            val safeTitle = mediaRepo.sanitizeFilename(item.title)
            val templated = when (cfg.filenameTemplate) {
                FilenameTemplate.TITLE -> safeTitle
                FilenameTemplate.TITLE_PLATFORM -> "${safeTitle}_${platform.displayName.replace(" ", "")}"
                FilenameTemplate.TITLE_DATE -> "${safeTitle}_$dateStamp"
            }
            val safeName = mediaRepo.sanitizeFilename(templated)
            DownloadTask(
                id = taskId,
                title = item.title,
                url = item.url,
                filename = "$safeName.${item.extension}",
                extension = item.extension,
                category = item.category,
                platform = platform,
                thumbnail = item.thumbnail,
                author = item.author,
                referer = item.referer,
                sourceUrl = sourceUrl,
                serverId = serverId,
                status = DownloadStatus.QUEUED,
                fromQuickSave = fromQuickSave,
            )
        }
        _tasks.update { added + it }
        pumpQueue()
    }

    fun retry(taskId: String) {
        cancelledIds.remove(taskId)
        forceFallbackIds.remove(taskId)
        activeJobs.remove(taskId)?.cancel()
        enqueueOrder[taskId] = seqGenerator.incrementAndGet()
        updateTask(taskId) {
            it.copy(status = DownloadStatus.QUEUED, progress = 0, errorMessage = "")
        }
        pumpQueue()
    }

    fun retryWithFallback(taskId: String) {
        cancelledIds.remove(taskId)
        forceFallbackIds.add(taskId)
        activeJobs.remove(taskId)?.cancel()
        enqueueOrder[taskId] = seqGenerator.incrementAndGet()
        updateTask(taskId) {
            it.copy(status = DownloadStatus.QUEUED, progress = 0, errorMessage = "")
        }
        pumpQueue()
    }

    fun cancel(taskId: String) {
        cancelledIds.add(taskId)
        activeJobs[taskId]?.cancel()
        updateTask(taskId) {
            it.copy(status = DownloadStatus.CANCELLED)
        }
        updateNotifications()
        pumpQueue()
    }

    fun cancelAll(quickSaveOnly: Boolean = false) {
        _tasks.value.filter {
            (!quickSaveOnly || it.fromQuickSave) &&
                (it.status == DownloadStatus.QUEUED ||
                    it.status == DownloadStatus.RESOLVING ||
                    it.status == DownloadStatus.DOWNLOADING)
        }.forEach { t ->
            cancelledIds.add(t.id)
            activeJobs[t.id]?.cancel()
        }
        _tasks.value = _tasks.value.map { t ->
            if ((!quickSaveOnly || t.fromQuickSave) &&
                (t.status == DownloadStatus.QUEUED ||
                    t.status == DownloadStatus.RESOLVING ||
                    t.status == DownloadStatus.DOWNLOADING)
            ) {
                t.copy(status = DownloadStatus.CANCELLED)
            } else t
        }
        updateNotifications()
    }

    fun clearFinished() {
        _tasks.value = _tasks.value.filter {
            it.status == DownloadStatus.QUEUED ||
                it.status == DownloadStatus.RESOLVING ||
                it.status == DownloadStatus.DOWNLOADING
        }
        updateNotifications()
    }

    @Synchronized
    private fun pumpQueue() {
        val maxConcurrent = settings.current.maxConcurrentDownloads.coerceIn(1, 5)
        val runningCount = _tasks.value.count {
            it.status == DownloadStatus.RESOLVING || it.status == DownloadStatus.DOWNLOADING
        }
        val availableSlots = (maxConcurrent - runningCount).coerceAtLeast(0)
        if (availableSlots == 0) {
            updateNotifications()
            return
        }

        val pending = _tasks.value
            .filter { it.status == DownloadStatus.QUEUED }
            .sortedBy { enqueueOrder[it.id] ?: Long.MAX_VALUE }
            .take(availableSlots)
        for (task in pending) {
            cancelledIds.remove(task.id)
            updateTask(task.id) { it.copy(status = DownloadStatus.RESOLVING, progress = 0) }
            val job = scope.launch {
                executeTask(task.id)
            }
            activeJobs[task.id] = job
        }
        updateNotifications()
    }

    private suspend fun executeTask(taskId: String) {
        try {
            val task = _tasks.value.firstOrNull { it.id == taskId } ?: return
            if (settings.current.wifiOnlyDownloads && !isWifiConnected()) {
                val msg = "Wi-Fi only mode enabled"
                updateTask(taskId) { it.copy(status = DownloadStatus.FAILED, errorMessage = msg) }
                emitDownloadError(task, msg)
                return
            }

            val forceFallback = forceFallbackIds.remove(taskId)
            val cfg = settings.current
            val maxAttempts = if (cfg.autoRetryFailed) cfg.maxRetryCount.coerceIn(1, 5) else 1
            var lastErr: Throwable? = null

            if (!forceFallback) {
                for (attempt in 1..maxAttempts) {
                    if (cancelledIds.contains(taskId)) return
                    val attemptRes = runCatching { performDownload(task) }
                    if (attemptRes.isSuccess) {
                        completeTask(task, attemptRes.getOrThrow())
                        return
                    }
                    lastErr = attemptRes.exceptionOrNull()
                }
            }

            // Automatic fallback across remaining platform servers if sourceUrl is available
            if (!cancelledIds.contains(taskId) && task.sourceUrl.isNotBlank() && task.platform.servers.size > 1) {
                val fallbackServers = task.platform.servers
                    .map { it.first }
                    .filterNot { it.equals(task.serverId, ignoreCase = true) }
                for (srvId in fallbackServers) {
                    if (cancelledIds.contains(taskId)) return
                    updateTask(taskId) {
                        it.copy(status = DownloadStatus.RESOLVING, progress = 0, serverId = srvId)
                    }
                    val scraped = runCatching {
                        scraper.analyze(task.sourceUrl, task.platform, srvId)
                    }.getOrNull() ?: continue
                    // For multi-track playlists/albums, NEVER fall back to items[0] (which overwrites every track with Track #1)!
                    val matchedItem = if (scraped.items.size > 1) {
                        val normTaskTitle = task.title.lowercase().replace(Regex("[^a-z0-9]"), "")
                        scraped.items.firstOrNull {
                            it.title.equals(task.title, ignoreCase = true) && it.category == task.category
                        } ?: scraped.items.firstOrNull {
                            it.title.equals(task.title, ignoreCase = true)
                        } ?: scraped.items.firstOrNull {
                            val normItem = it.title.lowercase().replace(Regex("[^a-z0-9]"), "")
                            normTaskTitle.isNotBlank() && normItem == normTaskTitle && it.category == task.category
                        }
                    } else {
                        scraped.items.firstOrNull {
                            it.category == task.category && it.extension.equals(task.extension, ignoreCase = true)
                        } ?: scraped.items.firstOrNull { it.category == task.category }
                            ?: scraped.items.firstOrNull()
                    }
                    if (matchedItem == null) {
                        if (task.platform == PlatformId.SPOTIFY && task.category == MediaCategory.AUDIO && task.title.isNotBlank()) {
                            val ytCandidate = task.copy(
                                url = "ytsearch_resolve:${ScraperUtils.urlEncode(task.title)}",
                                serverId = "yt_fallback",
                            )
                            updateTask(taskId) { ytCandidate.copy(status = DownloadStatus.RESOLVING) }
                            val ytRes = runCatching { performDownload(ytCandidate) }
                            if (ytRes.isSuccess) {
                                completeTask(ytCandidate, ytRes.getOrThrow())
                                return
                            }
                            lastErr = ytRes.exceptionOrNull()
                        }
                        continue
                    }

                    val candidate = task.copy(
                        url = matchedItem.url,
                        referer = matchedItem.referer,
                        thumbnail = matchedItem.thumbnail.ifBlank { task.thumbnail },
                        author = matchedItem.author.ifBlank { task.author },
                        serverId = srvId,
                    )
                    updateTask(taskId) { candidate.copy(status = DownloadStatus.RESOLVING) }
                    val fbRes = runCatching { performDownload(candidate) }
                    if (fbRes.isSuccess) {
                        completeTask(candidate, fbRes.getOrThrow())
                        return
                    }
                    lastErr = fbRes.exceptionOrNull()
                }
            }

            if (!cancelledIds.contains(taskId)) {
                val errMsg = lastErr?.message?.ifBlank { null } ?: "Download failed"
                updateTask(taskId) {
                    it.copy(
                        status = DownloadStatus.FAILED,
                        errorMessage = errMsg,
                    )
                }
                val latest = _tasks.value.firstOrNull { it.id == taskId } ?: task
                emitDownloadError(latest, errMsg)
            }
        } finally {
            val currentJob = coroutineContext[Job]
            if (currentJob != null) {
                activeJobs.remove(taskId, currentJob)
            } else {
                activeJobs.remove(taskId)
            }
            updateNotifications()
            pumpQueue()
        }
    }

    private suspend fun completeTask(task: DownloadTask, result: Triple<String, String, Long>) {
        val (localPath, uriStr, bytes) = result
        enqueueOrder.remove(task.id)
        // Remove completed task from queue immediately
        _tasks.update { list -> list.filterNot { it.id == task.id } }
        if (!settings.current.incognitoMode) {
            val rec = HistoryRecord(
                id = task.id,
                title = task.title,
                filename = task.filename,
                category = task.category,
                platform = task.platform.displayName,
                localPath = localPath,
                contentUri = uriStr,
                thumbnail = task.thumbnail,
                sourceUrl = task.sourceUrl,
                sizeBytes = bytes,
            )
            historyMutex.withLock {
                val limit = settings.current.historyLimit
                val combined = listOf(rec) + _history.value
                val nextHistory = if (limit > 0 && combined.size > limit) combined.take(limit) else combined
                _history.value = nextHistory
                mediaRepo.saveHistory(nextHistory)
            }
        }
        if (settings.current.completionSound) {
            playCompletionChime()
        }
    }

    private fun emitDownloadError(task: DownloadTask, errorMessage: String) {
        _latestDownloadError.value = DownloadErrorEvent(
            taskId = task.id,
            title = task.title,
            platform = task.platform,
            errorMessage = errorMessage,
            canFallback = task.sourceUrl.isNotBlank() && task.platform.servers.size > 1,
        )
    }

    private suspend fun performDownload(task: DownloadTask): Triple<String, String, Long> {
        if (task.url.startsWith("ytmux_resolve:")) {
            return performMuxedMp4Download(task)
        }
        val resolvedUrl = scraper.resolveLazyDownloadUrl(task.url)
        if (cancelledIds.contains(task.id)) throw InterruptedException("Cancelled")

        updateTask(task.id) { it.copy(status = DownloadStatus.DOWNLOADING) }

        val mime = when (task.category) {
            MediaCategory.VIDEO -> "video/mp4"
            MediaCategory.AUDIO -> if (task.extension.equals("m4a", true)) "audio/mp4" else "audio/mpeg"
            MediaCategory.IMAGE -> when (task.extension.lowercase(Locale.US)) {
                "png" -> "image/png"
                "webp" -> "image/webp"
                "gif" -> "image/gif"
                else -> "image/jpeg"
            }
        }

        val headerAttempts = buildHeaderCandidates(resolvedUrl, task.referer)
        var lastCode = 0
        for ((ua, ref) in headerAttempts) {
            if (cancelledIds.contains(task.id)) throw InterruptedException("Cancelled")
            val reqBuilder = Request.Builder()
                .url(resolvedUrl)
                .header("User-Agent", ua)
                .header("Accept", "*/*")
                .header("Accept-Language", "en-US,en;q=0.9")
            if (ref.isNotBlank()) {
                reqBuilder.header("Referer", ref)
                extractOrigin(ref)?.let { reqBuilder.header("Origin", it) }
            }

            scraper.http.downloadClient().newCall(reqBuilder.build()).execute().use { res ->
                if (!res.isSuccessful) {
                    lastCode = res.code
                    // Retry with alternate header profiles only on 401/403
                    if (res.code == 403 || res.code == 401) {
                        return@use
                    }
                    throw IllegalStateException("HTTP ${res.code} while downloading")
                }
                val body = res.body ?: throw IllegalStateException("Empty download body")
                val totalLen = body.contentLength()
                var finalBytes = 0L
                val baseName = task.filename.substringBeforeLast(".")
                val shouldTagMp3 = task.category == MediaCategory.AUDIO &&
                    task.extension.equals("mp3", ignoreCase = true) &&
                    (task.author.isNotBlank() || task.thumbnail.isNotBlank())

                val (localPath, uriStr) = if (shouldTagMp3) {
                    val tempFile = File(context.cacheDir, "dl_${task.id}.mp3")
                    try {
                        body.byteStream().use { inp ->
                            FileOutputStream(tempFile).use { out ->
                                val buf = ByteArray(16 * 1024)
                                var dl = 0L
                                var lastRep = 0L
                                while (true) {
                                    if (cancelledIds.contains(task.id)) throw InterruptedException("Cancelled by user")
                                    val n = inp.read(buf)
                                    if (n == -1) break
                                    out.write(buf, 0, n)
                                    dl += n
                                    val now = System.currentTimeMillis()
                                    if (now - lastRep >= 250) {
                                        finalBytes = dl
                                        val pct = if (totalLen > 0) ((dl * 100) / totalLen).toInt().coerceIn(0, 100) else 0
                                        updateTask(task.id) {
                                            it.copy(progress = pct, bytesDownloaded = dl, totalBytes = totalLen)
                                        }
                                        updateNotifications()
                                        lastRep = now
                                    }
                                }
                                finalBytes = dl
                                out.flush()
                            }
                        }
                        val (coverBytes, coverMime) = fetchCoverArt(task.thumbnail)
                        Mp3Id3Tagger.tagFileInPlaceIfNeeded(
                            mp3File = tempFile,
                            title = task.title,
                            artist = task.author,
                            album = task.platform.displayName,
                            coverBytes = coverBytes,
                            coverMime = coverMime,
                        )
                        val taggedLen = tempFile.length()
                        finalBytes = taggedLen
                        tempFile.inputStream().use { taggedIn ->
                            val cfgSnap = settings.current
                            val customSub = if (cfgSnap.downloadSubfolderMode == DownloadSubfolderMode.BY_PLATFORM) {
                                task.platform.displayName
                            } else null
                            mediaRepo.saveStreamToDownloads(
                                baseName = baseName,
                                extension = task.extension,
                                category = task.category,
                                mimeType = mime,
                                totalBytes = taggedLen,
                                inputStream = taggedIn,
                                overwriteMode = cfgSnap.overwriteMode,
                                customTreeUri = cfgSnap.effectiveTreeUriFor(task.category),
                                customSubfolder = customSub,
                                onProgress = { _, _ -> },
                                isCancelled = { cancelledIds.contains(task.id) },
                            )
                        }
                    } finally {
                        runCatching { tempFile.delete() }
                    }
                } else {
                    val cfgSnap = settings.current
                    val customSub = if (cfgSnap.downloadSubfolderMode == DownloadSubfolderMode.BY_PLATFORM) {
                        task.platform.displayName
                    } else null
                    mediaRepo.saveStreamToDownloads(
                        baseName = baseName,
                        extension = task.extension,
                        category = task.category,
                        mimeType = mime,
                        totalBytes = totalLen,
                        inputStream = body.byteStream(),
                        overwriteMode = cfgSnap.overwriteMode,
                        customTreeUri = cfgSnap.effectiveTreeUriFor(task.category),
                        customSubfolder = customSub,
                        onProgress = { dl, tot ->
                            finalBytes = dl
                            val pct = if (tot > 0) ((dl * 100) / tot).toInt().coerceIn(0, 100) else 0
                            updateTask(task.id) {
                                it.copy(progress = pct, bytesDownloaded = dl, totalBytes = tot)
                            }
                            updateNotifications()
                        },
                        isCancelled = { cancelledIds.contains(task.id) },
                    )
                }
                if (localPath.isNotBlank() && (task.category == MediaCategory.AUDIO || task.category == MediaCategory.VIDEO || task.category == MediaCategory.IMAGE)) {
                    runCatching {
                        MediaScannerConnection.scanFile(context, arrayOf(localPath), arrayOf(mime), null)
                    }
                }
                return Triple(localPath, uriStr, finalBytes.coerceAtLeast(totalLen))
            }
        }
        throw IllegalStateException("HTTP $lastCode while downloading")
    }

    private suspend fun performMuxedMp4Download(task: DownloadTask): Triple<String, String, Long> {
        val parts = task.url.removePrefix("ytmux_resolve:").split("|||")
        val videoUrl = com.nimiyo.nativeapp.scraper.ScraperUtils.urlDecode(parts.getOrNull(0).orEmpty())
        val audioUrl = com.nimiyo.nativeapp.scraper.ScraperUtils.urlDecode(parts.getOrNull(1).orEmpty())
        if (!videoUrl.startsWith("http") || !audioUrl.startsWith("http")) {
            throw IllegalStateException("Invalid YouTube HD stream URLs")
        }

        updateTask(task.id) { it.copy(status = DownloadStatus.DOWNLOADING, progress = 0) }
        val videoTmp = File(context.cacheDir, "mux_v_${task.id}.mp4")
        val audioTmp = File(context.cacheDir, "mux_a_${task.id}.m4a")
        val outTmp = File(context.cacheDir, "mux_out_${task.id}.mp4")
        try {
            val vBytes = downloadUrlToFile(videoUrl, videoTmp, task.id, 0, 75)
            val aBytes = downloadUrlToFile(audioUrl, audioTmp, task.id, 75, 92)
            updateTask(task.id) { it.copy(progress = 95, bytesDownloaded = vBytes + aBytes) }
            updateNotifications()
            Mp4Muxer.muxVideoAndAudio(videoTmp, audioTmp, outTmp)

            val finalLen = outTmp.length()
            val baseName = task.filename.substringBeforeLast(".")
            val (localPath, uriStr) = outTmp.inputStream().use { inp ->
                val cfgSnap = settings.current
                val customSub = if (cfgSnap.downloadSubfolderMode == DownloadSubfolderMode.BY_PLATFORM) {
                    task.platform.displayName
                } else null
                mediaRepo.saveStreamToDownloads(
                    baseName = baseName,
                    extension = "mp4",
                    category = MediaCategory.VIDEO,
                    mimeType = "video/mp4",
                    totalBytes = finalLen,
                    inputStream = inp,
                    overwriteMode = cfgSnap.overwriteMode,
                    customTreeUri = cfgSnap.effectiveTreeUriFor(task.category),
                    customSubfolder = customSub,
                    onProgress = { _, _ -> },
                    isCancelled = { cancelledIds.contains(task.id) },
                )
            }
            updateTask(task.id) { it.copy(progress = 100, bytesDownloaded = finalLen, totalBytes = finalLen) }
            return Triple(localPath, uriStr, finalLen)
        } finally {
            runCatching { videoTmp.delete() }
            runCatching { audioTmp.delete() }
            runCatching { outTmp.delete() }
        }
    }

    private fun downloadUrlToFile(
        url: String,
        target: File,
        taskId: String,
        pctStart: Int,
        pctEnd: Int,
    ): Long {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", YouTubeScraper.ANDROID_VR_UA)
            .header("Accept", "*/*")
            .build()
        scraper.http.downloadClient().newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw IllegalStateException("HTTP ${res.code} while downloading stream")
            val body = res.body ?: throw IllegalStateException("Empty stream body")
            val totalLen = body.contentLength()
            var dl = 0L
            var lastRep = 0L
            body.byteStream().use { inp ->
                FileOutputStream(target).use { out ->
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        if (cancelledIds.contains(taskId)) throw InterruptedException("Cancelled by user")
                        val n = inp.read(buf)
                        if (n == -1) break
                        out.write(buf, 0, n)
                        dl += n
                        val now = System.currentTimeMillis()
                        if (now - lastRep >= 250) {
                            val pct = if (totalLen > 0) {
                                pctStart + (((dl.toDouble() / totalLen) * (pctEnd - pctStart)).toInt())
                            } else pctStart
                            updateTask(taskId) {
                                it.copy(progress = pct.coerceIn(pctStart, pctEnd), bytesDownloaded = dl)
                            }
                            updateNotifications()
                            lastRep = now
                        }
                    }
                    out.flush()
                }
            }
            return dl
        }
    }

    private fun fetchCoverArt(thumbnailUrl: String): Pair<ByteArray?, String> {
        if (!thumbnailUrl.startsWith("http", ignoreCase = true)) return null to "image/jpeg"
        return runCatching {
            val req = Request.Builder()
                .url(thumbnailUrl)
                .header("User-Agent", scraper.http.effectiveUserAgent())
                .build()
            scraper.http.downloadClient().newCall(req).execute().use { res ->
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

    private fun buildHeaderCandidates(url: String, initialReferer: String): List<Pair<String, String>> {
        val defaultUa = scraper.http.effectiveUserAgent()
        val lower = url.lowercase(Locale.US)
        val list = mutableListOf<Pair<String, String>>()
        if (lower.contains("googlevideo.com")) {
            list.add(YouTubeScraper.ANDROID_VR_UA to "")
        }
        list.add(defaultUa to initialReferer)
        if (lower.contains("tiktok") || lower.contains("muscdn") || lower.contains("byteoversea")) {
            list.add(HttpClientFactory.CHROME_DESKTOP_UA to "https://www.tiktok.com/")
        }
        if (initialReferer.isNotBlank()) {
            list.add(HttpClientFactory.CHROME_DESKTOP_UA to "")
        }
        extractOrigin(url)?.let { origin ->
            list.add(HttpClientFactory.CHROME_DESKTOP_UA to "$origin/")
        }
        list.add(HttpClientFactory.SAFARI_MOBILE_UA to "")
        return list.distinct()
    }

    private fun extractOrigin(url: String): String? = runCatching {
        val uri = java.net.URI(url)
        if (!uri.scheme.isNullOrBlank() && !uri.host.isNullOrBlank()) {
            "${uri.scheme}://${uri.host}"
        } else null
    }.getOrNull()

    private fun updateTask(id: String, updater: (DownloadTask) -> DownloadTask) {
        _tasks.update { list -> list.map { if (it.id == id) updater(it) else it } }
    }

    private fun updateNotifications() {
        val list = _tasks.value
        val mainTasks = list.filter { !it.fromQuickSave }
        val mainActive = mainTasks.filter {
            it.status == DownloadStatus.QUEUED ||
                it.status == DownloadStatus.RESOLVING ||
                it.status == DownloadStatus.DOWNLOADING
        }
        val qsTasks = list.filter { it.fromQuickSave }
        val qsActive = qsTasks.filter {
            it.status == DownloadStatus.QUEUED ||
                it.status == DownloadStatus.RESOLVING ||
                it.status == DownloadStatus.DOWNLOADING
        }

        if (mainActive.isNotEmpty()) {
            val done = mainTasks.count { it.status == DownloadStatus.COMPLETED }
            val total = mainActive.size + done
            val avgPct = mainActive.map { it.progress }.average().toInt().coerceIn(0, 100)
            DownloadForegroundService.startOrUpdate(
                context = context,
                completed = done,
                total = total,
                percent = avgPct,
                quickSaveOnly = false,
            )
            if (qsActive.isNotEmpty()) {
                val qDone = qsTasks.count { it.status == DownloadStatus.COMPLETED }
                val qTotal = qsActive.size + qDone
                val qPct = qsActive.map { it.progress }.average().toInt().coerceIn(0, 100)
                notifHelper.updateQuickSaveProgress(qDone, qTotal, qPct, isOngoing = true)
            } else {
                notifHelper.cancelQuickSave()
            }
        } else if (qsActive.isNotEmpty()) {
            val qDone = qsTasks.count { it.status == DownloadStatus.COMPLETED }
            val qTotal = qsActive.size + qDone
            val qPct = qsActive.map { it.progress }.average().toInt().coerceIn(0, 100)
            DownloadForegroundService.startOrUpdate(
                context = context,
                completed = qDone,
                total = qTotal,
                percent = qPct,
                quickSaveOnly = true,
            )
            notifHelper.cancelQuickSave()
        } else {
            DownloadForegroundService.stop(context)
            notifHelper.cancelMain()
            notifHelper.cancelQuickSave()
        }
    }

    private fun isWifiConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun playCompletionChime() {
        runCatching {
            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 55)
            tg.startTone(ToneGenerator.TONE_PROP_ACK, 160)
            scope.launch {
                kotlinx.coroutines.delay(250L)
                runCatching { tg.release() }
            }
        }
    }
}

