package com.nimiyo.nativeapp.data

import androidx.compose.runtime.Immutable

enum class MediaCategory {
    VIDEO, AUDIO, IMAGE
}

enum class PlatformId(val id: String, val displayName: String, val servers: List<Pair<String, String>>) {
    YOUTUBE(
        "youtube",
        "YouTube",
        listOf("auto" to "Auto (Best)", "gg" to "ytmp3.gg (Convert1s)", "mobi" to "ytmp3.mobi (YMCDN)")
    ),
    TIKTOK(
        "tiktok",
        "TikTok",
        listOf("tiktokio" to "TikTokIO", "snaptik" to "SnapTik", "ssstik" to "SSSTik", "tikdownloader" to "TikDownloader")
    ),
    INSTAGRAM(
        "instagram",
        "Instagram",
        listOf("snapsave" to "SnapSave", "indown" to "InDown", "direct" to "Direct Embed")
    ),
    SPOTIFY(
        "spotify",
        "Spotify",
        listOf("spotidown" to "SpotiDown", "soundloaders" to "Spotify (YT-GG)", "spotisaver" to "Spotify (YT-Mobi)")
    ),
    SOUNDCLOUD(
        "soundcloud",
        "SoundCloud",
        listOf("direct" to "Direct API v2", "forhub" to "ForHub", "klickaud" to "KlickAud")
    ),
    TWITTER(
        "twitter",
        "X / Twitter",
        listOf("direct" to "FxTwitter", "tweeload" to "Tweeload", "tvd" to "TwitterVid")
    ),
    FACEBOOK("facebook", "Facebook", listOf("snapsave" to "SnapSave", "direct" to "Direct OG")),
    PINTEREST("pinterest", "Pinterest", listOf("direct" to "Pinterest Direct", "pindown" to "PinDown")),
    THREADS("threads", "Threads", listOf("direct" to "Direct SSR / OG", "threadster" to "Threadster")),
    BILIBILI("bilibili", "Bilibili", listOf("direct" to "Bilibili API")),
    DOUYIN("douyin", "Douyin", listOf("direct" to "Douyin API / SSR")),
    PIXIV("pixiv", "Pixiv", listOf("direct" to "Pixiv AJAX")),
    REDNOTE("rednote", "RedNote / XHS", listOf("direct" to "RedNote SSR")),
    APPLE_MUSIC("applemusic", "Apple Music", listOf("aplmate" to "Aplmate")),
    BANDCAMP("bandcamp", "Bandcamp", listOf("bandcampdl" to "BandcampDL")),
    SHOPEE("shopee", "Shopee Video", listOf("direct" to "Shopee Direct", "svxtract" to "SVXtract"));

    companion object {
        fun fromId(id: String): PlatformId? = entries.firstOrNull { it.id == id }
    }
}

@Immutable
data class ScraperDownloadItem(
    val id: String,
    val title: String,
    val label: String,
    val url: String,
    val category: MediaCategory,
    val extension: String,
    val thumbnail: String = "",
    val author: String = "",
    val durationMs: Long = 0L,
    val referer: String = "",
)

@Immutable
data class ScrapeResult(
    val platform: PlatformId,
    val serverUsed: String,
    val title: String,
    val author: String = "",
    val thumbnail: String = "",
    val sourceUrl: String,
    val items: List<ScraperDownloadItem>,
)

enum class DownloadStatus {
    QUEUED, RESOLVING, DOWNLOADING, COMPLETED, FAILED, CANCELLED
}

@Immutable
data class DownloadTask(
    val id: String,
    val title: String,
    val url: String,
    val filename: String,
    val extension: String,
    val category: MediaCategory,
    val platform: PlatformId,
    val thumbnail: String = "",
    val author: String = "",
    val referer: String = "",
    val sourceUrl: String = "",
    val serverId: String = "",
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val progress: Int = 0,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = -1L,
    val localPath: String = "",
    val contentUri: String = "",
    val errorMessage: String = "",
    val fromQuickSave: Boolean = false,
)

@Immutable
data class HistoryRecord(
    val id: String,
    val title: String,
    val filename: String,
    val category: MediaCategory,
    val platform: String,
    val localPath: String,
    val contentUri: String = "",
    val thumbnail: String = "",
    val sourceUrl: String = "",
    val sizeBytes: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
)

@Immutable
data class AudioTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val path: String,
    val contentUri: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val dateAddedSec: Long,
    val isNimiyoFolder: Boolean,
    val albumArtUri: String = "",
    val hasLrc: Boolean = false,
)

@Immutable
data class PlaylistModel(
    val id: String,
    val name: String,
    val trackPaths: List<String>,
    val updatedAt: Long = System.currentTimeMillis(),
)

