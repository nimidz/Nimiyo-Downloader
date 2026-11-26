package com.nimiyo.nativeapp.core

import com.nimiyo.nativeapp.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class ReleaseInfo(
    val versionName: String,
    val versionCode: Int?,
    val releaseDate: String,
    val downloadUrl: String,
    val releasePageUrl: String,
    val changelog: List<String>,
)

sealed class UpdateCheckResult {
    data class Available(val release: ReleaseInfo) : UpdateCheckResult()
    data object UpToDate : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

object UpdateChecker {
    private const val GITHUB_LATEST_RELEASE_API =
        "https://api.github.com/repos/nimidz/Nimiyo-Downloader/releases/latest"
    private const val GITHUB_VERSION_JSON_URL =
        "https://raw.githubusercontent.com/nimidz/Nimiyo-Downloader/main/version.json"
    private const val GITHUB_RELEASES_PAGE =
        "https://github.com/nimidz/Nimiyo-Downloader/releases/latest"

    suspend fun checkLatestRelease(httpFactory: HttpClientFactory): UpdateCheckResult = withContext(Dispatchers.IO) {
        val ghRelease = runCatching { fetchGitHubLatestRelease(httpFactory) }.getOrNull()
        val jsonRelease = runCatching { fetchVersionJson(httpFactory) }.getOrNull()

        if (ghRelease == null && jsonRelease == null) {
            return@withContext UpdateCheckResult.Error("Unable to reach update server")
        }

        val best = pickBestRelease(ghRelease, jsonRelease)
            ?: return@withContext UpdateCheckResult.UpToDate

        val currentVer = normalizeVersion(BuildConfig.VERSION_NAME)
        val remoteVer = normalizeVersion(best.versionName)
        val isNewerByName = compareVersions(remoteVer, currentVer) > 0
        val isNewerByCode = best.versionCode != null &&
            compareVersions(remoteVer, currentVer) == 0 &&
            best.versionCode > BuildConfig.VERSION_CODE

        if (isNewerByName || isNewerByCode) {
            UpdateCheckResult.Available(best)
        } else {
            UpdateCheckResult.UpToDate
        }
    }

    private fun fetchGitHubLatestRelease(httpFactory: HttpClientFactory): ReleaseInfo? {
        val res = httpFactory.get(
            GITHUB_LATEST_RELEASE_API,
            mapOf("Accept" to "application/vnd.github+json"),
        )
        if (res.status !in 200..299 || res.body.isBlank()) return null
        val json = JSONObject(res.body)
        if (json.optBoolean("draft", false)) return null

        val tag = json.optString("tag_name").ifBlank { json.optString("name") }
        val cleanVer = normalizeVersion(tag)
        if (cleanVer.isBlank()) return null

        val htmlUrl = json.optString("html_url", GITHUB_RELEASES_PAGE).ifBlank { GITHUB_RELEASES_PAGE }
        var apkUrl = ""
        val assets = json.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name")
                val dl = asset.optString("browser_download_url")
                if (name.endsWith(".apk", ignoreCase = true) && dl.startsWith("http")) {
                    apkUrl = dl
                    break
                }
            }
        }
        val bodyLines = json.optString("body")
            .lines()
            .map { it.trim().removePrefix("- ").removePrefix("* ").trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }

        val date = json.optString("published_at").take(10)

        return ReleaseInfo(
            versionName = cleanVer,
            versionCode = null,
            releaseDate = date,
            downloadUrl = apkUrl.ifBlank { htmlUrl },
            releasePageUrl = htmlUrl,
            changelog = bodyLines,
        )
    }

    private fun fetchVersionJson(httpFactory: HttpClientFactory): ReleaseInfo? {
        val res = httpFactory.get(GITHUB_VERSION_JSON_URL)
        if (res.status !in 200..299 || res.body.isBlank()) return null
        val json = JSONObject(res.body)
        val ver = normalizeVersion(
            json.optString("versionName").ifBlank { json.optString("version") },
        )
        if (ver.isBlank()) return null

        val code = if (json.has("versionCode")) json.optInt("versionCode") else null
        val dl = json.optString("downloadUrl").ifBlank { GITHUB_RELEASES_PAGE }
        val date = json.optString("releaseDate")
        val notes = mutableListOf<String>()
        val arr = json.optJSONArray("changelog")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val item = arr.optString(i).trim()
                if (item.isNotBlank()) notes.add(item)
            }
        }

        return ReleaseInfo(
            versionName = ver,
            versionCode = code,
            releaseDate = date,
            downloadUrl = dl,
            releasePageUrl = GITHUB_RELEASES_PAGE,
            changelog = notes,
        )
    }

    private fun pickBestRelease(gh: ReleaseInfo?, vj: ReleaseInfo?): ReleaseInfo? {
        if (gh == null) return vj
        if (vj == null) return gh

        val cmp = compareVersions(gh.versionName, vj.versionName)
        return when {
            cmp > 0 -> gh
            cmp < 0 -> vj
            else -> gh.copy(
                versionCode = vj.versionCode,
                releaseDate = gh.releaseDate.ifBlank { vj.releaseDate },
                downloadUrl = gh.downloadUrl.ifBlank { vj.downloadUrl },
                changelog = gh.changelog.ifEmpty { vj.changelog },
            )
        }
    }

    fun normalizeVersion(raw: String): String =
        raw.trim().removePrefix("v").removePrefix("V").substringBefore("-")

    fun compareVersions(a: String, b: String): Int {
        val pa = normalizeVersion(a).split(".").map { it.toIntOrNull() ?: 0 }
        val pb = normalizeVersion(b).split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(pa.size, pb.size)
        for (i in 0 until maxLen) {
            val va = pa.getOrElse(i) { 0 }
            val vb = pb.getOrElse(i) { 0 }
            if (va != vb) return va.compareTo(vb)
        }
        return 0
    }
}
