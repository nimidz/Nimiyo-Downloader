package com.nimiyo.nativeapp

import android.Manifest
import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.nimiyo.nativeapp.core.DarkModePref
import com.nimiyo.nativeapp.download.NotificationHelper
import com.nimiyo.nativeapp.scraper.PlatformDetector
import com.nimiyo.nativeapp.ui.MainScreen
import com.nimiyo.nativeapp.ui.theme.NimiyoTheme

class MainActivity : ComponentActivity() {
    private lateinit var container: NimiyoAppContainer
    private var openPlayerState by mutableStateOf(false)
    private var openQuickSaveSettingsTrigger by mutableStateOf(0)
    private var needsClipboardCheckOnFocus: Boolean = true

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        container.playerController.refreshLibrary()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lockHighestDisplayRefreshRate()
        container = NimiyoAppContainer.get(this)
        requestNecessaryPermissions()
        handleIntentFlags(intent)
        openPlayerState = intent?.getBooleanExtra(NotificationHelper.EXTRA_OPEN_PLAYER, false) == true

        setContent {
            val settings by container.settings.state.collectAsState()
            val sysDark = isSystemInDarkTheme()
            val isDark = when (settings.darkMode) {
                DarkModePref.DARK -> true
                DarkModePref.LIGHT -> false
                DarkModePref.SYSTEM -> sysDark
            }
            LaunchedEffect(isDark) {
                val style = if (isDark) {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            LaunchedEffect(settings.keepScreenAwake) {
                if (settings.keepScreenAwake) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
            NimiyoTheme(
                uiStyle = settings.uiStyle,
                darkModePref = settings.darkMode,
                accentColor = settings.accentColor,
            ) {
                MainScreen(
                    container = container,
                    forceOpenPlayer = openPlayerState,
                    openQuickSaveSettingsTrigger = openQuickSaveSettingsTrigger,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        needsClipboardCheckOnFocus = true
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            lockHighestDisplayRefreshRate()
        }
        if (hasFocus && needsClipboardCheckOnFocus && ::container.isInitialized && container.settings.current.clipboardAutoDetect) {
            needsClipboardCheckOnFocus = false
            runCatching {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clipText = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
                val url = PlatformDetector.extractFirstUrl(clipText)
                if (!url.isNullOrBlank() && PlatformDetector.isAutoDetectableMediaUrl(url)) {
                    container.notifyClipboardCandidate(url)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(NotificationHelper.EXTRA_OPEN_PLAYER, false)) {
            openPlayerState = true
        }
        handleIntentFlags(intent)
    }

    private fun handleIntentFlags(intent: Intent?) {
        if (intent?.getBooleanExtra(NotificationHelper.EXTRA_OPEN_BALLOON, false) == true) {
            container.downloadManager.setBalloonExpanded(true)
        }
        if (intent?.getBooleanExtra(NotificationHelper.EXTRA_OPEN_PLAYER, false) == true) {
            container.playerController.setNowPlayingOpen(true)
        }
        if (intent?.getBooleanExtra("open_quicksave_settings", false) == true) {
            openQuickSaveSettingsTrigger++
        }
    }

    private fun requestNecessaryPermissions() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
            perms.add(Manifest.permission.READ_MEDIA_AUDIO)
            perms.add(Manifest.permission.READ_MEDIA_VIDEO)
            perms.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permLauncher.launch(missing.toTypedArray())
        }
    }
}

fun Activity.lockHighestDisplayRefreshRate() {
    runCatching {
        val targetDisplay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay
        } ?: return
        val bestMode = targetDisplay.supportedModes.maxByOrNull { it.refreshRate } ?: return
        val params = window.attributes
        var changed = false
        if (params.preferredDisplayModeId != bestMode.modeId) {
            params.preferredDisplayModeId = bestMode.modeId
            changed = true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && params.preferredRefreshRate != bestMode.refreshRate) {
            params.preferredRefreshRate = bestMode.refreshRate
            changed = true
        }
        if (changed) {
            window.attributes = params
        }
    }
}

