package com.nimiyo.nativeapp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nimiyo.nativeapp.core.Strings
import com.nimiyo.nativeapp.data.DownloadStatus
import com.nimiyo.nativeapp.data.DownloadTask

@Composable
fun FloatingDownloadBalloon(
    tasks: List<DownloadTask>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeOrFailed = tasks.filter {
        it.status == DownloadStatus.QUEUED ||
            it.status == DownloadStatus.RESOLVING ||
            it.status == DownloadStatus.DOWNLOADING ||
            it.status == DownloadStatus.FAILED
    }
    val active = activeOrFailed.filter { it.status != DownloadStatus.FAILED }
    AnimatedVisibility(visible = activeOrFailed.isNotEmpty(), modifier = modifier) {
        Surface(
            modifier = Modifier
                .shadow(10.dp, CircleShape)
                .clip(CircleShape)
                .clickable(onClick = onClick),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (active.isNotEmpty()) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.4.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    val avg = active.map { it.progress }.average().toInt()
                    Text(
                        text = "${active.size} • $avg%",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                } else {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "${activeOrFailed.size}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadQueueSheet(
    tasks: List<DownloadTask>,
    strings: Strings,
    onDismiss: () -> Unit,
    onRetry: (String) -> Unit,
    onRetryFallback: (String) -> Unit = onRetry,
    onCancel: (String) -> Unit,
    onCancelAll: () -> Unit,
    onClearCompleted: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = strings.activeDownloadsTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onClearCompleted) {
                    Icon(Icons.Rounded.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(strings.clearCompleted, maxLines = 1, softWrap = false)
                }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onCancelAll) {
                    Text(strings.cancelAll, color = MaterialTheme.colorScheme.error, maxLines = 1, softWrap = false)
                }
            }
            Spacer(Modifier.height(6.dp))

            if (tasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(strings.noActiveDownloads, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(tasks, key = { it.id }) { task ->
                        NimiyoStyledCard(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = task.title,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        val statusLabel = when (task.status) {
                                            DownloadStatus.QUEUED -> strings.ui.statusQueued
                                            DownloadStatus.RESOLVING -> strings.ui.statusResolving
                                            DownloadStatus.DOWNLOADING -> strings.ui.statusDownloading
                                            DownloadStatus.COMPLETED -> strings.ui.statusCompleted
                                            DownloadStatus.FAILED -> strings.ui.statusFailed
                                            DownloadStatus.CANCELLED -> strings.ui.statusCancelled
                                        }
                                        Text(
                                            text = "${task.platform.displayName} • $statusLabel ${if (task.totalBytes > 0) "• ${formatFileSize(task.bytesDownloaded)}/${formatFileSize(task.totalBytes)}" else ""}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (task.status == DownloadStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        if (task.errorMessage.isNotBlank()) {
                                            Text(
                                                text = task.errorMessage,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.error,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                    if (task.status == DownloadStatus.FAILED) {
                                        if (task.sourceUrl.isNotBlank() && task.platform.servers.size > 1) {
                                            IconButton(onClick = { onRetryFallback(task.id) }) {
                                                Icon(Icons.Rounded.SwapHoriz, contentDescription = strings.retryFallbackBtn)
                                            }
                                        }
                                        IconButton(onClick = { onRetry(task.id) }) {
                                            Icon(Icons.Rounded.Refresh, contentDescription = strings.retryBtn)
                                        }
                                    } else if (task.status == DownloadStatus.QUEUED ||
                                        task.status == DownloadStatus.RESOLVING ||
                                        task.status == DownloadStatus.DOWNLOADING
                                    ) {
                                        IconButton(onClick = { onCancel(task.id) }) {
                                            Icon(Icons.Rounded.Close, contentDescription = strings.cancelBtn)
                                        }
                                    }
                                }
                                if (task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.RESOLVING) {
                                    Spacer(Modifier.height(6.dp))
                                    if (task.progress > 0) {
                                        LinearProgressIndicator(
                                            progress = { task.progress / 100f },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(3.dp)),
                                        )
                                    } else {
                                        LinearProgressIndicator(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(3.dp)),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

