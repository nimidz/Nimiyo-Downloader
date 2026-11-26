package com.nimiyo.nativeapp.scraper

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.Locale

enum class PolicyViolationCategory(val titleId: String) {
    NSFW_18("Pornografi & NSFW (18+)"),
    VIOLENCE_GORE("Kekerasan & Kebrutalan"),
    HATE_HARASSMENT("Pelecehan & Kebencian (SARA)"),
    ILLEGAL_ACTIVITY("Aktivitas Ilegal"),
}

class PolicyViolationException(
    val category: PolicyViolationCategory,
    message: String,
) : IllegalStateException(message)

/**
 * Multi-layer content guard enforcing NIMIYO's usage rules:
 * - Layer 1: Domain, host label, and explicit NSFW URL-path checks before network/yt-dlp execution.
 * - Layer 2: yt-dlp extractor_key / webpage_url checks (catches mirrors & shortlinks after resolution).
 * - Layer 3: Metadata checks for non-NSFW sites (age_limit >= 18, over_18/is_nsfw flags, tags, categories,
 *            and 4-category rule keyword patterns across titles, descriptions, and playlist entries).
 */
object NimiyoContentGuard {

    private val blockedHosts: Set<String> = setOf(
        // 77 dedicated adult / NSFW yt-dlp extractors
        "4tube.com", "alphaporno.com", "beeg.com", "behindkink.com", "bongacams.com",
        "cam4.com", "cammodels.com", "camsoda.com", "chaturbate.com", "drtuber.com",
        "empflix.com", "eporner.com", "erocast.me", "eroprofile.com", "fux.com",
        "hellporno.com", "lovehomeporn.com", "manyvids.com", "mellow-fan.com", "mellowfan.com",
        "moviefap.com", "murrtube.net", "nonktube.com", "noodlemagazine.com",
        "nubiles-porn.com", "nubilesporn.com", "nuvid.com", "peekvids.com", "playvids.com",
        "pornbox.com", "pornerbros.com", "pornflip.com", "pornhub.com", "pornhub.org",
        "pornhubpremium.com", "thumbzilla.com", "pornotube.com", "pornovoisines.com",
        "pornoxo.com", "porntop.com", "porntube.com", "redgifs.com", "redtube.com",
        "rule34video.com", "rule34.xxx", "sexu.com", "slutload.com", "soundgasm.net",
        "spankbang.com", "stripchat.com", "sunporno.com", "thisvid.com", "tnaflix.com",
        "toypics.net", "tube8.com", "txxx.com", "xhamster.com", "xhamster.desi",
        "xhamster2.com", "xhamsterlive.com", "xnxx.com", "xnxx.health", "xvideos.com",
        "xvideos.es", "xvideos2.com", "xxxymovies.com", "youjizz.com", "youporn.com",
        "zenporn.com",
        // Additional dedicated adult / NSFW / extreme gore domains outside yt-dlp extractors
        "onlyfans.com", "fansly.com", "nhentai.net", "hanime.tv", "hentaihaven.xxx",
        "missav.com", "missav.ws", "missav.ai", "javhd.com", "javmost.com", "javlibrary.com",
        "brazzers.com", "bangbros.com", "motherless.com", "simpcity.su",
        "coomer.su", "coomer.party", "kemono.su", "kemono.party", "nekopoi.care",
        "bestgore.com", "bestgore.fun", "kaotic.com", "crazyshit.com",
        "theync.com", "watchpeopledie.tv", "seegore.com", "goregrish.com",
    )

