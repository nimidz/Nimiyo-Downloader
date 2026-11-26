package com.nimiyo.nativeapp.scraper

import com.nimiyo.nativeapp.data.MediaCategory
import com.nimiyo.nativeapp.data.PlatformId
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SocialPlatformScrapersTest {

    @Test
    fun pixiv_extractsIllustIdsAcrossUrlVariants() {
        assertEquals("100412238", SocialPlatformScrapers.extractPixivIllustId("https://www.pixiv.net/artworks/100412238"))
        assertEquals("100412238", SocialPlatformScrapers.extractPixivIllustId("https://www.pixiv.net/en/artworks/100412238#1"))
        assertEquals("998877", SocialPlatformScrapers.extractPixivIllustId("https://www.pixiv.net/member_illust.php?mode=medium&illust_id=998877"))
    }

    @Test
    fun pixiv_parsesMultiPageSfwArtworkWithReferer() {
        val illust = JSONObject().apply {
            put("title", "Landscape Sketch")
            put("userName", "ArtistA")
            put("description", "<p>Morning view</p>")
            put("xRestrict", 0)
            put("urls", JSONObject().apply {
                put("regular", "https://i.pximg.net/img-master/img/123_p0_master1200.jpg")
                put("original", "https://i.pximg.net/img-original/img/123_p0.png")
            })
        }
        val pages = JSONArray().apply {
            put(JSONObject().put("urls", JSONObject().put("original", "https://i.pximg.net/img-original/img/123_p0.png")))
            put(JSONObject().put("urls", JSONObject().put("original", "https://i.pximg.net/img-original/img/123_p1.jpg")))
        }

        val res = SocialPlatformScrapers.parsePixivJson(
            illustBody = illust,
            pagesBody = pages,
            illustId = "123",
            sourceUrl = "https://www.pixiv.net/artworks/123",
        )
        assertEquals(PlatformId.PIXIV, res.platform)
        assertTrue(res.isPlaylist)
        assertEquals(2, res.items.size)
        assertEquals("png", res.items[0].extension)
        assertEquals("jpg", res.items[1].extension)
        assertEquals(MediaCategory.IMAGE, res.items[0].category)
        assertEquals("https://www.pixiv.net/", res.items[0].referer)
    }

    @Test
    fun pixiv_blocksR18AndR18G() {
        val r18 = JSONObject().apply {
            put("title", "Test")
            put("xRestrict", 1)
        }
        try {
            SocialPlatformScrapers.parsePixivJson(r18, null, "1", "https://www.pixiv.net/artworks/1")
            fail("Expected R-18 artwork to be blocked")
        } catch (e: PolicyViolationException) {
            assertEquals(PolicyViolationCategory.NSFW_18, e.category)
        }

        val r18g = JSONObject().apply {
            put("title", "Test")
            put("xRestrict", 2)
        }
        try {
            SocialPlatformScrapers.parsePixivJson(r18g, null, "2", "https://www.pixiv.net/artworks/2")
            fail("Expected R-18G artwork to be blocked")
        } catch (e: PolicyViolationException) {
            assertEquals(PolicyViolationCategory.VIOLENCE_GORE, e.category)
        }
    }

    @Test
    fun rednote_rewritesRegionalHostAndExtractsVideoFromInitialState() {
        val rewritten = SocialPlatformScrapers.rewriteRedNoteHostToXiaohongshu(
            "https://www.rednote.com/discovery/item/abc123?xsec_token=xyz"
        )
        assertEquals("https://www.xiaohongshu.com/discovery/item/abc123?xsec_token=xyz", rewritten)

        val html = """
            <html><head>
              <meta property="og:title" content="Coffee Vlog">
              <meta property="og:image" content="https://sns-webpic-qc.xhscdn.com/thumb.jpg">
            </head><body>
              <script>
                window.__INITIAL_STATE__={
                  "note":{
                    "noteDetailMap":{
                      "abc123":{
                        "note":{
                          "title":"Coffee Vlog",
                          "desc":"Morning brew",
                          "user":{"nickname":"Barista","extra":undefined},
                          "video":{"media":{"stream":{"h264":[{"masterUrl":"http:\u002F\u002Fsns-video-bd.xhscdn.com\u002Fclip.mp4"}]}}}
                        }
                      }
                    }
                  }
                };
              </script>
            </body></html>
        """.trimIndent()

        val res = SocialPlatformScrapers.parseRedNoteHtml(html, rewritten)
        assertNotNull(res)
        assertEquals(PlatformId.REDNOTE, res!!.platform)
        assertEquals("Coffee Vlog", res.title)
        assertEquals("Barista", res.author)
        assertEquals(1, res.items.size)
        assertEquals(MediaCategory.VIDEO, res.items[0].category)
        assertEquals("https://sns-video-bd.xhscdn.com/clip.mp4", res.items[0].url)
    }

    @Test
    fun threads_extractsVideoFromDataSjsJson() {
        val html = """
            <html><head>
              <meta property="og:title" content="zuck on Threads">
              <meta property="og:image" content="https://scontent.xx.fbcdn.net/thumb.jpg">
            </head><body>
              <script type="application/json" data-sjs>
                {
                  "data": {
                    "containing_thread": {
                      "thread_items": [{
                        "post": {
                          "user": {"username": "zuck"},
                          "caption": {"text": "Demo Threads Video"},
                          "video_versions": [{"url": "https://scontent.xx.fbcdn.net/v/threads_demo.mp4"}]
                        }
                      }]
                    }
                  }
                }
              </script>
            </body></html>
        """.trimIndent()

        val res = SocialPlatformScrapers.parseThreadsHtml(html, "https://www.threads.com/@zuck/post/C01")
        assertNotNull(res)
        assertEquals(PlatformId.THREADS, res!!.platform)
        assertEquals("@zuck", res.author)
        assertEquals("Demo Threads Video", res.title)
        assertEquals(MediaCategory.VIDEO, res.items[0].category)
        assertEquals("https://scontent.xx.fbcdn.net/v/threads_demo.mp4", res.items[0].url)
    }

    @Test
    fun shopee_extractsVideoFromNextData() {
        val html = """
            <html><head>
              <meta property="og:title" content="Review Sepatu Lari">
              <meta property="og:image" content="https://cf.shopee.co.id/file/cover.jpg">
            </head><body>
              <script id="__NEXT_DATA__" type="application/json">
                {"props":{"pageProps":{"video_url":"https://cv.shopee.co.id/video/demo123.mp4"}}}
              </script>
            </body></html>
        """.trimIndent()

        val res = SocialPlatformScrapers.parseShopeeHtml(html, "https://sv.shopee.co.id/share-video/123")
        assertNotNull(res)
        assertEquals(PlatformId.SHOPEE, res!!.platform)
        assertEquals("Review Sepatu Lari", res.title)
        assertEquals("https://cv.shopee.co.id/video/demo123.mp4", res.items[0].url)
    }

    @Test
    fun rednote_ignoresLoginPageAndStaticLogos() {
        val loginHtml = """
            <html><head>
              <meta property="og:title" content="RedNote">
              <meta property="og:image" content="https://static.xiaohongshu.com/logo.png">
            </head><body>Login required</body></html>
        """.trimIndent()
        val loginRes = SocialPlatformScrapers.parseRedNoteHtml(
            loginHtml,
            "https://www.rednote.com/login?redirectPath=https%3A%2F%2Fwww.rednote.com%2Fexplore",
        )
        assertEquals(null, loginRes)

        val avatarOnlyThreadsHtml = """
            <html><head>
              <meta property="og:image" content="https://scontent.xx.fbcdn.net/t51.2885-19/s150x150/avatar.jpg">
            </head><body></body></html>
        """.trimIndent()
        val threadsRes = SocialPlatformScrapers.parseThreadsHtml(
            avatarOnlyThreadsHtml,
            "https://www.threads.com/@user/post/123",
        )
        assertEquals(null, threadsRes)
    }

    @Test
    fun spotifyEmbed_parsesPlaylistTracksAndFiltersNsfwTrack() {
        val html = """
            <html><body>
              <script id="__NEXT_DATA__" type="application/json">
                {
                  "props": {
                    "pageProps": {
                      "state": {
                        "data": {
                          "entity": {
                            "name": "Chill Mix",
                            "subtitle": "Nimiyo",
                            "visualIdentity": {"image": [{"url": "https://i.scdn.co/image/cover"}]},
                            "trackList": [
                              {"title": "Midnight City", "subtitle": "M83", "duration": 243000},
                              {"title": "Bad #nsfw Track", "subtitle": "X", "duration": 100000}
                            ]
                          }
                        }
                      }
                    }
                  }
                }
              </script>
            </body></html>
        """.trimIndent()

        val res = SocialPlatformScrapers.parseSpotifyEmbedHtml(html, "https://open.spotify.com/playlist/123", "playlist")
        assertNotNull(res)
        assertTrue(res!!.isPlaylist)
        assertEquals(1, res.items.size)
        assertEquals("Midnight City", res.items[0].title)
        assertEquals("ytsearch1:M83 - Midnight City Official Audio", res.items[0].ytdlpTargetUrl)
    }

    @Test
    fun threads_picksHighestResVideoAndIgnoresReplies() {
        val html = """
            <html><body>
              <script type="application/json" data-sjs>
                {
                  "thread": {
                    "caption": {"text": "Main post"},
                    "user": {"username": "creator"},
                    "video_versions": [
                      {"width": 480, "height": 854, "url": "https://cdn.example.com/low.mp4"},
                      {"width": 1080, "height": 1920, "url": "https://cdn.example.com/hd.mp4"}
                    ],
                    "reply_threads": [
                      {
                        "caption": {"text": "Reply spam"},
                        "video_versions": [
                          {"width": 720, "height": 1280, "url": "https://cdn.example.com/reply.mp4"}
                        ]
                      }
                    ]
                  }
                }
              </script>
            </body></html>
        """.trimIndent()
        val res = SocialPlatformScrapers.parseThreadsHtml(html, "https://www.threads.com/@creator/post/1")
        assertNotNull(res)
        assertEquals(1, res!!.items.size)
        assertEquals("https://cdn.example.com/hd.mp4", res.items[0].url)
    }
}
