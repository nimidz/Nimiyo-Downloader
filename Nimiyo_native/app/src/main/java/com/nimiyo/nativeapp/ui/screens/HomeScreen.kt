package com.nimiyo.nativeapp.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nimiyo.nativeapp.NimiyoAppContainer
import com.nimiyo.nativeapp.core.AudioQualityPref
import com.nimiyo.nativeapp.core.BatchPhotoMode
import com.nimiyo.nativeapp.core.QuickMediaType
import com.nimiyo.nativeapp.core.QuickVideoAudioFormat
import com.nimiyo.nativeapp.core.QuickVideoCodec
import com.nimiyo.nativeapp.core.QuickVideoResolution
import com.nimiyo.nativeapp.core.SettingsState
import com.nimiyo.nativeapp.core.Strings
import com.nimiyo.nativeapp.data.MediaCategory
import com.nimiyo.nativeapp.data.PlatformId
import com.nimiyo.nativeapp.data.ScrapeResult
import com.nimiyo.nativeapp.data.ScraperDownloadItem
import com.nimiyo.nativeapp.scraper.NimiyoContentGuard
import com.nimiyo.nativeapp.scraper.PlatformDetector
import com.nimiyo.nativeapp.ui.components.NimiyoStyledCard
import com.nimiyo.nativeapp.ui.components.rememberThumbRequest
import com.nimiyo.nativeapp.ui.theme.LocalNimiyoStyle
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    container: NimiyoAppContainer,
    strings: Strings,
    isActive: Boolean = true,
    urlInput: String,
    onUrlChange: (String) -> Unit,
    scrapeResult: ScrapeResult?,
    onScrapeResultChange: (ScrapeResult?) -> Unit,
    onOpenQueueSheet: () -> Unit,
    onOpenCookieSettings: () -> Unit = {},
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val settingsState by container.settings.state.collectAsState()
    val cookieEntries by container.cookieVault.entries.collectAsState()
    val style = LocalNimiyoStyle.current

    var selectedPlatformOverride by remember { mutableStateOf<PlatformId?>(null) }
    var selectedServer by remember { mutableStateOf<String?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var analyzeJob by remember { mutableStateOf<Job?>(null) }
    var activeAnalyzeId by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var platformSearchQuery by remember { mutableStateOf("") }

    val allCompliantPlatforms = remember(context) {
        runCatching {
            context.assets.open("supported_platforms.txt").bufferedReader().useLines { lines ->
                lines.map { it.trim() }.filter { it.isNotEmpty() }.toList()
            }
        }.getOrElse {
            PlatformId.entries.filter { it != PlatformId.OTHER }.map { it.displayName }
        }
    }

    val detectedPlatform = selectedPlatformOverride ?: PlatformDetector.detect(urlInput)

    val effectiveServer = selectedServer ?: settingsState.defaultServerFor(detectedPlatform)

    fun triggerAnalyze(
        overrideSrv: String? = effectiveServer,
        targetUrl: String = urlInput,
    ) {
        val target = targetUrl.trim()
        if (target.isBlank()) return
        analyzeJob?.cancel()
        if (activeAnalyzeId.isNotBlank()) {
            container.scraper.cancelAnalyze(activeAnalyzeId)
        }
        val procId = "analyze_${System.currentTimeMillis()}"
        activeAnalyzeId = procId
        isAnalyzing = true
        errorMsg = null
        analyzeJob = scope.launch {
            val res = runCatching {
                container.scraper.analyze(target, selectedPlatformOverride, overrideSrv, procId)
            }
            isAnalyzing = false
            res.onSuccess {
                onScrapeResultChange(it)
                if (settingsState.autoDownloadOnAnalyze && it.items.isNotEmpty()) {
                    val photos = it.items.filter { item -> item.category == MediaCategory.IMAGE }
                    val autoItems = if (photos.size > 1 && photos.size == it.items.size) {
                        if (settingsState.batchPhotoMode == BatchPhotoMode.FIRST) listOf(photos.first()) else photos
                    } else {
                        listOf(it.items.first())
                    }
                    container.downloadManager.enqueue(
                        items = autoItems,
                        platform = it.platform,
                        sourceUrl = it.sourceUrl,
                        serverId = "ytdlp",
                    )
                    if (settingsState.autoClearInput) {
                        onUrlChange("")
                    }
                }
            }.onFailure { err ->
                if (err !is kotlinx.coroutines.CancellationException) {
                    onScrapeResultChange(null)
                    errorMsg = err.message ?: strings.ui.analyzeFailedMsg
                }
            }
        }
    }

    fun triggerQuickStartDownload() {
        val target = urlInput.trim()
        if (target.isBlank()) return
        analyzeJob?.cancel()
        if (activeAnalyzeId.isNotBlank()) {
            container.scraper.cancelAnalyze(activeAnalyzeId)
            activeAnalyzeId = ""
        }
        isAnalyzing = false
        errorMsg = null
        onScrapeResultChange(null)
        runCatching {
            container.downloadManager.enqueueQuickUrl(
                rawUrl = target,
                settingsSnapshot = settingsState,
                overridePlatform = selectedPlatformOverride,
            )
        }.onSuccess {
            Toast.makeText(context, strings.queuedToast, Toast.LENGTH_SHORT).show()
            if (settingsState.autoClearInput) {
                onUrlChange("")
            }
        }.onFailure { err ->
            errorMsg = err.message ?: strings.ui.queueFailedMsg
        }
    }

    val detectedClip by container.detectedClipboardUrl.collectAsState()
    LaunchedEffect(detectedClip) {
        val url = detectedClip
        if (!url.isNullOrBlank() && settingsState.clipboardAutoDetect) {
            onUrlChange(url)
            selectedPlatformOverride = null
            selectedServer = null
            container.consumeClipboardCandidate()
            if (settingsState.autoAnalyzeOnPaste) {
                triggerAnalyze(targetUrl = url)
            }
        }
    }

    BackHandler(enabled = isActive && (isAnalyzing || scrapeResult != null || errorMsg != null)) {
        when {
            isAnalyzing -> {
                if (activeAnalyzeId.isNotBlank()) container.scraper.cancelAnalyze(activeAnalyzeId)
                analyzeJob?.cancel()
                isAnalyzing = false
            }
            scrapeResult != null -> {
                onScrapeResultChange(null)
            }
            errorMsg != null -> {
                errorMsg = null
            }
        }
    }

    val activeResult = scrapeResult
    val isAudioOnlyPlaylist = activeResult != null && activeResult.isPlaylist && (
        activeResult.platform == PlatformId.SPOTIFY ||
            activeResult.platform == PlatformId.APPLE_MUSIC ||
            activeResult.platform == PlatformId.SOUNDCLOUD ||
            activeResult.platform == PlatformId.BANDCAMP ||
            activeResult.extractorName.contains("soundcloud", ignoreCase = true) ||
            activeResult.extractorName.contains("bandcamp", ignoreCase = true)
        )
    var selectedPlaylistAudioPref by remember(
        activeResult?.sourceUrl,
        activeResult?.items?.size,
        isAudioOnlyPlaylist,
        settingsState.defaultAudioQuality,
    ) {
        mutableStateOf<AudioQualityPref?>(
            if (isAudioOnlyPlaylist) settingsState.defaultAudioQuality else null
        )
    }
    val displayItems = remember(activeResult, selectedPlaylistAudioPref) {
        val res = activeResult ?: return@remember emptyList()
        val pref = selectedPlaylistAudioPref
        if (!res.isPlaylist || pref == null) {
            res.items
        } else {
            res.items.mapIndexed { idx, item ->
                item.copy(
                    category = MediaCategory.AUDIO,
                    extension = pref.extension,
                    label = "${idx + 1}. ${item.title} (${pref.label})",
                    ytdlpFormatSelector = if (pref == AudioQualityPref.M4A_AAC) "ba[ext=m4a]/ba/best" else "ba/best",
                    ytdlpAudioFormat = pref.ytdlpFormat,
                    ytdlpAudioQuality = pref.audioQualityArg,
                    ytdlpContainer = pref.extension,
                )
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Nimiyo Centered Brand Header + Cookie & Queue shortcuts
        item {
            val hasActiveCookieHeader = settingsState.cookiesEnabled && cookieEntries.any { it.isLoggedIn }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            ) {
                Surface(
                    modifier = Modifier
                        .size(40.dp)
                        .align(Alignment.CenterStart)
                        .clip(CircleShape)
                        .clickable(onClick = onOpenCookieSettings),
                    shape = CircleShape,
                    color = if (hasActiveCookieHeader) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    border = style.cardBorder,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Cookie,
                            contentDescription = strings.cookie.menuTitle,
                            tint = if (hasActiveCookieHeader) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Text(
                    text = "NIMIYO",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.align(Alignment.Center),
                )
                Surface(
                    modifier = Modifier
                        .size(40.dp)
                        .align(Alignment.CenterEnd)
                        .clip(CircleShape)
                        .clickable(onClick = onOpenQueueSheet),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    border = style.cardBorder,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.DownloadForOffline,
                            contentDescription = strings.activeDownloadsTitle,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }

        // Sleek Nimiyo Input Card + Inline Paste/Clear + Full-Width Primary Button
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = {
                        onUrlChange(it)
                        selectedPlatformOverride = null
                        selectedServer = null
                        errorMsg = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            strings.pasteLinkHint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(style.cornerRadius),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = style.cardSurface,
                        unfocusedContainerColor = style.cardSurface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (urlInput.isNotEmpty()) {
                                IconButton(onClick = {
                                    onUrlChange("")
                                    onScrapeResultChange(null)
                                    errorMsg = null
                                }) {
                                    Icon(Icons.Rounded.Clear, contentDescription = null)
                                }
                            }
                            IconButton(onClick = {
                                val clip = clipboard.getText()?.text.orEmpty()
                                val extracted = PlatformDetector.extractFirstUrl(clip) ?: clip
                                if (extracted.isNotBlank()) {
                                    onUrlChange(extracted)
                                    selectedPlatformOverride = null
                                    selectedServer = null
                                    if (settingsState.autoAnalyzeOnPaste) {
                                        triggerAnalyze(targetUrl = extracted)
                                    }
                                }
                            }) {
                                Icon(Icons.Rounded.ContentPaste, contentDescription = strings.pasteBtn)
                            }
                        }
                    },
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = { triggerAnalyze() },
                        enabled = urlInput.isNotBlank() && !isAnalyzing,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(style.cornerRadius),
                    ) {
                        if (isAnalyzing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(strings.analyzing.uppercase(), fontWeight = FontWeight.Bold)
                        } else {
                            Text(strings.analyzeBtn.uppercase(), fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp)
                        }
                    }

                    if (isAnalyzing) {
                        OutlinedButton(
                            onClick = {
                                if (activeAnalyzeId.isNotBlank()) container.scraper.cancelAnalyze(activeAnalyzeId)
                                analyzeJob?.cancel()
                                isAnalyzing = false
                            },
                            modifier = Modifier.height(52.dp),
                            shape = RoundedCornerShape(style.cornerRadius),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        ) {
                            Text(strings.cancelBtn.uppercase(), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        val quickOn = settingsState.quickModeEnabled
                        FilledTonalButton(
                            onClick = {
                                container.settings.update { it.copy(quickModeEnabled = !it.quickModeEnabled) }
                            },
                            modifier = Modifier.height(52.dp),
                            shape = RoundedCornerShape(style.cornerRadius),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (quickOn) MaterialTheme.colorScheme.primaryContainer else style.cardSurface,
                                contentColor = if (quickOn) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            ),
                            border = if (quickOn) {
                                BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                            } else {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            },
                            contentPadding = PaddingValues(horizontal = 14.dp),
                        ) {
                            Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(strings.quick.quickBtn.uppercase(), fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp)
                        }
                    }
                }

                AnimatedVisibility(visible = settingsState.quickModeEnabled) {
                    QuickPreferencesCard(
                        settings = settingsState,
                        strings = strings,
                        canStartDownload = urlInput.isNotBlank() && !isAnalyzing,
                        onUpdate = { transform -> container.settings.update(transform) },
                        onStartDownload = { triggerQuickStartDownload() },
                    )
                }
            }
        }

        if (!errorMsg.isNullOrBlank()) {
            item {
                NimiyoStyledCard(containerColor = MaterialTheme.colorScheme.errorContainer) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = errorMsg!!,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = { errorMsg = null }) {
                                Text(strings.ui.dismissBtn, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                            if (urlInput.isNotBlank()) {
                                Spacer(Modifier.width(8.dp))
                                TextButton(onClick = {
                                    if (settingsState.quickModeEnabled) triggerQuickStartDownload() else triggerAnalyze()
                                }) {
                                    Text(strings.retryBtn, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                                }
                            }
                        }
                    }
                }
            }
        }

        val result = scrapeResult
        if (result != null) {
            item {
                NimiyoStyledCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(modifier = Modifier.size(84.dp)) {
                                Surface(
                                    modifier = Modifier.fillMaxSize(),
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    if (result.thumbnail.isNotBlank()) {
                                        AsyncImage(
                                            model = rememberThumbRequest(result.thumbnail, 252),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    } else {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Rounded.Videocam, contentDescription = null)
                                        }
                                    }
                                }
                                val primaryCat = result.items.firstOrNull()?.category ?: MediaCategory.VIDEO
                                val primaryCatLabel = when (primaryCat) {
                                    MediaCategory.VIDEO -> strings.videoLabel
                                    MediaCategory.AUDIO -> strings.audioLabel
                                    MediaCategory.IMAGE -> strings.imageLabel
                                }
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(4.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                ) {
                                    Text(
                                        text = primaryCatLabel,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = result.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                    ) {
                                        Text(
                                            text = result.platform.displayName,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(result.serverUsed, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                clipboard.setText(AnnotatedString(result.title))
                                                Toast.makeText(context, strings.ui.titleCopiedToast, Toast.LENGTH_SHORT).show()
                                            },
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(13.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(strings.ui.copyBtnUpper, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }

                        if (result.isPlaylist && displayItems.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = strings.ytdlp.playlistFormatLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    if (!isAudioOnlyPlaylist) {
                                        FilterChip(
                                            selected = selectedPlaylistAudioPref == null,
                                            onClick = { selectedPlaylistAudioPref = null },
                                            label = { Text(strings.ytdlp.videoMp4Option) },
                                        )
                                    }
                                    AudioQualityPref.entries.forEach { pref ->
                                        FilterChip(
                                            selected = selectedPlaylistAudioPref == pref,
                                            onClick = { selectedPlaylistAudioPref = pref },
                                            label = { Text(pref.label) },
                                        )
                                    }
                                }
                            }
                        }

                        val batchItems = remember(displayItems, settingsState.batchPhotoMode) {
                            val photos = displayItems.filter { it.category == MediaCategory.IMAGE }
                            if (photos.size > 1 && photos.size == displayItems.size && settingsState.batchPhotoMode == BatchPhotoMode.FIRST) {
                                listOf(photos.first())
                            } else {
                                displayItems
                            }
                        }

                        if (displayItems.size > 1) {
                            FilledTonalButton(
                                onClick = {
                                    val usedSrvId = result.platform.servers.firstOrNull { (id, name) ->
                                        name.equals(result.serverUsed, ignoreCase = true) || id.equals(result.serverUsed, ignoreCase = true)
                                    }?.first ?: effectiveServer.orEmpty()
                                    container.downloadManager.enqueue(
                                        items = batchItems,
                                        platform = result.platform,
                                        sourceUrl = result.sourceUrl,
                                        serverId = usedSrvId,
                                    )
                                    if (settingsState.autoClearInput) onUrlChange("")
                                    Toast.makeText(context, "${strings.queuedToast} (${batchItems.size})", Toast.LENGTH_SHORT).show()
                                    onOpenQueueSheet()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Icon(Icons.Rounded.DownloadForOffline, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("${strings.downloadAll} (${batchItems.size})", fontWeight = FontWeight.Bold)
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        Text(strings.ui.downloadLinksHeader, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }

            items(displayItems, key = { "${it.id}_${it.extension}" }) { item ->
                NimiyoStyledCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val icon = when (item.category) {
                            MediaCategory.VIDEO -> Icons.Rounded.Videocam
                            MediaCategory.AUDIO -> Icons.Rounded.MusicNote
                            MediaCategory.IMAGE -> Icons.Rounded.Image
                        }
                        Surface(
                            modifier = Modifier.size(42.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.label,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = "${item.extension.uppercase()} • ${item.title}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(
                            onClick = {
                                val usedSrvId = result.platform.servers.firstOrNull { (id, name) ->
                                    name.equals(result.serverUsed, ignoreCase = true) || id.equals(result.serverUsed, ignoreCase = true)
                                }?.first ?: effectiveServer.orEmpty()
                                container.downloadManager.enqueue(
                                    items = listOf(item),
                                    platform = result.platform,
                                    sourceUrl = result.sourceUrl,
                                    serverId = usedSrvId,
                                )
                                if (settingsState.autoClearInput) onUrlChange("")
                                Toast.makeText(context, strings.queuedToast, Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(strings.downloadSingle)
                        }
                    }
                }
            }
        } else if (!settingsState.quickModeEnabled && !isAnalyzing) {
            item {
                Text(
                    text = strings.quick.platformsHeadline,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                )
            }

            item {
                OutlinedTextField(
                    value = platformSearchQuery,
                    onValueChange = { platformSearchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(style.cornerRadius),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = strings.quick.searchPlatformHint,
                        )
                    },
                    placeholder = { Text(strings.quick.searchPlatformHint) },
                    trailingIcon = {
                        if (platformSearchQuery.isNotBlank()) {
                            IconButton(onClick = { platformSearchQuery = "" }) {
                                Icon(Icons.Rounded.Clear, contentDescription = strings.quick.clearCd)
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = style.cardSurface,
                        unfocusedContainerColor = style.cardSurface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                )
            }

            val q = platformSearchQuery.trim()
            if (q.isNotBlank()) {
                item {
                    val filteredPlatforms = remember(q, allCompliantPlatforms) {
                        val lower = q.lowercase()
                        val starts = allCompliantPlatforms.filter { it.lowercase().startsWith(lower) }
                        val contains = allCompliantPlatforms.filter {
                            !it.lowercase().startsWith(lower) && it.lowercase().contains(lower)
                        }
                        (starts + contains).take(40)
                    }
                    if (filteredPlatforms.isNotEmpty()) {
                        NimiyoStyledCard {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    text = "${filteredPlatforms.size}${if (filteredPlatforms.size >= 40) "+" else ""} ${strings.quick.platformsFoundSuffix}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    filteredPlatforms.forEach { name ->
                                        val matchedId = mapSearchNameToPlatformId(name)
                                        FilterChip(
                                            selected = selectedPlatformOverride != null && selectedPlatformOverride == matchedId && matchedId != PlatformId.OTHER,
                                            onClick = {
                                                selectedPlatformOverride = matchedId
                                                selectedServer = matchedId.servers.firstOrNull()?.first
                                                Toast.makeText(
                                                    context,
                                                    "$name • ${strings.quick.supportedBadge} (${strings.ytdlp.engineBadge})",
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                            },
                                            label = {
                                                Text(
                                                    text = name,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        val isBlockedQuery = remember(q) {
                            val compact = q.lowercase().replace(" ", "")
                            NimiyoContentGuard.isBlockedUrl("https://$compact.com") || runCatching {
                                NimiyoContentGuard.checkTextCompliance(q)
                                false
                            }.getOrDefault(true)
                        }
                        NimiyoStyledCard(
                            containerColor = if (isBlockedQuery) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                style.cardSurface
                            },
                        ) {
                            Text(
                                text = if (isBlockedQuery) {
                                    strings.quick.blockedSearchMsg
                                } else {
                                    "${strings.quick.notInListMsg} ($q)"
                                },
                                modifier = Modifier.padding(14.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isBlockedQuery) {
                                    MaterialTheme.colorScheme.onErrorContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickPreferencesCard(
    settings: SettingsState,
    strings: Strings,
    canStartDownload: Boolean,
    onUpdate: ((SettingsState) -> SettingsState) -> Unit,
    onStartDownload: () -> Unit,
) {
    NimiyoStyledCard {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            com.nimiyo.nativeapp.ui.components.QuickFormatSelectorContent(
                mediaType = settings.quickMediaType,
                videoCodec = settings.quickVideoCodec,
                videoResolution = settings.quickVideoResolution,
                videoAudioFormat = settings.quickVideoAudioFormat,
                audioQuality = settings.quickAudioQuality,
                photoMode = settings.quickPhotoMode,
                strings = strings,
                onMediaTypeChange = { v -> onUpdate { it.copy(quickMediaType = v) } },
                onVideoCodecChange = { v -> onUpdate { it.copy(quickVideoCodec = v) } },
                onVideoResolutionChange = { v -> onUpdate { it.copy(quickVideoResolution = v) } },
                onVideoAudioFormatChange = { v -> onUpdate { it.copy(quickVideoAudioFormat = v) } },
                onAudioQualityChange = { v -> onUpdate { it.copy(quickAudioQuality = v) } },
                onPhotoModeChange = { v -> onUpdate { it.copy(quickPhotoMode = v) } },
            )

            Button(
                onClick = onStartDownload,
                enabled = canStartDownload,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = strings.quick.startDownloadBtn,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}

private fun mapSearchNameToPlatformId(name: String): PlatformId {
    val n = name.lowercase()
    return when {
        n == "youtube" || n.startsWith("youtube") -> PlatformId.YOUTUBE
        n == "tiktok" || n == "vm.tiktok" -> PlatformId.TIKTOK
        n.startsWith("instagram") -> PlatformId.INSTAGRAM
        n == "spotify" -> PlatformId.SPOTIFY
        n.startsWith("soundcloud") -> PlatformId.SOUNDCLOUD
        n.contains("twitter") -> PlatformId.TWITTER
        n.startsWith("facebook") -> PlatformId.FACEBOOK
        n.startsWith("pinterest") -> PlatformId.PINTEREST
        n == "threads" -> PlatformId.THREADS
        n.startsWith("bilibili") || n == "biliintl" -> PlatformId.BILIBILI
        n == "douyin" -> PlatformId.DOUYIN
        n == "pixiv" -> PlatformId.PIXIV
        n.contains("rednote") || n == "xiaohongshu" -> PlatformId.REDNOTE
        n == "apple music" -> PlatformId.APPLE_MUSIC
        n == "bandcamp" -> PlatformId.BANDCAMP
        n.contains("shopee") -> PlatformId.SHOPEE
        else -> PlatformId.OTHER
    }
}
