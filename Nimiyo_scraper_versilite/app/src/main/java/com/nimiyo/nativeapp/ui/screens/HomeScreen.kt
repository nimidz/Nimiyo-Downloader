package com.nimiyo.nativeapp.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
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
import com.nimiyo.nativeapp.VideoPlayerActivity
import com.nimiyo.nativeapp.core.BatchPhotoMode
import com.nimiyo.nativeapp.core.Strings
import com.nimiyo.nativeapp.data.MediaCategory
import com.nimiyo.nativeapp.data.PlatformId
import com.nimiyo.nativeapp.data.ScrapeResult
import com.nimiyo.nativeapp.scraper.PlatformDetector
import com.nimiyo.nativeapp.ui.components.NimiyoStyledCard
import com.nimiyo.nativeapp.ui.components.rememberThumbRequest
import com.nimiyo.nativeapp.ui.theme.LocalNimiyoStyle
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

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
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val settingsState by container.settings.state.collectAsState()
    val style = LocalNimiyoStyle.current

    var selectedPlatformOverride by remember { mutableStateOf<PlatformId?>(null) }
    var selectedServer by remember { mutableStateOf<String?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var analyzeJob by remember { mutableStateOf<Job?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var serverMenuOpen by remember { mutableStateOf(false) }

    val detectedPlatform = selectedPlatformOverride ?: PlatformDetector.detect(urlInput)

    val effectiveServer = selectedServer ?: settingsState.defaultServerFor(detectedPlatform)

    fun triggerAnalyze(overrideSrv: String? = effectiveServer, targetUrl: String = urlInput) {
        val target = targetUrl.trim()
        if (target.isBlank()) return
        analyzeJob?.cancel()
        isAnalyzing = true
        errorMsg = null
        analyzeJob = scope.launch {
            val plat = selectedPlatformOverride ?: PlatformDetector.detect(target)
            val res = runCatching {
                container.scraper.analyze(target, plat, overrideSrv)
            }
            isAnalyzing = false
            res.onSuccess {
                onScrapeResultChange(it)
                val requestedSrvName = overrideSrv?.let { reqId ->
                    it.platform.servers.firstOrNull { (id, _) -> id.equals(reqId, ignoreCase = true) }?.second
                }
                val actualSrvId = it.platform.servers.firstOrNull { (id, name) ->
                    name.equals(it.serverUsed, ignoreCase = true) || id.equals(it.serverUsed, ignoreCase = true)
                }?.first
                if (actualSrvId != null) {
                    selectedServer = actualSrvId
                }
                if (requestedSrvName != null &&
                    !it.serverUsed.equals(requestedSrvName, ignoreCase = true) &&
                    !it.serverUsed.contains(requestedSrvName, ignoreCase = true)
                ) {
                    Toast.makeText(
                        context,
                        "${strings.serverFallbackNotice} ${it.serverUsed}",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
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
                        serverId = actualSrvId ?: overrideSrv.orEmpty(),
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Nimiyo Centered Brand Header + Queue shortcut on right
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            ) {
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
                                analyzeJob?.cancel()
                                isAnalyzing = false
                            },
                            modifier = Modifier.height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        ) {
                            Text(strings.cancelBtn.uppercase(), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                    }
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
                        ) {
                            TextButton(onClick = { errorMsg = null }) {
                                Text(strings.ui.dismissBtn)
                            }
                            if (urlInput.isNotBlank()) {
                                Spacer(Modifier.width(4.dp))
                                Button(onClick = { triggerAnalyze() }) {
                                    Text(strings.retryBtn)
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
                            val previewVideo = remember(result) {
                                result.items.firstOrNull {
                                    it.category == MediaCategory.VIDEO && it.url.startsWith("http")
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .size(84.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .then(
                                        if (previewVideo != null) {
                                            Modifier.clickable {
                                                context.startActivity(
                                                    Intent(context, VideoPlayerActivity::class.java).apply {
                                                        putExtra("uri", previewVideo.url)
                                                        putExtra("title", result.title)
                                                    },
                                                )
                                            }
                                        } else {
                                            Modifier
                                        },
                                    ),
                            ) {
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
                                if (previewVideo != null) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.58f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            Icons.Rounded.PlayArrow,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                                val primaryCat = result.items.firstOrNull()?.category ?: MediaCategory.VIDEO
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(4.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                ) {
                                    Text(
                                        text = primaryCat.name,
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
                                    val activeServerId = selectedServer
                                        ?: result.platform.servers.firstOrNull { (_, name) ->
                                            name.equals(result.serverUsed, ignoreCase = true)
                                        }?.first
                                        ?: effectiveServer.orEmpty()
                                    val pendingSrvName = if (isAnalyzing && selectedServer != null) {
                                        result.platform.servers.firstOrNull { it.first == selectedServer }?.second
                                    } else null
                                    val chipServerLabel = pendingSrvName ?: result.serverUsed
                                    Box {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable(enabled = !isAnalyzing) { serverMenuOpen = true },
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                if (isAnalyzing) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(12.dp),
                                                        strokeWidth = 1.8.dp,
                                                    )
                                                } else {
                                                    Icon(Icons.Rounded.SwapHoriz, contentDescription = null, modifier = Modifier.size(14.dp))
                                                }
                                                Spacer(Modifier.width(4.dp))
                                                Text(chipServerLabel, style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                        DropdownMenu(expanded = serverMenuOpen, onDismissRequest = { serverMenuOpen = false }) {
                                            result.platform.servers.forEach { (srvId, srvName) ->
                                                val isCurrent = srvId.equals(activeServerId, ignoreCase = true)
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            srvName,
                                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                                        )
                                                    },
                                                    trailingIcon = if (isCurrent) {
                                                        { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                                    } else null,
                                                    onClick = {
                                                        serverMenuOpen = false
                                                        selectedServer = srvId
                                                        triggerAnalyze(srvId, targetUrl = result.sourceUrl.ifBlank { urlInput })
                                                    },
                                                )
                                            }
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

                        val batchItems = remember(result, settingsState.batchPhotoMode) {
                            val photos = result.items.filter { it.category == MediaCategory.IMAGE }
                            if (photos.size > 1 && photos.size == result.items.size && settingsState.batchPhotoMode == BatchPhotoMode.FIRST) {
                                listOf(photos.first())
                            } else {
                                result.items
                            }
                        }

                        if (result.items.size > 1) {
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

            items(result.items, key = { it.id }) { item ->
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
        } else {
            // Nimiyo Iconic Centered "SUPPORTED PLATFORMS" 2-Column Grid
            item {
                Text(
                    text = strings.supportedPlatformsLabel.uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                )
            }

            val rows = PlatformId.entries.chunked(3)
            items(rows.size) { rowIdx ->
                val row = rows[rowIdx]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { platform ->
                        PlatformGridTile(
                            platform = platform,
                            selected = detectedPlatform == platform,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                selectedPlatformOverride = platform
                                selectedServer = platform.servers.firstOrNull()?.first
                                Toast.makeText(
                                    context,
                                    "${platform.displayName}: ${platform.servers.joinToString { it.second }}",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                        )
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun PlatformGridTile(
    platform: PlatformId,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val (badgeColor, badgeLetter) = when (platform) {
        PlatformId.YOUTUBE -> Color(0xFFFF0000) to "▶"
        PlatformId.TIKTOK -> Color(0xFF111827) to "♪"
        PlatformId.INSTAGRAM -> Color(0xFFE1306C) to "IG"
        PlatformId.TWITTER -> Color(0xFF1D9BF0) to "𝕏"
        PlatformId.SPOTIFY -> Color(0xFF1DB954) to "S"
        PlatformId.APPLE_MUSIC -> Color(0xFFFA243C) to "♫"
        PlatformId.SOUNDCLOUD -> Color(0xFFFF5500) to "SC"
        PlatformId.FACEBOOK -> Color(0xFF1877F2) to "f"
        PlatformId.THREADS -> Color(0xFF1E293B) to "@"
        PlatformId.PINTEREST -> Color(0xFFE60023) to "P"
        PlatformId.BILIBILI -> Color(0xFF00A1D6) to "B"
        PlatformId.DOUYIN -> Color(0xFFFE2C55) to "D"
        PlatformId.BANDCAMP -> Color(0xFF1DA0C3) to "BC"
        PlatformId.PIXIV -> Color(0xFF0096FA) to "Px"
        PlatformId.REDNOTE -> Color(0xFFFF2442) to "小"
        PlatformId.SHOPEE -> Color(0xFFEE4D2D) to "Sh"
    }

    val style = LocalNimiyoStyle.current
    val darkBadgeRing = if (platform == PlatformId.TIKTOK || platform == PlatformId.THREADS) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    } else null

    Surface(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(style.cornerRadius * 0.7f))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(style.cornerRadius * 0.7f),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else style.cardSurface,
        border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else style.cardBorder,
        shadowElevation = style.cardElevation,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(20.dp),
                shape = CircleShape,
                color = badgeColor,
                border = darkBadgeRing,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = badgeLetter,
                        color = Color.White,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            val tileLabel = when (platform) {
                PlatformId.REDNOTE -> "REDNOTE"
                PlatformId.SHOPEE -> "SHOPEE"
                else -> platform.displayName.uppercase()
            }
            Text(
                text = tileLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
