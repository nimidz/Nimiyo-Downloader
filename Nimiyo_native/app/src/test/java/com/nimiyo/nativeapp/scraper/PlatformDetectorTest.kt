package com.nimiyo.nativeapp.scraper

import com.nimiyo.nativeapp.data.PlatformId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformDetectorTest {

    @Test
    fun detectKnownPlatform_doesNotMisclassifySubstringsAsTwitter() {
        assertNull(PlatformDetector.detectKnownPlatform("https://www.netflix.com/watch/80100172"))
        assertNull(PlatformDetector.detectKnownPlatform("https://www.dropbox.com/s/abcdef/video.mp4"))
        assertNull(PlatformDetector.detectKnownPlatform("https://yandex.com/video/preview/123"))
        assertNull(PlatformDetector.detectKnownPlatform("https://www.fox.com/live"))
        assertEquals(PlatformId.OTHER, PlatformDetector.detect("https://www.netflix.com/watch/80100172"))
        assertFalse(PlatformDetector.isAutoDetectableMediaUrl("https://www.dropbox.com/s/abcdef/file.zip"))
    }

    @Test
    fun detectKnownPlatform_detectsExactAndSubdomainPlatformHosts() {
        assertEquals(PlatformId.TWITTER, PlatformDetector.detectKnownPlatform("https://x.com/nasa/status/123"))
        assertEquals(PlatformId.TWITTER, PlatformDetector.detectKnownPlatform("https://mobile.twitter.com/nasa/status/123"))
        assertEquals(PlatformId.INSTAGRAM, PlatformDetector.detectKnownPlatform("https://www.instagram.com/stories/user/123/"))
        assertEquals(PlatformId.YOUTUBE, PlatformDetector.detectKnownPlatform("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals(PlatformId.SHOPEE, PlatformDetector.detectKnownPlatform("https://sv.shopee.co.id/share-video/abc"))
        assertTrue(PlatformDetector.isAutoDetectableMediaUrl("https://www.reddit.com/r/videos/comments/123/demo/"))
    }
}

