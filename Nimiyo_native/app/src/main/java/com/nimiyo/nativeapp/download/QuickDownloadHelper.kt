package com.nimiyo.nativeapp.download

import com.nimiyo.nativeapp.core.AudioQualityPref
import com.nimiyo.nativeapp.core.BatchPhotoMode
import com.nimiyo.nativeapp.core.QuickMediaType
import com.nimiyo.nativeapp.core.SettingsState
import com.nimiyo.nativeapp.core.buildQuickVideoFormatSelector
import com.nimiyo.nativeapp.data.MediaCategory
import com.nimiyo.nativeapp.data.ScrapeResult
import com.nimiyo.nativeapp.data.ScraperDownloadItem

/**
 * Resolves the concrete [ScraperDownloadItem] list for a Quick download according to the
 * user's selected [QuickMediaType], codec, resolution, audio format, and batch photo preference.
 */
fun buildQuickDownloadItems(
    result: ScrapeResult,
    settings: SettingsState,
): List<ScraperDownloadItem> {
    if (result.items.isEmpty()) return emptyList()
    return when (settings.quickMediaType) {
        QuickMediaType.PHOTO -> {
            val photos = result.items.filter { it.category == MediaCategory.IMAGE }
            if (photos.isEmpty()) emptyList()
            else if (settings.quickPhotoMode == BatchPhotoMode.FIRST) listOf(photos.first())
            else photos
        }

        QuickMediaType.AUDIO -> {
            val pref = settings.quickAudioQuality
            if (result.isPlaylist) {
                val audioCandidates = result.items.filter { it.category != MediaCategory.IMAGE }
                if (audioCandidates.isEmpty()) return emptyList()
                audioCandidates.mapIndexed { idx, item ->
                    item.copy(
                        category = MediaCategory.AUDIO,
                        extension = pref.extension,
                        label = "${idx + 1}. ${item.title} (${pref.label})",
                        ytdlpFormatSelector = if (pref == AudioQualityPref.M4A_AAC) "ba[ext=m4a]/ba/best" else "ba/best",
                        ytdlpAudioFormat = pref.ytdlpFormat,
                        ytdlpAudioQuality = pref.audioQualityArg,
                        ytdlpContainer = pref.extension,
                        ytdlpTargetUrl = item.ytdlpTargetUrl.ifBlank { item.url },
                    )
                }
            } else {
                val matchingAudio = result.items.firstOrNull {
                    it.category == MediaCategory.AUDIO && it.extension.equals(pref.extension, ignoreCase = true)
                }
                if (matchingAudio != null) {
                    listOf(matchingAudio)
                } else {
                    val base = result.items.firstOrNull { it.category != MediaCategory.IMAGE && it.ytdlpTargetUrl.isNotBlank() }
                        ?: result.items.firstOrNull { it.category == MediaCategory.AUDIO }
                        ?: return emptyList()
                    val target = base.ytdlpTargetUrl.ifBlank { result.sourceUrl }
                    listOf(
                        base.copy(
                            id = "quick_aud_${pref.id}_${target.hashCode()}",
                            category = MediaCategory.AUDIO,
                            extension = pref.extension,
                            label = "Audio ${pref.label}",
                            ytdlpFormatSelector = if (pref == AudioQualityPref.M4A_AAC) "ba[ext=m4a]/ba/best" else "ba/best",
                            ytdlpAudioFormat = pref.ytdlpFormat,
                            ytdlpAudioQuality = pref.audioQualityArg,
                            ytdlpContainer = pref.extension,
                            ytdlpTargetUrl = target,
                        ),
                    )
                }
            }
        }

        QuickMediaType.VIDEO -> {
            val selector = buildQuickVideoFormatSelector(
                codec = settings.quickVideoCodec,
                resolution = settings.quickVideoResolution,
                audioFormat = settings.quickVideoAudioFormat,
            )
            if (result.isPlaylist) {
                val vidEntries = result.items.filter { it.category == MediaCategory.VIDEO }
                if (vidEntries.isEmpty()) return emptyList()
                vidEntries.map { item ->
                    if (item.ytdlpTargetUrl.isNotBlank()) {
                        item.copy(
                            category = MediaCategory.VIDEO,
                            extension = "mp4",
                            ytdlpFormatSelector = selector,
                            ytdlpAudioFormat = "",
                            ytdlpContainer = "mp4",
                        )
                    } else item
                }
            } else {
                val directVideos = result.items.filter { it.category == MediaCategory.VIDEO && it.ytdlpTargetUrl.isBlank() }
                if (directVideos.isNotEmpty()) {
                    listOf(directVideos.first())
                } else {
                    val base = result.items.firstOrNull { it.category == MediaCategory.VIDEO && it.ytdlpTargetUrl.isNotBlank() }
                    if (base == null) {
                        // Fallback to photos if the post is a photo-only carousel (e.g. TikTok photo, Pixiv, IG photo)
                        val photos = result.items.filter { it.category == MediaCategory.IMAGE }
                        if (photos.isNotEmpty()) {
                            return if (settings.quickPhotoMode == BatchPhotoMode.FIRST) listOf(photos.first()) else photos
                        }
                        return emptyList()
                    }
                    listOf(
                        base.copy(
                            id = "quick_vid_${settings.quickVideoCodec.id}_${settings.quickVideoResolution.id}_${base.ytdlpTargetUrl.hashCode()}",
                            category = MediaCategory.VIDEO,
                            extension = "mp4",
                            label = "Quick Video • ${settings.quickVideoResolution.label}",
                            ytdlpFormatSelector = selector,
                            ytdlpAudioFormat = "",
                            ytdlpContainer = "mp4",
                        ),
                    )
                }
            }
        }
    }
}

