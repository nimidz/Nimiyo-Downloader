package com.nimiyo.nativeapp

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nimiyo.nativeapp.core.AppSettings
import com.nimiyo.nativeapp.core.HttpClientFactory
import com.nimiyo.nativeapp.data.PlatformId
import com.nimiyo.nativeapp.scraper.ScraperEngine
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URI
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class AllServersInstrumentedTest {
    private val tag = "NIMIYO_SERVER_TEST"

    private data class ProbeCase(
        val platform: PlatformId,
        val url: String,
    )

    private val cases = listOf(
        ProbeCase(PlatformId.YOUTUBE, "https://www.youtube.com/watch?v=jNQXAC9IVRw"),
        ProbeCase(PlatformId.TIKTOK, "https://www.tiktok.com/@tiktok/video/7106594312292453675"),
        ProbeCase(PlatformId.INSTAGRAM, "https://www.instagram.com/reel/C8x1Z0qS1X2/"),
        ProbeCase(PlatformId.SPOTIFY, "https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT"),
        ProbeCase(PlatformId.SOUNDCLOUD, "https://soundcloud.com/alanwalker/faded-slushii-remix-1"),
        ProbeCase(PlatformId.TWITTER, "https://x.com/SpaceX/status/1845442652093768104"),
        ProbeCase(PlatformId.FACEBOOK, "https://www.facebook.com/watch/?v=10153231379946729"),
        ProbeCase(PlatformId.PINTEREST, "https://www.pinterest.com/pin/99360735500167749/"),
        ProbeCase(PlatformId.THREADS, "https://www.threads.net/@zuck/post/CuV4y2hR2gR"),
        ProbeCase(PlatformId.BILIBILI, "https://www.bilibili.com/video/BV1GJ411x7h7"),
        ProbeCase(PlatformId.DOUYIN, "https://www.douyin.com/video/7150000000000000000"),
        ProbeCase(PlatformId.PIXIV, "https://www.pixiv.net/en/artworks/83384514"),
        ProbeCase(PlatformId.REDNOTE, "https://www.xiaohongshu.com/explore/65000000000000001e000000"),
        ProbeCase(PlatformId.APPLE_MUSIC, "https://music.apple.com/us/album/never-gonna-give-you-up/1773292758?i=1773293184"),
        ProbeCase(PlatformId.BANDCAMP, "https://c418.bandcamp.com/track/sweden"),
        ProbeCase(PlatformId.SHOPEE, "https://sv.shopee.co.id/share-video/0"),
    )

    @Test
    fun testAllPlatformsAndServers() {
        runBlocking {
            val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val settings = AppSettings(ctx)
        val http = HttpClientFactory(settings)
        val engine = ScraperEngine(settings, http)

        Log.i(tag, "=== STARTING ALL-SERVER PROBE (${cases.size} platforms) ===")

        for (c in cases) {
            val servers = c.platform.servers
            for ((srvId, srvName) in servers) {
                if (srvId == "auto") continue
                val start = System.currentTimeMillis()
                val res = runCatching {
                    engine.analyze(c.url, c.platform, srvId)
                }
                val elapsed = System.currentTimeMillis() - start
                if (res.isFailure) {
                    Log.i(tag, "[FAIL] ${c.platform.id}/$srvId ($srvName) | ${elapsed}ms | ERR=${res.exceptionOrNull()?.message}")
                    continue
                }
                val sr = res.getOrThrow()
                val usedMatched = sr.serverUsed.equals(srvName, ignoreCase = true) ||
                    sr.serverUsed.contains(srvName, ignoreCase = true) ||
                    sr.serverUsed.contains(srvId, ignoreCase = true) ||
                    servers.size == 1

                val firstItem = sr.items.firstOrNull()
                if (firstItem == null) {
                    Log.i(tag, "[EMPTY] ${c.platform.id}/$srvId | used=${sr.serverUsed} | ${elapsed}ms")
                    continue
                }

                // Resolve lazy URL if needed and probe HTTP downloadability (Range 0-512)
                val dlCheck = runCatching {
                    val resolved = engine.resolveLazyDownloadUrl(firstItem.url)
                    probeDownloadUrl(http, resolved, firstItem.referer)
                }
                val dlStatus = dlCheck.getOrElse { "DL_ERR:${it.message}" }
                val tagStatus = if (usedMatched) "OK" else "FALLBACK->${sr.serverUsed}"
                Log.i(
                    tag,
                    "[$tagStatus] ${c.platform.id}/$srvId | used=${sr.serverUsed} | items=${sr.items.size} | title=${sr.title.take(35)} | $dlStatus | ${elapsed}ms"
                )
            }
        }
        Log.i(tag, "=== FINISHED ALL-SERVER PROBE ===")
        }
    }

    private fun probeDownloadUrl(http: HttpClientFactory, url: String, initialReferer: String): String {
        val candidates = mutableListOf(http.effectiveUserAgent() to initialReferer)
        val lower = url.lowercase(Locale.US)
        if (lower.contains("tiktok") || lower.contains("muscdn") || lower.contains("byteoversea")) {
            candidates.add(HttpClientFactory.CHROME_DESKTOP_UA to "https://www.tiktok.com/")
        }
        if (initialReferer.isNotBlank()) candidates.add(HttpClientFactory.CHROME_DESKTOP_UA to "")
        extractOrigin(url)?.let { candidates.add(HttpClientFactory.CHROME_DESKTOP_UA to "$it/") }
        candidates.add(HttpClientFactory.SAFARI_MOBILE_UA to "")

        var lastCode = 0
        for ((ua, ref) in candidates.distinct()) {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", ua)
                .header("Accept", "*/*")
                .header("Range", "bytes=0-512")
            if (ref.isNotBlank()) {
                req.header("Referer", ref)
                extractOrigin(ref)?.let { req.header("Origin", it) }
            }
            http.downloadClient().newCall(req.build()).execute().use { r ->
                lastCode = r.code
                if (r.isSuccessful) {
                    val ctype = r.header("Content-Type") ?: "?"
                    return "DL_HTTP_${r.code}($ctype)"
                }
                if (r.code != 403 && r.code != 401) {
                    return "DL_HTTP_${r.code}"
                }
            }
        }
        return "DL_HTTP_${lastCode}_ALL_BLOCKED"
    }

    private fun extractOrigin(url: String): String? = runCatching {
        val u = URI(url)
        if (!u.scheme.isNullOrBlank() && !u.host.isNullOrBlank()) "${u.scheme}://${u.host}" else null
    }.getOrNull()
}