    private val blockedExtractorBases: Set<String> = setOf(
        "4tube", "alphaporno", "beeg", "behindkink", "bongacams", "cam4", "cammodels",
        "camsoda", "chaturbate", "drtuber", "empflix", "eporner", "erocast", "eroprofile",
        "fux", "hellporno", "lovehomeporn", "manyvids", "mellowfan", "moviefap",
        "murrtube", "murrtubeuser", "nonktube", "noodlemagazine", "nubilesporn", "nuvid",
        "peekvids", "playvids", "pornbox", "pornerbros", "pornflip",
        "pornhub", "pornhubpagedvideolist", "pornhubplaylist", "pornhubuser",
        "pornhubuservideosupload", "pornotube", "pornovoisines", "pornoxo",
        "porntop", "porntube", "redgifs", "redgifssearch", "redgifsuser", "redtube",
        "rule34video", "sexu", "slutload", "soundgasm", "spankbang", "spankbangplaylist",
        "stripchat", "sunporno", "thisvid", "thisvidmember", "thisvidplaylist",
        "tnaflix", "tnaflixnetworkembed", "toypics", "toypicsuser", "tube8", "txxx",
        "xhamster", "xhamsterembed", "xhamsteruser", "xnxx", "xvideos", "xxxymovies",
        "youjizz", "youporn", "youporncategory", "youpornchannel", "youporncollection",
        "youpornstar", "youporntag", "youpornvideos", "zenporn",
    )

    private val blockedHostPattern = Regex(
        """(?:^|[.-])(porn\w*|porno\w*|bokep\w*|hentai\w*|nsfw|xxx+|xvideos\d*|xhamster\d*|xnxx\d*|redtube|youporn|youjizz|spankbang|chaturbate|stripchat|bongacams|redgifs|eporner|rule34\w*|nhentai|hanime|onlyfans|fansly|missav|javhd|javmost|bestgore|kaotic|crazyshit|seegore)(?:$|[.-])""",
        RegexOption.IGNORE_CASE,
    )

    private val blockedUrlPathPattern = Regex(
        """/(?:r/(?:nsfw\w*|gonewild\w*|porn\w*|hentai\w*|rule34\w*|asiansgonewild\w*|realgirls\w*)|(?:porn|porno|bokep|hentai|nsfw|xxx|r18|rule34|onlyfans-?leak\w*))(?:[/?#]|$)""",
        RegexOption.IGNORE_CASE,
    )

    private data class RulePattern(
        val category: PolicyViolationCategory,
        val regex: Regex,
    )

