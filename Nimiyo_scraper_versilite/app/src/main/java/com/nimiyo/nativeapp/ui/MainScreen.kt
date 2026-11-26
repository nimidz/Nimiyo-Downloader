package com.nimiyo.nativeapp.ui

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nimiyo.nativeapp.NimiyoAppContainer
import com.nimiyo.nativeapp.core.ReleaseInfo
import com.nimiyo.nativeapp.core.StartupPage
import com.nimiyo.nativeapp.core.StringsI18n
import com.nimiyo.nativeapp.core.UpdateCheckResult
import com.nimiyo.nativeapp.core.UpdateChecker
import com.nimiyo.nativeapp.data.ScrapeResult
import com.nimiyo.nativeapp.ui.components.DownloadQueueSheet
import com.nimiyo.nativeapp.ui.components.FloatingDownloadBalloon
import com.nimiyo.nativeapp.ui.components.MiniPlayerBar
import com.nimiyo.nativeapp.ui.components.NimiyoUpdateDialog
import com.nimiyo.nativeapp.ui.components.UserGuideDialog
import com.nimiyo.nativeapp.ui.screens.HistoryScreen
import com.nimiyo.nativeapp.ui.screens.HomeScreen
import com.nimiyo.nativeapp.ui.screens.NimiyoRulesStartupDialog
import com.nimiyo.nativeapp.ui.screens.NowPlayingSheet
import com.nimiyo.nativeapp.ui.screens.PlayerScreen
import com.nimiyo.nativeapp.ui.screens.SettingsScreen
import com.nimiyo.nativeapp.ui.theme.LocalNimiyoStyle

