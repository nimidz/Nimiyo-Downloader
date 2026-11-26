package com.nimiyo.nativeapp.ui.screens

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nimiyo.nativeapp.NimiyoAppContainer
import com.nimiyo.nativeapp.core.Strings
import com.nimiyo.nativeapp.player.LyricsDisplayMode
import com.nimiyo.nativeapp.player.LyricsDocument
import com.nimiyo.nativeapp.player.RepeatMode
import com.nimiyo.nativeapp.player.TtmlDisplayLine
import com.nimiyo.nativeapp.ui.components.formatDurationMs
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@Composable
fun NowPlayingSheet(
    container: NimiyoAppContainer,
    strings: Strings,
    onDismiss: () -> Unit,
) {
    val player = container.playerController
    val state by player.state.collectAsState()
    val settingsState by container.settings.state.collectAsState()
    val track = state.currentTrack ?: return

    var showLyrics by remember { mutableStateOf(false) }
    var showLyricsSourceDialog by remember { mutableStateOf(false) }
    var showMixToolsModal by remember { mutableStateOf(false) }
    var showQueueModal by remember { mutableStateOf(false) }
    var showSongInfoModal by remember { mutableStateOf(false) }
    var showLyricsEditor by remember { mutableStateOf(false) }
    var showSleepDialog by remember { mutableStateOf(false) }
    var showPlayerSettingsModal by remember { mutableStateOf(false) }
    var playerSettingsInitialTab by remember { mutableIntStateOf(0) }
    var showRemainingTime by remember { mutableStateOf(false) }
    var editorTabIsTtml by remember { mutableStateOf(false) }
    var editorLrcText by remember(state.lyricsDoc) { mutableStateOf(state.lyricsDoc?.rawLrc.orEmpty()) }
    var editorTtmlText by remember(state.ttmlDoc) { mutableStateOf(state.ttmlDoc?.rawXml.orEmpty()) }

    BackHandler {
        when {
            showLyricsSourceDialog -> showLyricsSourceDialog = false
            showMixToolsModal -> showMixToolsModal = false
            showQueueModal -> showQueueModal = false
            showSongInfoModal -> showSongInfoModal = false
            showLyricsEditor -> showLyricsEditor = false
            showSleepDialog -> showSleepDialog = false
            showPlayerSettingsModal -> showPlayerSettingsModal = false
            showLyrics -> showLyrics = false
            else -> onDismiss()
        }
    }

    val isLyricsBackdrop = showLyrics
    val smoothCurve = remember { CubicBezierEasing(0.22f, 1f, 0.36f, 1f) }
    val lyricsProgress by animateFloatAsState(
        targetValue = if (showLyrics) 1f else 0f,
        animationSpec = tween(320, easing = smoothCurve),
        label = "np_lyrics_progress",
    )
    val backdropAlpha by animateFloatAsState(
        targetValue = if (isLyricsBackdrop) 1f else 0f,
        animationSpec = tween(360, easing = smoothCurve),
        label = "np_backdrop_alpha",
    )
    val primaryColor = MaterialTheme.colorScheme.primary
    val bgColor = MaterialTheme.colorScheme.background
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val lrcPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val content = stream.bufferedReader().use { it.readText() }
                    player.importLyricsContent(track, content) { success ->
                        val msg = if (success) "Lirik berhasil diimpor!" else "Format berkas lirik tidak valid"
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }.onFailure {
                Toast.makeText(context, "Gagal membaca berkas: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val ambientGlowBrush = remember(primaryColor, bgColor) {
        Brush.radialGradient(
            colors = listOf(primaryColor.copy(alpha = 0.22f), bgColor),
            radius = 900f,
        )
    }
    val fallbackBackdropBrush = remember(primaryColor) {
        Brush.verticalGradient(listOf(primaryColor.copy(alpha = 0.75f), Color(0xFF0F172A)))
    }
    val backdropScrimBrush = remember {
        Brush.verticalGradient(
            listOf(
                Color.Black.copy(alpha = 0.45f),
                Color.Black.copy(alpha = 0.68f),
                Color.Black.copy(alpha = 0.78f),
            )
        )
    }
    val coverRequest = remember(track.albumArtUri, context) {
        if (track.albumArtUri.isBlank()) null
        else ImageRequest.Builder(context)
            .data(track.albumArtUri)
            .size(720)
            .crossfade(false)
            .build()
    }
    val backdropRequest = remember(track.albumArtUri, context) {
        if (track.albumArtUri.isBlank()) null
        else ImageRequest.Builder(context)
            .data(track.albumArtUri)
            .size(320)
            .crossfade(false)
            .build()
    }
    val fgColor by animateColorAsState(
        targetValue = if (isLyricsBackdrop) Color.White else MaterialTheme.colorScheme.onBackground,
        animationSpec = tween(320),
        label = "np_fg_color",
    )
    val mutedFgColor by animateColorAsState(
        targetValue = if (isLyricsBackdrop) Color.White.copy(alpha = 0.72f) else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(320),
        label = "np_muted_fg_color",
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = bgColor,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 1. Base Ambient Glow (Artwork mode)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ambientGlowBrush)
            )

            // Crossfaded Lyrics Backdrop (Album Blur, Custom Media Img/GIF/Video, or Solid Black)
            LyricsCustomBackdrop(
                style = settingsState.playerStyling,
                albumBackdropRequest = backdropRequest,
                fallbackBackdropBrush = fallbackBackdropBrush,
                backdropAlpha = backdropAlpha,
                isActive = showLyrics || lyricsProgress > 0.005f,
            )

            // 2. Main Full-Screen Column
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = {
                        if (showLyrics) showLyrics = false else onDismiss()
                    }) {
                        Icon(
                            Icons.Rounded.KeyboardArrowDown,
                            contentDescription = null,
                            tint = fgColor,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    Text(
                        text = if (isLyricsBackdrop) strings.lyricsTitle.uppercase() else "SEDANG DIPUTAR",
                        style = MaterialTheme.typography.labelLarge,
                        letterSpacing = 1.4.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = mutedFgColor,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { showQueueModal = true }) {
                            Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null, tint = fgColor)
                        }
                        IconButton(onClick = { showSongInfoModal = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = null, tint = fgColor)
                        }
                    }
                }

                if (!state.activePlaylistName.isNullOrBlank()) {
                    Surface(
                        shape = CircleShape,
                        color = primaryColor.copy(alpha = 0.16f),
                        modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.QueueMusic,
                                contentDescription = null,
                                tint = primaryColor,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = "${strings.playerTabPlaylists}: ${state.activePlaylistName}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isLyricsBackdrop) Color.White else primaryColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                // Stage Area (Centered Artwork Stage <-> Flowing Fullscreen Lyrics Stage)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    // Artwork + Compact Info Stage (GPU-composited crossfade, zero layout reflow)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(if (!showLyrics) 1f else 0f)
                            .graphicsLayer {
                                alpha = (1f - lyricsProgress).coerceIn(0f, 1f)
                                val s = 1f - (0.06f * lyricsProgress)
                                scaleX = s
                                scaleY = s
                                translationY = -18f * lyricsProgress
                            },
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.76f)
                                    .widthIn(max = 286.dp)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(24.dp))
                                    .clickable(enabled = !showLyrics) { showLyrics = true },
                            ) {
                                Surface(
                                    modifier = Modifier.fillMaxSize(),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Rounded.MusicNote,
                                            contentDescription = null,
                                            modifier = Modifier.size(104.dp),
                                            tint = primaryColor,
                                        )
                                    }
                                }
                                if (coverRequest != null) {
                                    AsyncImage(
                                        model = coverRequest,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }

                                // Subtle Lyrics hint badge on cover corner
                                Surface(
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.56f),
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(12.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Icon(
                                            Icons.Rounded.MusicNote,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(13.dp),
                                        )
                                        Text(
                                            text = strings.lyricsTitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                        )
                                    }
                                }
                            }
                        }

                        // Compact Info Section directly above Progress Slider
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = fgColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = track.artist,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = mutedFgColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (track.album.isNotBlank() && !track.album.equals("Unknown Album", true)) {
                                    Text(
                                        text = track.album,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = mutedFgColor.copy(alpha = 0.78f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            val hasAnyLyrics = (state.lyricsDoc?.lines?.isNotEmpty() == true) || state.ttmlLines.isNotEmpty()
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (hasAnyLyrics) primaryColor.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable(enabled = !showLyrics) { showLyrics = true },
                            ) {
                                Text(
                                    text = if (hasAnyLyrics) "LYRICS" else strings.lyricsTitle,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (hasAnyLyrics) primaryColor else mutedFgColor,
                                )
                            }
                        }
                    }

                    // Fullscreen Flowing Lyrics Stage (only mounted while visible or animating)
                    if (showLyrics || lyricsProgress > 0.005f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .zIndex(if (showLyrics) 1f else 0f)
                                .graphicsLayer {
                                    alpha = lyricsProgress.coerceIn(0f, 1f)
                                    val s = 0.95f + (0.05f * lyricsProgress)
                                    scaleX = s
                                    scaleY = s
                                    translationY = 20f * (1f - lyricsProgress)
                                }
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = null,
                                    enabled = showLyrics,
                                    onClick = {},
                                ),
                        ) {
                            LyricsFullStage(
                                strings = strings,
                                track = track,
                                stateDoc = state.lyricsDoc,
                                ttmlLines = state.ttmlLines,
                                isTtmlFromProvider = state.ttmlDoc != null,
                                ttmlSource = state.ttmlDoc?.source,
                                displayMode = state.lyricsDisplayMode,
                                isLoading = state.lyricsLoading,
                                isPlaying = state.isPlaying,
                                isActive = showLyrics || lyricsProgress > 0.005f,
                                playbackSpeed = state.speed,
                                positionFlow = player.positionMs,
                                accentColor = primaryColor,
                                lyricsAlignment = settingsState.musicLyricsAlignment,
                                ttmlAnimMode = settingsState.musicTtmlAnimMode,
                                lyricsFontSize = settingsState.musicLyricsFontSize,
                                showTranslation = settingsState.musicLyricsShowTranslation,
                                tapToSeek = settingsState.musicLyricsTapToSeek,
                                playerStyling = settingsState.playerStyling,
                                onBackToCover = { showLyrics = false },
                                onToggleTtmlMode = { player.toggleLyricsDisplayMode() },
                                onSeek = { player.seekTo(it) },
                                onSearchOnline = { showLyricsSourceDialog = true },
                                onRefreshTtml = { player.refreshTtmlOnline(track) },
                                onOpenSourceMenu = { showLyricsSourceDialog = true },
                                onOpenEditor = {
                                    editorTabIsTtml = state.lyricsDisplayMode == LyricsDisplayMode.TTML
                                    editorLrcText = state.lyricsDoc?.rawLrc.orEmpty()
                                    editorTtmlText = state.ttmlDoc?.rawXml.orEmpty()
                                    showLyricsEditor = true
                                },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Progress Slider & Time Row (isolated recomposition scope for smooth 60/120fps animations)
                NowPlayingProgressSection(
                    positionFlow = player.positionMs,
                    durationMs = state.durationMs,
                    isLyricsBackdrop = isLyricsBackdrop,
                    mutedFgColor = mutedFgColor,
                    showRemainingTime = showRemainingTime,
                    onToggleRemaining = { showRemainingTime = !showRemainingTime },
                    onSeek = { player.seekTo(it) },
                )

                Spacer(Modifier.height(10.dp))

                // Main Controls Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { player.toggleShuffle() }) {
                        Icon(
                            Icons.Rounded.Shuffle,
                            contentDescription = null,
                            tint = if (state.shuffle) primaryColor else mutedFgColor,
                        )
                    }
                    IconButton(onClick = { player.skipPrev() }, modifier = Modifier.size(52.dp)) {
                        Icon(
                            Icons.Rounded.SkipPrevious,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = fgColor,
                        )
                    }
                    FilledIconButton(
                        onClick = { player.togglePlayPause() },
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape),
                        colors = if (isLyricsBackdrop) {
                            IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black,
                            )
                        } else {
                            IconButtonDefaults.filledIconButtonColors()
                        },
                    ) {
                        Icon(
                            if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                    IconButton(onClick = { player.skipNext() }, modifier = Modifier.size(52.dp)) {
                        Icon(
                            Icons.Rounded.SkipNext,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = fgColor,
                        )
                    }
                    IconButton(onClick = { player.cycleRepeat() }) {
                        val icon = if (state.repeatMode == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = if (state.repeatMode != RepeatMode.NONE) primaryColor else mutedFgColor,
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Secondary Action Dock (Preset Switcher, Timer, MixTools, Music Settings Gear)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val timerActive = state.sleepTimerRemainingSec > 0
                    val dockContainer = if (isLyricsBackdrop) {
                        Color.White.copy(alpha = 0.14f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    }
                    val customBgActive = settingsState.playerStyling.backdropMode != com.nimiyo.nativeapp.core.LyricsBackdropMode.ALBUM_BLUR

                    // Backdrop Mode Switcher (Icon-Only, left of Timer)
                    Surface(
                        shape = CircleShape,
                        color = if (customBgActive) primaryColor.copy(alpha = 0.24f) else dockContainer,
                        border = BorderStroke(1.dp, if (customBgActive) primaryColor else mutedFgColor.copy(alpha = 0.25f)),
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .clickable {
                                val currMode = settingsState.playerStyling.backdropMode
                                val modes = com.nimiyo.nativeapp.core.LyricsBackdropMode.entries
                                val nextMode = modes[(modes.indexOf(currMode) + 1) % modes.size]
                                if (nextMode == com.nimiyo.nativeapp.core.LyricsBackdropMode.CUSTOM_MEDIA &&
                                    settingsState.playerStyling.customMediaUri.isBlank()
                                ) {
                                    playerSettingsInitialTab = 1
                                    showPlayerSettingsModal = true
                                    Toast.makeText(context, strings.music.toastPickCustomMedia, Toast.LENGTH_SHORT).show()
                                } else {
                                    container.settings.update {
                                        it.copy(playerStyling = it.playerStyling.copy(backdropMode = nextMode))
                                    }
                                    val modeLbl = when (nextMode) {
                                        com.nimiyo.nativeapp.core.LyricsBackdropMode.ALBUM_BLUR -> strings.music.backdropAlbumBlur
                                        com.nimiyo.nativeapp.core.LyricsBackdropMode.CUSTOM_MEDIA -> strings.music.backdropCustomMedia
                                        com.nimiyo.nativeapp.core.LyricsBackdropMode.SOLID_BLACK -> strings.music.backdropSolidBlack
                                    }
                                    Toast.makeText(context, modeLbl, Toast.LENGTH_SHORT).show()
                                }
                            },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.Palette,
                                contentDescription = "Ganti Latar Lirik",
                                modifier = Modifier.size(18.dp),
                                tint = if (customBgActive) primaryColor else fgColor,
                            )
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = if (timerActive) primaryColor.copy(alpha = 0.24f) else dockContainer,
                        border = BorderStroke(1.dp, if (timerActive) primaryColor else mutedFgColor.copy(alpha = 0.25f)),
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { showSleepDialog = true },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                Icons.Rounded.AccessTime,
                                contentDescription = "Timer",
                                modifier = Modifier.size(16.dp),
                                tint = if (timerActive) primaryColor else fgColor,
                            )
                            val timerTxt = if (timerActive) {
                                val m = state.sleepTimerRemainingSec / 60
                                val s = state.sleepTimerRemainingSec % 60
                                "%d:%02d".format(m, s)
                            } else "Timer"
                            Text(
                                text = timerTxt,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (timerActive) primaryColor else fgColor,
                            )
                        }
                    }

                    val speedPct = (state.speed * 100f).roundToInt()
                    val mixActive = speedPct != 100 || state.reverbEnabled
                    val mixLabel = buildString {
                        if (speedPct != 100) append("$speedPct%") else append(strings.mixToolsTitle)
                        if (state.reverbEnabled) append(" • FX")
                    }
                    Surface(
                        shape = CircleShape,
                        color = if (mixActive) primaryColor.copy(alpha = 0.24f) else dockContainer,
                        border = BorderStroke(1.dp, if (mixActive) primaryColor else mutedFgColor.copy(alpha = 0.25f)),
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { showMixToolsModal = true },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                Icons.Rounded.Equalizer,
                                contentDescription = strings.mixToolsTitle,
                                modifier = Modifier.size(16.dp),
                                tint = if (mixActive) primaryColor else fgColor,
                            )
                            Text(
                                text = mixLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (mixActive) primaryColor else fgColor,
                            )
                        }
                    }

                    // Music Settings & Player Styling Shortcut (Gear Icon-Only, right of MixTools)
                    Surface(
                        shape = CircleShape,
                        color = dockContainer,
                        border = BorderStroke(1.dp, mutedFgColor.copy(alpha = 0.25f)),
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .clickable {
                                playerSettingsInitialTab = 0
                                showPlayerSettingsModal = true
                            },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.Settings,
                                contentDescription = strings.music.title,
                                modifier = Modifier.size(18.dp),
                                tint = fgColor,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPlayerSettingsModal) {
        MusicSettingsAndStylingSheet(
            container = container,
            strings = strings,
            settingsState = settingsState,
            initialTab = playerSettingsInitialTab,
            onDismiss = { showPlayerSettingsModal = false },
        )
    }

    // MixTools Modal Dialog (Speed FX 60%..140% + Edjing-Mix Reverb FX X/Y Pad)
    if (showMixToolsModal) {
        MixToolsDialog(
            strings = strings,
            speed = state.speed,
            reverbEnabled = state.reverbEnabled,
            reverbDryWet = state.reverbDryWet,
            reverbDepth = state.reverbDepth,
            onSpeedChange = { player.setSpeedFx(it) },
            onToggleReverb = { player.setReverbEnabled(it) },
            onReverbXYChange = { dw, dp -> player.setReverbXY(dw, dp) },
            onResetReverb = { player.resetReverbXY() },
            onResetAll = { player.resetAllMixTools() },
            onDismiss = { showMixToolsModal = false },
        )
    }

    // Queue Modal Dialog
    if (showQueueModal) {
        AlertDialog(
            onDismissRequest = { showQueueModal = false },
            title = { Text(strings.queueTitle) },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(state.queue) { idx, qTrack ->
                        val isCur = idx == state.currentIndex
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isCur) primaryColor.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    player.playFromList(state.queue, idx)
                                    showQueueModal = false
                                },
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    text = "${idx + 1}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCur) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = qTrack.title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = if (isCur) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isCur) primaryColor else MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = qTrack.artist,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQueueModal = false }) { Text(strings.closeBtn) }
            },
        )
    }

    // Song Info Dialog
    if (showSongInfoModal) {
        AlertDialog(
            onDismissRequest = { showSongInfoModal = false },
            title = { Text(strings.songInfoLabel) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoLine(strings.titleLabel, track.title)
                    InfoLine(strings.artistLabel, track.artist)
                    InfoLine(strings.albumLabel, track.album.ifBlank { "-" })
                    InfoLine(strings.durationLabel, formatDurationMs(state.durationMs))
                    InfoLine(strings.ui.locationLabel, track.path.ifBlank { track.contentUri })
                }
            },
            confirmButton = {
                TextButton(onClick = { showSongInfoModal = false }) { Text(strings.closeBtn) }
            },
        )
    }

    if (showLyricsEditor) {
        AlertDialog(
            onDismissRequest = { showLyricsEditor = false },
            title = { Text(strings.editLyrics) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = !editorTabIsTtml,
                            onClick = { editorTabIsTtml = false },
                            label = { Text("LRC (.lrc)") },
                            modifier = Modifier.weight(1f),
                        )
                        FilterChip(
                            selected = editorTabIsTtml,
                            onClick = { editorTabIsTtml = true },
                            label = { Text("TTML (.ttml)") },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        if (!editorTabIsTtml) {
                            TextButton(onClick = {
                                player.loadLyricsFor(track, forceOnline = true)
                                showLyricsEditor = false
                            }) {
                                Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(strings.searchLyrics)
                            }
                        } else {
                            TextButton(onClick = {
                                player.refreshTtmlOnline(track)
                                showLyricsEditor = false
                            }) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(strings.searchLyrics)
                            }
                        }
                        TextButton(onClick = {
                            showLyricsEditor = false
                            showLyricsSourceDialog = true
                        }) {
                            Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(strings.lyricsSourceBtn)
                        }
                    }
                    OutlinedTextField(
                        value = if (editorTabIsTtml) editorTtmlText else editorLrcText,
                        onValueChange = {
                            if (editorTabIsTtml) editorTtmlText = it else editorLrcText = it
                        },
                        placeholder = {
                            Text(
                                if (editorTabIsTtml) {
                                    "<tt xmlns=\"http://www.w3.org/ns/ttml\">...</tt>"
                                } else {
                                    "[00:12.34] Baris lirik..."
                                }
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        coroutineScope.launch {
                            val activeText = if (editorTabIsTtml) editorTtmlText else editorLrcText
                            val file = container.lyricsRepo.exportLyricsFile(track, editorTabIsTtml, activeText)
                            val msg = if (file != null) "Lirik diekspor ke: ${file.name}" else "Gagal mengekspor"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(strings.exportBtn)
                    }
                    TextButton(onClick = {
                        if (editorTabIsTtml) {
                            player.saveCustomTtml(track, editorTtmlText)
                        } else {
                            player.saveCustomLyrics(track, editorLrcText)
                        }
                        showLyricsEditor = false
                    }) {
                        Text(strings.saveLyrics)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showLyricsEditor = false }) {
                    Text(strings.cancelBtn)
                }
            },
        )
    }

    if (showLyricsSourceDialog) {
        AlertDialog(
            onDismissRequest = { showLyricsSourceDialog = false },
            title = {
                Column {
                    Text(strings.lyricsSourceTitle, fontWeight = FontWeight.Bold)
                    Text(
                        "KuGou / LRCLIB / TTML / .LRC",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // KuGou (Utama)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                showLyricsSourceDialog = false
                                player.searchLyricsKuGou(track) { found ->
                                    val msg = if (found) "KuGou: Lirik berhasil dimuat" else "KuGou: Lirik tidak ditemukan"
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(36.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("KG", fontWeight = FontWeight.Black, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("KuGou (Utama)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text(strings.kugouDesc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // LRCLIB (Assist / Fallback)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                showLyricsSourceDialog = false
                                player.searchLyricsLrcLib(track) { found ->
                                    val msg = if (found) "LRCLIB: Lirik berhasil dimuat" else "LRCLIB: Lirik tidak ditemukan"
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.size(36.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("LRC", fontWeight = FontWeight.Black, fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("LRCLIB (Assist / Fallback)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text(strings.lrclibDesc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // TTML Online
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                showLyricsSourceDialog = false
                                player.refreshTtmlOnline(track)
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                modifier = Modifier.size(36.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("TTML", fontWeight = FontWeight.Black, fontSize = 10.sp, color = MaterialTheme.colorScheme.tertiary)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(strings.searchTtmlOnlineTitle, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text(strings.searchTtmlOnlineDesc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // Import File Lirik (.lrc / .ttml)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                showLyricsSourceDialog = false
                                runCatching {
                                    lrcPickerLauncher.launch(arrayOf("*/*"))
                                }
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.size(36.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(strings.importLyricsTitle, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text(strings.importLyricsDesc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // Ekspor Lirik
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                showLyricsSourceDialog = false
                                coroutineScope.launch {
                                    val isTtml = state.lyricsDisplayMode == LyricsDisplayMode.TTML && state.ttmlDoc != null
                                    val file = player.exportLyrics(track, isTtml)
                                    val msg = if (file != null) "Lirik diekspor ke: ${file.name}" else "Tidak ada lirik aktif untuk diekspor"
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                }
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.size(36.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(strings.exportLyricsTitle, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text(strings.exportLyricsDesc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLyricsSourceDialog = false }) {
                    Text(strings.closeBtn)
                }
            }
        )
    }

    if (showSleepDialog) {
        var customMinutesInput by remember { mutableStateOf("") }
        val customMins = customMinutesInput.trim().toIntOrNull()
        AlertDialog(
            onDismissRequest = { showSleepDialog = false },
            title = { Text(strings.sleepTimer) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (state.sleepTimerRemainingSec > 0) {
                        val remMin = state.sleepTimerRemainingSec / 60
                        val remSec = state.sleepTimerRemainingSec % 60
                        Text(
                            text = "Aktif: %02d:%02d tersisa".format(remMin, remSec),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf(10 to "10m", 20 to "20m", 30 to "30m", 60 to "60m").forEach { (mins, lbl) ->
                            FilterChip(
                                selected = false,
                                onClick = {
                                    player.setSleepTimerMinutes(mins)
                                    showSleepDialog = false
                                },
                                label = { Text(lbl) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    OutlinedTextField(
                        value = customMinutesInput,
                        onValueChange = { raw ->
                            customMinutesInput = raw.filter { it.isDigit() }.take(4)
                        },
                        label = { Text(strings.customTimerHint) },
                        placeholder = { Text("45") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = customMins != null && customMins > 0,
                    onClick = {
                        if (customMins != null && customMins > 0) {
                            player.setSleepTimerMinutes(customMins)
                            showSleepDialog = false
                        }
                    },
                ) {
                    Text(strings.startBtn)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (state.sleepTimerRemainingSec > 0) {
                        TextButton(onClick = {
                            player.setSleepTimerMinutes(0)
                            showSleepDialog = false
                        }) {
                            Text(strings.turnOffBtn)
                        }
                    }
                    TextButton(onClick = { showSleepDialog = false }) {
                        Text(strings.cancelBtn)
                    }
                }
            },
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun MixToolsDialog(
    strings: Strings,
    speed: Float,
    reverbEnabled: Boolean,
    reverbDryWet: Float,
    reverbDepth: Float,
    onSpeedChange: (Float) -> Unit,
    onToggleReverb: (Boolean) -> Unit,
    onReverbXYChange: (Float, Float) -> Unit,
    onResetReverb: () -> Unit,
    onResetAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Header: MixTools + Reset All + Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Equalizer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = strings.mixToolsTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onResetAll) {
                            Icon(Icons.Rounded.Refresh, contentDescription = strings.ui.resetBtnLabel)
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, contentDescription = strings.closeBtn)
                        }
                    }
                }

                // Section 1: Speed FX (60%..140%, pitch-linked true Slowed / SpeedUp)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Speed FX",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = strings.ui.mixSpeedSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val bpmPct = (speed * 100f).roundToInt().coerceIn(60, 140)
                    val tag = when {
                        bpmPct < 100 -> strings.ui.mixSlowedLabel
                        bpmPct > 100 -> strings.ui.mixSpeedUpLabel
                        else -> strings.ui.mixNormalLabel
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "$bpmPct% • $tag",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        OutlinedButton(
                            onClick = { onSpeedChange(1.0f) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp),
                        ) {
                            Text(strings.ui.resetBtnLabel, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("60%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Slider(
                            value = bpmPct.toFloat(),
                            onValueChange = { raw ->
                                val stepped = raw.roundToInt().coerceIn(60, 140)
                                onSpeedChange(stepped / 100f)
                            },
                            valueRange = 60f..140f,
                            steps = 79,
                            modifier = Modifier.weight(1f),
                        )
                        Text("140%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = strings.ui.mixSlowedLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable { onSpeedChange(0.85f) },
                        )
                        Text(
                            text = strings.ui.mixNormalLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable { onSpeedChange(1.0f) },
                        )
                        Text(
                            text = strings.ui.mixSpeedUpLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable { onSpeedChange(1.20f) },
                        )
                    }
                }

                HorizontalDivider()

                // Section 2: Reverb FX (Edjing Mix 3D Acoustic Echo - strictly independent of Speed!)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Reverb FX",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = strings.ui.mixReverbSubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = reverbEnabled,
                            onCheckedChange = onToggleReverb,
                        )
                    }

                    if (reverbEnabled) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Dry/Wet: ${(reverbDryWet * 100f).roundToInt()}% • ${strings.ui.mixEchoLabel}: ${(reverbDepth * 100f).roundToInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            OutlinedButton(
                                onClick = onResetReverb,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp),
                            ) {
                                Text(strings.ui.resetBtnLabel, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }

                        // 2D XY Pad with cardinal labels
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    text = strings.ui.mixPadUp,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.align(Alignment.TopCenter),
                                )
                                Text(
                                    text = strings.ui.mixPadDown,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.align(Alignment.BottomCenter),
                                )
                                Text(
                                    text = strings.ui.mixPadLeft,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.align(Alignment.CenterStart),
                                )
                                Text(
                                    text = strings.ui.mixPadRight,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.align(Alignment.CenterEnd),
                                )

                                val primary = MaterialTheme.colorScheme.primary
                                val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 42.dp, vertical = 18.dp)
                                        .height(116.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            Brush.radialGradient(
                                                listOf(primary.copy(alpha = 0.24f), MaterialTheme.colorScheme.surfaceContainerLow)
                                            )
                                        )
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                                        .pointerInput(Unit) {
                                            detectTapGestures { pos ->
                                                val x = (pos.x / size.width.toFloat()).coerceIn(0f, 1f)
                                                val y = (1f - pos.y / size.height.toFloat()).coerceIn(0f, 1f)
                                                onReverbXYChange(x, y)
                                            }
                                        }
                                        .pointerInput(Unit) {
                                            detectDragGestures { change, _ ->
                                                change.consume()
                                                val x = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                                val y = (1f - change.position.y / size.height.toFloat()).coerceIn(0f, 1f)
                                                onReverbXYChange(x, y)
                                            }
                                        },
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                                        drawLine(
                                            color = gridColor,
                                            start = Offset(0f, size.height / 2f),
                                            end = Offset(size.width, size.height / 2f),
                                            strokeWidth = 2f,
                                            pathEffect = dash,
                                        )
                                        drawLine(
                                            color = gridColor,
                                            start = Offset(size.width / 2f, 0f),
                                            end = Offset(size.width / 2f, size.height),
                                            strokeWidth = 2f,
                                            pathEffect = dash,
                                        )
                                        val pad = 12.dp.toPx()
                                        val usableW = (size.width - pad * 2f).coerceAtLeast(1f)
                                        val usableH = (size.height - pad * 2f).coerceAtLeast(1f)
                                        val cx = pad + reverbDryWet.coerceIn(0f, 1f) * usableW
                                        val cy = pad + (1f - reverbDepth.coerceIn(0f, 1f)) * usableH
                                        drawCircle(color = primary.copy(alpha = 0.28f), radius = 22f, center = Offset(cx, cy))
                                        drawCircle(color = primary, radius = 12f, center = Offset(cx, cy))
                                        drawCircle(color = Color.White, radius = 5f, center = Offset(cx, cy))
                                    }
                                }
                            }
                        }
                        Text(
                            text = strings.ui.mixPadHint,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NowPlayingProgressSection(
    positionFlow: StateFlow<Long>,
    durationMs: Long,
    isLyricsBackdrop: Boolean,
    mutedFgColor: Color,
    showRemainingTime: Boolean,
    onToggleRemaining: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    val positionMs by positionFlow.collectAsState()
    val dur = durationMs.coerceAtLeast(1L)
    var isDragging by remember { mutableStateOf(false) }
    var dragPosMs by remember { mutableLongStateOf(0L) }
    val displayPosMs = if (isDragging) dragPosMs else positionMs

    Slider(
        value = displayPosMs.toFloat().coerceIn(0f, dur.toFloat()),
        onValueChange = {
            isDragging = true
            dragPosMs = it.toLong()
        },
        onValueChangeFinished = {
            val target = dragPosMs.coerceIn(0L, (dur - 250L).coerceAtLeast(0L))
            isDragging = false
            onSeek(target)
        },
        valueRange = 0f..dur.toFloat(),
        colors = if (isLyricsBackdrop) {
            SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.28f),
            )
        } else {
            SliderDefaults.colors()
        },
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = formatDurationMs(displayPosMs),
            style = MaterialTheme.typography.labelMedium,
            color = mutedFgColor,
        )
        val rightLabel = if (showRemainingTime) {
            "-${formatDurationMs((dur - displayPosMs).coerceAtLeast(0L))}"
        } else {
            formatDurationMs(durationMs)
        }
        Text(
            text = rightLabel,
            style = MaterialTheme.typography.labelMedium,
            color = mutedFgColor,
            modifier = Modifier.clickable(onClick = onToggleRemaining),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LyricsFullStage(
    strings: Strings,
    track: com.nimiyo.nativeapp.data.AudioTrack,
    stateDoc: LyricsDocument?,
    ttmlLines: List<TtmlDisplayLine>,
    isTtmlFromProvider: Boolean,
    ttmlSource: String?,
    displayMode: LyricsDisplayMode,
    isLoading: Boolean,
    isPlaying: Boolean,
    isActive: Boolean,
    playbackSpeed: Float,
    positionFlow: StateFlow<Long>,
    accentColor: Color,
    lyricsAlignment: com.nimiyo.nativeapp.core.LyricsAlignmentMode = com.nimiyo.nativeapp.core.LyricsAlignmentMode.LEFT,
    ttmlAnimMode: com.nimiyo.nativeapp.core.TtmlAnimationMode = com.nimiyo.nativeapp.core.TtmlAnimationMode.PER_CHAR,
    lyricsFontSize: com.nimiyo.nativeapp.core.LyricsFontSizePref = com.nimiyo.nativeapp.core.LyricsFontSizePref.NORMAL,
    showTranslation: Boolean = true,
    tapToSeek: Boolean = true,
    playerStyling: com.nimiyo.nativeapp.core.PlayerStylingConfig = com.nimiyo.nativeapp.core.PlayerStylingConfig(),
    onBackToCover: () -> Unit,
    onToggleTtmlMode: () -> Unit,
    onSeek: (Long) -> Unit,
    onSearchOnline: () -> Unit,
    onRefreshTtml: () -> Unit,
    onOpenSourceMenu: () -> Unit,
    onOpenEditor: () -> Unit,
) {
    val isTtmlRequested = displayMode == LyricsDisplayMode.TTML
    val hasLrc = stateDoc?.lines?.isNotEmpty() == true
    val hasTtml = ttmlLines.isNotEmpty()
    val isTtml = isTtmlRequested && hasTtml
    val hasLyrics = hasLrc || hasTtml
    val smoothCurve = remember { CubicBezierEasing(0.22f, 1f, 0.36f, 1f) }
    val bgCurve = remember { CubicBezierEasing(0.16f, 1f, 0.3f, 1f) }

    // Sub-frame 60/120fps time interpolation for silky word-by-word luminance & char wave
    var smoothPosMs by remember { mutableLongStateOf(positionFlow.value) }
    var anchorPosMs by remember { mutableLongStateOf(positionFlow.value) }
    var anchorNano by remember { mutableLongStateOf(System.nanoTime()) }

    LaunchedEffect(positionFlow, track.id, isActive) {
        if (!isActive) return@LaunchedEffect
        positionFlow.collectLatest { pos ->
            anchorPosMs = pos
            anchorNano = System.nanoTime()
            smoothPosMs = pos
        }
    }

    LaunchedEffect(isPlaying, playbackSpeed, track.id, isActive) {
        if (!isActive) return@LaunchedEffect
        anchorPosMs = positionFlow.value
        anchorNano = System.nanoTime()
        smoothPosMs = anchorPosMs
        if (isPlaying) {
            while (true) {
                withFrameNanos { frameNano ->
                    val elapsedMs = ((frameNano - anchorNano) / 1_000_000.0) * playbackSpeed.toDouble()
                    smoothPosMs = (anchorPosMs + elapsedMs.roundToLong()).coerceAtLeast(0L)
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Lyrics Stage Top Bar (Mini Artwork + Track Info + TTML Toggle Pill + Edit Icon)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onBackToCover)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (track.albumArtUri.isNotBlank()) {
                    AsyncImage(
                        model = track.albumArtUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(9.dp)),
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(9.dp),
                        color = accentColor.copy(alpha = 0.25f),
                        modifier = Modifier.size(38.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = when {
                            isTtml && !ttmlSource.isNullOrBlank() -> "${track.artist} • $ttmlSource"
                            isTtmlRequested && !hasTtml && hasLrc -> "${track.artist} • ${stateDoc?.source ?: "LRC"} (TTML N/A)"
                            else -> track.artist
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (hasLyrics) {
                    if (isTtml && !isTtmlFromProvider) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.14f),
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable(onClick = onRefreshTtml),
                        ) {
                            Icon(
                                Icons.Rounded.Refresh,
                                contentDescription = "Fetch TTML",
                                tint = Color.White,
                                modifier = Modifier
                                    .padding(7.dp)
                                    .size(16.dp),
                            )
                        }
                    }
                    val topBarW by animateDpAsState(if (isTtml) 10.dp else 10.dp, label = "lrcTopW")
                    val midBarW by animateDpAsState(if (isTtml) 20.dp else 17.dp, label = "lrcMidW")
                    val botBarW by animateDpAsState(if (isTtml) 10.dp else 10.dp, label = "lrcBotW")
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isTtml) accentColor.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, if (isTtml) accentColor else Color.White.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onToggleTtmlMode),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 7.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = if (isTtml) Alignment.Start else Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(topBarW)
                                        .height(2.2.dp)
                                        .clip(CircleShape)
                                        .background(Color.White),
                                )
                                Box(
                                    modifier = Modifier
                                        .width(midBarW)
                                        .height(2.2.dp)
                                        .clip(CircleShape)
                                        .background(Color.White),
                                )
                                Box(
                                    modifier = Modifier
                                        .width(botBarW)
                                        .height(2.2.dp)
                                        .clip(CircleShape)
                                        .background(Color.White),
                                )
                            }
                        }
                    }
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White.copy(alpha = 0.14f),
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onOpenEditor),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Edit,
                            contentDescription = strings.editLyrics,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.16f))

        // Main Flowing Lyrics Area
        val lyricsFontFamily = rememberLyricsFontFamily(playerStyling)
        val lyricsShadow = if (playerStyling.textShadow) {
            androidx.compose.ui.graphics.Shadow(
                color = Color.Black.copy(alpha = 0.78f),
                offset = androidx.compose.ui.geometry.Offset(0f, 2f),
                blurRadius = 10f,
            )
        } else null
        androidx.compose.material3.ProvideTextStyle(
            androidx.compose.material3.LocalTextStyle.current.copy(
                fontFamily = lyricsFontFamily,
                fontWeight = playerStyling.fontWeight.toComposeWeight(),
                shadow = lyricsShadow,
            )
        ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            } else if (!hasLyrics) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = strings.noLyricsFound,
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.18f),
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable(onClick = onSearchOnline),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(Icons.Rounded.Search, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Text(strings.searchLyrics, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else if (isTtml && ttmlLines.isNotEmpty()) {
                val activeIdx by remember(ttmlLines) {
                    derivedStateOf {
                        val cp = (smoothPosMs - 40L).coerceAtLeast(0L)
                        ttmlLines.indexOfLast { it.beginMs <= cp }.coerceAtLeast(-1)
                    }
                }
                val listState = rememberLazyListState()
                var viewportHeightPx by remember { mutableIntStateOf(0) }

                LaunchedEffect(activeIdx, viewportHeightPx) {
                    if (activeIdx >= 0 && viewportHeightPx > 0) {
                        val visible = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == activeIdx }
                        if (visible != null) {
                            val targetTop = (viewportHeightPx * 0.36f) - (visible.size * 0.5f)
                            val delta = visible.offset.toFloat() - targetTop
                            if (abs(delta) > 4f) {
                                runCatching {
                                    listState.animateScrollBy(
                                        delta,
                                        tween(durationMillis = 480, easing = smoothCurve),
                                    )
                                }
                            }
                        } else {
                            runCatching {
                                listState.animateScrollToItem(
                                    index = activeIdx,
                                    scrollOffset = -(viewportHeightPx * 0.32f).toInt(),
                                )
                            }
                        }
                    } else if (activeIdx < 0) {
                        runCatching { listState.animateScrollToItem(0) }
                    }
                }

                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(top = 72.dp, bottom = 180.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { viewportHeightPx = it.height }
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                brush = Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    0.10f to Color.Black,
                                    0.85f to Color.Black,
                                    1f to Color.Transparent,
                                ),
                                blendMode = BlendMode.DstIn,
                            )
                        },
                ) {
                    itemsIndexed(ttmlLines) { idx, line ->
                        val isActiveLine = idx == activeIdx
                        val dist = if (activeIdx < 0) idx else abs(idx - activeIdx)
                        val targetAlpha = when {
                            activeIdx < 0 -> when (idx) {
                                0 -> 0.94f
                                1 -> 0.48f
                                2 -> 0.30f
                                else -> 0.18f
                            }
                            isActiveLine -> 1.0f
                            dist == 1 -> 0.55f
                            dist == 2 -> 0.36f
                            else -> 0.22f
                        }
                        val targetScale = when {
                            isActiveLine -> 1.05f
                            dist == 1 -> 0.97f
                            else -> 0.94f
                        }
                        val animAlpha by animateFloatAsState(
                            targetValue = targetAlpha,
                            animationSpec = tween(340, easing = smoothCurve),
                            label = "ttml_alpha_$idx",
                        )
                        val animScale by animateFloatAsState(
                            targetValue = targetScale,
                            animationSpec = tween(380, easing = smoothCurve),
                            label = "ttml_scale_$idx",
                        )

                        val alignEnd = when (lyricsAlignment) {
                            com.nimiyo.nativeapp.core.LyricsAlignmentMode.LEFT -> false
                            com.nimiyo.nativeapp.core.LyricsAlignmentMode.RIGHT -> true
                            com.nimiyo.nativeapp.core.LyricsAlignmentMode.SPLIT_DUET -> line.isOpposite
                        }
                        val hArrange = if (alignEnd) Arrangement.End else Arrangement.Start
                        val prevEnd = if (idx > 0) ttmlLines[idx - 1].endMs else 0L
                        val showInterlude = idx > 0 &&
                            (line.beginMs - prevEnd >= 4500L) &&
                            (smoothPosMs in prevEnd until line.beginMs)
                        if (showInterlude) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth(0.95f)
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = hArrange,
                            ) {
                                Text(
                                    text = "•  •  •",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White.copy(alpha = 0.72f),
                                )
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth(0.95f)
                                .graphicsLayer {
                                    scaleX = animScale
                                    scaleY = animScale
                                    alpha = animAlpha
                                    transformOrigin = TransformOrigin(if (alignEnd) 1f else 0f, 0.5f)
                                }
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(enabled = tapToSeek) { onSeek(line.beginMs) }
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                            horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
                        ) {
                            val ttmlSp = lyricsFontSize.ttmlSp
                            if (line.mainWords.isNotEmpty()) {
                                if (isActiveLine) {
                                    ActiveTtmlWordsFlow(
                                        words = line.mainWords,
                                        posProvider = { (smoothPosMs - 40L).coerceAtLeast(0L) },
                                        horizontalArrangement = hArrange,
                                        animMode = ttmlAnimMode,
                                        fontSizeSp = ttmlSp,
                                    )
                                } else {
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = hArrange,
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        val wordAlpha = if (idx < activeIdx) 0.70f else 0.34f
                                        line.mainWords.forEach { word ->
                                            val cleanWord = word.text.trimEnd()
                                            if (cleanWord.isNotEmpty()) {
                                                Text(
                                                    text = "$cleanWord ",
                                                    fontSize = ttmlSp.sp,
                                                    lineHeight = (ttmlSp + 8).sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = Color.White.copy(alpha = wordAlpha),
                                                )
                                            }
                                        }
                                    }
                                }
                            } else if (line.text.isNotBlank() && line.bgWords.isEmpty()) {
                                Text(
                                    text = line.text,
                                    fontSize = ttmlSp.sp,
                                    lineHeight = (ttmlSp + 8).sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                )
                            }

                            if (showTranslation && line.translation.isNotBlank()) {
                                Text(
                                    text = line.translation,
                                    fontSize = (ttmlSp - 8).coerceAtLeast(12).sp,
                                    lineHeight = (ttmlSp - 2).coerceAtLeast(17).sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White.copy(alpha = if (isActiveLine) 0.78f else 0.45f),
                                )
                            }

                            if (line.bgWords.isNotEmpty()) {
                                TtmlBgVocalsSection(
                                    line = line,
                                    isActiveLine = isActiveLine,
                                    posProvider = { (smoothPosMs - 40L).coerceAtLeast(0L) },
                                    bgCurve = bgCurve,
                                    smoothCurve = smoothCurve,
                                    horizontalArrangement = hArrange,
                                    fontSizeSp = (ttmlSp - 6).coerceAtLeast(13),
                                )
                            }
                        }
                    }
                }
            } else {
                // Classic Centered LRC Mode (smooth scale + fade + viewport-centered glide)
                val lines = stateDoc?.lines.orEmpty()
                val activeIdx by remember(lines) {
                    derivedStateOf {
                        val p = smoothPosMs
                        lines.indexOfLast { it.timeMs <= p }.coerceAtLeast(-1)
                    }
                }
                val listState = rememberLazyListState()
                var viewportHeightPx by remember { mutableIntStateOf(0) }

                LaunchedEffect(activeIdx, viewportHeightPx) {
                    if (activeIdx >= 0 && viewportHeightPx > 0) {
                        val visible = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == activeIdx }
                        if (visible != null) {
                            val targetTop = (viewportHeightPx * 0.42f) - (visible.size * 0.5f)
                            val delta = visible.offset.toFloat() - targetTop
                            if (abs(delta) > 4f) {
                                runCatching {
                                    listState.animateScrollBy(
                                        delta,
                                        tween(durationMillis = 480, easing = smoothCurve),
                                    )
                                }
                            }
                        } else {
                            runCatching {
                                listState.animateScrollToItem(
                                    index = activeIdx,
                                    scrollOffset = -(viewportHeightPx * 0.36f).toInt(),
                                )
                            }
                        }
                    } else if (activeIdx < 0) {
                        runCatching { listState.animateScrollToItem(0) }
                    }
                }

                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(top = 80.dp, bottom = 180.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { viewportHeightPx = it.height }
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                brush = Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    0.10f to Color.Black,
                                    0.85f to Color.Black,
                                    1f to Color.Transparent,
                                ),
                                blendMode = BlendMode.DstIn,
                            )
                        },
                ) {
                    itemsIndexed(lines) { idx, line ->
                        val isActive = idx == activeIdx
                        val dist = if (activeIdx < 0) idx else abs(idx - activeIdx)
                        val targetAlpha = when {
                            activeIdx < 0 -> when (idx) {
                                0 -> 0.95f
                                1 -> 0.65f
                                2 -> 0.38f
                                else -> 0.18f
                            }
                            isActive -> 1.0f
                            dist == 1 -> 0.72f
                            dist == 2 -> 0.46f
                            dist == 3 -> 0.28f
                            else -> 0.16f
                        }
                        val targetScale = when {
                            isActive -> 1.05f
                            dist == 1 -> 0.97f
                            else -> 0.94f
                        }
                        val animAlpha by animateFloatAsState(
                            targetValue = targetAlpha,
                            animationSpec = tween(320, easing = smoothCurve),
                            label = "lrc_alpha_$idx",
                        )
                        val animScale by animateFloatAsState(
                            targetValue = targetScale,
                            animationSpec = tween(360, easing = smoothCurve),
                            label = "lrc_scale_$idx",
                        )
                        val animBg by animateColorAsState(
                            targetValue = if (isActive) accentColor.copy(alpha = 0.28f) else Color.Transparent,
                            animationSpec = tween(340, easing = smoothCurve),
                            label = "lrc_bg_$idx",
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.94f)
                                .graphicsLayer {
                                    scaleX = animScale
                                    scaleY = animScale
                                    alpha = animAlpha
                                    transformOrigin = TransformOrigin.Center
                                }
                                .clip(RoundedCornerShape(14.dp))
                                .background(animBg)
                                .clickable(enabled = tapToSeek) { onSeek(line.timeMs) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = line.text,
                                textAlign = TextAlign.Center,
                                fontSize = lyricsFontSize.lrcSp.sp,
                                lineHeight = (lyricsFontSize.lrcSp + 7).sp,
                                fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold,
                                color = Color.White,
                            )
                        }
                    }
                }
            }
        }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveTtmlWordsFlow(
    words: List<com.nimiyo.nativeapp.player.LyricWord>,
    posProvider: () -> Long,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    animMode: com.nimiyo.nativeapp.core.TtmlAnimationMode = com.nimiyo.nativeapp.core.TtmlAnimationMode.PER_CHAR,
    fontSizeSp: Int = 22,
) {
    val compensatedPos = posProvider()
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        words.forEach { word ->
            val cleanWord = word.text.trimEnd()
            if (cleanWord.isNotEmpty()) {
                val dur = (word.endMs - word.startMs).coerceAtLeast(1L).toFloat()
                val wordProg = if (compensatedPos >= word.endMs) {
                    1f
                } else if (compensatedPos <= word.startMs) {
                    0f
                } else {
                    ((compensatedPos - word.startMs).toFloat() / dur).coerceIn(0f, 1f)
                }
                val isWordActive = compensatedPos in word.startMs..word.endMs

                if (animMode == com.nimiyo.nativeapp.core.TtmlAnimationMode.PER_WORD) {
                    val wordAlpha = 0.28f + 0.72f * wordProg
                    val liftFactor = if (isWordActive) kotlin.math.sin(wordProg * kotlin.math.PI).toFloat().coerceAtLeast(0f) else 0f
                    Text(
                        text = "$cleanWord ",
                        fontSize = fontSizeSp.sp,
                        lineHeight = (fontSizeSp + 8).sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White.copy(alpha = wordAlpha),
                        modifier = if (liftFactor > 0.01f) {
                            Modifier.graphicsLayer {
                                translationY = -0.6.dp.toPx() * liftFactor
                                val ws = 1f + 0.016f * liftFactor
                                scaleX = ws
                                scaleY = ws
                            }
                        } else {
                            Modifier
                        },
                    )
                } else {
                    val totalChars = cleanWord.length.coerceAtLeast(1)
                    val cursor = wordProg * totalChars
                    Row(modifier = Modifier.padding(end = 6.dp)) {
                        cleanWord.forEachIndexed { cIdx, ch ->
                            val charProg = when {
                                compensatedPos >= word.endMs -> 1f
                                compensatedPos <= word.startMs -> 0f
                                else -> (cursor - cIdx).coerceIn(0f, 1f)
                            }
                            val charAlpha = 0.28f + 0.72f * charProg
                            val diff = (cIdx + 0.5f) - cursor
                            val factor = if (isWordActive && kotlin.math.abs(diff) < 1.45f) {
                                kotlin.math.cos((diff / 1.45f) * (kotlin.math.PI / 2.0).toFloat()).coerceAtLeast(0f)
                            } else {
                                0f
                            }
                            Text(
                                text = ch.toString(),
                                fontSize = fontSizeSp.sp,
                                lineHeight = (fontSizeSp + 8).sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White.copy(alpha = charAlpha),
                                modifier = if (factor > 0.005f) {
                                    Modifier.graphicsLayer {
                                        translationY = -0.55.dp.toPx() * factor
                                        val cs = 1f + 0.012f * factor
                                        scaleX = cs
                                        scaleY = cs
                                    }
                                } else {
                                    Modifier
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TtmlBgVocalsSection(
    line: TtmlDisplayLine,
    isActiveLine: Boolean,
    posProvider: () -> Long,
    bgCurve: CubicBezierEasing,
    smoothCurve: CubicBezierEasing,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    fontSizeSp: Int = 16,
) {
    val bgBegin = line.bgWords.first().startMs
    val bgEnd = line.bgWords.last().endMs
    val showBgAccordion by remember(isActiveLine, bgBegin, bgEnd) {
        derivedStateOf {
            if (isActiveLine) true else posProvider() in (bgBegin - 50L)..(bgEnd + 140L)
        }
    }
    if (line.isPureBg || line.mainWords.isEmpty()) {
        val compensatedPos = if (isActiveLine) posProvider() else -1L
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = horizontalArrangement,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            line.bgWords.forEach { bw ->
                val bwAlpha = when {
                    !isActiveLine -> 0.32f
                    compensatedPos >= bw.endMs -> 0.90f
                    compensatedPos >= bw.startMs -> {
                        val p = ((compensatedPos - bw.startMs).toFloat() /
                            (bw.endMs - bw.startMs).coerceAtLeast(1L)).coerceIn(0f, 1f)
                        0.28f + 0.62f * p
                    }
                    else -> 0.28f
                }
                Text(
                    text = "${bw.text.trim()} ",
                    fontSize = 17.sp,
                    lineHeight = 23.sp,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = bwAlpha),
                )
            }
        }
    } else {
        androidx.compose.animation.AnimatedVisibility(
            visible = showBgAccordion,
            enter = expandVertically(tween(340, easing = bgCurve)) +
                fadeIn(tween(260, easing = smoothCurve)) +
                slideInVertically(tween(340, easing = bgCurve)) { -it / 4 },
            exit = shrinkVertically(tween(300, easing = smoothCurve)) +
                fadeOut(tween(220)),
        ) {
            val compensatedPos = posProvider()
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 5.dp),
                horizontalArrangement = horizontalArrangement,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                line.bgWords.forEach { bw ->
                    val bwAlpha = when {
                        compensatedPos >= bw.endMs -> 0.88f
                        compensatedPos >= bw.startMs -> {
                            val p = ((compensatedPos - bw.startMs).toFloat() /
                                (bw.endMs - bw.startMs).coerceAtLeast(1L)).coerceIn(0f, 1f)
                            0.28f + 0.60f * p
                        }
                        else -> 0.28f
                    }
                    Text(
                        text = "${bw.text.trim()} ",
                        fontSize = (fontSizeSp - 1).coerceAtLeast(12).sp,
                        lineHeight = (fontSizeSp + 4).coerceAtLeast(18).sp,
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = bwAlpha),
                    )
                }
            }
        }
    }
}
