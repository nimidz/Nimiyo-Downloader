package com.nimiyo.nativeapp.download

import com.nimiyo.nativeapp.core.AudioQualityPref
import com.nimiyo.nativeapp.core.BatchPhotoMode
import com.nimiyo.nativeapp.core.QuickMediaType
import com.nimiyo.nativeapp.core.QuickVideoAudioFormat
import com.nimiyo.nativeapp.core.QuickVideoCodec
import com.nimiyo.nativeapp.core.QuickVideoResolution
import com.nimiyo.nativeapp.core.SettingsState
import com.nimiyo.nativeapp.data.MediaCategory
import com.nimiyo.nativeapp.data.PlatformId
import com.nimiyo.nativeapp.data.ScrapeResult
import com.nimiyo.nativeapp.data.ScraperDownloadItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickDownloadHelperTest {

    private fun sampleVideoResult(isPlaylist: Boolean = false): ScrapeResult {
        val items = listOf(
            ScraperDownloadItem(
                id = "vid_1",
                title = "Sample Video 1",
                label = "Video 1080p",
                url = "https://www.youtube.com/watch?v=abc123",
                category = MediaCategory.VIDEO,
                extension = "mp4",
                ytdlpTargetUrl = "https://www.youtube.com/watch?v=abc123",
            ),
            ScraperDownloadItem(
                id = "aud_1",
                title = "Sample Audio 1",
                label = "Audio MP3",
                url = "",
                category = MediaCategory.AUDIO,
                extension = "mp3",
                ytdlpTargetUrl = "https://www.youtube.com/watch?v=abc123",
            ),
            ScraperDownloadItem(
                id = "img_1",
                title = "Sample Photo 1",
                label = "Photo 1",
                url = "https://cdn.example.com/1.jpg",
                category = MediaCategory.IMAGE,
                extension = "jpg",
            ),
            ScraperDownloadItem(
                id = "img_2",
                title = "Sample Photo 2",
                label = "Photo 2",
                url = "https://cdn.example.com/2.jpg",
                category = MediaCategory.IMAGE,
                extension = "jpg",
            ),
        )
        return ScrapeResult(
            platform = PlatformId.YOUTUBE,
            serverUsed = "yt-dlp",
            title = "Sample",
            sourceUrl = "https://www.youtube.com/watch?v=abc123",
            items = items,
            isPlaylist = isPlaylist,
        )
    }

    @Test
    fun videoMode_buildsSelectorMatchingCodecResolutionAndAudioFormat() {
        val settings = SettingsState(
            quickMediaType = QuickMediaType.VIDEO,
            quickVideoCodec = QuickVideoCodec.LEGACY_H264,
            quickVideoResolution = QuickVideoResolution.P720,
            quickVideoAudioFormat = QuickVideoAudioFormat.M4A,
        )
        val picked = buildQuickDownloadItems(sampleVideoResult(), settings)
        assertEquals(1, picked.size)
        val item = picked.first()
        assertEquals(MediaCategory.VIDEO, item.category)
        assertEquals("mp4", item.extension)
        assertTrue(item.ytdlpFormatSelector.contains("height<=720"))
        assertTrue(item.ytdlpFormatSelector.contains("avc1"))
        assertTrue(item.ytdlpFormatSelector.contains("m4a"))
    }

    @Test
    fun videoPlaylistMode_filtersOutImagesAndAppliesSelectorToVideos() {
        val settings = SettingsState(
            quickMediaType = QuickMediaType.VIDEO,
            quickVideoCodec = QuickVideoCodec.MODERN_AV1_VP9_H265,
            quickVideoResolution = QuickVideoResolution.P1080,
            quickVideoAudioFormat = QuickVideoAudioFormat.OPUS,
        )
        val picked = buildQuickDownloadItems(sampleVideoResult(isPlaylist = true), settings)
        assertEquals(1, picked.size)
        assertEquals(MediaCategory.VIDEO, picked.first().category)
        assertTrue(picked.first().ytdlpFormatSelector.contains("height<=1080"))
    }

    @Test
    fun audioMode_appliesRequestedAudioFormat() {
        val settings = SettingsState(
            quickMediaType = QuickMediaType.AUDIO,
            quickAudioQuality = AudioQualityPref.OPUS,
        )
        val picked = buildQuickDownloadItems(sampleVideoResult(), settings)
        assertEquals(1, picked.size)
        val item = picked.first()
        assertEquals(MediaCategory.AUDIO, item.category)
        assertEquals("opus", item.extension)
        assertEquals("opus", item.ytdlpAudioFormat)
    }

    @Test
    fun photoMode_respectsFirstVsAllPreference() {
        val firstOnly = SettingsState(
            quickMediaType = QuickMediaType.PHOTO,
            quickPhotoMode = BatchPhotoMode.FIRST,
        )
        val allPhotos = SettingsState(
            quickMediaType = QuickMediaType.PHOTO,
            quickPhotoMode = BatchPhotoMode.ALL,
        )
        val resFirst = buildQuickDownloadItems(sampleVideoResult(), firstOnly)
        val resAll = buildQuickDownloadItems(sampleVideoResult(), allPhotos)
        assertEquals(1, resFirst.size)
        assertEquals("img_1", resFirst.first().id)
        assertEquals(2, resAll.size)
    }

    @Test
    fun emptyItems_returnsEmptyList() {
        val empty = ScrapeResult(
            platform = PlatformId.OTHER,
            serverUsed = "yt-dlp",
            title = "Empty",
            sourceUrl = "https://example.com",
            items = emptyList(),
        )
        assertTrue(buildQuickDownloadItems(empty, SettingsState()).isEmpty())
    }

    @Test
    fun audioMode_playlistExcludesImageCarousels() {
        val imageCarousel = ScrapeResult(
            platform = PlatformId.PIXIV,
            serverUsed = "Nimiyo",
            title = "Gallery",
            sourceUrl = "https://www.pixiv.net/artworks/1",
            isPlaylist = true,
            items = listOf(
                ScraperDownloadItem(
                    id = "p1",
                    title = "P1",
                    label = "Photo 1",
                    url = "https://i.pximg.net/1.jpg",
                    category = MediaCategory.IMAGE,
                    extension = "jpg",
                ),
                ScraperDownloadItem(
                    id = "p2",
                    title = "P2",
                    label = "Photo 2",
                    url = "https://i.pximg.net/2.jpg",
                    category = MediaCategory.IMAGE,
                    extension = "jpg",
                ),
            ),
        )
        val picked = buildQuickDownloadItems(
            imageCarousel,
            SettingsState(quickMediaType = QuickMediaType.AUDIO),
        )
        assertTrue(picked.isEmpty())
    }

    @Test
    fun videoMode_audioOnlySourceReturnsEmptyInsteadOfMp4() {
        val spotifyAudio = ScrapeResult(
            platform = PlatformId.SPOTIFY,
            serverUsed = "yt-dlp",
            title = "Song",
            sourceUrl = "https://open.spotify.com/track/1",
            items = listOf(
                ScraperDownloadItem(
                    id = "sp_1",
                    title = "Song",
                    label = "MP3 320",
                    url = "",
                    category = MediaCategory.AUDIO,
                    extension = "mp3",
                    ytdlpTargetUrl = "ytsearch1:Song audio",
                ),
            ),
        )
        val picked = buildQuickDownloadItems(
            spotifyAudio,
            SettingsState(quickMediaType = QuickMediaType.VIDEO),
        )
        assertTrue(picked.isEmpty())
    }
}