@Composable
fun MainScreen(
    container: NimiyoAppContainer,
    initialSharedUrl: String = "",
    forceOpenPlayer: Boolean = false,
    openQuickSaveSettingsTrigger: Int = 0,
) {
    val settingsState by container.settings.state.collectAsState()
    val strings = StringsI18n.get(settingsState.language)
    val downloadTasks by container.downloadManager.tasks.collectAsState()
    val downloadError by container.downloadManager.latestDownloadError.collectAsState()
    val balloonExpanded by container.downloadManager.balloonExpanded.collectAsState()
    val playerState by container.playerController.state.collectAsState()

    val startIdx = remember(forceOpenPlayer) {
        if (forceOpenPlayer) 1 else when (settingsState.startupPage) {
            StartupPage.HOME -> 0
            StartupPage.PLAYER -> 1
            StartupPage.HISTORY -> 2
        }
    }
    var selectedNav by remember { mutableIntStateOf(startIdx) }
    var navHistory by remember { mutableStateOf(listOf(startIdx)) }
    var homeUrlInput by remember(initialSharedUrl) { mutableStateOf(initialSharedUrl) }
    var homeScrapeResult by remember { mutableStateOf<ScrapeResult?>(null) }
    var userGuideClosedThisSession by remember { mutableStateOf(false) }
    var lastBackPressMs by remember { mutableLongStateOf(0L) }
    val context = LocalContext.current

    fun switchTab(target: Int) {
        if (target == selectedNav) return
        selectedNav = target
        navHistory = (navHistory.filterNot { it == target } + target)
    }

    LaunchedEffect(openQuickSaveSettingsTrigger) {
        if (openQuickSaveSettingsTrigger > 0) {
            switchTab(3)
        }
    }

    // Top-level system BackHandler: close sheets -> pop tab history -> return to Home -> confirm exit
    BackHandler {
        when {
            balloonExpanded -> container.downloadManager.setBalloonExpanded(false)
            navHistory.size > 1 -> {
                val popped = navHistory.dropLast(1)
                navHistory = popped
                selectedNav = popped.last()
            }
            selectedNav != 0 -> {
                selectedNav = 0
                navHistory = listOf(0)
            }
            else -> {
                val now = System.currentTimeMillis()
                if (now - lastBackPressMs < 2000L) {
                    (context as? Activity)?.finish()
                } else {
                    lastBackPressMs = now
                    Toast.makeText(context, strings.ui.pressBackAgainExit, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val style = LocalNimiyoStyle.current
    val rootBgMod = if (style.isGlass && style.backdropBrush != null) {
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .background(style.backdropBrush)
    } else {
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    }
    Box(modifier = rootBgMod) {
        Scaffold(
            containerColor = if (style.isGlass) Color.Transparent else MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        bottomBar = {
            Column {
                val navBarShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                val navItemColors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    indicatorColor = if (style.isGlass) MaterialTheme.colorScheme.primary.copy(alpha = 0.24f) else MaterialTheme.colorScheme.primaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (style.isCupertino) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = style.navBarBg,
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
                            HorizontalDivider(thickness = 0.5.dp, color = style.cupertinoDividerColor)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val tabs = listOf(
                                    Triple(0, Icons.Rounded.Home, strings.navHome),
                                    Triple(1, Icons.Rounded.MusicNote, strings.navPlayer),
                                    Triple(2, Icons.Rounded.History, strings.navHistory),
                                    Triple(3, Icons.Rounded.Settings, strings.navSettings),
                                )
                                tabs.forEach { (idx, icon, label) ->
                                    val selected = selectedNav == idx
                                    val tint = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        Color(0xFF8E8E93)
                                    }
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxSize()
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                onClick = { switchTab(idx) },
                                            ),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                    ) {
                                        Icon(
                                            icon,
                                            contentDescription = label,
                                            tint = tint,
                                            modifier = Modifier.size(23.dp),
                                        )
                                        Text(
                                            text = label,
                                            color = tint,
                                            fontSize = 10.sp,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else if (style.isGlass && style.glassBrush != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(navBarShape),
                        shape = navBarShape,
                        color = Color.Transparent,
                        border = style.cardBorder,
                    ) {
                        NavigationBar(
                            containerColor = Color.Transparent,
                            modifier = Modifier.background(style.glassBrush),
                        ) {
                            NavigationBarItem(
                                selected = selectedNav == 0,
                                onClick = { switchTab(0) },
                                icon = { Icon(Icons.Rounded.Home, contentDescription = null) },
                                label = { Text(strings.navHome) },
                                colors = navItemColors,
                            )
                            NavigationBarItem(
                                selected = selectedNav == 1,
                                onClick = { switchTab(1) },
                                icon = { Icon(Icons.Rounded.MusicNote, contentDescription = null) },
                                label = { Text(strings.navPlayer) },
                                colors = navItemColors,
                            )
                            NavigationBarItem(
                                selected = selectedNav == 2,
                                onClick = { switchTab(2) },
                                icon = { Icon(Icons.Rounded.History, contentDescription = null) },
                                label = { Text(strings.navHistory) },
                                colors = navItemColors,
                            )
                            NavigationBarItem(
                                selected = selectedNav == 3,
                                onClick = { switchTab(3) },
                                icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                                label = { Text(strings.navSettings) },
                                colors = navItemColors,
                            )
                        }
                    }
                } else {
                    NavigationBar(
                        containerColor = if (style.navBarBg == Color.Unspecified) MaterialTheme.colorScheme.surfaceContainer else style.navBarBg,
                    ) {
                        NavigationBarItem(
                            selected = selectedNav == 0,
                            onClick = { switchTab(0) },
                            icon = { Icon(Icons.Rounded.Home, contentDescription = null) },
                            label = { Text(strings.navHome) },
                            colors = navItemColors,
                        )
                        NavigationBarItem(
                            selected = selectedNav == 1,
                            onClick = { switchTab(1) },
                            icon = { Icon(Icons.Rounded.MusicNote, contentDescription = null) },
                            label = { Text(strings.navPlayer) },
                            colors = navItemColors,
                        )
                        NavigationBarItem(
                            selected = selectedNav == 2,
                            onClick = { switchTab(2) },
                            icon = { Icon(Icons.Rounded.History, contentDescription = null) },
                            label = { Text(strings.navHistory) },
                            colors = navItemColors,
                        )
                        NavigationBarItem(
                            selected = selectedNav == 3,
                            onClick = { switchTab(3) },
                            icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                            label = { Text(strings.navSettings) },
                            colors = navItemColors,
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Keep all 4 primary tabs mounted after a brief staggered pre-warm so tab switches are 0ms GPU crossfades
            var mountedTabs by remember { mutableStateOf(setOf(startIdx)) }
            LaunchedEffect(Unit) {
                for (tab in 0..3) {
                    if (!mountedTabs.contains(tab)) {
                        delay(90)
                        mountedTabs = mountedTabs + tab
                    }
                }
            }
            LaunchedEffect(selectedNav) {
                if (!mountedTabs.contains(selectedNav)) {
                    mountedTabs = mountedTabs + selectedNav
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                for (tabIdx in 0..3) {
                    if (mountedTabs.contains(tabIdx)) {
                        val isSelected = selectedNav == tabIdx
                        val tabAlpha by animateFloatAsState(
                            targetValue = if (isSelected) 1f else 0f,
                            animationSpec = if (settingsState.transitionAnimations) {
                                tween(durationMillis = 180, easing = FastOutSlowInEasing)
                            } else {
                                snap()
                            },
                            label = "MainTabAlpha_$tabIdx",
                        )
                        if (isSelected || tabAlpha > 0.01f) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .zIndex(if (isSelected) 1f else 0f)
                                    .graphicsLayer { alpha = tabAlpha },
                                color = if (style.isGlass) Color.Transparent else MaterialTheme.colorScheme.background,
                                contentColor = MaterialTheme.colorScheme.onBackground,
                            ) {
                                when (tabIdx) {
                                    0 -> HomeScreen(
                                        container = container,
                                        strings = strings,
                                        isActive = isSelected,
                                        urlInput = homeUrlInput,
                                        onUrlChange = { homeUrlInput = it },
                                        scrapeResult = homeScrapeResult,
                                        onScrapeResultChange = { homeScrapeResult = it },
                                        onOpenQueueSheet = { container.downloadManager.setBalloonExpanded(true) },
                                    )
                                    1 -> PlayerScreen(container = container, strings = strings, isActive = isSelected)
                                    2 -> HistoryScreen(
                                        container = container,
                                        strings = strings,
                                        isActive = isSelected,
                                        onNavigateToPlayer = { switchTab(1) },
                                    )
                                    else -> SettingsScreen(
                                        container = container,
                                        strings = strings,
                                        isActive = isSelected,
                                        openQuickSaveSettingsTrigger = openQuickSaveSettingsTrigger,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (!playerState.hideMiniPlayer) {
                MiniPlayerBar(
                    state = playerState,
                    positionFlow = container.playerController.positionMs,
                    onOpenNowPlaying = { container.playerController.setNowPlayingOpen(true) },
                    onTogglePlay = { container.playerController.togglePlayPause() },
                    onToggleMinimize = { container.playerController.setMiniPlayerMinimized(!playerState.miniPlayerMinimized) },
                    onClose = { container.playerController.stopAndClosePlayer() },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }

            FloatingDownloadBalloon(
                tasks = downloadTasks,
                onClick = { container.downloadManager.setBalloonExpanded(true) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = if (playerState.currentTrack != null && !playerState.hideMiniPlayer) 88.dp else 16.dp),
            )
        }
    }

    if (balloonExpanded) {
        DownloadQueueSheet(
            tasks = downloadTasks,
            strings = strings,
            onDismiss = { container.downloadManager.setBalloonExpanded(false) },
            onRetry = { container.downloadManager.retry(it) },
            onRetryFallback = { container.downloadManager.retryWithFallback(it) },
            onCancel = { container.downloadManager.cancel(it) },
            onCancelAll = { container.downloadManager.cancelAll(quickSaveOnly = false) },
            onClearCompleted = { container.downloadManager.clearFinished() },
        )
    }

    val activeErr = downloadError
    if (activeErr != null) {
        AlertDialog(
            onDismissRequest = { container.downloadManager.dismissDownloadError() },
            icon = {
                Icon(
                    Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            title = { Text(strings.downloadErrorTitle) },
            text = {
                Text("${activeErr.title}\n\n${activeErr.errorMessage}")
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (activeErr.canFallback) {
                            container.downloadManager.retryWithFallback(activeErr.taskId)
                        } else {
                            container.downloadManager.retry(activeErr.taskId)
                        }
                        container.downloadManager.dismissDownloadError()
                    },
                ) {
                    Text(if (activeErr.canFallback) strings.retryFallbackBtn else strings.retryBtn)
                }
            },
            dismissButton = {
                TextButton(onClick = { container.downloadManager.dismissDownloadError() }) {
                    Text(strings.closeBtn)
                }
            },
        )
    }

        AnimatedVisibility(
            visible = playerState.nowPlayingOpen,
            enter = if (settingsState.transitionAnimations) {
                fadeIn(animationSpec = tween(240, easing = FastOutSlowInEasing))
            } else {
                EnterTransition.None
            },
            exit = if (settingsState.transitionAnimations) {
                fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing))
            } else {
                ExitTransition.None
            },
        ) {
            NowPlayingSheet(
                container = container,
                strings = strings,
                onDismiss = { container.playerController.setNowPlayingOpen(false) },
            )
        }

        var availableUpdate by remember { mutableStateOf<ReleaseInfo?>(null) }

        LaunchedEffect(Unit) {
            when (val res = UpdateChecker.checkLatestRelease(container.httpFactory)) {
                is UpdateCheckResult.Available -> {
                    val now = System.currentTimeMillis()
                    val isSnoozed = now < settingsState.updateIgnoredUntilMs &&
                        res.release.versionName == settingsState.updateIgnoredVersion
                    if (!isSnoozed) {
                        availableUpdate = res.release
                    }
                }
                else -> Unit
            }
        }

        if (!settingsState.rulesAccepted) {
            NimiyoRulesStartupDialog(
                strings = strings,
                onAccept = { container.settings.update { it.copy(rulesAccepted = true) } },
            )
        } else if (!settingsState.userGuideDismissed && !userGuideClosedThisSession) {
            UserGuideDialog(
                strings = strings,
                onClose = { dontShow ->
                    if (dontShow) {
                        container.settings.update { it.copy(userGuideDismissed = true) }
                    }
                    userGuideClosedThisSession = true
                },
                onOpenSettings = { dontShow ->
                    if (dontShow) {
                        container.settings.update { it.copy(userGuideDismissed = true) }
                    }
                    userGuideClosedThisSession = true
                    switchTab(3)
                },
            )
        } else if (availableUpdate != null) {
            val rel = availableUpdate!!
            NimiyoUpdateDialog(
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
                    availableUpdate = null
                },
                onDismiss = { availableUpdate = null },
            )
        }
    }
}

