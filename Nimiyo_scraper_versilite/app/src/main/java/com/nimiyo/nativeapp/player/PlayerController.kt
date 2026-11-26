package com.nimiyo.nativeapp.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import com.nimiyo.nativeapp.data.AudioTrack
import com.nimiyo.nativeapp.data.MediaStoreRepository
import com.nimiyo.nativeapp.data.PlaylistModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

enum class RepeatMode {
    NONE, ALL, ONE
}

enum class SortOrder {
    DATE_DESC, DATE_ASC, TITLE_ASC, TITLE_DESC, ARTIST_ASC, DURATION_DESC
}

enum class LyricsDisplayMode {
    LRC, TTML
}

@androidx.compose.runtime.Immutable
data class PlayerUiState(
    val tracks: List<AudioTrack> = emptyList(),
    val playlists: List<PlaylistModel> = emptyList(),
    val queue: List<AudioTrack> = emptyList(),
    val currentIndex: Int = -1,
    val currentTrack: AudioTrack? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.NONE,
    val sortOrder: SortOrder = SortOrder.DATE_DESC,
    val speed: Float = 1.0f,
    val reverbEnabled: Boolean = false,
    val reverbDryWet: Float = 0.5f,
    val reverbDepth: Float = 0.5f,
    val sleepTimerRemainingSec: Int = 0,
    val lyricsDoc: LyricsDocument? = null,
    val ttmlDoc: TtmlDocument? = null,
    val ttmlLines: List<TtmlDisplayLine> = emptyList(),
    val lyricsDisplayMode: LyricsDisplayMode = LyricsDisplayMode.LRC,
    val lyricsLoading: Boolean = false,
    val nowPlayingOpen: Boolean = false,
    val miniPlayerMinimized: Boolean = false,
    val isScanning: Boolean = false,
    val activePlaylistId: String? = null,
    val activePlaylistName: String? = null,
    val hideMiniPlayer: Boolean = false,
)

