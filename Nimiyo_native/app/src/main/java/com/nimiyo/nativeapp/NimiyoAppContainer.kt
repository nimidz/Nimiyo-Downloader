package com.nimiyo.nativeapp

import android.content.Context
import androidx.compose.runtime.Stable
import coil.Coil
import coil.ImageLoader
import com.nimiyo.nativeapp.core.AppSettings
import com.nimiyo.nativeapp.core.CookieVaultManager
import com.nimiyo.nativeapp.core.HttpClientFactory
import com.nimiyo.nativeapp.data.MediaStoreRepository
import com.nimiyo.nativeapp.download.DownloadQueueManager
import com.nimiyo.nativeapp.download.NotificationHelper
import com.nimiyo.nativeapp.download.YtDlpEngine
import com.nimiyo.nativeapp.player.AudioArtworkFetcher
import com.nimiyo.nativeapp.player.LyricsRepository
import com.nimiyo.nativeapp.player.PlayerController
import com.nimiyo.nativeapp.scraper.ScraperEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Stable
class NimiyoAppContainer private constructor(context: Context) {
    val appContext: Context = context.applicationContext
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        Coil.setImageLoader {
            ImageLoader.Builder(appContext)
                .components {
                    add(AudioArtworkFetcher.Factory(appContext))
                }
                .build()
        }
    }

    val settings = AppSettings(appContext)
    val cookieVault = CookieVaultManager(appContext)
    val httpFactory = HttpClientFactory(settings) { url ->
        cookieVault.cookieHeaderForUrl(url, settings.current.cookiesEnabled)
    }
    val mediaRepo = MediaStoreRepository(appContext)
    val ytDlpEngine = YtDlpEngine(appContext, settings, httpFactory, mediaRepo, cookieVault)
    val scraper = ScraperEngine(settings, httpFactory, ytDlpEngine)
    val notifHelper = NotificationHelper(appContext)
    val downloadManager = DownloadQueueManager(appContext, settings, mediaRepo, scraper, ytDlpEngine, notifHelper)
    val lyricsRepo = LyricsRepository(httpFactory, mediaRepo)
    val playerController = PlayerController(appContext, mediaRepo, lyricsRepo)

    init {
        appScope.launch {
            ytDlpEngine.refreshVersion()
            launch {
                runCatching { ytDlpEngine.ensureMediaToolsInitialized() }
            }
            val autoClearDays = settings.current.autoClearCacheDays
            if (autoClearDays > 0) {
                launch(Dispatchers.IO) {
                    runCatching {
                        val cutoffMs = System.currentTimeMillis() - autoClearDays * 86_400_000L
                        appContext.cacheDir.listFiles()?.forEach { f ->
                            if (f.lastModified() in 1L until cutoffMs) {
                                f.deleteRecursively()
                            }
                        }
                    }
                }
            }
            if (settings.current.autoUpdateYtDlp) {
                ytDlpEngine.updateYtDlp(settings.current.ytDlpChannel)
            }
        }
    }

    private val _detectedClipboardUrl = MutableStateFlow<String?>(null)
    val detectedClipboardUrl: StateFlow<String?> = _detectedClipboardUrl.asStateFlow()
    private var lastHandledClipboardUrl: String = ""

    fun notifyClipboardCandidate(url: String) {
        val clean = url.trim()
        if (clean.isNotBlank() && clean != lastHandledClipboardUrl) {
            lastHandledClipboardUrl = clean
            _detectedClipboardUrl.value = clean
        }
    }

    fun consumeClipboardCandidate() {
        _detectedClipboardUrl.value = null
    }

    companion object {
        @Volatile
        private var instance: NimiyoAppContainer? = null

        fun get(context: Context): NimiyoAppContainer =
            instance ?: synchronized(this) {
                instance ?: NimiyoAppContainer(context).also { instance = it }
            }
    }
}
