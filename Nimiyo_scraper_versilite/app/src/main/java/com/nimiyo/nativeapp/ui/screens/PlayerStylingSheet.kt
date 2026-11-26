package com.nimiyo.nativeapp.ui.screens

import android.content.Intent
import android.graphics.ImageDecoder
import android.graphics.Typeface
import android.graphics.drawable.AnimatedImageDrawable
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.view.TextureView
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nimiyo.nativeapp.NimiyoAppContainer
import com.nimiyo.nativeapp.core.LyricsBackdropMode
import com.nimiyo.nativeapp.core.LyricsFontPref
import com.nimiyo.nativeapp.core.LyricsFontWeightPref
import com.nimiyo.nativeapp.core.PlayerStylingConfig
import com.nimiyo.nativeapp.core.SettingsState
import com.nimiyo.nativeapp.core.Strings
import java.io.File
import kotlin.math.roundToInt

@Composable
fun rememberLyricsFontFamily(style: PlayerStylingConfig): FontFamily {
    return remember(style.fontPref, style.customFontPath) {
        when (style.fontPref) {
            LyricsFontPref.DEFAULT -> FontFamily.Default
            LyricsFontPref.SYSTEM -> FontFamily.SansSerif
            LyricsFontPref.SERIF -> FontFamily.Serif
            LyricsFontPref.MONOSPACE -> FontFamily.Monospace
            LyricsFontPref.CURSIVE -> FontFamily.Cursive
            LyricsFontPref.CUSTOM -> {
                val path = style.customFontPath
                if (path.isNotBlank() && File(path).exists()) {
                    runCatching { FontFamily(Typeface.createFromFile(path)) }
                        .getOrDefault(FontFamily.Default)
                } else {
                    FontFamily.Default
                }
            }
        }
    }
}

fun LyricsFontWeightPref.toComposeWeight(): FontWeight = when (this) {
    LyricsFontWeightPref.NORMAL -> FontWeight.Normal
    LyricsFontWeightPref.MEDIUM -> FontWeight.Medium
    LyricsFontWeightPref.BOLD -> FontWeight.Bold
    LyricsFontWeightPref.EXTRA_BOLD -> FontWeight.ExtraBold
}

