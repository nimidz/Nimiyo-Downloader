package com.nimiyo.nativeapp.data

import androidx.compose.runtime.Immutable

enum class MediaCategory {
    VIDEO, AUDIO, IMAGE
}

enum class PlatformId(val id: String, val displayName: String, val servers: List<Pair<String, String>>) {
    YOUTUBE("youtube", "YouTube", listOf("ytdlp" to "yt-dlp + FFmpeg")),
    TIKTOK("tiktok", "TikTok", listOf("ytdlp" to "yt-dlp + FFmpeg")),
    INSTAGRAM("instagram", "Instagram", listOf("ytdlp" to "yt-dlp + FFmpeg")),
    SPOTIFY("spotify", "Spotify", listOf("ytdlp" to "yt-dlp + FFmpeg")),
    SOUNDCLOUD("soundcloud", "SoundCloud", listOf("ytdlp" to "yt-dlp + FFmpeg")),
    TWITTER("twitter", "X / Twitter", listOf("ytdlp" to "yt-dlp + FFmpeg")),
    FACEBOOK("facebook", "Facebook", listOf("ytdlp" to "yt-dlp + FFmpeg")),
    PINTEREST("pinterest", "Pinterest", listOf("ytdlp" to "yt-dlp + FFmpeg")),
    THREADS("threads", "Threads", listOf("ytdlp" to "yt-dlp + FFmpeg")),
    BILIBILI("bilibili", "Bilibili", listOf("ytdlp" to "yt-dlp + FFmpeg")),
    DOUYIN("douyin", "Douyin", listOf("ytdlp" to "yt-dlp + FFmpeg")),
    PIXIV("pixiv", "Pixiv", listOf("ytdlp" to "yt-dlp + FFmpeg")),
    REDNOTE("rednote", "RedNote / XHS", listOf("ytdlp" to "yt-dlp + FFmpeg")),
    APPLE_MUSIC("applemusic", "Apple Music", listOf("ytdlp" to "yt-dlp + FFmpeg")),
    BANDCAMP("bandcamp", "Bandcamp", listOf("ytdlp" to "yt-dlp + FFmpeg")),
    SHOPEE("shopee", "Shopee Video", listOf("ytdlp" to "yt-dlp + FFmpeg")),
    OTHER("web", "Web (yt-dlp)", listOf("ytdlp" to "yt-dlp + FFmpeg"));

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
    val ytdlpFormatSelector: String = "",
    val ytdlpAudioFormat: String = "",
    val ytdlpAudioQuality: String = "320K",
    val ytdlpContainer: String = "mp4",
    val ytdlpTargetUrl: String = "",
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
    val extractorName: String = "",
    val isPlaylist: Boolean = false,
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
    val ytdlpFormatSelector: String = "",
    val ytdlpAudioFormat: String = "",
    val ytdlpAudioQuality: String = "320K",
    val ytdlpContainer: String = "mp4",
    val ytdlpTargetUrl: String = "",
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