class PlayerController(
    private val context: Context,
    private val mediaRepo: MediaStoreRepository,
    private val lyricsRepo: LyricsRepository,
) {
    private val prefs = context.getSharedPreferences("nimiyo_player_prefs", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow(
        PlayerUiState(
            lyricsDisplayMode = if (prefs.getString("lyrics_display_mode", "lrc") == "ttml") {
                LyricsDisplayMode.TTML
            } else {
                LyricsDisplayMode.LRC
            }
        )
    )
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()
    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private var service: MusicPlaybackService? = null
    private var isServiceBound = false
    private var pendingTrackOnBind: AudioTrack? = null
    private var tickerJob: Job? = null
    private var sleepJob: Job? = null
    private var lyricsJob: Job? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val srv = (binder as MusicPlaybackService.LocalBinder).getService()
            srv.onCompletionCallback = { scope.launch { onTrackCompleted() } }
            srv.onErrorCallback = {
                scope.launch {
                    _state.value = _state.value.copy(isPlaying = false)
                }
            }
            srv.onPlayingStateChanged = { playing ->
                scope.launch {
                    if (_state.value.isPlaying != playing) {
                        _state.value = _state.value.copy(isPlaying = playing)
                    }
                    if (playing) ensureProgressTickerRunning()
                }
            }
            srv.onActionCallback = { act ->
                scope.launch {
                    when (act) {
                        MusicPlaybackService.ACTION_PLAY -> {
                            srv.playOnly()
                            _state.value = _state.value.copy(isPlaying = true)
                            ensureProgressTickerRunning()
                        }
                        MusicPlaybackService.ACTION_PAUSE -> {
                            srv.pauseOnly()
                            _state.value = _state.value.copy(isPlaying = false)
                        }
                        MusicPlaybackService.ACTION_PLAY_PAUSE -> togglePlayPause()
                        MusicPlaybackService.ACTION_NEXT -> skipNext()
                        MusicPlaybackService.ACTION_PREV -> skipPrev()
                        MusicPlaybackService.ACTION_SHUFFLE -> toggleShuffle()
                        MusicPlaybackService.ACTION_REPEAT -> cycleRepeat()
                        MusicPlaybackService.ACTION_STOP -> stopAndClosePlayer()
                    }
                }
            }
            service = srv
            srv.updatePlaybackModes(_state.value.shuffle, _state.value.repeatMode)
            pendingTrackOnBind?.let {
                pendingTrackOnBind = null
                val s = _state.value
                srv.playTrack(it, s.speed, s.reverbEnabled, s.reverbDryWet, s.reverbDepth)
                ensureProgressTickerRunning()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            isServiceBound = false
        }
    }

    init {
        refreshLibrary()
    }

    private fun ensureServiceBound() {
        if (service != null || isServiceBound) return
        val intent = Intent(context, MusicPlaybackService::class.java)
        isServiceBound = runCatching {
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }.getOrDefault(false)
    }

    fun refreshLibrary() {
        scope.launch {
            _state.value = _state.value.copy(isScanning = true)
            val tracks = applySort(mediaRepo.scanAudioLibrary(), _state.value.sortOrder)
            val playlists = mediaRepo.loadPlaylists()
            _state.value = _state.value.copy(
                tracks = tracks,
                playlists = playlists,
                isScanning = false,
            )
        }
    }

    fun setSortOrder(order: SortOrder) {
        _state.value = _state.value.copy(
            sortOrder = order,
            tracks = applySort(_state.value.tracks, order),
        )
    }

    private fun applySort(list: List<AudioTrack>, order: SortOrder): List<AudioTrack> = when (order) {
        SortOrder.DATE_DESC -> list.sortedByDescending { it.dateAddedSec }
        SortOrder.DATE_ASC -> list.sortedBy { it.dateAddedSec }
        SortOrder.TITLE_ASC -> list.sortedBy { it.title.lowercase() }
        SortOrder.TITLE_DESC -> list.sortedByDescending { it.title.lowercase() }
        SortOrder.ARTIST_ASC -> list.sortedBy { it.artist.lowercase() }
        SortOrder.DURATION_DESC -> list.sortedByDescending { it.durationMs }
    }

    fun setNowPlayingOpen(open: Boolean) {
        _state.value = _state.value.copy(nowPlayingOpen = open)
    }

    fun setMiniPlayerMinimized(minimized: Boolean) {
        _state.value = _state.value.copy(miniPlayerMinimized = minimized)
    }

    fun stopAndClosePlayer() {
        tickerJob?.cancel()
        tickerJob = null
        service?.stopPlayback()
        if (isServiceBound) {
            runCatching { context.unbindService(connection) }
            isServiceBound = false
        }
        service = null
        pendingTrackOnBind = null
        _positionMs.value = 0L
        _state.value = _state.value.copy(
            currentTrack = null,
            isPlaying = false,
            positionMs = 0L,
            nowPlayingOpen = false,
            miniPlayerMinimized = false,
            activePlaylistId = null,
            activePlaylistName = null,
        )
    }

    fun setMonoAudio(enabled: Boolean) {
        service?.setMonoAudio(enabled)
    }

    fun setLoudnessParams(normalize: Boolean, gainDb: Int) {
        service?.setLoudnessParams(normalize, gainDb)
    }

    fun setMiniPlayerHidden(hidden: Boolean) {
        if (_state.value.hideMiniPlayer != hidden) {
            _state.value = _state.value.copy(hideMiniPlayer = hidden)
        }
    }

    fun playFromList(list: List<AudioTrack>, startIndex: Int, playlist: PlaylistModel? = null) {
        if (list.isEmpty() || startIndex !in list.indices) return
        val queue = if (_state.value.shuffle) {
            val picked = list[startIndex]
            val rest = list.toMutableList().apply { removeAt(startIndex) }.shuffled()
            listOf(picked) + rest
        } else list
        val idx = if (_state.value.shuffle) 0 else startIndex
        _state.value = _state.value.copy(
            activePlaylistId = playlist?.id,
            activePlaylistName = playlist?.name,
        )
        playQueueIndex(queue, idx)
    }

    fun shuffleAll(list: List<AudioTrack> = _state.value.tracks, playlist: PlaylistModel? = null) {
        if (list.isEmpty()) return
        val shuffled = list.shuffled()
        _state.value = _state.value.copy(
            shuffle = true,
            activePlaylistId = playlist?.id,
            activePlaylistName = playlist?.name,
        )
        service?.updatePlaybackModes(true, _state.value.repeatMode)
        playQueueIndex(shuffled, 0)
    }

    private fun playQueueIndex(queue: List<AudioTrack>, index: Int) {
        val track = queue.getOrNull(index) ?: return
        ensureServiceBound()
        _positionMs.value = 0L
        _state.value = _state.value.copy(
            queue = queue,
            currentIndex = index,
            currentTrack = track,
            isPlaying = true,
            positionMs = 0L,
            durationMs = track.durationMs,
            lyricsDoc = null,
            ttmlDoc = null,
            ttmlLines = emptyList(),
        )
        if (service != null) {
            val s = _state.value
            service?.playTrack(track, s.speed, s.reverbEnabled, s.reverbDryWet, s.reverbDepth)
            ensureProgressTickerRunning()
        } else {
            pendingTrackOnBind = track
        }
        loadLyricsFor(track)
    }

    fun togglePlayPause() {
        val cur = _state.value.currentTrack ?: run {
            if (_state.value.tracks.isNotEmpty()) playFromList(_state.value.tracks, 0)
            return
        }
        val srv = service
        if (srv != null) {
            val nowPlaying = srv.togglePlayPause()
            _state.value = _state.value.copy(isPlaying = nowPlaying)
            if (nowPlaying) ensureProgressTickerRunning()
        } else {
            playQueueIndex(_state.value.queue.ifEmpty { listOf(cur) }, _state.value.currentIndex.coerceAtLeast(0))
        }
    }

    fun skipNext() {
        val q = _state.value.queue
        if (q.isEmpty()) return
        val nextIdx = if (_state.value.currentIndex + 1 < q.size) _state.value.currentIndex + 1 else 0
        playQueueIndex(q, nextIdx)
    }

    fun skipPrev() {
        val q = _state.value.queue
        if (q.isEmpty()) return
        if (_positionMs.value > 4000L) {
            seekTo(0L)
            return
        }
        val prevIdx = if (_state.value.currentIndex - 1 >= 0) _state.value.currentIndex - 1 else q.lastIndex
        playQueueIndex(q, prevIdx)
    }

    fun seekTo(ms: Long) {
        service?.seekTo(ms)
        _positionMs.value = ms
    }

    fun toggleShuffle() {
        val next = !_state.value.shuffle
        _state.value = _state.value.copy(shuffle = next)
        service?.updatePlaybackModes(next, _state.value.repeatMode)
    }

    fun cycleRepeat() {
        val next = when (_state.value.repeatMode) {
            RepeatMode.NONE -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.NONE
        }
        _state.value = _state.value.copy(repeatMode = next)
        service?.updatePlaybackModes(_state.value.shuffle, next)
    }

    fun setSpeedFx(speed: Float) {
        val clamped = speed.coerceIn(0.60f, 1.40f)
        val s = _state.value.copy(speed = clamped)
        _state.value = s
        service?.updateFx(clamped, s.reverbEnabled, s.reverbDryWet, s.reverbDepth)
    }

    fun setReverbEnabled(enabled: Boolean) {
        val s = _state.value.copy(reverbEnabled = enabled)
        _state.value = s
        service?.updateFx(s.speed, enabled, s.reverbDryWet, s.reverbDepth)
    }

    fun setReverbXY(dryWet: Float, depth: Float) {
        val dw = dryWet.coerceIn(0f, 1f)
        val dp = depth.coerceIn(0f, 1f)
        // Only updates X (Dry/Wet) and Y (Space/Decay); never alters speed!
        val s = _state.value.copy(reverbDryWet = dw, reverbDepth = dp)
        _state.value = s
        service?.updateFx(s.speed, s.reverbEnabled, dw, dp)
    }

    fun resetReverbXY() {
        setReverbXY(0.5f, 0.5f)
    }

    fun resetAllMixTools() {
        val s = _state.value.copy(
            speed = 1.0f,
            reverbEnabled = false,
            reverbDryWet = 0.5f,
            reverbDepth = 0.5f,
        )
        _state.value = s
        service?.updateFx(1.0f, false, 0.5f, 0.5f)
    }

    fun toggleLyricsDisplayMode() {
        val next = if (_state.value.lyricsDisplayMode == LyricsDisplayMode.TTML) {
            LyricsDisplayMode.LRC
        } else {
            LyricsDisplayMode.TTML
        }
        prefs.edit().putString("lyrics_display_mode", if (next == LyricsDisplayMode.TTML) "ttml" else "lrc").apply()
        _state.value = _state.value.copy(lyricsDisplayMode = next)
    }

    fun setSleepTimerMinutes(minutes: Int) {
        sleepJob?.cancel()
        service?.setPlaybackVolume(1f)
        if (minutes <= 0) {
            _state.value = _state.value.copy(sleepTimerRemainingSec = 0)
            return
        }
        var remaining = minutes * 60
        _state.value = _state.value.copy(sleepTimerRemainingSec = remaining)
        sleepJob = scope.launch {
            while (remaining > 0) {
                delay(1000L)
                remaining--
                _state.value = _state.value.copy(sleepTimerRemainingSec = remaining)
            }
            // Smooth 2.5s fade-out before pausing so playback never cuts abruptly
            val steps = 25
            for (s in steps - 1 downTo 0) {
                service?.setPlaybackVolume(s / steps.toFloat())
                delay(100L)
            }
            service?.pauseOnly()
            service?.setPlaybackVolume(1f)
            _state.value = _state.value.copy(isPlaying = false, sleepTimerRemainingSec = 0)
        }
    }

    private fun onTrackCompleted() {
        when (_state.value.repeatMode) {
            RepeatMode.ONE -> playQueueIndex(_state.value.queue, _state.value.currentIndex)
            RepeatMode.ALL -> skipNext()
            RepeatMode.NONE -> {
                val q = _state.value.queue
                if (_state.value.currentIndex + 1 < q.size) {
                    playQueueIndex(q, _state.value.currentIndex + 1)
                } else {
                    _state.value = _state.value.copy(isPlaying = false)
                }
            }
        }
    }

    fun loadLyricsFor(track: AudioTrack, forceOnline: Boolean = false) {
        lyricsJob?.cancel()
        lyricsJob = scope.launch {
            _state.value = _state.value.copy(lyricsLoading = true)
            // LRC channel (strictly LRC: local .lrc -> KuGou -> LRCLIB)
            val lrcJob = launch {
                val doc = if (forceOnline) lyricsRepo.searchOnlineLyrics(track) else lyricsRepo.loadLocalOrOnlineLyrics(track)
                if (_state.value.currentTrack?.id == track.id) {
                    _state.value = _state.value.copy(lyricsDoc = doc)
                }
            }
            // Dedicated TTML channel (strictly TTML: cached .ttml -> Boidu -> BetterLyrics -> Paxsenix Apple TTML)
            val ttmlJob = launch {
                val ttml = lyricsRepo.loadTtmlCachedOrOnline(track, forceOnline = forceOnline)
                if (_state.value.currentTrack?.id == track.id) {
                    _state.value = _state.value.copy(
                        ttmlDoc = ttml,
                        ttmlLines = ttml?.lines.orEmpty(),
                    )
                }
            }
            lrcJob.join()
            ttmlJob.join()
            if (_state.value.currentTrack?.id == track.id) {
                _state.value = _state.value.copy(lyricsLoading = false)
            }
        }
    }

    fun refreshTtmlOnline(track: AudioTrack) {
        scope.launch {
            _state.value = _state.value.copy(lyricsLoading = true)
            val ttml = lyricsRepo.loadTtmlCachedOrOnline(track, forceOnline = true)
            if (_state.value.currentTrack?.id == track.id) {
                if (ttml != null) {
                    _state.value = _state.value.copy(
                        ttmlDoc = ttml,
                        ttmlLines = ttml.lines,
                        lyricsLoading = false,
                    )
                } else {
                    _state.value = _state.value.copy(lyricsLoading = false)
                }
            }
        }
    }

    fun searchLyricsKuGou(track: AudioTrack, onDone: ((Boolean) -> Unit)? = null) {
        scope.launch {
            _state.value = _state.value.copy(lyricsLoading = true)
            val kg = runCatching { lyricsRepo.searchKuGouOnly(track) }.getOrNull()
            if (_state.value.currentTrack?.id == track.id) {
                if (kg != null && kg.lines.isNotEmpty()) {
                    runCatching { lyricsRepo.saveLocalLrc(track, kg.rawLrc) }
                    _state.value = _state.value.copy(lyricsDoc = kg, lyricsLoading = false)
                    onDone?.invoke(true)
                } else {
                    _state.value = _state.value.copy(lyricsLoading = false)
                    onDone?.invoke(false)
                }
            } else {
                _state.value = _state.value.copy(lyricsLoading = false)
                onDone?.invoke(kg != null)
            }
        }
    }

    fun searchLyricsLrcLib(track: AudioTrack, onDone: ((Boolean) -> Unit)? = null) {
        scope.launch {
            _state.value = _state.value.copy(lyricsLoading = true)
            val lrc = runCatching { lyricsRepo.searchLrcLibOnly(track) }.getOrNull()
            if (_state.value.currentTrack?.id == track.id) {
                if (lrc != null && lrc.lines.isNotEmpty()) {
                    runCatching { lyricsRepo.saveLocalLrc(track, lrc.rawLrc) }
                    _state.value = _state.value.copy(lyricsDoc = lrc, lyricsLoading = false)
                    onDone?.invoke(true)
                } else {
                    _state.value = _state.value.copy(lyricsLoading = false)
                    onDone?.invoke(false)
                }
            } else {
                _state.value = _state.value.copy(lyricsLoading = false)
                onDone?.invoke(lrc != null)
            }
        }
    }

    fun importLyricsContent(track: AudioTrack, content: String, onDone: ((Boolean) -> Unit)? = null) {
        scope.launch {
            val isTtml = content.contains("<tt", ignoreCase = true) || content.contains("<p", ignoreCase = true)
            if (isTtml) {
                lyricsRepo.saveLocalTtml(track, content)
                val doc = lyricsRepo.parseTtmlXmlToDocument(content, "Imported TTML")
                if (_state.value.currentTrack?.id == track.id) {
                    _state.value = _state.value.copy(
                        ttmlDoc = doc,
                        ttmlLines = doc.lines,
                        lyricsDisplayMode = LyricsDisplayMode.TTML,
                    )
                }
            } else {
                lyricsRepo.saveLocalLrc(track, content)
                val doc = lyricsRepo.parseLrcOrTtml(content, "Imported .lrc")
                prefs.edit().putString("lyrics_display_mode", "lrc").apply()
                if (_state.value.currentTrack?.id == track.id) {
                    val lines = lyricsRepo.toTtmlDisplayLines(doc)
                    _state.value = _state.value.copy(
                        lyricsDoc = doc,
                        ttmlLines = lines,
                        lyricsDisplayMode = LyricsDisplayMode.LRC,
                    )
                }
            }
            onDone?.invoke(true)
        }
    }

    suspend fun exportLyrics(track: AudioTrack, isTtml: Boolean): File? {
        val content = if (isTtml) {
            _state.value.ttmlDoc?.rawXml
        } else {
            _state.value.lyricsDoc?.rawLrc
        }
        if (content.isNullOrBlank()) return null
        return lyricsRepo.exportLyricsFile(track, isTtml, content)
    }

    fun saveCustomLyrics(track: AudioTrack, lrcText: String) {
        scope.launch {
            lyricsRepo.saveLocalLrc(track, lrcText)
            val parsed = lyricsRepo.parseLrcOrTtml(lrcText, "Local .lrc")
            prefs.edit().putString("lyrics_display_mode", "lrc").apply()
            val lines = lyricsRepo.toTtmlDisplayLines(parsed)
            _state.value = _state.value.copy(
                lyricsDoc = parsed,
                ttmlLines = lines,
                lyricsDisplayMode = LyricsDisplayMode.LRC,
            )
        }
    }

    fun saveCustomTtml(track: AudioTrack, ttmlXml: String) {
        scope.launch {
            lyricsRepo.saveLocalTtml(track, ttmlXml)
            val doc = lyricsRepo.parseTtmlXmlToDocument(ttmlXml, "Local TTML")
            if (_state.value.currentTrack?.id == track.id) {
                _state.value = _state.value.copy(
                    ttmlDoc = doc,
                    ttmlLines = doc.lines,
                    lyricsDisplayMode = LyricsDisplayMode.TTML,
                )
            }
        }
    }

    // ---- Playlist CRUD ----

    fun createPlaylist(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        scope.launch {
            val next = _state.value.playlists + PlaylistModel(
                id = UUID.randomUUID().toString(),
                name = clean,
                trackPaths = emptyList(),
            )
            _state.value = _state.value.copy(playlists = next)
            mediaRepo.savePlaylists(next)
        }
    }

    fun addTrackToPlaylist(playlistId: String, track: AudioTrack) {
        addTracksToPlaylist(playlistId, listOf(track))
    }

    private fun canonicalTrackId(track: AudioTrack): String {
        if (track.path.isNotBlank()) {
            return "file_${kotlin.math.abs(track.path.hashCode())}"
        }
        return track.id.ifBlank { track.contentUri }
    }

    fun addTracksToPlaylist(playlistId: String, tracks: List<AudioTrack>) {
        scope.launch {
            val newKeys = tracks.map(::canonicalTrackId).filter { it.isNotBlank() }
            val next = _state.value.playlists.map { pl ->
                if (pl.id == playlistId) {
                    val updated = (pl.trackPaths + newKeys).distinct()
                    pl.copy(trackPaths = updated, updatedAt = System.currentTimeMillis())
                } else pl
            }
            _state.value = _state.value.copy(playlists = next)
            mediaRepo.savePlaylists(next)
        }
    }

    fun resolvePlaylistTracks(playlist: PlaylistModel, allTracks: List<AudioTrack> = _state.value.tracks): List<AudioTrack> {
        if (playlist.trackPaths.isEmpty() || allTracks.isEmpty()) return emptyList()
        return playlist.trackPaths.mapNotNull { rawKey ->
            val key = rawKey.trim()
            if (key.isEmpty()) return@mapNotNull null
            val bareId = key.removePrefix("ms:").removePrefix("file:").removePrefix("file_")
            val keyFileName = key.substringAfterLast('/')
            allTracks.firstOrNull { t ->
                val legacyFileId = if (t.path.isNotBlank()) "file_${kotlin.math.abs(t.path.hashCode())}" else ""
                val legacyRawHash = if (t.path.isNotBlank()) kotlin.math.abs(t.path.hashCode()).toString() else ""
                t.id == key ||
                    t.id.removePrefix("ms:") == bareId ||
                    (legacyFileId.isNotBlank() && (legacyFileId == key || legacyRawHash == bareId)) ||
                    t.path == key ||
                    t.path == bareId ||
                    t.contentUri == key ||
                    (keyFileName.isNotBlank() && t.id.removePrefix("ms:") == keyFileName) ||
                    (keyFileName.isNotBlank() && t.contentUri.substringAfterLast('/') == bareId) ||
                    (keyFileName.isNotBlank() && t.path.substringAfterLast('/').equals(keyFileName, ignoreCase = true))
            }
        }.distinctBy { it.id }
    }

    fun playNext(track: AudioTrack) {
        val curQueue = _state.value.queue.toMutableList()
        if (curQueue.isEmpty() || _state.value.currentTrack == null) {
            playFromList(listOf(track), 0)
            return
        }
        val insertIdx = (_state.value.currentIndex + 1).coerceIn(0, curQueue.size)
        curQueue.add(insertIdx, track)
        _state.value = _state.value.copy(queue = curQueue)
    }

    fun addToQueue(track: AudioTrack) {
        val curQueue = _state.value.queue.toMutableList()
        if (curQueue.isEmpty() || _state.value.currentTrack == null) {
            playFromList(listOf(track), 0)
            return
        }
        curQueue.add(track)
        _state.value = _state.value.copy(queue = curQueue)
    }

    fun syncPlaylistsFromDatabase(onComplete: ((Int) -> Unit)? = null) {
        scope.launch {
            val diskPlaylists = mediaRepo.loadPlaylists()
            val merged = mutableListOf<PlaylistModel>()
            fun upsert(pl: PlaylistModel) {
                val byId = pl.id.trim().lowercase()
                val byName = pl.name.trim().lowercase()
                val idx = merged.indexOfFirst { ex ->
                    ex.id.trim().lowercase() == byId ||
                        (byName.isNotBlank() && ex.name.trim().lowercase() == byName)
                }
                if (idx < 0) {
                    merged.add(pl)
                } else {
                    val ex = merged[idx]
                    merged[idx] = ex.copy(
                        id = if (pl.id.isNotBlank()) pl.id else ex.id,
                        name = if (ex.name != ex.id) ex.name else pl.name,
                        trackPaths = (ex.trackPaths + pl.trackPaths).distinct(),
                        updatedAt = maxOf(ex.updatedAt, pl.updatedAt),
                    )
                }
            }
            diskPlaylists.forEach(::upsert)
            _state.value.playlists.forEach(::upsert)
            val sorted = merged.sortedByDescending { it.updatedAt }
            _state.value = _state.value.copy(playlists = sorted)
            mediaRepo.savePlaylists(sorted)
            withContext(Dispatchers.Main) {
                onComplete?.invoke(sorted.size)
            }
        }
    }

    fun deleteTracks(tracks: List<AudioTrack>, onComplete: ((Int) -> Unit)? = null) {
        scope.launch {
            val idsToDelete = tracks.map { it.id }.toSet()
            val deletedCount = withContext(Dispatchers.IO) {
                var count = 0
                tracks.forEach { track ->
                    if (mediaRepo.deleteAudioTrack(track)) count++
                }
                count
            }
            val currentWasDeleted = _state.value.currentTrack?.id in idsToDelete
            val updatedQueue = _state.value.queue.filterNot { it.id in idsToDelete }
            val updatedTracks = _state.value.tracks.filterNot { it.id in idsToDelete }
            _state.value = _state.value.copy(
                tracks = updatedTracks,
                queue = updatedQueue,
                currentIndex = _state.value.currentIndex.coerceAtMost((updatedQueue.size - 1).coerceAtLeast(0)),
            )
            if (currentWasDeleted) {
                if (updatedQueue.isNotEmpty()) {
                    playQueueIndex(updatedQueue, _state.value.currentIndex.coerceIn(0, updatedQueue.lastIndex))
                } else {
                    stopAndClosePlayer()
                }
            }
            refreshLibrary()
            onComplete?.invoke(deletedCount)
        }
    }

    fun moveTracksToNimiyo(tracks: List<AudioTrack>, onComplete: ((Int) -> Unit)? = null) {
        scope.launch {
            val movedCount = withContext(Dispatchers.IO) {
                var count = 0
                tracks.forEach { track ->
                    if (mediaRepo.moveAudioTrackToNimiyo(track)) count++
                }
                count
            }
            refreshLibrary()
            onComplete?.invoke(movedCount)
        }
    }

    fun removeTrackFromPlaylist(playlistId: String, trackKey: String) {
        scope.launch {
            val legacyHash = if (trackKey.startsWith("/")) "file_${kotlin.math.abs(trackKey.hashCode())}" else ""
            val next = _state.value.playlists.map { pl ->
                if (pl.id == playlistId) {
                    pl.copy(
                        trackPaths = pl.trackPaths.filterNot {
                            it == trackKey || (legacyHash.isNotBlank() && it == legacyHash)
                        },
                        updatedAt = System.currentTimeMillis(),
                    )
                } else pl
            }
            _state.value = _state.value.copy(playlists = next)
            mediaRepo.savePlaylists(next)
        }
    }

    fun deletePlaylist(playlistId: String) {
        scope.launch {
            mediaRepo.markPlaylistDeleted(playlistId)
            val next = _state.value.playlists.filterNot { it.id == playlistId }
            val clearedActive = _state.value.activePlaylistId == playlistId
            _state.value = _state.value.copy(
                playlists = next,
                activePlaylistId = if (clearedActive) null else _state.value.activePlaylistId,
                activePlaylistName = if (clearedActive) null else _state.value.activePlaylistName,
            )
            mediaRepo.savePlaylists(next)
        }
    }

    private fun ensureProgressTickerRunning() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            while (_state.value.currentTrack != null && _state.value.isPlaying) {
                val srv = service
                val curState = _state.value
                if (srv != null && curState.currentTrack != null && srv.isPrepared()) {
                    val playing = srv.isPlaybackActive()
                    val pos = srv.currentPositionMs()
                    val dur = srv.durationMs().let { if (it > 0L) it else curState.durationMs }
                    _positionMs.value = pos
                    if (playing != curState.isPlaying || dur != curState.durationMs) {
                        _state.value = curState.copy(isPlaying = playing, durationMs = dur)
                    }
                    if (!playing) break
                    delay(if (curState.nowPlayingOpen) 60L else 250L)
                } else {
                    delay(120L)
                }
            }
        }
    }
}