@Composable
fun LyricsCustomBackdrop(
    style: PlayerStylingConfig,
    albumBackdropRequest: ImageRequest?,
    fallbackBackdropBrush: Brush,
    backdropAlpha: Float,
    isActive: Boolean,
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = backdropAlpha },
    ) {
        when (style.backdropMode) {
            LyricsBackdropMode.SOLID_BLACK -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                )
            }
            LyricsBackdropMode.CUSTOM_MEDIA -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    val mediaUri = style.customMediaUri
                    if (mediaUri.isNotBlank()) {
                        val blurDp = (style.mediaBlurPercent.coerceIn(0f, 0.25f) * 40f)
                        val mediaBlurMod = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurDp > 0.5f) {
                            Modifier.blur(blurDp.dp)
                        } else {
                            Modifier
                        }
                        val zoomAlphaMod = Modifier
                            .then(mediaBlurMod)
                            .graphicsLayer {
                                val s = style.mediaScale.coerceIn(0.5f, 2.5f)
                                scaleX = s
                                scaleY = s
                                alpha = style.mediaOpacity.coerceIn(0f, 1f)
                            }

                        if (style.customMediaIsVideo && isActive) {
                            var videoAspect by remember(mediaUri) { mutableFloatStateOf(16f / 9f) }
                            val exoPlayer = remember(mediaUri) {
                                ExoPlayer.Builder(context).build().apply {
                                    setMediaItem(MediaItem.fromUri(Uri.parse(mediaUri)))
                                    repeatMode = Player.REPEAT_MODE_ONE
                                    volume = 0f
                                    playWhenReady = true
                                    prepare()
                                }
                            }
                            DisposableEffect(exoPlayer) {
                                val listener = object : Player.Listener {
                                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                                        if (videoSize.width > 0 && videoSize.height > 0) {
                                            videoAspect = (videoSize.width.toFloat() / videoSize.height.toFloat())
                                                .coerceIn(0.35f, 3.0f)
                                        }
                                    }
                                }
                                exoPlayer.addListener(listener)
                                onDispose {
                                    runCatching {
                                        exoPlayer.removeListener(listener)
                                        exoPlayer.stop()
                                        exoPlayer.release()
                                    }
                                }
                            }
                            AndroidView(
                                factory = { ctx ->
                                    TextureView(ctx).also { tv ->
                                        exoPlayer.setVideoTextureView(tv)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(videoAspect)
                                    .then(zoomAlphaMod),
                            )
                        } else if (!style.customMediaIsVideo) {
                            val isAnimatedDrawable = remember(mediaUri) {
                                val lower = mediaUri.lowercase()
                                val mime = runCatching { context.contentResolver.getType(Uri.parse(mediaUri)) }.getOrNull().orEmpty()
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                                    (mime.contains("gif") || mime.contains("webp") || lower.endsWith(".gif") || lower.endsWith(".webp"))
                            }
                            if (isAnimatedDrawable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                AndroidView(
                                    factory = { ctx ->
                                        ImageView(ctx).apply {
                                            scaleType = ImageView.ScaleType.FIT_CENTER
                                            adjustViewBounds = true
                                        }
                                    },
                                    update = { iv ->
                                        runCatching {
                                            val source = ImageDecoder.createSource(context.contentResolver, Uri.parse(mediaUri))
                                            val drawable = ImageDecoder.decodeDrawable(source)
                                            iv.setImageDrawable(drawable)
                                            if (drawable is AnimatedImageDrawable) {
                                                drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                                                drawable.start()
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(zoomAlphaMod),
                                )
                            } else {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(Uri.parse(mediaUri))
                                        .crossfade(false)
                                        .build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .then(zoomAlphaMod),
                                )
                            }
                        }

                        // Soft left & right edge fade into black
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        0.0f to Color.Black,
                                        0.20f to Color.Black.copy(alpha = 0.22f),
                                        0.50f to Color.Transparent,
                                        0.80f to Color.Black.copy(alpha = 0.22f),
                                        1.0f to Color.Black,
                                    )
                                ),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(fallbackBackdropBrush),
                        )
                    }
                }
            }
            LyricsBackdropMode.ALBUM_BLUR -> {
                if (albumBackdropRequest != null) {
                    AsyncImage(
                        model = albumBackdropRequest,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .let { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) it.blur(26.dp) else it },
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(fallbackBackdropBrush),
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.45f),
                                    Color.Black.copy(alpha = 0.68f),
                                    Color.Black.copy(alpha = 0.78f),
                                )
                            )
                        ),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicSettingsAndStylingSheet(
    container: NimiyoAppContainer,
    strings: Strings,
    settingsState: SettingsState,
    initialTab: Int = 0,
    onDismiss: () -> Unit,
) {
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab) }
    var showLowBatteryDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(strings.music.tabMusicSetting, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(Icons.Rounded.Palette, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(strings.music.tabPlayerStyling, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    },
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 14.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (selectedTab == 0) {
                    MusicSettingsBody(
                        container = container,
                        strings = strings,
                        playerSettings = settingsState,
                        onOpenLowBatteryDialog = { showLowBatteryDialog = true },
                    )
                } else {
                    PlayerStylingBody(
                        container = container,
                        strings = strings,
                        settingsState = settingsState,
                    )
                }
            }
        }
    }

    if (showLowBatteryDialog) {
        LowBatteryThresholdDialog(
            currentPercent = settingsState.musicLowBatteryPercent,
            strings = strings,
            onSave = { pct ->
                container.settings.update { it.copy(musicLowBatteryPercent = pct) }
                showLowBatteryDialog = false
            },
            onDismiss = { showLowBatteryDialog = false },
        )
    }
}

