package com.nimiyo.nativeapp

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nimiyo.nativeapp.core.BatchPhotoMode
import com.nimiyo.nativeapp.core.QuickSaveWindowMode
import com.nimiyo.nativeapp.core.StringsI18n
import com.nimiyo.nativeapp.data.DownloadStatus
import com.nimiyo.nativeapp.data.MediaCategory
import com.nimiyo.nativeapp.data.PlatformId
import com.nimiyo.nativeapp.data.ScrapeResult
import com.nimiyo.nativeapp.scraper.PlatformDetector
import com.nimiyo.nativeapp.ui.theme.NimiyoTheme

class ShareQuickSaveActivity : ComponentActivity() {
    private var sharedUrlState by mutableStateOf("")

    private fun extractSharedUrl(intent: Intent?): String {
        val type = intent?.type.orEmpty()
        val sharedText = if (intent?.action == Intent.ACTION_SEND && type.startsWith("text/plain", ignoreCase = true)) {
            intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        } else ""
        return PlatformDetector.extractFirstUrl(sharedText) ?: ""
    }

    private fun handleQuickModeOrSetUrl(container: NimiyoAppContainer, url: String): Boolean {
        if (url.isBlank()) return false
        val initialSettings = container.settings.state.value
        if (initialSettings.quickSaveDirectAfterShare) {
            val strings = StringsI18n.get(initialSettings.language)
            runCatching {
                container.downloadManager.enqueueQuickUrl(
                    rawUrl = url,
                    settingsSnapshot = initialSettings.asQuickSaveFormatSettings(),
                    fromQuickSave = true,
                )
            }.onSuccess {
                Toast.makeText(
                    applicationContext,
                    "${strings.queuedToast} (${strings.quickSaveMinimized})",
                    Toast.LENGTH_SHORT,
                ).show()
            }.onFailure { err ->
                Toast.makeText(
                    applicationContext,
                    err.message ?: strings.downloadErrorTitle,
                    Toast.LENGTH_LONG,
                ).show()
            }
            finishAndRemoveTask()
            return true
        }
        sharedUrlState = url
        return false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val newUrl = extractSharedUrl(intent)
        if (newUrl.isNotBlank()) {
            handleQuickModeOrSetUrl(NimiyoAppContainer.get(this), newUrl)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = NimiyoAppContainer.get(this)
        val extractedUrl = extractSharedUrl(intent)

        if (extractedUrl.isBlank()) {
            val fallbackStrings = StringsI18n.get(container.settings.state.value.language)
            Toast.makeText(applicationContext, fallbackStrings.downloadErrorTitle, Toast.LENGTH_SHORT).show()
            finishAndRemoveTask()
            return
        }

        if (handleQuickModeOrSetUrl(container, extractedUrl)) {
            return
        }

        setContent {
            val settings by container.settings.state.collectAsState()
            val strings = StringsI18n.get(settings.language)
            val allTasks by container.downloadManager.tasks.collectAsState()
            val qsTasks = allTasks.filter { it.fromQuickSave }
            val activeUrl = sharedUrlState.ifBlank { extractedUrl }

            NimiyoTheme(
                uiStyle = settings.uiStyle,
                darkModePref = settings.darkMode,
                accentColor = settings.accentColor,
            ) {
                QuickSaveOverlay(
                    url = activeUrl,
                    container = container,
                    strings = strings,
                    qsTasks = qsTasks,
                    onMinimize = { finishAndRemoveTask() },
                    onOpenSettings = {
                        container.scraper.cancelAnalyze("qs_${activeUrl.hashCode()}")
                        val intent = Intent(this@ShareQuickSaveActivity, MainActivity::class.java).apply {
                            putExtra("open_quicksave_settings", true)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                        startActivity(intent)
                        finishAndRemoveTask()
                    },
                )
            }
        }
    }
}

@Composable
private fun QuickSaveOverlay(
    url: String,
    container: NimiyoAppContainer,
    strings: com.nimiyo.nativeapp.core.Strings,
    qsTasks: List<com.nimiyo.nativeapp.data.DownloadTask>,
    onMinimize: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val analyzeProcId = remember(url) { "qs_${url.hashCode()}" }
    var keptRunning by remember(url) { mutableStateOf(false) }

    fun minimizeSafely() {
        if (!keptRunning) {
            container.scraper.cancelAnalyze(analyzeProcId)
        }
        onMinimize()
    }

    BackHandler { minimizeSafely() }

    val settings by container.settings.state.collectAsState()
    var windowModeOverride by remember(url) { mutableStateOf<QuickSaveWindowMode?>(null) }
    val activeWindowMode = windowModeOverride ?: settings.quickSaveWindowMode
    val isDirectWindow = activeWindowMode == QuickSaveWindowMode.DIRECT
    var loading by remember(url, isDirectWindow) { mutableStateOf(!isDirectWindow) }
    var result by remember(url) { mutableStateOf<ScrapeResult?>(null) }
    var error by remember(url) { mutableStateOf<String?>(null) }
    var autoDownloaded by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url, isDirectWindow) {
        if (isDirectWindow) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        error = null
        result = null
        autoDownloaded = false
        val res = runCatching {
            container.scraper.analyze(url, null, null, analyzeProcId)
        }
        loading = false
        res.onSuccess {
            result = it
            if (settings.autoDownloadOnAnalyze && !autoDownloaded && it.items.isNotEmpty()) {
                autoDownloaded = true
                keptRunning = true
                val photoItems = it.items.filter { item -> item.category == MediaCategory.IMAGE }
                val toQueue = if (photoItems.size > 1 && photoItems.size == it.items.size) {
                    if (settings.batchPhotoMode == BatchPhotoMode.FIRST) listOf(photoItems.first()) else photoItems
                } else {
                    listOf(it.items.first())
                }
                container.downloadManager.enqueue(
                    items = toQueue,
                    platform = it.platform,
                    sourceUrl = it.sourceUrl,
                    fromQuickSave = true,
                )
                if (settings.quickSaveAutoCloseOnStart) {
                    Toast.makeText(container.appContext, strings.quickSaveMinimized, Toast.LENGTH_SHORT).show()
                    onMinimize()
                }
            }
        }.onFailure { err ->
            if (err !is kotlinx.coroutines.CancellationException) {
                error = err.message ?: "Analysis failed"
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { minimizeSafely() },
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "QuickSave",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilledTonalButton(
                            onClick = {
                                val nextMode = if (isDirectWindow) QuickSaveWindowMode.ANALYZE else QuickSaveWindowMode.DIRECT
                                if (nextMode == QuickSaveWindowMode.DIRECT) {
                                    container.scraper.cancelAnalyze(analyzeProcId)
                                }
                                windowModeOverride = nextMode
                            },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Icon(
                                imageVector = if (isDirectWindow) Icons.Rounded.Search else Icons.Rounded.Bolt,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (isDirectWindow) strings.ytdlpExtra.qsWindowModeAnalyze else strings.ytdlpExtra.qsWindowModeDirect,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        FilledTonalButton(
                            onClick = onOpenSettings,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = strings.navSettings,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                if (isDirectWindow) {
                    Text(
                        text = url,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    com.nimiyo.nativeapp.ui.components.QuickFormatSelectorContent(
                        mediaType = settings.quickMediaType,
                        videoCodec = settings.quickVideoCodec,
                        videoResolution = settings.quickVideoResolution,
                        videoAudioFormat = settings.quickVideoAudioFormat,
                        audioQuality = settings.quickAudioQuality,
                        photoMode = settings.quickPhotoMode,
                        strings = strings,
                        onMediaTypeChange = { v -> container.settings.update { it.copy(quickMediaType = v) } },
                        onVideoCodecChange = { v -> container.settings.update { it.copy(quickVideoCodec = v) } },
                        onVideoResolutionChange = { v -> container.settings.update { it.copy(quickVideoResolution = v) } },
                        onVideoAudioFormatChange = { v -> container.settings.update { it.copy(quickVideoAudioFormat = v) } },
                        onAudioQualityChange = { v -> container.settings.update { it.copy(quickAudioQuality = v) } },
                        onPhotoModeChange = { v -> container.settings.update { it.copy(quickPhotoMode = v) } },
                    )
                    Button(
                        onClick = {
                            keptRunning = true
                            runCatching {
                                container.downloadManager.enqueueQuickUrl(
                                    rawUrl = url,
                                    settingsSnapshot = settings,
                                    fromQuickSave = true,
                                )
                            }.onSuccess {
                                Toast.makeText(container.appContext, strings.queuedToast, Toast.LENGTH_SHORT).show()
                                if (settings.quickSaveAutoCloseOnStart) {
                                    onMinimize()
                                }
                            }.onFailure { err ->
                                Toast.makeText(
                                    container.appContext,
                                    err.message ?: strings.downloadErrorTitle,
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(strings.quick.startDownloadBtn, fontWeight = FontWeight.ExtraBold)
                    }
                } else if (loading) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(strings.analyzing)
                    }
                } else if (!error.isNullOrBlank()) {
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else if (result != null) {
                    val res = result!!
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (res.thumbnail.isNotBlank()) {
                            AsyncImage(
                                model = res.thumbnail,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(58.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                            )
                            Spacer(Modifier.width(10.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${res.platform.displayName} • ${res.serverUsed}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = res.title,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }

                    val batchItems = remember(res, settings.batchPhotoMode) {
                        val photos = res.items.filter { it.category == MediaCategory.IMAGE }
                        if (photos.size > 1 && photos.size == res.items.size && settings.batchPhotoMode == BatchPhotoMode.FIRST) {
                            listOf(photos.first())
                        } else {
                            res.items
                        }
                    }

                    if (res.items.size > 1) {
                        FilledTonalButton(
                            onClick = {
                                keptRunning = true
                                container.downloadManager.enqueue(
                                    items = batchItems,
                                    platform = res.platform,
                                    sourceUrl = res.sourceUrl,
                                    fromQuickSave = true,
                                )
                                Toast.makeText(container.appContext, strings.queuedToast, Toast.LENGTH_SHORT).show()
                                if (settings.quickSaveAutoCloseOnStart) {
                                    onMinimize()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.DownloadForOffline, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("${strings.downloadAll} (${batchItems.size})")
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(res.items, key = { it.id }) { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "${item.label} (${item.extension.uppercase()})",
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Button(
                                    onClick = {
                                        keptRunning = true
                                        container.downloadManager.enqueue(
                                            items = listOf(item),
                                            platform = res.platform,
                                            sourceUrl = res.sourceUrl,
                                            fromQuickSave = true,
                                        )
                                        Toast.makeText(container.appContext, strings.queuedToast, Toast.LENGTH_SHORT).show()
                                        if (settings.quickSaveAutoCloseOnStart) {
                                            onMinimize()
                                        }
                                    },
                                ) {
                                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(strings.downloadSingle)
                                }
                            }
                        }
                    }
                }

                val activeQs = qsTasks.filter {
                    it.status == DownloadStatus.QUEUED ||
                        it.status == DownloadStatus.RESOLVING ||
                        it.status == DownloadStatus.DOWNLOADING
                }
                if (activeQs.isNotEmpty()) {
                    val avg = activeQs.map { it.progress }.average().toInt()
                    Text("${activeQs.size} active • $avg%", style = MaterialTheme.typography.labelSmall)
                    LinearProgressIndicator(
                        progress = { avg / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