    private val rulePatterns: List<RulePattern> = listOf(
        // 1. Pornografi & NSFW (18+)
        RulePattern(
            PolicyViolationCategory.NSFW_18,
            Regex(
                """\b(?:bokep|porno|pornografi|pornography|porn|xxx+|nsfw|r-?18|sextape|sex\s+tape|hentai|ahegao|futanari|rule34|onlyfans|fansly|bugil|telanjang|ngentot|memek|kontol|colmek|toket|peju|squirt(?:ing)?|blowjob|handjob|gangbang|creampie|bukkake|deepthroat|masturbat(?:e|ion|ing)|cumshot|anal\s+sex|incest|camgirl|camshow|chaturbate|stripchat|xvideos|pornhub|xhamster|xnxx|redgifs|seksual\s+eksplisit|sexually\s+explicit)\b""",
                RegexOption.IGNORE_CASE,
            ),
        ),
        RulePattern(
            PolicyViolationCategory.NSFW_18,
            Regex(
                """(?:^|[\s\[({#|/])(?:18\+|nsfw|r-?18|bokep|porn|xxx)(?=$|[\s\])},.|/!])""",
                RegexOption.IGNORE_CASE,
            ),
        ),
        RulePattern(
            PolicyViolationCategory.NSFW_18,
            Regex(
                """\b(?:nude\s+(?:video|leak|leaked|pic|pics|photo|photos|selfie)|leaked\s+nudes|video\s+(?:syur|mesum|panas|dewasa)|vcs\s+(?:bugil|telanjang|mesum)|jav\s+(?:hd|uncensored|censored|porn)|uncensored\s+jav)\b""",
                RegexOption.IGNORE_CASE,
            ),
        ),

        // 2. Kekerasan & Kebrutalan
        RulePattern(
            PolicyViolationCategory.VIOLENCE_GORE,
            Regex(
                """\b(?:gore\s+(?:video|uncensored|clip|real|site)|bestgore|kaotic|crazyshit|liveleak\s+gore|beheading|decapitat(?:ion|ed|ing)|pemenggalan|penyiksaan\s+(?:sadis|brutal|nyata)|mutilasi\s+(?:nyata|sadis|video)|snuff\s+(?:film|video|movie)|real\s+execution|cartel\s+(?:execution|gore|torture|beheading)|self[\s-]*harm\s+tutorial|cara\s+bunuh\s+diri|tutorial\s+bunuh\s+diri)\b""",
                RegexOption.IGNORE_CASE,
            ),
        ),
        RulePattern(
            PolicyViolationCategory.VIOLENCE_GORE,
            Regex("""#(?:gore|bestgore|snuff)\b""", RegexOption.IGNORE_CASE),
        ),

        // 3. Pelecehan & Kebencian (SARA)
        RulePattern(
            PolicyViolationCategory.HATE_HARASSMENT,
            Regex(
                """\b(?:hate\s+speech|ujaran\s+kebencian|doxx(?:ing)?\s+(?:leak|address|info)|neo[\s-]*nazi\s+propaganda|white\s+supremac(?:y|ist)\s+manifesto|terroris[mt]\s+propaganda|isis\s+(?:execution|propaganda|beheading))\b""",
                RegexOption.IGNORE_CASE,
            ),
        ),

        // 4. Aktivitas Ilegal
        RulePattern(
            PolicyViolationCategory.ILLEGAL_ACTIVITY,
            Regex(
                """\b(?:child\s+porn|csam|pedophil\w*|loli(?:con)?\s+(?:hentai|18\+|nsfw)|carding\s+tutorial|jual\s+(?:narkoba|sabu|ganja|kokain|heroin|senjata\s*(?:api\s*)?ilegal|senpi\s*ilegal)|beli\s+(?:narkoba|sabu|senpi\s*ilegal)|rakit\s+(?:bom|senjata\s*api|senpi)|cara\s+(?:membuat\s+(?:bom|narkoba|sabu)|merakit\s+(?:bom|senjata\s*api))|how\s+to\s+(?:make\s+(?:a\s+)?bomb|cook\s+meth|buy\s+illegal\s+guns))\b""",
                RegexOption.IGNORE_CASE,
            ),
        ),
    )

    private val adultRatings: Set<String> = setOf(
        "18+", "r-18", "r18", "nc-17", "xxx", "nsfw", "adult", "mature_18", "explicit"
    )

    fun isBlockedUrl(rawUrl: String): Boolean = findUrlViolation(rawUrl) != null

    fun checkUrlBeforeAnalyze(rawUrl: String) {
        val violation = findUrlViolation(rawUrl) ?: return
        throw buildException(violation, "URL / domain ini terdeteksi melanggar kebijakan konten NIMIYO.")
    }

    fun checkExtractorKey(extractorKey: String, webpageUrl: String = "") {
        val base = extractorKey.substringBefore(':').trim().lowercase(Locale.US)
        if (base.isNotEmpty() && blockedExtractorBases.contains(base)) {
            throw buildException(
                PolicyViolationCategory.NSFW_18,
                "Sumber media ($extractorKey) termasuk kategori situs dewasa/NSFW yang dilarang.",
            )
        }
        if (webpageUrl.isNotBlank()) {
            checkUrlBeforeAnalyze(webpageUrl)
        }
    }

    fun checkMetadata(json: JSONObject) {
        val extractorKey = json.optString("extractor_key")
            .ifBlank { json.optString("extractor") }
        val webpageUrl = json.optString("webpage_url")
        checkExtractorKey(extractorKey, webpageUrl)

        checkAgeAndNsfwFlags(json)

        val collectedTexts = collectMetadataTexts(json)
        checkTextCompliance(*collectedTexts.toTypedArray())
    }

    fun checkPlaylistEntryCompliant(entry: JSONObject): Boolean {
        val ieKey = entry.optString("ie_key")
        val entryUrl = entry.optString("webpage_url").ifBlank { entry.optString("url") }
        if (ieKey.isNotBlank() && blockedExtractorBases.contains(ieKey.substringBefore(':').trim().lowercase(Locale.US))) {
            return false
        }
        if (entryUrl.startsWith("http", ignoreCase = true) && isBlockedUrl(entryUrl)) {
            return false
        }
        if (hasAgeOrNsfwFlag(entry)) {
            return false
        }
        val texts = collectMetadataTexts(entry)
        return findTextViolation(texts) == null
    }

