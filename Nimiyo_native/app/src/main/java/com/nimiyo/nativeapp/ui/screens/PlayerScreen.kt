package com.nimiyo.nativeapp.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.nimiyo.nativeapp.NimiyoAppContainer
import com.nimiyo.nativeapp.core.Strings
import com.nimiyo.nativeapp.data.AudioTrack
import com.nimiyo.nativeapp.data.PlaylistModel
import com.nimiyo.nativeapp.player.SortOrder
import com.nimiyo.nativeapp.ui.components.formatDurationMs
import com.nimiyo.nativeapp.ui.components.formatFileSize
import com.nimiyo.nativeapp.ui.components.rememberThumbRequest
import com.nimiyo.nativeapp.ui.theme.LocalNimiyoStyle
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    container: NimiyoAppContainer,
    strings: Strings,
    isActive: Boolean = true,
) {
    val player = container.playerController
    val state by player.state.collectAsState()
    val style = LocalNimiyoStyle.current

    var selectedTab by remember { mutableIntStateOf(0) } // 0: NIMIYO, 1: SONGS, 2: ARTISTS, 3: ALBUM
    var searchQuery by remember { mutableStateOf("") }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var showPlaylistSheet by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var trackForPlaylistPicker by remember { mutableStateOf<AudioTrack?>(null) }
    var openedPlaylist by remember { mutableStateOf<PlaylistModel?>(null) }
    var drilldownTitle by remember { mutableStateOf<String?>(null) }
    var drilldownTracks by remember { mutableStateOf<List<AudioTrack>>(emptyList()) }
    var showMusicSettingsSheet by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    val playerSettings by container.settings.state.collectAsState()
    val swapMode = playerSettings.musicPlaylistPillSwap
    var showSwappedCategoryInPlaylist by remember(swapMode) { mutableStateOf(false) }

    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedTrackIds by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(isActive) {
        if (isActive && !state.isScanning) {
            player.refreshLibrary()
        }
    }
    LaunchedEffect(isActive, isSelectionMode) {
        player.setMiniPlayerHidden(isActive && isSelectionMode)
    }
    DisposableEffect(Unit) {
        onDispose { player.setMiniPlayerHidden(false) }
    }
    DisposableEffect(lifecycleOwner, isActive) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME && isActive && !state.isScanning) {
                player.refreshLibrary()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    var showBatchAddToPlaylistPicker by remember { mutableStateOf(false) }
    var showSongPickerDialog by remember { mutableStateOf(false) }
    var trackForOptionsMenu by remember { mutableStateOf<AudioTrack?>(null) }
    var trackForInfoDialog by remember { mutableStateOf<AudioTrack?>(null) }
    var trackForDeleteConfirm by remember { mutableStateOf<AudioTrack?>(null) }
    var showSyncPermDialog by remember { mutableStateOf(false) }

    fun hasAllFilesAccess(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

    fun openAllFilesAccessSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:${context.packageName}"),
                    )
                )
            }.onFailure {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            }
        }
    }

    BackHandler(
        enabled = isActive && (isSelectionMode ||
            showBatchDeleteConfirm ||
            showBatchAddToPlaylistPicker ||
            showSongPickerDialog ||
            trackForOptionsMenu != null ||
            trackForInfoDialog != null ||
            trackForDeleteConfirm != null ||
            showCreatePlaylistDialog ||
            trackForPlaylistPicker != null ||
            openedPlaylist != null ||
            showSwappedCategoryInPlaylist ||
            showPlaylistSheet ||
            drilldownTitle != null ||
            searchQuery.isNotEmpty() ||
            selectedTab != 0)
    ) {
        when {
            trackForDeleteConfirm != null -> trackForDeleteConfirm = null
            trackForInfoDialog != null -> trackForInfoDialog = null
            trackForOptionsMenu != null -> trackForOptionsMenu = null
            showBatchDeleteConfirm -> showBatchDeleteConfirm = false
            showBatchAddToPlaylistPicker -> showBatchAddToPlaylistPicker = false
            showSongPickerDialog -> showSongPickerDialog = false
            isSelectionMode -> {
                isSelectionMode = false
                selectedTrackIds = emptySet()
            }
            showCreatePlaylistDialog -> showCreatePlaylistDialog = false
            trackForPlaylistPicker != null -> trackForPlaylistPicker = null
            openedPlaylist != null -> openedPlaylist = null
            showSwappedCategoryInPlaylist -> showSwappedCategoryInPlaylist = false
            showPlaylistSheet -> showPlaylistSheet = false
            drilldownTitle != null -> drilldownTitle = null
            searchQuery.isNotEmpty() -> searchQuery = ""
            selectedTab != 0 -> selectedTab = 0
        }
    }

    val tabTitles = listOf(
        "NIMIYO",
        if (swapMode == com.nimiyo.nativeapp.core.PlaylistPillSwapMode.SONGS) strings.playerTabPlaylists.uppercase() else strings.playerTabSongs.uppercase(),
        if (swapMode == com.nimiyo.nativeapp.core.PlaylistPillSwapMode.ARTISTS) strings.playerTabPlaylists.uppercase() else strings.playerTabArtists.uppercase(),
        if (swapMode == com.nimiyo.nativeapp.core.PlaylistPillSwapMode.ALBUMS) strings.playerTabPlaylists.uppercase() else strings.playerTabAlbums.uppercase(),
    )

    val filteredTracks = remember(state.tracks, selectedTab, searchQuery) {
        val base = when (selectedTab) {
            0 -> state.tracks.filter { it.isNimiyoFolder }
            else -> state.tracks
        }
        val q = searchQuery.trim()
        if (q.isEmpty()) base else base.filter {
            it.title.contains(q, ignoreCase = true) ||
                it.artist.contains(q, ignoreCase = true) ||
                it.album.contains(q, ignoreCase = true)
        }
    }

    val nimiyoCount = remember(state.tracks) { state.tracks.count { it.isNimiyoFolder } }
    val totalCount = state.tracks.size

    val sortLabel = when (state.sortOrder) {
        SortOrder.DATE_DESC -> strings.sortNewest
        SortOrder.DATE_ASC -> strings.sortOldest
        SortOrder.TITLE_ASC -> "${strings.titleLabel} (A-Z)"
        SortOrder.TITLE_DESC -> "${strings.titleLabel} (Z-A)"
        SortOrder.ARTIST_ASC -> "${strings.artistLabel} (A-Z)"
        SortOrder.DURATION_DESC -> strings.sortLongest
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Nimiyo Flagship Player Header: "PLAYER" left + circular Playlist & Refresh right
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isSelectionMode) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        isSelectionMode = false
                        selectedTrackIds = emptySet()
                    }) {
                        Icon(Icons.Rounded.Close, contentDescription = strings.cancelBtn, tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${selectedTrackIds.size} ${strings.selectedCountSuffix}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                val currentList = if (drilldownTitle != null) drilldownTracks else filteredTracks
                val currentIds = currentList.map { it.id }.toSet()
                val allSelected = currentIds.isNotEmpty() && selectedTrackIds.containsAll(currentIds)
                TextButton(onClick = {
                    selectedTrackIds = if (allSelected) {
                        selectedTrackIds - currentIds
                    } else {
                        selectedTrackIds + currentIds
                    }
                }) {
                    Text(if (allSelected) strings.deselectAllLabel else strings.selectAllLabel, fontWeight = FontWeight.Bold)
                }
            } else if (drilldownTitle != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable { drilldownTitle = null },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = style.cardBorder,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = drilldownTitle!!,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                Text(
                    text = "PLAYER",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                )
            }

            if (!isSelectionMode) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (swapMode == com.nimiyo.nativeapp.core.PlaylistPillSwapMode.NONE) {
                        Surface(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .clickable { showPlaylistSheet = true },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            border = style.cardBorder,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.QueueMusic,
                                    contentDescription = strings.playerTabPlaylists,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                    Surface(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .clickable { showMusicSettingsSheet = true },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        border = style.cardBorder,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.Settings,
                                contentDescription = strings.music.title,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }

        if (drilldownTitle != null) {
            TrackListSection(
                tracks = drilldownTracks,
                currentTrackId = state.currentTrack?.id,
                emptyText = strings.noTracksFound,
                isSelectionMode = isSelectionMode,
                selectedTrackIds = selectedTrackIds,
                onToggleSelect = { track ->
                    selectedTrackIds = if (selectedTrackIds.contains(track.id)) {
                        selectedTrackIds - track.id
                    } else {
                        selectedTrackIds + track.id
                    }
                },
                onTrackLongClick = { track ->
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    isSelectionMode = true
                    selectedTrackIds = selectedTrackIds + track.id
                },
                onTrackClick = { idx -> player.playFromList(drilldownTracks, idx) },
                onTrackOptions = { track -> trackForOptionsMenu = track },
            )
        } else {
            // Segmented Rounded Pill Bar matching Nimiyo Layout + Material 3 Tonal Surfaces
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(style.cornerRadius),
            color = style.cardSurface,
            border = style.cardBorder,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                tabTitles.forEachIndexed { idx, label ->
                    val active = selectedTab == idx
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(style.cornerRadius * 0.7f))
                            .clickable { selectedTab = idx },
                        shape = RoundedCornerShape(style.cornerRadius * 0.7f),
                        color = if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label,
                                maxLines = 1,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Full-Width Search Pill
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            placeholder = {
                Text(
                    strings.searchTracksHint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(style.cornerRadius),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = style.cardSurface,
                unfocusedContainerColor = style.cardSurface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
        )

        // Action Pills Row: [Shuffle All] [Sort] + [X NIMIYO • Y Songs]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable {
                        val activeTracks = if (drilldownTitle != null) drilldownTracks else filteredTracks
                        if (state.shuffle) player.toggleShuffle() else player.shuffleAll(activeTracks)
                    },
                shape = RoundedCornerShape(50),
                color = if (state.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                border = if (state.shuffle) null else style.cardBorder,
            ) {
                val shuffleTint = if (state.shuffle) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Shuffle,
                        contentDescription = null,
                        tint = shuffleTint,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        strings.shuffleBtn,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = shuffleTint,
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Box {
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { sortMenuExpanded = true },
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    border = style.cardBorder,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Sort,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            sortLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
                DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                    listOf(
                        SortOrder.DATE_DESC to strings.sortNewest,
                        SortOrder.DATE_ASC to strings.sortOldest,
                        SortOrder.TITLE_ASC to "${strings.titleLabel} (A - Z)",
                        SortOrder.TITLE_DESC to "${strings.titleLabel} (Z - A)",
                        SortOrder.ARTIST_ASC to "${strings.artistLabel} (A - Z)",
                        SortOrder.DURATION_DESC to strings.sortLongest,
                    ).forEach { (order, label) ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = label,
                                    fontWeight = if (state.sortOrder == order) FontWeight.Bold else FontWeight.Normal,
                                    color = if (state.sortOrder == order) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                            },
                            onClick = {
                                player.setSortOrder(order)
                                sortMenuExpanded = false
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Text(
                text = "$nimiyoCount NIMIYO • $totalCount ${strings.playerTabSongs}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                if (playerSettings.transitionAnimations) {
                    fadeIn(tween(220)) togetherWith fadeOut(tween(160))
                } else {
                    androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
                }
            },
            modifier = Modifier.weight(1f),
            label = "PlayerTabTransition",
        ) { tab ->
            when {
                (tab == 1 && swapMode == com.nimiyo.nativeapp.core.PlaylistPillSwapMode.SONGS) ||
                    (tab == 2 && swapMode == com.nimiyo.nativeapp.core.PlaylistPillSwapMode.ARTISTS) ||
                    (tab == 3 && swapMode == com.nimiyo.nativeapp.core.PlaylistPillSwapMode.ALBUMS) -> {
                    val curPl = openedPlaylist?.let { open -> state.playlists.firstOrNull { it.id == open.id } ?: open }
                    if (showSwappedCategoryInPlaylist) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(onClick = { showSwappedCategoryInPlaylist = false }) {
                                    Text("← ${strings.playerTabPlaylists}", fontWeight = FontWeight.Bold)
                                }
                            }
                            when (swapMode) {
                                com.nimiyo.nativeapp.core.PlaylistPillSwapMode.SONGS -> {
                                    TrackListSection(
                                        tracks = filteredTracks,
                                        currentTrackId = state.currentTrack?.id,
                                        emptyText = strings.noTracksFound,
                                        isSelectionMode = isSelectionMode,
                                        selectedTrackIds = selectedTrackIds,
                                        onToggleSelect = { track ->
                                            selectedTrackIds = if (selectedTrackIds.contains(track.id)) {
                                                selectedTrackIds - track.id
                                            } else {
                                                selectedTrackIds + track.id
                                            }
                                        },
                                        onTrackLongClick = { track ->
                                            if (playerSettings.hapticFeedback) {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                            isSelectionMode = true
                                            selectedTrackIds = selectedTrackIds + track.id
                                        },
                                        onTrackClick = { idx -> player.playFromList(filteredTracks, idx) },
                                        onTrackOptions = { track -> trackForOptionsMenu = track },
                                    )
                                }
                                com.nimiyo.nativeapp.core.PlaylistPillSwapMode.ARTISTS -> {
                                    val grouped = remember(filteredTracks) {
                                        filteredTracks.groupBy { it.artist }.entries.sortedBy { it.key }
                                    }
                                    val cardShape = remember(style.cornerRadius) { RoundedCornerShape(style.cornerRadius * 0.85f) }
                                    val thumbShape = remember { RoundedCornerShape(12.dp) }
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 90.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        items(grouped, key = { "swapped_artist_${it.key}" }) { (artist, songs) ->
                                            Surface(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        drilldownTitle = artist
                                                        drilldownTracks = songs
                                                    },
                                                shape = cardShape,
                                                color = style.cardSurface,
                                                border = style.cardBorder,
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(14.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Surface(
                                                        modifier = Modifier.size(42.dp),
                                                        shape = thumbShape,
                                                        color = MaterialTheme.colorScheme.primaryContainer,
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Icon(Icons.Rounded.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                                        }
                                                    }
                                                    Spacer(Modifier.width(12.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(artist, fontWeight = FontWeight.Bold)
                                                        Text("${songs.size} ${strings.ui.tracksCountSuffix}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                com.nimiyo.nativeapp.core.PlaylistPillSwapMode.ALBUMS -> {
                                    val grouped = remember(filteredTracks) {
                                        filteredTracks.groupBy { it.album }.entries.sortedBy { it.key }
                                    }
                                    val cardShape = remember(style.cornerRadius) { RoundedCornerShape(style.cornerRadius * 0.85f) }
                                    val thumbShape = remember { RoundedCornerShape(12.dp) }
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 90.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        items(grouped, key = { "swapped_album_${it.key}" }) { (album, songs) ->
                                            Surface(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        drilldownTitle = album
                                                        drilldownTracks = songs
                                                    },
                                                shape = cardShape,
                                                color = style.cardSurface,
                                                border = style.cardBorder,
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(14.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Surface(
                                                        modifier = Modifier.size(42.dp),
                                                        shape = thumbShape,
                                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Icon(Icons.Rounded.Album, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                                        }
                                                    }
                                                    Spacer(Modifier.width(12.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(album, fontWeight = FontWeight.Bold)
                                                        Text("${songs.size} ${strings.ui.tracksCountSuffix}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                else -> {}
                            }
                        }
                    } else if (curPl != null) {
                        val plTracks = remember(curPl, state.tracks) {
                            player.resolvePlaylistTracks(curPl, state.tracks)
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(onClick = { openedPlaylist = null }) {
                                    Text("← ${curPl.name}", fontWeight = FontWeight.Bold)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    FilledTonalButton(onClick = { showSongPickerDialog = true }) {
                                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(strings.pickSongsLabel)
                                    }
                                    if (plTracks.isNotEmpty()) {
                                        FilledTonalButton(onClick = { player.playFromList(plTracks, 0, curPl) }) {
                                            Text("${strings.ui.playTrackLabel} (${plTracks.size})")
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 90.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                itemsIndexed(plTracks, key = { idx, t -> "pl_${curPl.id}_${t.id}_$idx" }) { idx, track ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { player.playFromList(plTracks, idx, curPl) }
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(track.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(track.artist, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        IconButton(onClick = {
                                            player.removeTrackFromPlaylist(curPl.id, track.path.ifBlank { track.contentUri })
                                        }) {
                                            Icon(Icons.Rounded.Delete, contentDescription = strings.removeFromPlaylist)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        val swappedCount = when (swapMode) {
                            com.nimiyo.nativeapp.core.PlaylistPillSwapMode.SONGS -> filteredTracks.size
                            com.nimiyo.nativeapp.core.PlaylistPillSwapMode.ARTISTS -> remember(filteredTracks) { filteredTracks.groupBy { it.artist }.size }
                            com.nimiyo.nativeapp.core.PlaylistPillSwapMode.ALBUMS -> remember(filteredTracks) { filteredTracks.groupBy { it.album }.size }
                            else -> 0
                        }
                        val swappedTitle = when (swapMode) {
                            com.nimiyo.nativeapp.core.PlaylistPillSwapMode.SONGS -> strings.playerTabSongs.uppercase()
                            com.nimiyo.nativeapp.core.PlaylistPillSwapMode.ARTISTS -> strings.playerTabArtists.uppercase()
                            com.nimiyo.nativeapp.core.PlaylistPillSwapMode.ALBUMS -> strings.playerTabAlbums.uppercase()
                            else -> ""
                        }
                        val swappedIcon = when (swapMode) {
                            com.nimiyo.nativeapp.core.PlaylistPillSwapMode.SONGS -> Icons.Rounded.MusicNote
                            com.nimiyo.nativeapp.core.PlaylistPillSwapMode.ARTISTS -> Icons.Rounded.Person
                            else -> Icons.Rounded.Album
                        }
                        val cardShape = remember(style.cornerRadius) { RoundedCornerShape(style.cornerRadius * 0.85f) }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 90.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            item(key = "pl_tab_header") {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(strings.playerTabPlaylists, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = {
                                                if (!hasAllFilesAccess()) {
                                                    showSyncPermDialog = true
                                                } else {
                                                    player.syncPlaylistsFromDatabase { count ->
                                                        Toast.makeText(context, "$count ${strings.perm.syncDoneSuffix}", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                                            colors = IconButtonDefaults.filledTonalIconButtonColors(),
                                        ) {
                                            Icon(Icons.Rounded.Sync, contentDescription = strings.syncDatabaseLabel, modifier = Modifier.size(18.dp))
                                        }
                                        FilledTonalButton(onClick = { showCreatePlaylistDialog = true }) {
                                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(strings.createPlaylist)
                                        }
                                    }
                                }
                            }
                            item(key = "pl_swapped_cat") {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(cardShape)
                                        .clickable { showSwappedCategoryInPlaylist = true },
                                    shape = cardShape,
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
                                    border = style.cardBorder,
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            swappedIcon,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            text = "$swappedTitle ($swappedCount)",
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                            itemsIndexed(state.playlists, key = { _, p -> "tab_pl_${p.id}" }) { _, pl ->
                                val isPlayingPl = state.activePlaylistId == pl.id && state.currentTrack != null
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(cardShape)
                                        .clickable { openedPlaylist = pl },
                                    shape = cardShape,
                                    color = if (isPlayingPl) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f) else style.cardSurface,
                                    border = if (isPlayingPl) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else style.cardBorder,
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            if (isPlayingPl) Icons.Rounded.GraphicEq else Icons.AutoMirrored.Rounded.QueueMusic,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                pl.name,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isPlayingPl) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                            )
                                            Text("${pl.trackPaths.size} ${strings.ui.tracksCountSuffix}", style = MaterialTheme.typography.labelSmall)
                                        }
                                        IconButton(onClick = { player.deletePlaylist(pl.id) }) {
                                            Icon(Icons.Rounded.Delete, contentDescription = strings.deleteBtn)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                tab == 0 || tab == 1 -> {
                    TrackListSection(
                        tracks = filteredTracks,
                        currentTrackId = state.currentTrack?.id,
                        emptyText = strings.noTracksFound,
                        isSelectionMode = isSelectionMode,
                        selectedTrackIds = selectedTrackIds,
                        onToggleSelect = { track ->
                            selectedTrackIds = if (selectedTrackIds.contains(track.id)) {
                                selectedTrackIds - track.id
                            } else {
                                selectedTrackIds + track.id
                            }
                        },
                        onTrackLongClick = { track ->
                            if (playerSettings.hapticFeedback) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            isSelectionMode = true
                            selectedTrackIds = selectedTrackIds + track.id
                        },
                        onTrackClick = { idx -> player.playFromList(filteredTracks, idx) },
                        onTrackOptions = { track -> trackForOptionsMenu = track },
                    )
                }
                tab == 2 -> {
                    val grouped = remember(filteredTracks) {
                        filteredTracks.groupBy { it.artist }.entries.sortedBy { it.key }
                    }
                    val cardShape = remember(style.cornerRadius) { RoundedCornerShape(style.cornerRadius * 0.85f) }
                    val thumbShape = remember { RoundedCornerShape(12.dp) }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 90.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(grouped, key = { "artist_${it.key}" }, contentType = { "artist" }) { (artist, songs) ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        drilldownTitle = artist
                                        drilldownTracks = songs
                                    },
                                shape = cardShape,
                                color = style.cardSurface,
                                border = style.cardBorder,
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Surface(
                                        modifier = Modifier.size(42.dp),
                                        shape = thumbShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Rounded.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(artist, fontWeight = FontWeight.Bold)
                                        Text("${songs.size} ${strings.ui.tracksCountSuffix}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
                tab == 3 -> {
                    val grouped = remember(filteredTracks) {
                        filteredTracks.groupBy { it.album }.entries.sortedBy { it.key }
                    }
                    val cardShape = remember(style.cornerRadius) { RoundedCornerShape(style.cornerRadius * 0.85f) }
                    val thumbShape = remember { RoundedCornerShape(12.dp) }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 90.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(grouped, key = { "album_${it.key}" }, contentType = { "album" }) { (album, songs) ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        drilldownTitle = album
                                        drilldownTracks = songs
                                    },
                                shape = cardShape,
                                color = style.cardSurface,
                                border = style.cardBorder,
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Surface(
                                        modifier = Modifier.size(42.dp),
                                        shape = thumbShape,
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Rounded.Album, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(album, fontWeight = FontWeight.Bold)
                                        Text("${songs.size} ${strings.ui.tracksCountSuffix}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        }

        if (isSelectionMode) {
            val selectedTracks = state.tracks.filter { it.id in selectedTrackIds }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 8.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            if (selectedTracks.isEmpty()) return@TextButton
                            player.moveTracksToNimiyo(selectedTracks) { count ->
                                Toast.makeText(context, "$count ${strings.ui.trackMovedSuffix}", Toast.LENGTH_SHORT).show()
                                isSelectionMode = false
                                selectedTrackIds = emptySet()
                            }
                        },
                        enabled = selectedTracks.isNotEmpty(),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.DriveFileMove, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("AudioYo/", fontSize = 12.sp)
                    }

                    TextButton(
                        onClick = {
                            if (selectedTracks.isNotEmpty()) {
                                showBatchAddToPlaylistPicker = true
                            }
                        },
                        enabled = selectedTracks.isNotEmpty(),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(strings.playerTabPlaylists, fontSize = 12.sp)
                    }

                    TextButton(
                        onClick = {
                            if (selectedTracks.isNotEmpty()) {
                                showBatchDeleteConfirm = true
                            }
                        },
                        enabled = selectedTracks.isNotEmpty(),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("${strings.deleteBtn} (${selectedTracks.size})", fontSize = 12.sp)
                    }
                }
            }
        }
    }

    if (showPlaylistSheet) {
        val curPl = openedPlaylist?.let { open -> state.playlists.firstOrNull { it.id == open.id } ?: open }
        ModalBottomSheet(onDismissRequest = { showPlaylistSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 28.dp),
            ) {
                if (curPl == null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(strings.playerTabPlaylists, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    if (!hasAllFilesAccess()) {
                                        showSyncPermDialog = true
                                    } else {
                                        player.syncPlaylistsFromDatabase { count ->
                                            Toast.makeText(context, "$count ${strings.perm.syncDoneSuffix}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                colors = IconButtonDefaults.filledTonalIconButtonColors(),
                            ) {
                                Icon(Icons.Rounded.Sync, contentDescription = strings.syncDatabaseLabel, modifier = Modifier.size(20.dp))
                            }
                            FilledTonalButton(onClick = { showCreatePlaylistDialog = true }) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(strings.createPlaylist)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        itemsIndexed(state.playlists, key = { _, p -> p.id }) { _, pl ->
                            val isPlayingPl = state.activePlaylistId == pl.id && state.currentTrack != null
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { openedPlaylist = pl },
                                shape = RoundedCornerShape(14.dp),
                                color = if (isPlayingPl) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f) else MaterialTheme.colorScheme.surfaceVariant,
                                border = if (isPlayingPl) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        if (isPlayingPl) Icons.Rounded.GraphicEq else Icons.AutoMirrored.Rounded.QueueMusic,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Text(
                                                pl.name,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isPlayingPl) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                            )
                                            if (isPlayingPl) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.primary,
                                                ) {
                                                    Text(
                                                        text = strings.music.playingBadge,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onPrimary,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    )
                                                }
                                            }
                                        }
                                        Text("${pl.trackPaths.size} ${strings.ui.tracksCountSuffix}", style = MaterialTheme.typography.labelSmall)
                                    }
                                    IconButton(onClick = { player.deletePlaylist(pl.id) }) {
                                        Icon(Icons.Rounded.Delete, contentDescription = strings.deleteBtn)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val plTracks = remember(curPl, state.tracks) {
                        player.resolvePlaylistTracks(curPl, state.tracks)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = { openedPlaylist = null }) {
                            Text("← ${curPl.name}")
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            FilledTonalButton(onClick = { showSongPickerDialog = true }) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(strings.pickSongsLabel)
                            }
                            if (plTracks.isNotEmpty()) {
                                FilledTonalButton(onClick = {
                                    player.playFromList(plTracks, 0, curPl)
                                    showPlaylistSheet = false
                                }) {
                                    Text("${strings.ui.playTrackLabel} (${plTracks.size})")
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    plTracks.forEachIndexed { idx, track ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    player.playFromList(plTracks, idx, curPl)
                                    showPlaylistSheet = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(track.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(track.artist, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = {
                                player.removeTrackFromPlaylist(curPl.id, track.path.ifBlank { track.contentUri })
                            }) {
                                Icon(Icons.Rounded.Delete, contentDescription = strings.removeFromPlaylist)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text(strings.createPlaylist) },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    placeholder = { Text(strings.playlistNameHint) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    player.createPlaylist(newPlaylistName)
                    newPlaylistName = ""
                    showCreatePlaylistDialog = false
                }) {
                    Text(strings.saveBtn)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text(strings.cancelBtn)
                }
            },
        )
    }

    val pickerTrack = trackForPlaylistPicker
    if (pickerTrack != null) {
        AlertDialog(
            onDismissRequest = { trackForPlaylistPicker = null },
            title = { Text(strings.addToPlaylist) },
            text = {
                if (state.playlists.isEmpty()) {
                    Text(strings.noPlaylistMsg)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.playlists.forEach { pl ->
                            TextButton(
                                onClick = {
                                    player.addTrackToPlaylist(pl.id, pickerTrack)
                                    trackForPlaylistPicker = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(pl.name, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { trackForPlaylistPicker = null }) {
                    Text(strings.closeBtn)
                }
            },
        )
    }

    if (showMusicSettingsSheet) {
        MusicSettingsAndStylingSheet(
            container = container,
            strings = strings,
            settingsState = playerSettings,
            initialTab = 0,
            onDismiss = { showMusicSettingsSheet = false },
        )
    }

    if (showSyncPermDialog) {
        AlertDialog(
            onDismissRequest = { showSyncPermDialog = false },
            title = { Text(strings.perm.syncPermTitle, fontWeight = FontWeight.Bold) },
            text = { Text(strings.perm.syncPermDesc) },
            confirmButton = {
                TextButton(onClick = {
                    showSyncPermDialog = false
                    openAllFilesAccessSettings()
                }) {
                    Text(strings.perm.grantPermBtn, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSyncPermDialog = false
                    player.syncPlaylistsFromDatabase { count ->
                        Toast.makeText(context, "$count ${strings.perm.syncDoneSuffix}", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text(strings.perm.continueWithoutBtn)
                }
            },
        )
    }

    if (showBatchDeleteConfirm) {
        val selectedTracks = state.tracks.filter { it.id in selectedTrackIds }
        val needsAllFiles = !hasAllFilesAccess()
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            title = { Text("${strings.deleteBtn} (${selectedTracks.size})?", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(strings.deleteSongLabel)
                    if (needsAllFiles) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = strings.perm.deletePermWarning,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                                TextButton(
                                    onClick = { openAllFilesAccessSettings() },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                ) {
                                    Text(strings.perm.grantPermBtn, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    player.deleteTracks(selectedTracks) { deletedCount ->
                        Toast.makeText(context, "$deletedCount ${strings.deleteBtn}", Toast.LENGTH_SHORT).show()
                        showBatchDeleteConfirm = false
                        isSelectionMode = false
                        selectedTrackIds = emptySet()
                    }
                }) {
                    Text(strings.deleteBtn, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteConfirm = false }) {
                    Text(strings.cancelBtn)
                }
            },
        )
    }

    if (showBatchAddToPlaylistPicker) {
        val selectedTracks = state.tracks.filter { it.id in selectedTrackIds }
        AlertDialog(
            onDismissRequest = { showBatchAddToPlaylistPicker = false },
            title = { Text("${strings.addToPlaylist} (${selectedTracks.size})") },
            text = {
                if (state.playlists.isEmpty()) {
                    Text(strings.noPlaylistMsg)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.playlists.forEach { pl ->
                            TextButton(
                                onClick = {
                                    player.addTracksToPlaylist(pl.id, selectedTracks)
                                    Toast.makeText(context, "${selectedTracks.size} lagu ditambahkan ke ${pl.name}", Toast.LENGTH_SHORT).show()
                                    showBatchAddToPlaylistPicker = false
                                    isSelectionMode = false
                                    selectedTrackIds = emptySet()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(pl.name, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBatchAddToPlaylistPicker = false }) {
                    Text(strings.closeBtn)
                }
            },
        )
    }

    val currentActivePlaylist = openedPlaylist?.let { open -> state.playlists.firstOrNull { it.id == open.id } ?: open }
    if (showSongPickerDialog && currentActivePlaylist != null) {
        SongPickerDialog(
            strings = strings,
            playlist = currentActivePlaylist,
            allTracks = state.tracks,
            preResolvedTracks = player.resolvePlaylistTracks(currentActivePlaylist, state.tracks),
            onDismiss = { showSongPickerDialog = false },
            onAddTracks = { pickedTracks ->
                player.addTracksToPlaylist(currentActivePlaylist.id, pickedTracks)
                Toast.makeText(context, "${pickedTracks.size} lagu diperbarui di ${currentActivePlaylist.name}", Toast.LENGTH_SHORT).show()
            },
        )
    }

    val optTrack = trackForOptionsMenu
    if (optTrack != null) {
        ModalBottomSheet(onDismissRequest = { trackForOptionsMenu = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = optTrack.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = optTrack.artist,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(4.dp))

                TrackOptionRow(Icons.Rounded.PlayArrow, strings.playNextLabel) {
                    player.playNext(optTrack)
                    Toast.makeText(context, strings.playNextLabel, Toast.LENGTH_SHORT).show()
                    trackForOptionsMenu = null
                }
                TrackOptionRow(Icons.AutoMirrored.Rounded.QueueMusic, strings.addToQueueLabel) {
                    player.addToQueue(optTrack)
                    Toast.makeText(context, strings.addToQueueLabel, Toast.LENGTH_SHORT).show()
                    trackForOptionsMenu = null
                }
                TrackOptionRow(Icons.AutoMirrored.Rounded.PlaylistAdd, strings.addToPlaylist) {
                    trackForPlaylistPicker = optTrack
                    trackForOptionsMenu = null
                }
                if (!optTrack.isNimiyoFolder) {
                    TrackOptionRow(Icons.AutoMirrored.Rounded.DriveFileMove, strings.moveToNimiyoLabel) {
                        player.moveTracksToNimiyo(listOf(optTrack)) { moved ->
                            val msg = if (moved > 0) "Downloads/Nimiyo/AudioYo/" else "Error"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                        trackForOptionsMenu = null
                    }
                }
                TrackOptionRow(Icons.Rounded.Share, strings.shareBtn) {
                    runCatching {
                        val f = File(optTrack.path)
                        val uri = if (f.exists()) {
                            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
                        } else {
                            Uri.parse(optTrack.contentUri)
                        }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "audio/*"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, optTrack.title))
                    }.onFailure {
                        Toast.makeText(context, "Error", Toast.LENGTH_SHORT).show()
                    }
                    trackForOptionsMenu = null
                }
                TrackOptionRow(Icons.Rounded.Info, strings.songInfoLabel) {
                    trackForInfoDialog = optTrack
                    trackForOptionsMenu = null
                }
                TrackOptionRow(Icons.Rounded.Delete, strings.deleteSongLabel, isDestructive = true) {
                    trackForDeleteConfirm = optTrack
                    trackForOptionsMenu = null
                }
            }
        }
    }

    val infoTrack = trackForInfoDialog
    if (infoTrack != null) {
        AlertDialog(
            onDismissRequest = { trackForInfoDialog = null },
            title = { Text(strings.songInfoLabel, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${strings.titleLabel}: ${infoTrack.title}", style = MaterialTheme.typography.bodySmall)
                    Text("${strings.artistLabel}: ${infoTrack.artist}", style = MaterialTheme.typography.bodySmall)
                    Text("${strings.albumLabel}: ${infoTrack.album.ifBlank { "-" }}", style = MaterialTheme.typography.bodySmall)
                    Text("${strings.durationLabel}: ${formatDurationMs(infoTrack.durationMs)}", style = MaterialTheme.typography.bodySmall)
                    Text("${strings.sizeLabel}: ${formatFileSize(infoTrack.sizeBytes)}", style = MaterialTheme.typography.bodySmall)
                    Text("${strings.ui.locationLabel}: ${infoTrack.path.ifBlank { infoTrack.contentUri }}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = { trackForInfoDialog = null }) {
                    Text(strings.closeBtn)
                }
            },
        )
    }

    val delTrack = trackForDeleteConfirm
    if (delTrack != null) {
        val needsAllFiles = !hasAllFilesAccess()
        AlertDialog(
            onDismissRequest = { trackForDeleteConfirm = null },
            title = { Text(strings.deleteSongLabel, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${strings.ui.deleteSongConfirmPrefix}${delTrack.title}${strings.ui.deleteSongConfirmSuffix}")
                    if (needsAllFiles) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = strings.perm.deletePermWarning,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                                TextButton(
                                    onClick = { openAllFilesAccessSettings() },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                ) {
                                    Text(strings.perm.grantPermBtn, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    player.deleteTracks(listOf(delTrack)) { count ->
                        val msg = if (count > 0) strings.ui.songDeletedToast else strings.ui.fileDeleteFailedToast
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                    trackForDeleteConfirm = null
                }) {
                    Text(strings.deleteBtn, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { trackForDeleteConfirm = null }) {
                    Text(strings.cancelBtn)
                }
            },
        )
    }
}

@Composable
private fun TrackOptionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = tint,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackListSection(
    tracks: List<AudioTrack>,
    currentTrackId: String?,
    emptyText: String,
    isSelectionMode: Boolean = false,
    selectedTrackIds: Set<String> = emptySet(),
    onToggleSelect: ((AudioTrack) -> Unit)? = null,
    onTrackLongClick: ((AudioTrack) -> Unit)? = null,
    onTrackClick: (Int) -> Unit,
    onTrackOptions: (AudioTrack) -> Unit,
) {
    val style = LocalNimiyoStyle.current
    if (tracks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val cardShape = remember(style.cornerRadius) { RoundedCornerShape(style.cornerRadius * 0.85f) }
    val thumbShape = remember { RoundedCornerShape(12.dp) }
    val selectedBorder = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = if (isSelectionMode) 130.dp else 90.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(
            items = tracks,
            key = { idx, t -> "${t.id}_$idx" },
            contentType = { _, _ -> "track" },
        ) { idx, track ->
            val isCurrent = track.id == currentTrackId
            val isSelected = selectedTrackIds.contains(track.id)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {
                            if (isSelectionMode) {
                                onToggleSelect?.invoke(track)
                            } else {
                                onTrackClick(idx)
                            }
                        },
                        onLongClick = {
                            if (!isSelectionMode) {
                                onTrackLongClick?.invoke(track)
                            } else {
                                onToggleSelect?.invoke(track)
                            }
                        },
                    ),
                shape = cardShape,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                } else if (isCurrent) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    style.cardSurface
                },
                border = if (isSelected || isCurrent) selectedBorder else style.cardBorder,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isSelectionMode) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onToggleSelect?.invoke(track) },
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = thumbShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.MusicNote,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            if (track.albumArtUri.isNotBlank()) {
                                AsyncImage(
                                    model = rememberThumbRequest(track.albumArtUri, 144),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Bold,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = track.artist,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = formatDurationMs(track.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!isSelectionMode) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .clickable { onTrackOptions(track) },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SongPickerDialog(
    strings: com.nimiyo.nativeapp.core.Strings,
    playlist: PlaylistModel,
    allTracks: List<AudioTrack>,
    preResolvedTracks: List<AudioTrack>,
    onDismiss: () -> Unit,
    onAddTracks: (List<AudioTrack>) -> Unit,
) {
    var pickerSearch by remember { mutableStateOf("") }
    var pickerTab by remember { mutableIntStateOf(0) }
    var selectedIds by remember(playlist.id) {
        mutableStateOf(preResolvedTracks.map { it.id }.toSet())
    }

    val displayTracks = remember(allTracks, pickerTab, pickerSearch) {
        val base = when (pickerTab) {
            1 -> allTracks.filter { it.isNimiyoFolder }
            else -> allTracks
        }
        val q = pickerSearch.trim()
        if (q.isEmpty()) base else base.filter {
            it.title.contains(q, ignoreCase = true) ||
                it.artist.contains(q, ignoreCase = true) ||
                it.album.contains(q, ignoreCase = true)
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(strings.pickSongsLabel, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(playlist.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = strings.closeBtn)
                    }
                }

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = pickerSearch,
                    onValueChange = { pickerSearch = it },
                    placeholder = { Text(strings.searchSongsHint) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )

                Spacer(Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf("${strings.allLabel.uppercase()} (${allTracks.size})", "NIMIYO (${allTracks.count { it.isNimiyoFolder }})").forEachIndexed { idx, title ->
                        val active = pickerTab == idx
                        FilterChip(
                            selected = active,
                            onClick = { pickerTab = idx },
                            label = { Text(title) },
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    val allCurrentIds = displayTracks.map { it.id }.toSet()
                    val allSelected = allCurrentIds.isNotEmpty() && selectedIds.containsAll(allCurrentIds)
                    TextButton(onClick = {
                        selectedIds = if (allSelected) {
                            selectedIds - allCurrentIds
                        } else {
                            selectedIds + allCurrentIds
                        }
                    }) {
                        Text(if (allSelected) strings.deselectAllLabel else strings.selectAllLabel, fontSize = 12.sp)
                    }
                }

                Spacer(Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(displayTracks, key = { it.id }) { track ->
                        val isChecked = selectedIds.contains(track.id)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    selectedIds = if (isChecked) selectedIds - track.id else selectedIds + track.id
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        selectedIds = if (checked == true) selectedIds + track.id else selectedIds - track.id
                                    },
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(track.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(track.artist, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(formatDurationMs(track.durationMs), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                val newlySelectedTracks = allTracks.filter { it.id in selectedIds }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text(strings.cancelBtn)
                    }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = {
                            onAddTracks(newlySelectedTracks)
                            onDismiss()
                        },
                    ) {
                        Text("${strings.saveBtn} (${newlySelectedTracks.size})")
                    }
                }
            }
        }
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
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun <T> MusicSettingDropdownRow(
    label: String,
    selectedLabel: String,
    options: List<Pair<T, String>>,
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
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
        )
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = selectedLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { (value, optLabel) ->
                    DropdownMenuItem(
                        text = { Text(optLabel) },
                        onClick = {
                            expanded = false
                            onSelect(value)
                        },
                    )
                }
            }
        }
    }
}
