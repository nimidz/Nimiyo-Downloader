package com.nimiyo.nativeapp.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoveToInbox
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nimiyo.nativeapp.BuildConfig
import com.nimiyo.nativeapp.NimiyoAppContainer
import com.nimiyo.nativeapp.R
import com.nimiyo.nativeapp.core.AccentColorPref
import com.nimiyo.nativeapp.core.AppLanguage
import com.nimiyo.nativeapp.core.BatchPhotoMode
import com.nimiyo.nativeapp.core.DarkModePref
import com.nimiyo.nativeapp.core.DownloadSubfolderMode
import com.nimiyo.nativeapp.core.FilenameTemplate
import com.nimiyo.nativeapp.core.OverwriteMode
import com.nimiyo.nativeapp.core.StartupPage
import com.nimiyo.nativeapp.core.Strings
import com.nimiyo.nativeapp.core.UiStyle
import com.nimiyo.nativeapp.data.PlatformId
import com.nimiyo.nativeapp.ui.components.NimiyoStyledCard
import com.nimiyo.nativeapp.ui.components.formatFileSize
import com.nimiyo.nativeapp.ui.theme.LocalNimiyoStyle
import com.nimiyo.nativeapp.ui.theme.palettePreviewTriple
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private enum class SettingsSubPage {
    MAIN, GENERAL, APPEARANCE, STORAGE, NETWORK, ABOUT, ABOUT_SOCIAL, ABOUT_CREDITS, ABOUT_LICENSE, ABOUT_RULES
}

private enum class FolderPickTarget { VIDEO, AUDIO, IMAGE, SDCARD }

