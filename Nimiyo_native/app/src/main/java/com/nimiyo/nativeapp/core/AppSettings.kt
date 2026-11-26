package com.nimiyo.nativeapp.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class AppLanguage(val code: String, val displayName: String) {
    SYSTEM("system", "Default (System)"),
    EN("en", "English"),
    ID("id", "Indonesia"),
    ZH("zh", "中文"),
    JA("ja", "日本語"),
    AR("ar", "العربية"),
    VI("vi", "Tiếng Việt"),
    TH("th", "ไทย"),
    KO("ko", "한국어"),
    ES("es", "Español"),
    PT("pt", "Português"),
    MS("ms", "Bahasa Melayu"),
    HI("hi", "हिन्दी");

    companion object {
        fun fromCode(code: String): AppLanguage =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: SYSTEM
    }
}

enum class UiStyle(val id: String) {
    MATERIAL("material"),
    GLASSMORPHISM("glass"),
    CUPERTINO("cupertino");

    companion object {
        fun fromId(id: String): UiStyle =
            entries.firstOrNull { it.id == id } ?: MATERIAL
    }
}

enum class DarkModePref(val id: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromId(id: String): DarkModePref =
            entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

enum class StartupPage(val id: String) {
    HOME("home"),
    PLAYER("player"),
    HISTORY("history");

    companion object {
        fun fromId(id: String): StartupPage =
            entries.firstOrNull { it.id == id } ?: HOME
    }
}

enum class AccentColorPref(val id: String, val label: String) {
    DYNAMIC("dynamic", "Dynamic"),
    GREEN("green", "Matcha"),
    TEAL("teal", "Mint Teal"),
    CYAN("cyan", "Aqua"),
    BLUE("blue", "Ocean"),
    INDIGO("indigo", "Indigo"),
    PURPLE("purple", "Lavender"),
    PINK("pink", "Sakura"),
    RED("red", "Crimson"),
    ORANGE("orange", "Sunset"),
    YELLOW("yellow", "Amber"),
    BROWN("brown", "Mocha"),
    GRAY("gray", "Slate");

    companion object {
        fun fromId(id: String): AccentColorPref =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DYNAMIC
    }
}

enum class FilenameTemplate(val id: String, val label: String) {
    TITLE("title", "Judul Saja"),
    TITLE_PLATFORM("title-platform", "Judul + Platform"),
    TITLE_DATE("title-date", "Judul + Tanggal");

    companion object {
        fun fromId(id: String): FilenameTemplate =
            entries.firstOrNull { it.id == id } ?: TITLE
    }
}

enum class OverwriteMode(val id: String, val label: String) {
    RENAME("rename", "Auto-Rename (_1, _2)"),
    OVERWRITE("overwrite", "Timpa File"),
    SKIP("skip", "Lewati Jika Ada");

    companion object {
        fun fromId(id: String): OverwriteMode =
            entries.firstOrNull { it.id == id } ?: RENAME
    }
}

enum class BatchPhotoMode(val id: String, val label: String) {
    ALL("all", "Semua Foto"),
    FIRST("first", "Foto Pertama Saja");

    companion object {
        fun fromId(id: String): BatchPhotoMode =
            entries.firstOrNull { it.id == id } ?: ALL
    }
}

enum class DownloadSubfolderMode(val id: String) {
    DEFAULT("default"),
    BY_PLATFORM("platform"),
    BY_CATEGORY("category");

    companion object {
        fun fromId(id: String): DownloadSubfolderMode =
            entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

enum class YtDlpUpdateChannel(val id: String) {
    STABLE("stable"),
    NIGHTLY("nightly");

    companion object {
        fun fromId(id: String): YtDlpUpdateChannel =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: STABLE
    }
}

enum class AudioQualityPref(
    val id: String,
    val ytdlpFormat: String,
    val extension: String,
    val audioQualityArg: String,
    val label: String,
) {
    MP3_HIGH("mp3_320", "mp3", "mp3", "320K", "MP3 320kbps"),
    M4A_AAC("m4a", "m4a", "m4a", "0", "M4A (AAC)"),
    OPUS("opus", "opus", "opus", "0", "OPUS");

    companion object {
        fun fromId(id: String): AudioQualityPref =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: MP3_HIGH
    }
}

enum class QuickMediaType(val id: String, val label: String) {
    VIDEO("video", "VIDEO"),
    AUDIO("audio", "AUDIO"),
    PHOTO("photo", "FOTO");

    companion object {
        fun fromId(id: String): QuickMediaType =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: VIDEO
    }
}

enum class QuickVideoCodec(val id: String, val label: String) {
    LEGACY_H264("h264", "Lawas (H.264)"),
    MODERN_AV1_VP9_H265("modern", "Kualitas (AV1 / VP9 / H.265)");

    companion object {
        fun fromId(id: String): QuickVideoCodec =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: LEGACY_H264
    }
}

enum class QuickVideoResolution(
    val id: String,
    val label: String,
    val maxHeight: Int?,
) {
    BEST("best", "Kualitas Terbaik (Default)", null),
    P2160("2160", "2160p (4K)", 2160),
    P1440("1440", "1440p (2K)", 1440),
    P1080("1080", "1080p", 1080),
    P720("720", "720p", 720),
    P480("480", "480p", 480),
    P360("360", "360p", 360),
    LOW("low", "Kualitas Rendah", 240);

    companion object {
        fun fromId(id: String): QuickVideoResolution =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: BEST
    }
}

enum class QuickVideoAudioFormat(
    val id: String,
    val label: String,
    val ytdlpAudioSelector: String,
) {
    M4A("m4a", "M4A (AAC)", "ba[ext=m4a]/ba"),
    OPUS("opus", "OPUS", "ba[acodec*=opus]/ba"),
    AUTO("auto", "Auto (Best)", "ba");

    companion object {
        fun fromId(id: String): QuickVideoAudioFormat =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: M4A
    }
}

enum class QuickSaveWindowMode(val id: String) {
    ANALYZE("analyze"),
    DIRECT("direct");

    companion object {
        fun fromId(id: String): QuickSaveWindowMode =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: ANALYZE
    }
}

enum class LyricsAlignmentMode(val id: String) {
    LEFT("left"),
    RIGHT("right"),
    SPLIT_DUET("split_duet");

    companion object {
        fun fromId(id: String): LyricsAlignmentMode =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: LEFT
    }
}

enum class TtmlAnimationMode(val id: String) {
    PER_CHAR("per_char"),
    PER_WORD("per_word");

    companion object {
        fun fromId(id: String): TtmlAnimationMode =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: PER_CHAR
    }
}

enum class LyricsFontSizePref(val id: String, val ttmlSp: Int, val lrcSp: Int) {
    SMALL("small", 18, 16),
    NORMAL("normal", 22, 19),
    LARGE("large", 25, 22),
    EXTRA_LARGE("xlarge", 28, 25);

    companion object {
        fun fromId(id: String): LyricsFontSizePref =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: NORMAL
    }
}

fun buildQuickVideoFormatSelector(
    codec: QuickVideoCodec,
    resolution: QuickVideoResolution,
    audioFormat: QuickVideoAudioFormat,
): String {
    val rawAudioSel = audioFormat.ytdlpAudioSelector
    val audioSel = if ('/' in rawAudioSel) "($rawAudioSel)" else rawAudioSel
    val hFilter = resolution.maxHeight?.let { "[height<=$it]" }.orEmpty()
    if (resolution == QuickVideoResolution.LOW) {
        return when (codec) {
            QuickVideoCodec.LEGACY_H264 ->
                "bv*[height<=240][vcodec^=avc1]+$audioSel/bv*[height<=240][ext=mp4]+ba[ext=m4a]/wv*+wa/worst"
            QuickVideoCodec.MODERN_AV1_VP9_H265 ->
                "bv*[height<=240][vcodec~='^(av01|vp0?9|hev1|hvc1)']+$audioSel/bv*[height<=240]+$audioSel/wv*+wa/worst"
        }
    }
    return when (codec) {
        QuickVideoCodec.LEGACY_H264 -> {
            val combinedFallback = if (hFilter.isEmpty()) "hd/sd/b[ext=mp4]" else "b$hFilter[ext=mp4]/hd/sd/b[ext=mp4]"
            "bv*$hFilter[vcodec^=avc1]+$audioSel/" +
                "bv*$hFilter[vcodec^=avc1]+ba/" +
                "$combinedFallback/" +
                "bv*$hFilter[ext=mp4][vcodec!^=av01]+ba[ext=m4a]/" +
                "bv*$hFilter[ext=mp4]+ba[ext=m4a]/" +
                "bv*$hFilter+ba/best"
        }
        QuickVideoCodec.MODERN_AV1_VP9_H265 ->
            "bv*$hFilter[vcodec~='^(av01|vp0?9|hev1|hvc1)']+$audioSel/bv*$hFilter[vcodec~='^(av01|vp0?9|hev1|hvc1)']+ba/bv*$hFilter+$audioSel/bv*$hFilter+ba/best"
    }
}

data class SettingsState(
    val language: AppLanguage = AppLanguage.SYSTEM,
    val uiStyle: UiStyle = UiStyle.MATERIAL,
    val darkMode: DarkModePref = DarkModePref.SYSTEM,
    val accentColor: AccentColorPref = AccentColorPref.DYNAMIC,
    val startupPage: StartupPage = StartupPage.HOME,
    val clipboardAutoDetect: Boolean = true,
    val autoAnalyzeOnPaste: Boolean = false,
    val autoClearInput: Boolean = false,
    val incognitoMode: Boolean = false,
    val completionSound: Boolean = true,
    val hapticFeedback: Boolean = true,
    val transitionAnimations: Boolean = true,
    val filenameTemplate: FilenameTemplate = FilenameTemplate.TITLE,
    val overwriteMode: OverwriteMode = OverwriteMode.RENAME,
    val batchPhotoMode: BatchPhotoMode = BatchPhotoMode.ALL,
    val autoDownloadOnAnalyze: Boolean = false,
    val wifiOnlyDownloads: Boolean = false,
    val autoRetryFailed: Boolean = true,
    val maxRetryCount: Int = 3,
    val maxConcurrentDownloads: Int = 2,
    val timeoutSeconds: Int = 30,
    val enableHeaderSpoofing: Boolean = true,
    val bypassSsl: Boolean = false,
    val forceIpv4: Boolean = false,
    val customDoh: String = "",
    val customUserAgent: String = "",
    val historyLimit: Int = 0, // 0 = Unlimited
    val autoClearHistoryDays: Int = 0, // 0 = Off
    val autoClearCacheDays: Int = 0, // 0 = Off
    val autoPlayMedia: Boolean = true,
    val autoLoopMedia: Boolean = true,
    val keepScreenAwake: Boolean = false,
    val youtubeServer: String = "ytdlp",
    val tiktokServer: String = "ytdlp",
    val instagramServer: String = "ytdlp",
    val twitterServer: String = "ytdlp",
    val spotifyServer: String = "ytdlp",
    val soundcloudServer: String = "ytdlp",
    val facebookServer: String = "ytdlp",
    val pinterestServer: String = "ytdlp",
    val threadsServer: String = "ytdlp",
    val shopeeServer: String = "ytdlp",
    val rulesAccepted: Boolean = false,
    val userGuideDismissed: Boolean = false,
    val updateIgnoredUntilMs: Long = 0L,
    val updateIgnoredVersion: String = "",
    val ytDlpChannel: YtDlpUpdateChannel = YtDlpUpdateChannel.STABLE,
    val autoUpdateYtDlp: Boolean = false,
    val embedThumbnail: Boolean = true,
    val embedMetadata: Boolean = true,
    val useAria2c: Boolean = false,
    val defaultAudioQuality: AudioQualityPref = AudioQualityPref.MP3_HIGH,
    val cookiesEnabled: Boolean = false,
    val quickModeEnabled: Boolean = false,
    val quickMediaType: QuickMediaType = QuickMediaType.VIDEO,
    val quickVideoCodec: QuickVideoCodec = QuickVideoCodec.LEGACY_H264,
    val quickVideoResolution: QuickVideoResolution = QuickVideoResolution.BEST,
    val quickVideoAudioFormat: QuickVideoAudioFormat = QuickVideoAudioFormat.M4A,
    val quickAudioQuality: AudioQualityPref = AudioQualityPref.MP3_HIGH,
    val quickPhotoMode: BatchPhotoMode = BatchPhotoMode.ALL,
    val quickSaveDirectAfterShare: Boolean = false,
    val quickSaveAutoCloseOnStart: Boolean = false,
    val quickSaveWindowMode: QuickSaveWindowMode = QuickSaveWindowMode.ANALYZE,
    val quickSaveMediaType: QuickMediaType = QuickMediaType.VIDEO,
    val quickSaveVideoCodec: QuickVideoCodec = QuickVideoCodec.LEGACY_H264,
    val quickSaveVideoResolution: QuickVideoResolution = QuickVideoResolution.BEST,
    val quickSaveVideoAudioFormat: QuickVideoAudioFormat = QuickVideoAudioFormat.M4A,
    val quickSaveAudioQuality: AudioQualityPref = AudioQualityPref.MP3_HIGH,
    val quickSavePhotoMode: BatchPhotoMode = BatchPhotoMode.ALL,
    val musicPauseAtDisconnect: Boolean = true,
    val musicStopOnLowBattery: Boolean = false,
    val musicLowBatteryPercent: Int = 15,
    val musicAutoPlayHeadset: Boolean = false,
    val musicAutoPlayBluetooth: Boolean = false,
    val musicResumeAfterCall: Boolean = true,
    val musicAlwaysPlayOnTop: Boolean = false,
    val musicMonoAudio: Boolean = false,
    val musicLoudnessNorm: Boolean = false,
    val musicGainDb: Int = 0,
    val musicLyricsAlignment: LyricsAlignmentMode = LyricsAlignmentMode.LEFT,
    val musicTtmlAnimMode: TtmlAnimationMode = TtmlAnimationMode.PER_CHAR,
    val musicLyricsFontSize: LyricsFontSizePref = LyricsFontSizePref.NORMAL,
    val musicLyricsShowTranslation: Boolean = true,
    val musicLyricsTapToSeek: Boolean = true,
    val musicPlaylistPillSwap: PlaylistPillSwapMode = PlaylistPillSwapMode.NONE,
    val playerStyling: PlayerStylingConfig = PlayerStylingConfig(),
    val playerStylePresets: List<PlayerStylePreset> = emptyList(),
    val activePlayerPresetId: String = "",
    val customVideoTreeUri: String = "",
    val customVideoFolderLabel: String = "",
    val customAudioTreeUri: String = "",
    val customAudioFolderLabel: String = "",
    val customImageTreeUri: String = "",
    val customImageFolderLabel: String = "",
    val sdCardEnabled: Boolean = false,
    val sdCardTreeUri: String = "",
    val sdCardFolderLabel: String = "",
    val downloadSubfolderMode: DownloadSubfolderMode = DownloadSubfolderMode.DEFAULT,
    val ytDlpSubtitlesEnabled: Boolean = false,
    val ytDlpEmbedSubtitles: Boolean = false,
    val ytDlpSubtitleLangs: String = "id,en",
    val ytDlpSponsorBlockEnabled: Boolean = false,
    val ytDlpSplitChapters: Boolean = false,
    val ytDlpVideoContainer: String = "mp4",
    val ytDlpCustomArgs: String = "",
) {
    fun asQuickSaveFormatSettings(): SettingsState = copy(
        quickMediaType = quickSaveMediaType,
        quickVideoCodec = quickSaveVideoCodec,
        quickVideoResolution = quickSaveVideoResolution,
        quickVideoAudioFormat = quickSaveVideoAudioFormat,
        quickAudioQuality = quickSaveAudioQuality,
        quickPhotoMode = quickSavePhotoMode,
    )

    fun defaultServerFor(platform: com.nimiyo.nativeapp.data.PlatformId?): String? = when (platform) {
        com.nimiyo.nativeapp.data.PlatformId.YOUTUBE -> youtubeServer
        com.nimiyo.nativeapp.data.PlatformId.TIKTOK -> tiktokServer
        com.nimiyo.nativeapp.data.PlatformId.INSTAGRAM -> instagramServer
        com.nimiyo.nativeapp.data.PlatformId.TWITTER -> twitterServer
        com.nimiyo.nativeapp.data.PlatformId.SPOTIFY -> spotifyServer
        com.nimiyo.nativeapp.data.PlatformId.SOUNDCLOUD -> soundcloudServer
        com.nimiyo.nativeapp.data.PlatformId.FACEBOOK -> facebookServer
        com.nimiyo.nativeapp.data.PlatformId.PINTEREST -> pinterestServer
        com.nimiyo.nativeapp.data.PlatformId.THREADS -> threadsServer
        com.nimiyo.nativeapp.data.PlatformId.SHOPEE -> shopeeServer
        else -> platform?.servers?.firstOrNull()?.first
    }

    fun effectiveTreeUriFor(category: com.nimiyo.nativeapp.data.MediaCategory): String {
        if (sdCardEnabled && sdCardTreeUri.isNotBlank()) return sdCardTreeUri
        return when (category) {
            com.nimiyo.nativeapp.data.MediaCategory.VIDEO -> customVideoTreeUri
            com.nimiyo.nativeapp.data.MediaCategory.AUDIO -> customAudioTreeUri
            com.nimiyo.nativeapp.data.MediaCategory.IMAGE -> customImageTreeUri
        }
    }
}

class AppSettings(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("nimiyo_native_settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(loadState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    val current: SettingsState get() = _state.value

    private fun loadState(): SettingsState {
        // Ensure upgraded installs from the forced-dark preview restore Material You System default once
        if (!prefs.getBoolean("migrated_material_system_v1", false)) {
            prefs.edit()
                .putString("uiStyle", "material")
                .putString("darkMode", "system")
                .putBoolean("migrated_material_system_v1", true)
                .apply()
        }
        return SettingsState(
            language = AppLanguage.fromCode(prefs.getString("language", "system") ?: "system"),
            uiStyle = UiStyle.fromId(prefs.getString("uiStyle", "material") ?: "material"),
            darkMode = DarkModePref.fromId(prefs.getString("darkMode", "system") ?: "system"),
            accentColor = AccentColorPref.fromId(prefs.getString("accentColor", "dynamic") ?: "dynamic"),
            startupPage = StartupPage.fromId(prefs.getString("startupPage", "home") ?: "home"),
            clipboardAutoDetect = prefs.getBoolean("clipboardAutoDetect", true),
            autoAnalyzeOnPaste = prefs.getBoolean("autoAnalyzeOnPaste", false),
            autoClearInput = prefs.getBoolean("autoClearInput", false),
            incognitoMode = prefs.getBoolean("incognitoMode", false),
            completionSound = prefs.getBoolean("completionSound", true),
            hapticFeedback = prefs.getBoolean("hapticFeedback", true),
            transitionAnimations = prefs.getBoolean("transitionAnimations", true),
            filenameTemplate = FilenameTemplate.fromId(prefs.getString("filenameTemplate", "title") ?: "title"),
            overwriteMode = OverwriteMode.fromId(prefs.getString("overwriteMode", "rename") ?: "rename"),
            batchPhotoMode = BatchPhotoMode.fromId(prefs.getString("batchPhotoMode", "all") ?: "all"),
            autoDownloadOnAnalyze = prefs.getBoolean("autoDownloadOnAnalyze", false),
            wifiOnlyDownloads = prefs.getBoolean("wifiOnlyDownloads", false),
            autoRetryFailed = prefs.getBoolean("autoRetryFailed", true),
            maxRetryCount = prefs.getInt("maxRetryCount", 3).coerceIn(1, 5),
            maxConcurrentDownloads = prefs.getInt("maxConcurrentDownloads", 2).coerceIn(1, 5),
            timeoutSeconds = prefs.getInt("timeoutSeconds", 30).coerceIn(5, 180),
            enableHeaderSpoofing = prefs.getBoolean("enableHeaderSpoofing", true),
            bypassSsl = prefs.getBoolean("bypassSsl", false),
            forceIpv4 = prefs.getBoolean("forceIpv4", false),
            customDoh = prefs.getString("customDoh", "") ?: "",
            customUserAgent = prefs.getString("customUserAgent", "") ?: "",
            historyLimit = prefs.getInt("historyLimit", 0),
            autoClearHistoryDays = prefs.getInt("autoClearHistoryDays", 0),
            autoClearCacheDays = prefs.getInt("autoClearCacheDays", 0),
            autoPlayMedia = prefs.getBoolean("autoPlayMedia", true),
            autoLoopMedia = prefs.getBoolean("autoLoopMedia", true),
            keepScreenAwake = prefs.getBoolean("keepScreenAwake", false),
            youtubeServer = prefs.getString("youtubeServer", "ytdlp") ?: "ytdlp",
            tiktokServer = prefs.getString("tiktokServer", "ytdlp") ?: "ytdlp",
            instagramServer = prefs.getString("instagramServer", "ytdlp") ?: "ytdlp",
            twitterServer = prefs.getString("twitterServer", "ytdlp") ?: "ytdlp",
            spotifyServer = prefs.getString("spotifyServer", "ytdlp") ?: "ytdlp",
            soundcloudServer = prefs.getString("soundcloudServer", "ytdlp") ?: "ytdlp",
            facebookServer = prefs.getString("facebookServer", "ytdlp") ?: "ytdlp",
            pinterestServer = prefs.getString("pinterestServer", "ytdlp") ?: "ytdlp",
            threadsServer = prefs.getString("threadsServer", "ytdlp") ?: "ytdlp",
            shopeeServer = prefs.getString("shopeeServer", "ytdlp") ?: "ytdlp",
            rulesAccepted = prefs.getBoolean("rulesAccepted", false),
            userGuideDismissed = prefs.getBoolean("userGuideDismissed", false),
            updateIgnoredUntilMs = prefs.getLong("updateIgnoredUntilMs", 0L),
            updateIgnoredVersion = prefs.getString("updateIgnoredVersion", "") ?: "",
            ytDlpChannel = YtDlpUpdateChannel.fromId(prefs.getString("ytDlpChannel", "stable") ?: "stable"),
            autoUpdateYtDlp = prefs.getBoolean("autoUpdateYtDlp", false),
            embedThumbnail = prefs.getBoolean("embedThumbnail", true),
            embedMetadata = prefs.getBoolean("embedMetadata", true),
            useAria2c = prefs.getBoolean("useAria2c", false),
            defaultAudioQuality = AudioQualityPref.fromId(prefs.getString("defaultAudioQuality", "mp3_320") ?: "mp3_320"),
            cookiesEnabled = prefs.getBoolean("cookiesEnabled", false),
            quickModeEnabled = prefs.getBoolean("quickModeEnabled", false),
            quickMediaType = QuickMediaType.fromId(prefs.getString("quickMediaType", "video") ?: "video"),
            quickVideoCodec = QuickVideoCodec.fromId(prefs.getString("quickVideoCodec", "h264") ?: "h264"),
            quickVideoResolution = QuickVideoResolution.fromId(prefs.getString("quickVideoResolution", "best") ?: "best"),
            quickVideoAudioFormat = QuickVideoAudioFormat.fromId(prefs.getString("quickVideoAudioFormat", "m4a") ?: "m4a"),
            quickAudioQuality = AudioQualityPref.fromId(prefs.getString("quickAudioQuality", "mp3_320") ?: "mp3_320"),
            quickPhotoMode = BatchPhotoMode.fromId(prefs.getString("quickPhotoMode", "all") ?: "all"),
            quickSaveDirectAfterShare = prefs.getBoolean("quickSaveDirectAfterShare", false),
            quickSaveAutoCloseOnStart = prefs.getBoolean("quickSaveAutoCloseOnStart", false),
            quickSaveWindowMode = QuickSaveWindowMode.fromId(prefs.getString("quickSaveWindowMode", "analyze") ?: "analyze"),
            quickSaveMediaType = QuickMediaType.fromId(prefs.getString("quickSaveMediaType", "video") ?: "video"),
            quickSaveVideoCodec = QuickVideoCodec.fromId(prefs.getString("quickSaveVideoCodec", "h264") ?: "h264"),
            quickSaveVideoResolution = QuickVideoResolution.fromId(prefs.getString("quickSaveVideoResolution", "best") ?: "best"),
            quickSaveVideoAudioFormat = QuickVideoAudioFormat.fromId(prefs.getString("quickSaveVideoAudioFormat", "m4a") ?: "m4a"),
            quickSaveAudioQuality = AudioQualityPref.fromId(prefs.getString("quickSaveAudioQuality", "mp3_320") ?: "mp3_320"),
            quickSavePhotoMode = BatchPhotoMode.fromId(prefs.getString("quickSavePhotoMode", "all") ?: "all"),
            musicPauseAtDisconnect = prefs.getBoolean("musicPauseAtDisconnect", true),
            musicStopOnLowBattery = prefs.getBoolean("musicStopOnLowBattery", false),
            musicLowBatteryPercent = prefs.getInt("musicLowBatteryPercent", 15).coerceIn(5, 50),
            musicAutoPlayHeadset = prefs.getBoolean("musicAutoPlayHeadset", false),
            musicAutoPlayBluetooth = prefs.getBoolean("musicAutoPlayBluetooth", false),
            musicResumeAfterCall = prefs.getBoolean("musicResumeAfterCall", true),
            musicAlwaysPlayOnTop = prefs.getBoolean("musicAlwaysPlayOnTop", false),
            musicMonoAudio = prefs.getBoolean("musicMonoAudio", false),
            musicLoudnessNorm = prefs.getBoolean("musicLoudnessNorm", false),
            musicGainDb = prefs.getInt("musicGainDb", 0).coerceIn(0, 9),
            musicLyricsAlignment = LyricsAlignmentMode.fromId(prefs.getString("musicLyricsAlignment", "left") ?: "left"),
            musicTtmlAnimMode = TtmlAnimationMode.fromId(prefs.getString("musicTtmlAnimMode", "per_char") ?: "per_char"),
            musicLyricsFontSize = LyricsFontSizePref.fromId(prefs.getString("musicLyricsFontSize", "normal") ?: "normal"),
            musicLyricsShowTranslation = prefs.getBoolean("musicLyricsShowTranslation", true),
            musicLyricsTapToSeek = prefs.getBoolean("musicLyricsTapToSeek", true),
            musicPlaylistPillSwap = PlaylistPillSwapMode.fromId(prefs.getString("musicPlaylistPillSwap", "none") ?: "none"),
            playerStyling = PlayerStylingConfig.fromJsonString(prefs.getString("playerStylingJson", "") ?: ""),
            playerStylePresets = PlayerStylePreset.listFromJsonString(prefs.getString("playerStylePresetsJson", "") ?: ""),
            activePlayerPresetId = prefs.getString("activePlayerPresetId", "") ?: "",
            customVideoTreeUri = prefs.getString("customVideoTreeUri", "") ?: "",
            customVideoFolderLabel = prefs.getString("customVideoFolderLabel", "") ?: "",
            customAudioTreeUri = prefs.getString("customAudioTreeUri", "") ?: "",
            customAudioFolderLabel = prefs.getString("customAudioFolderLabel", "") ?: "",
            customImageTreeUri = prefs.getString("customImageTreeUri", "") ?: "",
            customImageFolderLabel = prefs.getString("customImageFolderLabel", "") ?: "",
            sdCardEnabled = prefs.getBoolean("sdCardEnabled", false),
            sdCardTreeUri = prefs.getString("sdCardTreeUri", "") ?: "",
            sdCardFolderLabel = prefs.getString("sdCardFolderLabel", "") ?: "",
            downloadSubfolderMode = DownloadSubfolderMode.fromId(
                prefs.getString("downloadSubfolderMode", "default") ?: "default",
            ),
            ytDlpSubtitlesEnabled = prefs.getBoolean("ytDlpSubtitlesEnabled", false),
            ytDlpEmbedSubtitles = prefs.getBoolean("ytDlpEmbedSubtitles", false),
            ytDlpSubtitleLangs = prefs.getString("ytDlpSubtitleLangs", "id,en") ?: "id,en",
            ytDlpSponsorBlockEnabled = prefs.getBoolean("ytDlpSponsorBlockEnabled", false),
            ytDlpSplitChapters = prefs.getBoolean("ytDlpSplitChapters", false),
            ytDlpVideoContainer = prefs.getString("ytDlpVideoContainer", "mp4") ?: "mp4",
            ytDlpCustomArgs = prefs.getString("ytDlpCustomArgs", "") ?: "",
        )
    }

    fun update(transform: (SettingsState) -> SettingsState) {
        val next = transform(_state.value)
        saveState(next)
    }

    fun resetToDefaults() {
        saveState(SettingsState(rulesAccepted = _state.value.rulesAccepted))
    }

    private fun saveState(next: SettingsState) {
        _state.value = next
        prefs.edit().apply {
            putString("language", next.language.code)
            putString("uiStyle", next.uiStyle.id)
            putString("darkMode", next.darkMode.id)
            putString("accentColor", next.accentColor.id)
            putString("startupPage", next.startupPage.id)
            putBoolean("clipboardAutoDetect", next.clipboardAutoDetect)
            putBoolean("autoAnalyzeOnPaste", next.autoAnalyzeOnPaste)
            putBoolean("autoClearInput", next.autoClearInput)
            putBoolean("incognitoMode", next.incognitoMode)
            putBoolean("completionSound", next.completionSound)
            putBoolean("hapticFeedback", next.hapticFeedback)
            putBoolean("transitionAnimations", next.transitionAnimations)
            putString("filenameTemplate", next.filenameTemplate.id)
            putString("overwriteMode", next.overwriteMode.id)
            putString("batchPhotoMode", next.batchPhotoMode.id)
            putBoolean("autoDownloadOnAnalyze", next.autoDownloadOnAnalyze)
            putBoolean("wifiOnlyDownloads", next.wifiOnlyDownloads)
            putBoolean("autoRetryFailed", next.autoRetryFailed)
            putInt("maxRetryCount", next.maxRetryCount)
            putInt("maxConcurrentDownloads", next.maxConcurrentDownloads)
            putInt("timeoutSeconds", next.timeoutSeconds)
            putBoolean("enableHeaderSpoofing", next.enableHeaderSpoofing)
            putBoolean("bypassSsl", next.bypassSsl)
            putBoolean("forceIpv4", next.forceIpv4)
            putString("customDoh", next.customDoh)
            putString("customUserAgent", next.customUserAgent)
            putInt("historyLimit", next.historyLimit)
            putInt("autoClearHistoryDays", next.autoClearHistoryDays)
            putInt("autoClearCacheDays", next.autoClearCacheDays)
            putBoolean("autoPlayMedia", next.autoPlayMedia)
            putBoolean("autoLoopMedia", next.autoLoopMedia)
            putBoolean("keepScreenAwake", next.keepScreenAwake)
            putString("youtubeServer", next.youtubeServer)
            putString("tiktokServer", next.tiktokServer)
            putString("instagramServer", next.instagramServer)
            putString("twitterServer", next.twitterServer)
            putString("spotifyServer", next.spotifyServer)
            putString("soundcloudServer", next.soundcloudServer)
            putString("facebookServer", next.facebookServer)
            putString("pinterestServer", next.pinterestServer)
            putString("threadsServer", next.threadsServer)
            putString("shopeeServer", next.shopeeServer)
            putBoolean("rulesAccepted", next.rulesAccepted)
            putBoolean("userGuideDismissed", next.userGuideDismissed)
            putLong("updateIgnoredUntilMs", next.updateIgnoredUntilMs)
            putString("updateIgnoredVersion", next.updateIgnoredVersion)
            putString("ytDlpChannel", next.ytDlpChannel.id)
            putBoolean("autoUpdateYtDlp", next.autoUpdateYtDlp)
            putBoolean("embedThumbnail", next.embedThumbnail)
            putBoolean("embedMetadata", next.embedMetadata)
            putBoolean("useAria2c", next.useAria2c)
            putString("defaultAudioQuality", next.defaultAudioQuality.id)
            putBoolean("cookiesEnabled", next.cookiesEnabled)
            putBoolean("quickModeEnabled", next.quickModeEnabled)
            putString("quickMediaType", next.quickMediaType.id)
            putString("quickVideoCodec", next.quickVideoCodec.id)
            putString("quickVideoResolution", next.quickVideoResolution.id)
            putString("quickVideoAudioFormat", next.quickVideoAudioFormat.id)
            putString("quickAudioQuality", next.quickAudioQuality.id)
            putString("quickPhotoMode", next.quickPhotoMode.id)
            putBoolean("quickSaveDirectAfterShare", next.quickSaveDirectAfterShare)
            putBoolean("quickSaveAutoCloseOnStart", next.quickSaveAutoCloseOnStart)
            putString("quickSaveWindowMode", next.quickSaveWindowMode.id)
            putString("quickSaveMediaType", next.quickSaveMediaType.id)
            putString("quickSaveVideoCodec", next.quickSaveVideoCodec.id)
            putString("quickSaveVideoResolution", next.quickSaveVideoResolution.id)
            putString("quickSaveVideoAudioFormat", next.quickSaveVideoAudioFormat.id)
            putString("quickSaveAudioQuality", next.quickSaveAudioQuality.id)
            putString("quickSavePhotoMode", next.quickSavePhotoMode.id)
            putBoolean("musicPauseAtDisconnect", next.musicPauseAtDisconnect)
            putBoolean("musicStopOnLowBattery", next.musicStopOnLowBattery)
            putInt("musicLowBatteryPercent", next.musicLowBatteryPercent.coerceIn(5, 50))
            putBoolean("musicAutoPlayHeadset", next.musicAutoPlayHeadset)
            putBoolean("musicAutoPlayBluetooth", next.musicAutoPlayBluetooth)
            putBoolean("musicResumeAfterCall", next.musicResumeAfterCall)
            putBoolean("musicAlwaysPlayOnTop", next.musicAlwaysPlayOnTop)
            putBoolean("musicMonoAudio", next.musicMonoAudio)
            putBoolean("musicLoudnessNorm", next.musicLoudnessNorm)
            putInt("musicGainDb", next.musicGainDb.coerceIn(0, 9))
            putString("musicLyricsAlignment", next.musicLyricsAlignment.id)
            putString("musicTtmlAnimMode", next.musicTtmlAnimMode.id)
            putString("musicLyricsFontSize", next.musicLyricsFontSize.id)
            putBoolean("musicLyricsShowTranslation", next.musicLyricsShowTranslation)
            putBoolean("musicLyricsTapToSeek", next.musicLyricsTapToSeek)
            putString("musicPlaylistPillSwap", next.musicPlaylistPillSwap.id)
            putString("playerStylingJson", next.playerStyling.toJson().toString())
            putString("playerStylePresetsJson", PlayerStylePreset.listToJsonString(next.playerStylePresets))
            putString("activePlayerPresetId", next.activePlayerPresetId)
            putString("customVideoTreeUri", next.customVideoTreeUri)
            putString("customVideoFolderLabel", next.customVideoFolderLabel)
            putString("customAudioTreeUri", next.customAudioTreeUri)
            putString("customAudioFolderLabel", next.customAudioFolderLabel)
            putString("customImageTreeUri", next.customImageTreeUri)
            putString("customImageFolderLabel", next.customImageFolderLabel)
            putBoolean("sdCardEnabled", next.sdCardEnabled)
            putString("sdCardTreeUri", next.sdCardTreeUri)
            putString("sdCardFolderLabel", next.sdCardFolderLabel)
            putString("downloadSubfolderMode", next.downloadSubfolderMode.id)
            putBoolean("ytDlpSubtitlesEnabled", next.ytDlpSubtitlesEnabled)
            putBoolean("ytDlpEmbedSubtitles", next.ytDlpEmbedSubtitles)
            putString("ytDlpSubtitleLangs", next.ytDlpSubtitleLangs)
            putBoolean("ytDlpSponsorBlockEnabled", next.ytDlpSponsorBlockEnabled)
            putBoolean("ytDlpSplitChapters", next.ytDlpSplitChapters)
            putString("ytDlpVideoContainer", next.ytDlpVideoContainer)
            putString("ytDlpCustomArgs", next.ytDlpCustomArgs)
        }.apply()
    }
}


enum class LyricsFontPref(val id: String, val label: String) {
    DEFAULT("default", "Default"),
    SYSTEM("system", "Sistem"),
    SERIF("serif", "Serif"),
    MONOSPACE("mono", "Monospace"),
    CURSIVE("cursive", "Cursive"),
    CUSTOM("custom", "Custom (.ttf / .otf)");

    companion object {
        fun fromId(id: String): LyricsFontPref =
            entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

enum class LyricsFontWeightPref(val id: String, val label: String) {
    NORMAL("normal", "Normal"),
    MEDIUM("medium", "Medium"),
    BOLD("bold", "Bold"),
    EXTRA_BOLD("extra_bold", "Extra Bold");

    companion object {
        fun fromId(id: String): LyricsFontWeightPref =
            entries.firstOrNull { it.id == id } ?: BOLD
    }
}

enum class LyricsBackdropMode(val id: String, val label: String) {
    ALBUM_BLUR("album_blur", "Default (Album Blur)"),
    CUSTOM_MEDIA("custom_media", "Custom Media (Img/GIF/Video)"),
    SOLID_BLACK("solid_black", "Solid Black");

    companion object {
        fun fromId(id: String): LyricsBackdropMode =
            entries.firstOrNull { it.id == id } ?: ALBUM_BLUR
    }
}

data class PlayerStylingConfig(
    val fontPref: LyricsFontPref = LyricsFontPref.DEFAULT,
    val customFontPath: String = "",
    val customFontName: String = "",
    val fontWeight: LyricsFontWeightPref = LyricsFontWeightPref.BOLD,
    val textShadow: Boolean = false,
    val backdropMode: LyricsBackdropMode = LyricsBackdropMode.ALBUM_BLUR,
    val customMediaUri: String = "",
    val customMediaIsVideo: Boolean = false,
    val customMediaLabel: String = "",
    val mediaOffsetX: Float = 0f,
    val mediaOffsetY: Float = 0f,
    val mediaScale: Float = 1f,
    val mediaBlurPercent: Float = 0f,
    val mediaOpacity: Float = 0.80f,
    val edgeFadeEnabled: Boolean = true,
    val edgeFadeStrength: Float = 0.75f,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("fontPref", fontPref.id)
        put("customFontPath", customFontPath)
        put("customFontName", customFontName)
        put("fontWeight", fontWeight.id)
        put("textShadow", textShadow)
        put("backdropMode", backdropMode.id)
        put("customMediaUri", customMediaUri)
        put("customMediaIsVideo", customMediaIsVideo)
        put("customMediaLabel", customMediaLabel)
        put("mediaOffsetX", mediaOffsetX.toDouble())
        put("mediaOffsetY", mediaOffsetY.toDouble())
        put("mediaScale", mediaScale.toDouble())
        put("mediaBlurPercent", mediaBlurPercent.toDouble())
        put("mediaOpacity", mediaOpacity.toDouble())
        put("edgeFadeEnabled", edgeFadeEnabled)
        put("edgeFadeStrength", edgeFadeStrength.toDouble())
    }

    companion object {
        fun fromJson(obj: JSONObject?): PlayerStylingConfig {
            if (obj == null) return PlayerStylingConfig()
            return PlayerStylingConfig(
                fontPref = LyricsFontPref.fromId(obj.optString("fontPref", "default")),
                customFontPath = obj.optString("customFontPath", ""),
                customFontName = obj.optString("customFontName", ""),
                fontWeight = LyricsFontWeightPref.fromId(obj.optString("fontWeight", "bold")),
                textShadow = obj.optBoolean("textShadow", false),
                backdropMode = LyricsBackdropMode.fromId(obj.optString("backdropMode", "album_blur")),
                customMediaUri = obj.optString("customMediaUri", ""),
                customMediaIsVideo = obj.optBoolean("customMediaIsVideo", false),
                customMediaLabel = obj.optString("customMediaLabel", ""),
                mediaOffsetX = obj.optDouble("mediaOffsetX", 0.0).toFloat().coerceIn(-1f, 1f),
                mediaOffsetY = obj.optDouble("mediaOffsetY", 0.0).toFloat().coerceIn(-1f, 1f),
                mediaScale = obj.optDouble("mediaScale", 1.0).toFloat().coerceIn(0.5f, 2.5f),
                mediaBlurPercent = obj.optDouble("mediaBlurPercent", 0.0).toFloat().coerceIn(0f, 0.25f),
                mediaOpacity = obj.optDouble("mediaOpacity", 0.80).toFloat().coerceIn(0f, 1f),
                edgeFadeEnabled = obj.optBoolean("edgeFadeEnabled", true),
                edgeFadeStrength = obj.optDouble("edgeFadeStrength", 0.75).toFloat().coerceIn(0.2f, 1f),
            )
        }

        fun fromJsonString(raw: String): PlayerStylingConfig {
            if (raw.isBlank()) return PlayerStylingConfig()
            return runCatching { fromJson(JSONObject(raw)) }.getOrDefault(PlayerStylingConfig())
        }
    }
}

data class PlayerStylePreset(
    val id: String,
    val name: String,
    val style: PlayerStylingConfig,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("style", style.toJson())
    }

    companion object {
        fun listToJsonString(presets: List<PlayerStylePreset>): String {
            val arr = JSONArray()
            presets.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }

        fun listFromJsonString(raw: String): List<PlayerStylePreset> {
            if (raw.isBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(raw)
                val list = mutableListOf<PlayerStylePreset>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("id", "")
                    if (id.isNotBlank()) {
                        val name = obj.optString("name", "Preset ${i + 1}")
                        val style = PlayerStylingConfig.fromJson(obj.optJSONObject("style"))
                        list.add(PlayerStylePreset(id = id, name = name, style = style))
                    }
                }
                list
            }.getOrDefault(emptyList())
        }
    }
}

enum class PlaylistPillSwapMode(val id: String) {
    NONE("none"),
    SONGS("songs"),
    ARTISTS("artists"),
    ALBUMS("albums");

    companion object {
        fun fromId(id: String): PlaylistPillSwapMode =
            entries.firstOrNull { it.id == id } ?: NONE
    }
}
