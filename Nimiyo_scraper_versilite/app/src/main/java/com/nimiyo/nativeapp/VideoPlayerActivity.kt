package com.nimiyo.nativeapp

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.view.TextureView
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Forward5
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.nimiyo.nativeapp.core.StringsI18n
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.nimiyo.nativeapp.ui.components.formatDurationMs
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

class VideoPlayerActivity : ComponentActivity() {

    private data class VideoEntry(
        val path: String,
        val uriStr: String,
        val title: String,
    )

    private fun resolveUri(entry: VideoEntry): Uri? = when {
        entry.path.isNotBlank() && File(entry.path).exists() ->
            FileProvider.getUriForFile(this, "$packageName.fileprovider", File(entry.path))
        entry.uriStr.isNotBlank() -> Uri.parse(entry.uriStr)
        else -> null
    }

    private fun setImmersiveFullscreen(fullscreen: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (fullscreen) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val singlePath = intent.getStringExtra("path").orEmpty()
        val singleUri = intent.getStringExtra("uri").orEmpty()
        val singleTitle = intent.getStringExtra("title").orEmpty().ifBlank { "Nimiyo Video" }

        val paths = intent.getStringArrayListExtra("paths")
        val uris = intent.getStringArrayListExtra("uris")
        val titles = intent.getStringArrayListExtra("titles")
        val startIndexExtra = intent.getIntExtra("startIndex", 0)

        val playlist: List<VideoEntry> = if (!paths.isNullOrEmpty() || !uris.isNullOrEmpty()) {
            val count = maxOf(paths?.size ?: 0, uris?.size ?: 0)
            (0 until count).map { i ->
                VideoEntry(
                    path = paths?.getOrNull(i).orEmpty(),
                    uriStr = uris?.getOrNull(i).orEmpty(),
                    title = titles?.getOrNull(i).orEmpty().ifBlank { "Nimiyo Video" },
                )
            }
        } else {
            listOf(VideoEntry(singlePath, singleUri, singleTitle))
        }

        if (playlist.isEmpty() || resolveUri(playlist.first()) == null) {
            finish()
            return
        }

        val cfg = NimiyoAppContainer.get(this).settings.current
        val strings = StringsI18n.get(cfg.language)
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)

        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        setContent {
            BackHandler { finish() }

            val lifecycleOwner = LocalLifecycleOwner.current
            var currentIdx by remember { mutableIntStateOf(startIndexExtra.coerceIn(0, playlist.lastIndex)) }
            var showControls by remember { mutableStateOf(true) }
            var isPlaying by remember { mutableStateOf(cfg.autoPlayMedia) }
            var posMs by remember { mutableLongStateOf(0L) }
            var scrubTargetMs by remember { mutableStateOf<Long?>(null) }
            var durMs by remember { mutableLongStateOf(1L) }
            var videoAspect by remember { mutableFloatStateOf(16f / 9f) }
            var playerErrorMsg by remember { mutableStateOf<String?>(null) }

            var shuffleEnabled by remember { mutableStateOf(false) }
            var repeatOne by remember { mutableStateOf(cfg.autoLoopMedia) }
            var playbackSpeed by remember { mutableFloatStateOf(1f) }
            var showSpeedMenu by remember { mutableStateOf(false) }
            var isFullscreen by remember { mutableStateOf(false) }
            // 0 = Off (default), 1 = Landscape (90), 2 = Reverse Portrait (180), 3 = Reverse Landscape (270), 4 = Portrait (0)
            var rotateStep by remember { mutableIntStateOf(0) }

            var containerSize by remember { mutableStateOf(IntSize.Zero) }
            val latestDurMs by rememberUpdatedState(durMs)
            val latestContainerSize by rememberUpdatedState(containerSize)
            var hudText by remember { mutableStateOf<String?>(null) }
            var hudIcon by remember { mutableStateOf<ImageVector?>(null) }
            var hudTrigger by remember { mutableIntStateOf(0) }

            var currentBrightness by remember {
                val initB = window.attributes.screenBrightness
                mutableFloatStateOf(if (initB in 0f..1f) initB else 0.5f)
            }
            var volumeFloat by remember {
                mutableFloatStateOf(
                    audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol
                )
            }

            fun showHud(text: String, icon: ImageVector? = null) {
                hudText = text
                hudIcon = icon
                hudTrigger++
            }

            fun playIndex(idx: Int) {
                val safe = idx.coerceIn(0, playlist.lastIndex)
                currentIdx = safe
                playerErrorMsg = null
                val uri = resolveUri(playlist[safe]) ?: return
                exoPlayer.setMediaItem(MediaItem.fromUri(uri))
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
                exoPlayer.setPlaybackSpeed(playbackSpeed)
            }

            fun nextVideo() {
                if (playlist.size <= 1) {
                    exoPlayer.seekTo(0)
                    return
                }
                val next = if (shuffleEnabled) {
                    val pool = playlist.indices.filter { it != currentIdx }
                    pool.randomOrNull() ?: 0
                } else {
                    (currentIdx + 1) % playlist.size
                }
                playIndex(next)
            }

            fun prevVideo() {
                if (exoPlayer.currentPosition > 3500L || playlist.size <= 1) {
                    exoPlayer.seekTo(0)
                } else {
                    val prev = if (currentIdx - 1 < 0) playlist.lastIndex else currentIdx - 1
                    playIndex(prev)
                }
            }

            LaunchedEffect(hudTrigger) {
                if (hudTrigger > 0) {
                    delay(1100L)
                    hudText = null
                    hudIcon = null
                }
            }

            LaunchedEffect(showControls, isPlaying, scrubTargetMs, showSpeedMenu) {
                if (showControls && isPlaying && scrubTargetMs == null && !showSpeedMenu) {
                    delay(2000L)
                    showControls = false
                }
            }

            LaunchedEffect(repeatOne) {
                exoPlayer.repeatMode = if (repeatOne) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            }

            LaunchedEffect(playbackSpeed) {
                exoPlayer.setPlaybackSpeed(playbackSpeed)
            }

            LaunchedEffect(isFullscreen) {
                setImmersiveFullscreen(isFullscreen)
            }

            LaunchedEffect(isPlaying) {
                if (isPlaying) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                        if (exoPlayer.isPlaying) exoPlayer.pause()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            DisposableEffect(Unit) {
                val listener = object : Player.Listener {
                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        if (videoSize.width > 0 && videoSize.height > 0) {
                            videoAspect = (videoSize.width.toFloat() / videoSize.height.toFloat()).coerceIn(0.3f, 3.5f)
                        }
                    }

                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        val d = exoPlayer.duration
                        if (d > 0L) durMs = d
                        if (playbackState == Player.STATE_ENDED && !repeatOne && playlist.size > 1) {
                            nextVideo()
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        playerErrorMsg = error.localizedMessage ?: error.errorCodeName
                        isPlaying = false
                    }
                }
                exoPlayer.addListener(listener)
                val initialUri = resolveUri(playlist[currentIdx])
                if (initialUri != null) {
                    exoPlayer.setMediaItem(MediaItem.fromUri(initialUri))
                    exoPlayer.repeatMode = if (repeatOne) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = cfg.autoPlayMedia
                }
                onDispose {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    exoPlayer.removeListener(listener)
                    exoPlayer.release()
                }
            }

            LaunchedEffect(isPlaying) {
                posMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                val d0 = exoPlayer.duration
                if (d0 > 0L) durMs = d0
                while (isPlaying) {
                    if (scrubTargetMs == null) {
                        posMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                    }
                    val d = exoPlayer.duration
                    if (d > 0L) durMs = d
                    delay(120L)
                }
            }

            val currentEntry = playlist[currentIdx]
            val currentUri = resolveUri(currentEntry)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .onSizeChanged { containerSize = it }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                if (showSpeedMenu) {
                                    showSpeedMenu = false
                                } else {
                                    showControls = !showControls
                                }
                            },
                            onDoubleTap = {
                                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.start()
                            },
                        )
                    }
                    .pointerInput(Unit) {
                        var mode = 0 // 0=none, 1=seek, 2=brightness, 3=volume
                        var seekStartMs = 0L
                        var accumDx = 0f
                        detectDragGestures(
                            onDragStart = { startOffset ->
                                mode = 0
                                accumDx = 0f
                                seekStartMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                                val isLeft = startOffset.x < latestContainerSize.width / 2f
                                mode = if (isLeft) -2 else -3 // undecided vertical side
                            },
                            onDragEnd = {
                                if (mode == 1) {
                                    scrubTargetMs?.let { target ->
                                        exoPlayer.seekTo(target)
                                        posMs = target
                                    }
                                    scrubTargetMs = null
                                }
                                mode = 0
                            },
                            onDragCancel = {
                                scrubTargetMs = null
                                mode = 0
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                if (mode < 0) {
                                    if (abs(dragAmount.x) > abs(dragAmount.y) * 1.15f && abs(dragAmount.x) > 2f) {
                                        mode = 1
                                    } else if (abs(dragAmount.y) > 2f) {
                                        mode = if (mode == -2) 2 else 3
                                    }
                                }
                                when (mode) {
                                    1 -> {
                                        accumDx += dragAmount.x
                                        val deltaMs = (accumDx * 18f).toLong()
                                        val maxDur = latestDurMs.coerceAtLeast(1L)
                                        val target = (seekStartMs + deltaMs).coerceIn(0L, maxDur)
                                        scrubTargetMs = target
                                        posMs = target
                                        val sign = if (deltaMs >= 0) "+" else "-"
                                        showHud("${formatDurationWithMs(target)} ($sign${formatDurationWithMs(abs(deltaMs))})")
                                    }
                                    2 -> {
                                        val h = latestContainerSize.height.coerceAtLeast(1).toFloat()
                                        val delta = -dragAmount.y / (h * 0.75f)
                                        currentBrightness = (currentBrightness + delta).coerceIn(0.02f, 1f)
                                        val lp: WindowManager.LayoutParams = window.attributes
                                        lp.screenBrightness = currentBrightness
                                        window.attributes = lp
                                        showHud("${(currentBrightness * 100f).roundToInt()}%", Icons.Rounded.Brightness6)
                                    }
                                    3 -> {
                                        val h = latestContainerSize.height.coerceAtLeast(1).toFloat()
                                        val delta = -dragAmount.y / (h * 0.75f)
                                        volumeFloat = (volumeFloat + delta).coerceIn(0f, 1f)
                                        val targetVol = (volumeFloat * maxVol).roundToInt().coerceIn(0, maxVol)
                                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                                        showHud("${(volumeFloat * 100f).roundToInt()}%", Icons.AutoMirrored.Rounded.VolumeUp)
                                    }
                                }
                            },
                        )
                    },
            ) {
                AndroidView(
                    factory = { ctx ->
                        TextureView(ctx).also { tv ->
                            exoPlayer.setVideoTextureView(tv)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(videoAspect)
                        .align(Alignment.Center),
                )

                if (!playerErrorMsg.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.Black.copy(alpha = 0.78f),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 24.dp),
                    ) {
                        Text(
                            text = playerErrorMsg!!,
                            color = Color(0xFFFF6B6B),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }

                // Gesture HUD (Brightness / Volume / Millisecond Seek)
                AnimatedVisibility(
                    visible = hudText != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 72.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = 0.65f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            hudIcon?.let {
                                Icon(it, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                            Text(
                                text = hudText.orEmpty(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }

                // Legacy Overlay (Black 40% opacity)
                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.40f)),
                    ) {
                        // Top Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                                .align(Alignment.TopCenter),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = { finish() }) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = strings.viewer.backCd, tint = Color.White)
                            }
                            Text(
                                text = currentEntry.title,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = {
                                val u = currentUri ?: return@IconButton
                                val share = Intent(Intent.ACTION_SEND).apply {
                                    type = "video/*"
                                    putExtra(Intent.EXTRA_STREAM, u)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                startActivity(Intent.createChooser(share, currentEntry.title))
                            }) {
                                Icon(Icons.Rounded.Share, contentDescription = strings.viewer.shareLabel, tint = Color.White)
                            }
                        }

                        // Center Row: Shuffle | Prev | Play/Pause | Next | Repeat
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            IconButton(onClick = {
                                shuffleEnabled = !shuffleEnabled
                                Toast.makeText(
                                    this@VideoPlayerActivity,
                                    "${strings.shuffleBtn}: ${if (shuffleEnabled) "ON" else "OFF"}",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }) {
                                Icon(
                                    Icons.Rounded.Shuffle,
                                    contentDescription = strings.shuffleBtn,
                                    tint = if (shuffleEnabled) MaterialTheme.colorScheme.primary else Color.White,
                                    modifier = Modifier.size(26.dp),
                                )
                            }

                            IconButton(onClick = { prevVideo() }) {
                                Icon(
                                    Icons.Rounded.SkipPrevious,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(34.dp),
                                )
                            }

                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.45f),
                                modifier = Modifier
                                    .size(66.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                    },
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(40.dp),
                                    )
                                }
                            }

                            IconButton(onClick = { nextVideo() }) {
                                Icon(
                                    Icons.Rounded.SkipNext,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(34.dp),
                                )
                            }

                            IconButton(onClick = {
                                repeatOne = !repeatOne
                                Toast.makeText(
                                    this@VideoPlayerActivity,
                                    if (repeatOne) "1x: ON" else "1x: OFF",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }) {
                                Icon(
                                    if (repeatOne) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                                    contentDescription = null,
                                    tint = if (repeatOne) MaterialTheme.colorScheme.primary else Color.White,
                                    modifier = Modifier.size(26.dp),
                                )
                            }
                        }

                        // Bottom Panel: Speed Picker (when open) + Compact Seekbar + Bottom 5-Button Bar
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                                .align(Alignment.BottomCenter),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AnimatedVisibility(visible = showSpeedMenu) {
                                val speeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color.Black.copy(alpha = 0.70f),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .horizontalScroll(rememberScrollState())
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        speeds.forEach { spd ->
                                            val active = abs(playbackSpeed - spd) < 0.01f
                                            val label = if (spd == 1f) "1x" else "${spd}x"
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (active) Color.White else Color.White.copy(alpha = 0.14f),
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .clickable {
                                                        playbackSpeed = spd
                                                        showSpeedMenu = false
                                                    },
                                            ) {
                                                Text(
                                                    text = label,
                                                    color = if (active) Color.Black else Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Slim Seekbar + Time Labels
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                val displayPos = scrubTargetMs ?: posMs
                                Text(
                                    text = formatDurationMs(displayPos),
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                SlimVideoSeekBar(
                                    progress = if (durMs > 0L) (displayPos.toFloat() / durMs.toFloat()).coerceIn(0f, 1f) else 0f,
                                    onPreviewFraction = { frac ->
                                        val target = (frac * durMs).toLong().coerceIn(0L, durMs)
                                        scrubTargetMs = target
                                        posMs = target
                                    },
                                    onCommitFraction = { frac ->
                                        val target = (frac * durMs).toLong().coerceIn(0L, durMs)
                                        scrubTargetMs = null
                                        exoPlayer.seekTo(target)
                                        posMs = target
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = formatDurationMs(durMs),
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }

                            // Bottom 5 Controls: Rotate | -3s | 1X | +3s | Fullscreen
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val rotateLabel = when (rotateStep) {
                                    1 -> "90°"
                                    2 -> "180°"
                                    3 -> "270°"
                                    4 -> "0°"
                                    else -> "Off"
                                }
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            rotateStep = (rotateStep + 1) % 5
                                            requestedOrientation = when (rotateStep) {
                                                1 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                                2 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                                                3 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                                                4 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                                else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                            }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                ) {
                                    Icon(
                                        Icons.Rounded.ScreenRotation,
                                        contentDescription = strings.viewer.rotateLabel,
                                        tint = if (rotateStep != 0) MaterialTheme.colorScheme.primary else Color.White,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Text(rotateLabel, color = Color.White, fontSize = 10.sp)
                                }

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            val target = (exoPlayer.currentPosition - 3000L).coerceAtLeast(0L)
                                            exoPlayer.seekTo(target)
                                            posMs = target
                                        }
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                ) {
                                    Icon(Icons.Rounded.Replay, contentDescription = strings.viewer.rewind3s, tint = Color.White, modifier = Modifier.size(20.dp))
                                    Text("-3s", color = Color.White, fontSize = 10.sp)
                                }

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (showSpeedMenu || abs(playbackSpeed - 1f) > 0.01f) {
                                        Color.White.copy(alpha = 0.25f)
                                    } else {
                                        Color.Transparent
                                    },
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { showSpeedMenu = !showSpeedMenu },
                                ) {
                                    val spdLabel = if (abs(playbackSpeed - 1f) < 0.01f) "1X" else "${playbackSpeed}X"
                                    Text(
                                        text = spdLabel,
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    )
                                }

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            val maxD = durMs.coerceAtLeast(exoPlayer.duration).coerceAtLeast(1L)
                                            val target = (exoPlayer.currentPosition + 3000L).coerceAtMost(maxD)
                                            exoPlayer.seekTo(target)
                                            posMs = target
                                        }
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                ) {
                                    Icon(Icons.Rounded.Forward5, contentDescription = strings.viewer.forward3s, tint = Color.White, modifier = Modifier.size(20.dp))
                                    Text("+3s", color = Color.White, fontSize = 10.sp)
                                }

                                IconButton(onClick = { isFullscreen = !isFullscreen }) {
                                    Icon(
                                        if (isFullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                                        contentDescription = strings.viewer.fullscreen,
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun formatDurationWithMs(ms: Long): String {
        val totalMs = ms.coerceAtLeast(0L)
        val totalSec = totalMs / 1000L
        val min = totalSec / 60L
        val sec = totalSec % 60L
        val remMs = totalMs % 1000L
        return "%d:%02d.%03d".format(min, sec, remMs)
    }

    private fun ExoPlayer.start() {
        this.play()
    }
}

@Composable
private fun SlimVideoSeekBar(
    progress: Float,
    onPreviewFraction: (Float) -> Unit,
    onCommitFraction: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var widthPx by remember { mutableIntStateOf(1) }
    var lastFrac by remember { mutableFloatStateOf(progress.coerceIn(0f, 1f)) }
    val clamped = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .height(22.dp)
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onCommitFraction((offset.x / widthPx.toFloat()).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = { onCommitFraction(lastFrac) },
                    onDragCancel = { onCommitFraction(lastFrac) },
                    onDrag = { change, _ ->
                        change.consume()
                        val frac = (change.position.x / widthPx.toFloat()).coerceIn(0f, 1f)
                        lastFrac = frac
                        onPreviewFraction(frac)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(12.dp)) {
            val cy = size.height / 2f
            val activeX = size.width * clamped
            drawLine(
                color = Color.White.copy(alpha = 0.30f),
                start = Offset(0f, cy),
                end = Offset(size.width, cy),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = Color.White,
                start = Offset(0f, cy),
                end = Offset(activeX, cy),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawCircle(
                color = Color.White,
                radius = 5.5.dp.toPx(),
                center = Offset(activeX, cy),
            )
        }
    }
}