@Composable
fun SettingsScreen(
    container: NimiyoAppContainer,
    strings: Strings,
    isActive: Boolean = true,
    openQuickSaveSettingsTrigger: Int = 0,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by container.settings.state.collectAsState()
    var subPage by rememberSaveable { mutableStateOf(SettingsSubPage.MAIN) }
    androidx.compose.runtime.LaunchedEffect(openQuickSaveSettingsTrigger) {
        if (openQuickSaveSettingsTrigger > 0) {
            subPage = SettingsSubPage.STORAGE
        }
    }
    var pendingUpdate by remember { mutableStateOf<com.nimiyo.nativeapp.core.ReleaseInfo?>(null) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var showWipeConfirm by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<String?>(null) }
    var mediaFolderSizes by remember { mutableStateOf(com.nimiyo.nativeapp.data.MediaStoreRepository.NimiyoFolderSizes()) }
    var activeFolderTarget by remember { mutableStateOf(FolderPickTarget.VIDEO) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            val rawDocId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull().orEmpty()
            val readable = rawDocId.substringAfter(':', rawDocId).ifBlank { uri.lastPathSegment.orEmpty() }
            container.settings.update { cur ->
                when (activeFolderTarget) {
                    FolderPickTarget.VIDEO -> cur.copy(customVideoTreeUri = uri.toString(), customVideoFolderLabel = readable)
                    FolderPickTarget.AUDIO -> cur.copy(customAudioTreeUri = uri.toString(), customAudioFolderLabel = readable)
                    FolderPickTarget.IMAGE -> cur.copy(customImageTreeUri = uri.toString(), customImageFolderLabel = readable)
                    FolderPickTarget.SDCARD -> cur.copy(sdCardEnabled = true, sdCardTreeUri = uri.toString(), sdCardFolderLabel = readable)
                }
            }
        }
    }

    fun openExternalUrl(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    fun navigateUpFromSubPage() {
        subPage = when (subPage) {
            SettingsSubPage.ABOUT_SOCIAL,
            SettingsSubPage.ABOUT_CREDITS,
            SettingsSubPage.ABOUT_LICENSE,
            SettingsSubPage.ABOUT_RULES -> SettingsSubPage.ABOUT
            else -> SettingsSubPage.MAIN
        }
    }

    LaunchedEffect(subPage) {
        if (subPage == SettingsSubPage.STORAGE) {
            mediaFolderSizes = container.mediaRepo.computeFolderSizes()
        }
    }

    BackHandler(enabled = isActive && (showResetConfirm || showWipeConfirm || subPage != SettingsSubPage.MAIN)) {
        when {
            showResetConfirm -> showResetConfirm = false
            showWipeConfirm -> showWipeConfirm = false
            subPage != SettingsSubPage.MAIN -> navigateUpFromSubPage()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        val smoothFadeCurve = remember { CubicBezierEasing(0.22f, 1f, 0.36f, 1f) }
        val mainListState = rememberLazyListState()
        var mountedSubPages by remember { mutableStateOf(emptySet<SettingsSubPage>()) }

        fun titleForPage(page: SettingsSubPage): String = when (page) {
            SettingsSubPage.MAIN -> strings.settingsTitle
            SettingsSubPage.GENERAL -> strings.generalMenuTitle
            SettingsSubPage.APPEARANCE -> strings.appearanceMenuTitle
            SettingsSubPage.STORAGE -> strings.storageMenuTitle
            SettingsSubPage.NETWORK -> strings.networkMenuTitle
            SettingsSubPage.ABOUT -> strings.aboutAppTitle
            SettingsSubPage.ABOUT_SOCIAL -> strings.devSocialTitle
            SettingsSubPage.ABOUT_CREDITS -> strings.credits.title
            SettingsSubPage.ABOUT_LICENSE -> strings.licenseTitle
            SettingsSubPage.ABOUT_RULES -> strings.rulesMenuTitle
        }

        // Pre-warm primary settings subpages sequentially after startup so entering any menu has 0ms cold-compose lag
        LaunchedEffect(Unit) {
            val primaryPages = listOf(
                SettingsSubPage.GENERAL,
                SettingsSubPage.APPEARANCE,
                SettingsSubPage.STORAGE,
                SettingsSubPage.NETWORK,
                SettingsSubPage.ABOUT,
            )
            for (p in primaryPages) {
                if (!mountedSubPages.contains(p)) {
                    delay(65)
                    mountedSubPages = mountedSubPages + p
                }
            }
        }

        // Pre-warm About sub-entries when user enters About, and always ensure current subPage is mounted
        LaunchedEffect(subPage) {
            if (subPage != SettingsSubPage.MAIN && !mountedSubPages.contains(subPage)) {
                mountedSubPages = mountedSubPages + subPage
            }
            if (subPage == SettingsSubPage.ABOUT) {
                for (p in listOf(SettingsSubPage.ABOUT_SOCIAL, SettingsSubPage.ABOUT_CREDITS, SettingsSubPage.ABOUT_LICENSE, SettingsSubPage.ABOUT_RULES)) {
                    if (!mountedSubPages.contains(p)) {
                        delay(50)
                        mountedSubPages = mountedSubPages + p
                    }
                }
            }
        }

        val mainSlideFracState = animateFloatAsState(
            targetValue = if (subPage == SettingsSubPage.MAIN) 0f else -0.22f,
            animationSpec = if (state.transitionAnimations) {
                tween(durationMillis = 260, easing = smoothFadeCurve)
            } else {
                snap()
            },
            label = "SettingsMainSlide",
        )

        val style = LocalNimiyoStyle.current
        val pageBgModifier = if (style.isGlass && style.backdropBrush != null) {
            Modifier
                .background(MaterialTheme.colorScheme.background)
                .background(style.backdropBrush)
        } else {
            Modifier.background(MaterialTheme.colorScheme.background)
        }

        Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
            // Persistent Settings MAIN hub at zIndex(1f) with GPU parallax slide (header + list move together)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f)
                    .graphicsLayer {
                        translationX = mainSlideFracState.value * size.width
                    }
                    .then(pageBgModifier),
            ) {
                SettingsPageHeader(
                    title = strings.settingsTitle,
                    showBack = false,
                    onBack = {},
                )
                LazyColumn(
                    state = mainListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, top = 2.dp, end = 16.dp, bottom = 90.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                item(key = "menu_general") {
                    SettingsMenuCard(
                        icon = Icons.Rounded.Settings,
                        title = strings.generalMenuTitle,
                        desc = "${state.language.displayName} • ${strings.ui.menuDescGeneral}",
                        onClick = { subPage = SettingsSubPage.GENERAL },
                    )
                }
                item(key = "menu_appearance") {
                    val darkLbl = when (state.darkMode) {
                        DarkModePref.SYSTEM -> strings.ui.modeSystem
                        DarkModePref.LIGHT -> strings.ui.modeLight
                        DarkModePref.DARK -> strings.ui.modeDark
                    }
                    val styleLbl = when (state.uiStyle) {
                        UiStyle.MATERIAL -> "Material 3"
                        UiStyle.GLASSMORPHISM -> "Glassmorphism"
                        UiStyle.CUPERTINO -> "Cupertino (iOS)"
                    }
                    SettingsMenuCard(
                        icon = Icons.Rounded.Palette,
                        title = strings.appearanceMenuTitle,
                        desc = "$darkLbl • $styleLbl • ${state.accentColor.label}",
                        onClick = { subPage = SettingsSubPage.APPEARANCE },
                    )
                }
                item(key = "menu_storage") {
                    val fnLbl = when (state.filenameTemplate) {
                        FilenameTemplate.TITLE -> strings.fnTitleOnly
                        FilenameTemplate.TITLE_PLATFORM -> strings.fnTitlePlatform
                        FilenameTemplate.TITLE_DATE -> strings.fnTitleDate
                    }
                    SettingsMenuCard(
                        icon = Icons.Rounded.CloudDownload,
                        title = strings.storageMenuTitle,
                        desc = "${strings.ui.concurrentPrefix}: ${state.maxConcurrentDownloads} • $fnLbl • Folder & Cache",
                        onClick = { subPage = SettingsSubPage.STORAGE },
                    )
                }
                item(key = "menu_network") {
                    SettingsMenuCard(
                        icon = Icons.Rounded.Wifi,
                        title = strings.networkMenuTitle,
                        desc = "${strings.ui.retryPrefix} ${state.maxRetryCount}x • ${strings.ui.timeoutPrefix} ${state.timeoutSeconds}s • Server",
                        onClick = { subPage = SettingsSubPage.NETWORK },
                    )
                }
                item(key = "menu_about") {
                    SettingsMenuCard(
                        icon = Icons.Rounded.Info,
                        title = strings.aboutAppTitle,
                        desc = "NimiyoLite v${BuildConfig.VERSION_NAME} • ${strings.credits.title} • ${strings.licenseTitle} • ${strings.rulesMenuTitle}",
                        onClick = { subPage = SettingsSubPage.ABOUT },
                    )
                }
            }
            }

            val isAboutSub = subPage == SettingsSubPage.ABOUT_SOCIAL ||
                subPage == SettingsSubPage.ABOUT_CREDITS ||
                subPage == SettingsSubPage.ABOUT_LICENSE ||
                subPage == SettingsSubPage.ABOUT_RULES
            for (page in SettingsSubPage.entries) {
                if (page != SettingsSubPage.MAIN && mountedSubPages.contains(page)) {
                    val isSelected = subPage == page
                    val isParallaxParent = page == SettingsSubPage.ABOUT && isAboutSub
                    val targetSlide = when {
                        isSelected -> 0f
                        isParallaxParent -> -0.22f
                        else -> 1f
                    }
                    val pageSlideFracState = animateFloatAsState(
                        targetValue = targetSlide,
                        animationSpec = if (state.transitionAnimations) {
                            tween(durationMillis = 260, easing = smoothFadeCurve)
                        } else {
                            snap()
                        },
                        label = "SettingsSubPageSlide_${page.name}",
                    )
                    // derivedStateOf prevents per-frame recomposition during the 260ms slide
                    val isAnimatingInView by remember(page) {
                        derivedStateOf { pageSlideFracState.value < 0.995f }
                    }
                    val isVisibleOnScreen = isSelected || isParallaxParent || isAnimatingInView
                    val layerZ = when {
                        isSelected -> 3f
                        isVisibleOnScreen -> 2f
                        else -> 0f
                    }
                    if (isVisibleOnScreen) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(layerZ)
                            .graphicsLayer {
                                val frac = pageSlideFracState.value
                                translationX = frac * size.width
                                alpha = if (frac < 0.995f || isSelected || isParallaxParent) 1f else 0f
                            }
                            .then(pageBgModifier),
                    ) {
                        SettingsPageHeader(
                            title = titleForPage(page),
                            showBack = true,
                            onBack = { navigateUpFromSubPage() },
                        )
                        val subListState = rememberLazyListState()
                        LazyColumn(
                            state = subListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, top = 2.dp, end = 16.dp, bottom = 90.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                        when (page) {
                            SettingsSubPage.MAIN -> Unit

                SettingsSubPage.ABOUT -> {
                    // 1. Paling atas: Papan About App
                    item {
                        NimiyoStyledCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Image(
                                        painter = painterResource(R.drawable.icon_untukdi_aboutthisapp),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(58.dp)
                                            .clip(RoundedCornerShape(14.dp)),
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "NIMIYO Downloader",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.primary,
                                        ) {
                                            Text(
                                                text = "Version ${BuildConfig.VERSION_NAME} (NativeLite)",
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                            )
                                        }
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

                                Text(
                                    text = strings.aboutTagline,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }

                    // 2. Menu Credits
                    item {
                        SettingsMenuCard(
                            icon = Icons.Rounded.Code,
                            title = strings.credits.title,
                            desc = "scrapr (coflyn), NIMIYO Lite, Kotlin, Compose, Media3, OkHttp, Jsoup, Coil",
                            onClick = { subPage = SettingsSubPage.ABOUT_CREDITS },
                        )
                    }

                    // 3. Tombol lainnya dipisah sebagai menu item
                    item {
                        SettingsMenuCard(
                            icon = Icons.Rounded.Groups,
                            title = strings.devSocialTitle,
                            desc = "Instagram @nimidzy • TikTok @nimidz • YouTube • GitHub",
                            onClick = { subPage = SettingsSubPage.ABOUT_SOCIAL },
                        )
                    }
                    item {
                        SettingsMenuCard(
                            icon = Icons.Rounded.Favorite,
                            title = strings.supportTitle,
                            desc = strings.supportDesc,
                            onClick = { openExternalUrl("https://trakteer.id/nimidz") },
                        )
                    }
                    item {
                        SettingsMenuCard(
                            icon = Icons.Rounded.SystemUpdate,
                            title = strings.checkUpdates,
                            desc = updateResult ?: strings.checkUpdatesDesc,
                            onClick = {
                                updateResult = strings.checkingUpdatesMsg
                                scope.launch {
                                    when (val res = com.nimiyo.nativeapp.core.UpdateChecker.checkLatestRelease(container.httpFactory)) {
                                        is com.nimiyo.nativeapp.core.UpdateCheckResult.Available -> {
                                            updateResult = "${strings.updateAvailable}: v${res.release.versionName}"
                                            pendingUpdate = res.release
                                        }
                                        is com.nimiyo.nativeapp.core.UpdateCheckResult.UpToDate -> {
                                            updateResult = strings.upToDate
                                            Toast.makeText(context, strings.upToDate, Toast.LENGTH_SHORT).show()
                                        }
                                        is com.nimiyo.nativeapp.core.UpdateCheckResult.Error -> {
                                            updateResult = res.message
                                            Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                        )
                    }
                    item {
                        SettingsMenuCard(
                            icon = Icons.Rounded.Description,
                            title = strings.licenseTitle,
                            desc = "GNU General Public License v3.0 (GPL-3.0)",
                            onClick = { subPage = SettingsSubPage.ABOUT_LICENSE },
                        )
                    }
                    item {
                        SettingsMenuCard(
                            icon = Icons.Rounded.Gavel,
                            title = strings.rulesMenuTitle,
                            desc = if (state.rulesAccepted) strings.rulesMenuDescAccepted else strings.rulesMenuDescPending,
                            onClick = { subPage = SettingsSubPage.ABOUT_RULES },
                        )
                    }
                }

                SettingsSubPage.ABOUT_SOCIAL -> {
                    item {
                        SettingsMenuCard(
                            icon = Icons.Rounded.Public,
                            title = "Instagram",
                            desc = "@nimidzy",
                            onClick = { openExternalUrl("https://instagram.com/nimidzy") },
                        )
                    }
                    item {
                        SettingsMenuCard(
                            icon = Icons.Rounded.PlayCircle,
                            title = "TikTok",
                            desc = "@nimidz",
                            onClick = { openExternalUrl("https://tiktok.com/@nimidz") },
                        )
                    }
                    item {
                        SettingsMenuCard(
                            icon = Icons.Rounded.PlayCircle,
                            title = "YouTube",
                            desc = "@nimidz",
                            onClick = { openExternalUrl("https://youtube.com/@nimidz") },
                        )
                    }
                    item {
                        SettingsMenuCard(
                            icon = Icons.Rounded.Code,
                            title = "GitHub",
                            desc = "github.com/nimidz",
                            onClick = { openExternalUrl("https://github.com/nimidz") },
                        )
                    }
                }

                SettingsSubPage.ABOUT_CREDITS -> {
                    item {
                        NimiyoStyledCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { openExternalUrl("https://github.com/coflyn") },
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("scrapr (coflyn)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                            Text(strings.credits.scraprDesc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                listOf(
                                    "NIMIYO Lite Source (Nimidz)" to strings.credits.sourceDesc,
                                    "Kotlin & Coroutines (1.7.3)" to strings.credits.kotlinDesc,
                                    "Jetpack Compose & Material 3 (BOM 2024.09.03)" to "Declarative UI & Material Icons Extended",
                                    "AndroidX Core, Lifecycle & Activity" to "core-ktx 1.12.0 • lifecycle 2.7.0 • activity-compose 1.8.2 • profileinstaller 1.3.1",
                                    "AndroidX Media3 ExoPlayer (1.3.1) & Media (1.7.0)" to strings.credits.media3Desc,
                                    "OkHttp (4.12.0)" to strings.credits.okHttpDesc,
                                    "Jsoup (1.17.2)" to "HTML Parsing & Metadata Extraction",
                                    "Coil Compose (2.6.0)" to "Async Image & Artwork Loading",
                                ).forEach { (creditName, creditDesc) ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                            Text(creditName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                            Text(creditDesc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                SettingsSubPage.ABOUT_LICENSE -> {
                    item {
                        NimiyoStyledCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(
                                    text = "GNU General Public License v3.0",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                )
                                Text(
                                    text = "Version 3, 29 June 2007\nCopyright (C) 2007 Free Software Foundation, Inc. <https://fsf.org/>",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                Text(
                                    text = strings.gplSummary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Button(
                                    onClick = { openExternalUrl("https://www.gnu.org/licenses/gpl-3.0.html") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                ) {
                                    Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(strings.readFullGplBtn)
                                }
                            }
                        }
                    }
                }

                SettingsSubPage.ABOUT_RULES -> {
                    item {
                        NimiyoStyledCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                NimiyoRulesContent(strings)

                                Button(
                                    onClick = {
                                        container.settings.update { it.copy(rulesAccepted = true) }
                                        Toast.makeText(context, strings.rulesMenuDescAccepted, Toast.LENGTH_SHORT).show()
                                        subPage = SettingsSubPage.ABOUT
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                ) {
                                    Text(
                                        text = if (state.rulesAccepted) "SUDAH DISETUJUI" else "SAYA SETUJU & MENGERTI",
                                        fontWeight = FontWeight.ExtraBold,
                                    )
                                }
                            }
                        }
                    }
                }

                SettingsSubPage.GENERAL -> {
                    item {
                        val divColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                        NimiyoStyledCard {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(strings.languageLabel, fontWeight = FontWeight.Bold)
                                var langMenuExpanded by remember { mutableStateOf(false) }
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedButton(
                                        onClick = { langMenuExpanded = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(state.language.displayName)
                                            Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
                                        }
                                    }
                                    DropdownMenu(
                                        expanded = langMenuExpanded,
                                        onDismissRequest = { langMenuExpanded = false },
                                    ) {
                                        AppLanguage.entries.forEach { lang ->
                                            DropdownMenuItem(
                                                text = { Text(lang.displayName) },
                                                trailingIcon = {
                                                    if (state.language == lang) {
                                                        Icon(Icons.Rounded.Check, contentDescription = null)
                                                    }
                                                },
                                                onClick = {
                                                    container.settings.update { it.copy(language = lang) }
                                                    langMenuExpanded = false
                                                },
                                            )
                                        }
                                    }
                                }

                                HorizontalDivider(color = divColor)

                                SettingSwitchRow(strings.clipboardDetectLabel, state.clipboardAutoDetect) { v ->
                                    container.settings.update { it.copy(clipboardAutoDetect = v) }
                                }
                                HorizontalDivider(color = divColor)
                                SettingSwitchRow(strings.ui.autoAnalyzePasteLabel, state.autoAnalyzeOnPaste) { v ->
                                    container.settings.update { it.copy(autoAnalyzeOnPaste = v) }
                                }
                                HorizontalDivider(color = divColor)
                                SettingSwitchRow(strings.ui.autoClearInputLabel, state.autoClearInput) { v ->
                                    container.settings.update { it.copy(autoClearInput = v) }
                                }
                                HorizontalDivider(color = divColor)
                                SettingSwitchRow(strings.incognitoLabel, state.incognitoMode) { v ->
                                    container.settings.update { it.copy(incognitoMode = v) }
                                }
                            }
                        }
                    }

                    item {
                        val divColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                        NimiyoStyledCard {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(strings.storageExtra.mediaViewerSectionTitle, fontWeight = FontWeight.Bold)
                                SettingSwitchRow(strings.autoPlayVideoLabel, state.autoPlayMedia) { v ->
                                    container.settings.update { it.copy(autoPlayMedia = v) }
                                }
                                HorizontalDivider(color = divColor)
                                SettingSwitchRow(strings.autoLoopVideoLabel, state.autoLoopMedia) { v ->
                                    container.settings.update { it.copy(autoLoopMedia = v) }
                                }
                                HorizontalDivider(color = divColor)
                                SettingSwitchRow(strings.keepAwakeLabel, state.keepScreenAwake) { v ->
                                    container.settings.update { it.copy(keepScreenAwake = v) }
                                }
                            }
                        }
                    }

                    item {
                        val divColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SettingSectionTag(strings.advancedMenuTitle)
                            NimiyoStyledCard {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    SettingSwitchRow(strings.bypassSslLabel, state.bypassSsl) { v ->
                                        container.settings.update { it.copy(bypassSsl = v) }
                                    }
                                    HorizontalDivider(color = divColor)
                                    SettingSwitchRow(strings.forceIpv4Label, state.forceIpv4) { v ->
                                        container.settings.update { it.copy(forceIpv4 = v) }
                                    }
                                    HorizontalDivider(color = divColor)
                                    OutlinedTextField(
                                        value = state.customUserAgent,
                                        onValueChange = { v -> container.settings.update { it.copy(customUserAgent = v) } },
                                        label = { Text(strings.ui.customUaLabel) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    HorizontalDivider(color = divColor)
                                    Button(
                                        onClick = { showResetConfirm = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    ) {
                                        Text(strings.resetDefaultsBtn, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                SettingsSubPage.APPEARANCE -> {
                    item {
                        val divColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                        NimiyoStyledCard {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(strings.startupPageLabel, fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(
                                        StartupPage.HOME to strings.navHome,
                                        StartupPage.PLAYER to strings.navPlayer,
                                        StartupPage.HISTORY to strings.navHistory,
                                    ).forEach { (sp, lbl) ->
                                        FilterChip(
                                            selected = state.startupPage == sp,
                                            onClick = { container.settings.update { it.copy(startupPage = sp) } },
                                            label = { Text(lbl) },
                                        )
                                    }
                                }

                                HorizontalDivider(color = divColor)

                                Text(strings.darkModeLabel, fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(
                                        DarkModePref.SYSTEM to "System",
                                        DarkModePref.LIGHT to "Light",
                                        DarkModePref.DARK to "Dark",
                                    ).forEach { (dm, lbl) ->
                                        FilterChip(
                                            selected = state.darkMode == dm,
                                            onClick = { container.settings.update { it.copy(darkMode = dm) } },
                                            label = { Text(lbl) },
                                        )
                                    }
                                }

                                HorizontalDivider(color = divColor)

                                Text(strings.themeStyle, fontWeight = FontWeight.Bold)
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    listOf(
                                        UiStyle.MATERIAL to "Material 3",
                                        UiStyle.GLASSMORPHISM to "Glassmorphism",
                                        UiStyle.CUPERTINO to "Cupertino (iOS)",
                                    ).forEach { (styleEnum, lbl) ->
                                        FilterChip(
                                            selected = state.uiStyle == styleEnum,
                                            onClick = { container.settings.update { it.copy(uiStyle = styleEnum) } },
                                            label = { Text(lbl) },
                                        )
                                    }
                                }

                                HorizontalDivider(color = divColor)

                                Text(strings.accentColorLabel, fontWeight = FontWeight.Bold)
                                val isDarkPreview = LocalNimiyoStyle.current.isDark
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable {
                                            val next = if (state.accentColor == AccentColorPref.DYNAMIC) {
                                                AccentColorPref.GREEN
                                            } else {
                                                AccentColorPref.DYNAMIC
                                            }
                                            container.settings.update { it.copy(accentColor = next) }
                                        }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Palette,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = strings.ui.accentDynamic,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Text(
                                            text = if (state.language == AppLanguage.ID || state.language == AppLanguage.MS) {
                                                "Terapkan warna dari wallpaper ke tema aplikasi"
                                            } else {
                                                "Apply colors from wallpapers to the app theme"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Switch(
                                        checked = state.accentColor == AccentColorPref.DYNAMIC,
                                        onCheckedChange = { enabled ->
                                            val next = if (enabled) AccentColorPref.DYNAMIC else AccentColorPref.GREEN
                                            container.settings.update { it.copy(accentColor = next) }
                                        },
                                    )
                                }

                                val palettes = AccentColorPref.entries.filter { it != AccentColorPref.DYNAMIC }
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    palettes.chunked(4).forEach { rowItems ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            rowItems.forEach { acc ->
                                                val selected = state.accentColor == acc
                                                val (topColor, leftColor, rightColor) = palettePreviewTriple(acc, isDarkPreview)
                                                Surface(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clip(RoundedCornerShape(16.dp))
                                                        .clickable {
                                                            container.settings.update { it.copy(accentColor = acc) }
                                                        },
                                                    shape = RoundedCornerShape(16.dp),
                                                    color = if (selected) {
                                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                                    } else {
                                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                                    },
                                                    border = if (selected) {
                                                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                                    } else {
                                                        null
                                                    },
                                                ) {
                                                    Column(
                                                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                                    ) {
                                                        Box(
                                                            modifier = Modifier.size(38.dp),
                                                            contentAlignment = Alignment.Center,
                                                        ) {
                                                            Canvas(modifier = Modifier.fillMaxSize()) {
                                                                drawArc(
                                                                    color = topColor,
                                                                    startAngle = 180f,
                                                                    sweepAngle = 180f,
                                                                    useCenter = true,
                                                                )
                                                                drawArc(
                                                                    color = leftColor,
                                                                    startAngle = 90f,
                                                                    sweepAngle = 90f,
                                                                    useCenter = true,
                                                                )
                                                                drawArc(
                                                                    color = rightColor,
                                                                    startAngle = 0f,
                                                                    sweepAngle = 90f,
                                                                    useCenter = true,
                                                                )
                                                            }
                                                            if (selected) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .size(18.dp)
                                                                        .clip(CircleShape)
                                                                        .background(MaterialTheme.colorScheme.primary),
                                                                    contentAlignment = Alignment.Center,
                                                                ) {
                                                                    Icon(
                                                                        imageVector = Icons.Rounded.Check,
                                                                        contentDescription = null,
                                                                        tint = MaterialTheme.colorScheme.onPrimary,
                                                                        modifier = Modifier.size(12.dp),
                                                                    )
                                                                }
                                                            }
                                                        }
                                                        Text(
                                                            text = acc.label,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                            color = if (selected) {
                                                                MaterialTheme.colorScheme.primary
                                                            } else {
                                                                MaterialTheme.colorScheme.onSurfaceVariant
                                                            },
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            textAlign = TextAlign.Center,
                                                        )
                                                    }
                                                }
                                            }
                                            repeat(4 - rowItems.size) {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }

                                HorizontalDivider(color = divColor)

                                SettingSwitchRow(strings.completionSoundLabel, state.completionSound) { v ->
                                    container.settings.update { it.copy(completionSound = v) }
                                }
                                HorizontalDivider(color = divColor)
                                SettingSwitchRow(strings.hapticLabel, state.hapticFeedback) { v ->
                                    container.settings.update { it.copy(hapticFeedback = v) }
                                }
                                HorizontalDivider(color = divColor)
                                SettingSwitchRow(strings.transitionAnimLabel, state.transitionAnimations) { v ->
                                    container.settings.update { it.copy(transitionAnimations = v) }
                                }
                            }
                        }
                    }
                }

                SettingsSubPage.STORAGE -> {
                    val se = strings.storageExtra

                    // 1. Tag Umum: Folder per media + Pengelompokan Subfolder
                    item {
                        val divColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SettingSectionTag(se.sectionGeneralTag)
                            NimiyoStyledCard {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    SettingFolderRow(
                                        icon = Icons.Rounded.VideoLibrary,
                                        title = se.folderVideoTitle,
                                        subtitle = if (state.customVideoTreeUri.isNotBlank()) {
                                            state.customVideoFolderLabel.ifBlank { state.customVideoTreeUri }
                                        } else {
                                            "/storage/emulated/0/Download/Nimiyo/VideoYo"
                                        },
                                        isCustom = state.customVideoTreeUri.isNotBlank(),
                                        onPick = {
                                            activeFolderTarget = FolderPickTarget.VIDEO
                                            folderPickerLauncher.launch(null)
                                        },
                                        onClear = {
                                            container.settings.update { it.copy(customVideoTreeUri = "", customVideoFolderLabel = "") }
                                        },
                                    )
                                    HorizontalDivider(color = divColor)
                                    SettingFolderRow(
                                        icon = Icons.Rounded.LibraryMusic,
                                        title = se.folderAudioTitle,
                                        subtitle = if (state.customAudioTreeUri.isNotBlank()) {
                                            state.customAudioFolderLabel.ifBlank { state.customAudioTreeUri }
                                        } else {
                                            "/storage/emulated/0/Download/Nimiyo/AudioYo"
                                        },
                                        isCustom = state.customAudioTreeUri.isNotBlank(),
                                        onPick = {
                                            activeFolderTarget = FolderPickTarget.AUDIO
                                            folderPickerLauncher.launch(null)
                                        },
                                        onClear = {
                                            container.settings.update { it.copy(customAudioTreeUri = "", customAudioFolderLabel = "") }
                                        },
                                    )
                                    HorizontalDivider(color = divColor)
                                    SettingFolderRow(
                                        icon = Icons.Rounded.PhotoLibrary,
                                        title = se.folderImageTitle,
                                        subtitle = if (state.customImageTreeUri.isNotBlank()) {
                                            state.customImageFolderLabel.ifBlank { state.customImageTreeUri }
                                        } else {
                                            "/storage/emulated/0/Download/Nimiyo/ImageYo"
                                        },
                                        isCustom = state.customImageTreeUri.isNotBlank(),
                                        onPick = {
                                            activeFolderTarget = FolderPickTarget.IMAGE
                                            folderPickerLauncher.launch(null)
                                        },
                                        onClear = {
                                            container.settings.update { it.copy(customImageTreeUri = "", customImageFolderLabel = "") }
                                        },
                                    )
                                    HorizontalDivider(color = divColor)
                                    SettingSwitchRow(
                                        label = se.folderSdCardTitle,
                                        checked = state.sdCardEnabled && state.sdCardTreeUri.isNotBlank(),
                                        icon = Icons.Rounded.SdCard,
                                        desc = if (state.sdCardTreeUri.isNotBlank()) {
                                            state.sdCardFolderLabel.ifBlank { state.sdCardTreeUri }
                                        } else {
                                            se.folderSdCardSubtitle
                                        },
                                        onCheckedChange = { enabled ->
                                            if (enabled) {
                                                if (state.sdCardTreeUri.isNotBlank()) {
                                                    container.settings.update { it.copy(sdCardEnabled = true) }
                                                } else {
                                                    activeFolderTarget = FolderPickTarget.SDCARD
                                                    folderPickerLauncher.launch(null)
                                                }
                                            } else {
                                                container.settings.update { it.copy(sdCardEnabled = false) }
                                            }
                                        },
                                    )
                                    if (!state.sdCardEnabled) {
                                        HorizontalDivider(color = divColor)
                                        Text(se.subfolderModeTitle, fontWeight = FontWeight.Bold)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            listOf(
                                                DownloadSubfolderMode.DEFAULT to se.subfolderDefault,
                                                DownloadSubfolderMode.BY_PLATFORM to se.subfolderByPlatform,
                                            ).forEach { (mode, lbl) ->
                                                FilterChip(
                                                    selected = state.downloadSubfolderMode == mode,
                                                    onClick = { container.settings.update { it.copy(downloadSubfolderMode = mode) } },
                                                    label = { Text(lbl) },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. Tag Unduhan & Penamaan
                    item {
                        val divColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SettingSectionTag(se.sectionDownloadNamingTag)
                            NimiyoStyledCard {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Text(strings.filenameTemplateLabel, fontWeight = FontWeight.Bold)
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        FilenameTemplate.entries.forEach { tpl ->
                                            val tplLabel = when (tpl) {
                                                FilenameTemplate.TITLE -> strings.fnTitleOnly
                                                FilenameTemplate.TITLE_PLATFORM -> strings.fnTitlePlatform
                                                FilenameTemplate.TITLE_DATE -> strings.fnTitleDate
                                            }
                                            FilterChip(
                                                selected = state.filenameTemplate == tpl,
                                                onClick = { container.settings.update { it.copy(filenameTemplate = tpl) } },
                                                label = { Text(tplLabel) },
                                            )
                                        }
                                    }

                                    HorizontalDivider(color = divColor)

                                    Text(strings.duplicateHandlingLabel, fontWeight = FontWeight.Bold)
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        OverwriteMode.entries.forEach { om ->
                                            val omLabel = when (om) {
                                                OverwriteMode.RENAME -> strings.ui.owRename
                                                OverwriteMode.OVERWRITE -> strings.owOverwrite
                                                OverwriteMode.SKIP -> strings.owSkip
                                            }
                                            FilterChip(
                                                selected = state.overwriteMode == om,
                                                onClick = { container.settings.update { it.copy(overwriteMode = om) } },
                                                label = { Text(omLabel) },
                                            )
                                        }
                                    }

                                    HorizontalDivider(color = divColor)

                                    Text("${strings.concurrentDownloadsLabel}: ${state.maxConcurrentDownloads}", fontWeight = FontWeight.Bold)
                                    Slider(
                                        value = state.maxConcurrentDownloads.toFloat(),
                                        onValueChange = { f ->
                                            container.settings.update { it.copy(maxConcurrentDownloads = f.toInt().coerceIn(1, 5)) }
                                        },
                                        valueRange = 1f..5f,
                                        steps = 3,
                                    )

                                    HorizontalDivider(color = divColor)

                                    Text(strings.batchPhotoModeLabel, fontWeight = FontWeight.Bold)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        BatchPhotoMode.entries.forEach { bm ->
                                            val bmLabel = when (bm) {
                                                BatchPhotoMode.ALL -> strings.bpAll
                                                BatchPhotoMode.FIRST -> strings.bpFirst
                                            }
                                            FilterChip(
                                                selected = state.batchPhotoMode == bm,
                                                onClick = { container.settings.update { it.copy(batchPhotoMode = bm) } },
                                                label = { Text(bmLabel) },
                                            )
                                        }
                                    }

                                    HorizontalDivider(color = divColor)

                                    SettingSwitchRow(strings.autoDownloadAfterAnalyzeLabel, state.autoDownloadOnAnalyze) { v ->
                                        container.settings.update { it.copy(autoDownloadOnAnalyze = v) }
                                    }

                                    HorizontalDivider(color = divColor)

                                    SettingSwitchRow(
                                        label = strings.qsAutoCloseTitle,
                                        checked = state.quickSaveAutoCloseOnStart,
                                        desc = strings.qsAutoCloseDesc,
                                    ) { v ->
                                        container.settings.update { it.copy(quickSaveAutoCloseOnStart = v) }
                                    }
                                }
                            }
                        }
                    }

                    // 3. Tag Riwayat & Pembersihan Otomatis
                    item {
                        val divColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SettingSectionTag(se.sectionHistoryCleanupTag)
                            NimiyoStyledCard {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Text(strings.historyLimitLabel, fontWeight = FontWeight.Bold)
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        listOf(0 to strings.unlimitedLabel, 50 to "50", 100 to "100", 200 to "200").forEach { (lim, lbl) ->
                                            FilterChip(
                                                selected = state.historyLimit == lim,
                                                onClick = { container.settings.update { it.copy(historyLimit = lim) } },
                                                label = { Text(lbl) },
                                            )
                                        }
                                    }
                                    HorizontalDivider(color = divColor)
                                    Text(strings.autoClearHistoryLabel, fontWeight = FontWeight.Bold)
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        listOf(0 to strings.ui.offLabel, 1 to "1 ${strings.daysSuffix}", 7 to "7 ${strings.daysSuffix}", 30 to "30 ${strings.daysSuffix}", 90 to "90 ${strings.daysSuffix}").forEach { (d, lbl) ->
                                            FilterChip(
                                                selected = state.autoClearHistoryDays == d,
                                                onClick = { container.settings.update { it.copy(autoClearHistoryDays = d) } },
                                                label = { Text(lbl) },
                                            )
                                        }
                                    }
                                    HorizontalDivider(color = divColor)
                                    Text(strings.autoClearCacheLabel, fontWeight = FontWeight.Bold)
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        listOf(0 to strings.ui.offLabel, 1 to "1 ${strings.daysSuffix}", 7 to "7 ${strings.daysSuffix}", 30 to "30 ${strings.daysSuffix}").forEach { (d, lbl) ->
                                            FilterChip(
                                                selected = state.autoClearCacheDays == d,
                                                onClick = { container.settings.update { it.copy(autoClearCacheDays = d) } },
                                                label = { Text(lbl) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 4. Tag Pemeliharaan & Total Ukuran (menyatu rapi dengan tombol hapus/cache)
                    item {
                        val divColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SettingSectionTag(se.sectionMaintenanceTag)
                            NimiyoStyledCard {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(strings.totalMediaSizeLabel, fontWeight = FontWeight.Bold)
                                            Text(
                                                strings.totalMediaSizeDesc,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                        ) {
                                            Text(
                                                text = formatFileSize(mediaFolderSizes.totalMediaBytes).ifBlank { "0 MB" },
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            )
                                        }
                                    }
                                    Text(
                                        text = "${strings.videoLabel} -> Downloads/Nimiyo/VideoYo/ (${formatFileSize(mediaFolderSizes.videoBytes).ifBlank { "0 MB" }})\n" +
                                            "${strings.audioLabel} -> Downloads/Nimiyo/AudioYo/ (${formatFileSize(mediaFolderSizes.audioBytes).ifBlank { "0 MB" }})\n" +
                                            "${strings.imageLabel} -> Downloads/Nimiyo/ImageYo/ (${formatFileSize(mediaFolderSizes.imageBytes).ifBlank { "0 MB" }})\n" +
                                            "${strings.playerTabPlaylists} -> Downloads/Nimiyo/Database/ (${formatFileSize(mediaFolderSizes.databaseBytes).ifBlank { "0 MB" }})\n" +
                                            "${strings.lyricsTitle} -> Downloads/Nimiyo/LyricsYO/ (${formatFileSize(mediaFolderSizes.lyricsBytes).ifBlank { "0 MB" }})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    HorizontalDivider(color = divColor)
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                val cleared = container.mediaRepo.clearAppCache()
                                                val readable = formatFileSize(cleared).ifBlank { "0 KB" }
                                                Toast.makeText(context, "${strings.ui.cacheClearedPrefix} ($readable)", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp),
                                    ) {
                                        Text(strings.clearCacheBtn, fontWeight = FontWeight.SemiBold)
                                    }
                                    Button(
                                        onClick = { showWipeConfirm = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    ) {
                                        Text(strings.resetAllDataBtn, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                SettingsSubPage.NETWORK -> {
                    item {
                        val divColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                        NimiyoStyledCard {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                SettingSwitchRow(strings.wifiOnlyLabel, state.wifiOnlyDownloads) { v ->
                                    container.settings.update { it.copy(wifiOnlyDownloads = v) }
                                }
                                HorizontalDivider(color = divColor)
                                SettingSwitchRow(strings.autoRetryLabel, state.autoRetryFailed) { v ->
                                    container.settings.update { it.copy(autoRetryFailed = v) }
                                }
                                HorizontalDivider(color = divColor)
                                Text("Maksimal Coba Ulang: ${state.maxRetryCount}x", fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(1, 2, 3, 5).forEach { cnt ->
                                        FilterChip(
                                            selected = state.maxRetryCount == cnt,
                                            onClick = { container.settings.update { it.copy(maxRetryCount = cnt) } },
                                            label = { Text("${cnt}x") },
                                        )
                                    }
                                }

                                HorizontalDivider(color = divColor)

                                Text("${strings.timeoutLabel}: ${state.timeoutSeconds}s", fontWeight = FontWeight.Bold)
                                Slider(
                                    value = state.timeoutSeconds.toFloat(),
                                    onValueChange = { f ->
                                        container.settings.update { it.copy(timeoutSeconds = f.toInt().coerceIn(5, 180)) }
                                    },
                                    valueRange = 5f..120f,
                                )

                                HorizontalDivider(color = divColor)

                                Text("DNS over HTTPS (DoH)", fontWeight = FontWeight.Bold)
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    listOf(
                                        "" to "Off (System)",
                                        "https://cloudflare-dns.com/dns-query" to "Cloudflare 1.1.1.1",
                                        "https://dns.google/resolve" to "Google 8.8.8.8",
                                    ).forEach { (url, lbl) ->
                                        FilterChip(
                                            selected = state.customDoh == url,
                                            onClick = { container.settings.update { it.copy(customDoh = url) } },
                                            label = { Text(lbl) },
                                        )
                                    }
                                }
                                OutlinedTextField(
                                    value = state.customDoh,
                                    onValueChange = { v -> container.settings.update { it.copy(customDoh = v) } },
                                    label = { Text(strings.dohLabel) },
                                    placeholder = { Text("https://cloudflare-dns.com/dns-query") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )

                                HorizontalDivider(color = divColor)

                                SettingSwitchRow(strings.headerSpoofingLabel, state.enableHeaderSpoofing) { v ->
                                    container.settings.update { it.copy(enableHeaderSpoofing = v) }
                                }
                            }
                        }
                    }

                    item {
                        val divColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                        NimiyoStyledCard {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Default Server per Platform", fontWeight = FontWeight.Bold)
                                ServerPrefPickerRow("YouTube", PlatformId.YOUTUBE.servers, state.youtubeServer) { id ->
                                    container.settings.update { it.copy(youtubeServer = id) }
                                }
                                HorizontalDivider(color = divColor)
                                ServerPrefPickerRow("TikTok", PlatformId.TIKTOK.servers, state.tiktokServer) { id ->
                                    container.settings.update { it.copy(tiktokServer = id) }
                                }
                                HorizontalDivider(color = divColor)
                                ServerPrefPickerRow("Instagram", PlatformId.INSTAGRAM.servers, state.instagramServer) { id ->
                                    container.settings.update { it.copy(instagramServer = id) }
                                }
                                HorizontalDivider(color = divColor)
                                ServerPrefPickerRow("X / Twitter", PlatformId.TWITTER.servers, state.twitterServer) { id ->
                                    container.settings.update { it.copy(twitterServer = id) }
                                }
                                HorizontalDivider(color = divColor)
                                ServerPrefPickerRow("Spotify", PlatformId.SPOTIFY.servers, state.spotifyServer) { id ->
                                    container.settings.update { it.copy(spotifyServer = id) }
                                }
                                HorizontalDivider(color = divColor)
                                ServerPrefPickerRow("SoundCloud", PlatformId.SOUNDCLOUD.servers, state.soundcloudServer) { id ->
                                    container.settings.update { it.copy(soundcloudServer = id) }
                                }
                                HorizontalDivider(color = divColor)
                                ServerPrefPickerRow("Facebook", PlatformId.FACEBOOK.servers, state.facebookServer) { id ->
                                    container.settings.update { it.copy(facebookServer = id) }
                                }
                                HorizontalDivider(color = divColor)
                                ServerPrefPickerRow("Pinterest", PlatformId.PINTEREST.servers, state.pinterestServer) { id ->
                                    container.settings.update { it.copy(pinterestServer = id) }
                                }
                                HorizontalDivider(color = divColor)
                                ServerPrefPickerRow("Threads", PlatformId.THREADS.servers, state.threadsServer) { id ->
                                    container.settings.update { it.copy(threadsServer = id) }
                                }
                                HorizontalDivider(color = divColor)
                                ServerPrefPickerRow("Shopee Video", PlatformId.SHOPEE.servers, state.shopeeServer) { id ->
                                    container.settings.update { it.copy(shopeeServer = id) }
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
            }
        }
    }

    if (pendingUpdate != null) {
        val rel = pendingUpdate!!
        com.nimiyo.nativeapp.ui.components.NimiyoUpdateDialog(
            release = rel,
            strings = strings,
            onIgnoreDays = { days ->
                val until = System.currentTimeMillis() + days * 86_400_000L
                container.settings.update {
                    it.copy(
                        updateIgnoredUntilMs = until,
                        updateIgnoredVersion = rel.versionName,
                    )
                }
                pendingUpdate = null
            },
            onDismiss = { pendingUpdate = null },
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(strings.resetSettingsTitle) },
            text = { Text(strings.resetSettingsConfirm) },
            confirmButton = {
                TextButton(onClick = {
                    container.settings.resetToDefaults()
                    showResetConfirm = false
                    Toast.makeText(context, strings.resetSettingsTitle, Toast.LENGTH_SHORT).show()
                }) { Text(strings.ui.resetBtnLabel, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text(strings.cancelBtn) }
            },
        )
    }

    if (showWipeConfirm) {
        AlertDialog(
            onDismissRequest = { showWipeConfirm = false },
            title = { Text(strings.wipeDataTitle) },
            text = { Text(strings.wipeDataConfirm) },
            confirmButton = {
                TextButton(onClick = {
                    container.downloadManager.clearAllHistory()
                    container.settings.resetToDefaults()
                    scope.launch { container.mediaRepo.clearAppCache() }
                    showWipeConfirm = false
                    Toast.makeText(context, strings.wipeDataTitle, Toast.LENGTH_SHORT).show()
                }) { Text(strings.deleteBtn, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showWipeConfirm = false }) { Text(strings.cancelBtn) }
            },
        )
    }
}

@Composable
private fun ServerPrefPickerRow(
    platformLabel: String,
    servers: List<Pair<String, String>>,
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(platformLabel, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            servers.forEach { (id, name) ->
                FilterChip(
                    selected = selectedId == id,
                    onClick = { onSelect(id) },
                    label = { Text(name, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
    }
}

@Composable
private fun SettingsPageHeader(
    title: String,
    showBack: Boolean,
    onBack: () -> Unit,
) {
    val style = LocalNimiyoStyle.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (showBack) 8.dp else 20.dp,
                end = 20.dp,
                top = if (showBack) 12.dp else 22.dp,
                bottom = if (showBack) 14.dp else 18.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = null,
                    tint = if (style.isCupertino) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = if (showBack) title else title.uppercase(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SettingsMenuCard(
    icon: ImageVector,
    title: String,
    desc: String,
    onClick: () -> Unit,
) {
    val style = LocalNimiyoStyle.current
    val cardShape = remember(style.cornerRadius) { RoundedCornerShape(style.cornerRadius) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .clickable(onClick = onClick),
        shape = cardShape,
        color = style.cardSurface,
        border = style.cardBorder,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = desc,
                    fontSize = 13.5.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SettingSectionTag(title: String) {
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 6.dp, top = 4.dp),
    )
}

@Composable
private fun SettingFolderRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isCustom: Boolean,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onPick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                fontSize = 16.5.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        if (isCustom) {
            IconButton(
                onClick = onClear,
                modifier = Modifier.size(34.dp),
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        IconButton(
            onClick = onPick,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                Icons.Rounded.MoveToInbox,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun SettingSwitchRow(
    label: String,
    checked: Boolean,
    icon: ImageVector? = null,
    desc: String? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    val style = LocalNimiyoStyle.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(14.dp))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!desc.isNullOrBlank()) {
                Text(
                    text = desc,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (style.isCupertino) {
            val trackColor by animateColorAsState(
                targetValue = if (checked) Color(0xFF34C759) else if (style.isDark) Color(0xFF39393D) else Color(0xFFE9E9EA),
                animationSpec = tween(180),
                label = "IosSwitchTrack",
            )
            val thumbOffset by animateDpAsState(
                targetValue = if (checked) 22.dp else 2.dp,
                animationSpec = tween(180),
                label = "IosSwitchThumb",
            )
            Box(
                modifier = Modifier
                    .size(width = 50.dp, height = 30.dp)
                    .clip(CircleShape)
                    .background(trackColor)
                    .clickable { onCheckedChange(!checked) },
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(
                    modifier = Modifier
                        .padding(start = thumbOffset)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                )
            }
        } else {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
fun NimiyoRulesContent(strings: com.nimiyo.nativeapp.core.Strings = com.nimiyo.nativeapp.core.StringsI18n.get(AppLanguage.SYSTEM)) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = strings.rulesIntro,
            style = MaterialTheme.typography.bodySmall,
            lineHeight = 19.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Box 1: DILARANG KERAS
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.42f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.error,
                ) {
                    Text(
                        text = strings.strictlyProhibitedBadge,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onError,
                    )
                }
                Text(
                    text = strings.prohibitedContentTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                )
                RuleBulletItem(
                    label = strings.rulePornLabel,
                    detail = strings.rulePornDetail,
                )
                RuleBulletItem(
                    label = strings.ruleViolenceLabel,
                    detail = strings.ruleViolenceDetail,
                )
                RuleBulletItem(
                    label = strings.ruleHateLabel,
                    detail = strings.ruleHateDetail,
                )
                RuleBulletItem(
                    label = strings.ruleIllegalLabel,
                    detail = strings.ruleIllegalDetail,
                )
            }
        }

        // Box 2: KETENTUAN & HAK CIPTA
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        text = strings.termsCopyrightBadge,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Text(
                    text = strings.copyrightDisclaimerTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                RuleBulletItem(
                    label = strings.ruleArchiveLabel,
                    detail = strings.ruleArchiveDetail,
                )
                RuleBulletItem(
                    label = strings.ruleCreatorLabel,
                    detail = strings.ruleCreatorDetail,
                )
                RuleBulletItem(
                    label = strings.ruleUserRespLabel,
                    detail = strings.ruleUserRespDetail,
                )
            }
        }
    }
}

@Composable
private fun RuleBulletItem(
    label: String,
    detail: String,
) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append("• $label: ")
            }
            append(detail)
        },
        style = MaterialTheme.typography.bodySmall,
        lineHeight = 18.sp,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
fun NimiyoRulesStartupDialog(
    strings: com.nimiyo.nativeapp.core.Strings = com.nimiyo.nativeapp.core.StringsI18n.get(AppLanguage.SYSTEM),
    onAccept: () -> Unit,
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 640.dp),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(38.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.Gavel,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = strings.termsTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            text = strings.termsSubtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                ) {
                    NimiyoRulesContent(strings)
                }

                Button(
                    onClick = onAccept,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(
                        text = strings.agreeUnderstandBtn,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
    }
}
