package com.nimiyo.nativeapp.player

import android.util.Base64
import com.nimiyo.nativeapp.core.HttpClientFactory
import com.nimiyo.nativeapp.data.AudioTrack
import com.nimiyo.nativeapp.data.MediaStoreRepository
import com.nimiyo.nativeapp.scraper.ScraperUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets

data class LyricWord(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val isBackground: Boolean = false,
)

data class LyricLine(
    val timeMs: Long,
    val endMs: Long,
    val text: String,
    val words: List<LyricWord> = emptyList(),
)

data class TtmlDisplayLine(
    val beginMs: Long,
    val endMs: Long,
    val text: String,
    val mainWords: List<LyricWord>,
    val bgWords: List<LyricWord>,
    val bgBeginMs: Long,
    val bgEndMs: Long,
    val isPureBg: Boolean = false,
    val isOpposite: Boolean = false,
    val translation: String = "",
)

data class TtmlDocument(
    val source: String,
    val rawXml: String,
    val lines: List<TtmlDisplayLine>,
)

data class LyricsDocument(
    val source: String,
    val rawLrc: String,
    val lines: List<LyricLine>,
    val isWordSynced: Boolean,
)

class LyricsRepository(
    private val http: HttpClientFactory,
    private val mediaRepo: MediaStoreRepository,
) {

    fun cleanTrackQuery(title: String, artist: String): Pair<String, String> {
        var cleanedTitle = title
            .replace(Regex("""【[^】]*】"""), " ")
            .replace(Regex("""\([^)]*(?:official|lyric|audio|video|mv|pv|hd|4k|feat\.?|ft\.?|utau|synthv|synthesizer\s*v|vocaloid|cover)[^)]*\)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\[[^]]*(?:official|lyric|audio|video|mv|pv|hd|4k|feat\.?|ft\.?|utau|synthv|synthesizer\s*v|vocaloid|cover)[^]]*]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+-\s+.*$""")) { m ->
                if (m.value.contains("Radio Edit", true) || m.value.contains("Remix", true)) "" else m.value
            }
            .replace(Regex("\\s+"), " ")
            .trim()
        // Strip trailing "/ Kasane Teto" or "| Channel" when a meaningful title remains before it
        val slashStripped = cleanedTitle.replace(Regex("""\s*[/|｜]\s*.+$"""), "").trim()
        if (slashStripped.length >= 2) {
            cleanedTitle = slashStripped
        }
        val normalizedRawArtist = artist.trim()
        val rawArtist = if (
            normalizedRawArtist.equals("Unknown Artist", true) ||
            normalizedRawArtist.equals("<unknown>", true) ||
            normalizedRawArtist.equals("Nimiyo Offline", true) ||
            normalizedRawArtist.equals("YouTube", true) ||
            normalizedRawArtist.equals("TikTok", true) ||
            normalizedRawArtist.equals("SoundCloud", true) ||
            normalizedRawArtist.equals("Spotify", true)
        ) "" else normalizedRawArtist
        val primaryArtist = rawArtist
            .split(Regex("""[,&]|feat\.?|ft\.""", RegexOption.IGNORE_CASE))
            .firstOrNull()
            ?.trim()
            .orEmpty()
        return cleanedTitle.ifBlank { title.trim() } to primaryArtist.ifBlank { rawArtist }
    }

    suspend fun loadLocalOrOnlineLyrics(track: AudioTrack): LyricsDocument? = withContext(Dispatchers.IO) {
        // 1. Check same folder .lrc and Downloads/Nimiyo/LyricsYO/.lrc
        val localLrc = findLocalLrcFile(track)
        if (localLrc != null && localLrc.exists() && localLrc.canRead()) {
            val raw = runCatching { localLrc.readText() }.getOrNull()
            if (!raw.isNullOrBlank()) {
                val parsed = parseLrcOrTtml(raw, source = "Local .lrc")
                if (parsed.lines.isNotEmpty()) return@withContext parsed
            }
        }
        return@withContext searchOnlineLyrics(track)
    }

    suspend fun searchOnlineLyrics(track: AudioTrack): LyricsDocument? = withContext(Dispatchers.IO) {
        val (title, artist) = cleanTrackQuery(track.title, track.artist)
        val durSec = (track.durationMs / 1000L).toInt().coerceAtLeast(0)

        // 1. KuGou Studio is PRIMARY (Utama)
        runCatching {
            val kg = fetchKuGou(title, artist, track.durationMs)
            if (kg != null && kg.lines.isNotEmpty()) return@withContext kg
        }

        // 2. LRCLIB is ASSIST / FALLBACK TERAKHIR (saat tidak ada di KuGou)
        runCatching {
            val lrcLib = fetchLrcLib(title, artist, durSec)
            if (lrcLib != null && lrcLib.lines.isNotEmpty()) return@withContext lrcLib
        }

        null
    }

    suspend fun searchKuGouOnly(track: AudioTrack): LyricsDocument? = withContext(Dispatchers.IO) {
        val (title, artist) = cleanTrackQuery(track.title, track.artist)
        runCatching { fetchKuGou(title, artist, track.durationMs) }.getOrNull()
    }

    suspend fun searchLrcLibOnly(track: AudioTrack): LyricsDocument? = withContext(Dispatchers.IO) {
        val (title, artist) = cleanTrackQuery(track.title, track.artist)
        val durSec = (track.durationMs / 1000L).toInt().coerceAtLeast(0)
        runCatching { fetchLrcLib(title, artist, durSec) }.getOrNull()
    }

    suspend fun exportLyricsFile(track: AudioTrack, isTtml: Boolean, content: String): File = withContext(Dispatchers.IO) {
        if (isTtml) {
            saveLocalTtml(track, content)
        } else {
            saveLocalLrc(track, content)
        }
    }

    /**
     * Dedicated TTML Provider (independent from .lrc!):
     * Checks cached .ttml file in Downloads/Nimiyo/LyricsYO/, otherwise fetches from
     * lyrics-api.boidu.dev and api.betterlyrics.org.
     */
    suspend fun loadTtmlCachedOrOnline(track: AudioTrack, forceOnline: Boolean = false): TtmlDocument? = withContext(Dispatchers.IO) {
        if (!forceOnline) {
            val localTtml = findLocalTtmlFile(track)
            if (localTtml != null && localTtml.exists() && localTtml.canRead()) {
                val raw = runCatching { localTtml.readText() }.getOrNull()
                if (!raw.isNullOrBlank() && (raw.contains("<tt", ignoreCase = true) || raw.contains("<p", ignoreCase = true))) {
                    val doc = parseTtmlXmlToDocument(raw, "Local .ttml")
                    if (doc.lines.isNotEmpty()) return@withContext doc
                }
            }
        }
        val fetched = fetchBetterLyricsTtml(track) ?: return@withContext null
        runCatching { saveLocalTtml(track, fetched.rawXml) }
        fetched
    }

    fun findLocalTtmlFile(track: AudioTrack): File? {
        val audioFile = if (track.path.isNotBlank()) File(track.path) else null
        if (audioFile != null) {
            val sibling = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.ttml")
            if (sibling.exists()) return sibling
        }
        val safeBase = mediaRepo.sanitizeFilename(track.title)
        val dir = runCatching { mediaRepo.lyricsDir() }.getOrNull() ?: return null
        val candidates = listOf(
            File(dir, "$safeBase.ttml"),
            if (audioFile != null) File(dir, "${audioFile.nameWithoutExtension}.ttml") else null,
        ).filterNotNull()
        return candidates.firstOrNull { it.exists() }
    }

    suspend fun saveLocalTtml(track: AudioTrack, xmlContent: String): File = withContext(Dispatchers.IO) {
        val baseName = if (track.path.isNotBlank()) File(track.path).nameWithoutExtension else mediaRepo.sanitizeFilename(track.title)
        val dir = runCatching { mediaRepo.lyricsDir() }.getOrElse { File(System.getProperty("java.io.tmpdir") ?: "/data/local/tmp") }
        val file = File(dir, "$baseName.ttml")
        runCatching { file.writeText(xmlContent) }
        file
    }

    private fun fetchBetterLyricsTtml(track: AudioTrack): TtmlDocument? {
        val (title, primaryArtist) = cleanTrackQuery(track.title, track.artist)
        if (title.isBlank()) return null
        val isUnknownArtist = primaryArtist.isBlank() ||
            primaryArtist.equals("Unknown Artist", ignoreCase = true) ||
            primaryArtist.equals("<unknown>", ignoreCase = true)
        val durSec = if (track.durationMs > 0L) (track.durationMs / 1000L).toInt() else 0
        val qS = ScraperUtils.urlEncode(title)
        val qA = if (isUnknownArtist) "" else ScraperUtils.urlEncode(primaryArtist)
        val durParam = if (durSec > 0) "&d=$durSec" else ""

        val endpoints = buildList {
            if (qA.isNotBlank()) {
                if (durParam.isNotEmpty()) {
                    add("https://lyrics-api.boidu.dev/getLyrics?s=$qS&a=$qA$durParam" to "Boidu TTML")
                    add("https://api.betterlyrics.org/getLyrics?s=$qS&a=$qA$durParam" to "BetterLyrics TTML")
                }
                add("https://lyrics.paxsenix.org/apple-music/lyrics?title=$qS&artist=$qA" to "Paxsenix Apple TTML")
                add("https://lyrics-api.boidu.dev/getLyrics?s=$qS&a=$qA" to "Boidu TTML")
                add("https://api.betterlyrics.org/getLyrics?s=$qS&a=$qA" to "BetterLyrics TTML")
            }
            add("https://lyrics.paxsenix.org/apple-music/lyrics?title=$qS" to "Paxsenix Apple TTML")
            add("https://lyrics-api.boidu.dev/getLyrics?s=$qS" to "Boidu TTML")
            add("https://api.betterlyrics.org/getLyrics?s=$qS" to "BetterLyrics TTML")
        }.distinctBy { it.first }
        for ((ep, srcName) in endpoints) {
            val res = runCatching { http.get(ep, mapOf("User-Agent" to "NimiyoNative/2.4.0")) }.getOrNull() ?: continue
            if (res.status >= 400 || res.body.isBlank()) continue
            val ttmlXml = if (res.body.trimStart().startsWith("{")) {
                val j = runCatching { JSONObject(res.body) }.getOrNull()
                j?.optString("ttml").orEmpty()
                    .ifBlank { j?.optString("lyrics").orEmpty() }
                    .ifBlank { j?.optString("data").orEmpty() }
            } else res.body
            if (ttmlXml.contains("<tt", ignoreCase = true) && ttmlXml.contains("<p", ignoreCase = true)) {
                val doc = parseTtmlXmlToDocument(ttmlXml.trim(), srcName)
                if (doc.lines.isNotEmpty()) return doc
            }
        }
        return null
    }

    fun findLocalLrcFile(track: AudioTrack): File? {
        val audioFile = if (track.path.isNotBlank()) File(track.path) else null
        if (audioFile != null) {
            val sibling = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.lrc")
            if (sibling.exists()) return sibling
        }
        val safeBase = mediaRepo.sanitizeFilename(track.title)
        val dir = runCatching { mediaRepo.lyricsDir() }.getOrNull() ?: return null
        val candidates = listOf(
            File(dir, "$safeBase.lrc"),
            if (audioFile != null) File(dir, "${audioFile.nameWithoutExtension}.lrc") else null,
        ).filterNotNull()
        return candidates.firstOrNull { it.exists() }
    }

    suspend fun saveLocalLrc(track: AudioTrack, lrcContent: String): File = withContext(Dispatchers.IO) {
        val baseName = if (track.path.isNotBlank()) File(track.path).nameWithoutExtension else mediaRepo.sanitizeFilename(track.title)
        val dir = runCatching { mediaRepo.lyricsDir() }.getOrElse { File(System.getProperty("java.io.tmpdir") ?: "/data/local/tmp") }
        val file = File(dir, "$baseName.lrc")
        runCatching { file.writeText(lrcContent) }
        file
    }

    private fun fetchTtmlWordSynced(title: String, artist: String, durSec: Int): LyricsDocument? {
        val qS = ScraperUtils.urlEncode(title)
        val qA = ScraperUtils.urlEncode(artist)
        val endpoints = listOf(
            "https://lyrics-api.boidu.dev/getLyrics?s=$qS&a=$qA&d=$durSec" to "Boidu TTML",
            "https://api.betterlyrics.org/getLyrics?s=$qS&a=$qA&d=$durSec" to "BetterLyrics TTML",
        )
        for ((ep, srcName) in endpoints) {
            val res = runCatching { http.get(ep) }.getOrNull() ?: continue
            if (res.status >= 400 || res.body.isBlank()) continue
            val ttmlXml = if (res.body.trimStart().startsWith("{")) {
                val j = runCatching { JSONObject(res.body) }.getOrNull()
                j?.optString("ttml").orEmpty().ifBlank { j?.optString("lyrics").orEmpty() }
            } else res.body
            if (ttmlXml.contains("<p", ignoreCase = true) || ttmlXml.contains("<tt", ignoreCase = true)) {
                val doc = parseTtmlXml(ttmlXml, srcName)
                if (doc.lines.isNotEmpty()) return doc
            }
        }
        return null
    }

    private fun fetchLrcLib(title: String, artist: String, durSec: Int): LyricsDocument? {
        val durParam = if (durSec > 0) "&duration=$durSec" else ""
        val urls = buildList {
            if (artist.isNotBlank()) {
                add("https://lrclib.net/api/get?track_name=${ScraperUtils.urlEncode(title)}&artist_name=${ScraperUtils.urlEncode(artist)}$durParam")
                add("https://lrclib.net/api/search?q=${ScraperUtils.urlEncode("$title $artist")}")
            }
            add("https://lrclib.net/api/search?q=${ScraperUtils.urlEncode(title)}")
        }.distinct()

        for (queryUrl in urls) {
            val res = runCatching { http.get(queryUrl, mapOf("User-Agent" to "NimiyoNative/3.0.0")) }.getOrNull()
                ?: continue
            if (res.status >= 400 || res.body.isBlank()) continue
            val trimmed = res.body.trimStart()
            val bestItem = when {
                trimmed.startsWith("[") -> {
                    val arr = runCatching { JSONArray(res.body) }.getOrNull() ?: continue
                    pickBestLrcLibItem(arr, durSec)
                }
                trimmed.startsWith("{") -> runCatching { JSONObject(res.body) }.getOrNull()
                else -> null
            } ?: continue

            val synced = if (!bestItem.isNull("syncedLyrics")) bestItem.optString("syncedLyrics", "") else ""
            val plain = if (!bestItem.isNull("plainLyrics")) bestItem.optString("plainLyrics", "") else ""
            val raw = synced.ifBlank { plain }
            if (raw.isNotBlank()) {
                return parseLrcOrTtml(raw, "LRCLIB")
            }
        }
        return null
    }

    private fun pickBestLrcLibItem(arr: JSONArray, durSec: Int): JSONObject? {
        if (arr.length() == 0) return null
        var bestSynced: JSONObject? = null
        var bestSyncedDiff = Int.MAX_VALUE
        var bestPlain: JSONObject? = null
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val synced = if (!obj.isNull("syncedLyrics")) obj.optString("syncedLyrics", "").trim() else ""
            val plain = if (!obj.isNull("plainLyrics")) obj.optString("plainLyrics", "").trim() else ""
            if (synced.isNotEmpty()) {
                val itemDur = obj.optDouble("duration", 0.0).toInt()
                val diff = if (durSec > 0 && itemDur > 0) kotlin.math.abs(itemDur - durSec) else i
                if (bestSynced == null || diff < bestSyncedDiff) {
                    bestSynced = obj
                    bestSyncedDiff = diff
                }
            } else if (plain.isNotEmpty() && bestPlain == null) {
                bestPlain = obj
            }
        }
        return bestSynced ?: bestPlain
    }

    private fun fetchKuGou(title: String, artist: String, durationMs: Long): LyricsDocument? {
        val queries = buildList {
            if (artist.isNotBlank()) add("$artist - $title")
            add(title)
        }.distinct()

        for (kw in queries) {
            val searchRes = runCatching {
                http.get("https://songsearch.kugou.com/song_search_v2?keyword=${ScraperUtils.urlEncode(kw)}&page=1&pagesize=5")
            }.getOrNull() ?: continue
            val lists = runCatching {
                JSONObject(searchRes.body).optJSONObject("data")?.optJSONArray("lists")
            }.getOrNull() ?: continue
            if (lists.length() == 0) continue

            val sortedHashes = (0 until lists.length())
                .mapNotNull { i ->
                    val s = lists.optJSONObject(i) ?: return@mapNotNull null
                    val hash = s.optString("FileHash")
                    if (hash.isBlank()) return@mapNotNull null
                    val dur = s.optLong("Duration", 0L) * 1000L
                    val diff = if (durationMs > 0 && dur > 0) kotlin.math.abs(dur - durationMs) else i.toLong()
                    hash to diff
                }
                .sortedBy { it.second }
                .take(3)

            for ((hash, _) in sortedHashes) {
                val krcSearch = runCatching {
                    http.get("https://krcs.kugou.com/search?ver=1&man=yes&client=mobi&hash=$hash")
                }.getOrNull() ?: continue
                val candidates = runCatching {
                    JSONObject(krcSearch.body).optJSONArray("candidates")
                }.getOrNull() ?: continue
                if (candidates.length() == 0) continue
                val cand = candidates.getJSONObject(0)
                val cid = cand.optString("id")
                val key = cand.optString("accesskey")
                val dl = runCatching {
                    http.get("https://lyrics.kugou.com/download?ver=1&client=pc&id=$cid&accesskey=$key&fmt=lrc&charset=utf8")
                }.getOrNull() ?: continue
                val contentB64 = runCatching { JSONObject(dl.body).optString("content") }.getOrDefault("")
                if (contentB64.isBlank()) continue
                val decoded = String(Base64.decode(contentB64, Base64.DEFAULT), StandardCharsets.UTF_8)
                val parsed = parseLrcOrTtml(decoded, "KuGou Studio")
                if (parsed.lines.isNotEmpty()) return parsed
            }
        }
        return null
    }

    fun parseLrcOrTtml(raw: String, source: String = "LRC"): LyricsDocument {
        if (raw.contains("<p ", ignoreCase = true) || raw.contains("<tt", ignoreCase = true)) {
            val doc = parseTtmlXml(raw, source)
            if (doc.lines.isNotEmpty()) return doc
        }
        val lrcLines = mutableListOf<LyricLine>()
        val tagRegex = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
        val metaTagRegex = Regex("""^\[[a-zA-Z_-]+:.*]$""")
        val rawRows = raw.lines()
        for (row in rawRows) {
            val matches = tagRegex.findAll(row).toList()
            val text = tagRegex.replace(row, "").trim()
            if (matches.isEmpty()) {
                val trimmedRow = row.trim()
                if (text.isNotBlank() && !metaTagRegex.matches(trimmedRow)) {
                    lrcLines.add(LyricLine(0L, 0L, text))
                }
                continue
            }
            for (m in matches) {
                val min = m.groupValues[1].toLongOrNull() ?: 0L
                val sec = m.groupValues[2].toLongOrNull() ?: 0L
                val fracRaw = m.groupValues[3]
                val msPart = when (fracRaw.length) {
                    1 -> (fracRaw.toLongOrNull() ?: 0L) * 100
                    2 -> (fracRaw.toLongOrNull() ?: 0L) * 10
                    3 -> (fracRaw.toLongOrNull() ?: 0L)
                    else -> 0L
                }
                val ts = (min * 60_000L) + (sec * 1000L) + msPart
                if (text.isNotBlank()) {
                    lrcLines.add(LyricLine(ts, ts + 4000L, text))
                }
            }
        }
        val baseSorted = lrcLines.sortedBy { it.timeMs }
        val sorted = baseSorted.mapIndexed { idx, line ->
            val nextStart = baseSorted.getOrNull(idx + 1)?.timeMs ?: (line.timeMs + 5000L)
            line.copy(endMs = nextStart)
        }
        return LyricsDocument(source = source, rawLrc = raw, lines = sorted, isWordSynced = false)
    }

    private fun decodeXmlEntities(text: String): String =
        text.replace("&amp;", "&")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&quot;", "\"")
            .replace("&lt;", "<")
            .replace("&gt;", ">")

    private fun extractWordsFromTtmlFragment(
        fragment: String,
        lineBeginMs: Long,
        lineEndMs: Long,
        isBgFragment: Boolean,
    ): List<LyricWord> {
        val tokenRegex = Regex("""<span([^>]*)>([\s\S]*?)</span>|(\s+)""", RegexOption.IGNORE_CASE)
        val beginAttr = Regex("""begin\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        val endAttr = Regex("""end\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)

        val words = mutableListOf<LyricWord>()
        var curWordText = StringBuilder()
        var curWordBegin = 0L
        var curWordEnd = 0L
        var hasOpenWord = false

        fun flushCurWord() {
            if (!hasOpenWord) return
            val t = curWordText.toString().trim()
            if (t.isNotEmpty()) {
                words.add(LyricWord(curWordBegin, curWordEnd.coerceAtLeast(curWordBegin + 120L), t, isBgFragment))
            }
            curWordText.clear()
            hasOpenWord = false
        }

        for (m in tokenRegex.findAll(fragment)) {
            if (m.groupValues[3].isNotEmpty()) {
                flushCurWord()
                continue
            }
            val attrs = m.groupValues[1]
            val innerRaw = decodeXmlEntities(m.groupValues[2].replace(Regex("<[^>]+>"), ""))
            val bMatch = beginAttr.find(attrs)?.groupValues?.get(1)
            val eMatch = endAttr.find(attrs)?.groupValues?.get(1)
            val wStart = if (bMatch != null) parseTtmlTimeMs(bMatch) else lineBeginMs
            val wEnd = if (eMatch != null) parseTtmlTimeMs(eMatch) else lineEndMs

            val hasLeadingSpace = innerRaw.firstOrNull()?.isWhitespace() == true
            val hasTrailingSpace = innerRaw.lastOrNull()?.isWhitespace() == true
            val clean = innerRaw.trim()

            if (hasLeadingSpace) flushCurWord()

            if (clean.isNotEmpty()) {
                if (!hasOpenWord) {
                    hasOpenWord = true
                    curWordText.clear()
                    curWordText.append(clean)
                    curWordBegin = wStart
                    curWordEnd = wEnd
                } else {
                    curWordText.append(clean)
                    if (wEnd > curWordEnd) curWordEnd = wEnd
                }
            }

            if (hasTrailingSpace) flushCurWord()
        }
        flushCurWord()
        // Fallback if fragment (such as a <span ttm:role="x-bg"> block) contains plain text without inner word <span> tags
        if (words.isEmpty()) {
            val plain = decodeXmlEntities(
                fragment.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ")
            ).trim()
            if (plain.isNotEmpty()) {
                val tokens = plain.split(Regex("\\s+")).filter { it.isNotEmpty() }
                val totalDur = (lineEndMs - lineBeginMs).coerceAtLeast(400L)
                val step = totalDur / tokens.size.coerceAtLeast(1)
                tokens.forEachIndexed { idx, tok ->
                    val ws = lineBeginMs + idx * step
                    val we = (ws + step).coerceAtLeast(ws + 120L)
                    words.add(LyricWord(ws, we, tok, isBgFragment))
                }
            }
        }
        return words
    }

    fun parseTtmlXmlToDocument(xml: String, source: String): TtmlDocument {
        val resultLines = mutableListOf<TtmlDisplayLine>()
        val pRegex = Regex("""<p\b([^>]*)>([\s\S]*?)</p>""", RegexOption.IGNORE_CASE)
        val beginAttr = Regex("""begin\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        val endAttr = Regex("""end\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        val agentAttr = Regex("""agent\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        val xbgStartRegex = Regex("""<span\s+[^>]*role=["']x-bg["'][^>]*>""", RegexOption.IGNORE_CASE)
        val transSpanRegex = Regex("""<span\s+[^>]*role=["'](?:x-translation|x-roman)["'][^>]*>([\s\S]*?)</span>""", RegexOption.IGNORE_CASE)
        var primaryAgent: String? = null

        for (pm in pRegex.findAll(xml)) {
            val pAttrs = pm.groupValues[1]
            var inner = pm.groupValues[2]
            val pBegin = parseTtmlTimeMs(beginAttr.find(pAttrs)?.groupValues?.get(1).orEmpty())
            val pEnd = parseTtmlTimeMs(endAttr.find(pAttrs)?.groupValues?.get(1).orEmpty()).coerceAtLeast(pBegin + 2000L)
            val isPureBgLine = pAttrs.contains("""role="x-bg"""", ignoreCase = true) ||
                pAttrs.contains("""role='x-bg'""", ignoreCase = true)
            val agent = agentAttr.find(pAttrs)?.groupValues?.get(1)?.trim().orEmpty()
            if (agent.isNotEmpty() && primaryAgent == null) {
                primaryAgent = agent
            }
            val isOpposite = agent.isNotEmpty() && (
                (primaryAgent != null && !agent.equals(primaryAgent, ignoreCase = true)) ||
                    agent.equals("v2", ignoreCase = true) ||
                    agent.equals("v1000", ignoreCase = true)
                )

            val transParts = mutableListOf<String>()
            inner = transSpanRegex.replace(inner) { m ->
                val t = decodeXmlEntities(m.groupValues[1].replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ")).trim()
                if (t.isNotEmpty()) transParts.add(t)
                ""
            }
            val translationText = transParts.joinToString(" • ")

            var mainWords = mutableListOf<LyricWord>()
            var bgWords = mutableListOf<LyricWord>()
            val cleanLineText: String

            if (isPureBgLine) {
                bgWords = extractWordsFromTtmlFragment(inner, pBegin, pEnd, isBgFragment = true).toMutableList()
                cleanLineText = decodeXmlEntities(
                    inner.replace(Regex("<[^>]+>"), "").replace(Regex("\\s+"), " ")
                ).trim()
            } else {
                // Depth-counting extractor for nested <span ttm:role="x-bg">...</span> blocks
                val bgBlocks = mutableListOf<String>()
                val cleanInnerBuilder = StringBuilder()
                var cursor = 0
                var bgMatch = xbgStartRegex.find(inner, 0)
                while (bgMatch != null) {
                    cleanInnerBuilder.append(inner.substring(cursor, bgMatch.range.first))
                    val contentStart = bgMatch.range.last + 1
                    var depth = 1
                    var i = contentStart
                    while (i < inner.length && depth > 0) {
                        when {
                            inner.startsWith("<span", i, ignoreCase = true) -> {
                                depth++
                                i += 5
                            }
                            inner.startsWith("</span>", i, ignoreCase = true) -> {
                                depth--
                                if (depth == 0) {
                                    bgBlocks.add(inner.substring(contentStart, i))
                                    cursor = i + 7
                                    break
                                }
                                i += 7
                            }
                            else -> i++
                        }
                    }
                    if (depth > 0) {
                        cursor = inner.length
                        break
                    }
                    bgMatch = xbgStartRegex.find(inner, cursor)
                }
                if (cursor < inner.length) cleanInnerBuilder.append(inner.substring(cursor))
                val cleanInner = cleanInnerBuilder.toString()

                val rawMain = extractWordsFromTtmlFragment(cleanInner, pBegin, pEnd, isBgFragment = false)
                val rawBg = bgBlocks.flatMap { extractWordsFromTtmlFragment(it, pBegin, pEnd, isBgFragment = true) }

                // Separate parenthesized words into backing vocal channel just like Nimiyo Lite
                var inParen = false
                for (w in rawMain) {
                    val t = w.text.trim()
                    when {
                        t.startsWith("(") && t.endsWith(")") -> bgWords.add(w.copy(isBackground = true))
                        t.startsWith("(") -> {
                            inParen = true
                            bgWords.add(w.copy(isBackground = true))
                        }
                        inParen -> {
                            bgWords.add(w.copy(isBackground = true))
                            if (t.endsWith(")")) inParen = false
                        }
                        else -> mainWords.add(w)
                    }
                }
                bgWords.addAll(rawBg)

                cleanLineText = decodeXmlEntities(
                    cleanInner.replace(Regex("<[^>]+>"), "").replace(Regex("\\s+"), " ")
                ).trim()

                // Fallback when <p> has line-level timing without <span> words
                if (mainWords.isEmpty() && bgWords.isEmpty() && cleanLineText.isNotEmpty()) {
                    val fallbackDoc = toTtmlDisplayLines(
                        LyricsDocument(source, "", listOf(LyricLine(pBegin, pEnd, cleanLineText)), false)
                    ).firstOrNull()
                    if (fallbackDoc != null) {
                        mainWords = fallbackDoc.mainWords.toMutableList()
                        bgWords = fallbackDoc.bgWords.toMutableList()
                    }
                }
            }

            val bgBegin = bgWords.minOfOrNull { it.startMs } ?: pBegin
            val bgEnd = bgWords.maxOfOrNull { it.endMs } ?: pEnd
            if (mainWords.isNotEmpty() || bgWords.isNotEmpty() || cleanLineText.isNotBlank()) {
                resultLines.add(
                    TtmlDisplayLine(
                        beginMs = pBegin,
                        endMs = pEnd,
                        text = cleanLineText.ifBlank { (mainWords + bgWords).joinToString(" ") { it.text } },
                        mainWords = mainWords,
                        bgWords = bgWords,
                        bgBeginMs = bgBegin,
                        bgEndMs = bgEnd,
                        isPureBg = mainWords.isEmpty() && bgWords.isNotEmpty(),
                        isOpposite = isOpposite,
                        translation = translationText,
                    )
                )
            }
        }
        return TtmlDocument(source = source, rawXml = xml, lines = resultLines.sortedBy { it.beginMs })
    }

    private fun parseTtmlXml(xml: String, source: String): LyricsDocument {
        val doc = parseTtmlXmlToDocument(xml, source)
        val lrcLines = mutableListOf<LyricLine>()
        val lrcBuilder = StringBuilder()
        for (line in doc.lines) {
            val txt = line.text.ifBlank { (line.mainWords + line.bgWords).joinToString(" ") { it.text } }.trim()
            if (txt.isNotBlank()) {
                val min = line.beginMs / 60000
                val sec = (line.beginMs % 60000) / 1000
                val cs = (line.beginMs % 1000) / 10
                lrcBuilder.append("[%02d:%02d.%02d]%s\n".format(min, sec, cs, txt))
                lrcLines.add(LyricLine(line.beginMs, line.endMs, txt, line.mainWords + line.bgWords))
            }
        }
        return LyricsDocument(
            source = source,
            rawLrc = lrcBuilder.toString(),
            lines = lrcLines,
            isWordSynced = true,
        )
    }

    private fun parseTtmlTimeMs(ts: String): Long {
        if (ts.isBlank()) return 0L
        val clean = ts.trim().removeSuffix("s")
        if (!clean.contains(":")) {
            return ((clean.toDoubleOrNull() ?: 0.0) * 1000.0).toLong()
        }
        val parts = clean.split(":")
        return when (parts.size) {
            2 -> {
                val m = parts[0].toLongOrNull() ?: 0L
                val s = ((parts[1].toDoubleOrNull() ?: 0.0) * 1000.0).toLong()
                m * 60_000L + s
            }
            3 -> {
                val h = parts[0].toLongOrNull() ?: 0L
                val m = parts[1].toLongOrNull() ?: 0L
                val s = ((parts[2].toDoubleOrNull() ?: 0.0) * 1000.0).toLong()
                h * 3_600_000L + m * 60_000L + s
            }
            else -> 0L
        }
    }

    /**
     * Converts any parsed LRC or TTML LyricsDocument into word-by-word TtmlDisplayLine items
     * matching generateTtmlFromLrc() in Nimiyo Lite (with backing-vocal parenthetical splitting
     * and weight-proportional word timing interpolation when true TTML words are absent).
     */
    fun toTtmlDisplayLines(doc: LyricsDocument): List<TtmlDisplayLine> {
        if (doc.lines.isEmpty()) return emptyList()
        val result = mutableListOf<TtmlDisplayLine>()
        val fullParenRegex = Regex("""^\([^)]+\)$""")
        val inlineParenRegex = Regex("""\([^)]+\)""")

        for (i in doc.lines.indices) {
            val item = doc.lines[i]
            val nextItem = doc.lines.getOrNull(i + 1)
            val lineBegin = item.timeMs
            val rawDuration = if (nextItem != null && nextItem.timeMs > lineBegin) {
                (nextItem.timeMs - lineBegin).coerceIn(1200L, 7000L)
            } else {
                (item.endMs - lineBegin).coerceIn(1200L, 7000L)
            }
            val lineEnd = lineBegin + rawDuration

            if (item.words.isNotEmpty()) {
                val main = item.words.filter { !it.isBackground }
                val bg = item.words.filter { it.isBackground }
                val bgBegin = bg.minOfOrNull { it.startMs } ?: lineBegin
                val bgEnd = bg.maxOfOrNull { it.endMs } ?: lineEnd
                result.add(
                    TtmlDisplayLine(
                        beginMs = lineBegin,
                        endMs = item.endMs.coerceAtLeast(lineEnd),
                        text = item.text,
                        mainWords = main,
                        bgWords = bg,
                        bgBeginMs = bgBegin,
                        bgEndMs = bgEnd,
                    )
                )
                continue
            }

            val rawText = item.text.trim()
            if (rawText.isEmpty()) continue

            val mainTextPart: String
            val bgTextPart: String
            if (fullParenRegex.matches(rawText)) {
                mainTextPart = ""
                bgTextPart = rawText
            } else {
                val bgMatches = mutableListOf<String>()
                val cleaned = inlineParenRegex.replace(rawText) { m ->
                    bgMatches.add(m.value)
                    ""
                }.replace(Regex("\\s+"), " ").trim()
                if (bgMatches.isNotEmpty()) {
                    mainTextPart = cleaned
                    bgTextPart = bgMatches.joinToString(" ")
                } else {
                    mainTextPart = rawText
                    bgTextPart = ""
                }
            }

            val mainTokens = mainTextPart.split(Regex("\\s+")).filter { it.isNotBlank() }
            val bgTokens = bgTextPart.split(Regex("\\s+")).filter { it.isNotBlank() }

            val mainWords = mutableListOf<LyricWord>()
            if (mainTokens.isNotEmpty()) {
                val totalChars = mainTokens.sumOf { it.length.coerceAtLeast(1) }.toFloat()
                val mainDur = if (bgTokens.isNotEmpty()) (rawDuration * 0.85f).toLong() else rawDuration
                var curBegin = lineBegin
                for (w in mainTokens) {
                    val weight = w.length.coerceAtLeast(1) / totalChars
                    val wDur = (mainDur * weight).toLong().coerceAtLeast(160L)
                    val wEnd = (curBegin + wDur).coerceAtMost(lineEnd)
                    mainWords.add(LyricWord(curBegin, wEnd, w, isBackground = false))
                    curBegin = wEnd
                }
            }

            val bgWords = mutableListOf<LyricWord>()
            var bgBegin = lineBegin
            var bgEnd = lineEnd
            if (bgTokens.isNotEmpty()) {
                val totalBgChars = bgTokens.sumOf { it.length.coerceAtLeast(1) }.toFloat()
                val bgStartOffset = if (mainTokens.isNotEmpty()) (rawDuration * 0.40f).toLong() else 0L
                var curBgBegin = lineBegin + bgStartOffset
                val bgDur = (rawDuration - bgStartOffset).coerceAtLeast(400L)
                bgBegin = curBgBegin
                for (w in bgTokens) {
                    val weight = w.length.coerceAtLeast(1) / totalBgChars
                    val wDur = (bgDur * weight).toLong().coerceAtLeast(160L)
                    val wEnd = (curBgBegin + wDur).coerceAtMost(lineEnd)
                    bgWords.add(LyricWord(curBgBegin, wEnd, w, isBackground = true))
                    curBgBegin = wEnd
                }
                bgEnd = curBgBegin
            }

            result.add(
                TtmlDisplayLine(
                    beginMs = lineBegin,
                    endMs = lineEnd,
                    text = mainTextPart.ifBlank { bgTextPart.ifBlank { rawText } },
                    mainWords = mainWords,
                    bgWords = bgWords,
                    bgBeginMs = bgBegin,
                    bgEndMs = bgEnd,
                )
            )
        }
        return result
    }
}

