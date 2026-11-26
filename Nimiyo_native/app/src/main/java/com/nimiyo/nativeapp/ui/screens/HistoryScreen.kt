package com.nimiyo.nativeapp.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.nimiyo.nativeapp.ImageViewerActivity
import com.nimiyo.nativeapp.NimiyoAppContainer
import com.nimiyo.nativeapp.VideoPlayerActivity
import com.nimiyo.nativeapp.core.Strings
import com.nimiyo.nativeapp.data.AudioTrack
import com.nimiyo.nativeapp.data.HistoryRecord
import com.nimiyo.nativeapp.data.MediaCategory
import com.nimiyo.nativeapp.ui.components.NimiyoStyledCard
import com.nimiyo.nativeapp.ui.components.formatFileSize
import com.nimiyo.nativeapp.ui.components.rememberThumbRequest
import java.io.File

@Composable
fun HistoryScreen(
    container: NimiyoAppContainer,
    strings: Strings,
    isActive: Boolean = true,
    onNavigateToPlayer: () -> Unit = {},
) {
    val context = LocalContext.current
    val records by container.downloadManager.history.collectAsState()
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<MediaCategory?>(null) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = isActive && (query.isNotEmpty() || selectedCategory != null || showClearConfirmDialog)) {
        when {
            showClearConfirmDialog -> showClearConfirmDialog = false
            query.isNotEmpty() -> query = ""
            selectedCategory != null -> selectedCategory = null
        }
    }

    val filtered = remember(records, query, selectedCategory) {
        val q = query.trim()
        val byCat = if (selectedCategory == null) records else records.filter { it.category == selectedCategory }
        if (q.isEmpty()) byCat else byCat.filter {
            it.title.contains(q, ignoreCase = true) ||
                it.platform.contains(q, ignoreCase = true) ||
                it.filename.contains(q, ignoreCase = true)
        }
    }

    fun openMedia(rec: HistoryRecord) {
        when (rec.category) {
            MediaCategory.VIDEO -> {
                val videoList = filtered.filter { it.category == MediaCategory.VIDEO }
                    .ifEmpty { listOf(rec) }
                val startIdx = videoList.indexOfFirst { it.id == rec.id }.coerceAtLeast(0)
                context.startActivity(
                    Intent(context, VideoPlayerActivity::class.java).apply {
                        putExtra("path", rec.localPath)
                        putExtra("uri", rec.contentUri)
                        putExtra("title", rec.title)
                        putStringArrayListExtra("paths", ArrayList(videoList.map { it.localPath }))
                        putStringArrayListExtra("uris", ArrayList(videoList.map { it.contentUri }))
                        putStringArrayListExtra("titles", ArrayList(videoList.map { it.title }))
                        putExtra("startIndex", startIdx)
                    }
                )
            }
            MediaCategory.IMAGE -> {
                context.startActivity(
                    Intent(context, ImageViewerActivity::class.java).apply {
                        putExtra("path", rec.localPath)
                        putExtra("uri", rec.contentUri)
                        putExtra("title", rec.title)
                    }
                )
            }
            MediaCategory.AUDIO -> {
                val playerTracks = container.playerController.state.value.tracks
                val matchedIdx = playerTracks.indexOfFirst { t ->
                    (rec.localPath.isNotBlank() && t.path == rec.localPath) ||
                        (rec.contentUri.isNotBlank() && t.contentUri == rec.contentUri)
                }
                if (matchedIdx >= 0) {
                    container.playerController.playFromList(playerTracks, matchedIdx)
                } else {
                    val track = AudioTrack(
                        id = "hist_${rec.id}",
                        title = rec.title,
                        artist = rec.platform,
                        album = strings.navPlayer,
                        path = rec.localPath,
                        contentUri = rec.contentUri,
                        durationMs = 0L,
                        sizeBytes = rec.sizeBytes,
                        dateAddedSec = rec.createdAt / 1000L,
                        isNimiyoFolder = true,
                        albumArtUri = com.nimiyo.nativeapp.player.AudioArtworkFetcher.buildUri(
                            rec.localPath,
                            rec.contentUri,
                        ),
                    )
                    container.playerController.playFromList(listOf(track), 0)
                }
                container.playerController.setNowPlayingOpen(true)
                onNavigateToPlayer()
            }
        }
    }

    fun shareRecord(rec: HistoryRecord) {
        runCatching {
            val f = File(rec.localPath)
            val uri = if (f.exists()) {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
            } else if (rec.contentUri.isNotBlank()) {
                Uri.parse(rec.contentUri)
            } else {
                Toast.makeText(context, strings.ui.fileNotFoundToast, Toast.LENGTH_SHORT).show()
                return
            }
            val mime = when (rec.category) {
                MediaCategory.VIDEO -> "video/*"
                MediaCategory.AUDIO -> "audio/*"
                MediaCategory.IMAGE -> "image/*"
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, rec.title))
        }
    }

    fun openNimiyoFolder() {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(
                    Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload%2FNimiyo"),
                    "vnd.android.document/directory"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure {
            Toast.makeText(context, strings.ui.savedFolderToast, Toast.LENGTH_LONG).show()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = strings.historyTitle.uppercase(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (records.isNotEmpty()) {
                    IconButton(
                        onClick = { showClearConfirmDialog = true },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = strings.ui.clearHistoryCd, modifier = Modifier.size(20.dp))
                    }
                }
                FilledTonalButton(onClick = { openNimiyoFolder() }) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.openFolder, maxLines = 1, softWrap = false)
                }
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            placeholder = { Text(strings.searchHistoryHint) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
        )

        Spacer(Modifier.height(8.dp))

        // Category Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val chips = listOf(
                null to strings.allLabel,
                MediaCategory.VIDEO to strings.videoLabel,
                MediaCategory.AUDIO to strings.audioLabel,
                MediaCategory.IMAGE to strings.imageLabel,
            )
            chips.forEach { (cat, label) ->
                val selected = selectedCategory == cat
                FilterChip(
                    selected = selected,
                    onClick = { selectedCategory = if (selected) null else cat },
                    label = { Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(strings.historyEmpty, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            val thumbShape = remember { RoundedCornerShape(12.dp) }
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            LaunchedEffect(isActive) {
                if (isActive && filtered.isNotEmpty()) {
                    listState.scrollToItem(0)
                }
            }
            LaunchedEffect(filtered.firstOrNull()?.id) {
                if (filtered.isNotEmpty()) {
                    listState.animateScrollToItem(0)
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(filtered, key = { it.id }, contentType = { "history" }) { rec ->
                    NimiyoStyledCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { openMedia(rec) },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val icon = when (rec.category) {
                                MediaCategory.VIDEO -> Icons.Rounded.Videocam
                                MediaCategory.AUDIO -> Icons.Rounded.MusicNote
                                MediaCategory.IMAGE -> Icons.Rounded.Image
                            }
                            Surface(
                                modifier = Modifier.size(56.dp),
                                shape = thumbShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(26.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    if (rec.thumbnail.isNotBlank()) {
                                        AsyncImage(
                                            model = rememberThumbRequest(rec.thumbnail, 168),
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
                                    text = rec.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                val catLabel = when (rec.category) {
                                    MediaCategory.VIDEO -> strings.videoLabel
                                    MediaCategory.AUDIO -> strings.audioLabel
                                    MediaCategory.IMAGE -> strings.imageLabel
                                }
                                Text(
                                    text = "${rec.platform} • $catLabel • ${formatFileSize(rec.sizeBytes)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { shareRecord(rec) }) {
                                Icon(Icons.Rounded.Share, contentDescription = strings.shareBtn)
                            }
                            IconButton(onClick = { container.downloadManager.deleteHistoryItem(rec.id) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = strings.deleteBtn)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text(strings.clearHistoryTitle, fontWeight = FontWeight.Bold) },
            text = { Text(strings.clearHistoryConfirm) },
            confirmButton = {
                TextButton(onClick = {
                    container.downloadManager.clearAllHistory()
                    showClearConfirmDialog = false
                    Toast.makeText(context, strings.clearHistoryTitle, Toast.LENGTH_SHORT).show()
                }) {
                    Text(strings.deleteBtn, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text(strings.cancelBtn)
                }
            },
        )
    }
}

