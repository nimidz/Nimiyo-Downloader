package com.nimiyo.nativeapp.scraper

import com.nimiyo.nativeapp.data.PlatformId
import java.net.URI
import java.util.Locale

object PlatformDetector {
    private val urlExtractRegex = Regex("""https?://[^\s<>"'\])]+""", RegexOption.IGNORE_CASE)

    fun extractFirstUrl(text: String): String? =
        urlExtractRegex.find(text.trim())?.value?.trimEnd('.', ',', ';', ')', ']')

    fun isHttpUrl(rawUrl: String): Boolean {
        val u = rawUrl.trim().lowercase(Locale.US)
        return u.startsWith("http://") || u.startsWith("https://")
    }

    private fun extractHost(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        val parsed = runCatching { URI(trimmed).host }.getOrNull()
        val raw = parsed ?: Regex("""^https?://([^/:?#]+)""", RegexOption.IGNORE_CASE)
            .find(trimmed)?.groupValues?.getOrNull(1).orEmpty()
        return raw.trim().lowercase(Locale.US).removePrefix(".")
    }

    private fun hostMatches(host: String, domain: String): Boolean =
        host == domain || host.endsWith(".$domain")

    private fun hostMatchesPrefix(host: String, prefix: String): Boolean =
        host.startsWith(prefix) || host.contains(".$prefix")

    fun detectKnownPlatform(rawUrl: String): PlatformId? {
        val host = extractHost(rawUrl)
        if (host.isBlank()) return null
        return when {
            hostMatches(host, "youtube.com") || hostMatches(host, "youtu.be") -> PlatformId.YOUTUBE
            hostMatches(host, "tiktok.com") || hostMatches(host, "vm.tiktok.com") || hostMatches(host, "vt.tiktok.com") -> PlatformId.TIKTOK
            hostMatches(host, "instagram.com") || hostMatches(host, "instagr.am") -> PlatformId.INSTAGRAM
            hostMatches(host, "spotify.com") || hostMatches(host, "spotify.link") -> PlatformId.SPOTIFY
            hostMatches(host, "soundcloud.com") || hostMatches(host, "on.soundcloud.com") -> PlatformId.SOUNDCLOUD
            hostMatches(host, "twitter.com") || hostMatches(host, "x.com") ||
                hostMatches(host, "fxtwitter.com") || hostMatches(host, "vxtwitter.com") -> PlatformId.TWITTER
            hostMatches(host, "facebook.com") || hostMatches(host, "fb.watch") || hostMatches(host, "fb.com") -> PlatformId.FACEBOOK
            hostMatchesPrefix(host, "pinterest.") || hostMatches(host, "pin.it") -> PlatformId.PINTEREST
            hostMatches(host, "threads.net") || hostMatches(host, "threads.com") -> PlatformId.THREADS
            hostMatchesPrefix(host, "bilibili.") || hostMatches(host, "b23.tv") || hostMatches(host, "bili.im") -> PlatformId.BILIBILI
            hostMatches(host, "douyin.com") || hostMatches(host, "iesdouyin.com") -> PlatformId.DOUYIN
            hostMatches(host, "pixiv.net") -> PlatformId.PIXIV
            hostMatches(host, "xiaohongshu.com") || hostMatches(host, "xhslink.com") ||
                hostMatches(host, "rednote.com") || hostMatches(host, "rednoteshare.com") -> PlatformId.REDNOTE
            hostMatches(host, "music.apple.com") -> PlatformId.APPLE_MUSIC
            hostMatches(host, "bandcamp.com") -> PlatformId.BANDCAMP
            hostMatchesPrefix(host, "shopee.") || hostMatches(host, "shp.ee") || hostMatches(host, "s.shopee.") -> PlatformId.SHOPEE
            else -> null
        }
    }

    fun isAutoDetectableMediaUrl(rawUrl: String): Boolean {
        if (!isHttpUrl(rawUrl)) return false
        return detectKnownPlatform(rawUrl) != null
    }

    fun detect(rawUrl: String): PlatformId? {
        val u = rawUrl.trim()
        if (u.isBlank()) return null
        return detectKnownPlatform(u)
    }
}