    fun checkTextCompliance(vararg texts: String?) {
        val violation = findTextViolation(texts.filterNotNull()) ?: return
        throw buildException(
            violation,
            "Metadata konten terindikasi melanggar Peraturan NIMIYO (${violation.titleId}).",
        )
    }

    private fun checkAgeAndNsfwFlags(json: JSONObject) {
        if (hasAgeOrNsfwFlag(json)) {
            throw buildException(
                PolicyViolationCategory.NSFW_18,
                "Konten bertanda batas usia 18+ / NSFW diblokir sesuai Peraturan NIMIYO.",
            )
        }
    }

    private fun hasAgeOrNsfwFlag(obj: JSONObject): Boolean {
        if (obj.optInt("age_limit", 0) >= 18) return true
        if (obj.optBoolean("is_nsfw", false) ||
            obj.optBoolean("over_18", false) ||
            obj.optBoolean("nsfw", false)
        ) {
            return true
        }
        val rating = obj.optString("content_rating")
            .ifBlank { obj.optString("rating") }
            .trim()
            .lowercase(Locale.US)
        return rating in adultRatings
    }

    private fun collectMetadataTexts(obj: JSONObject): List<String> = buildList {
        add(obj.optString("title"))
        add(obj.optString("fulltitle"))
        add(obj.optString("alt_title"))
        add(obj.optString("description"))
        add(obj.optString("webpage_url"))
        addJsonStringArray(obj.optJSONArray("tags"), this)
        addJsonStringArray(obj.optJSONArray("categories"), this)
    }.filter { it.isNotBlank() }

    private fun addJsonStringArray(arr: JSONArray?, out: MutableList<String>) {
        if (arr == null) return
        for (i in 0 until arr.length()) {
            val v = arr.optString(i).trim()
            if (v.isNotEmpty()) out.add(v)
        }
    }

    private fun findUrlViolation(rawUrl: String): PolicyViolationCategory? {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) return null
        val host = extractHost(trimmed)
        if (host.isNotEmpty()) {
            if (blockedHosts.any { d -> host == d || host.endsWith(".$d") }) {
                return if (host.contains("gore") || host in setOf("kaotic.com", "crazyshit.com", "theync.com", "watchpeopledie.tv")) {
                    PolicyViolationCategory.VIOLENCE_GORE
                } else {
                    PolicyViolationCategory.NSFW_18
                }
            }
            if (blockedHostPattern.containsMatchIn(host)) {
                return if (host.contains("gore") || host.contains("kaotic") || host.contains("crazyshit")) {
                    PolicyViolationCategory.VIOLENCE_GORE
                } else {
                    PolicyViolationCategory.NSFW_18
                }
            }
        }
        if (blockedUrlPathPattern.containsMatchIn(trimmed)) {
            return PolicyViolationCategory.NSFW_18
        }
        return null
    }

    private fun findTextViolation(texts: List<String>): PolicyViolationCategory? {
        for (text in texts) {
            if (text.isBlank()) continue
            for (rule in rulePatterns) {
                if (rule.regex.containsMatchIn(text)) {
                    return rule.category
                }
            }
        }
        return null
    }

    private fun extractHost(rawUrl: String): String {
        val parsed = runCatching { URI(rawUrl).host }.getOrNull()
        val rawHost = parsed ?: Regex("""^https?://([^/:?#]+)""", RegexOption.IGNORE_CASE)
            .find(rawUrl)?.groupValues?.getOrNull(1)
        return rawHost.orEmpty().trim().lowercase(Locale.US).removePrefix("www.")
    }

    private fun buildException(category: PolicyViolationCategory, detail: String): PolicyViolationException {
        return PolicyViolationException(
            category = category,
            message = "Diblokir oleh Peraturan NIMIYO [${category.titleId}]: $detail",
        )
    }
}