@Composable
fun MusicSettingsBody(
    container: NimiyoAppContainer,
    strings: Strings,
    playerSettings: SettingsState,
    onOpenLowBatteryDialog: () -> Unit,
) {
    val player = container.playerController
    Text(
        text = strings.music.title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.ExtraBold,
    )

    MusicSettingSwitchRow(
        title = strings.music.pauseAtDisconnectTitle,
        desc = strings.music.pauseAtDisconnectDesc,
        checked = playerSettings.musicPauseAtDisconnect,
        onCheckedChange = { v -> container.settings.update { it.copy(musicPauseAtDisconnect = v) } },
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                container.settings.update { it.copy(musicStopOnLowBattery = !playerSettings.musicStopOnLowBattery) }
            }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = strings.music.stopLowBatteryTitle,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${strings.music.stopLowBatteryDesc} (${playerSettings.musicLowBatteryPercent}%)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onOpenLowBatteryDialog) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = strings.music.lowBatteryThresholdTitle,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Switch(
                checked = playerSettings.musicStopOnLowBattery,
                onCheckedChange = { v -> container.settings.update { it.copy(musicStopOnLowBattery = v) } },
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

    Text(
        text = strings.music.autoPlaySection,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
    MusicSettingSwitchRow(
        title = strings.music.autoPlayHeadsetTitle,
        desc = strings.music.autoPlayHeadsetDesc,
        checked = playerSettings.musicAutoPlayHeadset,
        onCheckedChange = { v -> container.settings.update { it.copy(musicAutoPlayHeadset = v) } },
    )
    MusicSettingSwitchRow(
        title = strings.music.autoPlayBluetoothTitle,
        desc = strings.music.autoPlayBluetoothDesc,
        checked = playerSettings.musicAutoPlayBluetooth,
        onCheckedChange = { v -> container.settings.update { it.copy(musicAutoPlayBluetooth = v) } },
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

    MusicSettingSwitchRow(
        title = strings.music.resumeAfterCallTitle,
        desc = strings.music.resumeAfterCallDesc,
        checked = playerSettings.musicResumeAfterCall,
        onCheckedChange = { v -> container.settings.update { it.copy(musicResumeAfterCall = v) } },
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    MusicSettingSwitchRow(
        title = strings.music.alwaysOnTopTitle,
        desc = strings.music.alwaysOnTopDesc,
        checked = playerSettings.musicAlwaysPlayOnTop,
        onCheckedChange = { v -> container.settings.update { it.copy(musicAlwaysPlayOnTop = v) } },
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    MusicSettingSwitchRow(
        title = strings.music.monoAudioTitle,
        desc = strings.music.monoAudioDesc,
        checked = playerSettings.musicMonoAudio,
        onCheckedChange = { v ->
            container.settings.update { it.copy(musicMonoAudio = v) }
            player.setMonoAudio(v)
        },
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    MusicSettingSwitchRow(
        title = strings.music.loudnessNormTitle,
        desc = strings.music.loudnessNormDesc,
        checked = playerSettings.musicLoudnessNorm,
        onCheckedChange = { v ->
            container.settings.update { it.copy(musicLoudnessNorm = v) }
            player.setLoudnessParams(v, playerSettings.musicGainDb)
        },
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = strings.music.volumeGainLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (playerSettings.musicGainDb == 0) "0 dB" else "+${playerSettings.musicGainDb} dB",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = playerSettings.musicGainDb.toFloat(),
            onValueChange = { raw ->
                val db = kotlin.math.round(raw).toInt().coerceIn(0, 9)
                if (db != playerSettings.musicGainDb) {
                    container.settings.update { it.copy(musicGainDb = db) }
                    player.setLoudnessParams(playerSettings.musicLoudnessNorm, db)
                }
            },
            valueRange = 0f..9f,
            steps = 8,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = strings.music.playlistPillSwapTitle,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = strings.music.playlistPillSwapDesc,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val swapOptions = listOf(
            com.nimiyo.nativeapp.core.PlaylistPillSwapMode.NONE to strings.music.playlistPillSwapOff,
            com.nimiyo.nativeapp.core.PlaylistPillSwapMode.SONGS to strings.playerTabSongs.uppercase(),
            com.nimiyo.nativeapp.core.PlaylistPillSwapMode.ARTISTS to strings.playerTabArtists.uppercase(),
            com.nimiyo.nativeapp.core.PlaylistPillSwapMode.ALBUMS to strings.playerTabAlbums.uppercase(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            swapOptions.forEach { (mode, lbl) ->
                FilterChip(
                    selected = playerSettings.musicPlaylistPillSwap == mode,
                    onClick = { container.settings.update { it.copy(musicPlaylistPillSwap = mode) } },
                    modifier = Modifier.weight(1f),
                    label = {
                        Text(
                            text = lbl,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                )
            }
        }
    }
}

@Composable
fun LowBatteryThresholdDialog(
    currentPercent: Int,
    strings: Strings,
    onSave: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var tempPct by remember(currentPercent) {
        mutableIntStateOf(currentPercent.coerceIn(5, 50))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.music.lowBatteryThresholdTitle, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "$tempPct%",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Slider(
                    value = tempPct.toFloat(),
                    onValueChange = { raw ->
                        tempPct = (kotlin.math.round(raw / 5f) * 5).toInt().coerceIn(5, 50)
                    },
                    valueRange = 5f..50f,
                    steps = 8,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(10, 15, 20, 30).forEach { pct ->
                        FilterChip(
                            selected = tempPct == pct,
                            onClick = { tempPct = pct },
                            modifier = Modifier.weight(1f),
                            label = {
                                Text(
                                    text = "$pct%",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(tempPct) }) {
                Text(strings.saveBtn, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancelBtn)
            }
        },
    )
}

@Composable
fun PlayerStylingBody(
    container: NimiyoAppContainer,
    strings: Strings,
    settingsState: SettingsState,
) {
    val context = LocalContext.current
    val style = settingsState.playerStyling

    fun updateStyle(transform: (PlayerStylingConfig) -> PlayerStylingConfig) {
        container.settings.update { curr ->
            curr.copy(playerStyling = transform(curr.playerStyling))
        }
    }

    fun queryDisplayName(uri: Uri): String {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull().orEmpty().ifBlank { uri.lastPathSegment.orEmpty() }
    }

    val fontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val name = queryDisplayName(uri).ifBlank { "custom_font.ttf" }
                val ext = name.substringAfterLast('.', "ttf").lowercase()
                val dest = File(context.filesDir, "custom_lyrics_font.$ext")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                Typeface.createFromFile(dest)
                updateStyle {
                    it.copy(
                        fontPref = LyricsFontPref.CUSTOM,
                        customFontPath = dest.absolutePath,
                        customFontName = name,
                    )
                }
                Toast.makeText(context, "${strings.music.toastFontApplied} $name", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, strings.music.toastFontInvalid, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull().orEmpty()
            val name = queryDisplayName(uri)
            val isVideo = mime.startsWith("video/") ||
                name.lowercase().let { it.endsWith(".mp4") || it.endsWith(".webm") || it.endsWith(".mkv") || it.endsWith(".mov") }
            updateStyle {
                it.copy(
                    backdropMode = LyricsBackdropMode.CUSTOM_MEDIA,
                    customMediaUri = uri.toString(),
                    customMediaIsVideo = isVideo,
                    customMediaLabel = name.ifBlank { if (isVideo) "Video" else "Media" },
                )
            }
        }
    }

    Text(
        text = strings.music.stylingHeaderTitle,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.ExtraBold,
    )

    // 1. Latar Belakang Lirik (di paling atas, di atas kategori Lirik)
    Text(
        text = strings.music.lyricsBackdropSection,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
    val bgOptions = listOf(
        LyricsBackdropMode.ALBUM_BLUR to strings.music.backdropAlbumBlur,
        LyricsBackdropMode.CUSTOM_MEDIA to strings.music.backdropCustomMedia,
        LyricsBackdropMode.SOLID_BLACK to strings.music.backdropSolidBlack,
    )
    MusicSettingDropdownRow(
        label = strings.music.backdropModeLabel,
        selectedLabel = bgOptions.firstOrNull { it.first == style.backdropMode }?.second
            ?: strings.music.backdropAlbumBlur,
        options = bgOptions,
        onSelect = { mode -> updateStyle { it.copy(backdropMode = mode) } },
    )

    if (style.backdropMode == LyricsBackdropMode.CUSTOM_MEDIA) {
        OutlinedButton(
            onClick = { mediaPickerLauncher.launch(arrayOf("image/*", "video/*")) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (style.customMediaLabel.isNotBlank()) {
                    "${strings.music.changeMediaBtn} (${style.customMediaLabel})"
                } else {
                    strings.music.pickMediaBtn
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        StylingSliderRow(
            label = strings.music.mediaZoomLabel,
            valueText = "${"%.2f".format(style.mediaScale)}x",
            value = style.mediaScale,
            range = 0.5f..2.5f,
            onValueChange = { v -> updateStyle { it.copy(mediaScale = v) } },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = {
                updateStyle { it.copy(mediaScale = 1f) }
            }) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(strings.music.resetZoomBtn)
            }
        }
        StylingSliderRow(
            label = strings.music.mediaBlurLabel,
            valueText = "${(style.mediaBlurPercent.coerceIn(0f, 0.25f) * 100f).roundToInt()}%",
            value = style.mediaBlurPercent.coerceIn(0f, 0.25f),
            range = 0f..0.25f,
            onValueChange = { v -> updateStyle { it.copy(mediaBlurPercent = v) } },
        )
        StylingSliderRow(
            label = strings.music.mediaOpacityLabel,
            valueText = "${(style.mediaOpacity * 100f).roundToInt()}%",
            value = style.mediaOpacity,
            range = 0f..1f,
            onValueChange = { v -> updateStyle { it.copy(mediaOpacity = v) } },
        )
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

    // 2. Kategori Lirik
    Text(
        text = strings.lyricsTitle,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
    val alignOptions = listOf(
        com.nimiyo.nativeapp.core.LyricsAlignmentMode.LEFT to strings.ui.lyricsAlignLeft,
        com.nimiyo.nativeapp.core.LyricsAlignmentMode.RIGHT to strings.ui.lyricsAlignRight,
        com.nimiyo.nativeapp.core.LyricsAlignmentMode.SPLIT_DUET to strings.ui.lyricsAlignSplitDuet,
    )
    MusicSettingDropdownRow(
        label = strings.music.lyricsAlignLabel,
        desc = strings.music.lyricsAlignDesc,
        selectedLabel = alignOptions.firstOrNull { it.first == settingsState.musicLyricsAlignment }?.second
            ?: strings.ui.lyricsAlignLeft,
        options = alignOptions,
        onSelect = { mode -> container.settings.update { it.copy(musicLyricsAlignment = mode) } },
    )
    val ttmlAnimOptions = listOf(
        com.nimiyo.nativeapp.core.TtmlAnimationMode.PER_CHAR to strings.music.ttmlModePerChar,
        com.nimiyo.nativeapp.core.TtmlAnimationMode.PER_WORD to strings.music.ttmlModePerWord,
    )
    MusicSettingDropdownRow(
        label = strings.music.ttmlModeLabel,
        desc = strings.music.ttmlModeDesc,
        selectedLabel = ttmlAnimOptions.firstOrNull { it.first == settingsState.musicTtmlAnimMode }?.second
            ?: strings.music.ttmlModePerChar,
        options = ttmlAnimOptions,
        onSelect = { mode -> container.settings.update { it.copy(musicTtmlAnimMode = mode) } },
    )
    MusicSettingSwitchRow(
        title = strings.music.lyricsShowTranslationTitle,
        desc = strings.music.lyricsShowTranslationDesc,
        checked = settingsState.musicLyricsShowTranslation,
        onCheckedChange = { v -> container.settings.update { it.copy(musicLyricsShowTranslation = v) } },
    )
    MusicSettingSwitchRow(
        title = strings.music.lyricsTapToSeekTitle,
        desc = strings.music.lyricsTapToSeekDesc,
        checked = settingsState.musicLyricsTapToSeek,
        onCheckedChange = { v -> container.settings.update { it.copy(musicLyricsTapToSeek = v) } },
    )

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

    // 3. Kategori Font Lirik (termasuk Ukuran Teks Lirik)
    Text(
        text = strings.music.lyricsFontSection,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
    val fontOptions = LyricsFontPref.entries.map { it to it.label }
    MusicSettingDropdownRow(
        label = strings.music.fontTypeLabel,
        selectedLabel = if (style.fontPref == LyricsFontPref.CUSTOM && style.customFontName.isNotBlank()) {
            "Custom (${style.customFontName})"
        } else {
            style.fontPref.label
        },
        options = fontOptions,
        onSelect = { pref ->
            if (pref == LyricsFontPref.CUSTOM) {
                if (style.customFontPath.isNotBlank() && File(style.customFontPath).exists()) {
                    updateStyle { it.copy(fontPref = LyricsFontPref.CUSTOM) }
                } else {
                    fontPickerLauncher.launch(arrayOf("*/*"))
                }
            } else {
                updateStyle { it.copy(fontPref = pref) }
            }
        },
    )
    if (style.fontPref == LyricsFontPref.CUSTOM) {
        OutlinedButton(
            onClick = { fontPickerLauncher.launch(arrayOf("*/*")) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (style.customFontName.isNotBlank()) {
                    "${strings.music.changeFontBtn} (${style.customFontName})"
                } else {
                    strings.music.pickFontBtn
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    val fontSizeOptions = listOf(
        com.nimiyo.nativeapp.core.LyricsFontSizePref.SMALL to strings.music.lyricsSizeSmall,
        com.nimiyo.nativeapp.core.LyricsFontSizePref.NORMAL to strings.music.lyricsSizeNormal,
        com.nimiyo.nativeapp.core.LyricsFontSizePref.LARGE to strings.music.lyricsSizeLarge,
        com.nimiyo.nativeapp.core.LyricsFontSizePref.EXTRA_LARGE to strings.music.lyricsSizeExtraLarge,
    )
    MusicSettingDropdownRow(
        label = strings.music.lyricsSizeLabel,
        selectedLabel = fontSizeOptions.firstOrNull { it.first == settingsState.musicLyricsFontSize }?.second
            ?: strings.music.lyricsSizeNormal,
        options = fontSizeOptions,
        onSelect = { sizePref -> container.settings.update { it.copy(musicLyricsFontSize = sizePref) } },
    )
    val weightOptions = LyricsFontWeightPref.entries.map { it to it.label }
    MusicSettingDropdownRow(
        label = strings.music.fontWeightLabel,
        selectedLabel = style.fontWeight.label,
        options = weightOptions,
        onSelect = { w -> updateStyle { it.copy(fontWeight = w) } },
    )
    MusicSettingSwitchRow(
        title = strings.music.textShadowTitle,
        desc = strings.music.textShadowDesc,
        checked = style.textShadow,
        onCheckedChange = { v -> updateStyle { it.copy(textShadow = v) } },
    )
}

@Composable
private fun StylingSliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun MusicSettingSwitchRow(
    title: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun <T> MusicSettingDropdownRow(
    label: String,
    selectedLabel: String,
    options: List<Pair<T, String>>,
    desc: String? = null,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (!desc.isNullOrBlank()) {
                Text(
                    text = desc,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { expanded = true },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = selectedLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(
                        imageVector = Icons.Rounded.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { (value, optionLabel) ->
                    DropdownMenuItem(
                        text = { Text(optionLabel) },
                        onClick = {
                            onSelect(value)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
