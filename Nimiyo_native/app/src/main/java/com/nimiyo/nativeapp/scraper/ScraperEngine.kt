package com.nimiyo.nativeapp.scraper

import com.nimiyo.nativeapp.core.AppSettings
import com.nimiyo.nativeapp.core.HttpClientFactory
import com.nimiyo.nativeapp.data.PlatformId
import com.nimiyo.nativeapp.data.ScrapeResult
import com.nimiyo.nativeapp.download.YtDlpEngine
import java.util.UUID

class ScraperEngine(
    private val settings: AppSettings,
    val http: HttpClientFactory,
    private val ytDlpEngine: YtDlpEngine,
) {
    suspend fun analyze(
        rawUrl: String,
        overridePlatform: PlatformId? = null,
        @Suppress("UNUSED_PARAMETER") overrideServer: String? = null,
        processId: String = "analyze_${UUID.randomUUID()}",
    ): ScrapeResult {
        return ytDlpEngine.analyze(
            rawUrl = rawUrl,
            overridePlatform = overridePlatform,
            processId = processId,
        )
    }

    fun cancelAnalyze(processId: String) {
        ytDlpEngine.cancelProcess(processId)
    }

    suspend fun resolveLazyDownloadUrl(rawUrl: String): String = rawUrl
}
