package com.nimiyo.nativeapp.player

import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.media.AudioAttributes as AndroidAudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.nimiyo.nativeapp.MainActivity
import com.nimiyo.nativeapp.NimiyoAppContainer
import com.nimiyo.nativeapp.R
import com.nimiyo.nativeapp.core.StringsI18n
import com.nimiyo.nativeapp.data.AudioTrack
import com.nimiyo.nativeapp.download.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MusicPlaybackService : Service() {

    companion object {
        private const val TAG = "MusicPlaybackService"
        const val ACTION_PLAY = "com.nimiyo.nativeapp.ACTION_PLAY"
        const val ACTION_PAUSE = "com.nimiyo.nativeapp.ACTION_PAUSE"
        const val ACTION_PLAY_PAUSE = "com.nimiyo.nativeapp.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.nimiyo.nativeapp.ACTION_NEXT"
        const val ACTION_PREV = "com.nimiyo.nativeapp.ACTION_PREV"
        const val ACTION_SHUFFLE = "com.nimiyo.nativeapp.ACTION_SHUFFLE"
        const val ACTION_REPEAT = "com.nimiyo.nativeapp.ACTION_REPEAT"
        const val ACTION_STOP = "com.nimiyo.nativeapp.ACTION_STOP"
    }

    inner class LocalBinder : Binder() {
        fun getService(): MusicPlaybackService = this@MusicPlaybackService
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var artworkJob: Job? = null
    private var cachedArtworkTrackId: String? = null
    private var cachedArtworkBitmap: Bitmap? = null
    private val defaultLargeIcon: Bitmap? by lazy {
        runCatching {
            BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)
                ?: ContextCompat.getDrawable(this, R.mipmap.ic_launcher)?.let { drawable ->
                    val size = 192
                    Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bmp ->
                        val canvas = Canvas(bmp)
                        drawable.setBounds(0, 0, canvas.width, canvas.height)
                        drawable.draw(canvas)
                    }
                }
        }.getOrNull()
    }
    private var exoPlayer: ExoPlayer? = null
    private val dspProcessor = NimiyoDspAudioProcessor()
    private lateinit var mediaSession: MediaSessionCompat

    var onCompletionCallback: (() -> Unit)? = null
    var onActionCallback: ((String) -> Unit)? = null
    var onErrorCallback: (() -> Unit)? = null
    var onPlayingStateChanged: ((Boolean) -> Unit)? = null

    private var currentTrack: AudioTrack? = null
    private var currentSpeed: Float = 1.0f
    private var currentReverbEnabled: Boolean = false
    private var currentReverbDryWet: Float = 0.5f
    private var currentReverbDepth: Float = 0.5f
    private var currentShuffle: Boolean = false
    private var currentRepeatMode: RepeatMode = RepeatMode.NONE
    private var isForegroundStarted = false
    @Volatile
    private var playerReady = false

    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var pausedByTransientFocus = false
    private var isReceiverRegistered = false

    private fun appSettings() = NimiyoAppContainer.get(this).settings.current

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        val cfg = appSettings()
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                if (!cfg.musicAlwaysPlayOnTop) {
                    pausedByTransientFocus = false
                    pauseOnly()
                    onPlayingStateChanged?.invoke(false)
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (!cfg.musicAlwaysPlayOnTop && isPlaying()) {
                    pausedByTransientFocus = true
                    pauseOnly()
                    onPlayingStateChanged?.invoke(false)
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (!cfg.musicAlwaysPlayOnTop) {
                    exoPlayer?.volume = 0.25f
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                exoPlayer?.volume = 1.0f
                if (pausedByTransientFocus) {
                    pausedByTransientFocus = false
                    if (cfg.musicResumeAfterCall && exoPlayer != null && currentTrack != null) {
                        playOnly()
                        onPlayingStateChanged?.invoke(true)
                    }
                }
            }
        }
    }

    private val musicEventsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            val cfg = appSettings()
            when (action) {
                AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                    if (cfg.musicPauseAtDisconnect && isPlaying()) {
                        pausedByTransientFocus = false
                        pauseOnly()
                        onPlayingStateChanged?.invoke(false)
                    }
                }
                Intent.ACTION_BATTERY_LOW -> {
                    if (cfg.musicStopOnLowBattery && isPlaying()) {
                        pausedByTransientFocus = false
                        pauseOnly()
                        onPlayingStateChanged?.invoke(false)
                    }
                }
                Intent.ACTION_BATTERY_CHANGED -> {
                    if (cfg.musicStopOnLowBattery && isPlaying()) {
                        val status = intent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
                        val isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == android.os.BatteryManager.BATTERY_STATUS_FULL
                        val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                        val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                        if (!isCharging && level >= 0) {
                            val pct = (level * 100) / scale
                            if (pct <= cfg.musicLowBatteryPercent) {
                                pausedByTransientFocus = false
                                pauseOnly()
                                onPlayingStateChanged?.invoke(false)
                            }
                        }
                    }
                }
                Intent.ACTION_HEADSET_PLUG -> {
                    if (!isInitialStickyBroadcast && intent.getIntExtra("state", -1) == 1) {
                        if (cfg.musicAutoPlayHeadset && exoPlayer != null && currentTrack != null && !isPlaying()) {
                            playOnly()
                            onPlayingStateChanged?.invoke(true)
                        }
                    }
                }
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
                    if (state == BluetoothProfile.STATE_CONNECTED) {
                        if (cfg.musicAutoPlayBluetooth && exoPlayer != null && currentTrack != null && !isPlaying()) {
                            playOnly()
                            onPlayingStateChanged?.invoke(true)
                        }
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val initCfg = appSettings()
        dspProcessor.setMonoAudio(initCfg.musicMonoAudio)
        dspProcessor.setLoudnessParams(initCfg.musicLoudnessNorm, initCfg.musicGainDb)
        val filter = IntentFilter().apply {
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
        }
        runCatching {
            ContextCompat.registerReceiver(this, musicEventsReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
            isReceiverRegistered = true
        }
        mediaSession = MediaSessionCompat(this, "NimiyoNativeMusic").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    onActionCallback?.invoke(ACTION_PLAY) ?: playOnly()
                }

                override fun onPause() {
                    onActionCallback?.invoke(ACTION_PAUSE) ?: pauseOnly()
                }

                override fun onStop() {
                    onActionCallback?.invoke(ACTION_STOP) ?: stopPlayback()
                }

                override fun onSkipToNext() {
                    onActionCallback?.invoke(ACTION_NEXT)
                }

                override fun onSkipToPrevious() {
                    onActionCallback?.invoke(ACTION_PREV)
                }

                override fun onSeekTo(pos: Long) {
                    seekTo(pos)
                }

                override fun onCustomAction(action: String?, extras: Bundle?) {
                    when (action) {
                        ACTION_SHUFFLE -> onActionCallback?.invoke(ACTION_SHUFFLE)
                        ACTION_REPEAT -> onActionCallback?.invoke(ACTION_REPEAT)
                    }
                }
            })
            isActive = true
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> onActionCallback?.invoke(ACTION_PLAY) ?: playOnly()
            ACTION_PAUSE -> onActionCallback?.invoke(ACTION_PAUSE) ?: pauseOnly()
            ACTION_PLAY_PAUSE -> onActionCallback?.invoke(ACTION_PLAY_PAUSE) ?: run { togglePlayPause() }
            ACTION_NEXT -> onActionCallback?.invoke(ACTION_NEXT)
            ACTION_PREV -> onActionCallback?.invoke(ACTION_PREV)
            ACTION_SHUFFLE -> onActionCallback?.invoke(ACTION_SHUFFLE)
            ACTION_REPEAT -> onActionCallback?.invoke(ACTION_REPEAT)
            ACTION_STOP -> onActionCallback?.invoke(ACTION_STOP) ?: stopPlayback()
        }
        return START_NOT_STICKY
    }

    @OptIn(UnstableApi::class)
    private fun ensurePlayer(): ExoPlayer {
        exoPlayer?.let { return it }
        val attrs = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(dspProcessor))
                    .build()
            }
        }
        return ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(attrs, false)
            .setHandleAudioBecomingNoisy(false)
            .build()
            .also { player ->
                player.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_READY -> {
                                playerReady = true
                                applySpeedAndReverb(player)
                                val dur = player.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: currentTrack?.durationMs ?: 0L
                                currentTrack?.let { updateMediaSessionAndNotification(it, player.isPlaying, dur) }
                            }
                            Player.STATE_ENDED -> {
                                onCompletionCallback?.invoke()
                            }
                            else -> Unit
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        val dur = player.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: currentTrack?.durationMs ?: 0L
                        val effectivePlaying = isPlaying || (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING)
                        currentTrack?.let { updateMediaSessionAndNotification(it, effectivePlaying, dur) }
                        if (player.playbackState == Player.STATE_READY) {
                            onPlayingStateChanged?.invoke(isPlaying)
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.e(TAG, "ExoPlayer error for ${currentTrack?.title}: ${error.errorCodeName}", error)
                        // Retry direct file path if content URI failed or vice-versa
                        val t = currentTrack ?: run {
                            onErrorCallback?.invoke()
                            return
                        }
                        val fallbackFile = if (t.path.isNotBlank()) File(t.path) else null
                        if (fallbackFile != null && fallbackFile.exists()) {
                            val currentUriStr = player.currentMediaItem?.localConfiguration?.uri?.toString()
                            if (currentUriStr != Uri.fromFile(fallbackFile).toString()) {
                                val retried = runCatching {
                                    player.setMediaItem(MediaItem.fromUri(Uri.fromFile(fallbackFile)))
                                    player.prepare()
                                    player.play()
                                }
                                if (retried.isSuccess) return
                            }
                        }
                        playerReady = false
                        onErrorCallback?.invoke()
                    }
                })
                exoPlayer = player
            }
    }

    fun updatePlaybackModes(shuffle: Boolean, repeatMode: RepeatMode) {
        currentShuffle = shuffle
        currentRepeatMode = repeatMode
        val t = currentTrack ?: return
        val dur = durationMs()
        updateMediaSessionAndNotification(t, isPlaying(), dur)
    }

    fun playTrack(
        track: AudioTrack,
        speed: Float,
        reverbEnabled: Boolean,
        reverbDryWet: Float,
        reverbDepth: Float,
    ) {
        currentTrack = track
        currentSpeed = speed
        currentReverbEnabled = reverbEnabled
        currentReverbDryWet = reverbDryWet
        currentReverbDepth = reverbDepth
        playerReady = false

        if (cachedArtworkTrackId != track.id) {
            cachedArtworkTrackId = track.id
            cachedArtworkBitmap = null
            loadTrackArtworkAsync(track)
        }

        val player = ensurePlayer()
        val uri = when {
            track.contentUri.isNotBlank() -> Uri.parse(track.contentUri)
            track.path.isNotBlank() -> Uri.fromFile(File(track.path))
            else -> return
        }

        if (!isForegroundStarted) {
            runCatching {
                ContextCompat.startForegroundService(
                    applicationContext,
                    Intent(applicationContext, MusicPlaybackService::class.java),
                )
            }
        }
        player.setMediaItem(MediaItem.fromUri(uri))
        requestAudioFocusIfNeeded()
        applySpeedAndReverb(player)
        player.prepare()
        player.playWhenReady = true
        updateMediaSessionAndNotification(track, true, track.durationMs)
    }

    private fun requestAudioFocusIfNeeded() {
        if (appSettings().musicAlwaysPlayOnTop) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = audioFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AndroidAudioAttributes.Builder()
                        .setUsage(AndroidAudioAttributes.USAGE_MEDIA)
                        .setContentType(AndroidAudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener(audioFocusListener)
                .build()
                .also { audioFocusRequest = it }
            runCatching { audioManager.requestAudioFocus(req) }
        } else {
            @Suppress("DEPRECATION")
            runCatching {
                audioManager.requestAudioFocus(
                    audioFocusListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN,
                )
            }
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { runCatching { audioManager.abandonAudioFocusRequest(it) } }
        } else {
            @Suppress("DEPRECATION")
            runCatching { audioManager.abandonAudioFocus(audioFocusListener) }
        }
    }

    private fun loadTrackArtworkAsync(track: AudioTrack) {
        artworkJob?.cancel()
        artworkJob = serviceScope.launch {
            val bmp = withContext(Dispatchers.IO) { decodeTrackArtwork(track) }
            if (currentTrack?.id == track.id) {
                cachedArtworkTrackId = track.id
                cachedArtworkBitmap = bmp
                updateMediaSessionAndNotification(track, isPlaying(), durationMs())
            }
        }
    }

    private fun decodeTrackArtwork(track: AudioTrack): Bitmap? {
        // 1. Always prefer per-file embedded ID3 / MP4 artwork first
        val mmr = MediaMetadataRetriever()
        val embeddedBmp = try {
            val setOk = when {
                track.path.isNotBlank() && File(track.path).exists() -> {
                    mmr.setDataSource(track.path)
                    true
                }
                track.contentUri.isNotBlank() -> {
                    mmr.setDataSource(this@MusicPlaybackService, Uri.parse(track.contentUri))
                    true
                }
                else -> false
            }
            if (setOk) {
                mmr.embeddedPicture?.let { bytes ->
                    decodeSampledBitmapFromBytes(bytes, 512)?.let { return@let scaleBitmapMax(it, 512) }
                }
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { mmr.release() }
        }
        if (embeddedBmp != null) return embeddedBmp

        // 2. Fallback to MediaStore albumArtUri (unwrap nimiyoart:// fallback if present)
        val fallbackUri = if (track.albumArtUri.startsWith("${AudioArtworkFetcher.SCHEME}://")) {
            Uri.parse(track.albumArtUri).getQueryParameter("fallback").orEmpty()
        } else {
            track.albumArtUri
        }
        if (fallbackUri.isNotBlank()) {
            runCatching {
                decodeSampledBitmapFromUri(Uri.parse(fallbackUri), 512)?.let { return scaleBitmapMax(it, 512) }
            }
        }
        return null
    }

    private fun computeInSampleSize(rawW: Int, rawH: Int, maxDim: Int): Int {
        var inSampleSize = 1
        while ((rawW / inSampleSize) > maxDim * 2 || (rawH / inSampleSize) > maxDim * 2) {
            inSampleSize *= 2
        }
        return inSampleSize.coerceAtLeast(1)
    }

    private fun decodeSampledBitmapFromBytes(bytes: ByteArray, maxDim: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, maxDim)
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    private fun decodeSampledBitmapFromUri(uri: Uri, maxDim: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, maxDim)
        }
        return contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, opts)
        }
    }

    private fun scaleBitmapMax(src: Bitmap, maxDim: Int): Bitmap {
        val w = src.width
        val h = src.height
        if (w <= maxDim && h <= maxDim) return src
        val ratio = maxDim.toFloat() / maxOf(w, h).toFloat()
        val nw = (w * ratio).toInt().coerceAtLeast(1)
        val nh = (h * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, nw, nh, true)
    }

    @OptIn(UnstableApi::class)
    private fun applySpeedAndReverb(player: ExoPlayer) {
        val spd = currentSpeed.coerceIn(0.60f, 1.40f)
        // True Slowed / SpeedUp: link pitch proportionally to speed (like preservesPitch = false)
        val pitch = if (kotlin.math.abs(spd - 1.0f) > 0.005f) spd else 1.0f
        runCatching {
            player.playbackParameters = PlaybackParameters(spd, pitch)
        }
        val cfg = appSettings()
        dspProcessor.setReverbParams(currentReverbEnabled, currentReverbDryWet, currentReverbDepth)
        dspProcessor.setMonoAudio(cfg.musicMonoAudio)
        dspProcessor.setLoudnessParams(cfg.musicLoudnessNorm, cfg.musicGainDb)
    }

    fun setMonoAudio(enabled: Boolean) {
        dspProcessor.setMonoAudio(enabled)
    }

    fun setLoudnessParams(normalize: Boolean, gainDb: Int) {
        dspProcessor.setLoudnessParams(normalize, gainDb)
    }

    fun updateFx(
        speed: Float,
        reverbEnabled: Boolean,
        reverbDryWet: Float,
        reverbDepth: Float,
    ) {
        currentSpeed = speed
        currentReverbEnabled = reverbEnabled
        currentReverbDryWet = reverbDryWet
        currentReverbDepth = reverbDepth
        exoPlayer?.let { applySpeedAndReverb(it) } ?: run {
            dspProcessor.setReverbParams(reverbEnabled, reverbDryWet, reverbDepth)
        }
    }

    fun togglePlayPause(): Boolean {
        val player = exoPlayer ?: return false
        return if (player.isPlaying) {
            pausedByTransientFocus = false
            player.pause()
            false
        } else {
            requestAudioFocusIfNeeded()
            player.play()
            true
        }
    }

    fun isPrepared(): Boolean = playerReady
    fun isPlaying(): Boolean = exoPlayer?.isPlaying == true
    fun isPlaybackActive(): Boolean = exoPlayer?.let {
        it.isPlaying || (it.playWhenReady && it.playbackState == Player.STATE_BUFFERING)
    } == true
    fun currentPositionMs(): Long = (exoPlayer?.currentPosition ?: 0L).coerceAtLeast(0L)
    fun durationMs(): Long {
        val d = exoPlayer?.duration ?: C.TIME_UNSET
        return if (d != C.TIME_UNSET && d > 0L) d else (currentTrack?.durationMs ?: 0L)
    }

    fun seekTo(ms: Long) {
        val player = exoPlayer ?: return
        player.seekTo(ms.coerceAtLeast(0L))
        val effectivePlaying = player.isPlaying || (player.playWhenReady && player.playbackState != Player.STATE_ENDED)
        currentTrack?.let { updateMediaSessionAndNotification(it, effectivePlaying, durationMs()) }
    }

    fun playOnly() {
        requestAudioFocusIfNeeded()
        exoPlayer?.let { if (!it.isPlaying) it.play() }
    }

    fun pauseOnly() {
        exoPlayer?.let { if (it.isPlaying) it.pause() }
    }

    fun setPlaybackVolume(volume: Float) {
        exoPlayer?.volume = volume.coerceIn(0f, 1f)
    }

    private fun updateMediaSessionAndNotification(track: AudioTrack, playing: Boolean, durMs: Long) {
        val artwork = if (cachedArtworkTrackId == track.id) cachedArtworkBitmap else null
        val fallbackLargeIcon = artwork ?: defaultLargeIcon

        val metaBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.album)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durMs.coerceAtLeast(track.durationMs))
        if (artwork != null) {
            metaBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artwork)
        }
        mediaSession.setMetadata(metaBuilder.build())

        val strings = StringsI18n.get(NimiyoAppContainer.get(this).settings.current.language)
        val shuffleIconRes = if (currentShuffle) R.drawable.ic_shuffle else R.drawable.ic_shuffle_off
        val shuffleLabel = if (currentShuffle) strings.ui.shuffleOnLabel else strings.ui.shuffleOffLabel
        val repeatIconRes = when (currentRepeatMode) {
            RepeatMode.NONE -> R.drawable.ic_repeat_off
            RepeatMode.ALL -> R.drawable.ic_repeat
            RepeatMode.ONE -> R.drawable.ic_repeat_one
        }
        val repeatLabel = when (currentRepeatMode) {
            RepeatMode.NONE -> strings.ui.repeatOffLabel
            RepeatMode.ALL -> strings.ui.repeatAllLabel
            RepeatMode.ONE -> strings.ui.repeatOneLabel
        }

        val state = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_SEEK_TO
            )
            .addCustomAction(ACTION_SHUFFLE, shuffleLabel, shuffleIconRes)
            .addCustomAction(ACTION_REPEAT, repeatLabel, repeatIconRes)
            .setState(
                if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                currentPositionMs(),
                if (playing) currentSpeed else 0f,
            )
            .build()
        mediaSession.setPlaybackState(state)

        val openIntent = PendingIntent.getActivity(
            this,
            8803,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(NotificationHelper.EXTRA_OPEN_PLAYER, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val shufflePi = servicePi(ACTION_SHUFFLE, 4)
        val prevPi = servicePi(ACTION_PREV, 1)
        val playPausePi = servicePi(ACTION_PLAY_PAUSE, 2)
        val nextPi = servicePi(ACTION_NEXT, 3)
        val repeatPi = servicePi(ACTION_REPEAT, 5)

        val builder = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_MUSIC)
            .setSmallIcon(R.drawable.ic_music_note)
            .setLargeIcon(fallbackLargeIcon)
            .setContentTitle(track.title)
            .setContentText(track.artist)
            .setContentIntent(openIntent)
            .setOngoing(playing)
            .setOnlyAlertOnce(true)
            .addAction(shuffleIconRes, shuffleLabel, shufflePi)
            .addAction(android.R.drawable.ic_media_previous, strings.ui.prevTrackLabel, prevPi)
            .addAction(
                if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (playing) strings.ui.pauseTrackLabel else strings.ui.playTrackLabel,
                playPausePi,
            )
            .addAction(android.R.drawable.ic_media_next, strings.ui.nextTrackLabel, nextPi)
            .addAction(repeatIconRes, repeatLabel, repeatPi)
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(1, 2, 3)
            )

        val notif = builder.build()
        if (!isForegroundStarted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NotificationHelper.NOTIF_ID_MUSIC,
                    notif,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
                )
            } else {
                startForeground(NotificationHelper.NOTIF_ID_MUSIC, notif)
            }
            isForegroundStarted = true
        } else {
            val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.notify(NotificationHelper.NOTIF_ID_MUSIC, notif)
        }
    }

    private fun servicePi(action: String, code: Int): PendingIntent {
        val intent = Intent(this, MusicPlaybackService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            code,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun releasePlayer() {
        playerReady = false
        dspProcessor.setReverbParams(false, currentReverbDryWet, currentReverbDepth)
        runCatching { exoPlayer?.release() }
        exoPlayer = null
    }

    fun stopPlayback() {
        releasePlayer()
        if (isForegroundStarted) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isForegroundStarted = false
        }
        stopSelf()
    }

    override fun onDestroy() {
        if (isReceiverRegistered) {
            runCatching { unregisterReceiver(musicEventsReceiver) }
            isReceiverRegistered = false
        }
        abandonAudioFocus()
        artworkJob?.cancel()
        releasePlayer()
        if (isForegroundStarted) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isForegroundStarted = false
        }
        serviceScope.cancel()
        runCatching { mediaSession.release() }
        super.onDestroy()
    }
}
