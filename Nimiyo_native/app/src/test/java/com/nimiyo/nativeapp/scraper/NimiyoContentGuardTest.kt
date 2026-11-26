package com.nimiyo.nativeapp.scraper

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class NimiyoContentGuardTest {

    @Test
    fun layer1_blocksDedicatedAdultAndGoreUrls() {
        val blocked = listOf(
            "https://www.pornhub.com/view_video.php?viewkey=123",
            "https://xvideos.com/video123/test",
            "https://xhamster.com/videos/abc",
            "https://chaturbate.com/room",
            "https://www.redgifs.com/watch/demo",
            "https://onlyfans.com/creator",
            "https://bestgore.com/clip",
            "https://www.reddit.com/r/nsfw/comments/abc/test/",
        )
        for (url in blocked) {
            assertTrue("Expected blocked URL: $url", NimiyoContentGuard.isBlockedUrl(url))
            assertFalse("Expected clipboard auto-detect disabled: $url", PlatformDetector.isAutoDetectableMediaUrl(url))
            assertViolates { NimiyoContentGuard.checkUrlBeforeAnalyze(url) }
        }
    }

    @Test
    fun layer1_allowsCleanGeneralAndLookalikeUrls() {
        val allowed = listOf(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://x.com/androiddev/status/123456",
            "https://www.reddit.com/r/androiddev/comments/abc/compose/",
            "https://www.adultswim.com/videos/rick-and-morty",
            "https://popcorntv.it/movie/123",
            "https://www.tiktok.com/@user/video/123456",
        )
        for (url in allowed) {
            assertFalse("Expected allowed URL: $url", NimiyoContentGuard.isBlockedUrl(url))
            NimiyoContentGuard.checkUrlBeforeAnalyze(url)
        }
    }

    @Test
    fun layer2_blocksMirrorViaExtractorKey() {
        assertViolates(PolicyViolationCategory.NSFW_18) {
            NimiyoContentGuard.checkExtractorKey("PornHub:playlist", "https://short.example/123")
        }
        assertViolates(PolicyViolationCategory.NSFW_18) {
            NimiyoContentGuard.checkExtractorKey("YouPorn:user", "https://short.example/456")
        }
    }

    @Test
    fun layer3_blocksAgeLimit18AndNsfwFlagsOnNonNsfwSites() {
        val ytAgeRestricted = JSONObject().apply {
            put("extractor_key", "Youtube")
            put("title", "Normal Looking Title")
            put("age_limit", 18)
            put("webpage_url", "https://www.youtube.com/watch?v=abc")
        }
        assertViolates(PolicyViolationCategory.NSFW_18) {
            NimiyoContentGuard.checkMetadata(ytAgeRestricted)
        }

        val redditOver18 = JSONObject().apply {
            put("extractor_key", "Reddit")
            put("title", "Clip")
            put("over_18", true)
            put("webpage_url", "https://www.reddit.com/r/videos/comments/1/clip")
        }
        assertViolates(PolicyViolationCategory.NSFW_18) {
            NimiyoContentGuard.checkMetadata(redditOver18)
        }

        val ratedR18 = JSONObject().apply {
            put("extractor_key", "generic")
            put("title", "Illustration")
            put("content_rating", "R-18")
        }
        assertViolates(PolicyViolationCategory.NSFW_18) {
            NimiyoContentGuard.checkMetadata(ratedR18)
        }
    }

    @Test
    fun layer3_blocksAllFourRuleCategoriesInMetadata() {
        assertViolates(PolicyViolationCategory.NSFW_18) {
            NimiyoContentGuard.checkTextCompliance("Video viral #bokep terbaru")
        }
        assertViolates(PolicyViolationCategory.VIOLENCE_GORE) {
            NimiyoContentGuard.checkTextCompliance("Cartel execution gore uncensored")
        }
        assertViolates(PolicyViolationCategory.HATE_HARASSMENT) {
            NimiyoContentGuard.checkTextCompliance("Video ujaran kebencian SARA")
        }
        assertViolates(PolicyViolationCategory.ILLEGAL_ACTIVITY) {
            NimiyoContentGuard.checkTextCompliance("Cara merakit bom rakitan")
        }
    }

    @Test
    fun layer3_avoidsFalsePositivesOnMainstreamMedia() {
        val cleanSongs = listOf(
            JSONObject().apply {
                put("extractor_key", "Youtube")
                put("title", "Kings of Leon - Sex on Fire (Official Video)")
                put("age_limit", 0)
                put("tags", JSONArray(listOf("rock", "music", "live")))
            },
            JSONObject().apply {
                put("extractor_key", "Youtube")
                put("title", "Kill Bill Vol. 1 Soundtrack - Battle Without Honor")
                put("age_limit", 0)
            },
            JSONObject().apply {
                put("extractor_key", "Youtube")
                put("title", "Adult Swim Samurai Jack Trailer")
                put("age_limit", 13)
            },
        )
        for (json in cleanSongs) {
            NimiyoContentGuard.checkMetadata(json)
        }
    }

    @Test
    fun layer3_filtersViolatingPlaylistEntries() {
        val safeEntry = JSONObject().apply {
            put("title", "Tutorial Kotlin Compose")
            put("age_limit", 0)
            put("url", "https://www.youtube.com/watch?v=safe1")
        }
        val nsfwEntry = JSONObject().apply {
            put("title", "Clip #nsfw")
            put("age_limit", 18)
            put("url", "https://www.youtube.com/watch?v=bad1")
        }
        assertTrue(NimiyoContentGuard.checkPlaylistEntryCompliant(safeEntry))
        assertFalse(NimiyoContentGuard.checkPlaylistEntryCompliant(nsfwEntry))
    }

    private fun assertViolates(expectedCategory: PolicyViolationCategory? = null, block: () -> Unit) {
        try {
            block()
            fail("Expected PolicyViolationException to be thrown")
        } catch (e: PolicyViolationException) {
            if (expectedCategory != null && e.category != expectedCategory) {
                fail("Expected $expectedCategory but got ${e.category}: ${e.message}")
            }
        }
    }
}

