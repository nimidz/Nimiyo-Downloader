package com.nimiyo.nativeapp.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CookieVaultManagerTest {

    @Test
    fun normalizeDomain_stripsSchemePathAndSubdomainPrefix() {
        assertEquals("instagram.com", CookieVaultManager.normalizeDomain("https://www.instagram.com/accounts/login/"))
        assertEquals("facebook.com", CookieVaultManager.normalizeDomain("http://m.facebook.com/watch?v=1"))
        assertEquals("xiaohongshu.com", CookieVaultManager.normalizeDomain("  XiaoHongShu.com "))
    }

    @Test
    fun netscapeFormatting_generatesValidSevenColumnLines() {
        val lines = CookieVaultManager.parseRawCookieHeaderToNetscapeLines(
            domain = "instagram.com",
            rawCookieHeader = "sessionid=abc123; csrftoken=xyz789; broken_segment",
            nowSec = 1_700_000_000L,
        )
        assertEquals(2, lines.size)
        val cols = lines[0].split('\t')
        assertEquals(7, cols.size)
        assertEquals(".instagram.com", cols[0])
        assertEquals("TRUE", cols[1])
        assertEquals("/", cols[2])
        assertEquals("TRUE", cols[3])
        assertEquals("sessionid", cols[5])
        assertEquals("abc123", cols[6])

        val fileContent = CookieVaultManager.buildNetscapeCookieFileContent(lines)
        assertTrue(fileContent.startsWith("# Netscape HTTP Cookie File"))
        assertTrue(fileContent.contains("csrftoken\txyz789"))
    }

    @Test
    fun matchesRelatedDomain_handlesCrossBrandAliases() {
        assertTrue(CookieVaultManager.matchesRelatedDomain("www.instagram.com", "instagram.com"))
        assertTrue(CookieVaultManager.matchesRelatedDomain("www.xiaohongshu.com", "rednote.com"))
        assertTrue(CookieVaultManager.matchesRelatedDomain("twitter.com", "x.com"))
        assertFalse(CookieVaultManager.matchesRelatedDomain("youtube.com", "instagram.com"))
        assertFalse(CookieVaultManager.matchesRelatedDomain("evil-twitter.com", "x.com"))
        assertFalse(CookieVaultManager.matchesRelatedDomain("rednote.com.attacker.tld", "xiaohongshu.com"))
    }

    @Test
    fun quickVideoFormatSelector_buildsExpectedCodecResolutionAndAudioConstraints() {
        val h264_1080_m4a = buildQuickVideoFormatSelector(
            codec = QuickVideoCodec.LEGACY_H264,
            resolution = QuickVideoResolution.P1080,
            audioFormat = QuickVideoAudioFormat.M4A,
        )
        assertTrue(h264_1080_m4a.contains("[height<=1080]"))
        assertTrue(h264_1080_m4a.contains("[vcodec^=avc1]"))
        assertTrue(h264_1080_m4a.contains("ba[ext=m4a]"))

        val modern_2160_opus = buildQuickVideoFormatSelector(
            codec = QuickVideoCodec.MODERN_AV1_VP9_H265,
            resolution = QuickVideoResolution.P2160,
            audioFormat = QuickVideoAudioFormat.OPUS,
        )
        assertTrue(modern_2160_opus.contains("[height<=2160]"))
        assertTrue(modern_2160_opus.contains("av01|vp0?9|hev1|hvc1"))
        assertTrue(modern_2160_opus.contains("acodec*=opus"))

        val low = buildQuickVideoFormatSelector(
            codec = QuickVideoCodec.LEGACY_H264,
            resolution = QuickVideoResolution.LOW,
            audioFormat = QuickVideoAudioFormat.AUTO,
        )
        assertTrue(low.contains("worst"))
    }
}

