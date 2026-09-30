// NIMIYO Application Logic

// Capacitor Plugins
const { Filesystem, Directory } = window.Capacitor?.Plugins?.Filesystem ? window.Capacitor.Plugins : { Filesystem: null };
const { Clipboard } = window.Capacitor?.Plugins?.Clipboard ? window.Capacitor.Plugins : { Clipboard: null };
const { Haptics } = window.Capacitor?.Plugins?.Haptics ? window.Capacitor.Plugins : { Haptics: null };
const { Network } = window.Capacitor?.Plugins?.Network ? window.Capacitor.Plugins : { Network: null };

// Native-first, cross-platform Clipboard Reader
async function getClipboardText() {
  // 1. Try MediaSaver native Android ClipboardManager
  try {
    const MediaSaver = window.Capacitor?.Plugins?.MediaSaver;
    if (MediaSaver && typeof MediaSaver.readClipboard === "function") {
      const res = await MediaSaver.readClipboard();
      if (res && typeof res.value === "string" && res.value.trim().length > 0) {
        return res.value.trim();
      }
    }
  } catch (e) {
    console.warn("[CLIPBOARD] MediaSaver.readClipboard failed:", e);
  }

  // 2. Try Capacitor Clipboard Plugin
  try {
    const ClipboardPlugin = window.Capacitor?.Plugins?.Clipboard;
    if (ClipboardPlugin && typeof ClipboardPlugin.read === "function") {
      const res = await ClipboardPlugin.read();
      if (res && typeof res.value === "string" && res.value.trim().length > 0) {
        return res.value.trim();
      }
    }
  } catch (e) {
    console.warn("[CLIPBOARD] Capacitor Clipboard.read failed:", e);
  }

  // 3. Web navigator.clipboard fallback
  try {
    if (navigator?.clipboard?.readText) {
      const text = await navigator.clipboard.readText();
      if (text && text.trim().length > 0) {
        return text.trim();
      }
    }
  } catch (e) {
    console.warn("[CLIPBOARD] navigator.clipboard.readText failed:", e);
  }

  return "";
}

// Native-first, cross-platform Clipboard Writer
async function setClipboardText(text) {
  if (!text) return false;
  // 1. Try MediaSaver native
  try {
    const MediaSaver = window.Capacitor?.Plugins?.MediaSaver;
    if (MediaSaver && typeof MediaSaver.setClipboard === "function") {
      await MediaSaver.setClipboard({ value: text });
      return true;
    }
  } catch (_) { }

  // 2. Try Capacitor Clipboard
  try {
    const ClipboardPlugin = window.Capacitor?.Plugins?.Clipboard;
    if (ClipboardPlugin && typeof ClipboardPlugin.write === "function") {
      await ClipboardPlugin.write({ string: text });
      return true;
    }
  } catch (_) { }

  // 3. Web fallback
  try {
    if (navigator?.clipboard?.writeText) {
      await navigator.clipboard.writeText(text);
      return true;
    }
  } catch (_) { }

  return false;
}

// Translation dictionary
const translations = {
  en: {
    appTitle: "NIMIYO",
    inputPlaceholder: "Paste link here...",
    btnAnalyze: "ANALYZE LINK",
    btnAnalyzing: "ANALYZING...",
    btnCancel: "CANCEL",
    btnCancelAnalysis: "CANCEL",
    btnBack: "Back",
    platformsTitle: "Supported Platforms",
    downloadsTitle: "Download Links",
    btnDownload: "DOWNLOAD",
    btnDownloading: "DOWNLOADING...",
    btnDownloadAll: "DOWNLOAD ALL ({count})",
    historyTitle: "Download History",
    historyEmpty: "No download history yet.",
    btnClearHistory: "CLEAR HISTORY",
    headerHistory: "History Download",
    headerSettings: "Settings",
    tabHome: "HOME",
    tabPlayer: "PLAYER",
    tabHistory: "HISTORY",
    tabSettings: "SETTINGS",
    headerPlayer: "PLAYER",
    musicTabNimiyo: "NIMIYO",
    musicTabSongs: "SONGS",
    musicTabArtists: "ARTISTS",
    musicTabAlbums: "ALBUM",
    musicPermRequiredTitle: "Songs not detected",
    musicPermRequiredDesc: "Allow the app to manage all music files on the device. This is safe.",
    musicSearchPlaceholder: "Search songs, artists, albums...",
    musicNoSongs: "No local audio files found",
    musicNoNimiyoSongs: "No audio downloads in NIMIYO folder yet",
    musicNoArtists: "No artists found",
    musicNoAlbums: "No albums found",
    musicUnknownTitle: "Untitled Track",
    musicUnknownArtist: "Unknown Artist",
    musicUnknownAlbum: "Unknown Album",
    musicNowPlaying: "NOW PLAYING",
    musicUpNext: "Up Next in Queue:",
    musicQueueTitle: "Playback Queue",
    musicClearQueue: "Clear",
    musicEmptyQueue: "Queue is empty",
    musicLyricsBtn: "LYRICS",
    musicShowCover: "Cover",
    musicLyricsTranslation: "Translation",
    toastNoTranslationAvailable: "No lyrics translation available for this track",
    musicTapForLyrics: "Lyrics",
    musicTapToFlip: "Tap to view album cover",
    musicFetchingLyrics: "Searching lyrics online...",
    musicNoLyrics: "No lyrics found for this song",
    musicAddedToQueue: "added to queue",
    musicWillPlayNext: "will play next",
    musicPlayNow: "Play Now",
    musicPlayNext: "Play Next",
    musicAddToQueue: "Add to Queue",
    musicShuffleOn: "Shuffle On",
    musicShuffleOff: "Shuffle Off",
    musicRepeatOff: "Repeat Off",
    musicRepeatAll: "Repeat All",
    musicRepeatOne: "Repeat One",
    musicScanError: "Failed to scan audio library",
    musicPlaybackError: "Unsupported audio format or file moved",
    musicBtnShuffleAll: "Shuffle All",
    musicSortTitle: "Sort",
    musicSortTitleAsc: "Title (A to Z)",
    musicSortTitleDesc: "Title (Z to A)",
    musicSortArtistAsc: "Artist (A to Z)",
    musicSortAlbumAsc: "Album (A to Z)",
    musicSortDurDesc: "Duration (Longest)",
    musicSortDurAsc: "Duration (Shortest)",
    musicGoToArtist: "Go to Artist",
    musicGoToAlbum: "Go to Album",
    musicSongInfo: "Song Details",
    musicShareAudio: "Share Audio",
    musicDeleteTrack: "Delete File",
    confirmDeleteTrack: "Delete this audio file from device storage?",
    musicTrackDeleted: "Audio file deleted successfully",
    musicSleepTimerBtn: "TIMER",
    musicSleepTimerTitle: "Sleep Timer",
    musicTimerOff: "Off",
    musicTimerEndSong: "End of this Song",
    musicTimerOffToast: "Sleep timer turned off",
    musicTimerEndSongToast: "Will stop after current track finishes",
    musicSleepTimerSet: "Sleep timer set",
    musicSpeedTitle: "Playback Speed",
    mixtoolsTitle: "MixTools",
    mixtoolsResetAll: "Reset All",
    mixtoolsSpeedTitle: "Speed FX",
    mixtoolsSpeedDesc: "Adjust playback tempo",
    mixtoolsGuideSlowed: "Slowed",
    mixtoolsGuideNormal: "Normal",
    mixtoolsGuideSpeedUp: "SpeedUp",
    mixtoolsReverbTitle: "Reverb FX",
    mixtoolsReverbDesc: "3D spatial echo effect",
    mixtoolsReverbStadium: "▲ Wide",
    mixtoolsReverbClub: "▼ Tight",
    mixtoolsReverbDry: "◀ Dry",
    mixtoolsReverbWet: "Wet ▶",
    mixtoolsReverbHint: "Up = Wide • Right = Wet",
    mixtoolsResetSuccess: "MixTools reset to default",
    mixtoolsStatusDecay: "Decay",
    editLyricsTitle: "Edit Lyrics",
    editLyricsFormatHelp: "Format: <code>[00:00.00] Lyrics line here</code> (minutes:seconds.ms)",
    editLyricsPlaceholder: "[00:00.00] First lyric line...\n[00:15.30] Second lyric line...",
    editLyricsRefresh: "REFRESH",
    editLyricsRefreshTitle: "Reset & search new lyrics",
    editLyricsImport: "IMPORT",
    editLyricsImportTitle: "Import local .LRC lyrics file",
    editLyricsDownloadLrcTitle: "Download .LRC file",
    resetLyricsTitle: "Reset Lyrics Options",
    resetLyricsSubtitle: "Choose lyrics search source",
    resetLyricsOptDefaultTitle: "Auto Search (Default / LRCLIB)",
    resetLyricsOptDefaultDesc: "Clear local lyrics & re-fetch synced lyrics from online database",
    resetLyricsOptLyricsifyTitle: "Search on Lyricsify.com",
    resetLyricsOptLyricsifyDesc: "Open this song on Lyricsify via browser to copy .LRC lyrics",
    resetLyricsOptImportTitle: "Import Local .LRC File",
    resetLyricsOptImportDesc: "Select .lrc or .txt lyrics file stored on your device",
    toastSelectSongFirst: "Please select a song first",
    toastLyricsSearchingOnline: "Searching lyrics automatically (LRCLIB)...",
    toastLyricsResetSuccess: "Lyrics reset successfully from online database!",
    toastLyricsNotFoundOnline: "Lyrics not found in online database",
    toastLyricsFetchFailed: "Failed to fetch online lyrics",
    toastLyricsLyricsifyHint: "Lyrics reset. Copy .LRC lyrics from Lyricsify and paste here",
    toastLyricsImportSuccess: "Lyrics {file} imported successfully",
    toastLyricsImportFailed: "Failed to read lyrics file",
    toastLyricsEmptyWarning: "Lyrics cannot be empty",
    toastLyricsSavedSuccess: "Lyrics edited and saved locally",
    toastLyricsNoDownload: "No lyrics available to download",
    toastLyricsSavedTo: "Lyrics saved: {file} in Nimiyo/LyricsYo",
    toastLyricsDownloaded: "Lyrics {file} downloaded successfully",
    toastLyricsDownloadFailed: "Failed to download lyrics",
    btnReset: "Reset",
    musicSongInfoTitle: "Song Details",
    musicLabelTitle: "Title",
    musicLabelArtist: "Artist",
    musicLabelAlbum: "Album",
    musicLabelDuration: "Duration",
    musicLabelFileSize: "File Size",
    musicLabelFormat: "Format",
    musicLabelFilePath: "File Path",
    musicSortModalTitle: "Sort Songs By",
    musicShuffleAllStarted: "Shuffling all songs",
    musicTapForLyrics: "Lyrics",
    musicTapToFlip: "Tap for cover",
    labelUiTheme: "UI Design",
    optThemeNeobrutalism: "Neo-Brutalism (Bold)",
    optThemeSoftUi: "Soft UI (Neumorphic Glass)",
    settingsTitle: "Settings",
    groupGeneral: "General",
    menuGeneralDesc: "Language, Auto-Paste & Analyze",
    labelLanguage: "Language",
    labelAutoPaste: "Auto-Paste Link",
    labelAutoAnalyze: "Auto-Analyze on Paste",
    labelAutoClearInput: "Auto-Clear Input",
    labelIncognito: "Incognito Mode",
    groupAppearance: "Appearance UIUX",
    menuAppearanceDesc: "Dark Mode, Sound & Haptics",
    labelDarkMode: "Dark Mode",
    labelCompletionSound: "Completion Sound",
    labelHaptic: "Vibration & Haptics",
    labelAppFont: "App Font",
    optFontMisans: "MiSans (Default)",
    optFontInter: "Inter Modern",
    optFontOutfit: "Outfit Display",
    optFontMono: "Space Mono",
    groupStorage: "Storage & Download",
    menuStorageDesc: "Filename, Duplicates & Cache",
    labelStoragePaths: "Storage Paths",
    labelFilenameTemplate: "Filename Format",
    optFilenameTitle: "Title Only",
    optFilenameTitlePlatform: "Title + Platform",
    optFilenameTitleDate: "Title + Date",
    labelOverwriteMode: "Duplicate Files",
    optOverwriteRename: "Auto-Rename (_1, _2)",
    optOverwriteOverwrite: "Overwrite Existing",
    optOverwriteSkip: "Skip if Exists",
    labelConcurrentDl: "Concurrent Downloads",
    optConcurrent1: "1 (Sequential)",
    optConcurrent2: "2 at once",
    optConcurrent3: "3 at once",
    optConcurrent5: "5 at once",
    labelBatchPhotoMode: "Batch Photo Mode",
    optBatchPhotoAll: "Download All Photos",
    optBatchPhotoFirst: "First Photo Only",
    labelAutoDownload: "Auto-Download on Analyze",
    labelTotalStorage: "Total Media Size",
    btnClearCache: "CLEAR APP CACHE",
    btnWipeData: "WIPE ALL DATA & RESET",
    groupNetwork: "Network & Performance",
    menuNetworkDesc: "Auto-Retry, Wi-Fi & DNS",
    labelAutoRetry: "Auto-Retry Download",
    labelMaxRetry: "Max Retry Count",
    optRetry1: "1 (No Retry)",
    optRetry2: "2 Attempts",
    optRetry3: "3 Attempts",
    optRetry5: "5 Attempts",
    labelWifiOnly: "Download via Wi-Fi Only",
    labelDoh: "DNS over HTTPS",
    optDohOff: "Off (System DNS)",
    labelHeaderSpoofing: "Anti-403 Header Guard",
    groupAdvanced: "Advanced",
    menuAdvancedDesc: "Retention, Media Player & Reset",
    labelHistoryLimit: "History Retention Limit",
    optUnlimited: "Unlimited",
    optHistory50: "50 items",
    optHistory100: "100 items",
    optHistory200: "200 items",
    labelAutoClearDays: "Auto-Clear History",
    labelAutoClearCacheDays: "Auto-Clear Cache",
    optDaysOff: "Off",
    optDays1: "1 Day",
    optDays7: "7 Days",
    optDays30: "30 Days",
    optDays90: "90 Days",
    labelAutoPlay: "Auto-Play Media Player",
    labelAutoLoop: "Auto-Loop Media Player",
    labelKeepAwake: "Keep Screen Awake",
    btnResetSettings: "RESET ALL SETTINGS TO DEFAULT",
    groupAbout: "About & Help",
    menuAboutDesc: "Version, Info & Developer",
    aboutVersion: "Version 2.1.1 (Lite)",
    aboutDesc: "A premium, modern, and lightweight media downloader engine built on scrapr.",
    aboutThanks: "Thanks to:",
    toastClipboardEmpty: "Clipboard is empty or does not contain a text link.",
    toastInvalidUrl: "Please enter a valid link from a supported platform.",
    toastDetecting: "Detecting platform...",
    toastScraping: "Running scraper ({scraper})...",
    toastScrapeSuccess: "Analysis complete!",
    toastScrapeFail: "Scraper {scraper} failed: {message}",
    toastScrapeAllFailed: "Failed to analyze link. Please check URL or switch server in Settings.",
    toastUnsupportedPlatform: "Unsupported platform link or unrecognized URL format.",
    toastErrorOffline: "No internet connection. Please check your data or Wi-Fi.",
    toastErrorTimeout: "Connection timed out. Server or network is slow.",
    toastErrorSpotifyPlaylistPrivate: "Spotify playlist is private or not found. Make sure the playlist is set to Public on Spotify.",
    toastErrorSpotifyTrackNotFound: "Spotify track or album not found, private, or region-restricted.",
    toastErrorInstagramPrivate: "Instagram post or Reel is private, restricted, or has been deleted.",
    toastErrorTikTokPrivate: "TikTok video is private or has been removed by the creator.",
    toastErrorYouTubeUnavailable: "YouTube video or playlist is private, age-restricted, or unavailable.",
    toastErrorTwitterPrivate: "X (Twitter) post not found or the account is private.",
    toastErrorFacebookPrivate: "Facebook post is private, restricted, or requires login.",
    toastErrorMediaNotFound: "Media not found or content has been deleted/private.",
    toastErrorServerBlocked: "Scraper server is busy or protected. Please switch server in Settings.",
    toastAnalysisCancelled: "Analysis cancelled",
    toastCopiedTitle: "Title copied successfully!",
    toastCopiedDesc: "Description copied successfully!",
    toastDownloadingSingle: "Downloading {title}...",
    toastDownloadingMulti: "Downloading {count} files...",
    toastDownloadStart: "Starting download of {filename}...",
    toastDownloadSuccess: "File saved successfully!",
    toastDownloadFail: "Download failed after multiple attempts.",
    toastDownloadFailSwitchServer: "Download Failed - Switching Server to {server}...",
    toastDownloadFailManualServer: "Download Failed - Switch Server",
    toastDownloadCancelled: "Download cancelled by user.",
    toastDownloadCancelledItem: "Download cancelled: {title}",
    toastBatchCompleted: "Downloads completed! ({count} items saved to Downloads/Nimiyo/)",
    toastHistoryCleared: "History cleared successfully.",
    toastSettingsSaved: "Settings saved.",
    toastCacheCleared: "App cache cleared successfully.",
    toastWipeCompleted: "All app data and history have been wiped.",
    toastPlaying: "Playing : {title}",
    toastOpeningFolder: "Opening folder Downloads/Nimiyo/{folder}...",
    toastLocation: "Location: Downloads/Nimiyo/{folder}/{filename}",
    confirmResetSettings: "Are you sure you want to reset all settings to defaults?",
    confirmWipeData: "Are you sure you want to wipe all history, cache, and data?",
    statusConnecting: "Connecting...",
    statusDownloading: "Downloading... {percent}%",
    statusSaving: "Saving file to storage...",
    statusReadyToDownload: "Ready to download",
    statusNoUrl: "No URL",
    noDownloadLinks: "No download links available.",
    balloonSheetTitle: "Active Downloads",
    balloonCancelAll: "CANCEL ALL",
    queuePending: "Pending...",
    queueDownloading: "Downloading... {percent}%",
    queueCompleted: "Completed",
    queueCancelTitle: "Cancel",
    toggleSeeMore: "See more",
    toggleSeeLess: "See less",
    descLabel: "Description",
    btnCopy: "COPY",
    historyPlayTitle: "Play",
    historyFolderTitle: "Open Folder",
    historyDeleteTitle: "Delete",
    mediaPlayerTitle: "Media Player",
    downloadModalTitle: "Downloading...",
    badgePhoto: "PHOTO",
    badgeVideo: "VIDEO",
    badgeAudio: "AUDIO",
    labelAutoUpdate: "Auto-Update & Install APK",
    hintAutoUpdate: "Allow automatic APK update installation",
    btnPermissionAllow: "Allow",
    btnPermissionGranted: "Enabled",
    btnCheckUpdate: "Check for Updates",
    updateModalTitle: "Update Available 🚀",
    updateChangelogTitle: "Changelog:",
    updateDownloading: "Downloading update...",
    btnLater: "Remind Me Later",
    btnRemindLater: "Remind Me Later",
    remindScheduleTitle: "Remind Update in:",
    remind1Day: "1 Day",
    remind3Days: "3 Days",
    remind1Week: "1 Week",
    toastRemindScheduled: "Update reminder set for {time}.",
    btnUpdateNow: "Update and Install",
    btnUpdateAndInstall: "Update and Install",
    btnManualDownload: "Download Manual Install",
    labelAccentColor: "UI Accent Color",
    optAccentYellow: "Yellow (Default app)",
    optAccentBlue: "Blue",
    optAccentRed: "Red",
    optAccentGray: "Gray",
    toastDownloadingApkManual: "Downloading APK file to Downloads folder...",
    toastManualApkDownloaded: "APK file downloaded to Downloads folder. Please install manually.",
    toastAppUpToDate: "You are using the latest version (v{version}).",
    toastUpdateChecking: "Checking for updates...",
    toastUpdateCheckFailed: "Could not check for updates. Check internet connection.",
    toastPressBackAgain: "Press back again to exit.",
    aboutFollowMe: "Follow Developer:",
    btnSupportMe: "Support Me (Trakteer)",
    supportDesc: "Support NIMIYO development & ongoing updates",
    btnRules: "Rules & Terms",
    rulesModalTitle: "Terms of Use & Guidelines",
    rulesIntroText: "Welcome to NIMIYO Downloader. By accessing or using this app, you agree to comply with the following terms & guidelines:",
    rulesBadgeProhibited: "❌ STRICTLY PROHIBITED",
    rulesProhibitedTitle: "Prohibited Content:",
    rulesItemNsfw: "Pornography & NSFW (18+): Any sexually explicit, nudity, or adult media.",
    rulesItemViolence: "Violence & Gore: Extreme violence, physical harm, brutality, or dangerous acts.",
    rulesItemHate: "Hate Speech & Harassment: Discrimination, hate speech, or cyberbullying.",
    rulesItemIllegal: "Illegal Content: Unlawful acts, extremist propaganda, or narcotics.",
    rulesBadgeDisclaimer: "⚖️ TERMS & COPYRIGHT",
    rulesDisclaimerTitle: "Copyright & Disclaimer:",
    rulesItemPersonal: "NIMIYO is a tool for personal offline archiving purposes only.",
    rulesItemCopyright: "Respect creator copyright. Commercial redistribution without permission is strictly prohibited.",
    rulesItemLegal: "Developers do not host any media files. Users assume full legal liability for submitted URLs.",
    btnAgreeRules: "I AGREE & UNDERSTAND",

    "btnAllowAudioAccess": "Allow Access",
    "playlistHeaderTitle": "PLAYLIST",
    "playlistBtnAdd": "New Playlist",
    "playlistEmptyTitle": "No Playlists Yet",
    "playlistEmptyDesc": "Collect your favorite tracks from NIMIYO, Songs, Artists, and Albums into custom playlists.",
    "playlistBtnCreateNew": "+ Create New Playlist",
    "playlistDetailPlayAll": "Play All",
    "playlistDetailShuffle": "Shuffle",
    "playlistDetailAddSongs": "Add Songs",
    "playlistDetailTracksTitle": "Tracks",
    "playlistDetailDeletePlaylist": "Delete Playlist",
    "playlistDetailEmptyTitle": "This Playlist is Empty",
    "playlistDetailEmptyDesc": "Add songs now from NIMIYO, Songs, Artists, or Albums.",
    "playlistDetailPickSongsBtn": "+ Pick Songs Now",
    "playlistBtnSelect": "Select",
    "playlistBtnCancel": "Cancel",
    "playlistActionRemove": "Remove",
    "playlistRemoveSelected": "Remove",
    "playlistSelectAll": "Select All",
    "playlistDeselectAll": "Deselect All",
    "playlistTracksRemovedToast": "{count} tracks removed from playlist",
    "playlistConfirmRemoveSelected": "Remove {count} songs from this playlist?",
    "playlistReorderDone": "Playlist order updated",
    "playlistDragReorderHint": "Drag to reorder",
    "playlistSelectedCount": "{count} selected",
    "playlistSongsCount": "Songs",
    "playlistCountSubtitle": "Playlists",
    "pickerModalTitle": "Select Songs",
    "pickerSearchPlaceholder": "Search tracks in collection...",
    "pickerTabAll": "ALL",
    "pickerSelectAll": "Select All",
    "pickerDeselectAll": "Deselect All",
    "pickerSelected": "selected",
    "pickerAvailable": "songs available",
    "pickerBtnAdd": "Add",
    "pickerNoMatch": "No songs match the filter or search.",
    "createPlaylistTitle": "New Playlist",
    "renamePlaylistTitle": "Rename Playlist",
    "createPlaylistDesc": "Give a name for your new playlist:",
    "createPlaylistPlaceholder": "e.g., Favorites, Chill, Roadtrip...",
    "btnSave": "Save",
    "addToPlaylistTitle": "Add to Playlist",
    "addToPlaylistSubtitle": "Choose target playlist",
    "quickItemAlreadyAdded": "✓ Added",
    "quickItemAdd": "+ Add",
    "selectionAll": "All",
    "selectionMove": "Move",
    "selectionDelete": "Delete",
    "selectionCancel": "Cancel",
    "selectionTitleMove": "Move selected songs",
    "selectionTitleDelete": "Permanently delete selected songs",
    "moveTargetModalTitle": "Move Songs To",
    "moveTargetModalDesc": "Select destination for selected songs:",
    "moveOptionPlaylistTitle": "Playlist",
    "moveOptionPlaylistDesc": "Collect into a playlist",
    "moveOptionNimiyoTitle": "Nimiyo",
    "moveOptionNimiyoDesc": "Move audio files into NIMIYO music folder",
    "deleteConfirmModalTitle": "Permanent Delete",
    "deleteConfirmPrompt": "Do you want to permanently delete {count} selected audio files? This will remove the files from device storage.",
    "btnYes": "Yes",
    "musicPermGranted": "Music access granted, loading tracks...",
    "musicQueueCleared": "Queue cleared",
    "musicDeleteProtectedError": "Delete failed: file is protected by Android system",
    "playlistNameRequired": "Playlist name cannot be empty",
    "playlistNameUpdated": "Playlist name updated",
    "playlistCreatedToast": "Playlist \"{name}\" created",
    "playlistDeletedToast": "Playlist deleted",
    "playlistSongRemoved": "Song removed from playlist",
    "playlistEmptyToast": "This playlist is empty",
    "playlistSongNotFound": "Song in playlist not found",
    "playlistPlayingToast": "Playing: {name}",
    "playlistUpdatedToast": "Playlist \"{name}\" updated ({count} songs)",
    "playlistSongAlreadyIn": "Song already exists in \"{name}\"",
    "playlistSongAddedTo": "Added to \"{name}\"",
    "selectionMinOneSong": "Select at least 1 song first",
    "selectionAddedToPlaylistToast": "{count} songs added to \"{name}\"",
    "selectionMovedToNimiyoToast": "{count} songs moved to Nimiyo folder",
    "selectionAlreadyInNimiyoToast": "All selected songs are already in Nimiyo folder",
    "selectionPermanentDeletedToast": "{count} audio files permanently deleted",
    "selectionNoFilesDeletedToast": "No files were deleted",
    "manageStoragePermRequired": "Storage Permission Required:\n\nOn Android 11+, Nimiyo needs 'All files access' permission to permanently delete and manage audio files on your device.\n\nOpen Settings now to enable this permission?",
    "confirmDeletePlaylist": "Are you sure you want to delete this playlist?",
    "musicAddToPlaylist": "Add to Playlist",
    "musicMoveToNimiyo": "Move to Nimiyo",
    "musicSelectSong": "Select Song",
    "musicDurationHour": "hr",
    "musicDurationMin": "min",
    "about-text": "Nimidz is a fast and versatile media downloader. Built with love by coflyn.",
    "anim-fast": "Fast",
    "anim-normal": "Normal",
    "anim-off": "Off",
    "anim-slow": "Slow",
    "backup-monthly": "Monthly",
    "backup-off": "Off",
    "backup-weekly": "Weekly",
    "batch-download-all": "Download All",
    "batch-modal-title": "Batch Download Queue",
    "batch-photo-all": "Download All Photos",
    "batch-photo-first": "Download First Photo Only",
    "batch-photo-pdf": "Combine into Single PDF",
    "btn-analyze": "Analyze",
    "btn-analyze-batch": "Analyze Batch",
    "btn-cancel": "Cancel",
    "btn-cancel-download": "CANCEL DOWNLOAD",
    "btn-check": "CHECK",
    "btn-clear": "CLEAR",
    "btn-clear-all": "CLEAR ALL",
    "btn-close": "CLOSE",
    "btn-delete": "DELETE",
    "btn-done": "DONE",
    "btn-download-all-title": "Download All",
    "btn-edit": "EDIT",
    "btn-open-settings": "Open settings",
    "btn-processing": "Processing...",
    "btn-report": "REPORT",
    "btn-reset": "RESET",
    "btn-reset-default": "RESET TO DEFAULT",
    "btn-reset-settings": "RESET",
    "btn-share": "SHARE",
    "btn-stop": "Stop",
    "btn-update": "UPDATE",
    "btn-view": "VIEW",
    "concurrent-1": "1 (Sequential)",
    "concurrent-2": "2 at once",
    "concurrent-3": "3 at once",
    "concurrent-5": "5 at once",
    "confirm-reset-settings": "Reset all settings to their defaults? This will not delete your history or downloaded files.",
    "days-1": "1 Day",
    "days-30": "30 Days",
    "days-7": "7 Days",
    "days-90": "90 Days",
    "days-off": "Off",
    "desc-clearcache": "Remove temporary thumbnails and image previews to free up space. Your downloaded media stays safe. Continue?",
    "desc-wipedata": "Reset history, settings, and cache. This will NOT delete your downloaded videos or music in your Gallery. Continue?",
    "dl-stats-total": "Total Downloads",
    "doh-cloudflare": "Cloudflare (1.1.1.1)",
    "doh-google": "Google (8.8.8.8)",
    "doh-off": "Off (System DNS)",
    "download-all-complete": "All ${count} items queued for download!",
    "downloading-progress": "Downloading...",
    "err-yt-playlist-source": "YouTube playlists require 'ytmp3.gg' server.",
    "filename-default": "Default",
    "filename-title": "Title Only",
    "filename-title-date": "Title + Date",
    "filename-title-platform": "Title + Platform",
    "font-default": "Inter",
    "font-display": "Display Bold",
    "font-jakarta": "Plus Jakarta Sans",
    "font-mono": "Modern Mono",
    "font-serif": "Classic Serif",
    "guide-step-1": "Copy any media link from TikTok, Instagram, YouTube, Twitter/X, Douyin & 14+ platforms.",
    "guide-step-2": "Tap \"Paste\" (or the Batch button beside it) to analyze the link and select your preferred quality.",
    "guide-step-3": "Tap \"Download\" to save files directly to device storage with live progress tracking.",
    "guide-step-4": "Access saved media in History for offline playback or secure with Biometric Lock.",
    "guide-title": "User guide",
    "history-desc": "Your recent downloads",
    "history-unlimited": "Unlimited",
    "howtouse-steps": ["Copy a link from any supported platform.","Return to Nimidz and tap the Paste button.","Wait for the analysis to finish.","Tap Download to save."],
    "label-about": "About Nimidz",
    "label-anim-speed": "Animation Speed",
    "label-animated-bg": "Live Background",
    "label-auto-analyze": "Auto-Analyze on Paste",
    "label-auto-clear-input": "Auto-Clear Input",
    "label-auto-folder": "Subfolder per Platform",
    "label-auto-retry": "Auto-Retry Download",
    "label-auto-update": "Auto Check Updates",
    "label-autoclear-cache": "Auto-Clear Cache",
    "label-autoclear-history": "Auto-Clear History",
    "label-autodownload": "Auto-Download Link",
    "label-autofolder": "Subfolder per Platform",
    "label-autoloop": "Auto-Loop Media",
    "label-autopaste": "Auto-Paste Link",
    "label-autoplay": "Auto-Play Media",
    "label-available-downloads": "Available Downloads",
    "label-batch-photo-mode": "Batch Photo Mode",
    "label-bg-shapes": "Background Shapes",
    "label-brightness": "Brightness",
    "label-bypass-ssl": "Bypass SSL Errors",
    "label-cache-cleared": "Cache cleared successfully!",
    "label-cellular-warning": "Cellular Data Warning",
    "label-check-failed": "Check Failed",
    "label-check-failed-msg": "Unable to reach server. Check your connection.",
    "label-choose-server": "Choose Server:",
    "label-clearcache": "Clear Cache",
    "label-compact-mode": "Compact Mode",
    "label-concurrent-downloads": "Concurrent Downloads",
    "label-content": "Content",
    "label-darkmode": "Dark Mode",
    "label-data-wiped": "App reset successfully. Your gallery is safe!",
    "label-datasaver": "Data Saver",
    "label-developer": "Developer",
    "label-dl-stats": "Download Statistics",
    "label-doh": "DNS over HTTPS",
    "label-dont-show": "Don't show up again",
    "label-dont-show-again": "Don't show again",
    "label-download": "Download",
    "label-download-complete": "Download Complete",
    "label-download-sound": "Completion Sound",
    "label-error": "Error",
    "label-fatal": "Fatal",
    "label-fatal-error": "Fatal error",
    "label-file-missing": "File missing",
    "label-filename-template": "Filename",
    "label-font": "App Font",
    "label-force-ipv4": "Force IPv4 Mode",
    "label-haptic": "Vibration & Haptics",
    "label-header-spoofing": "Anti-403 Header Guard",
    "label-hide-progress": "Hide Progress Bar",
    "label-history-limit": "History Limit",
    "label-howtouse": "How to Use",
    "label-incognito": "Incognito Mode",
    "label-items-count": "${count} Items",
    "label-keep-awake": "Keep Screen Awake",
    "label-language": "Language",
    "label-lock-type": "Lock Type",
    "label-max-retry": "Max Retry Count",
    "label-modal-error": "Error opening modal",
    "label-offline": "OFFLINE",
    "label-opening-wa": "Opening WhatsApp...",
    "label-option": "Option",
    "label-overwrite-files": "Duplicate Files",
    "label-path-music": "Music Path",
    "label-path-presets": "PRESETS / SUGGESTIONS",
    "label-path-video": "Video Path",
    "label-platform": "Platform",
    "label-platforms": "Supported Platforms",
    "label-prefer-server": "Preferred Server",
    "label-privacy-lock": "Privacy Lock",
    "label-reportbug": "Report a Bug",
    "label-request-timeout": "Timeout Limit",
    "label-reset-settings": "Reset Settings",
    "label-saving": "SAVING...",
    "label-server": "Server",
    "label-server-options": "Server Options",
    "label-share-file": "Share File",
    "label-share-link": "Share Link",
    "label-share-media": "Share Media",
    "label-shareapp": "Share Nimidz App",
    "label-storagesize": "Total Media Size",
    "label-subfolder-downloads": "Subfolder in Downloads",
    "label-support": "Support Me",
    "label-test-latency": "Check Server Latency",
    "label-text-size": "Text Size",
    "label-toast-duration": "Toast Duration",
    "label-up-to-date": "You are on the latest version.",
    "label-update": "Update",
    "label-update-available": "Update Available",
    "label-user-agent": "User-Agent",
    "label-version": "Version",
    "label-wifi-only": "Download via Wi-Fi Only",
    "label-wipedata": "Wipe All Data",
    "lang-ar": "Arabic",
    "lang-en": "English",
    "lang-hi": "Hindi",
    "lang-id": "Indonesian",
    "lang-ja": "Japanese",
    "lang-ko": "Korean",
    "lang-ru": "Russian",
    "lang-tl": "Tagalog",
    "lang-zh": "Chinese (Simplified)",
    "loader-analyzing": "Analyzing link...",
    "loader-phrases": ["Analyzing link...","Fetching media...","Extracting data...","Scraping content...","Hunting for pixels...","Processing request...","Almost there..."],
    "lock-type-biometric": "Biometric",
    "lock-type-none": "None",
    "lock-type-pin": "PIN Code",
    "menu-about-desc": "Version, Help, Developer links",
    "menu-about-title": "About & Help",
    "menu-advanced-desc": "History Retention, Media Player & Reset",
    "menu-advanced-title": "Advanced",
    "menu-animation-desc": "Live background, shapes & brightness",
    "menu-animation-title": "Animation",
    "menu-appearance-desc": "Theme, Fonts, Animations & Visual UI",
    "menu-appearance-title": "Look & feel",
    "menu-general-desc": "Language, Input Automation & App Security",
    "menu-general-title": "General",
    "menu-network-desc": "Servers, Connections, DoH & Retries",
    "menu-network-title": "Network & Performance",
    "menu-storage-desc": "Save Paths, Filenames & Download Queue",
    "menu-storage-title": "Storage & Download",
    "msg-cellular-warning": "You are currently on Cellular Data. Download anyway?",
    "msg-clear-all-confirm": "Are you sure you want to delete all download history?",
    "msg-delete-item-confirm": "Remove this item from history?",
    "header-history": "Download History",
    "header-settings": "Settings",
    "tab-history": "History",
    "tab-home": "Home",
    "tab-settings": "Settings",
    "overwrite-overwrite": "Overwrite Existing",
    "overwrite-rename": "Auto-Rename (e.g. _1, _2)",
    "overwrite-skip": "Skip if Exists",
    "pdf-btn-gallery": "SAVE AS PDF (GALLERY)",
    "pdf-btn-images": "SAVE IMAGES AS PDF",
    "pdf-error-no-images": "No valid images were processed.",
    "pdf-images-detected": "Images Detected",
    "pdf-pages": "Pages",
    "pdf-toast-finalizing": "Finalizing PDF structure... Please wait.",
    "pdf-toast-processing": "Processed ${count}/${total} images...",
    "pdf-toast-saved": "PDF saved successfully to Download/Nimidz",
    "pdf-toast-saving": "Saving to device... This may take a few seconds.",
    "pdf-toast-starting": "Starting PDF Export... (Large galleries may take a moment)",
    "pin-enter-title": "Enter 4-Digit PIN",
    "placeholder-batch-link": "Paste multiple links (one per line)...",
    "placeholder-paste-link": "Paste link here...",
    "player-error-file": "Unable to load local file.",
    "player-error-stream": "Unable to stream media.",
    "retry-1": "1 (No Retry)",
    "retry-2": "2 Attempts",
    "retry-3": "3 Attempts",
    "retry-5": "5 Attempts",
    "server-1": "Server 1 (Primary)",
    "server-2": "Server 2 (Backup)",
    "server-ask": "Always Ask",
    "settings-desc": "Configure your experience",
    "shape-bubbles": "Bubbles",
    "shape-particles": "Particles",
    "shape-stars": "Stars",
    "shape-waves": "Waves",
    "share-err-error": "An error occurred during analysis.",
    "share-err-failed": "Failed to parse link.",
    "share-err-no-links": "No download links found.",
    "share-err-unsupported": "Unsupported platform link.",
    "share-msg": "Check out Nimidz, an awesome app to download media from anywhere! https://github.com/coflyn/Nimidz",
    "share-panel-sub": "Configure & download media",
    "share-panel-title": "Nimidz Quick Save",
    "text-large": "Large",
    "text-medium": "Medium",
    "text-small": "Small",
    "timeout-120": "120 Seconds",
    "timeout-15": "15 Seconds",
    "timeout-30": "30 Seconds",
    "timeout-60": "60 Seconds",
    "toast-anim-speed": "Animation speed: ",
    "toast-animatedbg-off": "Live background disabled",
    "toast-animatedbg-on": "Live background enabled",
    "toast-anti403-off": "Anti-403 guard disabled",
    "toast-anti403-on": "Anti-403 guard enabled",
    "toast-autoanalyze-off": "Auto-analyze disabled",
    "toast-autoanalyze-on": "Auto-analyze enabled",
    "toast-autoclear-cache-off": "Auto-Clear Cache disabled",
    "toast-autoclear-cache-on": "Auto-Clear Cache enabled",
    "toast-autoclear-history-off": "Auto-Clear History disabled",
    "toast-autoclear-history-on": "Auto-Clear History enabled",
    "toast-autoclearinput-off": "Auto-clear input disabled",
    "toast-autoclearinput-on": "Auto-clear input enabled",
    "toast-autodownload-off": "Auto-Download disabled",
    "toast-autodownload-on": "Auto-Download enabled",
    "toast-autofolder-off": "Subfolder per platform disabled",
    "toast-autofolder-on": "Subfolder per platform enabled",
    "toast-autoloop-off": "Auto-loop disabled",
    "toast-autoloop-on": "Auto-loop enabled",
    "toast-autopaste-off": "Auto-paste disabled",
    "toast-autopaste-on": "Auto-paste enabled",
    "toast-autoplay-off": "Auto-play disabled",
    "toast-autoplay-on": "Auto-play enabled",
    "toast-autoretry-off": "Auto-retry disabled",
    "toast-autoretry-on": "Auto-retry enabled",
    "toast-autoupdate-off": "Auto-check updates disabled",
    "toast-autoupdate-on": "Auto-check updates enabled",
    "toast-bypassssl-off": "Bypass SSL disabled",
    "toast-bypassssl-on": "Bypass SSL enabled",
    "toast-cache-error": "Error clearing cache.",
    "toast-cellularwarning-off": "Cellular data warning disabled",
    "toast-cellularwarning-on": "Cellular data warning enabled",
    "toast-clipboard-empty": "Clipboard is empty",
    "toast-compact-off": "Compact mode disabled",
    "toast-compact-on": "Compact mode enabled",
    "toast-connection-lost": "Connection lost. Please check your internet.",
    "toast-copy-failed": "Copy failed",
    "toast-copy-success": "Copied to clipboard",
    "toast-darkmode-off": "Light mode enabled",
    "toast-darkmode-on": "Dark mode enabled",
    "toast-datasaver-off": "Data Saver disabled",
    "toast-datasaver-on": "Data Saver enabled",
    "toast-doh": "DNS over HTTPS: ",
    "toast-download-cancelled": "Download cancelled",
    "toast-download-complete": "Download Complete",
    "toast-download-failed": "Download Failed",
    "toast-dur-1": "1 second",
    "toast-dur-2": "2 seconds",
    "toast-dur-3": "3 seconds",
    "toast-dur-5": "5 seconds",
    "toast-failed": "Failed:",
    "toast-forceipv4-off": "Force IPv4 disabled",
    "toast-forceipv4-on": "Force IPv4 enabled",
    "toast-haptic-off": "Haptics disabled",
    "toast-haptic-on": "Haptics enabled",
    "toast-hide-progress-off": "Download progress bar shown",
    "toast-hide-progress-on": "Download progress bar hidden",
    "toast-incognito-off": "Incognito Mode disabled",
    "toast-incognito-on": "Incognito Mode enabled",
    "toast-keepawake-off": "Screen keep awake disabled",
    "toast-keepawake-on": "Screen keep awake enabled",
    "toast-memory-error": "Memory error during conversion.",
    "toast-no-batch-urls": "No valid URLs found in text",
    "toast-no-link": "No link found in clipboard",
    "toast-overwrite": "Duplicate files: ",
    "toast-pasted-share": "Link Pasted from Share",
    "toast-path-updated": "Path updated successfully",
    "toast-pdf-downloaded": "PDF Downloaded",
    "toast-press-back-exit": "Press back again to exit",
    "toast-privacy-off": "Privacy Lock disabled",
    "toast-privacy-on": "Privacy Lock enabled",
    "toast-reset-settings": "Settings reset to default",
    "toast-saved": "Saved:",
    "toast-sound-off": "Completion sound disabled",
    "toast-sound-on": "Completion sound enabled",
    "toast-storage-error": "Storage error: Make sure you have space.",
    "toast-text-size": "Text size: ",
    "toast-wifi-needed": "Wi-Fi connection required",
    "toast-wifi-off": "Wi-Fi Only disabled",
    "toast-wifi-on": "Wi-Fi Only enabled",
    "ua-chrome": "Mobile Chrome",
    "ua-default": "Default",
    "ua-desktop": "Desktop Chrome",
    "ua-safari": "iOS Safari"
  },
  id: {
    appTitle: "NIMIYO",
    inputPlaceholder: "Tempel tautan di sini...",
    btnAnalyze: "ANALISIS TAUTAN",
    btnAnalyzing: "MENGANALISIS...",
    btnCancel: "BATAL",
    btnCancelAnalysis: "BATAL",
    btnBack: "Kembali",
    platformsTitle: "Platform Didukung",
    downloadsTitle: "Tautan Unduhan",
    btnDownload: "UNDUH",
    btnDownloading: "MENGUNDUH...",
    btnDownloadAll: "UNDUH SEMUA ({count})",
    historyTitle: "Riwayat Unduhan",
    historyEmpty: "Belum ada riwayat unduhan.",
    btnClearHistory: "HAPUS RIWAYAT",
    headerHistory: "Riwayat Unduhan",
    headerSettings: "Pengaturan",
    tabHome: "HOME",
    tabPlayer: "PLAYER",
    tabHistory: "RIWAYAT",
    tabSettings: "SETTINGS",
    headerPlayer: "PEMUTAR MUSIK",
    musicTabNimiyo: "NIMIYO",
    musicTabSongs: "LAGU",
    musicTabArtists: "ARTIS",
    musicTabAlbums: "ALBUM",
    musicPermRequiredTitle: "Lagu tidak terdeteksi",
    musicPermRequiredDesc: "Izinkan aplikasi untuk mengelola seluruh file musik di perangkat, Tentu ini aman.",
    musicSearchPlaceholder: "Cari lagu, artis, album...",
    musicNoSongs: "Tidak ada file audio lokal ditemukan",
    musicNoNimiyoSongs: "Belum ada audio di folder musik NIMIYO",
    musicNoArtists: "Tidak ada artis ditemukan",
    musicNoAlbums: "Tidak ada album ditemukan",
    musicUnknownTitle: "Tanpa Judul",
    musicUnknownArtist: "Artis Tidak Dikenal",
    musicUnknownAlbum: "Album Tidak Dikenal",
    musicNowPlaying: "SEDANG DIPUTAR",
    musicUpNext: "Berikutnya dalam Antrean:",
    musicQueueTitle: "Antrean Musik",
    musicClearQueue: "Bersihkan",
    musicEmptyQueue: "Antrean kosong",
    musicLyricsBtn: "LIRIK",
    musicShowCover: "Cover",
    musicLyricsTranslation: "Terjemahan",
    toastNoTranslationAvailable: "Terjemahan lirik belum tersedia untuk lagu ini",
    musicTapForLyrics: "Lirik Lagu",
    musicTapToFlip: "Ketuk untuk kembali ke cover",
    musicFetchingLyrics: "Mencari lirik lagu online...",
    musicNoLyrics: "Lirik tidak ditemukan untuk lagu ini",
    musicAddedToQueue: "ditambahkan ke antrean",
    musicWillPlayNext: "akan diputar berikutnya",
    musicPlayNow: "Putar Sekarang",
    musicPlayNext: "Putar Berikutnya",
    musicAddToQueue: "Tambah ke Antrean",
    musicShuffleOn: "Shuffle Aktif",
    musicShuffleOff: "Shuffle Nonaktif",
    musicRepeatOff: "Ulangi Nonaktif",
    musicRepeatAll: "Ulangi Semua",
    musicRepeatOne: "Ulangi Satu Lagu",
    musicScanError: "Gagal memindai audio lokal",
    musicPlaybackError: "Format audio tidak didukung atau file telah dipindah",
    musicBtnShuffleAll: "Acak Semua",
    musicSortTitle: "Urutkan",
    musicSortTitleAsc: "Judul (A ke Z)",
    musicSortTitleDesc: "Judul (Z ke A)",
    musicSortArtistAsc: "Artis (A ke Z)",
    musicSortAlbumAsc: "Album (A ke Z)",
    musicSortDurDesc: "Durasi (Terpanjang)",
    musicSortDurAsc: "Durasi (Terpendek)",
    musicGoToArtist: "Buka Artis",
    musicGoToAlbum: "Buka Album",
    musicSongInfo: "Rincian Lagu",
    musicShareAudio: "Bagikan Audio",
    musicDeleteTrack: "Hapus Berkas",
    confirmDeleteTrack: "Hapus berkas audio ini dari penyimpanan perangkat?",
    musicTrackDeleted: "Berkas audio berhasil dihapus",
    musicSleepTimerBtn: "TIMER",
    musicSleepTimerTitle: "Sleep Timer",
    musicTimerOff: "Nonaktif",
    musicTimerEndSong: "Akhir Lagu Ini",
    musicTimerOffToast: "Sleep timer dinonaktifkan",
    musicTimerEndSongToast: "Akan berhenti setelah lagu selesai",
    musicSleepTimerSet: "Sleep timer diatur",
    musicSpeedTitle: "Kecepatan Putar",
    mixtoolsTitle: "MixTools",
    mixtoolsResetAll: "Reset Semua",
    mixtoolsSpeedTitle: "Speed FX",
    mixtoolsSpeedDesc: "Ubah kecepatan tempo musik",
    mixtoolsGuideSlowed: "Slowed",
    mixtoolsGuideNormal: "Normal",
    mixtoolsGuideSpeedUp: "SpeedUp",
    mixtoolsReverbTitle: "Reverb FX",
    mixtoolsReverbDesc: "Efek gema akustik 3D",
    mixtoolsReverbStadium: "▲ Luas",
    mixtoolsReverbClub: "▼ Rapat",
    mixtoolsReverbDry: "◀ Dry",
    mixtoolsReverbWet: "Wet ▶",
    mixtoolsReverbHint: "Atas = Luas • Kanan = Gema",
    mixtoolsResetSuccess: "MixTools di-reset ke default",
    mixtoolsStatusDecay: "Gema",
    editLyricsTitle: "Edit Lirik",
    editLyricsFormatHelp: "Format: <code>[00:00.00] Lirik di sini</code> (menit:detik.milidetik)",
    editLyricsPlaceholder: "[00:00.00] Baris lirik pertama...\n[00:15.30] Baris lirik kedua...",
    editLyricsRefresh: "REFRESH",
    editLyricsRefreshTitle: "Reset & cari lirik baru",
    editLyricsImport: "IMPORT",
    editLyricsImportTitle: "Import file lirik .LRC lokal",
    editLyricsDownloadLrcTitle: "Download file .LRC",
    resetLyricsTitle: "Pilihan Reset Lirik",
    resetLyricsSubtitle: "Pilih sumber pencarian lirik",
    resetLyricsOptDefaultTitle: "Cari Otomatis (Default / LRCLIB)",
    resetLyricsOptDefaultDesc: "Hapus lirik lokal & ambil ulang lirik asli sinkron dari database online",
    resetLyricsOptLyricsifyTitle: "Cari di Lyricsify.com",
    resetLyricsOptLyricsifyDesc: "Buka pencarian lagu ini di Lyricsify via browser untuk salin lirik .LRC",
    resetLyricsOptImportTitle: "Import File .LRC Lokal",
    resetLyricsOptImportDesc: "Pilih file lirik .lrc atau .txt yang tersimpan di HP Anda",
    toastSelectSongFirst: "Pilih lagu terlebih dahulu",
    toastLyricsSearchingOnline: "Mencari lirik otomatis (LRCLIB)...",
    toastLyricsResetSuccess: "Lirik berhasil di-reset dari database online!",
    toastLyricsNotFoundOnline: "Lirik tidak ditemukan di database online",
    toastLyricsFetchFailed: "Gagal mengambil lirik online",
    toastLyricsLyricsifyHint: "Lirik di-reset. Salin lirik .LRC dari Lyricsify lalu tempel di sini",
    toastLyricsImportSuccess: "Lirik {file} berhasil di-import",
    toastLyricsImportFailed: "Gagal membaca file lirik",
    toastLyricsEmptyWarning: "Lirik tidak boleh kosong",
    toastLyricsSavedSuccess: "Edit Lirik berhasil, dan disimpan Lokal",
    toastLyricsNoDownload: "Belum ada lirik untuk di-download",
    toastLyricsSavedTo: "Lirik tersimpan: {file} di Nimiyo/LyricsYo",
    toastLyricsDownloaded: "Lirik {file} berhasil diunduh",
    toastLyricsDownloadFailed: "Gagal mendownload lirik",
    btnReset: "Reset",
    musicSongInfoTitle: "Rincian Lagu",
    musicLabelTitle: "Judul",
    musicLabelArtist: "Artis",
    musicLabelAlbum: "Album",
    musicLabelDuration: "Durasi",
    musicLabelFileSize: "Ukuran Berkas",
    musicLabelFormat: "Format",
    musicLabelFilePath: "Lokasi Berkas",
    musicSortModalTitle: "Urutkan Lagu Berdasarkan",
    musicShuffleAllStarted: "Memutar acak semua lagu",
    musicTapForLyrics: "Lirik",
    musicTapToFlip: "Ketuk untuk cover",
    labelUiTheme: "Desain UI",
    optThemeNeobrutalism: "Neo-Brutalism (Tegas)",
    optThemeSoftUi: "Soft UI (Lembut & Kaca)",
    settingsTitle: "Setelan",
    groupGeneral: "Umum",
    menuGeneralDesc: "Bahasa, Tempel & Analisis Otomatis",
    labelLanguage: "Bahasa",
    labelAutoPaste: "Tempel Tautan Otomatis",
    labelAutoAnalyze: "Analisis Otomatis",
    labelAutoClearInput: "Bersihkan Input Otomatis",
    labelIncognito: "Mode Penyamaran (Incognito)",
    groupAppearance: "Tampilan UIUX",
    menuAppearanceDesc: "Mode Gelap, Suara & Getaran",
    labelDarkMode: "Mode Gelap",
    labelCompletionSound: "Suara Selesai",
    labelHaptic: "Getaran & Haptik",
    labelAppFont: "Font Aplikasi",
    optFontMisans: "MiSans (Bawaan)",
    optFontInter: "Inter Modern",
    optFontOutfit: "Outfit Display",
    optFontMono: "Space Mono",
    groupStorage: "Penyimpanan & Unduhan",
    menuStorageDesc: "Format Nama, Duplikat & Cache",
    labelStoragePaths: "Lokasi Penyimpanan",
    labelFilenameTemplate: "Format Nama Berkas",
    optFilenameTitle: "Hanya Judul",
    optFilenameTitlePlatform: "Judul + Platform",
    optFilenameTitleDate: "Judul + Tanggal",
    labelOverwriteMode: "Berkas Duplikat",
    optOverwriteRename: "Ganti Nama Otomatis (_1, _2)",
    optOverwriteOverwrite: "Timpa Berkas Lama",
    optOverwriteSkip: "Lewati Jika Sudah Ada",
    labelConcurrentDl: "Unduhan Bersamaan",
    optConcurrent1: "1 (Berurutan)",
    optConcurrent2: "2 Sekaligus",
    optConcurrent3: "3 Sekaligus",
    optConcurrent5: "5 Sekaligus",
    labelBatchPhotoMode: "Mode Foto Banyak",
    optBatchPhotoAll: "Unduh Semua Foto",
    optBatchPhotoFirst: "Hanya Foto Pertama",
    labelAutoDownload: "Unduh Otomatis Saat Selesai Analisis",
    labelTotalStorage: "Ukuran Total Media",
    btnClearCache: "BERSIHKAN CACHE APLIKASI",
    btnWipeData: "HAPUS SEMUA DATA & RESET",
    groupNetwork: "Jaringan & Performa",
    menuNetworkDesc: "Unduh Ulang, Wi-Fi & DNS",
    labelAutoRetry: "Unduh Ulang Otomatis",
    labelMaxRetry: "Jumlah Percobaan Ulang",
    optRetry1: "1 (Tanpa Pengulangan)",
    optRetry2: "2 Kali Percobaan",
    optRetry3: "3 Kali Percobaan",
    optRetry5: "5 Kali Percobaan",
    labelWifiOnly: "Unduh Hanya Lewat Wi-Fi",
    labelDoh: "DNS over HTTPS",
    optDohOff: "Mati (DNS Sistem)",
    labelHeaderSpoofing: "Pelindung Header Anti-403",
    groupAdvanced: "Lanjutan",
    menuAdvancedDesc: "Retensi Riwayat, Pemutar & Reset",
    labelHistoryLimit: "Batas Retensi Riwayat",
    optUnlimited: "Tanpa Batas",
    optHistory50: "50 item",
    optHistory100: "100 item",
    optHistory200: "200 item",
    labelAutoClearDays: "Hapus Riwayat Otomatis",
    labelAutoClearCacheDays: "Hapus Cache Otomatis",
    optDaysOff: "Mati",
    optDays1: "1 Hari",
    optDays7: "7 Hari",
    optDays30: "30 Hari",
    optDays90: "90 Hari",
    labelAutoPlay: "Putar Otomatis di Pemutar",
    labelAutoLoop: "Ulang Otomatis di Pemutar",
    labelKeepAwake: "Pertahankan Layar Tetap Menyala",
    btnResetSettings: "KEMBALIKAN SEMUA SETELAN KE DEFAULT",
    groupAbout: "Tentang & Bantuan",
    menuAboutDesc: "Versi, Info & Pengembang",
    aboutVersion: "Versi 2.1.1 (Lite)",
    aboutDesc: "Mesin pengunduh media premium, modern, dan ringan yang dibangun di atas scrapr.",
    aboutThanks: "Terima kasih kepada:",
    toastClipboardEmpty: "Papan klip kosong atau tidak berisi tautan teks.",
    toastInvalidUrl: "Masukkan tautan yang valid dari platform yang didukung.",
    toastDetecting: "Mendeteksi platform...",
    toastScraping: "Menjalankan scraper ({scraper})...",
    toastScrapeSuccess: "Analisis selesai!",
    toastScrapeFail: "Scraper {scraper} gagal: {message}",
    toastScrapeAllFailed: "Gagal menganalisis tautan. Periksa link atau coba server lain di Setelan.",
    toastUnsupportedPlatform: "Platform tautan belum didukung atau format tautan tidak dikenali.",
    toastErrorOffline: "Tidak ada koneksi internet. Periksa koneksi data atau Wi-Fi Anda.",
    toastErrorTimeout: "Koneksi batas waktu (timeout). Server atau jaringan lambat.",
    toastErrorSpotifyPlaylistPrivate: "Playlist Spotify bersifat privat atau tidak ditemukan. Pastikan playlist disetel Publik di Spotify.",
    toastErrorSpotifyTrackNotFound: "Lagu atau album Spotify tidak ditemukan atau dibatasi wilayah/privat.",
    toastErrorInstagramPrivate: "Postingan/Reel Instagram bersifat privat, dibatasi, atau telah dihapus.",
    toastErrorTikTokPrivate: "Video TikTok bersifat privat atau telah dihapus oleh pengunggah.",
    toastErrorYouTubeUnavailable: "Video/Playlist YouTube bersifat privat, dibatasi usia, atau tidak tersedia.",
    toastErrorTwitterPrivate: "Postingan X (Twitter) tidak ditemukan atau akun digembok (privat).",
    toastErrorFacebookPrivate: "Postingan Facebook bersifat privat atau membutuhkan login.",
    toastErrorMediaNotFound: "Media tidak ditemukan atau konten telah dihapus/bersifat privat.",
    toastErrorServerBlocked: "Server scraper sedang padat atau terproteksi. Coba ganti server di Setelan.",
    toastAnalysisCancelled: "Analisis dibatalkan",
    toastCopiedTitle: "Judul berhasil disalin!",
    toastCopiedDesc: "Deskripsi berhasil disalin!",
    toastDownloadingSingle: "Sedang mengunduh {title}...",
    toastDownloadingMulti: "Sedang mengunduh {count} berkas...",
    toastDownloadStart: "Memulai unduhan {filename}...",
    toastDownloadSuccess: "Berkas berhasil disimpan!",
    toastDownloadFail: "Unduhan gagal setelah beberapa kali mencoba.",
    toastDownloadFailSwitchServer: "Download Gagal - Ganti Server ke {server}...",
    toastDownloadFailManualServer: "Download Gagal - Ganti Server",
    toastDownloadCancelled: "Unduhan dibatalkan oleh pengguna.",
    toastDownloadCancelledItem: "Unduhan dibatalkan: {title}",
    toastBatchCompleted: "Unduhan selesai! ({count} item tersimpan di Downloads/Nimiyo/)",
    toastHistoryCleared: "Riwayat berhasil dihapus.",
    toastSettingsSaved: "Setelan berhasil disimpan.",
    toastCacheCleared: "Cache aplikasi berhasil dibersihkan.",
    toastWipeCompleted: "Semua data aplikasi dan riwayat telah dihapus bersih.",
    toastPlaying: "Memutar : {title}",
    toastOpeningFolder: "Membuka folder Download/Nimiyo/{folder}...",
    toastLocation: "Lokasi: Download/Nimiyo/{folder}/{filename}",
    confirmResetSettings: "Apakah Anda yakin ingin mengembalikan semua setelan ke default?",
    confirmWipeData: "Apakah Anda yakin ingin menghapus seluruh riwayat, cache, dan data aplikasi?",
    statusConnecting: "Menghubungkan...",
    statusDownloading: "Mengunduh... {percent}%",
    statusSaving: "Menyimpan berkas ke penyimpanan...",
    statusReadyToDownload: "Siap diunduh",
    statusNoUrl: "URL tidak tersedia",
    noDownloadLinks: "Tidak ada tautan unduhan yang tersedia.",
    balloonSheetTitle: "Unduhan Berjalan",
    balloonCancelAll: "BATALKAN SEMUA",
    queuePending: "Menunggu...",
    queueDownloading: "Mengunduh... {percent}%",
    queueCompleted: "Selesai",
    queueCancelTitle: "Batalkan",
    toggleSeeMore: "Lihat selengkapnya",
    toggleSeeLess: "Sembunyikan",
    descLabel: "Deskripsi",
    btnCopy: "SALIN",
    historyPlayTitle: "Putar",
    historyFolderTitle: "Buka Folder",
    historyDeleteTitle: "Hapus",
    mediaPlayerTitle: "Pemutar Media",
    downloadModalTitle: "Mengunduh...",
    badgePhoto: "FOTO",
    badgeVideo: "VIDEO",
    badgeAudio: "AUDIO",
    labelAutoUpdate: "Auto-Update & Izin Pasang APK",
    hintAutoUpdate: "Izin pasang update APK otomatis tanpa ribet",
    btnPermissionAllow: "Izinkan",
    btnPermissionGranted: "Diaktifkan",
    btnCheckUpdate: "Periksa Pembaruan",
    updateModalTitle: "Pembaruan Tersedia 🚀",
    updateChangelogTitle: "Catatan Pembaruan:",
    updateDownloading: "Mengunduh pembaruan...",
    btnLater: "Nanti ingatkan kembali",
    btnRemindLater: "Nanti ingatkan kembali",
    remindScheduleTitle: "Ingatkan Pembaruan Dalam:",
    remind1Day: "1 Hari",
    remind3Days: "3 Hari",
    remind1Week: "1 Minggu",
    toastRemindScheduled: "Pengingat pembaruan diatur untuk {time}.",
    btnUpdateNow: "Update dan Install",
    btnUpdateAndInstall: "Update dan Install",
    btnManualDownload: "Download Manual Install",
    labelAccentColor: "Warna Aksen UI",
    optAccentYellow: "Yellow (dafault app)",
    optAccentBlue: "Biru",
    optAccentRed: "Merah",
    optAccentGray: "Abu-Abu",
    toastDownloadingApkManual: "Mengunduh file APK ke folder Download...",
    toastManualApkDownloaded: "File APK berhasil diunduh ke folder Download. Silakan install manual.",
    toastAppUpToDate: "Aplikasi sudah dalam versi terbaru (v{version}).",
    toastUpdateChecking: "Memeriksa pembaruan...",
    toastUpdateCheckFailed: "Gagal memeriksa pembaruan. Periksa koneksi internet.",
    toastPressBackAgain: "Tekan sekali lagi untuk keluar.",
    aboutFollowMe: "Ikuti Pengembang:",
    btnSupportMe: "Dukung Saya (Trakteer)",
    supportDesc: "Bantu pengembangan NIMIYO agar terus update",
    btnRules: "Ketentuan & Aturan",
    rulesModalTitle: "Ketentuan & Aturan Aplikasi",
    rulesIntroText: "Selamat datang di NIMIYO Downloader. Dengan menggunakan aplikasi ini, Anda setuju untuk mematuhi aturan dan ketentuan berikut:",
    rulesBadgeProhibited: "❌ DILARANG KERAS",
    rulesProhibitedTitle: "Konten yang Dilarang Diunduh:",
    rulesItemNsfw: "Pornografi & NSFW (18+): Segala bentuk konten dewasa, ketelanjangan, atau materi seksual eksplisit.",
    rulesItemViolence: "Kekerasan & Kebrutalan: Adegan kekerasan ekstrem, penyiksaan, darah/gore, atau tindakan berbahaya.",
    rulesItemHate: "Pelecehan & Kebencian: Ujaran kebencian (SARA), perundungan siber, atau tindakan intimidasi.",
    rulesItemIllegal: "Aktivitas Ilegal: Materi pelanggaran hukum, promosi senjata ilegal, atau narkotika.",
    rulesBadgeDisclaimer: "⚖️ KETENTUAN & HAK CIPTA",
    rulesDisclaimerTitle: "Hak Cipta & Disclaimer:",
    rulesItemPersonal: "NIMIYO adalah utilitas bantu untuk keperluan arsip pribadi (personal backup / offline consumption).",
    rulesItemCopyright: "Hormati hak cipta kreator. Dilarang mengomersialkan hasil unduhan tanpa izin pemilik hak cipta resmi.",
    rulesItemLegal: "Pengembang tidak menyimpan file media apa pun. Pengguna bertanggung jawab penuh secara hukum atas segala konten yang diunduh.",
    btnAgreeRules: "SAYA SETUJU & MENGERTI",

    "btnAllowAudioAccess": "Izinkan Akses",
    "playlistHeaderTitle": "PLAYLIST",
    "playlistBtnAdd": "Tambah Playlist Baru",
    "playlistEmptyTitle": "Belum Ada Playlist",
    "playlistEmptyDesc": "Kumpulkan lagu favoritmu dari NIMIYO, Songs, Artists, dan Album ke dalam daftar putar khusus.",
    "playlistBtnCreateNew": "+ Buat Playlist Baru",
    "playlistDetailPlayAll": "Putar Semua",
    "playlistDetailShuffle": "Acak",
    "playlistDetailAddSongs": "Tambah Lagu",
    "playlistDetailTracksTitle": "Daftar Lagu",
    "playlistDetailDeletePlaylist": "Hapus Playlist",
    "playlistDetailEmptyTitle": "Playlist Ini Masih Kosong",
    "playlistDetailEmptyDesc": "Tambahkan lagu sekarang dari folder NIMIYO, Songs, Artis, atau Album.",
    "playlistDetailPickSongsBtn": "+ Pilih Lagu Sekarang",
    "playlistBtnSelect": "Pilih",
    "playlistBtnCancel": "Batal",
    "playlistActionRemove": "Hapus",
    "playlistRemoveSelected": "Hapus",
    "playlistSelectAll": "Pilih Semua",
    "playlistDeselectAll": "Batal Pilih",
    "playlistTracksRemovedToast": "{count} lagu dihapus dari playlist",
    "playlistConfirmRemoveSelected": "Hapus {count} lagu dari playlist ini?",
    "playlistReorderDone": "Urutan lagu diperbarui",
    "playlistDragReorderHint": "Geser untuk atur urutan",
    "playlistSelectedCount": "{count} dipilih",
    "playlistSongsCount": "Lagu",
    "playlistCountSubtitle": "Playlist",
    "pickerModalTitle": "Pilih Lagu",
    "pickerSearchPlaceholder": "Cari lagu dalam koleksi...",
    "pickerTabAll": "SEMUA",
    "pickerSelectAll": "Pilih Semua",
    "pickerDeselectAll": "Batal Semua",
    "pickerSelected": "dipilih",
    "pickerAvailable": "lagu tersedia",
    "pickerBtnAdd": "Tambahkan",
    "pickerNoMatch": "Tidak ada lagu yang cocok dengan filter atau pencarian.",
    "createPlaylistTitle": "Playlist Baru",
    "renamePlaylistTitle": "Ubah Nama Playlist",
    "createPlaylistDesc": "Beri nama untuk playlist barumu:",
    "createPlaylistPlaceholder": "Contoh: Lagu Favorit, Santai...",
    "btnSave": "Simpan",
    "addToPlaylistTitle": "Tambah ke Playlist",
    "addToPlaylistSubtitle": "Pilih playlist tujuan",
    "quickItemAlreadyAdded": "✓ Sudah ada",
    "quickItemAdd": "+ Tambah",
    "selectionAll": "All",
    "selectionMove": "Pindah",
    "selectionDelete": "Hapus",
    "selectionCancel": "Batal",
    "selectionTitleMove": "Pindahkan lagu",
    "selectionTitleDelete": "Hapus permanen lagu",
    "moveTargetModalTitle": "Pindahkan Lagu Ke",
    "moveTargetModalDesc": "Pilih tujuan pemindahan untuk lagu yang dipilih:",
    "moveOptionPlaylistTitle": "Playlist",
    "moveOptionPlaylistDesc": "Kumpulkan ke daftar putar Playlist",
    "moveOptionNimiyoTitle": "Nimiyo",
    "moveOptionNimiyoDesc": "Pindahkan berkas audio ke folder musik NIMIYO",
    "deleteConfirmModalTitle": "Hapus Permanen",
    "deleteConfirmPrompt": "Apakah anda ingin menghapus permanent {count} lagu atau Audio yang di pilih? ini akan menghilangkan Berkas Audio tersebut.",
    "btnYes": "Ya",
    "musicPermGranted": "Akses musik diizinkan, memuat lagu...",
    "musicQueueCleared": "Antrean dibersihkan",
    "musicDeleteProtectedError": "Gagal menghapus: berkas dilindungi sistem Android",
    "playlistNameRequired": "Nama playlist tidak boleh kosong",
    "playlistNameUpdated": "Nama playlist diperbarui",
    "playlistCreatedToast": "Playlist \"{name}\" dibuat",
    "playlistDeletedToast": "Playlist berhasil dihapus",
    "playlistSongRemoved": "Lagu dihapus dari playlist",
    "playlistEmptyToast": "Playlist ini masih kosong",
    "playlistSongNotFound": "Lagu dalam playlist tidak ditemukan",
    "playlistPlayingToast": "Memutar: {name}",
    "playlistUpdatedToast": "Playlist \"{name}\" diperbarui ({count} lagu)",
    "playlistSongAlreadyIn": "Lagu sudah ada di \"{name}\"",
    "playlistSongAddedTo": "Ditambahkan ke \"{name}\"",
    "selectionMinOneSong": "Pilih minimal 1 lagu terlebih dahulu",
    "selectionAddedToPlaylistToast": "{count} lagu ditambahkan ke \"{name}\"",
    "selectionMovedToNimiyoToast": "{count} lagu berhasil dipindahkan ke folder Nimiyo",
    "selectionAlreadyInNimiyoToast": "Semua lagu yang dipilih sudah ada di folder Nimiyo",
    "selectionPermanentDeletedToast": "{count} berkas audio berhasil dihapus permanent",
    "selectionNoFilesDeletedToast": "Tidak ada berkas yang terhapus",
    "manageStoragePermRequired": "Izin Akses Berkas Diperlukan:\n\nPada Android 11 ke atas, Nimiyo memerlukan izin 'Akses semua berkas' agar dapat menghapus atau mengelola berkas audio secara permanen dari memori perangkat.\n\nBuka Setelan sekarang untuk mengaktifkan izin ini?",
    "confirmDeletePlaylist": "Yakin ingin menghapus playlist ini?",
    "musicAddToPlaylist": "Tambah ke Playlist",
    "musicMoveToNimiyo": "Pindahkan ke Nimiyo",
    "musicSelectSong": "Pilih Lagu",
    "musicDurationHour": "jam",
    "musicDurationMin": "min",
    "about-text": "Nimidz adalah pengunduh media yang cepat dan serbaguna. Dibuat dengan cinta oleh coflyn.",
    "anim-fast": "Cepat",
    "anim-normal": "Normal",
    "anim-off": "Mati",
    "anim-slow": "Lambat",
    "backup-monthly": "Bulanan",
    "backup-off": "Nonaktif",
    "backup-weekly": "Mingguan",
    "batch-download-all": "Unduh Semua",
    "batch-modal-title": "Antrean Batch Download",
    "batch-photo-all": "Unduh Semua Foto",
    "batch-photo-first": "Hanya Foto Pertama",
    "batch-photo-pdf": "Gabungkan ke Dokumen PDF",
    "btn-analyze": "Analisis",
    "btn-analyze-batch": "Analisis Batch",
    "btn-cancel": "Batal",
    "btn-cancel-download": "BATALKAN UNDUHAN",
    "btn-check": "CEK",
    "btn-clear": "BERSIHKAN",
    "btn-clear-all": "HAPUS SEMUA",
    "btn-close": "TUTUP",
    "btn-delete": "HAPUS",
    "btn-done": "SELESAI",
    "btn-download-all-title": "Unduh Semua",
    "btn-edit": "UBAH",
    "btn-open-settings": "Buka pengaturan",
    "btn-processing": "Memproses...",
    "btn-report": "LAPOR",
    "btn-reset": "RESET",
    "btn-reset-default": "RESET KE DEFAULT",
    "btn-reset-settings": "RESET",
    "btn-share": "BAGI",
    "btn-stop": "Hentikan",
    "btn-update": "UPDATE",
    "btn-view": "LIHAT",
    "concurrent-1": "1 (Urutan)",
    "concurrent-2": "2 sekaligus",
    "concurrent-3": "3 sekaligus",
    "concurrent-5": "5 sekaligus",
    "confirm-reset-settings": "Reset semua pengaturan ke default? History dan file unduhan tidak akan terhapus.",
    "days-1": "1 Hari",
    "days-30": "30 Hari",
    "days-7": "7 Hari",
    "days-90": "90 Hari",
    "days-off": "Nonaktif",
    "desc-clearcache": "Hapus thumbnail sementara dan pratinjau gambar untuk menghemat ruang. File unduhan Anda tetap aman. Lanjutkan?",
    "desc-wipedata": "Reset riwayat, pengaturan, dan cache. Ini TIDAK akan menghapus video atau musik yang telah Anda unduh di Galeri. Lanjutkan?",
    "dl-stats-total": "Total Unduhan",
    "doh-cloudflare": "Cloudflare (1.1.1.1)",
    "doh-google": "Google (8.8.8.8)",
    "doh-off": "Mati (DNS Sistem)",
    "download-all-complete": "Seluruh ${count} item berhasil diunduh!",
    "downloading-progress": "Mengunduh...",
    "err-yt-playlist-source": "Playlist YouTube memerlukan server 'ytmp3.gg'.",
    "filename-default": "Default",
    "filename-title": "Hanya Judul",
    "filename-title-date": "Judul + Tanggal",
    "filename-title-platform": "Judul + Platform",
    "font-default": "Inter",
    "font-display": "Display Bold",
    "font-jakarta": "Plus Jakarta Sans",
    "font-mono": "Mono Modern",
    "font-serif": "Serif Klasik",
    "guide-step-1": "Salin link media dari TikTok, Instagram, YouTube, Twitter/X, Douyin & 14+ platform.",
    "guide-step-2": "Ketuk \"Tempel\" (atau tombol Batch di sebelahnya) untuk menganalisis link dan pilih kualitas.",
    "guide-step-3": "Ketuk \"Unduh\" untuk menyimpan file langsung ke menimidz HP dengan pantauan progress.",
    "guide-step-4": "Buka media tersimpan di Riwayat untuk pemutaran offline atau amankan dengan Kunci Biometrik.",
    "guide-title": "Panduan pengguna",
    "history-desc": "Unduhan terakhir Anda",
    "history-unlimited": "Tanpa Batas",
    "howtouse-steps": ["Salin tautan dari platform yang didukung.","Kembali ke Nimidz dan tekan tombol Tempel.","Tunggu hingga analisis selesai.","Tekan Unduh untuk menyimpan."],
    "label-about": "Tentang Nimidz",
    "label-anim-speed": "Kecepatan Animasi",
    "label-animated-bg": "Latar Bergerak",
    "label-auto-analyze": "Analisis Otomatis",
    "label-auto-clear-input": "Bersihkan Input Otomatis",
    "label-auto-folder": "Subfolder per Platform",
    "label-auto-retry": "Auto-Retry Download",
    "label-auto-update": "Cek Update Otomatis",
    "label-autoclear-cache": "Hapus Cache Otomatis",
    "label-autoclear-history": "Hapus Riwayat Otomatis",
    "label-autodownload": "Unduh Tautan Otomatis",
    "label-autofolder": "Subfolder per Platform",
    "label-autoloop": "Loop Otomatis",
    "label-autopaste": "Tempel Otomatis",
    "label-autoplay": "Putar Otomatis",
    "label-available-downloads": "Unduhan Tersedia",
    "label-batch-photo-mode": "Mode Foto Batch",
    "label-bg-shapes": "Bentuk Latar Belakang",
    "label-brightness": "Kecerahan",
    "label-bypass-ssl": "Bypass Sertifikat SSL",
    "label-cache-cleared": "Cache berhasil dibersihkan!",
    "label-cellular-warning": "Peringatan Kuota Seluler",
    "label-check-failed": "Pemeriksaan Gagal",
    "label-check-failed-msg": "Tidak dapat menjangkau server. Periksa koneksi Anda.",
    "label-choose-server": "Pilih Server:",
    "label-clearcache": "Hapus Cache",
    "label-compact-mode": "Mode Ringkas",
    "label-concurrent-downloads": "Download Bersamaan",
    "label-content": "Konten",
    "label-darkmode": "Mode Gelap",
    "label-data-wiped": "Aplikasi berhasil direset. Galeri Anda tetap aman!",
    "label-datasaver": "Hemat Data",
    "label-developer": "Pengembang",
    "label-dl-stats": "Statistik Unduhan",
    "label-doh": "DNS over HTTPS",
    "label-dont-show": "Jangan tampilkan lagi",
    "label-dont-show-again": "Jangan tampilkan lagi",
    "label-download": "Unduh",
    "label-download-complete": "Download Selesai",
    "label-download-sound": "Suara Selesai Download",
    "label-error": "Kesalahan",
    "label-fatal": "Fatal",
    "label-fatal-error": "Kesalahan fatal",
    "label-file-missing": "File tidak ditemukan",
    "label-filename-template": "Format Nama File",
    "label-font": "Font Aplikasi",
    "label-force-ipv4": "Paksa Mode IPv4",
    "label-haptic": "Getar & Haptik",
    "label-header-spoofing": "Anti-403 Header Guard",
    "label-hide-progress": "Sembunyikan Progress Bar",
    "label-history-limit": "Batas Riwayat",
    "label-howtouse": "Cara Penggunaan",
    "label-incognito": "Mode Incognito",
    "label-items-count": "${count} Item",
    "label-keep-awake": "Layar Tetap Menyala",
    "label-language": "Bahasa",
    "label-lock-type": "Tipe Kunci",
    "label-max-retry": "Maks Percobaan Ulang",
    "label-modal-error": "Gagal membuka modal",
    "label-offline": "OFFLINE",
    "label-opening-wa": "Membuka WhatsApp...",
    "label-option": "Opsi",
    "label-overwrite-files": "File Duplikat",
    "label-path-music": "Lokasi Musik",
    "label-path-presets": "PRESET / SARAN LOKASI",
    "label-path-video": "Lokasi Video",
    "label-platform": "Platform",
    "label-platforms": "Platform Didukung",
    "label-prefer-server": "Server Pilihan",
    "label-privacy-lock": "Kunci Privasi",
    "label-reportbug": "Laporkan Bug",
    "label-request-timeout": "Batas Waktu Timeout",
    "label-reset-settings": "Reset Pengaturan",
    "label-saving": "MENYIMPAN...",
    "label-server": "Server",
    "label-server-options": "Opsi Server",
    "label-share-file": "Bagikan Berkas",
    "label-share-link": "Bagikan Tautan",
    "label-share-media": "Bagikan Media",
    "label-shareapp": "Bagikan Aplikasi Nimidz",
    "label-storagesize": "Total Ukuran Media",
    "label-subfolder-downloads": "Subfolder di Downloads",
    "label-support": "Dukung Saya",
    "label-test-latency": "Cek Latensi Server",
    "label-text-size": "Ukuran Teks",
    "label-toast-duration": "Durasi Toast",
    "label-up-to-date": "Anda menggunakan versi terbaru.",
    "label-update": "Pembaruan",
    "label-update-available": "Pembaruan Tersedia",
    "label-user-agent": "User-Agent",
    "label-version": "Versi",
    "label-wifi-only": "Unduh via Wi-Fi Saja",
    "label-wipedata": "Hapus Semua Data",
    "lang-ar": "Bahasa Arab",
    "lang-en": "Bahasa Inggris",
    "lang-hi": "Bahasa Hindi",
    "lang-id": "Bahasa Indonesia",
    "lang-ja": "Bahasa Jepang",
    "lang-ko": "Bahasa Korea",
    "lang-ru": "Bahasa Rusia",
    "lang-tl": "Bahasa Tagalog",
    "lang-zh": "Bahasa Mandarin",
    "loader-analyzing": "Menganalisis tautan...",
    "loader-phrases": ["Menganalisis tautan...","Mengambil media...","Mengekstrak data...","Scraping konten...","Mencari piksel...","Memproses permintaan...","Hampir selesai..."],
    "lock-type-biometric": "Biometrik",
    "lock-type-none": "Tidak Ada",
    "lock-type-pin": "Kode PIN",
    "menu-about-desc": "Versi, Bantuan, Profil pengembang",
    "menu-about-title": "Tentang & Bantuan",
    "menu-advanced-desc": "Retensi Riwayat, Pemutar Media & Reset",
    "menu-advanced-title": "Lanjutan",
    "menu-animation-desc": "Latar bergerak, bentuk & kecerahan",
    "menu-animation-title": "Animasi",
    "menu-appearance-desc": "Tema, Font, Animasi & Visual UI",
    "menu-appearance-title": "Tampilan & rasa",
    "menu-general-desc": "Bahasa, Otomatisasi Input & Keamanan Aplikasi",
    "menu-general-title": "Umum",
    "menu-network-desc": "Server, Koneksi, DoH & Percobaan Ulang",
    "menu-network-title": "Jaringan & Performa",
    "menu-storage-desc": "Lokasi Simpan, Format File & Antrean Download",
    "menu-storage-title": "Penyimpanan & Unduhan",
    "msg-cellular-warning": "Anda sedang menggunakan Data Seluler. Tetap unduh?",
    "msg-clear-all-confirm": "Apakah Anda yakin ingin menghapus semua riwayat unduhan?",
    "msg-delete-item-confirm": "Hapus item ini dari riwayat?",
    "header-history": "Riwayat Unduhan",
    "header-settings": "Pengaturan",
    "tab-history": "Riwayat",
    "tab-home": "Beranda",
    "tab-settings": "Pengaturan",
    "overwrite-overwrite": "Timpa File Lama",
    "overwrite-rename": "Ganti Nama Otomatis",
    "overwrite-skip": "Lewati jika Ada",
    "pdf-btn-gallery": "SIMPAN KE PDF (GALERI)",
    "pdf-btn-images": "SIMPAN GAMBAR KE PDF",
    "pdf-error-no-images": "Tidak ada gambar valid yang diproses.",
    "pdf-images-detected": "Gambar Terdeteksi",
    "pdf-pages": "Halaman",
    "pdf-toast-finalizing": "Menyusun struktur PDF... Mohon tunggu.",
    "pdf-toast-processing": "Memproses ${count}/${total} gambar...",
    "pdf-toast-saved": "PDF berhasil disimpan di Download/Nimidz",
    "pdf-toast-saving": "Menyimpan ke perangkat... Mohon tunggu sebentar.",
    "pdf-toast-starting": "Memulai Ekspor PDF... (Galeri besar mungkin memakan waktu)",
    "pin-enter-title": "Masukkan 4-Digit PIN",
    "placeholder-batch-link": "Tempel beberapa tautan (satu per baris)...",
    "placeholder-paste-link": "Tempel tautan di sini...",
    "player-error-file": "Tidak dapat memuat berkas lokal.",
    "player-error-stream": "Tidak dapat memutar streaming media.",
    "retry-1": "1 (Tanpa Ulang)",
    "retry-2": "2 Percobaan",
    "retry-3": "3 Percobaan",
    "retry-5": "5 Percobaan",
    "server-1": "Server 1 (Utama)",
    "server-2": "Server 2 (Cadangan)",
    "server-ask": "Selalu Tanya",
    "settings-desc": "Konfigurasi pengalaman Anda",
    "shape-bubbles": "Gelembung",
    "shape-particles": "Partikel",
    "shape-stars": "Bintang",
    "shape-waves": "Gelombang",
    "share-err-error": "Terjadi kesalahan saat analisis.",
    "share-err-failed": "Gagal memproses tautan.",
    "share-err-no-links": "Tidak ada tautan unduhan ditemukan.",
    "share-err-unsupported": "Tautan platform tidak didukung.",
    "share-msg": "Cobain Nimidz, aplikasi keren untuk mengunduh media dari mana saja! https://github.com/coflyn/Nimidz",
    "share-panel-sub": "Konfigurasi & unduh media",
    "share-panel-title": "Nimidz Simpan Cepat",
    "text-large": "Besar",
    "text-medium": "Sedang",
    "text-small": "Kecil",
    "timeout-120": "120 Detik",
    "timeout-15": "15 Detik",
    "timeout-30": "30 Detik",
    "timeout-60": "60 Detik",
    "toast-anim-speed": "Kecepatan animasi: ",
    "toast-animatedbg-off": "Latar bergerak dinonaktifkan",
    "toast-animatedbg-on": "Latar bergerak diaktifkan",
    "toast-anti403-off": "Pelindung Anti-403 dinonaktifkan",
    "toast-anti403-on": "Pelindung Anti-403 diaktifkan",
    "toast-autoanalyze-off": "Analisis otomatis dinonaktifkan",
    "toast-autoanalyze-on": "Analisis otomatis diaktifkan",
    "toast-autoclear-cache-off": "Hapus Cache Otomatis dinonaktifkan",
    "toast-autoclear-cache-on": "Hapus Cache Otomatis diaktifkan",
    "toast-autoclear-history-off": "Hapus Riwayat Otomatis dinonaktifkan",
    "toast-autoclear-history-on": "Hapus Riwayat Otomatis diaktifkan",
    "toast-autoclearinput-off": "Hapus input otomatis dinonaktifkan",
    "toast-autoclearinput-on": "Hapus input otomatis diaktifkan",
    "toast-autodownload-off": "Unduh Otomatis dinonaktifkan",
    "toast-autodownload-on": "Unduh Otomatis diaktifkan",
    "toast-autofolder-off": "Subfolder per platform dinonaktifkan",
    "toast-autofolder-on": "Subfolder per platform diaktifkan",
    "toast-autoloop-off": "Loop otomatis dinonaktifkan",
    "toast-autoloop-on": "Loop otomatis diaktifkan",
    "toast-autopaste-off": "Tempel Otomatis dinonaktifkan",
    "toast-autopaste-on": "Tempel Otomatis diaktifkan",
    "toast-autoplay-off": "Putar otomatis dinonaktifkan",
    "toast-autoplay-on": "Putar otomatis diaktifkan",
    "toast-autoretry-off": "Coba lagi otomatis dinonaktifkan",
    "toast-autoretry-on": "Coba lagi otomatis diaktifkan",
    "toast-autoupdate-off": "Cek pembaruan otomatis dinonaktifkan",
    "toast-autoupdate-on": "Cek pembaruan otomatis diaktifkan",
    "toast-bypassssl-off": "Bypass SSL dinonaktifkan",
    "toast-bypassssl-on": "Bypass SSL diaktifkan",
    "toast-cache-error": "Gagal menghapus cache.",
    "toast-cellularwarning-off": "Peringatan data seluler dinonaktifkan",
    "toast-cellularwarning-on": "Peringatan data seluler diaktifkan",
    "toast-clipboard-empty": "Clipboard kosong",
    "toast-compact-off": "Mode ringkas nonaktif",
    "toast-compact-on": "Mode ringkas aktif",
    "toast-connection-lost": "Koneksi terputus. Periksa internet Anda.",
    "toast-copy-failed": "Gagal menyalin",
    "toast-copy-success": "Berhasil disalin ke clipboard",
    "toast-darkmode-off": "Mode terang diaktifkan",
    "toast-darkmode-on": "Mode gelap diaktifkan",
    "toast-datasaver-off": "Penghemat Data dinonaktifkan",
    "toast-datasaver-on": "Penghemat Data diaktifkan",
    "toast-doh": "DNS over HTTPS: ",
    "toast-download-cancelled": "Unduhan dibatalkan",
    "toast-download-complete": "Download Selesai",
    "toast-download-failed": "Unduhan Gagal",
    "toast-dur-1": "1 detik",
    "toast-dur-2": "2 detik",
    "toast-dur-3": "3 detik",
    "toast-dur-5": "5 detik",
    "toast-failed": "Gagal:",
    "toast-forceipv4-off": "Paksa IPv4 dinonaktifkan",
    "toast-forceipv4-on": "Paksa IPv4 diaktifkan",
    "toast-haptic-off": "Haptik dinonaktifkan",
    "toast-haptic-on": "Haptik diaktifkan",
    "toast-hide-progress-off": "Progress bar unduhan ditampilkan",
    "toast-hide-progress-on": "Progress bar unduhan disembunyikan",
    "toast-incognito-off": "Mode Samaran dinonaktifkan",
    "toast-incognito-on": "Mode Samaran diaktifkan",
    "toast-keepawake-off": "Layar tetap menyala dinonaktifkan",
    "toast-keepawake-on": "Layar tetap menyala diaktifkan",
    "toast-memory-error": "Kesalahan menimidz saat konversi.",
    "toast-no-batch-urls": "Tidak ada URL valid yang ditemukan",
    "toast-no-link": "Tidak ada tautan di clipboard",
    "toast-overwrite": "File duplikat: ",
    "toast-pasted-share": "Tautan ditempel dari Share",
    "toast-path-updated": "Lokasi penyimpanan berhasil diperbarui",
    "toast-pdf-downloaded": "PDF Berhasil Diunduh",
    "toast-press-back-exit": "Tekan sekali lagi untuk keluar",
    "toast-privacy-off": "Kunci Privasi dinonaktifkan",
    "toast-privacy-on": "Kunci Privasi diaktifkan",
    "toast-reset-settings": "Pengaturan direset ke default",
    "toast-saved": "Tersimpan:",
    "toast-sound-off": "Suara selesai dinonaktifkan",
    "toast-sound-on": "Suara selesai diaktifkan",
    "toast-storage-error": "Kesalahan penyimpanan: Pastikan menimidz cukup.",
    "toast-text-size": "Ukuran teks: ",
    "toast-wifi-needed": "Diperlukan koneksi Wi-Fi",
    "toast-wifi-off": "Hanya Wi-Fi dinonaktifkan",
    "toast-wifi-on": "Hanya Wi-Fi diaktifkan",
    "ua-chrome": "Mobile Chrome",
    "ua-default": "Default",
    "ua-desktop": "Desktop Chrome",
    "ua-safari": "iOS Safari"
  },
  zh: {
    appTitle: "NIMIYO",
    inputPlaceholder: "在此粘贴链接...",
    btnAnalyze: "解析链接",
    btnAnalyzing: "正在解析...",
    btnCancel: "取消",
    btnCancelAnalysis: "取消",
    btnBack: "返回",
    platformsTitle: "支持的平台",
    downloadsTitle: "下载链接",
    btnDownload: "下载",
    btnDownloading: "正在下载...",
    btnDownloadAll: "全部下载 ({count})",
    historyTitle: "下载历史",
    historyEmpty: "暂无下载历史记录。",
    btnClearHistory: "清空历史",
    headerHistory: "下载历史",
    headerSettings: "设置",
    tabHome: "主页",
    tabPlayer: "播放器",
    tabHistory: "历史",
    tabSettings: "设置",
    headerPlayer: "本地音乐",
    musicTabNimiyo: "NIMIYO",
    musicTabSongs: "单曲",
    musicTabArtists: "艺术家",
    musicTabAlbums: "专辑",
    musicSearchPlaceholder: "搜索歌曲、歌手、专辑...",
    musicNoSongs: "未找到本地音频文件",
    musicNoNimiyoSongs: "NIMIYO 下载目录中暂无音频",
    musicNoArtists: "未找到艺术家",
    musicNoAlbums: "未找到专辑",
    musicUnknownTitle: "未知标题",
    musicUnknownArtist: "未知艺术家",
    musicUnknownAlbum: "未知专辑",
    musicNowPlaying: "正在播放",
    musicUpNext: "接下来播放:",
    musicQueueTitle: "播放队列",
    musicClearQueue: "清空",
    musicEmptyQueue: "播放队列为空",
    musicLyricsBtn: "歌词",
    musicShowCover: "封面",
    musicLyricsTranslation: "翻译",
    toastNoTranslationAvailable: "此歌曲暂无歌词翻译",
    musicTapForLyrics: "歌词",
    musicTapToFlip: "点击返回封面",
    musicFetchingLyrics: "正在获取在线歌词...",
    musicNoLyrics: "未找到此歌曲的歌词",
    musicAddedToQueue: "已添加到队列",
    musicWillPlayNext: "将在下一首播放",
    musicPlayNow: "立即播放",
    musicPlayNext: "下一首播放",
    musicAddToQueue: "添加到队列",
    musicShuffleOn: "随机播放开启",
    musicShuffleOff: "随机播放关闭",
    musicRepeatOff: "循环播放关闭",
    musicRepeatAll: "列表循环",
    musicRepeatOne: "单曲循环",
    musicScanError: "扫描本地音频失败",
    musicPlaybackError: "音频格式不受支持或文件已移动",
    musicBtnShuffleAll: "随机全部",
    musicSortTitle: "排序",
    musicSortTitleAsc: "标题 (A 到 Z)",
    musicSortTitleDesc: "标题 (Z 到 A)",
    musicSortArtistAsc: "艺术家 (A 到 Z)",
    musicSortAlbumAsc: "专辑 (A 到 Z)",
    musicSortDurDesc: "时长 (从长到短)",
    musicSortDurAsc: "时长 (从短到长)",
    musicGoToArtist: "查看艺术家",
    musicGoToAlbum: "查看专辑",
    musicSongInfo: "歌曲详情",
    musicShareAudio: "分享音频",
    musicDeleteTrack: "删除文件",
    confirmDeleteTrack: "确定要从设备存储中删除此音频文件吗？",
    musicTrackDeleted: "音频文件已成功删除",
    musicSleepTimerBtn: "定时",
    musicSleepTimerTitle: "睡眠定时器",
    musicTimerOff: "关闭",
    musicTimerEndSong: "当前歌曲播放完毕",
    musicTimerOffToast: "睡眠定时器已关闭",
    musicTimerEndSongToast: "将在当前歌曲结束后停止播放",
    musicSleepTimerSet: "睡眠定时器已设置",
    musicSpeedTitle: "播放速度",
    mixtoolsTitle: "混音工具 (MixTools)",
    mixtoolsResetAll: "全部重置",
    mixtoolsSpeedTitle: "Speed FX",
    mixtoolsSpeedDesc: "调节播放速度与节奏",
    mixtoolsGuideSlowed: "Slowed",
    mixtoolsGuideNormal: "Normal",
    mixtoolsGuideSpeedUp: "SpeedUp",
    mixtoolsReverbTitle: "Reverb FX",
    mixtoolsReverbDesc: "3D空间立体回声",
    mixtoolsReverbStadium: "▲ 开阔",
    mixtoolsReverbClub: "▼ 紧凑",
    mixtoolsReverbDry: "◀ Dry",
    mixtoolsReverbWet: "Wet ▶",
    mixtoolsReverbHint: "向上 = 开阔 • 向右 = 混响",
    mixtoolsResetSuccess: "MixTools 已重置为默认值",
    mixtoolsStatusDecay: "衰减",
    editLyricsTitle: "编辑歌词",
    editLyricsFormatHelp: "格式: <code>[00:00.00] 歌词内容</code> (分:秒.毫秒)",
    editLyricsPlaceholder: "[00:00.00] 第一句歌词...\n[00:15.30] 第二句歌词...",
    editLyricsRefresh: "刷新",
    editLyricsRefreshTitle: "重置并搜索新歌词",
    editLyricsImport: "导入",
    editLyricsImportTitle: "导入本地 .LRC 歌词文件",
    editLyricsDownloadLrcTitle: "下载 .LRC 歌词文件",
    resetLyricsTitle: "重置歌词选项",
    resetLyricsSubtitle: "选择歌词搜索来源",
    resetLyricsOptDefaultTitle: "自动搜索 (默认 / LRCLIB)",
    resetLyricsOptDefaultDesc: "清除本地歌词并从在线数据库重新获取同步歌词",
    resetLyricsOptLyricsifyTitle: "在 Lyricsify.com 搜索",
    resetLyricsOptLyricsifyDesc: "在浏览器中打开 Lyricsify 页面以复制 .LRC 歌词",
    resetLyricsOptImportTitle: "导入本地 .LRC 文件",
    resetLyricsOptImportDesc: "选择存储在设备上的 .lrc 或 .txt 歌词文件",
    toastSelectSongFirst: "请先选择一首歌曲",
    toastLyricsSearchingOnline: "正在自动搜索歌词 (LRCLIB)...",
    toastLyricsResetSuccess: "歌词已成功从在线数据库重置！",
    toastLyricsNotFoundOnline: "在线数据库中未找到歌词",
    toastLyricsFetchFailed: "获取在线歌词失败",
    toastLyricsLyricsifyHint: "歌词已重置。从 Lyricsify 复制 .LRC 歌词并粘贴在此处",
    toastLyricsImportSuccess: "歌词 {file} 导入成功",
    toastLyricsImportFailed: "读取歌词文件失败",
    toastLyricsEmptyWarning: "歌词内容不能为空",
    toastLyricsSavedSuccess: "歌词编辑成功并已保存在本地",
    toastLyricsNoDownload: "没有可供下载的歌词",
    toastLyricsSavedTo: "歌词已保存: {file} 至 Nimiyo/LyricsYo",
    toastLyricsDownloaded: "歌词 {file} 下载成功",
    toastLyricsDownloadFailed: "下载歌词失败",
    btnReset: "重置",
    musicSongInfoTitle: "歌曲详情",
    musicLabelTitle: "标题",
    musicLabelArtist: "艺术家",
    musicLabelAlbum: "专辑",
    musicLabelDuration: "时长",
    musicLabelFileSize: "文件大小",
    musicLabelFormat: "格式",
    musicLabelFilePath: "文件路径",
    musicSortModalTitle: "排序方式",
    musicShuffleAllStarted: "正在随机播放所有歌曲",
    musicTapForLyrics: "歌词",
    musicTapToFlip: "点击查看封面",
    labelUiTheme: "界面设计",
    optThemeNeobrutalism: "新野兽派 (经典硬朗)",
    optThemeSoftUi: "拟态拟物 (柔和玻璃质感)",
    settingsTitle: "设置",
    groupGeneral: "常规设置",
    menuGeneralDesc: "语言、自动粘贴与自动解析",
    labelLanguage: "语言",
    labelAutoPaste: "自动粘贴链接",
    labelAutoAnalyze: "粘贴时自动解析",
    labelAutoClearInput: "自动清空输入框",
    labelIncognito: "无痕模式 (不记录历史)",
    groupAppearance: "外观与界面",
    menuAppearanceDesc: "深色模式、提示音与振动",
    labelDarkMode: "深色模式",
    labelCompletionSound: "完成提示音",
    labelHaptic: "触觉与振动反馈",
    labelAppFont: "应用字体",
    optFontMisans: "MiSans (默认)",
    optFontInter: "Inter 现代",
    optFontOutfit: "Outfit 艺术",
    optFontMono: "等宽字体",
    groupStorage: "存储与下载",
    menuStorageDesc: "文件名、重复处理与缓存",
    labelStoragePaths: "保存路径",
    labelFilenameTemplate: "文件名格式",
    optFilenameTitle: "仅标题",
    optFilenameTitlePlatform: "标题 + 平台",
    optFilenameTitleDate: "标题 + 日期",
    labelOverwriteMode: "重复文件处理",
    optOverwriteRename: "自动重命名 (_1, _2)",
    optOverwriteOverwrite: "覆盖已有文件",
    optOverwriteSkip: "跳过已存在文件",
    labelConcurrentDl: "同时下载数",
    optConcurrent1: "1 (单任务)",
    optConcurrent2: "同时下载 2 个",
    optConcurrent3: "同时下载 3 个",
    optConcurrent5: "同时下载 5 个",
    labelBatchPhotoMode: "图集下载模式",
    optBatchPhotoAll: "下载全部图片",
    optBatchPhotoFirst: "仅首张图片",
    labelAutoDownload: "解析成功后自动下载",
    labelTotalStorage: "已下载媒体大小",
    btnClearCache: "清空应用缓存",
    btnWipeData: "清除全部数据并重置",
    groupNetwork: "网络与性能",
    menuNetworkDesc: "自动重试、Wi-Fi 与 DNS",
    labelAutoRetry: "自动重试下载",
    labelMaxRetry: "最大重试次数",
    optRetry1: "1 (不重试)",
    optRetry2: "2 次尝试",
    optRetry3: "3 次尝试",
    optRetry5: "5 次尝试",
    labelWifiOnly: "仅通过 Wi-Fi 下载",
    labelDoh: "安全 DNS (DoH)",
    optDohOff: "关闭 (系统 DNS)",
    labelHeaderSpoofing: "防 403 请求头保护",
    groupAdvanced: "高级设置",
    menuAdvancedDesc: "历史保留、播放器与重置",
    labelHistoryLimit: "历史记录保留上限",
    optUnlimited: "无限制",
    optHistory50: "50 项",
    optHistory100: "100 项",
    optHistory200: "200 项",
    labelAutoClearDays: "自动清理历史记录",
    labelAutoClearCacheDays: "自动清理缓存",
    optDaysOff: "关闭",
    optDays1: "1 天",
    optDays7: "7 天",
    optDays30: "30 天",
    optDays90: "90 天",
    labelAutoPlay: "打开播放器时自动播放",
    labelAutoLoop: "播放器自动循环播放",
    labelKeepAwake: "下载与播放时保持屏幕常亮",
    btnResetSettings: "恢复所有设置到默认值",
    groupAbout: "关于与帮助",
    menuAboutDesc: "版本信息、开源与开发团队",
    aboutVersion: "版本 2.1.1 (Lite)",
    aboutDesc: "基于 scrapr 构建的高级、现代且轻量级的媒体下载引擎。",
    aboutThanks: "致谢:",
    toastClipboardEmpty: "剪贴板为空或不包含文本链接。",
    toastInvalidUrl: "请输入受支持平台的有效链接。",
    toastDetecting: "正在检测平台...",
    toastScraping: "正在运行解析器 ({scraper})...",
    toastScrapeSuccess: "解析完成！",
    toastScrapeFail: "解析器 {scraper} 失败：{message}",
    toastScrapeAllFailed: "解析链接失败。请检查链接或在设置中更换服务器。",
    toastUnsupportedPlatform: "不支持该平台链接或链接格式无法识别。",
    toastErrorOffline: "无网络连接。请检查您的移动数据或 Wi-Fi 连接。",
    toastErrorTimeout: "连接超时。服务器或网络响应缓慢。",
    toastErrorSpotifyPlaylistPrivate: "Spotify 歌单为私密或未找到。请确保该歌单在 Spotify 上已设为公开。",
    toastErrorSpotifyTrackNotFound: "Spotify 歌曲或专辑未找到、私密或受地区限制。",
    toastErrorInstagramPrivate: "Instagram 帖子或 Reel 属于私密、受限或已被删除。",
    toastErrorTikTokPrivate: "TikTok 视频属于私密或已被创作者删除。",
    toastErrorYouTubeUnavailable: "YouTube 视频或播放列表属于私密、年龄受限或不可用。",
    toastErrorTwitterPrivate: "X (Twitter) 推文未找到或该账号为私密账号。",
    toastErrorFacebookPrivate: "Facebook 帖子为私密或需要登录访问。",
    toastErrorMediaNotFound: "未找到媒体，或内容已被删除/设为私密。",
    toastErrorServerBlocked: "解析服务器繁忙或受到保护。请在设置中切换服务器。",
    toastAnalysisCancelled: "已取消解析",
    toastCopiedTitle: "标题复制成功！",
    toastCopiedDesc: "描述复制成功！",
    toastDownloadingSingle: "正在下载 {title}...",
    toastDownloadingMulti: "正在下载 {count} 个文件...",
    toastDownloadStart: "开始下载 {filename}...",
    toastDownloadSuccess: "文件保存成功！",
    toastDownloadFail: "多次尝试后下载失败。",
    toastDownloadFailSwitchServer: "下载失败 - 正在自动切换至服务器 {server}...",
    toastDownloadFailManualServer: "下载失败 - 请切换服务器",
    toastDownloadCancelled: "用户已取消下载。",
    toastDownloadCancelledItem: "已取消下载：{title}",
    toastBatchCompleted: "下载完成！（已保存 {count} 个项目至 Downloads/Nimiyo/）",
    toastHistoryCleared: "历史记录已清空。",
    toastSettingsSaved: "设置已保存。",
    toastCacheCleared: "应用缓存已清空。",
    toastWipeCompleted: "所有应用数据和历史已全部重置。",
    toastPlaying: "正在播放：{title}",
    toastOpeningFolder: "正在打开文件夹 Downloads/Nimiyo/{folder}...",
    toastLocation: "路径: Downloads/Nimiyo/{folder}/{filename}",
    confirmResetSettings: "确定要将所有设置重置为默认值吗？",
    confirmWipeData: "确定要清除所有历史记录、缓存和数据吗？",
    statusConnecting: "正在连接...",
    statusDownloading: "正在下载... {percent}%",
    statusSaving: "正在保存文件至存储...",
    statusReadyToDownload: "准备下载",
    statusNoUrl: "无可用 URL",
    noDownloadLinks: "暂无可用下载链接。",
    balloonSheetTitle: "正在下载",
    balloonCancelAll: "全部取消",
    queuePending: "等待中...",
    queueDownloading: "正在下载... {percent}%",
    queueCompleted: "已完成",
    queueCancelTitle: "取消",
    toggleSeeMore: "查看更多",
    toggleSeeLess: "收起",
    descLabel: "描述",
    btnCopy: "复制",
    historyPlayTitle: "播放",
    historyFolderTitle: "打开文件夹",
    historyDeleteTitle: "删除",
    mediaPlayerTitle: "媒体播放器",
    downloadModalTitle: "正在下载...",
    badgePhoto: "图片",
    badgeVideo: "视频",
    badgeAudio: "音频",
    labelAutoUpdate: "自动更新与安装权限",
    hintAutoUpdate: "允许应用自动安装更新包",
    btnPermissionAllow: "授权",
    btnPermissionGranted: "已启用",
    btnCheckUpdate: "检查更新",
    updateModalTitle: "发现新版本 🚀",
    updateChangelogTitle: "更新日志:",
    updateDownloading: "正在下载更新...",
    btnLater: "稍后提醒我",
    btnRemindLater: "稍后提醒我",
    remindScheduleTitle: "更新提醒时间：",
    remind1Day: "1 天",
    remind3Days: "3 天",
    remind1Week: "1 周",
    toastRemindScheduled: "已设置更新提醒：{time}后。",
    btnUpdateNow: "更新并安装",
    btnUpdateAndInstall: "更新并安装",
    btnManualDownload: "手动下载安装包",
    labelAccentColor: "UI 强调色",
    optAccentYellow: "黄色 (默认)",
    optAccentBlue: "蓝色",
    optAccentRed: "红色",
    optAccentGray: "灰色",
    toastDownloadingApkManual: "正在下载 APK 文件到下载目录...",
    toastManualApkDownloaded: "APK 文件已下载至下载目录，请手动安装。",
    toastAppUpToDate: "已是最新版本 (v{version})。",
    toastUpdateChecking: "正在检查更新...",
    toastUpdateCheckFailed: "检查更新失败，请检查网络连接。",
    toastPressBackAgain: "再按一次退出应用。",
    aboutFollowMe: "关注开发者:",
    btnSupportMe: "赞助支持 (Trakteer)",
    supportDesc: "助力 NIMIYO 持续更新与维护",
    btnRules: "用户条款与规范",
    rulesModalTitle: "使用条款与社区规范",
    rulesIntroText: "欢迎使用 NIMIYO 下载器。使用本应用即表示您同意遵守以下规则与条款：",
    rulesBadgeProhibited: "❌ 严禁下载",
    rulesProhibitedTitle: "禁止下载的内容：",
    rulesItemNsfw: "色情与成人内容 (18+)：任何形式的露骨色情、裸露或成人媒体。",
    rulesItemViolence: "暴力与血腥：极端暴力、人身伤害、残虐行为或危险活动。",
    rulesItemHate: "仇恨言论与骚扰：仇恨言论、歧视或网络霸凌。",
    rulesItemIllegal: "违法活动：违法犯罪行为、极端主义宣传或违禁品。",
    rulesBadgeDisclaimer: "⚖️ 版权与免责声明",
    rulesDisclaimerTitle: "版权与责任归属：",
    rulesItemPersonal: "NIMIYO 仅为个人离线归档的辅助工具。",
    rulesItemCopyright: "请尊重创作者版权。严禁未经授权将下载内容用于商业盈利。",
    rulesItemLegal: "开发者不存储或分发任何媒体文件。用户对其提交的下载链接承担全部法律责任。",
    btnAgreeRules: "我同意并理解",

    "musicPermRequiredTitle": "需要音频权限",
    "musicPermRequiredDesc": "允许应用访问设备上的音乐文件。这是安全且完全离线的。",
    "btnAllowAudioAccess": "允许访问",
    "playlistHeaderTitle": "播放列表",
    "playlistBtnAdd": "新建播放列表",
    "playlistEmptyTitle": "暂无播放列表",
    "playlistEmptyDesc": "将来自 NIMIYO、歌曲、艺术家和专辑的喜爱曲目添加到自定义播放列表。",
    "playlistBtnCreateNew": "+ 创建新播放列表",
    "playlistDetailPlayAll": "全部播放",
    "playlistDetailShuffle": "随机播放",
    "playlistDetailAddSongs": "添加歌曲",
    "playlistDetailTracksTitle": "歌曲列表",
    "playlistDetailDeletePlaylist": "删除播放列表",
    "playlistDetailEmptyTitle": "此播放列表为空",
    "playlistDetailEmptyDesc": "现在从 NIMIYO、歌曲、艺术家或专辑中添加歌曲。",
    "playlistDetailPickSongsBtn": "+ 立即选择歌曲",
    "playlistBtnSelect": "选择",
    "playlistBtnCancel": "取消",
    "playlistActionRemove": "移除",
    "playlistRemoveSelected": "移除",
    "playlistSelectAll": "全选",
    "playlistDeselectAll": "取消全选",
    "playlistTracksRemovedToast": "已从播放列表移除 {count} 首歌曲",
    "playlistConfirmRemoveSelected": "从该播放列表中移除 {count} 首歌曲？",
    "playlistReorderDone": "播放列表顺序已更新",
    "playlistDragReorderHint": "拖拽调整顺序",
    "playlistSelectedCount": "已选 {count} 首",
    "playlistSongsCount": "首歌曲",
    "playlistCountSubtitle": "个播放列表",
    "pickerModalTitle": "选择歌曲",
    "pickerSearchPlaceholder": "搜索收藏中的曲目...",
    "pickerTabAll": "全部",
    "pickerSelectAll": "全选",
    "pickerDeselectAll": "取消全选",
    "pickerSelected": "已选择",
    "pickerAvailable": "首歌曲可用",
    "pickerBtnAdd": "添加",
    "pickerNoMatch": "没有符合筛选或搜索的歌曲。",
    "createPlaylistTitle": "新建播放列表",
    "renamePlaylistTitle": "重命名播放列表",
    "createPlaylistDesc": "为你的新播放列表命名：",
    "createPlaylistPlaceholder": "例如：最爱、轻松、旅途...",
    "btnSave": "保存",
    "addToPlaylistTitle": "添加到播放列表",
    "addToPlaylistSubtitle": "选择目标播放列表",
    "quickItemAlreadyAdded": "✓ 已添加",
    "quickItemAdd": "+ 添加",
    "selectionAll": "全选",
    "selectionMove": "移动",
    "selectionDelete": "删除",
    "selectionCancel": "取消",
    "selectionTitleMove": "移动所选歌曲",
    "selectionTitleDelete": "永久删除所选歌曲",
    "moveTargetModalTitle": "移动歌曲至",
    "moveTargetModalDesc": "为所选歌曲选择移动目标：",
    "moveOptionPlaylistTitle": "播放列表",
    "moveOptionPlaylistDesc": "收录到自定义播放列表",
    "moveOptionNimiyoTitle": "Nimiyo",
    "moveOptionNimiyoDesc": "将音频文件移动到 NIMIYO 音乐文件夹",
    "deleteConfirmModalTitle": "永久删除",
    "deleteConfirmPrompt": "确定要永久删除所选的 {count} 个音频文件吗？这将从设备存储中彻底移除这些文件。",
    "btnYes": "确定",
    "musicPermGranted": "已获取音乐访问权限，正在加载曲目...",
    "musicQueueCleared": "队列已清空",
    "musicDeleteProtectedError": "删除失败：文件受 Android 系统保护",
    "playlistNameRequired": "播放列表名称不能为空",
    "playlistNameUpdated": "播放列表名称已更新",
    "playlistCreatedToast": "播放列表 \"{name}\" 已创建",
    "playlistDeletedToast": "播放列表已删除",
    "playlistSongRemoved": "已从播放列表中移除歌曲",
    "playlistEmptyToast": "此播放列表为空",
    "playlistSongNotFound": "未找到播放列表中的歌曲",
    "playlistPlayingToast": "正在播放：{name}",
    "playlistUpdatedToast": "播放列表 \"{name}\" 已更新 ({count} 首歌曲)",
    "playlistSongAlreadyIn": "歌曲已存在于 \"{name}\" 中",
    "playlistSongAddedTo": "已添加到 \"{name}\"",
    "selectionMinOneSong": "请先至少选择 1 首歌曲",
    "selectionAddedToPlaylistToast": "已将 {count} 首歌曲添加到 \"{name}\"",
    "selectionMovedToNimiyoToast": "已将 {count} 首歌曲移动到 Nimiyo 文件夹",
    "selectionAlreadyInNimiyoToast": "所选歌曲均已在 Nimiyo 文件夹中",
    "selectionPermanentDeletedToast": "已永久删除 {count} 个音频文件",
    "selectionNoFilesDeletedToast": "未删除任何文件",
    "manageStoragePermRequired": "需要存储权限：\n\n在 Android 11 及以上版本中，Nimiyo 需要“所有文件访问权限”才能永久删除和管理设备上的音频文件。\n\n现在打开设置以启用此权限？",
    "confirmDeletePlaylist": "确定要删除此播放列表吗？",
    "musicAddToPlaylist": "添加到播放列表",
    "musicMoveToNimiyo": "移动到 Nimiyo",
    "musicSelectSong": "选择歌曲",
    "musicDurationHour": "小时",
    "musicDurationMin": "分钟",
    "about-text": "Nimidz 是一款快速且多功能的媒体下载器。由 coflyn 用心制作。",
    "anim-fast": "快速",
    "anim-normal": "正常",
    "anim-off": "关闭",
    "anim-slow": "慢速",
    "backup-monthly": "每月",
    "backup-off": "关闭",
    "backup-weekly": "每周",
    "batch-download-all": "下载全部",
    "batch-modal-title": "批量下载队列",
    "batch-photo-all": "下载所有图片",
    "batch-photo-first": "仅下载第一张图片",
    "batch-photo-pdf": "合并为单个 PDF",
    "btn-analyze": "解析",
    "btn-analyze-batch": "批量分析",
    "btn-cancel": "取消",
    "btn-cancel-download": "取消下载",
    "btn-check": "检查",
    "btn-clear": "清除",
    "btn-clear-all": "清空全部",
    "btn-close": "关闭",
    "btn-delete": "删除",
    "btn-done": "完成",
    "btn-download-all-title": "下载全部",
    "btn-edit": "编辑",
    "btn-open-settings": "打开设置",
    "btn-processing": "处理中...",
    "btn-report": "反馈",
    "btn-reset": "重置",
    "btn-reset-default": "恢复默认设置",
    "btn-reset-settings": "重置",
    "btn-share": "分享",
    "btn-stop": "停止",
    "btn-update": "更新",
    "btn-view": "查看",
    "concurrent-1": "1个（顺序）",
    "concurrent-2": "2个同时",
    "concurrent-3": "3个同时",
    "concurrent-5": "5个同时",
    "confirm-reset-settings": "将所有设置重置为默认值？这不会删除您的历史记录或下载的文件。",
    "days-1": "1 天",
    "days-30": "30 天",
    "days-7": "7 天",
    "days-90": "90 天",
    "days-off": "关闭",
    "desc-clearcache": "移除临时缩略图和图片预览以释放空间。您已下载的媒体文件将保持安全。是否继续？",
    "desc-wipedata": "重置历史记录、设置和缓存。这不会删除相册中已下载的视频或音乐。是否继续？",
    "dl-stats-total": "总下载次数",
    "doh-cloudflare": "Cloudflare (1.1.1.1)",
    "doh-google": "Google (8.8.8.8)",
    "doh-off": "关闭 (系统DNS)",
    "download-all-complete": "全部 ${count} 个文件已加入下载队列！",
    "downloading-progress": "正在下载...",
    "err-yt-playlist-source": "YouTube播放列表需要'ytmp3.gg'服务器。",
    "filename-default": "默认",
    "filename-title": "仅标题",
    "filename-title-date": "标题 + 日期",
    "filename-title-platform": "标题 + 平台",
    "font-default": "Inter",
    "font-display": "Display Bold",
    "font-jakarta": "Plus Jakarta Sans",
    "font-mono": "现代 Mono",
    "font-serif": "经典 Serif",
    "guide-step-1": "从 TikTok、Instagram、YouTube、Twitter/X、抖音等 14+ 平台复制媒体链接。",
    "guide-step-2": "点击“粘贴”（或旁边的批量按钮）解析链接并选择清晰度。",
    "guide-step-3": "点击“下载”直接保存文件到设备存储，并实时追踪进度。",
    "guide-step-4": "在“历史”中访问已保存的媒体进行离线播放，或使用生物识别锁保护隐私。",
    "guide-title": "用户指南",
    "history-desc": "您最近的下载",
    "history-unlimited": "无限制",
    "howtouse-steps": ["从任何支持的平台复制链接。","返回 Nimidz 并点击粘贴按钮。","等待分析完成。","点击下载以保存。"],
    "label-about": "关于 Nimidz",
    "label-anim-speed": "动画速度",
    "label-animated-bg": "动态背景",
    "label-auto-analyze": "粘贴时自动解析",
    "label-auto-clear-input": "自动清空输入框",
    "label-auto-folder": "按平台创建子文件夹",
    "label-auto-retry": "下载自动重试",
    "label-auto-update": "自动检查更新",
    "label-autoclear-cache": "自动清除缓存",
    "label-autoclear-history": "自动清除历史",
    "label-autodownload": "自动下载链接",
    "label-autofolder": "平台子文件夹",
    "label-autoloop": "循环播放",
    "label-autopaste": "自动粘贴链接",
    "label-autoplay": "自动播放",
    "label-available-downloads": "可用下载",
    "label-batch-photo-mode": "批量图片模式",
    "label-bg-shapes": "背景形状",
    "label-brightness": "亮度",
    "label-bypass-ssl": "忽略 SSL 错误",
    "label-cache-cleared": "缓存已成功清除！",
    "label-cellular-warning": "蜂窝数据警告",
    "label-check-failed": "检查失败",
    "label-check-failed-msg": "无法连接到服务器。请检查您的连接。",
    "label-choose-server": "选择服务器:",
    "label-clearcache": "清除缓存",
    "label-compact-mode": "紧凑模式",
    "label-concurrent-downloads": "并发下载",
    "label-content": "内容",
    "label-darkmode": "深色模式",
    "label-data-wiped": "应用重置成功。您的相册很安全！",
    "label-datasaver": "省流量模式",
    "label-developer": "开发者",
    "label-dl-stats": "下载统计",
    "label-doh": "DNS over HTTPS",
    "label-dont-show": "不再显示",
    "label-dont-show-again": "不再显示",
    "label-download": "下载",
    "label-download-complete": "下载完成",
    "label-download-sound": "下载完成提示音",
    "label-error": "错误",
    "label-fatal": "致命错误",
    "label-fatal-error": "致命错误",
    "label-file-missing": "文件缺失",
    "label-filename-template": "文件名格式",
    "label-font": "应用字体",
    "label-force-ipv4": "强制 IPv4 模式",
    "label-haptic": "振动与触觉反馈",
    "label-header-spoofing": "Anti-403 请求头保护",
    "label-hide-progress": "隐藏下载进度条",
    "label-history-limit": "历史保留上限",
    "label-howtouse": "使用说明",
    "label-incognito": "无痕模式",
    "label-items-count": "${count} 个",
    "label-keep-awake": "保持屏幕常亮",
    "label-language": "语言",
    "label-lock-type": "锁定类型",
    "label-max-retry": "最大重试次数",
    "label-modal-error": "无法打开弹窗",
    "label-offline": "离线",
    "label-opening-wa": "正在打开 WhatsApp...",
    "label-option": "选项",
    "label-overwrite-files": "重复文件",
    "label-path-music": "音乐保存路径",
    "label-path-presets": "预设 / 推荐路径",
    "label-path-video": "视频保存路径",
    "label-platform": "平台",
    "label-platforms": "支持的平台",
    "label-prefer-server": "首选服务器",
    "label-privacy-lock": "隐私锁",
    "label-reportbug": "反馈 Bug",
    "label-request-timeout": "超时限制",
    "label-reset-settings": "重置设置",
    "label-saving": "保存中...",
    "label-server": "服务器",
    "label-server-options": "服务器选项",
    "label-share-file": "分享文件",
    "label-share-link": "分享链接",
    "label-share-media": "分享媒体",
    "label-shareapp": "分享 Nimidz 应用",
    "label-storagesize": "媒体总大小",
    "label-subfolder-downloads": "下载文件夹中的子文件夹",
    "label-support": "支持我",
    "label-test-latency": "测试服务器延迟",
    "label-text-size": "字体大小",
    "label-toast-duration": "提示持续时间",
    "label-up-to-date": "您已是最新版本。",
    "label-update": "更新",
    "label-update-available": "有可用更新",
    "label-user-agent": "User-Agent",
    "label-version": "版本",
    "label-wifi-only": "仅限 Wi-Fi 下载",
    "label-wipedata": "清除所有数据",
    "lang-ar": "阿拉伯语",
    "lang-en": "英语",
    "lang-hi": "印地语",
    "lang-id": "印尼语",
    "lang-ja": "日语",
    "lang-ko": "韩语",
    "lang-ru": "俄语",
    "lang-tl": "他加禄语",
    "lang-zh": "中文 (简体)",
    "loader-analyzing": "正在解析链接...",
    "loader-phrases": ["正在解析链接...","正在获取媒体...","正在提取数据...","正在抓取内容...","正在寻找像素...","正在处理请求...","即将完成..."],
    "lock-type-biometric": "生物识别",
    "lock-type-none": "无",
    "lock-type-pin": "PIN 码",
    "menu-about-desc": "版本、帮助与开发者链接",
    "menu-about-title": "关于与帮助",
    "menu-advanced-desc": "历史保留与媒体播放器",
    "menu-advanced-title": "高级",
    "menu-animation-desc": "动态背景、形状和亮度",
    "menu-animation-title": "动画背景",
    "menu-appearance-desc": "主题与触觉反馈",
    "menu-appearance-title": "外观与体验",
    "menu-general-desc": "语言、自动化与应用安全",
    "menu-general-title": "通用",
    "menu-network-desc": "首选服务器、User-Agent 与省流量",
    "menu-network-title": "网络与性能",
    "menu-storage-desc": "保存路径、文件名与缓存清理",
    "menu-storage-title": "存储与下载",
    "msg-cellular-warning": "您当前使用的是蜂窝数据。确定要继续下载吗？",
    "msg-clear-all-confirm": "确定要删除所有下载历史记录吗？",
    "msg-delete-item-confirm": "从历史记录中移除此项？",
    "header-history": "下载历史",
    "header-settings": "设置",
    "tab-history": "历史",
    "tab-home": "首页",
    "tab-settings": "设置",
    "overwrite-overwrite": "覆盖现有文件",
    "overwrite-rename": "自动重命名",
    "overwrite-skip": "如存在则跳过",
    "pdf-btn-gallery": "保存为 PDF (相册)",
    "pdf-btn-images": "将图片保存为 PDF",
    "pdf-error-no-images": "没有处理任何有效图片。",
    "pdf-images-detected": "检测到的图片",
    "pdf-pages": "页数",
    "pdf-toast-finalizing": "正在生成 PDF 结构... 请稍候。",
    "pdf-toast-processing": "已处理 ${count}/${total} 张图片...",
    "pdf-toast-saved": "PDF 已成功保存至 Download/Nimidz",
    "pdf-toast-saving": "正在保存到设备... 可能需要几秒钟。",
    "pdf-toast-starting": "正在开始导出 PDF...（大型图集可能需要一些时间）",
    "pin-enter-title": "输入 4 位 PIN 码",
    "placeholder-batch-link": "粘贴多个链接（每行一个）...",
    "placeholder-paste-link": "在此粘贴链接...",
    "player-error-file": "无法加载本地文件。",
    "player-error-stream": "无法流式传输媒体。",
    "retry-1": "1 (不重试)",
    "retry-2": "2次尝试",
    "retry-3": "3次尝试",
    "retry-5": "5次尝试",
    "server-1": "服务器 1 (主)",
    "server-2": "服务器 2 (备)",
    "server-ask": "总是询问",
    "settings-desc": "配置您的应用体验",
    "shape-bubbles": "气泡",
    "shape-particles": "粒子",
    "shape-stars": "星星",
    "shape-waves": "波浪",
    "share-err-error": "解析过程中发生错误。",
    "share-err-failed": "解析链接失败。",
    "share-err-no-links": "未找到下载链接。",
    "share-err-unsupported": "不支持的平台链接。",
    "share-msg": "快来看看 Nimidz，这是一款超棒的媒体下载应用！https://github.com/coflyn/Nimidz",
    "share-panel-sub": "配置并下载媒体",
    "share-panel-title": "Nimidz 快速保存",
    "text-large": "大",
    "text-medium": "中",
    "text-small": "小",
    "timeout-120": "120 秒",
    "timeout-15": "15 秒",
    "timeout-30": "30 秒",
    "timeout-60": "60 秒",
    "toast-anim-speed": "动画速度: ",
    "toast-animatedbg-off": "动态背景已关闭",
    "toast-animatedbg-on": "动态背景已开启",
    "toast-anti403-off": "Anti-403防封锁已关闭",
    "toast-anti403-on": "Anti-403防封锁已开启",
    "toast-autoanalyze-off": "自动分析已关闭",
    "toast-autoanalyze-on": "自动分析已开启",
    "toast-autoclear-cache-off": "自动清除缓存已关闭",
    "toast-autoclear-cache-on": "自动清除缓存已开启",
    "toast-autoclear-history-off": "自动清理历史已关闭",
    "toast-autoclear-history-on": "自动清理历史已开启",
    "toast-autoclearinput-off": "自动清除输入已关闭",
    "toast-autoclearinput-on": "自动清除输入已开启",
    "toast-autodownload-off": "自动下载已关闭",
    "toast-autodownload-on": "自动下载已开启",
    "toast-autofolder-off": "平台子文件夹已关闭",
    "toast-autofolder-on": "平台子文件夹已开启",
    "toast-autoloop-off": "循环播放已关闭",
    "toast-autoloop-on": "循环播放已开启",
    "toast-autopaste-off": "自动粘贴已关闭",
    "toast-autopaste-on": "自动粘贴已开启",
    "toast-autoplay-off": "自动播放已关闭",
    "toast-autoplay-on": "自动播放已开启",
    "toast-autoretry-off": "自动重试已关闭",
    "toast-autoretry-on": "自动重试已开启",
    "toast-autoupdate-off": "自动检查更新已关闭",
    "toast-autoupdate-on": "自动检查更新已开启",
    "toast-bypassssl-off": "忽略SSL错误已关闭",
    "toast-bypassssl-on": "忽略SSL错误已开启",
    "toast-cache-error": "清除缓存失败。",
    "toast-cellularwarning-off": "蜂窝网络警告已关闭",
    "toast-cellularwarning-on": "蜂窝网络警告已开启",
    "toast-clipboard-empty": "剪贴板为空",
    "toast-compact-off": "紧凑模式已禁用",
    "toast-compact-on": "紧凑模式已启用",
    "toast-connection-lost": "连接已断开，请检查网络。",
    "toast-copy-failed": "复制失败",
    "toast-copy-success": "已复制到剪贴板",
    "toast-darkmode-off": "浅色模式已开启",
    "toast-darkmode-on": "深色模式已开启",
    "toast-datasaver-off": "流量节省已关闭",
    "toast-datasaver-on": "流量节省已开启",
    "toast-doh": "DNS over HTTPS: ",
    "toast-download-cancelled": "下载已取消",
    "toast-download-complete": "下载完成",
    "toast-download-failed": "下载失败",
    "toast-dur-1": "1秒",
    "toast-dur-2": "2秒",
    "toast-dur-3": "3秒",
    "toast-dur-5": "5秒",
    "toast-failed": "失败:",
    "toast-forceipv4-off": "强制IPv4已关闭",
    "toast-forceipv4-on": "强制IPv4已开启",
    "toast-haptic-off": "触觉反馈已关闭",
    "toast-haptic-on": "触觉反馈已开启",
    "toast-hide-progress-off": "下载进度条已显示",
    "toast-hide-progress-on": "下载进度条已隐藏",
    "toast-incognito-off": "无痕模式已关闭",
    "toast-incognito-on": "无痕模式已开启",
    "toast-keepawake-off": "保持屏幕常亮已关闭",
    "toast-keepawake-on": "保持屏幕常亮已开启",
    "toast-memory-error": "转换过程中发生内存错误。",
    "toast-no-batch-urls": "未在文本中找到有效的 URL",
    "toast-no-link": "剪贴板中未找到链接",
    "toast-overwrite": "重复文件: ",
    "toast-pasted-share": "已粘贴来自分享的链接",
    "toast-path-updated": "存储路径已更新",
    "toast-pdf-downloaded": "PDF 已下载",
    "toast-press-back-exit": "再次按返回键退出",
    "toast-privacy-off": "隐私锁已停用",
    "toast-privacy-on": "隐私锁已启用",
    "toast-reset-settings": "设置已重置为默认值",
    "toast-saved": "已保存:",
    "toast-sound-off": "完成提示音已关闭",
    "toast-sound-on": "完成提示音已开启",
    "toast-storage-error": "存储错误：请确保存储空间充足。",
    "toast-text-size": "字体大小: ",
    "toast-wifi-needed": "需要 Wi-Fi 网络连接",
    "toast-wifi-off": "仅 Wi-Fi 已关闭",
    "toast-wifi-on": "仅 Wi-Fi 已开启",
    "ua-chrome": "移动版 Chrome",
    "ua-default": "默认",
    "ua-desktop": "桌面版 Chrome",
    "ua-safari": "iOS Safari"
  },
  ja: {
    appTitle: "NIMIYO",
    inputPlaceholder: "リンクをここに貼り付け...",
    btnAnalyze: "リンクを解析",
    btnAnalyzing: "解析中...",
    btnCancel: "キャンセル",
    btnCancelAnalysis: "キャンセル",
    btnBack: "戻る",
    platformsTitle: "対応プラットフォーム",
    downloadsTitle: "ダウンロードリンク",
    btnDownload: "ダウンロード",
    btnDownloading: "ダウンロード中...",
    btnDownloadAll: "すべてダウンロード ({count})",
    historyTitle: "ダウンロード履歴",
    historyEmpty: "ダウンロード履歴はまだありません。",
    btnClearHistory: "履歴をクリア",
    headerHistory: "ダウンロード履歴",
    headerSettings: "設定",
    tabHome: "ホーム",
    tabPlayer: "プレイヤー",
    tabHistory: "履歴",
    tabSettings: "設定",
    headerPlayer: "プレイヤー",
    musicTabNimiyo: "NIMIYO",
    musicTabSongs: "曲",
    musicTabArtists: "アーティスト",
    musicTabAlbums: "アルバム",
    musicSearchPlaceholder: "曲、アーティスト、アルバムを検索...",
    musicNoSongs: "ローカル音声ファイルが見つかりません",
    musicNoNimiyoSongs: "NIMIYO フォルダに音声がありません",
    musicNoArtists: "アーティストが見つかりません",
    musicNoAlbums: "アルバムが見つかりません",
    musicUnknownTitle: "無題",
    musicUnknownArtist: "不明なアーティスト",
    musicUnknownAlbum: "不明なアルバム",
    musicNowPlaying: "再生中",
    musicUpNext: "次に再生:",
    musicQueueTitle: "再生キュー",
    musicClearQueue: "クリア",
    musicEmptyQueue: "キューが空です",
    musicLyricsBtn: "歌詞",
    musicShowCover: "カバー",
    musicLyricsTranslation: "翻訳",
    toastNoTranslationAvailable: "この曲の歌詞の翻訳はまだありません",
    musicTapForLyrics: "歌詞",
    musicTapToFlip: "タップしてカバーに戻る",
    musicFetchingLyrics: "オンライン歌詞を取得中...",
    musicNoLyrics: "この曲の歌詞は見つかりませんでした",
    musicAddedToQueue: "キューに追加しました",
    musicWillPlayNext: "次に再生されます",
    musicPlayNow: "今すぐ再生",
    musicPlayNext: "次に再生",
    musicAddToQueue: "キューに追加",
    musicShuffleOn: "シャッフル ON",
    musicShuffleOff: "シャッフル OFF",
    musicRepeatOff: "リピート OFF",
    musicRepeatAll: "全曲リピート",
    musicRepeatOne: "1曲リピート",
    musicScanError: "ローカル音声のスキャンに失敗しました",
    musicPlaybackError: "未対応の形式またはファイルが移動されました",
    musicBtnShuffleAll: "すべてシャッフル",
    musicSortTitle: "並び替え",
    musicSortTitleAsc: "曲名 (昇順)",
    musicSortTitleDesc: "曲名 (降順)",
    musicSortArtistAsc: "アーティスト (昇順)",
    musicSortAlbumAsc: "アルバム (昇順)",
    musicSortDurDesc: "再生時間 (長い順)",
    musicSortDurAsc: "再生時間 (短い順)",
    musicGoToArtist: "アーティストを表示",
    musicGoToAlbum: "アルバムを表示",
    musicSongInfo: "楽曲の詳細情報",
    musicShareAudio: "音声を共有",
    musicDeleteTrack: "ファイルを削除",
    confirmDeleteTrack: "端末のストレージからこの音声ファイルを削除しますか？",
    musicTrackDeleted: "音声ファイルを削除しました",
    musicSleepTimerBtn: "タイマー",
    musicSleepTimerTitle: "スリープタイマー",
    musicTimerOff: "オフ",
    musicTimerEndSong: "この曲の終了時",
    musicTimerOffToast: "スリープタイマーをオフにしました",
    musicTimerEndSongToast: "現在の曲が終わったら停止します",
    musicSleepTimerSet: "タイマーを設定しました",
    musicSpeedTitle: "再生速度",
    mixtoolsTitle: "MixTools",
    mixtoolsResetAll: "すべてリセット",
    mixtoolsSpeedTitle: "Speed FX",
    mixtoolsSpeedDesc: "再生テンポを調整",
    mixtoolsGuideSlowed: "Slowed",
    mixtoolsGuideNormal: "Normal",
    mixtoolsGuideSpeedUp: "SpeedUp",
    mixtoolsReverbTitle: "Reverb FX",
    mixtoolsReverbDesc: "3D空間残響エフェクト",
    mixtoolsReverbStadium: "▲ 広大",
    mixtoolsReverbClub: "▼ タイト",
    mixtoolsReverbDry: "◀ Dry",
    mixtoolsReverbWet: "Wet ▶",
    mixtoolsReverbHint: "上 = 空間 • 右 = 残響",
    mixtoolsResetSuccess: "MixTools がデフォルトにリセットされました",
    mixtoolsStatusDecay: "減衰",
    editLyricsTitle: "歌詞を編集",
    editLyricsFormatHelp: "形式: <code>[00:00.00] 歌詞テキスト</code> (分:秒.ミリ秒)",
    editLyricsPlaceholder: "[00:00.00] 最初の歌詞行...\n[00:15.30] 2番目の歌詞行...",
    editLyricsRefresh: "更新",
    editLyricsRefreshTitle: "リセットして新しい歌詞を検索",
    editLyricsImport: "インポート",
    editLyricsImportTitle: "ローカルの .LRC 歌詞ファイルをインポート",
    editLyricsDownloadLrcTitle: ".LRC ファイルをダウンロード",
    resetLyricsTitle: "歌詞リセットオプション",
    resetLyricsSubtitle: "歌詞の検索元を選択",
    resetLyricsOptDefaultTitle: "自動検索 (デフォルト / LRCLIB)",
    resetLyricsOptDefaultDesc: "ローカル歌詞を削除し、オンラインから同期歌詞を再取得",
    resetLyricsOptLyricsifyTitle: "Lyricsify.com で検索",
    resetLyricsOptLyricsifyDesc: "ブラウザで Lyricsify を開き、.LRC 歌詞をコピー",
    resetLyricsOptImportTitle: "ローカル .LRC ファイルをインポート",
    resetLyricsOptImportDesc: "端末に保存されている .lrc または .txt 歌詞ファイルを選択",
    toastSelectSongFirst: "先に曲を選択してください",
    toastLyricsSearchingOnline: "歌詞を自動検索中 (LRCLIB)...",
    toastLyricsResetSuccess: "オンラインデータベースから歌詞をリセットしました！",
    toastLyricsNotFoundOnline: "オンラインデータベースに歌詞が見つかりませんでした",
    toastLyricsFetchFailed: "オンライン歌詞の取得に失敗しました",
    toastLyricsLyricsifyHint: "歌詞をリセットしました。Lyricsify から .LRC 歌詞をコピーして貼り付けてください",
    toastLyricsImportSuccess: "歌詞 {file} を正常にインポートしました",
    toastLyricsImportFailed: "歌詞ファイルの読み込みに失敗しました",
    toastLyricsEmptyWarning: "歌詞を空にすることはできません",
    toastLyricsSavedSuccess: "歌詞を編集し、ローカルに保存しました",
    toastLyricsNoDownload: "ダウンロード可能な歌詞がありません",
    toastLyricsSavedTo: "歌詞を保存しました: {file} (Nimiyo/LyricsYo)",
    toastLyricsDownloaded: "歌詞 {file} をダウンロードしました",
    toastLyricsDownloadFailed: "歌詞のダウンロードに失敗しました",
    btnReset: "リセット",
    musicSongInfoTitle: "楽曲の詳細情報",
    musicLabelTitle: "曲名",
    musicLabelArtist: "アーティスト",
    musicLabelAlbum: "アルバム",
    musicLabelDuration: "再生時間",
    musicLabelFileSize: "ファイルサイズ",
    musicLabelFormat: "形式",
    musicLabelFilePath: "保存場所",
    musicSortModalTitle: "並び替え順",
    musicShuffleAllStarted: "すべての曲をシャッフル再生中",
    musicTapForLyrics: "歌詞",
    musicTapToFlip: "タップでカバーへ",
    labelUiTheme: "UIデザイン",
    optThemeNeobrutalism: "ネオブュータリズム (大胆)",
    optThemeSoftUi: "ソフトUI (ニューモフィズム)",
    settingsTitle: "設定",
    groupGeneral: "一般設定",
    menuGeneralDesc: "言語、自動貼り付けと自動解析",
    labelLanguage: "言語",
    labelAutoPaste: "リンクを自動貼り付け",
    labelAutoAnalyze: "貼り付け時に自動解析",
    labelAutoClearInput: "入力欄を自動クリア",
    labelIncognito: "シークレットモード",
    groupAppearance: "外観とUIUX",
    menuAppearanceDesc: "ダークモード、効果音と触覚",
    labelDarkMode: "ダークモード",
    labelCompletionSound: "完了通知音",
    labelHaptic: "バイブレーションと触覚",
    labelAppFont: "アプリのフォント",
    optFontMisans: "MiSans (デフォルト)",
    optFontInter: "Inter モダン",
    optFontOutfit: "Outfit デザイン",
    optFontMono: "等幅フォント",
    groupStorage: "ストレージと保存",
    menuStorageDesc: "ファイル名、重複処理とキャッシュ",
    labelStoragePaths: "保存フォルダー",
    labelFilenameTemplate: "ファイル名フォーマット",
    optFilenameTitle: "タイトルのみ",
    optFilenameTitlePlatform: "タイトル + プラットフォーム",
    optFilenameTitleDate: "タイトル + 日付",
    labelOverwriteMode: "重複ファイル処理",
    optOverwriteRename: "自動リネーム (_1, _2)",
    optOverwriteOverwrite: "上書き保存",
    optOverwriteSkip: "既存の場合はスキップ",
    labelConcurrentDl: "同時ダウンロード数",
    optConcurrent1: "1 (順次)",
    optConcurrent2: "2 件同時",
    optConcurrent3: "3 件同時",
    optConcurrent5: "5 件同時",
    labelBatchPhotoMode: "画像一括ダウンロード",
    optBatchPhotoAll: "すべての画像を保存",
    optBatchPhotoFirst: "最初の画像のみ",
    labelAutoDownload: "解析完了後に自動ダウンロード",
    labelTotalStorage: "保存済みメディア容量",
    btnClearCache: "アプリキャッシュをクリア",
    btnWipeData: "すべてのデータを削除して初期化",
    groupNetwork: "ネットワークとパフォーマンス",
    menuNetworkDesc: "自動再試行、Wi-Fi と DNS",
    labelAutoRetry: "自動再試行",
    labelMaxRetry: "最大再試行回数",
    optRetry1: "1 (再試行なし)",
    optRetry2: "2 回試行",
    optRetry3: "3 回試行",
    optRetry5: "5 回試行",
    labelWifiOnly: "Wi-Fi 接続時のみダウンロード",
    labelDoh: "DNS over HTTPS",
    optDohOff: "オフ (システム DNS)",
    labelHeaderSpoofing: "403防止ヘッダー保護",
    groupAdvanced: "詳細設定",
    menuAdvancedDesc: "履歴保持、プレーヤーと初期化",
    labelHistoryLimit: "履歴保持件数上限",
    optUnlimited: "無制限",
    optHistory50: "50 件",
    optHistory100: "100 件",
    optHistory200: "200 件",
    labelAutoClearDays: "履歴の自動削除",
    labelAutoClearCacheDays: "キャッシュの自動クリア",
    optDaysOff: "オフ",
    optDays1: "1 日",
    optDays7: "7 日",
    optDays30: "30 日",
    optDays90: "90 日",
    labelAutoPlay: "プレーヤーで自動再生",
    labelAutoLoop: "プレーヤーでループ再生",
    labelKeepAwake: "ダウンロード・再生時に画面を点灯維持",
    btnResetSettings: "すべての設定を初期値に戻す",
    groupAbout: "アプリについてとヘルプ",
    menuAboutDesc: "バージョン、情報と開発チーム",
    aboutVersion: "バージョン 2.1.1 (Lite)",
    aboutDesc: "scrapr をベースに構築されたプレミアムでモダン、軽量なメディアダウンローダー。",
    aboutThanks: "スペシャルサンクス:",
    toastClipboardEmpty: "クリップボードが空か、有効なテキストリンクが含まれていません。",
    toastInvalidUrl: "対応プラットフォームの有効なリンクを入力してください。",
    toastDetecting: "プラットフォームを検出中...",
    toastScraping: "スクレイパーを実行中 ({scraper})...",
    toastScrapeSuccess: "解析が完了しました！",
    toastScrapeFail: "スクレイパー {scraper} でエラーが発生しました: {message}",
    toastScrapeAllFailed: "リンクの解析に失敗しました。URLを確認するか、設定でサーバーを変更してください。",
    toastUnsupportedPlatform: "サポートされていないプラットフォームのリンクか、形式が無効です。",
    toastErrorOffline: "インターネット接続がありません。データ通信または Wi-Fi を確認してください。",
    toastErrorTimeout: "接続がタイムアウトしました。サーバーまたはネットワークが遅延しています。",
    toastErrorSpotifyPlaylistPrivate: "Spotify プレイリストが非公開か見つかりません。公開設定になっているか確認してください。",
    toastErrorSpotifyTrackNotFound: "Spotify の楽曲またはアルバムが見つからないか、非公開または地域制限されています。",
    toastErrorInstagramPrivate: "Instagram の投稿または Reel は非公開、制限されているか、削除されています。",
    toastErrorTikTokPrivate: "TikTok 動画は非公開か、投稿者によって削除されています。",
    toastErrorYouTubeUnavailable: "YouTube 動画または再生リストが非公開、年齢制限、または利用できません。",
    toastErrorTwitterPrivate: "X (Twitter) の投稿が見つからないか、アカウントが非公開です。",
    toastErrorFacebookPrivate: "Facebook の投稿は非公開か、ログインが必要です。",
    toastErrorMediaNotFound: "メディアが見つからないか、非公開または削除されています。",
    toastErrorServerBlocked: "スクレイパーサーバーが混雑または保護されています。設定でサーバーを変更してください。",
    toastAnalysisCancelled: "解析をキャンセルしました",
    toastCopiedTitle: "タイトルをコピーしました！",
    toastCopiedDesc: "説明をコピーしました！",
    toastDownloadingSingle: "{title} をダウンロード中...",
    toastDownloadingMulti: "{count} 個のファイルをダウンロード中...",
    toastDownloadStart: "{filename} のダウンロードを開始中...",
    toastDownloadSuccess: "ファイルを正常に保存しました！",
    toastDownloadFail: "複数回試行後、ダウンロードに失敗しました。",
    toastDownloadFailSwitchServer: "ダウンロード失敗 - サーバー {server} に切り替えています...",
    toastDownloadFailManualServer: "ダウンロード失敗 - サーバーを変更してください",
    toastDownloadCancelled: "ユーザーによってダウンロードがキャンセルされました。",
    toastDownloadCancelledItem: "ダウンロードをキャンセルしました: {title}",
    toastBatchCompleted: "ダウンロード完了！（{count} 件を Downloads/Nimiyo/ に保存しました）",
    toastHistoryCleared: "履歴を正常にクリアしました。",
    toastSettingsSaved: "設定を保存しました。",
    toastCacheCleared: "アプリキャッシュを正常にクリアしました。",
    toastWipeCompleted: "すべてのデータと履歴を初期化しました。",
    toastPlaying: "再生中: {title}",
    toastOpeningFolder: "フォルダー Downloads/Nimiyo/{folder} を開いています...",
    toastLocation: "場所: Downloads/Nimiyo/{folder}/{filename}",
    confirmResetSettings: "すべての設定をデフォルトに戻しますか？",
    confirmWipeData: "すべての履歴、キャッシュ、データを完全に削除しますか？",
    statusConnecting: "接続中...",
    statusDownloading: "ダウンロード中... {percent}%",
    statusSaving: "ファイルをストレージに保存中...",
    statusReadyToDownload: "ダウンロード可能",
    statusNoUrl: "URLなし",
    noDownloadLinks: "利用可能なダウンロードリンクがありません。",
    balloonSheetTitle: "進行中のダウンロード",
    balloonCancelAll: "すべてキャンセル",
    queuePending: "待機中...",
    queueDownloading: "ダウンロード中... {percent}%",
    queueCompleted: "完了",
    queueCancelTitle: "キャンセル",
    toggleSeeMore: "もっと見る",
    toggleSeeLess: "閉じる",
    descLabel: "説明",
    btnCopy: "コピー",
    historyPlayTitle: "再生",
    historyFolderTitle: "フォルダーを開く",
    historyDeleteTitle: "削除",
    mediaPlayerTitle: "メディアプレーヤー",
    downloadModalTitle: "ダウンロード中...",
    badgePhoto: "画像",
    badgeVideo: "動画",
    badgeAudio: "音声",
    labelAutoUpdate: "自動更新とインストール権限",
    hintAutoUpdate: "アプリの自動更新インストールを許可",
    btnPermissionAllow: "許可",
    btnPermissionGranted: "有効",
    btnCheckUpdate: "更新を確認",
    updateModalTitle: "アップデートが利用可能です 🚀",
    updateChangelogTitle: "更新履歴:",
    updateDownloading: "更新をダウンロード中...",
    btnLater: "後で通知する",
    btnRemindLater: "後で通知する",
    remindScheduleTitle: "アップデート再通知の時期：",
    remind1Day: "1 日後",
    remind3Days: "3 日後",
    remind1Week: "1 週間後",
    toastRemindScheduled: "{time}後にアップデートを再通知します。",
    btnUpdateNow: "更新してインストール",
    btnUpdateAndInstall: "更新してインストール",
    btnManualDownload: "手動ダウンロード・インストール",
    labelAccentColor: "UIアクセントカラー",
    optAccentYellow: "イエロー (既定)",
    optAccentBlue: "ブルー",
    optAccentRed: "レッド",
    optAccentGray: "グレー",
    toastDownloadingApkManual: "ダウンロードフォルダに APK を保存中...",
    toastManualApkDownloaded: "APK ファイルがダウンロードフォルダに保存されました。手動でインストールしてください。",
    toastAppUpToDate: "最新バージョンを使用しています (v{version})。",
    toastUpdateChecking: "更新を確認中...",
    toastUpdateCheckFailed: "更新の確認に失敗しました。接続を確認してください。",
    toastPressBackAgain: "もう一度戻るを押すと終了します。",
    aboutFollowMe: "開発者をフォロー:",
    btnSupportMe: "開発者を支援 (Trakteer)",
    supportDesc: "NIMIYO の継続的な更新を支援",
    btnRules: "利用規約とルール",
    rulesModalTitle: "利用規約とガイドライン",
    rulesIntroText: "NIMIYO ダウンローダーへようこそ。本アプリを使用することにより、以下の規約に同意したものとみなされます：",
    rulesBadgeProhibited: "❌ 厳格な禁止事項",
    rulesProhibitedTitle: "ダウンロード禁止コンテンツ：",
    rulesItemNsfw: "ポルノおよび成人向け (18+)：あらゆる露骨な性的コンテンツ、ヌード、成人向けメディア。",
    rulesItemViolence: "暴力および残虐表現：過激な暴力、身体的危害、残虐行為、危険な活動。",
    rulesItemHate: "ヘイトスピーチおよび嫌がらせ：差別的表現やネットいじめ。",
    rulesItemIllegal: "違法コンテンツ：法律違反、過激派の宣伝、違法薬物。",
    rulesBadgeDisclaimer: "⚖️ 規約と免責事項",
    rulesDisclaimerTitle: "著作権と免責事項：",
    rulesItemPersonal: "NIMIYO は個人利用のオフライン保存専用ユーティリティです。",
    rulesItemCopyright: "著作権を尊重してください。無断での商用再配布は禁止されています。",
    rulesItemLegal: "開発者はメディアファイルを保存していません。ユーザーが利用に対して法的責任を負います。",
    btnAgreeRules: "同意して続ける",

    "musicPermRequiredTitle": "音声の権限が必要です",
    "musicPermRequiredDesc": "端末上の音楽ファイルへのアクセスを許可してください。安全かつ完全オフラインです。",
    "btnAllowAudioAccess": "アクセスを許可",
    "playlistHeaderTitle": "プレイリスト",
    "playlistBtnAdd": "新規プレイリスト",
    "playlistEmptyTitle": "プレイリストがありません",
    "playlistEmptyDesc": "NIMIYO、曲、アーティスト、アルバムからお気に入りの曲をカスタムプレイリストに集めましょう。",
    "playlistBtnCreateNew": "+ 新しいプレイリストを作成",
    "playlistDetailPlayAll": "すべて再生",
    "playlistDetailShuffle": "シャッフル",
    "playlistDetailAddSongs": "曲を追加",
    "playlistDetailTracksTitle": "曲リスト",
    "playlistDetailDeletePlaylist": "プレイリストを削除",
    "playlistDetailEmptyTitle": "このプレイリストは空です",
    "playlistDetailEmptyDesc": "NIMIYO、曲、アーティスト、アルバムから曲を追加しましょう。",
    "playlistDetailPickSongsBtn": "+ 今すぐ曲を選択",
    "playlistBtnSelect": "選択",
    "playlistBtnCancel": "キャンセル",
    "playlistActionRemove": "削除",
    "playlistRemoveSelected": "削除",
    "playlistSelectAll": "すべて選択",
    "playlistDeselectAll": "選択解除",
    "playlistTracksRemovedToast": "{count} 曲をプレイリストから削除しました",
    "playlistConfirmRemoveSelected": "このプレイリストから {count} 曲を削除しますか？",
    "playlistReorderDone": "曲順を更新しました",
    "playlistDragReorderHint": "ドラッグして並べ替え",
    "playlistSelectedCount": "{count} 件選択中",
    "playlistSongsCount": "曲",
    "playlistCountSubtitle": "件のプレイリスト",
    "pickerModalTitle": "曲を選択",
    "pickerSearchPlaceholder": "コレクション内の曲を検索...",
    "pickerTabAll": "すべて",
    "pickerSelectAll": "すべて選択",
    "pickerDeselectAll": "選択解除",
    "pickerSelected": "選択済み",
    "pickerAvailable": "曲が利用可能",
    "pickerBtnAdd": "追加",
    "pickerNoMatch": "フィルターまたは検索に一致する曲はありません。",
    "createPlaylistTitle": "新規プレイリスト",
    "renamePlaylistTitle": "プレイリスト名を変更",
    "createPlaylistDesc": "新しいプレイリストの名前を入力してください：",
    "createPlaylistPlaceholder": "例：お気に入り、リラックス、ドライブ...",
    "btnSave": "保存",
    "addToPlaylistTitle": "プレイリストに追加",
    "addToPlaylistSubtitle": "対象のプレイリストを選択",
    "quickItemAlreadyAdded": "✓ 追加済み",
    "quickItemAdd": "+ 追加",
    "selectionAll": "全選択",
    "selectionMove": "移動",
    "selectionDelete": "削除",
    "selectionCancel": "キャンセル",
    "selectionTitleMove": "選択した曲を移動",
    "selectionTitleDelete": "選択した曲を完全に削除",
    "moveTargetModalTitle": "曲の移動先",
    "moveTargetModalDesc": "選択した曲の移動先を選択してください：",
    "moveOptionPlaylistTitle": "プレイリスト",
    "moveOptionPlaylistDesc": "プレイリストに追加",
    "moveOptionNimiyoTitle": "Nimiyo",
    "moveOptionNimiyoDesc": "音声ファイルを NIMIYO 音楽フォルダーに移動",
    "deleteConfirmModalTitle": "完全削除",
    "deleteConfirmPrompt": "選択した {count} 件の音声ファイルを完全に削除しますか？端末のストレージからファイルが削除されます。",
    "btnYes": "はい",
    "musicPermGranted": "音楽へのアクセスが許可されました。曲を読み込んでいます...",
    "musicQueueCleared": "キューを消去しました",
    "musicDeleteProtectedError": "削除に失敗しました：ファイルはAndroidシステムにより保護されています",
    "playlistNameRequired": "プレイリスト名を入力してください",
    "playlistNameUpdated": "プレイリスト名を更新しました",
    "playlistCreatedToast": "プレイリスト「{name}」を作成しました",
    "playlistDeletedToast": "プレイリストを削除しました",
    "playlistSongRemoved": "曲をプレイリストから削除しました",
    "playlistEmptyToast": "このプレイリストは空です",
    "playlistSongNotFound": "プレイリスト内の曲が見つかりません",
    "playlistPlayingToast": "再生中: {name}",
    "playlistUpdatedToast": "プレイリスト「{name}」を更新しました ({count}曲)",
    "playlistSongAlreadyIn": "「{name}」には既に曲が存在します",
    "playlistSongAddedTo": "「{name}」に追加しました",
    "selectionMinOneSong": "まず1曲以上選択してください",
    "selectionAddedToPlaylistToast": "{count}曲を「{name}」に追加しました",
    "selectionMovedToNimiyoToast": "{count}曲を Nimiyo フォルダーに移動しました",
    "selectionAlreadyInNimiyoToast": "選択したすべての曲は既に Nimiyo フォルダーにあります",
    "selectionPermanentDeletedToast": "{count}件の音声ファイルを完全に削除しました",
    "selectionNoFilesDeletedToast": "削除されたファイルはありません",
    "manageStoragePermRequired": "ストレージのアクセス権限が必要です:\n\nAndroid 11以降では、端末上の音声ファイルを完全に削除・管理するためにNimiyoに「すべてのファイルへのアクセス」権限が必要です。\n\n今すぐ設定を開いてこの権限を有効にしますか？",
    "confirmDeletePlaylist": "このプレイリストを削除してもよろしいですか？",
    "musicAddToPlaylist": "プレイリストに追加",
    "musicMoveToNimiyo": "Nimiyoに移動",
    "musicSelectSong": "曲を選択",
    "musicDurationHour": "時間",
    "musicDurationMin": "分",
    "about-text": "Nimidzは高速で多機能なメディアダウンローダーです。coflynによって愛を込めて作られました。",
    "anim-fast": "速い",
    "anim-normal": "普通",
    "anim-off": "オフ",
    "anim-slow": "ゆっくり",
    "backup-monthly": "毎月",
    "backup-off": "無効",
    "backup-weekly": "毎週",
    "batch-download-all": "すべてダウンロード",
    "batch-modal-title": "バッチダウンロードキュー",
    "batch-photo-all": "すべての写真をダウンロード",
    "batch-photo-first": "最初の写真のみダウンロード",
    "batch-photo-pdf": "単一PDFに結合",
    "btn-analyze": "解析する",
    "btn-analyze-batch": "バッチ解析",
    "btn-cancel": "キャンセル",
    "btn-cancel-download": "ダウンロードをキャンセル",
    "btn-check": "確認",
    "btn-clear": "クリア",
    "btn-clear-all": "すべて削除",
    "btn-close": "閉じる",
    "btn-delete": "削除",
    "btn-done": "完了",
    "btn-download-all-title": "全曲一括ダウンロード",
    "btn-edit": "編集",
    "btn-open-settings": "設定を開く",
    "btn-processing": "処理中...",
    "btn-report": "報告",
    "btn-reset": "リセット",
    "btn-reset-default": "デフォルトに戻す",
    "btn-reset-settings": "リセット",
    "btn-share": "シェア",
    "btn-stop": "停止",
    "btn-update": "更新",
    "btn-view": "表示",
    "concurrent-1": "1（順次）",
    "concurrent-2": "2同時",
    "concurrent-3": "3同時",
    "concurrent-5": "5同時",
    "confirm-reset-settings": "すべての設定をデフォルトに戻しますか？履歴とダウンロードファイルは削除されません。",
    "days-1": "1日",
    "days-30": "30日",
    "days-7": "7日",
    "days-90": "90日",
    "days-off": "オフ",
    "desc-clearcache": "スペースを解放するために一時データ（サムネイルなど）のみを削除します。ダウンロードしたファイルは安全です。続行しますか？",
    "desc-wipedata": "警告：これにより、履歴とNimidzフォルダ内のすべてのダウンロード済みファイルが完全に削除されます。ファイルを保持したい場合は、別のフォルダに移動してください。続行しますか？",
    "dl-stats-total": "総ダウンロード数",
    "doh-cloudflare": "Cloudflare (1.1.1.1)",
    "doh-google": "Google (8.8.8.8)",
    "doh-off": "オフ（システムDNS）",
    "download-all-complete": "全${count}件のアイテムをダウンロードしました！",
    "downloading-progress": "ダウンロード中...",
    "err-yt-playlist-source": "YouTubeプレイリストは'ytmp3.gg'サーバーが必要です。",
    "filename-default": "デフォルト",
    "filename-title": "タイトルのみ",
    "filename-title-date": "タイトル + 日付",
    "filename-title-platform": "タイトル + プラットフォーム",
    "font-default": "Inter",
    "font-display": "ディスプレイ Bold",
    "font-jakarta": "Plus Jakarta Sans",
    "font-mono": "モダン Mono",
    "font-serif": "クラシック Serif",
    "guide-step-1": "TikTok、Instagram、YouTube、Twitter/X、Douyinなど14以上の対応サイトからリンクをコピーします。",
    "guide-step-2": "「貼り付け」（またはその隣のバッチボタン）をタップしてリンクを解析し、画質またはフォーマットを選択します。",
    "guide-step-3": "「ダウンロード」をタップしてデバイスに直接保存します（プログレス表示付き）。",
    "guide-step-4": "「履歴」タブでオフライン再生したり、生体認証ロックで保護することができます。",
    "guide-title": "ユーザーガイド",
    "history-desc": "最近のダウンロード",
    "history-unlimited": "無制限",
    "howtouse-steps": ["サポートされているプラットフォームからリンクをコピーします。","Nimidzに戻り、貼り付けボタンをタップします。","分析が完了するまで待ちます。","ダウンロードをタップして保存します。"],
    "label-about": "Nimidzについて",
    "label-anim-speed": "アニメーション速度",
    "label-animated-bg": "動く背景",
    "label-auto-analyze": "貼り付け時に自動解析",
    "label-auto-clear-input": "入力欄の自動クリア",
    "label-auto-folder": "プラットフォーム別フォルダ",
    "label-auto-retry": "ダウンロード自動再試行",
    "label-auto-update": "自動アップデート確認",
    "label-autoclear-cache": "自動キャッシュ消去",
    "label-autoclear-history": "履歴の自動消去",
    "label-autodownload": "自動ダウンロード",
    "label-autofolder": "プラットフォーム別サブフォルダ",
    "label-autoloop": "ループ再生",
    "label-autopaste": "自動貼り付け",
    "label-autoplay": "自動再生",
    "label-available-downloads": "利用可能なダウンロード",
    "label-batch-photo-mode": "バッチ写真モード",
    "label-bg-shapes": "背景の形状",
    "label-brightness": "明るさ",
    "label-bypass-ssl": "SSLエラーをスキップ",
    "label-cache-cleared": "キャッシュが正常に消去されました！",
    "label-cellular-warning": "モバイルデータ警告",
    "label-check-failed": "確認に失敗しました",
    "label-check-failed-msg": "サーバーに到達できません。接続を確認してください。",
    "label-choose-server": "サーバーを選択:",
    "label-clearcache": "キャッシュを消去",
    "label-compact-mode": "コンパクトモード",
    "label-concurrent-downloads": "同時ダウンロード",
    "label-content": "コンテンツ",
    "label-darkmode": "ダークモード",
    "label-data-wiped": "すべてのデータが消去されました。再起動中...",
    "label-datasaver": "データセーバー",
    "label-developer": "開発者",
    "label-dl-stats": "ダウンロード統計",
    "label-doh": "DNS over HTTPS",
    "label-dont-show": "次回から表示しない",
    "label-dont-show-again": "次回から表示しない",
    "label-download": "ダウンロード",
    "label-download-complete": "ダウンロード完了",
    "label-download-sound": "完了通知音",
    "label-error": "エラー",
    "label-fatal": "致命的",
    "label-fatal-error": "致命的なエラー",
    "label-file-missing": "ファイルが見つかりません",
    "label-filename-template": "ファイル名",
    "label-font": "フォント",
    "label-force-ipv4": "IPv4接続を強制",
    "label-haptic": "振動と触覚フィードバック",
    "label-header-spoofing": "Anti-403 ヘッダーガード",
    "label-hide-progress": "進行状況バーを非表示",
    "label-history-limit": "履歴保存上限",
    "label-howtouse": "使用方法",
    "label-incognito": "シークレットモード",
    "label-items-count": "${count}件",
    "label-keep-awake": "画面常時点灯",
    "label-language": "言語",
    "label-lock-type": "ロックタイプ",
    "label-max-retry": "最大リトライ数",
    "label-modal-error": "モーダルの表示に失敗しました",
    "label-offline": "オフライン",
    "label-opening-wa": "WhatsAppを開いています...",
    "label-option": "オプション",
    "label-overwrite-files": "重複ファイル",
    "label-path-music": "音楽の保存先",
    "label-path-presets": "プリセット / おすすめ",
    "label-path-video": "ビデオの保存先",
    "label-platform": "プラットフォーム",
    "label-platforms": "対応プラットフォーム",
    "label-prefer-server": "優先サーバー",
    "label-privacy-lock": "プライバシーロック",
    "label-reportbug": "バグを報告する",
    "label-request-timeout": "タイムアウト制限",
    "label-reset-settings": "設定をリセット",
    "label-saving": "保存中...",
    "label-server": "サーバー",
    "label-server-options": "サーバーオプション",
    "label-share-file": "ファイルを共有",
    "label-share-link": "リンクを共有",
    "label-share-media": "メディアを共有",
    "label-shareapp": "Nimidzをシェアする",
    "label-storagesize": "合計メディアサイズ",
    "label-subfolder-downloads": "ダウンロード内のサブフォルダ",
    "label-support": "応援する",
    "label-test-latency": "サーバーのレイテンシを確認",
    "label-text-size": "文字サイズ",
    "label-toast-duration": "トースト表示時間",
    "label-up-to-date": "最新バージョンを使用しています。",
    "label-update": "アップデート",
    "label-update-available": "アップデートが利用可能です",
    "label-user-agent": "ユーザーエージェント",
    "label-version": "バージョン",
    "label-wifi-only": "Wi-Fiのみでダウンロード",
    "label-wipedata": "すべてのデータを消去",
    "lang-ar": "アラビア語",
    "lang-en": "英語",
    "lang-hi": "ヒンディー語",
    "lang-id": "インドネシア語",
    "lang-ja": "日本語",
    "lang-ko": "韓国語",
    "lang-ru": "ロシア語",
    "lang-tl": "タガログ語",
    "lang-zh": "中国語 (簡体字)",
    "loader-analyzing": "リンクを解析中...",
    "loader-phrases": ["リンクを解析中...","メディアを取得中...","データを抽出中...","コンテンツを収集中...","ピクセルを探しています...","リクエストを処理中...","もうすぐ完了です..."],
    "lock-type-biometric": "生体認証",
    "lock-type-none": "なし",
    "lock-type-pin": "PINコード",
    "menu-about-desc": "バージョン、ヘルプ、開発者情報",
    "menu-about-title": "情報とヘルプ",
    "menu-advanced-desc": "履歴保持、自動バックアップ、メディアプレーヤー",
    "menu-advanced-title": "高度な設定",
    "menu-animation-desc": "動く背景、形状、明るさ",
    "menu-animation-title": "アニメーション",
    "menu-appearance-desc": "テーマ、アクセントカラー、ハプティクス",
    "menu-appearance-title": "外観と操作感",
    "menu-general-desc": "言語、自動処理、アプリセキュリティ",
    "menu-general-title": "全般",
    "menu-network-desc": "優先サーバー、ユーザーエージェント、データセーバー",
    "menu-network-title": "ネットワークとパフォーマンス",
    "menu-storage-desc": "保存先、ファイル名、キャッシュ消去",
    "menu-storage-title": "ストレージとダウンロード",
    "msg-cellular-warning": "現在モバイルデータ通信を使用しています。ダウンロードを続行しますか？",
    "msg-clear-all-confirm": "すべてのダウンロード履歴を削除してもよろしいですか？",
    "msg-delete-item-confirm": "このアイテムを履歴から削除しますか？",
    "header-history": "ダウンロード履歴",
    "header-settings": "設定",
    "tab-history": "履歴",
    "tab-home": "ホーム",
    "tab-settings": "設定",
    "overwrite-overwrite": "上書き",
    "overwrite-rename": "自動リネーム",
    "overwrite-skip": "スキップ",
    "pdf-btn-gallery": "PDFとして保存 (ギャラリー)",
    "pdf-btn-images": "画像をPDFとして保存",
    "pdf-error-no-images": "有効な画像が処理されませんでした。",
    "pdf-images-detected": "枚の画像が検出されました",
    "pdf-pages": "ページ",
    "pdf-toast-finalizing": "PDF構造をファイナライズ中... お待ちください。",
    "pdf-toast-processing": "${count}/${total} 枚の画像を処理中...",
    "pdf-toast-saved": "PDFが ダウンロード/Nimidz に正常に保存されました",
    "pdf-toast-saving": "デバイスに保存中... 数秒かかる場合があります。",
    "pdf-toast-starting": "PDF書き出しを開始中... (大きなギャラリーは時間がかかる場合があります)",
    "pin-enter-title": "4桁のPINを入力",
    "placeholder-batch-link": "複数のリンクを貼り付け（1行に1つ）...",
    "placeholder-paste-link": "ここにリンクを貼り付け...",
    "player-error-file": "ローカルファイルを読み込めません。",
    "player-error-stream": "メディアをストリーミングできません。",
    "retry-1": "1（リトライなし）",
    "retry-2": "2回試行",
    "retry-3": "3回試行",
    "retry-5": "5回試行",
    "server-1": "サーバー 1 (メイン)",
    "server-2": "サーバー 2 (バックアップ)",
    "server-ask": "毎回確認",
    "settings-desc": "アプリの設定",
    "shape-bubbles": "泡",
    "shape-particles": "粒子",
    "shape-stars": "星",
    "shape-waves": "波",
    "share-err-error": "解析中にエラーが発生しました。",
    "share-err-failed": "リンクの解析に失敗しました。",
    "share-err-no-links": "ダウンロードリンクが見つかりませんでした。",
    "share-err-unsupported": "サポートされていないプラットフォームのリンクです。",
    "share-msg": "どこからでもメディアをダウンロードできる素晴らしいアプリ、Nimidzをチェックしてください！ https://github.com/coflyn/Nimidz",
    "share-panel-sub": "メディアの設定とダウンロード",
    "share-panel-title": "Nimidz クイック保存",
    "text-large": "大",
    "text-medium": "中",
    "text-small": "小",
    "timeout-120": "120秒",
    "timeout-15": "15秒",
    "timeout-30": "30秒",
    "timeout-60": "60秒",
    "toast-anim-speed": "アニメーション速度: ",
    "toast-animatedbg-off": "動く背景が無効になりました",
    "toast-animatedbg-on": "動く背景が有効になりました",
    "toast-anti403-off": "Anti-403ガードが無効になりました",
    "toast-anti403-on": "Anti-403ガードが有効になりました",
    "toast-autoanalyze-off": "自動分析が無効になりました",
    "toast-autoanalyze-on": "自動分析が有効になりました",
    "toast-autoclear-cache-off": "キャッシュ自動クリアが無効になりました",
    "toast-autoclear-cache-on": "キャッシュ自動クリアが有効になりました",
    "toast-autoclear-history-off": "履歴の自動消去がオフ",
    "toast-autoclear-history-on": "履歴の自動消去がオン",
    "toast-autoclearinput-off": "入力の自動クリアが無効になりました",
    "toast-autoclearinput-on": "入力の自動クリアが有効になりました",
    "toast-autodownload-off": "自動ダウンロードがオフ",
    "toast-autodownload-on": "自動ダウンロードがオン",
    "toast-autofolder-off": "プラットフォーム別サブフォルダが無効になりました",
    "toast-autofolder-on": "プラットフォーム別サブフォルダが有効になりました",
    "toast-autoloop-off": "ループ再生が無効になりました",
    "toast-autoloop-on": "ループ再生が有効になりました",
    "toast-autopaste-off": "自動貼り付けがオフ",
    "toast-autopaste-on": "自動貼り付けがオン",
    "toast-autoplay-off": "自動再生が無効になりました",
    "toast-autoplay-on": "自動再生が有効になりました",
    "toast-autoretry-off": "自動再試行が無効になりました",
    "toast-autoretry-on": "自動再試行が有効になりました",
    "toast-autoupdate-off": "自動更新確認が無効になりました",
    "toast-autoupdate-on": "自動更新確認が有効になりました",
    "toast-bypassssl-off": "SSLバイパスが無効になりました",
    "toast-bypassssl-on": "SSLバイパスが有効になりました",
    "toast-cache-error": "キャッシュの消去に失敗しました。",
    "toast-cellularwarning-off": "モバイルデータ警告が無効になりました",
    "toast-cellularwarning-on": "モバイルデータ警告が有効になりました",
    "toast-clipboard-empty": "クリップボードが空です",
    "toast-compact-off": "コンパクトモード無効",
    "toast-compact-on": "コンパクトモード有効",
    "toast-connection-lost": "接続が切断されました。インターネットを確認してください。",
    "toast-copy-failed": "コピーに失敗しました",
    "toast-copy-success": "クリップボードにコピーしました",
    "toast-darkmode-off": "ライトモードが有効になりました",
    "toast-darkmode-on": "ダークモードが有効になりました",
    "toast-datasaver-off": "データセーバーがオフ",
    "toast-datasaver-on": "データセーバーがオン",
    "toast-doh": "DNS over HTTPS: ",
    "toast-download-cancelled": "ダウンロードをキャンセルしました",
    "toast-download-complete": "ダウンロード完了",
    "toast-download-failed": "ダウンロード失敗",
    "toast-dur-1": "1秒",
    "toast-dur-2": "2秒",
    "toast-dur-3": "3秒",
    "toast-dur-5": "5秒",
    "toast-failed": "失敗:",
    "toast-forceipv4-off": "IPv4強制が無効になりました",
    "toast-forceipv4-on": "IPv4強制が有効になりました",
    "toast-haptic-off": "触覚フィードバックが無効になりました",
    "toast-haptic-on": "触覚フィードバックが有効になりました",
    "toast-hide-progress-off": "ダウンロードバーを表示しました",
    "toast-hide-progress-on": "ダウンロードバーを非表示にしました",
    "toast-incognito-off": "シークレットモードがオフ",
    "toast-incognito-on": "シークレットモードがオン",
    "toast-keepawake-off": "画面常時点灯が無効になりました",
    "toast-keepawake-on": "画面常時点灯が有効になりました",
    "toast-memory-error": "変換中にメモリのエラーが発生しました。",
    "toast-no-batch-urls": "有効なURLが見つかりません",
    "toast-no-link": "クリップボードにリンクが見つかりません",
    "toast-overwrite": "重複ファイル: ",
    "toast-pasted-share": "共有からリンクを貼り付けました",
    "toast-path-updated": "保存先パスが更新されました",
    "toast-pdf-downloaded": "PDFがダウンロードされました",
    "toast-press-back-exit": "もう一度戻るを押して終了",
    "toast-privacy-off": "プライバシーロックが無効になりました",
    "toast-privacy-on": "プライバシーロックが有効になりました",
    "toast-reset-settings": "設定をデフォルトにリセットしました",
    "toast-saved": "保存完了:",
    "toast-sound-off": "完了音が無効になりました",
    "toast-sound-on": "完了音が有効になりました",
    "toast-storage-error": "ストレージエラー：空き容量を確認してください。",
    "toast-text-size": "文字サイズ: ",
    "toast-wifi-needed": "Wi-Fi接続が必要です",
    "toast-wifi-off": "Wi-Fiのみがオフ",
    "toast-wifi-on": "Wi-Fiのみがオン",
    "ua-chrome": "モバイル Chrome",
    "ua-default": "デフォルト",
    "ua-desktop": "デスクトップ Chrome",
    "ua-safari": "iOS Safari"
  }
};

// System Language Detection (Default to system device language)
function detectSystemLanguage() {
  const navLang = ((navigator.languages && navigator.languages[0]) || navigator.language || navigator.userLanguage || "en").toLowerCase();
  if (navLang.startsWith("id") || navLang.startsWith("in")) {
    return "id";
  } else if (navLang.startsWith("zh")) {
    return "zh";
  } else if (navLang.startsWith("ja")) {
    return "ja";
  } else {
    return "en";
  }
}

// Default Settings
let settings = {
  language: detectSystemLanguage(),
  uiTheme: 'neobrutalism',   // 'neobrutalism' | 'softui'
  accentColor: 'yellow',     // 'yellow' | 'blue' | 'red' | 'gray'
  autoPaste: false,
  autoAnalyze: false,
  autoClearInput: false,
  incognito: false,
  darkMode: false,
  completionSound: true,
  haptic: true,
  appFont: 'misans',
  filenameTemplate: 'title', // 'title' | 'title-platform' | 'title-date'
  overwriteMode: 'rename',   // 'rename' | 'overwrite' | 'skip'
  concurrentDl: 1,           // 1 | 2 | 3 | 5
  batchPhotoMode: 'all',     // 'all' | 'first'
  autoDownload: false,
  preferredServer: 'auto',
  timeout: 30,
  autoRetry: true,
  maxRetry: 3,
  wifiOnly: false,
  doh: 'off',
  headerSpoofing: true,
  historyLimit: 'unlimited', // 'unlimited' | '50' | '100' | '200'
  autoClearDays: 'off',      // 'off' | '1' | '7' | '30' | '90'
  autoClearCacheDays: 'off', // 'off' | '1' | '7' | '30'
  autoPlay: true,
  autoLoop: true,
  keepAwake: false,
  videoPath: 'Downloads',
  musicPath: 'Downloads'
};

// Local variables
let currentLanguage = detectSystemLanguage();
let localHistory = [];
let activeAnalysisResult = null;
let currentPlatform = '';
let activeScraperMethod = '';
let downloadCancelled = false;
let downloadProgressInterval = null;
let lastAutoPastedUrl = '';

// Platform details & Regex mapping
const platformMapping = {
  youtube: { name: "YouTube", domains: /(youtube\.com|youtu\.be)/i, color: "#FF0000" },
  tiktok: { name: "TikTok", domains: /(tiktok\.com)/i, color: "#000000" },
  instagram: { name: "Instagram", domains: /(instagram\.com)/i, color: "#E4405F" },
  twitter: { name: "Twitter / X", domains: /(twitter\.com|x\.com)/i, color: "#1DA1F2" },
  spotify: { name: "Spotify", domains: /(spotify\.com)/i, color: "#1ED760" },
  applemusic: { name: "Apple Music", domains: /(music\.apple\.com)/i, color: "#FA576E" },
  facebook: { name: "Facebook", domains: /(facebook\.com|fb\.watch|fb\.com)/i, color: "#1877F2" },
  threads: { name: "Threads", domains: /(threads\.(?:net|com))/i, color: "#000000" },
  pinterest: { name: "Pinterest", domains: /(pinterest\.com|pin\.it)/i, color: "#E60023" },
  bilibili: { name: "Bilibili", domains: /(bilibili\.com|b23\.tv)/i, color: "#00AEEC" },
  douyin: { name: "Douyin", domains: /(douyin\.com)/i, color: "#FF0050" },
  bandcamp: { name: "Bandcamp", domains: /(bandcamp\.com)/i, color: "#1DA1F2" },
  pixiv: { name: "Pixiv", domains: /(pixiv\.net|pixiv\.me|pximg\.net)/i, color: "#0096FA" },
  rednote: { name: "RedNote (小红书)", domains: /(rednote\.com|xiaohongshu\.com|xhslink\.com|xhslink\.cn)/i, color: "#FF2442" },
  shopee: { name: "Shopee", domains: /(shopee\.[a-z.]+|shp\.ee)/i, color: "#EE4D2D" }
};

// Predefined scraper fallback order per platform (100% matching share.js & scrapr)
const fallbackChains = {
  tiktok: ['tiktokio', 'snaptik', 'ssstik', 'direct'],
  instagram: ['snapsave', 'indown', 'direct'],
  facebook: ['snapsave', 'direct'],
  spotify: ['spotidown', 'soundloaders', 'direct'],
  twitter: ['direct', 'tweeload', 'tvd'],
  youtube: ['ytmp3', 'direct'],
  applemusic: ['aplmate', 'direct'],
  pinterest: ['pindown', 'direct'],
  threads: ['threadster', 'direct'],
  bilibili: ['direct'],
  douyin: ['direct'],
  bandcamp: ['bandcampdownloader', 'direct'],
  pixiv: ['direct'],
  rednote: ['direct'],
  shopee: ['svxtract', 'direct']
};

// App Version Constants & GitHub Auto-Update Engine
const APP_VERSION_NAME = "2.2.1";
const APP_VERSION_CODE = 6;
const UPDATE_MANIFEST_URL = "https://raw.githubusercontent.com/nimidz/Nimiyo-Downloader/main/version.json";
let latestUpdateInfo = null;

// Initialize Application
document.addEventListener("DOMContentLoaded", () => {
  loadSettings();
  loadHistory();
  initUI();
  setupEventListeners();

  // Smooth Coldstart Render Veil Dismissal
  // Allows micro-delay for all theme styles, dark mode, fonts, translations, and tab indicators to render smoothly
  requestAnimationFrame(() => {
    const activeTabBtn = document.querySelector(".tab-btn.active") || document.querySelector(".tab-btn");
    if (activeTabBtn && typeof updateTabIndicator === "function") {
      updateTabIndicator(activeTabBtn);
    }
    setTimeout(() => {
      const veil = document.getElementById("appLaunchVeil");
      if (veil) {
        veil.classList.add("fade-out");
        setTimeout(() => {
          if (veil && veil.parentNode) veil.parentNode.removeChild(veil);
        }, 400);
      }
    }, 240); // 240ms rendering delay ensures butter-smooth 60fps start without flashing old UI
  });

  setTimeout(() => {
    checkClipboardOnResume();
  }, 450);
  checkInstallPermissionStatus();
  checkRulesOnboarding();

  // Ensure notification permission is requested on startup
  const MediaSaver = window.Capacitor?.Plugins?.MediaSaver;
  if (MediaSaver && typeof MediaSaver.requestNotificationPermission === "function") {
    MediaSaver.requestNotificationPermission().catch(() => { });
  }

  setTimeout(() => {
    checkForAppUpdates(false);
  }, 2500);
});

// Load Settings from LocalStorage
function loadSettings() {
  const systemLang = detectSystemLanguage();
  const savedSettings = localStorage.getItem("nimiyo_settings");
  if (savedSettings) {
    try {
      const parsed = JSON.parse(savedSettings);
      settings = { ...settings, ...parsed };
      if (!parsed.language) {
        settings.language = systemLang;
      }
    } catch (e) {
      console.error("Failed to parse settings", e);
      settings.language = systemLang;
    }
  } else {
    settings.language = systemLang;
  }
  if (!settings.timeout || isNaN(settings.timeout)) {
    settings.timeout = 30;
  }
  if (!settings.preferredServer) {
    settings.preferredServer = 'auto';
  }
  currentLanguage = settings.language || systemLang;
  applyDarkMode(settings.darkMode);
  applyUiTheme(settings.uiTheme);
  applyAccentColor(settings.accentColor);
  syncSettingsToNative();
}

// Sync settings to native Android SharedPreferences so QuickSave and background services have them
function syncSettingsToNative() {
  try {
    if (window.Capacitor && window.Capacitor.Plugins && window.Capacitor.Plugins.MediaSaver && window.Capacitor.Plugins.MediaSaver.saveAppSettings) {
      window.Capacitor.Plugins.MediaSaver.saveAppSettings({ settings: JSON.stringify(settings) });
    }
  } catch (e) {
    console.warn("Failed to sync settings to native", e);
  }
}

// Save Settings to LocalStorage & Native
function saveSettings() {
  localStorage.setItem("nimiyo_settings", JSON.stringify(settings));
  localStorage.setItem("nimiyo_accent_color", settings.accentColor || "yellow");
  currentLanguage = settings.language || 'en';
  applyDarkMode(settings.darkMode);
  applyUiTheme(settings.uiTheme);
  applyAccentColor(settings.accentColor);
  applyTranslations();
  syncSettingsToNative();
  showToast(getTranslation("toastSettingsSaved"), "success");
}

// Apply UI Theme (Neo-Brutalism vs Soft UI)
function applyUiTheme(theme) {
  const currentTheme = theme || settings.uiTheme || 'neobrutalism';
  document.body.classList.remove('theme-neobrutalism', 'theme-softui');
  document.body.classList.add(`theme-${currentTheme}`);
  document.documentElement.classList.remove('theme-neobrutalism', 'theme-softui');
  document.documentElement.classList.add(`theme-${currentTheme}`);
  const activeBtn = document.querySelector(".tab-btn.active");
  if (activeBtn && typeof updateTabIndicator === "function") {
    requestAnimationFrame(() => updateTabIndicator(activeBtn));
  }
}

// Apply Accent Color
function applyAccentColor(accent) {
  const currentAccent = accent || settings.accentColor || 'yellow';
  document.documentElement.setAttribute('data-accent', currentAccent);
  document.body.setAttribute('data-accent', currentAccent);
  const preview = document.getElementById("accentColorPreview");
  if (preview) {
    const colorMap = {
      yellow: '#FFDE59',
      blue: '#2563EB',
      red: '#EF4444',
      gray: '#64748B'
    };
    preview.style.backgroundColor = colorMap[currentAccent] || colorMap.yellow;
  }
  // Refresh music player glow and tab indicator if active
  if (window.musicPlayer && typeof window.musicPlayer.resetDynamicColor === "function") {
    window.musicPlayer.resetDynamicColor();
  }
  const activeBtn = document.querySelector(".tab-btn.active");
  if (activeBtn && typeof updateTabIndicator === "function") {
    requestAnimationFrame(() => updateTabIndicator(activeBtn));
  }
}

// Load History from LocalStorage & Sync from Native Shared Preferences
async function loadHistory() {
  const savedHistory = localStorage.getItem("nimiyo_history");
  if (savedHistory) {
    try {
      localHistory = JSON.parse(savedHistory);
    } catch (e) {
      console.error("Failed to parse history", e);
      localHistory = [];
    }
  }

  // Sync any items downloaded from QuickSave / Share Target
  try {
    const MediaSaver = window.Capacitor?.Plugins?.MediaSaver;
    if (MediaSaver && typeof MediaSaver.getPendingSharedHistory === "function") {
      const res = await MediaSaver.getPendingSharedHistory();
      if (res && Array.isArray(res.items) && res.items.length > 0) {
        let hasNew = false;
        for (let item of res.items) {
          if (typeof item === "string") {
            try { item = JSON.parse(item); } catch (_) { }
          }
          if (item && item.filename && !localHistory.some(h => h.filename === item.filename)) {
            localHistory.unshift(item);
            hasNew = true;
          }
        }
        if (hasNew) {
          if (localHistory.length > 200) localHistory = localHistory.slice(0, 200);
          saveHistory();
          renderHistory();
  // Re-render Music Player dynamic views & UI if active/instantiated
  if (window.nimiyoMusicPlayer && typeof window.nimiyoMusicPlayer.updateLanguage === 'function') {
    window.nimiyoMusicPlayer.updateLanguage();
  }
        }
      }
    }
  } catch (e) {
    console.warn("Failed to sync shared history:", e);
  }
}

// Save History to LocalStorage with QuotaExceededError protection and auto-trimming
function saveHistory() {
  try {
    localStorage.setItem("nimiyo_history", JSON.stringify(localHistory));
  } catch (e) {
    console.warn("[STORAGE] localStorage write failed or quota exceeded, attempting auto-trim:", e);
    try {
      // Step 1: Strip large base64/thumbnail images from older history entries
      if (Array.isArray(localHistory) && localHistory.length > 0) {
        localHistory = localHistory.map((item, idx) => {
          if (idx < localHistory.length - 10 && item && item.thumbnail && item.thumbnail.length > 400) {
            const trimmed = { ...item };
            delete trimmed.thumbnail;
            return trimmed;
          }
          return item;
        });
        localStorage.setItem("nimiyo_history", JSON.stringify(localHistory));
        return;
      }
    } catch (_) {}

    try {
      // Step 2: If still overflowing, retain only the newest 50 entries
      if (Array.isArray(localHistory) && localHistory.length > 50) {
        localHistory = localHistory.slice(-50);
        localStorage.setItem("nimiyo_history", JSON.stringify(localHistory));
      }
    } catch (finalErr) {
      console.error("[STORAGE] Critical localStorage save failure:", finalErr);
    }
  }
}

// Apply Dark Mode Class to Body & Document
function applyDarkMode(enabled) {
  if (enabled) {
    document.body.classList.add("dark-mode");
    document.body.classList.remove("light-mode");
    document.documentElement.classList.add("dark-mode");
    document.documentElement.classList.remove("light-mode");
  } else {
    document.body.classList.remove("dark-mode");
    document.body.classList.add("light-mode");
    document.documentElement.classList.remove("dark-mode");
    document.documentElement.classList.add("light-mode");
  }
}

// Apply Translation strings to UI elements
function applyTranslations() {
  currentLanguage = settings.language || 'en';

  // Home tab logo is always NIMIYO
  const homeLogoEl = document.querySelector("#tab-home .logo");
  if (homeLogoEl) homeLogoEl.innerText = getTranslation("appTitle");

  const urlInputEl = document.getElementById("urlInput");
  if (urlInputEl) urlInputEl.placeholder = getTranslation("inputPlaceholder");

  const analyzeBtnText = document.querySelector("#analyzeBtn .btn-text");
  const analyzeLoader = document.querySelector("#analyzeBtn .loader");
  if (analyzeBtnText && analyzeLoader && !analyzeLoader.classList.contains("hidden")) {
    analyzeBtnText.innerText = getTranslation("btnAnalyzing");
  } else if (analyzeBtnText) {
    analyzeBtnText.innerText = getTranslation("btnAnalyze");
  }

  const cancelAnalyzeBtnText = document.querySelector("#cancelAnalyzeBtn .btn-text");
  if (cancelAnalyzeBtnText) {
    cancelAnalyzeBtnText.innerText = getTranslation("btnCancel");
  }

  const platH2 = document.querySelector(".platforms-section h2");
  if (platH2) platH2.innerText = getTranslation("platformsTitle");

  const clearHistBtn = document.getElementById("clearHistoryBtn");
  if (clearHistBtn) clearHistBtn.innerText = getTranslation("btnClearHistory");

  // Automatically translate all elements with data-i18n attribute
  document.querySelectorAll("[data-i18n]").forEach(el => {
    const key = el.getAttribute("data-i18n");
    if (key) {
      const val = getTranslation(key);
      if (val && val.includes("<") && val.includes(">")) {
        el.innerHTML = val;
      } else if (val) {
        el.innerText = val;
      }
    }
  });

  // Automatically translate placeholders with data-i18n-placeholder attribute
  document.querySelectorAll("[data-i18n-placeholder]").forEach(el => {
    const key = el.getAttribute("data-i18n-placeholder");
    if (key) el.placeholder = getTranslation(key);
  });

  // Automatically translate titles / tooltips with data-i18n-title attribute
  document.querySelectorAll("[data-i18n-title]").forEach(el => {
    const key = el.getAttribute("data-i18n-title");
    if (key) {
      const val = getTranslation(key);
      el.title = val;
      el.setAttribute("aria-label", val);
    }
  });

  // Balloon sheet strings
  const balloonSheetTitleEl = document.querySelector(".balloon-sheet-title");
  if (balloonSheetTitleEl) balloonSheetTitleEl.innerText = getTranslation("balloonSheetTitle");
  const balloonCancelAllBtnEl = document.getElementById("balloonCancelAllBtn");
  if (balloonCancelAllBtnEl) balloonCancelAllBtnEl.innerText = getTranslation("balloonCancelAll");

  // Copy buttons
  const copyTitleBtnSpan = document.querySelector("#copyTitleBtn span");
  if (copyTitleBtnSpan) copyTitleBtnSpan.innerText = getTranslation("btnCopy");
  const copyDescBtnSpan = document.querySelector("#copyDescBtn span");
  if (copyDescBtnSpan) copyDescBtnSpan.innerText = getTranslation("btnCopy");

  // Description label
  const descLabelEl = document.querySelector("#descriptionContainer .description-label");
  if (descLabelEl) descLabelEl.innerText = getTranslation("descLabel");

  // Toggle see more/less buttons if present
  const toggleTitleBtnEl = document.getElementById("toggleTitleBtn");
  if (toggleTitleBtnEl) {
    const titleText = document.getElementById("resultTitle");
    if (titleText && titleText.classList.contains("collapsed")) {
      toggleTitleBtnEl.innerText = getTranslation("toggleSeeMore");
    } else {
      toggleTitleBtnEl.innerText = getTranslation("toggleSeeLess");
    }
  }

  const toggleDescBtnEl = document.getElementById("toggleDescBtn");
  if (toggleDescBtnEl) {
    const descText = document.getElementById("resultDescription");
    if (descText && descText.classList.contains("collapsed")) {
      toggleDescBtnEl.innerText = getTranslation("toggleSeeMore");
    } else {
      toggleDescBtnEl.innerText = getTranslation("toggleSeeLess");
    }
  }

  // Modal titles
  const downloadOverlayH2 = document.querySelector("#downloadOverlay h2");
  if (downloadOverlayH2) downloadOverlayH2.innerText = getTranslation("downloadModalTitle");
  const cancelDownloadBtn = document.getElementById("cancelDownloadBtn");
  if (cancelDownloadBtn) cancelDownloadBtn.innerText = getTranslation("btnCancel");

  const mediaPlayerTitleEl = document.getElementById("mediaPlayerTitle");
  if (mediaPlayerTitleEl && (mediaPlayerTitleEl.innerText === "Media Player" || mediaPlayerTitleEl.innerText === "Pemutar Media")) {
    mediaPlayerTitleEl.innerText = getTranslation("mediaPlayerTitle");
  }

  // Update result headers if visible
  const resultTitleHeader = document.querySelector(".download-container h3");
  if (resultTitleHeader) {
    resultTitleHeader.innerText = getTranslation("downloadsTitle");
  }

  // Re-render download options if activeAnalysisResult is present
  if (activeAnalysisResult && currentPlatform) {
    renderResult(activeAnalysisResult, currentPlatform, activeScraperMethod);
  }

  // Re-render balloon queue list with new language strings
  updateBalloonQueueUI();

  // Re-render history list with new language strings
  renderHistory();
}

// Get Translate key with optional string interpolation
function getTranslation(key, params = {}) {
  let text = translations[currentLanguage]?.[key] || translations['en']?.[key] || key;
  if (params && typeof params === "object") {
    Object.keys(params).forEach(p => {
      text = text.replace(new RegExp(`\\{${p}\\}`, "g"), params[p]);
    });
  }
  return text;
}
window.getTranslation = getTranslation;

// Show Toast Alert (with Anti-Spam and Smooth Queue)
let activeToastTimer = null;
let lastToastMsg = "";
let lastToastTime = 0;

function showToast(message, type = "info") {
  if (!message) return;
  const now = Date.now();
  if (message === lastToastMsg && (now - lastToastTime) < 1500) {
    return;
  }
  lastToastMsg = message;
  lastToastTime = now;

  const container = document.getElementById("toastContainer");
  if (!container) return;

  const existingToasts = container.querySelectorAll(".toast");
  existingToasts.forEach(t => {
    t.style.opacity = "0";
    t.style.transform = "translateY(-8px)";
    setTimeout(() => t.remove(), 150);
  });

  const toast = document.createElement("div");
  toast.className = `toast toast-${type}`;
  toast.innerHTML = `<span>${message}</span>`;

  toast.addEventListener("click", () => {
    toast.style.opacity = "0";
    setTimeout(() => toast.remove(), 150);
  });

  container.appendChild(toast);

  clearTimeout(activeToastTimer);
  activeToastTimer = setTimeout(() => {
    toast.style.opacity = "0";
    setTimeout(() => toast.remove(), 200);
  }, 3200);
}

// Play synthesizer completion sound via Web Audio API
function playCompletionSound() {
  if (!settings.completionSound) return;
  try {
    const AudioContext = window.AudioContext || window.webkitAudioContext;
    if (!AudioContext) return;
    const ctx = new AudioContext();

    const playTone = (freq, startTime, duration) => {
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.connect(gain);
      gain.connect(ctx.destination);

      osc.type = "sine";
      osc.frequency.setValueAtTime(freq, startTime);

      gain.gain.setValueAtTime(0, startTime);
      gain.gain.linearRampToValueAtTime(0.15, startTime + 0.05);
      gain.gain.exponentialRampToValueAtTime(0.0001, startTime + duration);

      osc.start(startTime);
      osc.stop(startTime + duration);
    };

    const now = ctx.currentTime;
    playTone(523.25, now, 0.4); // C5
    playTone(659.25, now + 0.1, 0.6); // E5
  } catch (e) {
    console.error("Failed to play sound chime", e);
  }
}

// Trigger Haptic Feedback
function triggerHaptic() {
  if (settings.haptic && Haptics) {
    Haptics.impact({ style: 'medium' }).catch(() => { });
  }
}

// Apply App Font
function applyAppFont(fontKey) {
  const fontMap = {
    misans: "'MiSans', sans-serif",
    inter: "'Inter', sans-serif",
    outfit: "'Outfit', sans-serif",
    mono: "'Space Mono', monospace"
  };
  const font = fontMap[fontKey] || fontMap.misans;
  document.documentElement.style.setProperty('--font-main', font);
}

// Update Total Media Storage Size Display
function updateStorageSizeDisplay() {
  const el = document.getElementById("storageSizeDisplay");
  if (!el) return;
  // Compute approximate storage from history
  const count = localHistory.length;
  if (count === 0) {
    el.innerText = "0 MB";
    return;
  }
  // Estimated size: 12.5 MB average per history item
  const estimatedMB = (count * 12.5).toFixed(1);
  el.innerText = `${estimatedMB} MB (${count} items)`;
}

// Screen WakeLock Engine
let activeWakeLock = null;
async function applyKeepAwake(enabled) {
  const isEnabled = Boolean(enabled);
  try {
    const MediaSaver = window.Capacitor?.Plugins?.MediaSaver;
    if (MediaSaver && typeof MediaSaver.setKeepAwake === "function") {
      await MediaSaver.setKeepAwake({ enabled: isEnabled });
    }
  } catch (e) {
    console.warn("[WAKELOCK] Native setKeepAwake error:", e);
  }

  try {
    if ('wakeLock' in navigator) {
      if (isEnabled) {
        if (!activeWakeLock) {
          activeWakeLock = await navigator.wakeLock.request('screen');
          activeWakeLock.addEventListener('release', () => {
            activeWakeLock = null;
          });
        }
      } else {
        if (activeWakeLock) {
          await activeWakeLock.release();
          activeWakeLock = null;
        }
      }
    }
  } catch (e) {
    console.warn("[WAKELOCK] navigator.wakeLock error:", e);
  }
}

// Clear App Cache (real cleanup of Capacitor Cache sandbox directory)
async function clearAppCache() {
  triggerHaptic();
  try {
    if (Filesystem && Directory?.Cache) {
      await Filesystem.rmdir({
        path: "",
        directory: Directory.Cache,
        recursive: true
      }).catch(() => {});
    }
  } catch (e) {
    console.warn("[CACHE] Filesystem.rmdir error:", e);
  }
  localStorage.setItem("nimiyo_last_cache_cleanup", Date.now().toString());
  showToast(getTranslation("toastCacheCleared"), "success");
  updateStorageSizeDisplay();
}

// Wipe All Data & Reset
function wipeAllData() {
  triggerHaptic();
  if (confirm(getTranslation("confirmWipeData"))) {
    localHistory = [];
    localStorage.removeItem("nimiyo_history");
    localStorage.removeItem("nimiyo_settings");
    localStorage.removeItem("nimiyo_last_cache_cleanup");
    settings = {
      language: detectSystemLanguage(),
      accentColor: 'yellow',
      autoPaste: false,
      autoAnalyze: false,
      autoClearInput: false,
      incognito: false,
      darkMode: false,
      completionSound: true,
      haptic: true,
      appFont: 'misans',
      filenameTemplate: 'title',
      overwriteMode: 'rename',
      concurrentDl: 1,
      batchPhotoMode: 'all',
      autoDownload: false,
      autoRetry: true,
      maxRetry: 3,
      wifiOnly: false,
      doh: 'off',
      headerSpoofing: true,
      historyLimit: 'unlimited',
      autoClearDays: 'off',
      autoClearCacheDays: 'off',
      autoPlay: true,
      autoLoop: true,
      keepAwake: false,
      videoPath: 'Downloads',
      musicPath: 'Downloads'
    };
    saveSettings();
    renderHistory();
    initUI();
    showToast(getTranslation("toastWipeCompleted"), "success");
  }
}

// Reset Settings to Default
function resetSettingsToDefault() {
  triggerHaptic();
  if (confirm(getTranslation("confirmResetSettings"))) {
    settings = {
      language: detectSystemLanguage(),
      accentColor: 'yellow',
      autoPaste: false,
      autoAnalyze: false,
      autoClearInput: false,
      incognito: false,
      darkMode: false,
      completionSound: true,
      haptic: true,
      appFont: 'misans',
      filenameTemplate: 'title',
      overwriteMode: 'rename',
      concurrentDl: 1,
      batchPhotoMode: 'all',
      autoDownload: false,
      autoRetry: true,
      maxRetry: 3,
      wifiOnly: false,
      doh: 'off',
      headerSpoofing: true,
      historyLimit: 'unlimited',
      autoClearDays: 'off',
      autoClearCacheDays: 'off',
      autoPlay: true,
      autoLoop: true,
      keepAwake: false,
      videoPath: 'Downloads',
      musicPath: 'Downloads'
    };
    saveSettings();
    initUI();
    showToast(getTranslation("toastSettingsSaved"), "success");
  }
}

// Auto-Clear History / Cache based on settings
function runAutoCleanups() {
  if (settings.autoClearDays && settings.autoClearDays !== 'off') {
    const days = parseInt(settings.autoClearDays, 10);
    const cutoff = Date.now() - (days * 24 * 60 * 60 * 1000);
    localHistory = localHistory.filter(item => item.timestamp && item.timestamp > cutoff);
    saveHistory();
  }
  if (settings.autoClearCacheDays && settings.autoClearCacheDays !== 'off') {
    const days = parseInt(settings.autoClearCacheDays, 10);
    const lastClean = parseInt(localStorage.getItem("nimiyo_last_cache_cleanup") || "0", 10);
    if (Date.now() - lastClean > (days * 24 * 60 * 60 * 1000)) {
      clearAppCache();
    }
  }
}

// Init UI elements values
function initUI() {
  // Update inputs in Settings modal
  const setVal = (id, val) => {
    const el = document.getElementById(id);
    if (el) el.value = val;
  };
  const setChecked = (id, val) => {
    const el = document.getElementById(id);
    if (el) el.checked = Boolean(val);
  };

  setVal("settingLanguage", settings.language || 'en');
  setVal("settingUiTheme", settings.uiTheme || 'neobrutalism');
  setVal("settingAccentColor", settings.accentColor || 'yellow');
  setChecked("settingAutoPaste", settings.autoPaste);
  setChecked("settingAutoAnalyze", settings.autoAnalyze);
  setChecked("settingAutoClearInput", settings.autoClearInput);
  setChecked("settingIncognito", settings.incognito);
  setChecked("settingDarkMode", settings.darkMode);
  setChecked("settingCompletionSound", settings.completionSound);
  setChecked("settingHaptic", settings.haptic);
  setVal("settingAppFont", settings.appFont || 'misans');
  setVal("settingFilenameTemplate", settings.filenameTemplate || 'title');
  setVal("settingOverwriteMode", settings.overwriteMode || 'rename');
  setVal("settingConcurrentDl", settings.concurrentDl || 1);
  setVal("settingBatchPhotoMode", settings.batchPhotoMode || 'all');
  setChecked("settingAutoDownload", settings.autoDownload);
  setChecked("settingAutoRetry", settings.autoRetry);
  setVal("settingMaxRetry", settings.maxRetry || 3);
  setChecked("settingWifiOnly", settings.wifiOnly);
  setVal("settingDoh", settings.doh || 'off');
  setChecked("settingHeaderSpoofing", settings.headerSpoofing !== false);
  setVal("settingHistoryLimit", settings.historyLimit || 'unlimited');
  setVal("settingAutoClearDays", settings.autoClearDays || 'off');
  setVal("settingAutoClearCacheDays", settings.autoClearCacheDays || 'off');
  setChecked("settingAutoPlay", settings.autoPlay !== false);
  setChecked("settingAutoLoop", settings.autoLoop !== false);
  setChecked("settingKeepAwake", settings.keepAwake);

  applyAppFont(settings.appFont);
  applyAccentColor(settings.accentColor);
  applyKeepAwake(settings.keepAwake);
  updateStorageSizeDisplay();
  runAutoCleanups();

  // Apply translations
  applyTranslations();

  // Populate Supported Platforms Grid
  const grid = document.getElementById("platformsGrid");
  grid.innerHTML = "";

  if (window.scrapr) {
    Object.keys(platformMapping).forEach(key => {
      if (window.scrapr[key]) {
        const plat = platformMapping[key];
        const card = document.createElement("div");
        card.className = "platform-card";
        card.style.borderColor = plat.color;

        card.innerHTML = `
          <div class="platform-icon" style="color: ${plat.color}">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor">
              ${getSVGPath(key)}
            </svg>
          </div>
          <span class="platform-name">${plat.name}</span>
        `;
        grid.appendChild(card);
      }
    });
  }
}

// Set up UI Event listeners
function setupEventListeners() {
  const urlInput = document.getElementById("urlInput");
  const clearInputBtn = document.getElementById("clearInputBtn");
  const pasteInputBtn = document.getElementById("pasteInputBtn");
  const analyzeBtn = document.getElementById("analyzeBtn");

  // Show clear button when text is in URL input & Debounced Auto-Analyze for Gboard/autocomplete
  let inputAutoAnalyzeTimer = null;
  urlInput.addEventListener("input", () => {
    const rawVal = (urlInput.value || "").trim();
    if (rawVal.length > 0) {
      clearInputBtn.classList.remove("hidden");
    } else {
      clearInputBtn.classList.add("hidden");
    }

    if (settings.autoAnalyze && rawVal.length > 0) {
      const urlMatch = rawVal.match(/https?:\/\/[^\s'"]+/i);
      const cleanVal = urlMatch ? urlMatch[0] : rawVal;
      if (isValidMediaUrl(cleanVal)) {
        clearTimeout(inputAutoAnalyzeTimer);
        inputAutoAnalyzeTimer = setTimeout(() => {
          if (urlInput.value.trim().length > 0 && lastAutoPastedUrl !== cleanVal) {
            lastAutoPastedUrl = cleanVal;
            urlInput.value = cleanVal;
            triggerHaptic();
            analyzeLink(cleanVal);
          }
        }, 350);
      }
    }
  });

  // Clear Input button
  clearInputBtn.addEventListener("click", () => {
    urlInput.value = "";
    lastAutoPastedUrl = "";
    clearInputBtn.classList.add("hidden");
    urlInput.focus();
  });

  // Paste Input button
  pasteInputBtn.addEventListener("click", async () => {
    triggerHaptic();
    try {
      const rawText = await getClipboardText();
      const trimmed = (rawText || "").trim();
      if (trimmed) {
        const urlMatch = trimmed.match(/https?:\/\/[^\s'"]+/i);
        const finalUrl = urlMatch ? urlMatch[0] : trimmed;

        urlInput.value = finalUrl;
        lastAutoPastedUrl = finalUrl;
        clearInputBtn.classList.remove("hidden");
        showToast(getTranslation("toastDetecting"), "info");

        if (settings.autoAnalyze && isValidMediaUrl(finalUrl)) {
          setTimeout(() => {
            analyzeLink(finalUrl);
          }, 150);
        }
      } else {
        showToast(getTranslation("toastClipboardEmpty"), "error");
      }
    } catch (err) {
      console.error("[PASTE] Error reading clipboard:", err);
      showToast(getTranslation("toastClipboardEmpty"), "error");
    }
  });

  // Direct paste into input field (context menu / keyboard paste)
  urlInput.addEventListener("paste", () => {
    setTimeout(() => {
      const text = (urlInput.value || "").trim();
      if (text) {
        clearInputBtn.classList.remove("hidden");
        const urlMatch = text.match(/https?:\/\/[^\s'"]+/i);
        const cleanVal = urlMatch ? urlMatch[0] : text;
        urlInput.value = cleanVal;
        lastAutoPastedUrl = cleanVal;

        if (settings.autoAnalyze && isValidMediaUrl(cleanVal)) {
          triggerHaptic();
          setTimeout(() => {
            analyzeLink(cleanVal);
          }, 150);
        }
      }
    }, 100);
  });

  // Analyze Link button
  analyzeBtn.addEventListener("click", () => {
    triggerHaptic();
    const url = urlInput.value.trim();
    if (!url) {
      showToast(getTranslation("toastInvalidUrl"), "error");
      return;
    }
    analyzeLink(url);
  });

  // Cancel Analyze button
  const cancelAnalyzeBtn = document.getElementById("cancelAnalyzeBtn");
  if (cancelAnalyzeBtn) {
    cancelAnalyzeBtn.addEventListener("click", () => {
      triggerHaptic();
      cancelAnalysis();
    });
  }

  // Server selection change triggers re-analysis using chosen scraper
  document.getElementById("serverSelect").addEventListener("change", (e) => {
    triggerHaptic();
    const scraperMethod = e.target.value;
    const url = urlInput.value.trim();
    analyzeLink(url, scraperMethod);
  });

  // ===================== BOTTOM TAB BAR SWITCHING =====================
  function updateTabIndicator(activeBtn) {
    const indicator = document.getElementById("tabIndicator");
    const tabBar = document.getElementById("tabBar");
    if (!indicator || !tabBar || !activeBtn) return;
    const barRect = tabBar.getBoundingClientRect();
    const btnRect = activeBtn.getBoundingClientRect();
    const targetX = (btnRect.left - barRect.left) + (btnRect.width / 2) - (indicator.offsetWidth / 2);
    indicator.style.transform = `translateX(${targetX}px)`;
  }

  function switchTab(tabId) {
    // Hide all tab panes
    document.querySelectorAll(".tab-pane").forEach(pane => pane.classList.add("hidden"));
    // Remove active from all tab buttons
    document.querySelectorAll(".tab-btn").forEach(btn => btn.classList.remove("active"));
    // Show target tab
    const target = document.getElementById(tabId);
    if (target) target.classList.remove("hidden");
    // Mark corresponding button active
    const tabBtn = document.querySelector(`.tab-btn[data-tab="${tabId}"]`);
    if (tabBtn) {
      tabBtn.classList.add("active");
      updateTabIndicator(tabBtn);
    }
    // Trigger tab-specific actions
    if (tabId === "tab-history") {
      loadHistory().then(() => renderHistory());
    } else if (tabId === "tab-player") {
      if (window.nimiyoMusicPlayer) {
        window.nimiyoMusicPlayer.autoCheckPermissionOnResume().catch(() => {});
        window.nimiyoMusicPlayer.renderCurrentTab();
        requestAnimationFrame(() => {
          window.nimiyoMusicPlayer.updateSubTabIndicator();
        });
      }
    } else if (tabId === "tab-settings") {
      updateStorageSizeDisplay();
      // Always show main settings menu on tab switch
      const mainMenu = document.getElementById("settingsMenuMain");
      if (mainMenu) mainMenu.classList.remove("hidden");
      document.querySelectorAll(".settings-sub-page").forEach(sp => sp.classList.add("hidden"));
    }
    triggerHaptic();
  }

  // Tab bar click handlers
  document.querySelectorAll(".tab-btn[data-tab]").forEach(btn => {
    btn.addEventListener("click", () => {
      const tabId = btn.getAttribute("data-tab");
      if (tabId) switchTab(tabId);
    });
  });

  // Initial tab indicator placement
  const initialActiveTabBtn = document.querySelector(".tab-btn.active") || document.querySelector(".tab-btn");
  if (initialActiveTabBtn) {
    requestAnimationFrame(() => updateTabIndicator(initialActiveTabBtn));
    setTimeout(() => updateTabIndicator(initialActiveTabBtn), 100);
  }
  window.addEventListener("resize", () => {
    const activeBtn = document.querySelector(".tab-btn.active");
    if (activeBtn) updateTabIndicator(activeBtn);
  });

  // Make switchTab accessible globally
  window.switchTab = switchTab;

  // Global Hardware Back Button Handler
  window.handleAppBackButton = function() {
    // 1. Close Lyrics modal if open
    const lyricsModal = document.getElementById("musicLyricsModal");
    if (lyricsModal && !lyricsModal.classList.contains("hidden")) {
      lyricsModal.classList.add("hidden");
      return true;
    }

    // 2. Close Queue modal if open
    const queueModal = document.getElementById("musicQueueModal");
    if (queueModal && !queueModal.classList.contains("hidden")) {
      queueModal.classList.add("hidden");
      return true;
    }

    // 3. Close Full Player modal if open
    const fullPlayer = document.getElementById("fullPlayerModal");
    if (fullPlayer && !fullPlayer.classList.contains("hidden")) {
      fullPlayer.classList.add("hidden");
      return true;
    }

    // 4. Close Track context menu if open
    const ctxMenu = document.querySelector(".track-context-menu-backdrop");
    if (ctxMenu) {
      ctxMenu.remove();
      return true;
    }

    // 5. Back from Player drilldown (artist/album)
    if (window.nimiyoMusicPlayer && (window.nimiyoMusicPlayer.selectedArtist || window.nimiyoMusicPlayer.selectedAlbum)) {
      window.nimiyoMusicPlayer.selectedArtist = null;
      window.nimiyoMusicPlayer.selectedAlbum = null;
      window.nimiyoMusicPlayer.renderCurrentTab();
      return true;
    }

    // 6. Back from Settings Subpages
    const activeSubPage = document.querySelector(".settings-sub-page:not(.hidden)");
    if (activeSubPage) {
      activeSubPage.classList.add("hidden");
      const mainMenu = document.getElementById("settingsMenuMain");
      if (mainMenu) mainMenu.classList.remove("hidden");
      return true;
    }

    // 7. If on other tab, switch back to home
    const activeTab = document.querySelector(".tab-pane:not(.hidden)");
    if (activeTab && activeTab.id !== "tab-home") {
      switchTab("tab-home");
      return true;
    }

    return false;
  };

  // Settings Subpage Routing
  document.querySelectorAll(".settings-menu-item").forEach(item => {
    item.addEventListener("click", () => {
      triggerHaptic();
      const targetId = item.getAttribute("data-target");
      if (!targetId) return;
      document.getElementById("settingsMenuMain").classList.add("hidden");
      document.querySelectorAll(".settings-sub-page").forEach(sp => sp.classList.add("hidden"));
      const targetPage = document.getElementById(targetId);
      if (targetPage) {
        targetPage.classList.remove("hidden");
      }
    });
  });

  document.querySelectorAll(".settings-back-btn").forEach(btn => {
    btn.addEventListener("click", () => {
      triggerHaptic();
      document.querySelectorAll(".settings-sub-page").forEach(sp => sp.classList.add("hidden"));
      document.getElementById("settingsMenuMain").classList.remove("hidden");
    });
  });

  // Settings Inputs Binding
  const bindChange = (id, key, isCheck = false, callback = null) => {
    const el = document.getElementById(id);
    if (!el) return;
    el.addEventListener("change", (e) => {
      settings[key] = isCheck ? e.target.checked : e.target.value;
      saveSettings();
      if (callback) callback(settings[key]);
    });
  };

  bindChange("settingLanguage", "language");
  bindChange("settingUiTheme", "uiTheme", false, applyUiTheme);
  bindChange("settingAccentColor", "accentColor", false, applyAccentColor);
  bindChange("settingAutoPaste", "autoPaste", true, (val) => {
    if (val) {
      setTimeout(checkClipboardOnResume, 200);
    }
  });
  bindChange("settingAutoAnalyze", "autoAnalyze", true);
  bindChange("settingAutoClearInput", "autoClearInput", true);
  bindChange("settingIncognito", "incognito", true);
  bindChange("settingDarkMode", "darkMode", true);
  bindChange("settingCompletionSound", "completionSound", true);
  bindChange("settingHaptic", "haptic", true);
  bindChange("settingAppFont", "appFont", false, applyAppFont);
  bindChange("settingFilenameTemplate", "filenameTemplate");
  bindChange("settingOverwriteMode", "overwriteMode");
  bindChange("settingConcurrentDl", "concurrentDl", false, () => {
    processDownloadQueue();
  });
  bindChange("settingBatchPhotoMode", "batchPhotoMode");
  bindChange("settingAutoDownload", "autoDownload", true);
  bindChange("settingAutoRetry", "autoRetry", true);
  bindChange("settingMaxRetry", "maxRetry");
  bindChange("settingWifiOnly", "wifiOnly", true);
  bindChange("settingDoh", "doh");
  bindChange("settingHeaderSpoofing", "headerSpoofing", true);
  bindChange("settingHistoryLimit", "historyLimit", false, (val) => {
    if (val !== 'unlimited') {
      const max = parseInt(val, 10);
      if (!isNaN(max) && localHistory.length > max) {
        localHistory = localHistory.slice(0, max);
        saveHistory();
        renderHistory();
      }
    }
  });
  bindChange("settingAutoClearDays", "autoClearDays", false, () => {
    runAutoCleanups();
    renderHistory();
  });
  bindChange("settingAutoClearCacheDays", "autoClearCacheDays", false, () => {
    runAutoCleanups();
  });
  bindChange("settingAutoPlay", "autoPlay", true);
  bindChange("settingAutoLoop", "autoLoop", true);
  bindChange("settingKeepAwake", "keepAwake", true, applyKeepAwake);

  // Settings Action Buttons
  const clearCacheBtn = document.getElementById("clearCacheBtn");
  if (clearCacheBtn) clearCacheBtn.addEventListener("click", clearAppCache);
  const wipeDataBtn = document.getElementById("wipeDataBtn");
  if (wipeDataBtn) wipeDataBtn.addEventListener("click", wipeAllData);
  const resetSettingsBtn = document.getElementById("resetSettingsBtn");
  if (resetSettingsBtn) resetSettingsBtn.addEventListener("click", resetSettingsToDefault);

  // History tab is now accessed via bottom nav — no separate modal button needed
  // But keep clearHistoryBtn handler

  // Helper to Close Media Player Modal
  window.closeMediaPlayerModal = function() {
    const modal = document.getElementById("mediaPlayerModal");
    if (modal && !modal.classList.contains("hidden")) {
      modal.classList.add("hidden");
      const container = document.getElementById("mediaPlayerContainer");
      if (container) {
        const player = container.querySelector("audio, video");
        if (player) {
          player.pause();
        }
        container.innerHTML = "";
      }
      const MediaSaver = window.Capacitor?.Plugins?.MediaSaver;
      if (MediaSaver && window.Capacitor?.isNativePlatform()) {
        MediaSaver.clearMusicPlaybackNotification().catch(() => { });
      }
      return true;
    }
    return false;
  };

  // Close Media Player Modal Button
  const closeMediaBtn = document.getElementById("closeMediaPlayerBtn");
  if (closeMediaBtn) {
    closeMediaBtn.addEventListener("click", () => {
      triggerHaptic();
      window.closeMediaPlayerModal();
    });
  }

  // Dismiss Media Player when clicking outside modal window (on backdrop)
  const mediaPlayerModalEl = document.getElementById("mediaPlayerModal");
  if (mediaPlayerModalEl) {
    mediaPlayerModalEl.addEventListener("click", (e) => {
      if (e.target === mediaPlayerModalEl) {
        triggerHaptic();
        window.closeMediaPlayerModal();
      }
    });
  }

  // Dismiss other popup modals when clicking on their overlay backdrop
  [
    "updateModal",
    "rulesModal"
  ].forEach(modalId => {
    const el = document.getElementById(modalId);
    if (el) {
      el.addEventListener("click", (e) => {
        if (e.target === el) {
          triggerHaptic();
          el.classList.add("hidden");
        }
      });
    }
  });

  // Global Android Back Button Handler
  let lastBackPressTime = 0;
  window.handleAppBackButton = function() {
    // 1. If Media Player Modal is open -> close it
    if (window.closeMediaPlayerModal && window.closeMediaPlayerModal()) {
      return true;
    }

    // 3. If Rules Modal is open -> close it
    const rulesModal = document.getElementById("rulesModal");
    if (rulesModal && !rulesModal.classList.contains("hidden")) {
      rulesModal.classList.add("hidden");
      return true;
    }

    // 4. If Update Modal is open -> close it
    const updateModal = document.getElementById("updateModal");
    if (updateModal && !updateModal.classList.contains("hidden")) {
      updateModal.classList.add("hidden");
      return true;
    }

    // 5. If Floating Download Balloon sheet is open -> close sheet
    const balloonSheet = document.getElementById("balloonSheet");
    if (balloonSheet && !balloonSheet.classList.contains("hidden")) {
      balloonSheet.classList.add("hidden");
      return true;
    }

    // 6. If any Settings Subpage is open -> return to Settings main menu
    const openSubPage = document.querySelector(".settings-sub-page:not(.hidden)");
    if (openSubPage) {
      document.querySelectorAll(".settings-sub-page").forEach(sp => sp.classList.add("hidden"));
      const mainMenu = document.getElementById("settingsMenuMain");
      if (mainMenu) mainMenu.classList.remove("hidden");
      return true;
    }

    // 7. If Music Player has drilldown or modal open -> handle sub-navigation
    if (window.nimiyoMusicPlayer && typeof window.nimiyoMusicPlayer.handleBackButton === "function") {
      if (window.nimiyoMusicPlayer.handleBackButton()) {
        return true;
      }
    }

    // 8. If currently on History or Settings tab -> return to Home tab
    const activeTab = document.querySelector(".tab-pane.active");
    if (activeTab && activeTab.id !== "tab-home") {
      const homeBtn = document.querySelector('.tab-btn[data-tab="tab-home"]');
      if (homeBtn) {
        homeBtn.click();
      } else if (typeof switchTab === "function") {
        switchTab("tab-home");
      }
      return true;
    }

    // 8. On Home tab with nothing open: double-press back to exit app
    const now = Date.now();
    if (now - lastBackPressTime < 2000) {
      return false; // Tells native layer to proceed with finish()
    } else {
      lastBackPressTime = now;
      showToast(getTranslation("toastPressBackAgain") || "Tekan sekali lagi untuk keluar.", "info");
      return true;
    }
  };

  // Register document backbutton event & Capacitor App backButton listener
  document.addEventListener("backbutton", (e) => {
    if (typeof window.handleAppBackButton === "function") {
      const handled = window.handleAppBackButton();
      if (handled && e && typeof e.preventDefault === "function") {
        e.preventDefault();
      }
    }
  });

  if (window.Capacitor?.Plugins?.App?.addListener) {
    try {
      window.Capacitor.Plugins.App.addListener("backButton", () => {
        if (typeof window.handleAppBackButton === "function") {
          window.handleAppBackButton();
        }
      });
    } catch (_) {}
  }

  // Clear History
  document.getElementById("clearHistoryBtn").addEventListener("click", () => {
    triggerHaptic();
    localHistory = [];
    saveHistory();
    renderHistory();
    updateStorageSizeDisplay();
    showToast(getTranslation("toastHistoryCleared"), "success");
  });

  // Cancel Download button (in modal overlay)
  document.getElementById("cancelDownloadBtn").addEventListener("click", () => {
    triggerHaptic();
    cancelDownload();
  });

  // Balloon Circle button toggle details card
  const balloonCircleBtn = document.getElementById("balloonCircleBtn");
  if (balloonCircleBtn) {
    balloonCircleBtn.addEventListener("click", () => {
      triggerHaptic();
      const sheet = document.getElementById("balloonSheet");
      if (sheet) sheet.classList.toggle("hidden");
    });
  }

  // Balloon Cancel All button (in flyout details card)
  const balloonCancelAllBtn = document.getElementById("balloonCancelAllBtn");
  if (balloonCancelAllBtn) {
    balloonCancelAllBtn.addEventListener("click", () => {
      triggerHaptic();
      cancelAllQueue();
      const sheet = document.getElementById("balloonSheet");
      if (sheet) sheet.classList.add("hidden");
    });
  }

  // Description Toggle button
  document.getElementById("toggleDescBtn").addEventListener("click", () => {
    triggerHaptic();
    const descText = document.getElementById("resultDescription");
    const toggleBtn = document.getElementById("toggleDescBtn");
    const fullText = descText.getAttribute("data-full-text") || "";

    if (descText.classList.contains("collapsed")) {
      descText.classList.remove("collapsed");
      descText.innerText = fullText;
      toggleBtn.innerText = getTranslation("toggleSeeLess");
    } else {
      descText.classList.add("collapsed");
      descText.innerText = fullText.substring(0, 80) + "...";
      toggleBtn.innerText = getTranslation("toggleSeeMore");
    }
  });

  // Description Copy button
  document.getElementById("copyDescBtn").addEventListener("click", async () => {
    triggerHaptic();
    const descText = document.getElementById("resultDescription");
    const fullText = descText.getAttribute("data-full-text") || "";
    if (fullText) {
      await setClipboardText(fullText);
      showToast(getTranslation("toastCopiedDesc"), "success");
    }
  });

  // Title Toggle button
  document.getElementById("toggleTitleBtn").addEventListener("click", () => {
    triggerHaptic();
    const titleText = document.getElementById("resultTitle");
    const toggleBtn = document.getElementById("toggleTitleBtn");
    const fullText = titleText.getAttribute("data-full-text") || "";

    if (titleText.classList.contains("collapsed")) {
      titleText.classList.remove("collapsed");
      titleText.innerText = fullText;
      toggleBtn.innerText = getTranslation("toggleSeeLess");
    } else {
      titleText.classList.add("collapsed");
      titleText.innerText = fullText.substring(0, 80) + "...";
      toggleBtn.innerText = getTranslation("toggleSeeMore");
    }
  });

  // Title Copy button
  document.getElementById("copyTitleBtn").addEventListener("click", async () => {
    triggerHaptic();
    const titleText = document.getElementById("resultTitle");
    const fullText = titleText.getAttribute("data-full-text") || "";
    if (fullText) {
      await setClipboardText(fullText);
      showToast(getTranslation("toastCopiedTitle"), "success");
    }
  });

  // Auto-Update permission button in Settings -> Advanced
  const btnAutoUpdatePerm = document.getElementById("btnAutoUpdatePermission");
  if (btnAutoUpdatePerm) {
    btnAutoUpdatePerm.addEventListener("click", () => {
      requestAutoUpdatePermission();
    });
  }

  // Manual Check for Updates button in About App
  const btnCheckUpdateManual = document.getElementById("btnCheckUpdateManual");
  if (btnCheckUpdateManual) {
    btnCheckUpdateManual.addEventListener("click", () => {
      triggerHaptic();
      checkForAppUpdates(true);
    });
  }

  // Update Modal Close & Later buttons
  const closeUpdateModalBtn = document.getElementById("closeUpdateModalBtn");
  if (closeUpdateModalBtn) {
    closeUpdateModalBtn.addEventListener("click", () => {
      triggerHaptic();
      document.getElementById("updateModal").classList.add("hidden");
    });
  }
  const btnLaterUpdate = document.getElementById("btnLaterUpdate");
  if (btnLaterUpdate) {
    btnLaterUpdate.addEventListener("click", () => {
      triggerHaptic();
      document.getElementById("remindScheduleMenu")?.classList.add("hidden");
      document.getElementById("updateModal").classList.add("hidden");
    });
  }

  // Toggle Reminder Schedule Menu
  const btnRemindSchedule = document.getElementById("btnRemindSchedule");
  const remindScheduleMenu = document.getElementById("remindScheduleMenu");
  if (btnRemindSchedule && remindScheduleMenu) {
    btnRemindSchedule.addEventListener("click", (e) => {
      e.stopPropagation();
      triggerHaptic();
      remindScheduleMenu.classList.toggle("hidden");
    });
  }

  // Handle reminder schedule options: 1 Hari, 3 Hari, 1 Minggu
  document.querySelectorAll(".remind-opt-btn").forEach(btn => {
    btn.addEventListener("click", (e) => {
      e.stopPropagation();
      triggerHaptic();
      const days = parseInt(btn.getAttribute("data-days")) || 1;
      const until = Date.now() + (days * 24 * 60 * 60 * 1000);
      const versionStr = String(latestUpdateInfo?.versionName || latestUpdateInfo?.versionCode || "latest");

      localStorage.setItem("nimiyo_update_snooze_until", String(until));
      localStorage.setItem("nimiyo_update_snooze_version", versionStr);
      localStorage.removeItem("nimiyo_update_snooze_expired");

      let timeLabel = `${days} Hari`;
      if (days === 1) timeLabel = getTranslation("remind1Day", "1 Hari");
      else if (days === 3) timeLabel = getTranslation("remind3Days", "3 Hari");
      else if (days === 7) timeLabel = getTranslation("remind1Week", "1 Minggu");

      const msg = getTranslation("toastRemindScheduled", { time: timeLabel });
      showToast(msg, "info");

      if (remindScheduleMenu) remindScheduleMenu.classList.add("hidden");
      document.getElementById("updateModal")?.classList.add("hidden");
    });
  });

  // Update Now / Update & Install button
  const btnStartUpdate = document.getElementById("btnStartUpdate");
  if (btnStartUpdate) {
    btnStartUpdate.addEventListener("click", () => {
      triggerHaptic();
      downloadAndInstallUpdate();
    });
  }

  // Download Manual Install button
  const btnManualDownloadUpdate = document.getElementById("btnManualDownloadUpdate");
  if (btnManualDownloadUpdate) {
    btnManualDownloadUpdate.addEventListener("click", () => {
      triggerHaptic();
      downloadManualUpdate();
    });
  }

  // Rules & Terms Modal Listeners
  const btnOpenRulesModal = document.getElementById("btnOpenRulesModal");
  if (btnOpenRulesModal) {
    btnOpenRulesModal.addEventListener("click", () => {
      triggerHaptic();
      const modal = document.getElementById("rulesModal");
      if (modal) modal.classList.remove("hidden");
    });
  }
  const closeRulesModalBtn = document.getElementById("closeRulesModalBtn");
  if (closeRulesModalBtn) {
    closeRulesModalBtn.addEventListener("click", () => {
      triggerHaptic();
      const modal = document.getElementById("rulesModal");
      if (modal) modal.classList.add("hidden");
    });
  }
  const btnAgreeRules = document.getElementById("btnAgreeRules");
  if (btnAgreeRules) {
    btnAgreeRules.addEventListener("click", () => {
      triggerHaptic();
      localStorage.setItem("nimiyo_rules_accepted", "true");
      const modal = document.getElementById("rulesModal");
      if (modal) modal.classList.add("hidden");
      showToast(getTranslation("toastSettingsSaved"), "success");
    });
  }

  // Register resume & focus event listeners for Auto-Paste, History Sync & Permission recheck
  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "visible") {
      loadHistory();
      setTimeout(checkClipboardOnResume, 350);
      checkAndResumePendingUpdateInstall();
      if (window.nimiyoMusicPlayer) window.nimiyoMusicPlayer.autoCheckPermissionOnResume().catch(() => {});
    }
  });

  window.addEventListener("focus", () => {
    loadHistory();
    setTimeout(checkClipboardOnResume, 350);
    checkAndResumePendingUpdateInstall();
    if (window.nimiyoMusicPlayer) window.nimiyoMusicPlayer.autoCheckPermissionOnResume().catch(() => {});
  });

  const AppPlugin = window.Capacitor?.Plugins?.App;
  if (AppPlugin && typeof AppPlugin.addListener === "function") {
    AppPlugin.addListener("appStateChange", ({ isActive }) => {
      if (isActive) {
        loadHistory();
        setTimeout(checkClipboardOnResume, 350);
        checkAndResumePendingUpdateInstall();
        if (window.nimiyoMusicPlayer) window.nimiyoMusicPlayer.autoCheckPermissionOnResume().catch(() => {});
      }
    });
    AppPlugin.addListener("resume", () => {
      loadHistory();
      setTimeout(checkClipboardOnResume, 350);
      checkAndResumePendingUpdateInstall();
      if (window.nimiyoMusicPlayer) window.nimiyoMusicPlayer.autoCheckPermissionOnResume().catch(() => {});
    });
  }
}

// Auto-Paste/Analyze on App Launch & Resume
async function checkClipboardOnResume() {
  if (!settings.autoPaste && !settings.autoAnalyze) return;

  try {
    const rawText = await getClipboardText();
    const trimmed = (rawText || "").trim();

    const urlInput = document.getElementById("urlInput");
    const clearBtn = document.getElementById("clearBtn");
    const currentVal = (urlInput?.value || "").trim();

    // 1. If autoPaste is enabled and valid URL is on clipboard
    if (settings.autoPaste && trimmed) {
      const urlMatch = trimmed.match(/https?:\/\/[^\s'"]+/i);
      const candidateUrl = urlMatch ? urlMatch[0] : trimmed;

      if (candidateUrl && isValidMediaUrl(candidateUrl)) {
        if (currentVal !== candidateUrl || lastAutoPastedUrl !== candidateUrl) {
          if (urlInput) urlInput.value = candidateUrl;
          lastAutoPastedUrl = candidateUrl;
          const clearInputBtn = document.getElementById("clearInputBtn");
          if (clearInputBtn) clearInputBtn.classList.remove("hidden");
          showToast(getTranslation("toastDetecting"), "info");

          if (settings.autoAnalyze) {
            setTimeout(() => {
              analyzeLink(candidateUrl);
            }, 200);
          }
          return;
        }
      }
    }

    // 2. If autoAnalyze is enabled and input field has a valid URL ready
    if (settings.autoAnalyze && currentVal && isValidMediaUrl(currentVal) && !activeAnalysisResult) {
      if (lastAutoPastedUrl !== currentVal) {
        lastAutoPastedUrl = currentVal;
        analyzeLink(currentVal);
      }
    }
  } catch (err) {
    console.warn("[CLIPBOARD RESUME] Failed:", err);
  }
}

// Validate URL against supported platform patterns
function isValidMediaUrl(url) {
  return Object.keys(platformMapping).some(key => platformMapping[key].domains.test(url));
}

// Get matching platform key for URL
function getPlatformFromUrl(url) {
  const match = Object.keys(platformMapping).find(key => platformMapping[key].domains.test(url));
  return match || null;
}

// Main Analyze Link Function
// Global analysis control state
let analysisCancelled = false;
let currentAnalysisCancelReject = null;

// Function to immediately cancel and terminate the ongoing analysis
function cancelAnalysis() {
  analysisCancelled = true;
  if (currentAnalysisCancelReject) {
    currentAnalysisCancelReject(new Error("Analysis aborted by user"));
    currentAnalysisCancelReject = null;
  }

  // Reset UI Immediately without any delay
  const analyzeBtn = document.getElementById("analyzeBtn");
  const loader = document.querySelector("#analyzeBtn .loader");
  const btnText = document.querySelector("#analyzeBtn .btn-text");
  const cancelBtn = document.getElementById("cancelAnalyzeBtn");

  if (analyzeBtn) {
    analyzeBtn.disabled = false;
    analyzeBtn.classList.remove("split-active");
  }
  if (loader) loader.classList.add("hidden");
  if (btnText) btnText.innerText = getTranslation("btnAnalyze");
  if (cancelBtn) {
    cancelBtn.classList.remove("split-active");
    cancelBtn.classList.add("hidden");
  }

  showToast(getTranslation("toastAnalysisCancelled"), "info");
}

// Format detailed, user-friendly error messages based on diagnosis
async function formatAnalysisErrorMessage(url, platform, failureDetails = []) {
  const lowerUrl = (url || "").toLowerCase();
  const allErrorsText = failureDetails
    .map(f => (f.message || ""))
    .join(" ")
    .toLowerCase();

  // 1. Comprehensive Network / Offline detection
  // Covers Android CapacitorHttp, OkHttp, Java net exceptions, Chromium WebView, and standard Fetch
  const isNetworkFailure =
    (typeof navigator !== "undefined" && navigator.onLine === false) ||
    /unable to resolve host|no address associated|network is unreachable|unreachable|failed to connect|failed to fetch|networkerror|network error|unknownhost|unknown host|connectexception|socketexception|sockettimeout|connection refused|connection reset|route to host|no route|ehostunreach|enotfound|econnrefused|econnreset|net::err|internet_disconnected|name_not_resolved|network request failed/i.test(allErrorsText);

  if (isNetworkFailure) {
    return getTranslation("toastErrorOffline");
  }

  // 2. Check if ANY scraper managed to reach an HTTP server
  const hadAnyHttpContact = failureDetails.some(f => f.statusCode && f.statusCode > 0);

  // If no scraper ever reached any HTTP endpoint, do a quick non-blocking probe (max 600ms)
  // This adds ZERO latency to normal usage because it ONLY runs when ALL scrapers have failed
  if (!hadAnyHttpContact) {
    try {
      await Promise.race([
        fetch("https://www.google.com/generate_204", { method: "HEAD", mode: "no-cors", cache: "no-store" }),
        new Promise((_, reject) => setTimeout(() => reject(new Error("probe_timeout")), 600))
      ]);
    } catch (_) {
      return getTranslation("toastErrorOffline");
    }
  }

  // 3. Timeout detection
  const isTimeout = /timed out|timeout|request timeout/i.test(allErrorsText);
  if (isTimeout && failureDetails.length > 0 && failureDetails.every(f => /timed out|timeout/i.test(f.message || ""))) {
    return getTranslation("toastErrorTimeout");
  }

  // 4. Platform-specific diagnostics ONLY IF content/server was actually reached or error explicitly mentions private/deleted
  if (platform === "spotify") {
    if (lowerUrl.includes("/playlist/")) {
      return getTranslation("toastErrorSpotifyPlaylistPrivate");
    }
    return getTranslation("toastErrorSpotifyTrackNotFound");
  }

  if (platform === "instagram") {
    if (/private|login|checkpoint|restricted|not found|404|removed|deleted|empty/i.test(allErrorsText) || hadAnyHttpContact) {
      return getTranslation("toastErrorInstagramPrivate");
    }
  }

  if (platform === "tiktok") {
    if (/private|deleted|removed|not found|404|unavailable/i.test(allErrorsText) || hadAnyHttpContact) {
      return getTranslation("toastErrorTikTokPrivate");
    }
  }

  if (platform === "youtube") {
    if (/private|age|restricted|not available|unavailable|removed|deleted|404|playlist/i.test(allErrorsText) || hadAnyHttpContact) {
      return getTranslation("toastErrorYouTubeUnavailable");
    }
  }

  if (platform === "twitter") {
    if (/private|suspended|not found|404|protected|empty/i.test(allErrorsText) || hadAnyHttpContact) {
      return getTranslation("toastErrorTwitterPrivate");
    }
  }

  if (platform === "facebook") {
    if (/private|login|permission|not found|404/i.test(allErrorsText) || hadAnyHttpContact) {
      return getTranslation("toastErrorFacebookPrivate");
    }
  }

  // 5. Rate-limit / Cloudflare protection block
  if (/cloudflare|rate-limit|rate limited|blocked|429|503|502|html error page/i.test(allErrorsText)) {
    return getTranslation("toastErrorServerBlocked");
  }

  // 6. Clean, human-readable scraper message if available
  for (const f of failureDetails) {
    const msg = (f.message || "").trim();
    if (
      msg &&
      msg.length > 8 &&
      !msg.startsWith("<") &&
      !msg.includes("Error:") &&
      !msg.includes("SyntaxError") &&
      !msg.includes("TypeError") &&
      !msg.includes("JSON.parse") &&
      !msg.includes("returned failure") &&
      !msg.includes("dummy")
    ) {
      return msg;
    }
  }

  if (/not found|404|empty|removed|deleted/i.test(allErrorsText)) {
    return getTranslation("toastErrorMediaNotFound");
  }

  if (isTimeout) {
    return getTranslation("toastErrorTimeout");
  }

  return getTranslation("toastScrapeAllFailed");
}

// Main Analyze Link Function
async function analyzeLink(url, specificScraper = null) {
  const platform = getPlatformFromUrl(url);
  if (!platform) {
    showToast(getTranslation("toastInvalidUrl"), "error");
    return;
  }

  currentPlatform = platform;
  const scrapers = fallbackChains[platform];
  if (!scrapers || scrapers.length === 0) {
    showToast(getTranslation("toastUnsupportedPlatform"), "error");
    return;
  }

  // Determine scrapers order based on parameters or settings
  let orderedScrapers = [...scrapers];
  if (specificScraper) {
    orderedScrapers = [specificScraper];
  } else if (settings.preferredServer !== 'auto' && scrapers.includes(settings.preferredServer)) {
    orderedScrapers = [
      settings.preferredServer,
      ...scrapers.filter(s => s !== settings.preferredServer)
    ];
  }

  const analyzeBtn = document.getElementById("analyzeBtn");
  const loader = document.querySelector("#analyzeBtn .loader");
  const btnText = document.querySelector("#analyzeBtn .btn-text");
  const cancelBtn = document.getElementById("cancelAnalyzeBtn");

  // Show Loading state once at the start
  analysisCancelled = false;
  analyzeBtn.disabled = true;
  loader.classList.remove("hidden");
  btnText.innerText = getTranslation("btnAnalyzing");
  document.getElementById("resultSection").classList.add("hidden");

  // Split-layout activation for cancel button
  if (cancelBtn) {
    cancelBtn.classList.remove("hidden");
    // Small delay to allow transition after class removal
    setTimeout(() => {
      analyzeBtn.classList.add("split-active");
      cancelBtn.classList.add("split-active");
    }, 10);
  }

  let success = false;
  let allFailed = true;
  const failureDetails = [];

  for (const scraperMethod of orderedScrapers) {
    if (analysisCancelled) {
      allFailed = false;
      break;
    }

    console.log(`Trying scraper: ${scraperMethod} for platform ${platform}`);

    // Apply safe network timeout
    const timeoutSec = (settings.timeout && !isNaN(settings.timeout)) ? Number(settings.timeout) : 30;
    const timeoutMs = Math.max(timeoutSec, 15) * 1000;
    let timeoutId;

    const scraperPromise = (async () => {
      const scraperModule = window.scrapr?.[platform];
      const scrapeFunc = scraperModule?.[scraperMethod] || scraperModule?.default || window.scrapr?.[`scrape${platform.charAt(0).toUpperCase() + platform.slice(1)}`];
      if (!scrapeFunc) {
        return { status: false, message: `Scraper ${scraperMethod} not found` };
      }
      return await scrapeFunc(url);
    })();

    const timeoutPromise = new Promise((_, reject) => {
      timeoutId = setTimeout(() => reject(new Error("Request timed out.")), timeoutMs);
    });

    const cancelPromise = new Promise((_, reject) => {
      currentAnalysisCancelReject = reject;
    });

    try {
      // Race the request against the timeout AND immediate user cancellation!
      const response = await Promise.race([scraperPromise, timeoutPromise, cancelPromise]);
      clearTimeout(timeoutId);
      currentAnalysisCancelReject = null;

      if (analysisCancelled) {
        allFailed = false;
        break;
      }

      if (response && response.status === true) {
        // SUCCESS
        activeAnalysisResult = response.result;
        activeScraperMethod = scraperMethod;
        showToast(getTranslation("toastScrapeSuccess"), "success");
        renderResult(response.result, platform, scraperMethod);

        // Auto-Clear Input if enabled
        if (settings.autoClearInput) {
          const urlInput = document.getElementById("urlInput");
          if (urlInput) urlInput.value = "";
          const clearInputBtn = document.getElementById("clearInputBtn");
          if (clearInputBtn) clearInputBtn.classList.add("hidden");
        }

        // Auto-Download if enabled
        if (settings.autoDownload && response.result?.downloads?.length > 0) {
          setTimeout(() => {
            if (response.result.downloads.length === 1) {
              triggerDownload(response.result.downloads[0], response.result);
            } else {
              triggerBatchDownload(response.result.downloads, response.result);
            }
          }, 400);
        }

        success = true;
        break; // Stop fallback chain on success
      } else {
        const errorMsg = response?.message || "Unknown error";
        console.warn(`Scraper ${scraperMethod} failed: ${errorMsg}`);
        failureDetails.push({ scraper: scraperMethod, message: errorMsg, statusCode: response?.statusCode });
      }
    } catch (error) {
      clearTimeout(timeoutId);
      currentAnalysisCancelReject = null;
      if (analysisCancelled || error.message === "Analysis aborted by user") {
        allFailed = false;
        break; // Instant break on user cancel
      }
      console.error(`Scraper ${scraperMethod} crashed or timed out: `, error);
      failureDetails.push({ scraper: scraperMethod, message: error?.message || String(error) });
    }
  }

  // If user cancelled, UI is already reset by cancelAnalysis()
  if (analysisCancelled) {
    return;
  }

  // If all scrapers failed, format a specific, informative toast message
  if (!success && allFailed) {
    const errorMsg = await formatAnalysisErrorMessage(url, platform, failureDetails);
    showToast(errorMsg, "error");
  }

  // Hide Loading state once at the very end
  analyzeBtn.disabled = false;
  loader.classList.add("hidden");
  btnText.innerText = getTranslation("btnAnalyze");

  if (cancelBtn) {
    analyzeBtn.classList.remove("split-active");
    cancelBtn.classList.remove("split-active");
    setTimeout(() => {
      cancelBtn.classList.add("hidden");
    }, 250); // Matches transition duration
  }
}

// Render Results Section
// Load thumbnail securely via native CapacitorHttp to bypass CORS and Referer blocking on Android
async function loadSecureThumbnail(url, imgElement) {
  if (!url) {
    imgElement.src = "nimiyo_icon.webp";
    return;
  }

  // If local converted URI, file URI, Base64, or Blob, load directly without fetch!
  if (
    url.startsWith("http://localhost/_capacitor_file_/") ||
    url.startsWith("capacitor://") ||
    url.startsWith("file://") ||
    url.startsWith("data:") ||
    url.startsWith("blob:") ||
    url.startsWith("content://")
  ) {
    imgElement.src = url;
    return;
  }

  // Pre-load local icon while fetching remote image
  imgElement.src = "nimiyo_icon.webp";

  try {
    const response = await fetch(url);
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const blob = await response.blob();

    if (imgElement.src && imgElement.src.startsWith("blob:")) {
      try {
        URL.revokeObjectURL(imgElement.src);
      } catch (_) { }
    }

    const objectUrl = URL.createObjectURL(blob);
    imgElement.src = objectUrl;
  } catch (err) {
    console.warn("Secure thumbnail load failed, falling back to direct URL:", err);
    imgElement.src = url; // fallback to direct link
  }
}

// Render Results Section
function renderResult(result, platform, activeMethod) {
  const section = document.getElementById("resultSection");
  const previewContainer = document.querySelector(".result-preview");
  const title = document.getElementById("resultTitle");
  const platformTag = document.getElementById("platformTag");
  const serverSelect = document.getElementById("serverSelect");
  const serverContainer = document.getElementById("serverSelectorContainer");

  // Check for multi-photo content (TikTok slideshow, Instagram carousel, etc.)
  let photoUrls = [];
  if (result.photos && Array.isArray(result.photos) && result.photos.length > 0) {
    photoUrls = result.photos.map(p => typeof p === "string" ? p : p.url || p.src || "").filter(Boolean);
  } else if (result.downloads && Array.isArray(result.downloads)) {
    photoUrls = result.downloads
      .filter(d => detectMediaCategory(d, result) === "image" && d.url && d.url.startsWith("http"))
      .map(d => d.url);
  }

  if (photoUrls.length > 1) {
    // Render Swipeable / Scrollable Carousel
    previewContainer.innerHTML = "";

    const carousel = document.createElement("div");
    carousel.className = "carousel-container";

    const track = document.createElement("div");
    track.className = "carousel-track";

    photoUrls.forEach((pUrl, pIdx) => {
      const slide = document.createElement("div");
      slide.className = "carousel-slide";
      const slideImg = document.createElement("img");
      slideImg.alt = `Photo ${pIdx + 1}`;
      loadSecureThumbnail(pUrl, slideImg);
      slide.appendChild(slideImg);
      track.appendChild(slide);
    });

    const counter = document.createElement("div");
    counter.className = "carousel-counter";
    counter.innerText = `1 / ${photoUrls.length}`;

    const prevBtn = document.createElement("button");
    prevBtn.className = "carousel-nav-btn carousel-prev";
    prevBtn.setAttribute("aria-label", "Previous photo");
    prevBtn.innerHTML = `<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="15 18 9 12 15 6"></polyline></svg>`;

    const nextBtn = document.createElement("button");
    nextBtn.className = "carousel-nav-btn carousel-next";
    nextBtn.setAttribute("aria-label", "Next photo");
    nextBtn.innerHTML = `<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="9 18 15 12 9 6"></polyline></svg>`;

    let currentIndex = 0;

    const scrollToSlide = (idx) => {
      if (idx < 0) idx = 0;
      if (idx >= photoUrls.length) idx = photoUrls.length - 1;
      currentIndex = idx;
      track.scrollTo({
        left: idx * track.clientWidth,
        behavior: "smooth"
      });
      counter.innerText = `${idx + 1} / ${photoUrls.length}`;
    };

    prevBtn.addEventListener("click", (e) => {
      e.stopPropagation();
      triggerHaptic();
      scrollToSlide(currentIndex - 1);
    });

    nextBtn.addEventListener("click", (e) => {
      e.stopPropagation();
      triggerHaptic();
      scrollToSlide(currentIndex + 1);
    });

    track.addEventListener("scroll", () => {
      const idx = Math.round(track.scrollLeft / track.clientWidth);
      if (idx !== currentIndex && idx >= 0 && idx < photoUrls.length) {
        currentIndex = idx;
        counter.innerText = `${idx + 1} / ${photoUrls.length}`;
      }
    });

    const badge = document.createElement("div");
    badge.id = "mediaTypeBadge";
    badge.className = "badge badge-image";
    badge.innerText = `PHOTO (${photoUrls.length})`;

    carousel.appendChild(track);
    carousel.appendChild(prevBtn);
    carousel.appendChild(nextBtn);
    carousel.appendChild(counter);
    carousel.appendChild(badge);
    previewContainer.appendChild(carousel);
  } else {
    // Single image/video/audio preview
    let detectedMainType = result.type;
    if (!detectedMainType) {
      if (platform === "spotify" || platform === "applemusic" || platform === "bandcamp") {
        detectedMainType = "audio";
      } else if (photoUrls.length > 0) {
        detectedMainType = "photo";
      } else if (result.downloads && result.downloads.length > 0) {
        const firstCat = detectMediaCategory(result.downloads[0], result);
        detectedMainType = firstCat === "image" ? "photo" : firstCat;
      } else {
        detectedMainType = "video";
      }
    } else if (photoUrls.length === 1) {
      detectedMainType = "photo";
    }

    previewContainer.innerHTML = `
      <img id="resultThumbnail" src="" alt="Media Preview">
      <div id="mediaTypeBadge" class="badge badge-${detectedMainType.toLowerCase()}">${detectedMainType.toUpperCase()}</div>
    `;
    const thumbnail = document.getElementById("resultThumbnail");
    const thumbUrl = (photoUrls.length === 1 ? photoUrls[0] : null) || result.thumbnail;
    loadSecureThumbnail(thumbUrl, thumbnail);
  }

  // Title
  const mediaTitle = result.title || "Media Title";
  title.setAttribute("data-full-text", mediaTitle);
  const toggleTitleBtn = document.getElementById("toggleTitleBtn");

  if (mediaTitle.length > 80) {
    title.innerText = mediaTitle.substring(0, 80) + "...";
    title.classList.add("collapsed");
    toggleTitleBtn.innerText = getTranslation("toggleSeeMore");
    toggleTitleBtn.style.display = "block";
  } else {
    title.innerText = mediaTitle;
    title.classList.remove("collapsed");
    toggleTitleBtn.style.display = "none";
  }

  // Platform Tag
  platformTag.innerText = platformMapping[platform]?.name || platform;
  platformTag.style.backgroundColor = platformMapping[platform]?.color || "#FF5E5E";
  platformTag.style.color = "#FFFFFF";

  // Description rendering
  const descContainer = document.getElementById("descriptionContainer");
  const descText = document.getElementById("resultDescription");
  const toggleBtn = document.getElementById("toggleDescBtn");
  const descLabel = document.querySelector("#descriptionContainer .description-label");

  if (descLabel) {
    descLabel.innerText = getTranslation("descLabel");
  }

  const description = result.description || "";
  if (description) {
    descText.setAttribute("data-full-text", description);

    if (description.length > 80) {
      descText.innerText = description.substring(0, 80) + "...";
      descText.classList.add("collapsed");
      toggleBtn.innerText = getTranslation("toggleSeeMore");
      toggleBtn.style.display = "block";
    } else {
      descText.innerText = description;
      descText.classList.remove("collapsed");
      toggleBtn.style.display = "none";
    }
    descContainer.classList.remove("hidden");
  } else {
    descContainer.classList.add("hidden");
  }

  // Server selection population
  const chain = fallbackChains[platform];
  if (chain && chain.length > 1) {
    serverContainer.classList.remove("hidden");
    serverSelect.innerHTML = "";
    chain.forEach(method => {
      const option = document.createElement("option");
      option.value = method;
      option.innerText = method;
      option.selected = (method === activeMethod);
      serverSelect.appendChild(option);
    });
  } else {
    serverContainer.classList.add("hidden");
  }

  // Populate download options
  const list = document.getElementById("downloadLinksList");
  list.innerHTML = "";

  if (result.downloads && result.downloads.length > 0) {
    const isMultiTrack = result.downloads.length > 1;

    if (isMultiTrack) {
      const groupDiv = document.createElement("div");
      groupDiv.className = "multi-download-header";
      groupDiv.style.marginBottom = "16px";
      groupDiv.style.width = "100%";

      const downloadAllBtn = document.createElement("button");
      downloadAllBtn.className = "btn primary-btn";
      downloadAllBtn.style.width = "100%";
      downloadAllBtn.innerText = getTranslation("btnDownloadAll", { count: result.downloads.length });
      downloadAllBtn.onclick = () => {
        triggerHaptic();
        enqueueDownloads(result.downloads, result);
      };

      groupDiv.appendChild(downloadAllBtn);
      list.appendChild(groupDiv);
    }

    result.downloads.forEach((dl) => {
      const option = document.createElement("div");
      option.className = "download-option";
      option.style.display = "flex";
      option.style.alignItems = "center";
      option.style.gap = "10px";

      const meta = document.createElement("div");
      meta.className = "download-option-meta";
      meta.style.flex = "1";

      const type = document.createElement("span");
      type.className = "option-type";
      type.innerText = formatDownloadOptionLabel(dl, result);

      const quality = document.createElement("span");
      quality.className = "option-quality";
      quality.innerText = dl.url ? getTranslation("statusReadyToDownload") : getTranslation("statusNoUrl");

      meta.appendChild(type);
      meta.appendChild(quality);

      const btn = document.createElement("button");
      btn.className = "btn primary-btn option-btn";
      btn.innerText = getTranslation("btnDownload");

      btn.addEventListener("click", () => {
        triggerHaptic();
        enqueueDownloads([dl], result);
      });

      option.appendChild(meta);
      option.appendChild(btn);
      list.appendChild(option);
    });
  } else {
    list.innerHTML = `<p class="history-empty">${getTranslation("noDownloadLinks")}</p>`;
  }

  section.classList.remove("hidden");
  section.scrollIntoView({ behavior: "smooth" });
}

// Helper to detect media category: 'image', 'audio', or 'video'
function detectMediaCategory(dlItem, mediaResult, contentType = "") {
  const ct = String(contentType || "").toLowerCase();
  if (ct.startsWith("image/")) return "image";
  if (ct.startsWith("audio/")) return "audio";
  if (ct.startsWith("video/")) return "video";

  const itemType = String(dlItem?.type || "").toUpperCase();
  const resType = String(mediaResult?.type || "").toUpperCase();
  const quality = String(dlItem?.quality || "").toUpperCase();
  const rawUrl = String(dlItem?.url || "").split("?")[0].toLowerCase();

  // 1. Audio detection
  if (
    itemType.includes("[MP3]") ||
    itemType.includes("MP3") ||
    itemType.includes("AUDIO") ||
    itemType.includes("MUSIC") ||
    itemType.includes("TRACK") ||
    itemType.includes("M4A") ||
    itemType.includes("FLAC") ||
    itemType.includes("WAV") ||
    resType === "AUDIO" ||
    quality.includes("MP3") ||
    quality.includes("AUDIO") ||
    quality.includes("KBPS") ||
    rawUrl.endsWith(".mp3") ||
    rawUrl.endsWith(".m4a") ||
    rawUrl.endsWith(".wav") ||
    rawUrl.endsWith(".flac") ||
    rawUrl.includes("spotidown_resolve:") ||
    rawUrl.includes("soundloaders_resolve:") ||
    rawUrl.includes("ytmp3gg_resolve:") ||
    rawUrl.includes("applemusic_resolve:")
  ) {
    return "audio";
  }

  // 2. Image / Photo detection
  if (
    itemType.includes("PHOTO") ||
    itemType.includes("IMAGE") ||
    itemType.includes("PICTURE") ||
    itemType.includes("FOTO") ||
    itemType.includes("GAMBAR") ||
    itemType.includes("SLIDESHOW") ||
    itemType.includes("[COVER]") ||
    itemType.includes("COVER") ||
    resType === "IMAGE" ||
    resType === "PHOTO" ||
    quality.includes("PHOTO") ||
    quality.includes("IMAGE") ||
    rawUrl.endsWith(".png") ||
    rawUrl.endsWith(".jpg") ||
    rawUrl.endsWith(".jpeg") ||
    rawUrl.endsWith(".webp")
  ) {
    return "image";
  }

  // 3. Default: video
  return "video";
}

// Determine file extension from Content-Type, URL, or Category
function determineExtension(mediaCategory, url = "", contentType = "") {
  const ct = String(contentType || "").toLowerCase();
  const lowerUrl = String(url || "").split("?")[0].toLowerCase();

  if (mediaCategory === "image") {
    if (ct.includes("png") || lowerUrl.endsWith(".png")) return ".png";
    if (ct.includes("webp") || lowerUrl.endsWith(".webp")) return ".webp";
    if (ct.includes("jpeg") || ct.includes("jpg") || lowerUrl.endsWith(".jpg") || lowerUrl.endsWith(".jpeg")) return ".jpg";
    return ".png";
  }

  if (mediaCategory === "audio") {
    if (ct.includes("m4a") || lowerUrl.endsWith(".m4a")) return ".m4a";
    if (ct.includes("wav") || lowerUrl.endsWith(".wav")) return ".wav";
    if (ct.includes("flac") || lowerUrl.endsWith(".flac")) return ".flac";
    return ".mp3";
  }

  // Video
  if (ct.includes("webm") || lowerUrl.endsWith(".webm")) return ".webm";
  if (ct.includes("mov") || lowerUrl.endsWith(".mov")) return ".mov";
  if (ct.includes("mkv") || lowerUrl.endsWith(".mkv")) return ".mkv";
  return ".mp4";
}

// Format download option labels according to standardized category & quality rules:
// Video: (VIDEO) MP4 1040P / (VIDEO) MP4 1080P / (VIDEO) MP4
// Audio: (AUDIO) MP3 HD / (AUDIO) MP3 SD
// Image: (IMAGE) JPG / (IMAGE) PNG / (IMAGE) WEBP
function formatDownloadOptionLabel(dl, mediaResult) {
  if (!dl) return "DOWNLOAD";

  const category = detectMediaCategory(dl, mediaResult);
  const rawType = String(dl.type || "").trim();
  const rawQuality = String(dl.quality || "").trim();
  const rawUrl = String(dl.url || "").split("?")[0].toLowerCase();
  const combined = `${rawType} ${rawQuality} ${rawUrl}`.toUpperCase();

  // Determine file extension
  let extension = "MP4";
  if (category === "audio") {
    if (combined.includes("M4A") || rawUrl.endsWith(".m4a")) extension = "M4A";
    else if (combined.includes("WAV") || rawUrl.endsWith(".wav")) extension = "WAV";
    else if (combined.includes("FLAC") || rawUrl.endsWith(".flac")) extension = "FLAC";
    else extension = "MP3";
  } else if (category === "image") {
    if (combined.includes("PNG") || rawUrl.endsWith(".png")) extension = "PNG";
    else if (combined.includes("WEBP") || rawUrl.endsWith(".webp")) extension = "WEBP";
    else extension = "JPG";
  } else {
    if (combined.includes("WEBM") || rawUrl.endsWith(".webm")) extension = "WEBM";
    else if (combined.includes("MOV") || rawUrl.endsWith(".mov")) extension = "MOV";
    else extension = "MP4";
  }

  // Check if item has a specific track title (e.g. in playlists: "01. Artist - Song")
  const isPlaylistTrack = Boolean(
    dl.title ||
    (/^\d+[\.\s]/.test(rawType) && !rawType.startsWith("1080") && !rawType.startsWith("720") && !rawType.startsWith("360") && !rawType.startsWith("480")) ||
    (rawType.includes(" - ") && !rawType.toUpperCase().startsWith("VIDEO") && !rawType.toUpperCase().startsWith("AUDIO") && !rawType.toUpperCase().startsWith("PHOTO") && !rawType.toUpperCase().startsWith("IMAGE"))
  );
  const trackTitle = dl.title || (isPlaylistTrack ? rawType : "");

  let badge = "";

  if (category === "audio") {
    // Audio SD or HD only:
    // Bitrate >= 192k or 320k or 256k or marked HD/HQ => HD
    // 128k or lower or marked SD => SD
    // Default to HD if standard/unspecified
    const isSD = combined.includes("128KBPS") || combined.includes("128K") || combined.includes("64KBPS") || combined.includes("64K") || combined.includes("SD") || combined.includes("LOW");
    const isHD = combined.includes("320KBPS") || combined.includes("320K") || combined.includes("256KBPS") || combined.includes("256K") || combined.includes("192KBPS") || combined.includes("HD") || combined.includes("HQ") || combined.includes("HIGH");

    const qualityTag = (isSD && !isHD) ? "SD" : "HD";
    badge = `(AUDIO) ${extension} ${qualityTag}`;
  } else if (category === "image") {
    // Image: (IMAGE) PNG, (IMAGE) JPG, (IMAGE) WEBP
    badge = `(IMAGE) ${extension}`;
  } else {
    // Video: (VIDEO) MP4 1040P, (VIDEO) MP4 1080P, (VIDEO) MP4 720P, etc.
    let qualityTag = "";
    const resMatch = combined.match(/\b(\d{3,4}P|2K|4K)\b/i);
    if (resMatch) {
      qualityTag = resMatch[1].toUpperCase();
    } else {
      const numMatch = combined.match(/\b(\d{3,4})\b/);
      if (numMatch) {
        const val = parseInt(numMatch[1], 10);
        if ([144, 240, 360, 480, 720, 1040, 1080, 1440, 2160].includes(val)) {
          qualityTag = `${val}P`;
        }
      }
    }

    badge = qualityTag ? `(VIDEO) ${extension} ${qualityTag}` : `(VIDEO) ${extension}`;
  }

  return trackTitle ? `${trackTitle} • ${badge}` : badge;
}

// Helper to detect generic placeholder titles
function isGenericMediaTitle(str) {
  if (!str || typeof str !== "string") return true;
  const s = str.trim().toUpperCase();
  if (s.length < 2) return true;
  const genericList = [
    "VIDEO", "AUDIO", "IMAGE", "PHOTO", "PICTURE", "MEDIA", "DOWNLOAD", "FILE",
    "MP4", "MP3", "PNG", "JPG", "JPEG", "WEBP", "M4A", "WAV", "FLAC",
    "TIKTOK VIDEO", "TIKTOK CONTENT", "TIKTOK PHOTO",
    "INSTAGRAM VIDEO", "INSTAGRAM PHOTO", "INSTAGRAM MEDIA",
    "SPOTIFY TRACK", "SPOTIFY CONTENT", "SPOTIFY MUSIC", "SPOTIFY SONG",
    "YOUTUBE VIDEO", "YOUTUBE CONTENT", "YOUTUBE AUDIO", "YOUTUBE PLAYLIST",
    "PINTEREST PIN", "PINTEREST", "FACEBOOK MEDIA", "FACEBOOK VIDEO", "THREADS MEDIA",
    "APPLE MUSIC CONTENT", "APPLE MUSIC TRACK", "TRACK", "SONG", "ORIGINAL IMAGE",
    "AUDIOYO", "VIDEOYO", "IMAGEYO"
  ];
  if (genericList.includes(s)) return true;
  if (/^\(VIDEO\)/i.test(s) || /^\(AUDIO\)/i.test(s) || /^\(IMAGE\)/i.test(s)) return true;
  if (/^VIDEO\s*[\(\[]/i.test(s) || /^AUDIO\s*[\(\[]/i.test(s) || /^PHOTO\s*[\(\[]/i.test(s)) return true;
  if (/^VIDEO_\d+/i.test(s) || /^AUDIO_\d+/i.test(s) || /^IMAGE_\d+/i.test(s)) return true;
  return false;
}

// Build human-readable, safe filename
function buildTargetFilename(dlItem, mediaResult, mediaCategory, platform, extension) {
  const isInstagram = (platform && platform.toLowerCase() === "instagram") || (currentPlatform && currentPlatform.toLowerCase() === "instagram") || (mediaResult?.sourceUrl && mediaResult.sourceUrl.includes("instagram.com"));
  let title = (mediaResult?.title || mediaResult?.description || "").trim();
  let itemTitle = (dlItem?.title || dlItem?.name || "").trim();
  let dlType = (dlItem?.type || "").trim();

  let base = "";

  // 1. If item has a specific track name (e.g. in playlists / albums)
  if (itemTitle && !isGenericMediaTitle(itemTitle) && itemTitle !== "Instagram Content") {
    base = itemTitle;
  } else if (dlType && !isGenericMediaTitle(dlType) && (/^\d+[\.\s]/.test(dlType) || dlType.includes(" - "))) {
    base = dlType.replace(/\s*•\s*\(AUDIO\).*$/i, "").replace(/\s*\[(MP3|M4A|Cover|HD|SD)\]/gi, "").trim();
  } else if (title && !isGenericMediaTitle(title)) {
    // 2. Primary: Use the media's actual caption or description
    base = title;
  } else if (mediaResult?.description && !isGenericMediaTitle(mediaResult.description)) {
    base = mediaResult.description;
  } else {
    // 3. Fallback: For Instagram MUST be "Instagram Content", never shortcode
    if (isInstagram) {
      base = "Instagram Content";
    } else {
      const sourceUrl = mediaResult?.sourceUrl || document.getElementById("urlInput")?.value || "";
      const ytMatch = sourceUrl.match(/(?:v=|youtu\.be\/|shorts\/)([\w-]{11})/i);
      const spMatch = sourceUrl.match(/track\/([A-Za-z0-9]+)/i);
      const pinMatch = sourceUrl.match(/pin\/(\d+)/i);
      const author = mediaResult?.author || mediaResult?.username || "";
      const platformName = platform ? (platform.charAt(0).toUpperCase() + platform.slice(1)) : "Media";

      if (ytMatch && ytMatch[1]) {
        base = `YouTube_${ytMatch[1]}`;
      } else if (spMatch && spMatch[1]) {
        base = `Spotify_${spMatch[1]}`;
      } else if (pinMatch && pinMatch[1]) {
        base = `${platformName}_Pin_${pinMatch[1]}`;
      } else if (author) {
        base = `${platformName}_${author}`;
      } else {
        base = `${platformName}_${Date.now()}`;
      }
    }
  }

  // If item has an index or itemIndex, ensure filename includes the unique index
  const itemIdx = dlItem?.itemIndex || dlItem?.index;
  if (itemIdx && !base.endsWith(`_${itemIdx}`) && !base.includes(`(${itemIdx})`) && !base.startsWith(`${itemIdx}.`) && !base.startsWith(`0${itemIdx}.`)) {
    base += `_${itemIdx}`;
  }

  // Strip duplicate extension if present in title
  base = base.replace(/\.(mp4|mp3|png|jpg|jpeg|webp|m4a|wav|webm|mov)$/i, "").trim();

  let cleaned = base.replace(/[\\/:*?"<>|#%&{}$!'@+`=~]/g, "_");
  cleaned = cleaned.replace(/[\s_]+/g, "_").trim();
  cleaned = cleaned.replace(/^_+|_+$/g, "");

  // Apply Filename Format Setting
  if (settings && settings.filenameTemplate === "title-platform" && platform) {
    const platformName = platform.charAt(0).toUpperCase() + platform.slice(1);
    if (!cleaned.toLowerCase().includes(platformName.toLowerCase())) {
      cleaned += `_${platformName}`;
    }
  } else if (settings && settings.filenameTemplate === "title-date") {
    const d = new Date();
    const dStr = `${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, '0')}${String(d.getDate()).padStart(2, '0')}`;
    if (!cleaned.includes(dStr)) {
      cleaned += `_${dStr}`;
    }
  }

  if (cleaned.length > 80) {
    cleaned = cleaned.substring(0, 80).replace(/_+$/, "");
  }

  if (!cleaned || isGenericMediaTitle(cleaned)) {
    if (isInstagram) {
      cleaned = "Instagram_Content" + (itemIdx ? `_${itemIdx}` : "");
    } else {
      cleaned = (platform ? platform.charAt(0).toUpperCase() + platform.slice(1) : "Nimiyo") + "_" + Date.now();
    }
  }

  return cleaned + (extension.startsWith(".") ? extension : `.${extension}`);
}

// Execute Core Single File Download
async function downloadSingleFile(dlItem, mediaResult, batchOptions = null, retryCount = 0) {
  if (!dlItem || !dlItem.url) {
    throw new Error("Invalid download url provided");
  }

  let downloadUrl = dlItem.url;
  let mediaCategory = detectMediaCategory(dlItem, mediaResult);
  let targetExtension = determineExtension(mediaCategory, downloadUrl);
  let sanitizedFilename = buildTargetFilename(dlItem, mediaResult, mediaCategory, currentPlatform, targetExtension);
  let itemTitle = dlItem.title || dlItem.type || mediaResult?.title || sanitizedFilename;

  console.log(`[DOWNLOAD] Processing: "${sanitizedFilename}" (Category: ${mediaCategory}, Batch: ${Boolean(batchOptions)})`);

  // ==========================================
  // STAGE 1: NETWORK DOWNLOAD / RESOLVE
  // ==========================================

  // 1A. Spotify SpotiDown Lazy Resolving
  if (downloadUrl.startsWith("spotidown_resolve:")) {
    console.log("[SPOTIFY RESOLVE] Resolving SpotiDown token for track:", itemTitle);
    const parts = downloadUrl.replace("spotidown_resolve:", "").split("|||");
    const payload = parts[0];
    let cookie = decodeURIComponent(parts[1] || "");

    try {
      if (!cookie) {
        try {
          const homeRes = window.scrapr?.scraperFetch
            ? await window.scrapr.scraperFetch({ url: "https://spotidown.app/", rawResponse: true }, "SpotiDown Cookie")
            : await fetch("https://spotidown.app/");
          const hHeaders = homeRes?.headers || {};
          const sc = hHeaders["set-cookie"] || hHeaders["Set-Cookie"] || "";
          if (sc) cookie = typeof sc === "string" ? sc.split(";")[0] : sc[0].split(";")[0];
        } catch (_) {}
      }

      let data = null;
      if (window.scrapr?.scraperFetch) {
        data = await window.scrapr.scraperFetch({
          url: "https://spotidown.app/action/track",
          method: "POST",
          data: payload,
          headers: {
            "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
            "X-Requested-With": "XMLHttpRequest",
            Referer: "https://spotidown.app/",
            Origin: "https://spotidown.app",
            Cookie: cookie
          },
          rawResponse: true
        }, "SpotiDown Track Resolve");
      } else {
        const res = await fetch("https://spotidown.app/action/track", {
          method: "POST",
          headers: {
            "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
            "X-Requested-With": "XMLHttpRequest",
            Referer: "https://spotidown.app/",
            Origin: "https://spotidown.app",
            Cookie: cookie
          },
          body: payload
        });
        data = await res.json();
      }

      let parsedData = data;
      if (typeof data === "string") {
        try { parsedData = JSON.parse(data); } catch (_) { }
      } else if (data?.data) {
        if (typeof data.data === "string" && (data.data.startsWith("{") || data.data.startsWith("["))) {
          try { parsedData = JSON.parse(data.data); } catch (_) { parsedData = data.data; }
        } else {
          parsedData = data.data;
        }
      }

      const htmlContent = parsedData?.data || (typeof parsedData === "string" ? parsedData : "");
      const parser = new DOMParser();
      const doc = parser.parseFromString(htmlContent, "text/html");

      const resolvedTrackTitle = doc.querySelector("h3")?.textContent?.trim() || "";
      const resolvedArtist = doc.querySelector("p")?.textContent?.trim() || "";
      if (resolvedTrackTitle) {
        const resolvedFull = resolvedArtist ? `${resolvedArtist} - ${resolvedTrackTitle}` : resolvedTrackTitle;
        if (resolvedFull && !isGenericMediaTitle(resolvedFull)) {
          dlItem.title = resolvedFull;
          sanitizedFilename = buildTargetFilename(dlItem, mediaResult, mediaCategory, currentPlatform, targetExtension);
        }
      }

      const allAnchors = Array.from(doc.querySelectorAll("a"));
      let mp3Url = "";
      let fallbackUrl = "";

      for (const a of allAnchors) {
        const href = a.getAttribute("href") || "";
        const text = (a.textContent || "").toLowerCase();

        if (!href.startsWith("http") || href.includes("premium.html") || href === "https://spotidown.app/" || href === "https://spotidown.app") {
          continue;
        }

        if (text.includes("cover") || href.includes("cover")) {
          continue;
        }

        if (text.includes("mp3") || text.includes("download mp3") || text.includes("song") || href.includes("rapid.spotidown.app") || href.includes("/v2?token=")) {
          mp3Url = href;
          break;
        }

        if (!fallbackUrl) {
          fallbackUrl = href;
        }
      }

      const resolvedUrl = mp3Url || fallbackUrl;
      if (!resolvedUrl) {
        throw new Error("[NETWORK DOWNLOAD FAILED] Could not find download link in SpotiDown response.");
      }
      downloadUrl = resolvedUrl;
    } catch (e) {
      console.error("[SPOTIFY RESOLVE] Error resolving SpotiDown token:", e);
      throw new Error(`[NETWORK DOWNLOAD FAILED] Spotify resolve failed: ${e.message}`);
    }
  }

  // 1B. Spotify SoundLoaders Lazy Resolving
  if (downloadUrl.startsWith("soundloaders_resolve:")) {
    console.log("[SPOTIFY RESOLVE] Resolving SoundLoaders token for track:", itemTitle);
    const parts = downloadUrl.replace("soundloaders_resolve:", "").split("|||");
    const dataVal = parts[0];
    const trackToken = parts[1];

    try {
      let resData = null;
      const formBody = new URLSearchParams({ data: dataVal, track_token: trackToken }).toString();

      if (window.scrapr?.scraperFetch) {
        resData = await window.scrapr.scraperFetch({
          url: "https://soundloaders.app/action/tracks",
          method: "POST",
          headers: {
            "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
            "X-Requested-With": "XMLHttpRequest",
            Referer: "https://soundloaders.app/",
            Origin: "https://soundloaders.app"
          },
          data: formBody,
          rawResponse: true
        }, "SoundLoaders Track Resolve");
      } else {
        const res = await fetch("https://soundloaders.app/action/tracks", {
          method: "POST",
          headers: {
            "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
            "X-Requested-With": "XMLHttpRequest",
            Referer: "https://soundloaders.app/",
            Origin: "https://soundloaders.app"
          },
          body: formBody
        });
        resData = await res.json();
      }

      let parsedData = resData;
      if (typeof resData === "string") {
        try { parsedData = JSON.parse(resData); } catch (_) { }
      } else if (resData?.data) {
        parsedData = resData.data;
        if (typeof parsedData === "string") {
          try { parsedData = JSON.parse(parsedData); } catch (_) { }
        }
      }

      const htmlContent = parsedData?.html || (typeof parsedData === "string" ? parsedData : "");
      const parser = new DOMParser();
      const doc = parser.parseFromString(htmlContent, "text/html");

      const resolvedTrackTitle = doc.querySelector("h2, h3, .title")?.textContent?.trim() || "";
      if (resolvedTrackTitle && !isGenericMediaTitle(resolvedTrackTitle)) {
        dlItem.title = resolvedTrackTitle;
        sanitizedFilename = buildTargetFilename(dlItem, mediaResult, mediaCategory, currentPlatform, targetExtension);
      }

      let resolvedUrl = "";
      doc.querySelectorAll("a").forEach(a => {
        const link = a.getAttribute("href");
        if (link && link.startsWith("http") && !link.includes("soundloaders.app")) {
          resolvedUrl = link;
        }
      });

      if (!resolvedUrl) {
        throw new Error("[NETWORK DOWNLOAD FAILED] Could not find download link in SoundLoaders response.");
      }
      downloadUrl = resolvedUrl;
    } catch (e) {
      console.error("[SPOTIFY RESOLVE] Error resolving SoundLoaders token:", e);
      throw new Error(`[NETWORK DOWNLOAD FAILED] SoundLoaders resolve failed: ${e.message}`);
    }
  }

  // 1C. YouTube Playlist Lazy Resolving (ytmp3gg_resolve:videoId|||format|||quality)
  if (downloadUrl.startsWith("ytmp3gg_resolve:")) {
    console.log("[YOUTUBE RESOLVE] Resolving ytmp3gg token:", sanitizedFilename);
    const parts = downloadUrl.replace("ytmp3gg_resolve:", "").split("|||");
    const videoId = parts[0];
    const format = parts[1] || "mp3";
    const quality = parts[2] || "128";
    const targetUrl = `https://www.youtube.com/watch?v=${videoId}`;

    try {
      const headers = {
        Origin: "https://media.ytmp3.gg",
        Referer: "https://media.ytmp3.gg/",
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        Accept: "application/json, text/plain, */*",
        "Content-Type": "application/json"
      };

      let convRes = null;
      if (window.scrapr?.scraperFetch) {
        convRes = await window.scrapr.scraperFetch({
          url: "https://hub.convert1s.com/api/download",
          method: "POST",
          headers,
          data: JSON.stringify({
            url: targetUrl,
            os: "macos",
            output: {
              type: format === "mp4" ? "video" : "audio",
              format,
              quality,
            },
            audio: { bitrate: "128k" },
          }),
          rawResponse: true,
        }, "ytmp3.gg Playlist Resolve");
      } else {
        const res = await fetch("https://hub.convert1s.com/api/download", {
          method: "POST",
          headers,
          body: JSON.stringify({
            url: targetUrl,
            os: "macos",
            output: {
              type: format === "mp4" ? "video" : "audio",
              format,
              quality,
            },
            audio: { bitrate: "128k" },
          }),
        });
        convRes = { data: await res.json() };
      }

      let conv = convRes?.data;
      if (typeof conv === "string") {
        try { conv = JSON.parse(conv); } catch (_) { }
      }

      if (!conv || conv.error || !conv.statusUrl) {
        throw new Error(conv?.message || conv?.error || "Conversion failed on ytmp3.gg");
      }

      let resolvedDlUrl = null;
      let attempts = 0;
      while (!resolvedDlUrl && attempts < 30) {
        await new Promise((r) => setTimeout(r, 1500));
        let pollData = null;
        if (window.scrapr?.scraperFetch) {
          pollData = await window.scrapr.scraperFetch({
            url: conv.statusUrl,
            headers,
          }, "ytmp3.gg Status");
        } else {
          const pRes = await fetch(conv.statusUrl, { headers });
          pollData = await pRes.json();
        }
        attempts++;
        if (pollData && pollData.status === "completed" && pollData.downloadUrl) {
          resolvedDlUrl = pollData.downloadUrl;
          break;
        }
        if (pollData && (pollData.status === "error" || pollData.status === "failed")) {
          break;
        }
      }

      if (!resolvedDlUrl) {
        throw new Error("[NETWORK DOWNLOAD FAILED] Conversion timed out or failed on ytmp3.gg.");
      }
      downloadUrl = resolvedDlUrl;
    } catch (e) {
      console.error("[YOUTUBE RESOLVE] Error resolving ytmp3gg token:", e);
      throw new Error(`[NETWORK DOWNLOAD FAILED] YouTube resolve failed: ${e.message}`);
    }
  }

  // 1D. Apple Music Lazy Resolving (applemusic_resolve:payloadStr)
  if (downloadUrl.startsWith("applemusic_resolve:")) {
    console.log("[APPLE MUSIC RESOLVE] Resolving token:", sanitizedFilename);
    const payloadStr = downloadUrl.replace("applemusic_resolve:", "");

    try {
      let r4Data = null;
      const headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Content-Type": "application/x-www-form-urlencoded",
        Accept: "application/json, text/javascript, */*; q=0.01",
        "X-Requested-With": "XMLHttpRequest",
        Origin: "https://aplmate.com",
        Referer: "https://aplmate.com/"
      };

      if (window.scrapr?.scraperFetch) {
        r4Data = await window.scrapr.scraperFetch({
          url: "https://aplmate.com/action/track",
          method: "POST",
          data: payloadStr,
          headers,
          rawResponse: true,
        }, "Aplmate Track Resolve");
      } else {
        const res = await fetch("https://aplmate.com/action/track", {
          method: "POST",
          headers,
          body: payloadStr,
        });
        r4Data = { data: await res.text() };
      }

      const trackHtml = r4Data?.data || r4Data;
      const parser = new DOMParser();
      const doc3 = parser.parseFromString(typeof trackHtml === "string" ? trackHtml : JSON.stringify(trackHtml), "text/html");

      let resolvedUrl = "";
      doc3.querySelectorAll("a").forEach((a) => {
        const href = a.getAttribute("href");
        const text = (a.textContent || "").trim();
        if (href && (href.includes("/dl?token=") || a.classList.contains("abutton"))) {
          if (href.includes("ko-fi.com") || href.includes("premium.html")) return;
          if (text.toLowerCase().includes("another song")) return;
          resolvedUrl = href.startsWith("http") ? href : "https://aplmate.com" + href;
        }
      });

      if (!resolvedUrl) {
        throw new Error("[NETWORK DOWNLOAD FAILED] Could not find download link in Aplmate response.");
      }
      downloadUrl = resolvedUrl;
    } catch (e) {
      console.error("[APPLE MUSIC RESOLVE] Error resolving token:", e);
      throw new Error(`[NETWORK DOWNLOAD FAILED] Apple Music resolve failed: ${e.message}`);
    }
  }

  // Update UI Progress Info
  const updateProgressPercent = (percent) => {
    let displayPercent = percent;
    if (batchOptions) {
      const base = ((batchOptions.current - 1) / batchOptions.total) * 100;
      const slice = (1 / batchOptions.total) * percent;
      displayPercent = Math.min(Math.round(base + slice), 99);
      showBalloonProgress(batchOptions.current, batchOptions.total, sanitizedFilename, displayPercent, false);
    }
  };

  // 1C. Anti-403 Download Headers
  const useHeaderSpoofing = settings.headerSpoofing !== false;
  const downloadHeaders = useHeaderSpoofing ? {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
    "Accept-Language": "en-US,en;q=0.9,id;q=0.8",
    "Referer": (downloadUrl.includes("tiktok") || downloadUrl.includes("snaptik") || downloadUrl.includes("tikwm")) ? "https://www.tiktok.com/" : ((downloadUrl.includes("instagram") || downloadUrl.includes("cdninstagram")) ? "https://www.instagram.com/" : ""),
    ...(dlItem?.headers || {})
  } : {
    ...(dlItem?.headers || {})
  };

  const isNative = !!(window.Capacitor?.isNativePlatform && window.Capacitor.isNativePlatform());
  const MediaSaver = window.Capacitor?.Plugins?.MediaSaver;
  let savedFileUri = null;

  if (isNative && MediaSaver?.downloadFile) {
    // ==========================================
    // STAGE 2A: HIGH-PERFORMANCE NATIVE STREAM
    // Streams direct to MediaStore.Downloads (Downloads/Nimiyo/ImageYo, AudioYo, VideoYo)
    // ==========================================
    let currentPercent = 10;
    const progressInterval = setInterval(() => {
      if (currentPercent < 90) {
        currentPercent += Math.floor(Math.random() * 6) + 3;
        if (currentPercent > 90) currentPercent = 90;
        updateProgressPercent(currentPercent);
      }
    }, 250);

    try {
      updateProgressPercent(15);
      const res = await MediaSaver.downloadFile({
        url: downloadUrl,
        fileName: sanitizedFilename,
        fileType: mediaCategory,
        overwriteMode: settings.overwriteMode || "rename",
        headers: downloadHeaders
      });

      clearInterval(progressInterval);
      if (res?.skipped) {
        savedFileUri = res.uri;
        if (res.fileName) sanitizedFilename = res.fileName;
        updateProgressPercent(100);
        showToast(getTranslation("toastSkippedDuplicate") || `Berkas sudah ada (dilewati): ${sanitizedFilename}`, "info");
        return;
      }
      if (!res || !res.success || !res.uri) {
        throw new Error(res?.error || "MediaSaver direct download failed");
      }
      savedFileUri = res.uri;
      if (res?.fileName) {
        sanitizedFilename = res.fileName;
      }
      updateProgressPercent(100);
    } catch (nativeDlErr) {
      clearInterval(progressInterval);
      console.warn("[DOWNLOAD] Direct MediaSaver.downloadFile failed, falling back to cached download:", nativeDlErr);
      if (downloadCancelled) throw new Error("Aborted");

      // SECONDARY FALLBACK: Cache sandbox download + MediaSaver copy
      const capDir = "CACHE";
      const targetRelativePath = sanitizedFilename;

      try {
        await Filesystem.downloadFile({
          url: downloadUrl,
          path: targetRelativePath,
          directory: capDir,
          headers: downloadHeaders
        });
      } catch (fsDlErr) {
        const CapacitorHttp = window.Capacitor?.Plugins?.CapacitorHttp;
        let base64data = "";
        if (CapacitorHttp) {
          const httpRes = await CapacitorHttp.request({
            method: "GET",
            url: downloadUrl,
            headers: downloadHeaders,
            responseType: "base64"
          });
          if (httpRes.status && httpRes.status >= 400) throw new Error(`HTTP ${httpRes.status}`);
          base64data = httpRes.data;
        } else {
          const response = await fetch(downloadUrl, { headers: downloadHeaders });
          if (!response.ok) throw new Error(`HTTP ${response.status}`);
          const blob = await response.blob();
          base64data = await new Promise((resolve, reject) => {
            const reader = new FileReader();
            reader.onloadend = () => resolve(reader.result.split(',')[1]);
            reader.onerror = () => reject(new Error("Base64 conversion failed"));
            reader.readAsDataURL(blob);
          });
        }

        await Filesystem.writeFile({
          path: targetRelativePath,
          data: base64data,
          directory: capDir
        });
      }

      const uriResult = await Filesystem.getUri({
        path: targetRelativePath,
        directory: capDir
      });

      if (!uriResult?.uri) {
        throw new Error("[FILE WRITE FAILED] Could not retrieve cached file URI.");
      }

      try {
        if (MediaSaver?.saveToPublicStorage) {
          const mediaSaverRes = await MediaSaver.saveToPublicStorage({
            filePath: uriResult.uri,
            fileName: sanitizedFilename,
            fileType: mediaCategory,
            overwriteMode: settings.overwriteMode || "rename"
          });
          if (mediaSaverRes?.skipped) {
            savedFileUri = mediaSaverRes.uri;
            if (mediaSaverRes.fileName) sanitizedFilename = mediaSaverRes.fileName;
            updateProgressPercent(100);
            showToast(getTranslation("toastSkippedDuplicate") || `Berkas sudah ada (dilewati): ${sanitizedFilename}`, "info");
            return;
          }
          if (!mediaSaverRes || !mediaSaverRes.success || !mediaSaverRes.uri) {
            throw new Error("[PUBLIC STORAGE FAILED] MediaSaver failed to copy file to Downloads/Nimiyo/ storage.");
          }
          savedFileUri = mediaSaverRes.uri;
          if (mediaSaverRes?.fileName) {
            sanitizedFilename = mediaSaverRes.fileName;
          }
        } else {
          savedFileUri = uriResult.uri;
        }
      } finally {
        try {
          await Filesystem.deleteFile({
            path: targetRelativePath,
            directory: capDir
          });
        } catch (_) { }
      }

      updateProgressPercent(100);
    }
  } else if (Filesystem && isNative) {
    const capDir = "CACHE";
    const targetRelativePath = sanitizedFilename;
    await Filesystem.downloadFile({
      url: downloadUrl,
      path: targetRelativePath,
      directory: capDir,
      headers: downloadHeaders
    });
    const uriResult = await Filesystem.getUri({
      path: targetRelativePath,
      directory: capDir
    });
    savedFileUri = uriResult?.uri;
    updateProgressPercent(100);
  } else {
    // Browser fallback
    const response = await fetch(downloadUrl, { headers: downloadHeaders });
    if (!response.ok) throw new Error(`HTTP status ${response.status}`);
    const blob = await response.blob();
    const blobUrl = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = blobUrl;
    a.download = sanitizedFilename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(blobUrl);
    updateProgressPercent(100);
  }

  // Extract accurate track-specific title & thumbnail for history: MUST USE ACTUAL FILE NAME OR TITLE
  let historyTitle = sanitizedFilename.replace(/\.(mp4|mp3|png|jpg|jpeg|webp|m4a|wav|webm|mov)$/i, "").replace(/_+/g, " ").trim();
  if (mediaResult?.title && !isGenericMediaTitle(mediaResult.title)) {
    historyTitle = mediaResult.title;
  }
  if (dlItem?.title && !isGenericMediaTitle(dlItem.title)) {
    historyTitle = dlItem.title;
  }
  if (!historyTitle || isGenericMediaTitle(historyTitle)) {
    historyTitle = sanitizedFilename.replace(/\.(mp4|mp3|png|jpg|jpeg|webp|m4a|wav|webm|mov)$/i, "").replace(/_+/g, " ").trim();
  }

  const isImageMedia = mediaCategory === "image" || dlItem.type?.toUpperCase().includes("PHOTO") || dlItem.type?.toUpperCase().includes("IMAGE") || sanitizedFilename.match(/\.(png|jpg|jpeg|webp)$/i);
  let historyThumb = dlItem.thumbnail || dlItem.cover;

  // For downloaded images, prioritize the exact downloaded local file URI!
  if (isImageMedia && savedFileUri && window.Capacitor?.convertFileSrc) {
    historyThumb = window.Capacitor.convertFileSrc(savedFileUri);
  } else if (!historyThumb && isImageMedia && dlItem.url && dlItem.url.startsWith("http")) {
    historyThumb = dlItem.url;
  }
  if (!historyThumb) {
    historyThumb = mediaResult?.thumbnail || "nimiyo_icon.webp";
  }

  // Add immediately to History on single file finish
  addToHistory({
    title: historyTitle,
    thumbnail: historyThumb,
    originalUrl: document.getElementById("urlInput")?.value || "",
    platform: currentPlatform,
    mediaType: mediaCategory,
    filename: sanitizedFilename,
    timestamp: Date.now()
  });

  // Auto-refresh Music Library if audio was downloaded
  if (mediaCategory === "audio" || sanitizedFilename.match(/\.(mp3|m4a|wav|flac|ogg|opus|aac|wma)$/i)) {
    if (window.nimiyoMusicPlayer && typeof window.nimiyoMusicPlayer.refreshLibrary === "function") {
      window.nimiyoMusicPlayer.refreshLibrary();
    }
  }

  return { success: true, filename: sanitizedFilename };
}

let balloonPillTimer = null;

// Show or Update Floating Download Balloon
function showBalloonProgress(current, total, filename = "", percent = 0) {
  const balloon = document.getElementById("downloadBalloon");
  const circle = document.getElementById("balloonProgressCircle");
  const sheetCounter = document.getElementById("balloonSheetCounter");
  const sheetItem = document.getElementById("balloonSheetItem");
  const sheetProgressBar = document.getElementById("balloonSheetProgressBar");

  if (!balloon) return;

  balloon.classList.remove("hidden", "hiding");

  // Update SVG Circular Ring (Circumference = 2 * PI * 23 ≈ 144.5)
  const circumference = 144.5;
  const validPercent = Math.max(0, Math.min(100, percent));
  const offset = circumference - (circumference * (validPercent / 100));
  if (circle) {
    circle.style.strokeDashoffset = offset;
  }

  // Update sheet flyout details
  if (sheetCounter) sheetCounter.innerText = `${current}/${total}`;
  if (sheetItem) sheetItem.innerText = filename || `Item ${current}`;
  if (sheetProgressBar) sheetProgressBar.style.width = `${validPercent}%`;
}

// Hide Floating Download Balloon
function hideBalloonProgress() {
  const balloon = document.getElementById("downloadBalloon");
  if (!balloon) return;

  const sheet = document.getElementById("balloonSheet");
  if (sheet) sheet.classList.add("hidden");

  balloon.classList.add("hiding");
  setTimeout(() => {
    balloon.classList.add("hidden");
    balloon.classList.remove("hiding");
    const circle = document.getElementById("balloonProgressCircle");
    if (circle) circle.style.strokeDashoffset = 144.5;
  }, 250);
}

// Native Android System Notification
async function updateSystemDownloadNotification(title, message, progress = 0, max = 100, isCompleted = false) {
  try {
    const MediaSaver = window.Capacitor?.Plugins?.MediaSaver;
    if (MediaSaver && typeof MediaSaver.showSystemNotification === "function") {
      await MediaSaver.showSystemNotification({
        title: title || "Nimiyo Downloader",
        message: message || "Mengunduh...",
        progress,
        max,
        isCompleted
      });
    }
  } catch (e) {
    console.warn("System notification update failed:", e);
  }
}

async function clearSystemDownloadNotification() {
  try {
    const MediaSaver = window.Capacitor?.Plugins?.MediaSaver;
    if (MediaSaver && typeof MediaSaver.clearSystemNotification === "function") {
      await MediaSaver.clearSystemNotification();
    }
  } catch (e) {
    console.warn("Clear system notification failed:", e);
  }
}

// ==========================================
// UNIFIED DOWNLOAD QUEUE & BALLOON MANAGER
// ==========================================
let activeDownloadQueue = [];
let isProcessingQueue = false;
let completedQueueCount = 0;
let totalSessionCount = 0;

// Add items to unified download queue
function enqueueDownloads(items, mediaResult) {
  if (!items || !items.length) return;

  // Apply Batch Photo Mode setting: 'first' = First Photo Only
  let targetItems = items;
  if (settings.batchPhotoMode === 'first' && items.length > 1) {
    const isPhotoBatch = items.every(it => {
      const cat = detectMediaCategory(it, mediaResult);
      return cat === "image" || it.type?.toLowerCase().includes("photo") || it.type?.toLowerCase().includes("image");
    });
    if (isPhotoBatch) {
      targetItems = [items[0]];
    }
  }

  const activeExisting = activeDownloadQueue.filter(t => t.status === 'downloading' || t.status === 'pending');
  if (activeExisting.length === 0) {
    totalSessionCount = 0;
  }
  totalSessionCount += targetItems.length;

  targetItems.forEach((dlItem, idx) => {
    if (targetItems.length > 1 && !dlItem.itemIndex && !dlItem.index) {
      dlItem.itemIndex = idx + 1;
    }
    const mediaCategory = detectMediaCategory(dlItem, mediaResult);
    const targetExtension = determineExtension(mediaCategory, dlItem.url);
    const sanitizedFilename = buildTargetFilename(dlItem, mediaResult, mediaCategory, currentPlatform, targetExtension);

    let historyTitle = sanitizedFilename.replace(/\.(mp4|mp3|png|jpg|jpeg|webp|m4a|wav|webm|mov)$/i, "").replace(/_+/g, " ").trim();
    if (mediaResult?.title && !isGenericMediaTitle(mediaResult.title)) {
      historyTitle = mediaResult.title;
    }
    if (dlItem?.title && !isGenericMediaTitle(dlItem.title)) {
      historyTitle = dlItem.title;
    }
    if (!historyTitle || isGenericMediaTitle(historyTitle)) {
      historyTitle = sanitizedFilename.replace(/\.(mp4|mp3|png|jpg|jpeg|webp|m4a|wav|webm|mov)$/i, "").replace(/_+/g, " ").trim();
    }

    const isImageMedia = mediaCategory === "image" || dlItem.type?.toUpperCase().includes("PHOTO") || dlItem.type?.toUpperCase().includes("IMAGE");
    let historyThumb = dlItem.thumbnail || dlItem.cover;
    if (!historyThumb && isImageMedia && dlItem.url && dlItem.url.startsWith("http")) {
      historyThumb = dlItem.url;
    }
    if (!historyThumb) {
      historyThumb = mediaResult?.thumbnail || "nimiyo_icon.webp";
    }

    const taskId = "dl_" + Date.now() + "_" + Math.random().toString(36).substr(2, 6);
    activeDownloadQueue.push({
      id: taskId,
      dlItem,
      mediaResult,
      filename: sanitizedFilename,
      title: historyTitle,
      thumbnail: historyThumb,
      status: 'pending', // 'pending', 'downloading', 'completed', 'cancelled', 'error'
      progress: 0,
      isCancelled: false
    });
  });

  // Tampilkan toast hanya saat penambahan unduhan SEKALI
  if (targetItems.length === 1) {
    const singleTitle = activeDownloadQueue[activeDownloadQueue.length - 1].title;
    showToast(getTranslation("toastDownloadingSingle", { title: singleTitle }), "info");
  } else {
    showToast(getTranslation("toastDownloadingMulti", { count: targetItems.length }), "info");
  }

  updateBalloonQueueUI();
  processDownloadQueue();
}

// Update Balloon UI (Ring, Counter, and Scrollable Queue List in Sheet)
function updateBalloonQueueUI() {
  const balloon = document.getElementById("downloadBalloon");
  const circle = document.getElementById("balloonProgressCircle");
  const sheetCounter = document.getElementById("balloonSheetCounter");
  const queueList = document.getElementById("balloonQueueList");

  if (!balloon) return;

  const activeTasks = activeDownloadQueue.filter(t => t.status === 'downloading' || t.status === 'pending');
  const remainingActiveCount = activeTasks.length;

  if (remainingActiveCount === 0 && activeDownloadQueue.length === 0) {
    hideBalloonProgress();
    return;
  }

  balloon.classList.remove("hidden", "hiding");

  // Progress melingkar mengikuti berkurangnya active task (semakin sedikit aktif, semakin selesai)
  const currentCompleted = Math.max(0, totalSessionCount - remainingActiveCount);
  const currentTask = activeTasks.find(t => t.status === 'downloading');
  const currentTaskProg = currentTask ? (currentTask.progress / 100) : 0;

  let overallPercent = 0;
  if (totalSessionCount > 0) {
    overallPercent = Math.min(100, Math.round(((currentCompleted + currentTaskProg) / totalSessionCount) * 100));
  } else {
    overallPercent = 50;
  }

  const circumference = 150.8;
  const offset = circumference - (circumference * (overallPercent / 100));
  if (circle) {
    circle.style.strokeDashoffset = offset;
  }

  if (sheetCounter) {
    sheetCounter.innerText = `${currentCompleted}/${totalSessionCount || remainingActiveCount}`;
  }

  // Render Queue List inside Sheet
  if (queueList) {
    queueList.innerHTML = "";
    activeDownloadQueue.forEach(task => {
      if (task.status === 'cancelled') return;

      const itemEl = document.createElement("div");
      itemEl.className = "balloon-queue-item";
      itemEl.id = `task_row_${task.id}`;

      let statusLabel = getTranslation("queuePending");
      if (task.status === 'downloading') {
        statusLabel = getTranslation("queueDownloading", { percent: task.progress });
      } else if (task.status === 'completed') {
        statusLabel = getTranslation("queueCompleted");
      }

      itemEl.innerHTML = `
        <img src="${task.thumbnail || 'nimiyo_icon.webp'}" alt="Thumb" class="queue-thumb">
        <div class="queue-info">
          <div class="queue-title" title="${task.title}">${task.title}</div>
          <div class="queue-progress-bar-bg">
            <div class="queue-progress-bar-fill" style="width: ${task.progress}%;"></div>
          </div>
          <div class="queue-status-text">${statusLabel}</div>
        </div>
        <button class="queue-cancel-btn" title="${getTranslation('queueCancelTitle')}" data-id="${task.id}">
          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
            <line x1="18" y1="6" x2="6" y2="18"></line>
            <line x1="6" y1="6" x2="18" y2="18"></line>
          </svg>
        </button>
      `;

      // Cancel single task handler
      const cancelBtn = itemEl.querySelector(".queue-cancel-btn");
      if (cancelBtn) {
        cancelBtn.addEventListener("click", (e) => {
          e.stopPropagation();
          triggerHaptic();
          cancelSingleTask(task.id);
        });
      }

      queueList.appendChild(itemEl);
    });
  }
}

// Cancel Single Task from Queue
function cancelSingleTask(taskId) {
  const task = activeDownloadQueue.find(t => t.id === taskId);
  if (task) {
    task.isCancelled = true;
    task.status = 'cancelled';
    activeDownloadQueue = activeDownloadQueue.filter(t => t.id !== taskId);
    updateBalloonQueueUI();
    showToast(getTranslation("toastDownloadCancelledItem", { title: task.title }), "error");

    if (activeDownloadQueue.length === 0) {
      hideBalloonProgress();
      clearSystemDownloadNotification();
    }
  }
}

// Cancel All Tasks in Queue
function cancelAllQueue() {
  activeDownloadQueue.forEach(task => {
    task.isCancelled = true;
    task.status = 'cancelled';
  });
  activeDownloadQueue = [];
  hideBalloonProgress();
  clearSystemDownloadNotification();
  showToast(getTranslation("toastDownloadCancelled"), "error");
}

// Process Queue Worker
async function processDownloadQueue() {
  if (isProcessingQueue) return;
  isProcessingQueue = true;

  // 1. Download via Wi-Fi Only setting check
  if (settings.wifiOnly && Network && typeof Network.getStatus === "function") {
    try {
      const netStatus = await Network.getStatus();
      if (netStatus && netStatus.connected && netStatus.connectionType !== 'wifi') {
        showToast(getTranslation("toastWifiOnlyPaused") || "Unduhan ditangguhkan: Pengaturan Hanya Lewat Wi-Fi aktif", "error");
        isProcessingQueue = false;
        return;
      }
    } catch (e) {
      console.warn("[NETWORK] Wi-Fi check error:", e);
    }
  }

  const maxWorkers = Math.max(1, parseInt(settings.concurrentDl, 10) || 1);

  async function runWorker() {
    while (true) {
      const nextTask = activeDownloadQueue.find(t => t.status === 'pending' && !t.isCancelled);
      if (!nextTask) break;

      nextTask.status = 'downloading';
      const isBatch = totalSessionCount > 1;
      const taskIndex = completedQueueCount + 1;
      const initialMsg = isBatch
        ? `Mengunduh (${taskIndex}/${totalSessionCount}): ${nextTask.filename}`
        : `Mengunduh: ${nextTask.filename}`;

      updateSystemDownloadNotification("Nimiyo Downloader", initialMsg, 10, 100, false);

      const maxAttempts = (settings.autoRetry && settings.maxRetry) ? Math.max(1, parseInt(settings.maxRetry, 10) || 1) : 1;
      let downloadSuccess = false;
      let lastErr = null;

      for (let attempt = 1; attempt <= maxAttempts; attempt++) {
        if (nextTask.isCancelled) break;
        try {
          if (attempt > 1) {
            updateSystemDownloadNotification(
              "Nimiyo Downloader",
              `Mencoba ulang (${attempt}/${maxAttempts}): ${nextTask.filename}`,
              10,
              100,
              false
            );
          }
          await downloadSingleFile(
            nextTask.dlItem,
            nextTask.mediaResult,
            null,
            0,
            (p) => {
              nextTask.progress = p;
              updateBalloonQueueUI();
              const progMsg = isBatch
                ? `Mengunduh (${taskIndex}/${totalSessionCount}): ${nextTask.filename} (${p}%)`
                : `Mengunduh: ${nextTask.filename} (${p}%)`;
              updateSystemDownloadNotification(
                "Nimiyo Downloader",
                progMsg,
                p,
                100,
                false
              );
            },
            nextTask
          );

          if (nextTask.isCancelled) {
            throw new Error("Aborted");
          }

          downloadSuccess = true;
          break;
        } catch (err) {
          lastErr = err;
          if (nextTask.isCancelled) break;
          if (attempt < maxAttempts) {
            await new Promise(r => setTimeout(r, 1500));
          }
        }
      }

      if (downloadSuccess) {
        nextTask.status = 'completed';
        nextTask.progress = 100;
        completedQueueCount++;
        updateBalloonQueueUI();

        const finishMsg = isBatch
          ? `Selesai (${completedQueueCount}/${totalSessionCount}): ${nextTask.filename}`
          : `Selesai: ${nextTask.filename}`;

        updateSystemDownloadNotification(
          "Nimiyo Downloader",
          finishMsg,
          100,
          100,
          completedQueueCount >= totalSessionCount
        );

        setTimeout(() => {
          activeDownloadQueue = activeDownloadQueue.filter(t => t.id !== nextTask.id);
          updateBalloonQueueUI();
        }, 700);
      } else {
        console.error(`[QUEUE ERROR] Task ${nextTask.title} failed:`, lastErr);
        nextTask.status = 'error';
        activeDownloadQueue = activeDownloadQueue.filter(t => t.id !== nextTask.id);
        updateBalloonQueueUI();

        // Release ongoing lock on notification drawer so it doesn't get stuck
        updateSystemDownloadNotification(
          "Nimiyo Downloader",
          `Gagal mengunduh: ${nextTask.filename}`,
          0,
          0,
          true
        );

        setTimeout(() => {
          const hasRunning = activeDownloadQueue.some(t => t.status === 'downloading' || t.status === 'pending');
          if (!hasRunning) {
            clearSystemDownloadNotification();
          }
        }, 3000);

        // Auto-switch server on download failure and re-analyze
        const platform = currentPlatform || nextTask.mediaResult?.platform || nextTask.dlItem?.platform;
        const scrapers = platform ? (fallbackChains[platform] || []) : [];
        const currentUrl = document.getElementById("urlInput")?.value || nextTask.mediaResult?.url;

        if (scrapers.length > 1 && currentUrl) {
          const currentIdx = scrapers.indexOf(activeScraperMethod);
          const nextScraper = scrapers[(currentIdx + 1) % scrapers.length];
          const formatted = nextScraper.charAt(0).toUpperCase() + nextScraper.slice(1);
          showToast(getTranslation("toastDownloadFailSwitchServer", { server: formatted }), "error");

          setTimeout(() => {
            analyzeLink(currentUrl, nextScraper);
          }, 800);
        } else {
          showToast(getTranslation("toastDownloadFailManualServer"), "error");
        }
      }
    }
  }

  const workerPromises = [];
  for (let i = 0; i < maxWorkers; i++) {
    workerPromises.push(runWorker());
  }
  await Promise.all(workerPromises);

  isProcessingQueue = false;

  // If queue is fully drained
  const remainingActive = activeDownloadQueue.filter(t => t.status === 'downloading' || t.status === 'pending');
  if (remainingActive.length === 0) {
    hideBalloonProgress();
    if (completedQueueCount > 0) {
      updateSystemDownloadNotification(
        "Nimiyo Downloader",
        getTranslation("toastBatchCompleted", { count: completedQueueCount }),
        100,
        100,
        true
      );
      playCompletionSound();
      triggerHaptic();
      showToast(getTranslation("toastBatchCompleted", { count: completedQueueCount }), "success");
      completedQueueCount = 0;
    } else {
      // If 0 items succeeded (all failed or cancelled), ensure notification is cleared
      setTimeout(() => {
        clearSystemDownloadNotification();
      }, 3000);
    }
  }
}

// Show or Update Floating Download Balloon
function showBalloonProgress(current, total, filename = "", percent = 0) {
  updateBalloonQueueUI();
}

// Hide Floating Download Balloon
function hideBalloonProgress() {
  const balloon = document.getElementById("downloadBalloon");
  if (!balloon) return;

  const sheet = document.getElementById("balloonSheet");
  if (sheet) sheet.classList.add("hidden");

  balloon.classList.add("hiding");
  setTimeout(() => {
    balloon.classList.add("hidden");
    balloon.classList.remove("hiding");
    const circle = document.getElementById("balloonProgressCircle");
    if (circle) circle.style.strokeDashoffset = 150.8;
  }, 250);
}

// Compatibility wrapper for batch / single download calls
async function triggerBatchDownload(items, mediaResult) {
  enqueueDownloads(items, mediaResult);
}

async function triggerDownload(dlItem, mediaResult) {
  enqueueDownloads([dlItem], mediaResult);
}

// Cancel Download
function cancelDownload() {
  downloadCancelled = true;
  if (activeDownloadXHR) {
    activeDownloadXHR.abort();
    activeDownloadXHR = null;
  }
  if (downloadProgressInterval) {
    clearInterval(downloadProgressInterval);
  }
  document.getElementById("downloadOverlay").classList.add("hidden");
  showToast(getTranslation("toastDownloadCancelled"), "error");
}

// Add Item to History
function addToHistory(item) {
  if (settings.incognito) {
    return; // Incognito mode enabled: do not record to history
  }

  localHistory.unshift(item); // insert at beginning

  // History retention limit check
  const maxLimit = settings.historyLimit === 'unlimited' ? 1000 : parseInt(settings.historyLimit, 10);
  if (localHistory.length > maxLimit) {
    localHistory = localHistory.slice(0, maxLimit);
  }

  saveHistory();
  updateStorageSizeDisplay();

  // If video on native, proactively generate/cache local frame thumbnail in background
  const isVideoItem = item.mediaType === "video" || item.filename?.match(/\.(mp4|webm|mov|mkv)$/i);
  if (isVideoItem && window.Capacitor?.isNativePlatform() && window.Capacitor?.Plugins?.MediaSaver) {
    window.Capacitor.Plugins.MediaSaver.getVideoThumbnail({
      fileName: item.filename,
      subFolder: "VideoYo"
    }).then(res => {
      if (res?.filePath) {
        item.localThumbnail = res.filePath;
        saveHistory();
        const img = document.querySelector(`.history-thumbnail[data-filename="${item.filename}"]`);
        if (img && window.Capacitor?.convertFileSrc) {
          img.src = window.Capacitor.convertFileSrc(res.filePath);
        }
      }
    }).catch(() => {});
  }

  // Jika modal riwayat terbuka, langsung render item baru tanpa menunggu sisa download selesai
  const historyModal = document.getElementById("historyModal");
  if (historyModal && !historyModal.classList.contains("hidden")) {
    renderHistory();
  }
}

// Open Destination Folder for History item directly in Android File Manager
async function openHistoryFolder(item) {
  const isAudio = item.mediaType === "audio" || item.filename?.endsWith(".mp3") || item.filename?.endsWith(".m4a");
  const isImage = item.mediaType === "image" || item.filename?.match(/\.(png|jpg|jpeg|webp)$/i);
  const subFolder = isImage ? "ImageYo" : isAudio ? "AudioYo" : "VideoYo";

  const MediaSaver = window.Capacitor?.Plugins?.MediaSaver;
  if (MediaSaver && window.Capacitor?.isNativePlatform()) {
    try {
      await MediaSaver.openDirectory({
        subFolder: subFolder,
        fileName: item.filename
      });
      showToast(getTranslation("toastOpeningFolder", { folder: subFolder }), "info");
      return;
    } catch (e) {
      console.warn("MediaSaver.openDirectory failed:", e);
    }
  }

  showToast(getTranslation("toastLocation", { folder: subFolder, filename: item.filename }), "info");
}

// Play Media from History directly in App or Native Intent
async function playMediaFromHistory(item) {
  triggerHaptic();
  const isAudio = item.mediaType === "audio" || item.filename?.endsWith(".mp3") || item.filename?.endsWith(".m4a");
  const isImage = item.mediaType === "image" || item.filename?.match(/\.(png|jpg|jpeg|webp)$/i);
  const isVideo = !isAudio && !isImage;
  const subFolder = isImage ? "ImageYo" : isAudio ? "AudioYo" : "VideoYo";
  const cleanDisplayTitle = getCleanHistoryDisplayTitle(item);

  const MediaSaver = window.Capacitor?.Plugins?.MediaSaver;

  // 1. For Videos on Native Android: Open NIMIYO's dedicated in-app Video Player directly!
  if (isVideo && MediaSaver && window.Capacitor?.isNativePlatform()) {
    try {
      await MediaSaver.playVideoInApp({
        fileName: item.filename,
        subFolder: subFolder,
        title: cleanDisplayTitle,
        autoPlay: settings.autoPlay !== false,
        autoLoop: settings.autoLoop !== false
      });
      const truncatedTitle = cleanDisplayTitle.length > 20 ? cleanDisplayTitle.substring(0, 16) + "..." : cleanDisplayTitle;
      showToast(getTranslation("toastPlaying", { title: truncatedTitle }), "info");
      return;
    } catch (e) {
      console.warn("MediaSaver.playVideoInApp failed, falling back to modal:", e);
    }
  }

  // 1b. For Images on Native Android: Open NIMIYO's dedicated in-app Full-Screen Photo Viewer directly!
  if (isImage && MediaSaver && window.Capacitor?.isNativePlatform()) {
    try {
      await MediaSaver.previewImageInApp({
        fileName: item.filename,
        subFolder: subFolder,
        title: cleanDisplayTitle
      });
      const truncatedTitle = cleanDisplayTitle.length > 20 ? cleanDisplayTitle.substring(0, 16) + "..." : cleanDisplayTitle;
      showToast(`Membuka : ${truncatedTitle}`, "info");
      return;
    } catch (e) {
      console.warn("MediaSaver.previewImageInApp failed, falling back to modal:", e);
    }
  }

  const modal = document.getElementById("mediaPlayerModal");
  const container = document.getElementById("mediaPlayerContainer");
  const title = document.getElementById("mediaPlayerTitle");

  let webUrl = "";

  if (MediaSaver && window.Capacitor?.isNativePlatform()) {
    try {
      const res = await MediaSaver.getMediaData({
        fileName: item.filename,
        subFolder: subFolder,
        fileType: item.mediaType
      });

      if (res?.filePath && window.Capacitor?.convertFileSrc) {
        webUrl = window.Capacitor.convertFileSrc(res.filePath);
      } else if (res?.fileUri && window.Capacitor?.convertFileSrc) {
        webUrl = window.Capacitor.convertFileSrc(res.fileUri);
      } else if (res?.base64) {
        const mime = isAudio ? "audio/mpeg" : isImage ? "image/jpeg" : "video/mp4";
        webUrl = `data:${mime};base64,${res.base64}`;
      } else if (res?.uri && !res.uri.startsWith("content://") && window.Capacitor?.convertFileSrc) {
        webUrl = window.Capacitor.convertFileSrc(res.uri);
      }
    } catch (e) {
      console.warn("MediaSaver.getMediaData failed:", e);
    }
  }

  // Filesystem fallback if needed
  if (!webUrl && Filesystem && window.Capacitor?.isNativePlatform()) {
    try {
      let fileUri = "";
      const pathsToTry = [
        `Nimiyo/${subFolder}/${item.filename}`,
        `${subFolder}/${item.filename}`,
        `Nimiyo/${item.filename}`,
        item.filename
      ];
      for (const p of pathsToTry) {
        try {
          const uriRes = await Filesystem.getUri({ path: p, directory: "EXTERNAL" });
          if (uriRes?.uri) {
            fileUri = uriRes.uri;
            break;
          }
        } catch (_) { }
      }
      if (fileUri) {
        webUrl = window.Capacitor.convertFileSrc ? window.Capacitor.convertFileSrc(fileUri) : fileUri;
      }
    } catch (e) {
      console.warn("Filesystem URI lookup failed:", e);
    }
  }

  if (modal && container) {
    const cleanDisplayTitle = getCleanHistoryDisplayTitle(item);
    if (title) title.innerText = cleanDisplayTitle;
    container.innerHTML = "";

    const autoPlayAttr = settings.autoPlay !== false ? "autoplay" : "";
    const autoLoopAttr = settings.autoLoop !== false ? "loop" : "";

    if (webUrl) {
      if (isAudio) {
        const displayTitle = cleanDisplayTitle;
        container.innerHTML = `
          <div class="media-player-audio-wrapper">
            <img src="${item.thumbnail || 'nimiyo_icon.webp'}" class="media-player-audio-thumb" alt="Audio Thumbnail">
            <audio id="activeAudioPlayer" controls ${autoPlayAttr} ${autoLoopAttr} class="media-player-audio-control">
              <source src="${webUrl}" type="audio/mpeg">
              Your browser does not support the audio element.
            </audio>
          </div>
        `;

        const audioElement = container.querySelector("#activeAudioPlayer");
        if (audioElement) {
          const MediaSaver = window.Capacitor?.Plugins?.MediaSaver;

          const updateMusicNotif = (isPlaying) => {
            if (MediaSaver && window.Capacitor?.isNativePlatform()) {
              if (isPlaying) {
                MediaSaver.showMusicPlaybackNotification({
                  title: displayTitle,
                  isPlaying: true
                }).catch(() => { });
              } else {
                MediaSaver.showMusicPlaybackNotification({
                  title: displayTitle,
                  isPlaying: false
                }).catch(() => { });
              }
            }
          };

          if ("mediaSession" in navigator) {
            try {
              navigator.mediaSession.metadata = new MediaMetadata({
                title: displayTitle,
                artist: "NIMIYO Downloader",
                album: "Download History",
                artwork: [
                  { src: item.thumbnail || "nimiyo_icon.webp", sizes: "512x512", type: "image/png" }
                ]
              });
              navigator.mediaSession.setActionHandler("play", () => {
                audioElement.play();
                updateMusicNotif(true);
              });
              navigator.mediaSession.setActionHandler("pause", () => {
                audioElement.pause();
                updateMusicNotif(false);
              });
              navigator.mediaSession.setActionHandler("stop", () => {
                audioElement.pause();
                if (MediaSaver) MediaSaver.clearMusicPlaybackNotification().catch(() => { });
              });
            } catch (_) { }
          }

          audioElement.addEventListener("play", () => updateMusicNotif(true));
          audioElement.addEventListener("pause", () => updateMusicNotif(false));
          audioElement.addEventListener("ended", () => {
            if (MediaSaver) MediaSaver.clearMusicPlaybackNotification().catch(() => { });
          });

          if (settings.autoPlay !== false) {
            updateMusicNotif(true);
          }
        }
      } else if (isImage) {
        container.innerHTML = `
          <div style="display: flex; justify-content: center; align-items: center; width: 100%; padding: 12px;">
            <img src="${webUrl}" style="max-width: 100%; max-height: 60vh; border-radius: var(--radius); border: 2px solid var(--border-color); object-fit: contain;">
          </div>
        `;
      } else {
        container.innerHTML = `
          <video src="${webUrl}" controls ${autoPlayAttr} ${autoLoopAttr} playsinline style="width: 100%; max-height: 55vh; border-radius: var(--radius); border: 2px solid var(--border-color); background-color: #000;">
            Your browser does not support the video element.
          </video>
        `;

        const videoElement = container.querySelector("video");
        if (videoElement) {
          videoElement.addEventListener("error", (e) => {
            console.warn("In-app video playback error, falling back to native player:", e);
            if (MediaSaver && window.Capacitor?.isNativePlatform()) {
              MediaSaver.openMediaFile({
                fileName: item.filename,
                subFolder: subFolder,
                fileType: item.mediaType
              }).catch(() => { });
            }
          });
        }
      }
      modal.classList.remove("hidden");
      const rawTitle = item.title || item.filename || "Media";
      const truncatedTitle = rawTitle.length > 20 ? rawTitle.substring(0, 16) + "..." : rawTitle;
      showToast(getTranslation("toastPlaying", { title: truncatedTitle }), "info");
      return;
    }
  }

  // Native Player Fallback
  if (MediaSaver && window.Capacitor?.isNativePlatform()) {
    try {
      await MediaSaver.openMediaFile({
        fileName: item.filename,
        subFolder: subFolder,
        fileType: item.mediaType
      });
      const rawTitle = item.title || item.filename || "Media";
      const truncatedTitle = rawTitle.length > 20 ? rawTitle.substring(0, 16) + "..." : rawTitle;
      showToast(getTranslation("toastPlaying", { title: truncatedTitle }), "info");
      return;
    } catch (e) {
      console.warn("MediaSaver.openMediaFile failed:", e);
    }
  }

  const rawTitle = item.title || item.filename || "Media";
  const truncatedTitle = rawTitle.length > 20 ? rawTitle.substring(0, 16) + "..." : rawTitle;
  showToast(getTranslation("toastPlaying", { title: truncatedTitle }), "info");
}

// Render History list in Modal
// Helper to format clean display title in History (resolving generic tags to filename)
function getCleanHistoryDisplayTitle(item) {
  let title = item.title || "";
  const filename = item.filename || "";

  if (isGenericMediaTitle(title)) {
    title = filename;
  }

  return title.replace(/\.(mp4|mp3|png|jpg|jpeg|webp|m4a|wav|webm|mov)$/i, "").replace(/_+/g, " ").trim() || filename || "Media";
}

function renderHistory() {
  const list = document.getElementById("historyList");
  list.innerHTML = "";

  if (localHistory.length === 0) {
    list.innerHTML = `<p class="history-empty">${getTranslation("historyEmpty")}</p>`;
    return;
  }

  localHistory.forEach((item, index) => {
    const card = document.createElement("div");
    card.className = "history-item";
    const isAudio = item.mediaType === "audio" || item.filename?.endsWith(".mp3") || item.filename?.endsWith(".m4a");
    const isImage = item.mediaType === "image" || item.filename?.match(/\.(png|jpg|jpeg|webp)$/i);
    const isVideo = !isAudio && !isImage;
    const isPlayable = isAudio || isVideo || isImage;

    const playBtnIcon = isImage ? `
      <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
        <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path>
        <circle cx="12" cy="12" r="3"></circle>
      </svg>
    ` : `
      <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor">
        <polygon points="6 4 20 12 6 20 6 4"></polygon>
      </svg>
    `;
    const playBtnTitle = isImage ? "Lihat Foto" : getTranslation('historyPlayTitle');

    const playBtnHtml = isPlayable ? `
      <button class="history-play-btn" data-index="${index}" aria-label="${playBtnTitle}" title="${playBtnTitle}">
        ${playBtnIcon}
      </button>
    ` : "";

    const folderBtnHtml = `
      <button class="history-folder-btn" data-index="${index}" aria-label="${getTranslation('historyFolderTitle')}" title="${getTranslation('historyFolderTitle')}">
        <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor">
          <path d="M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z"/>
        </svg>
      </button>
    `;

    let initialThumbSrc = "nimiyo_icon.webp";
    if (item.localThumbnail) {
      initialThumbSrc = window.Capacitor?.convertFileSrc ? window.Capacitor.convertFileSrc(item.localThumbnail) : item.localThumbnail;
    } else if (item.thumbnail) {
      initialThumbSrc = item.thumbnail;
    }

    card.innerHTML = `
      <img src="${initialThumbSrc}" alt="Thumb" class="history-thumbnail" data-filename="${item.filename || ''}">
      <div class="history-details">
        <h4 class="history-title">${getCleanHistoryDisplayTitle(item)}</h4>
        <div class="history-meta">
          <span class="history-platform" style="color: ${platformMapping[item.platform]?.color || '#121212'}">${item.platform || 'Media'}</span>
          <span>•</span>
          <span class="history-type" style="text-transform: uppercase; font-weight: 700;">${item.mediaType || 'File'}</span>
          <span>•</span>
          <span>${formatDate(item.timestamp)}</span>
        </div>
      </div>
      <div class="history-actions">
        ${playBtnHtml}
        ${folderBtnHtml}
        <button class="history-delete-btn" data-index="${index}" aria-label="${getTranslation('historyDeleteTitle')}" title="${getTranslation('historyDeleteTitle')}">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="3 6 5 6 21 6"></polyline>
            <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path>
            <line x1="10" y1="11" x2="10" y2="17"></line>
            <line x1="14" y1="11" x2="14" y2="17"></line>
          </svg>
        </button>
      </div>
    `;

    // Play action button
    if (isPlayable) {
      const playBtn = card.querySelector(".history-play-btn");
      if (playBtn) {
        playBtn.addEventListener("click", (e) => {
          e.stopPropagation();
          triggerHaptic();
          playMediaFromHistory(item);
        });
      }
      const imgThumbEl = card.querySelector(".history-thumbnail");
      if (imgThumbEl) {
        imgThumbEl.style.cursor = "pointer";
        imgThumbEl.addEventListener("click", (e) => {
          e.stopPropagation();
          triggerHaptic();
          playMediaFromHistory(item);
        });
      }
      const titleEl = card.querySelector(".history-title");
      if (titleEl) {
        titleEl.style.cursor = "pointer";
        titleEl.addEventListener("click", (e) => {
          e.stopPropagation();
          triggerHaptic();
          playMediaFromHistory(item);
        });
      }
    }

    // Folder open button
    const folderBtn = card.querySelector(".history-folder-btn");
    if (folderBtn) {
      folderBtn.addEventListener("click", (e) => {
        e.stopPropagation();
        triggerHaptic();
        openHistoryFolder(item);
      });
    }

    // Delete single history item
    card.querySelector(".history-delete-btn").addEventListener("click", (e) => {
      e.stopPropagation();
      triggerHaptic();
      const idx = parseInt(e.currentTarget.getAttribute("data-index"));
      localHistory.splice(idx, 1);
      saveHistory();
      renderHistory();
    });

    const imgThumb = card.querySelector(".history-thumbnail");
    if (imgThumb) {
      if (isImage) {
        if (item.localThumbnail) {
          imgThumb.src = window.Capacitor?.convertFileSrc ? window.Capacitor.convertFileSrc(item.localThumbnail) : item.localThumbnail;
        } else if (Filesystem && window.Capacitor?.isNativePlatform()) {
          Filesystem.getUri({ path: `Nimiyo/ImageYo/${item.filename}`, directory: "EXTERNAL" })
            .catch(() => Filesystem.getUri({ path: `ImageYo/${item.filename}`, directory: "EXTERNAL" }))
            .catch(() => Filesystem.getUri({ path: `Nimiyo/${item.filename}`, directory: "EXTERNAL" }))
            .catch(() => Filesystem.getUri({ path: item.filename, directory: "EXTERNAL" }))
            .then(res => {
              if (res?.uri) {
                item.localThumbnail = res.uri;
                imgThumb.src = window.Capacitor.convertFileSrc(res.uri);
                saveHistory();
              } else {
                loadSecureThumbnail(item.thumbnail, imgThumb);
              }
            })
            .catch(() => {
              loadSecureThumbnail(item.thumbnail, imgThumb);
            });
        } else {
          loadSecureThumbnail(item.thumbnail, imgThumb);
        }
      } else if (isVideo) {
        if (item.localThumbnail && window.Capacitor?.convertFileSrc) {
          imgThumb.src = window.Capacitor.convertFileSrc(item.localThumbnail);
        } else if (window.Capacitor?.isNativePlatform() && window.Capacitor?.Plugins?.MediaSaver) {
          window.Capacitor.Plugins.MediaSaver.getVideoThumbnail({
            fileName: item.filename,
            subFolder: "VideoYo"
          }).then(res => {
            if (res?.filePath && window.Capacitor?.convertFileSrc) {
              item.localThumbnail = res.filePath;
              imgThumb.src = window.Capacitor.convertFileSrc(res.filePath);
              saveHistory();
            } else {
              loadSecureThumbnail(item.thumbnail, imgThumb);
            }
          }).catch(() => {
            loadSecureThumbnail(item.thumbnail, imgThumb);
          });
        } else {
          loadSecureThumbnail(item.thumbnail, imgThumb);
        }
      } else {
        loadSecureThumbnail(item.thumbnail, imgThumb);
      }

      // Fallback on error if CDN image link expired or 403
      imgThumb.onerror = () => {
        if (isVideo && window.Capacitor?.isNativePlatform() && window.Capacitor?.Plugins?.MediaSaver && !imgThumb.dataset.fallbackTried) {
          imgThumb.dataset.fallbackTried = "1";
          window.Capacitor.Plugins.MediaSaver.getVideoThumbnail({
            fileName: item.filename,
            subFolder: "VideoYo"
          }).then(res => {
            if (res?.filePath && window.Capacitor?.convertFileSrc) {
              item.localThumbnail = res.filePath;
              imgThumb.src = window.Capacitor.convertFileSrc(res.filePath);
              saveHistory();
            } else {
              imgThumb.src = "nimiyo_icon.webp";
            }
          }).catch(() => {
            imgThumb.src = "nimiyo_icon.webp";
          });
        } else {
          imgThumb.src = "nimiyo_icon.webp";
        }
      };
    }

    list.appendChild(card);
  });
}

// Format date timestamp
function formatDate(timestamp) {
  const date = new Date(timestamp);
  return `${date.getDate()}/${date.getMonth() + 1} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
}

// Platform SVG path templates
function getSVGPath(platform) {
  switch (platform) {
    case 'youtube':
      return `<path d="M23.498 6.163a3.003 3.003 0 0 0-2.11-2.108C19.524 3.545 12 3.545 12 3.545s-7.525 0-9.387.51A3.003 3.003 0 0 0 .502 6.163C0 8.07 0 12 0 12s0 3.93.502 5.837a3.003 3.003 0 0 0 2.11 2.108c1.862.51 9.387.51 9.387.51s7.525 0 9.387-.51a3.003 3.003 0 0 0 2.11-2.108C24 15.93 24 12 24 12s0-3.93-.502-5.837zM9.545 15.568V8.432L15.818 12l-6.273 3.568z"/>`;
    case 'tiktok':
      return `<path d="M12.525.02c1.31-.02 2.61-.01 3.91-.02.08 1.53.63 3.01 1.63 4.14 1.13 1.2 2.67 1.95 4.3 2.11v3.83c-1.85-.07-3.66-.74-5.12-1.92-.09-.08-.18-.16-.26-.25v6.59c.02 1.83-.53 3.63-1.58 5.11-1.37 1.89-3.56 3.06-5.91 3.19-2.6.14-5.18-.89-6.9-2.85C.8 17.92-.07 14.93.18 12.01c.29-3.23 2.45-6.01 5.6-6.85 1.05-.28 2.15-.34 3.23-.2v3.74c-.95-.21-1.96-.09-2.81.41-1.25.72-2.02 2.06-2 3.5.03 1.81 1.34 3.39 3.12 3.73 1.25.24 2.57-.1 3.42-1.03.62-.68.96-1.57.96-2.5V.02z"/>`;
    case 'instagram':
      return `<path d="M12 2.163c3.204 0 3.584.012 4.85.07 3.252.148 4.771 1.691 4.919 4.919.058 1.265.069 1.645.069 4.849 0 3.205-.012 3.584-.069 4.849-.149 3.225-1.664 4.771-4.919 4.919-1.266.058-1.644.07-4.85.07-3.204 0-3.584-.012-4.849-.07-3.26-.149-4.771-1.699-4.919-4.92-.058-1.265-.07-1.644-.07-4.849 0-3.204.013-3.583.07-4.849.149-3.227 1.664-4.771 4.919-4.919 1.266-.057 1.645-.069 4.849-.069zM12 0C8.741 0 8.333.014 7.053.072 2.695.272.273 2.69.073 7.051.014 8.333 0 8.741 0 12c0 3.259.014 3.668.072 4.948.2 4.358 2.618 6.78 6.98 6.98 1.281.058 1.689.072 4.948.072 3.259 0 3.668-.014 4.948-.072 4.354-.2 6.782-2.618 6.979-6.98.059-1.28.073-1.689.073-4.948 0-3.259-.014-3.667-.072-4.947-.196-4.354-2.617-6.78-6.979-6.98C15.668.014 15.259 0 12 0zm0 5.838a6.162 6.162 0 1 0 0 12.324 6.162 6.162 0 0 0 0-12.324zM12 16a4 4 0 1 1 0-8 4 4 0 0 1 0 8zm6.406-11.845a1.44 1.44 0 1 0 0 2.881 1.44 1.44 0 0 0 0-2.881z"/>`;
    case 'twitter':
      return `<path d="M18.244 2.25h3.308l-7.227 8.26 8.502 11.24H16.17l-5.214-6.817L4.99 21.75H1.68l7.73-8.835L1.254 2.25H8.08l4.713 6.231zm-1.161 17.52h1.833L7.084 4.126H5.117z"/>`;
    case 'spotify':
      return `<path d="M12 0C5.372 0 0 5.372 0 12s5.372 12 12 12 12-5.372 12-12S18.628 0 12 0zm5.49 17.31c-.216.354-.677.468-1.03.252-2.868-1.75-6.48-2.148-10.73-1.176-.403.092-.81-.16-.902-.563-.092-.403.16-.81.563-.902 4.654-1.062 8.636-.615 11.847 1.343.354.217.468.677.252 1.03zm1.464-3.26c-.272.443-.854.588-1.298.316-3.28-2.015-8.28-2.598-12.16-1.42-.497.15-1.022-.132-1.173-.63-.15-.497.13-1.022.63-1.172 4.43-1.345 9.94-.697 13.687 1.603.444.27.59.853.317 1.298zm.13-3.388c-3.935-2.336-10.428-2.55-14.212-1.398-.6.183-1.237-.156-1.42-.756-.183-.6.155-1.236.756-1.42 4.35-1.32 11.52-1.066 16.05 1.62.54.32.715 1.018.396 1.558-.32.54-1.018.715-1.558.396z"/>`;
    case 'applemusic':
      return `<path d="M12 0C5.372 0 0 5.372 0 12s5.372 12 12 12 12-5.372 12-12S18.628 0 12 0zm4.5 13.5c0 2.25-1.5 3-3.75 3-1.5 0-2.25-.75-2.25-1.5 0-.75.75-1.5 2.25-1.5.75 0 1.5.15 1.5.15v-3.4c-.45-.15-1.2-.15-1.65 0l-3 1.05c-.45.15-.9.45-.9.9v3.8c0 1.8-1.2 2.4-3 2.4-1.2 0-1.8-.6-1.8-1.2 0-.6.6-1.2 1.8-1.2.6 0 1.2.1 1.2.1V9.9c0-.9.6-1.5 1.5-1.8l3.6-1.2c.6-.15 1.2.15 1.2.75v5.85z"/>`;
    case 'soundcloud':
      return `<path d="M10.36 15.65c.08.06.18.09.28.09h10.45c.5 0 .9-.4.9-.9V10.2c0-.5-.4-.9-.9-.9h-1.05c-.15 0-.28-.08-.34-.22-.39-.89-1.27-1.47-2.27-1.47-.94 0-1.78.51-2.2 1.32-.07.14-.2.23-.36.23h-.8c-.14 0-.26-.1-.31-.23a4.237 4.237 0 0 0-4.14-3.13c-2.18 0-3.98 1.63-4.22 3.75-.02.16-.14.28-.3.3L4.17 9.9c-.1 0-.19.06-.23.16-.36.79-.54 1.65-.54 2.52 0 .94.21 1.86.62 2.68.05.09.14.15.24.15H10.08c.11 0 .2-.04.28-.11zM1.8 12.3c0-.6.1-1.2.2-1.7.05-.2.2-.3.35-.25.1.05.2.15.25.25.1.5.2 1.1.2 1.7 0 .6-.1 1.2-.2 1.7-.05.2-.2.3-.35.25-.1-.05-.2-.15-.25-.25-.1-.5-.2-1.1-.2-1.7z"/>`;
    case 'facebook':
      return `<path d="M24 12.073c0-6.627-5.373-12-12-12s-12 5.373-12 12c0 5.99 4.388 10.954 10.125 11.854v-8.385H7.078v-3.47h3.047V9.43c0-3.007 1.792-4.669 4.533-4.669 1.312 0 2.686.235 2.686.235v2.953H15.83c-1.491 0-1.956.925-1.956 1.874v2.25h3.328l-.532 3.47h-2.796v8.385C19.612 23.027 24 18.062 24 12.073z"/>`;
    case 'threads':
      return `<path d="M12 0C5.373 0 0 5.373 0 12s5.373 12 12 12 12-5.373 12-12S18.627 0 12 0zm4.27 15.36c-.45.69-1.07 1.13-1.84 1.32-.48.12-.97.15-1.46.12-.96-.06-1.82-.42-2.5-1.06-.57-.54-.92-1.24-1.04-2.02-.05-.33-.06-.66-.02-.99.07-.63.29-1.19.65-1.68.45-.6 1.05-.98 1.76-1.14.36-.08.72-.1 1.08-.06.76.08 1.42.41 1.94.97.43.46.68 1.01.75 1.63.02.16.03.32.02.48H11.5c.02.66.24 1.19.66 1.58.33.3.74.45 1.21.43.38-.02.7-.13.97-.33.15-.11.27-.25.37-.41l1.56.88zM12.94 11.2c-.36.02-.66.16-.88.4-.22.25-.33.56-.32.9h2.38v-.08c-.02-.34-.14-.64-.37-.88-.22-.24-.51-.36-.81-.34z"/>`;
    case 'pinterest':
      return `<path d="M12.017 0C5.396 0 .029 5.367.029 11.987c0 5.079 3.158 9.417 7.618 11.162-.105-.949-.199-2.403.041-3.439.219-.937 1.406-5.957 1.406-5.957s-.359-.72-.359-1.781c0-1.663.967-2.907 2.17-2.907 1.025 0 1.522.771 1.522 1.697 0 1.03-.656 2.57-1.002 3.996-.283 1.194.599 2.169 1.777 2.169 2.133 0 3.772-2.249 3.772-5.495 0-2.873-2.064-4.882-5.012-4.882-3.414 0-5.418 2.561-5.418 5.207 0 1.031.397 2.138.893 2.738.1.119.112.224.081.355-.089.371-.287 1.17-.327 1.332-.053.218-.172.263-.398.158-1.482-.687-2.407-2.844-2.407-4.577 0-3.725 2.707-7.147 7.807-7.147 4.1 0 7.286 2.924 7.286 6.828 0 4.072-2.57 7.352-6.131 7.352-1.198 0-2.325-.623-2.711-1.359l-.742 2.827c-.269 1.034-.997 2.33-1.487 3.13 1.079.333 2.226.513 3.415.513 6.621 0 11.987-5.366 11.987-11.987C23.999 5.368 18.636 0 12.017 0z"/>`;
    case 'bilibili':
      return `<path d="M17.8 19.8c.8 0 1.5-.7 1.5-1.5v-6.8c0-.8-.7-1.5-1.5-1.5s-1.5.7-1.5 1.5v6.8c0 .8.7 1.5 1.5 1.5zm-11.6 0c.8 0 1.5-.7 1.5-1.5v-6.8c0-.8-.7-1.5-1.5-1.5s-1.5.7-1.5 1.5v6.8c0 .8.7 1.5 1.5 1.5zm11.2-13.8L19 4.3c.4-.4.4-1 0-1.4s-1-.4-1.4 0l-2.4 2.4H8.8L6.4 2.9C6 2.5 5.4 2.5 5 2.9s-.4 1 0 1.4L6.6 6H3.5C1.6 6 0 7.6 0 9.5v8C0 19.4 1.6 21 3.5 21h17c1.9 0 3.5-1.6 3.5-3.5v-8C24 7.6 22.4 6 20.5 6h-3.1zM21 17.5c0 .3-.2.5-.5.5h-17c-.3 0-.5-.2-.5-.5v-8c0-.3.2-.5.5-.5h17c.3 0 .5.2.5.5v8z"/>`;
    case 'douyin':
      return `<path d="M12.525.02c1.31-.02 2.61-.01 3.91-.02.08 1.53.63 3.01 1.63 4.14 1.13 1.2 2.67 1.95 4.3 2.11v3.83c-1.85-.07-3.66-.74-5.12-1.92-.09-.08-.18-.16-.26-.25v6.59c.02 1.83-.53 3.63-1.58 5.11-1.37 1.89-3.56 3.06-5.91 3.19-2.6.14-5.18-.89-6.9-2.85C.8 17.92-.07 14.93.18 12.01c.29-3.23 2.45-6.01 5.6-6.85 1.05-.28 2.15-.34 3.23-.2v3.74c-.95-.21-1.96-.09-2.81.41-1.25.72-2.02 2.06-2 3.5.03 1.81 1.34 3.39 3.12 3.73 1.25.24 2.57-.1 3.42-1.03.62-.68.96-1.57.96-2.5V.02z"/>`;
    case 'bandcamp':
      return `<path d="M0 18.75h14.302L24 5.25H9.698L0 18.75z"/>`;
    case 'shopee':
      return `<path d="M19.33 6.06c-.84-.5-1.9-.81-3.05-.88-.41-2.92-2.18-5.18-4.28-5.18s-3.87 2.26-4.28 5.18c-1.15.07-2.21.38-3.05.88-2.6 1.54-3.67 4.97-3.67 10.44 0 4.14 3.79 7.5 8.46 7.5s8.46-3.36 8.46-7.5c0-5.47-1.07-8.9-3.67-10.44zm-7.33-4.18c1.13 0 2.14 1.54 2.45 3.75H9.55c.31-2.21 1.32-3.75 2.45-3.75zm0 18.82c-3.63 0-6.58-2.52-6.58-5.62 0-3.9 1.15-6.85 2.76-7.81.65-.39 1.48-.62 2.37-.67l.14 1.32h2.62l.14-1.32c.89.05 1.72.28 2.37.67 1.61.96 2.76 3.91 2.76 7.81 0 3.1-2.95 5.62-6.58 5.62z"/>`;
    default:
      return `<path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z"/>`;
  }
}

// -------------------------------------------------------------
// Auto-Update & Native Install Permission Engine
// -------------------------------------------------------------

// Check & Update Permission Button Status (Izinkan vs Diaktifkan)
async function checkInstallPermissionStatus() {
  const permBtn = document.getElementById("btnAutoUpdatePermission");
  const permTxt = document.getElementById("txtAutoUpdatePermission");
  if (!permBtn || !permTxt) return false;

  const MediaSaver = window.Capacitor?.Plugins?.MediaSaver;
  if (MediaSaver && window.Capacitor?.isNativePlatform()) {
    try {
      const res = await MediaSaver.checkInstallPermission();
      if (res && res.isGranted) {
        permBtn.classList.add("granted");
        permTxt.innerText = getTranslation("btnPermissionGranted");
        return true;
      } else {
        permBtn.classList.remove("granted");
        permTxt.innerText = getTranslation("btnPermissionAllow");
        return false;
      }
    } catch (e) {
      console.warn("checkInstallPermission error:", e);
    }
  }

  permBtn.classList.remove("granted");
  permTxt.innerText = getTranslation("btnPermissionAllow");
  return false;
}

// Request Permission by opening Android Settings
async function requestAutoUpdatePermission() {
  triggerHaptic();
  const MediaSaver = window.Capacitor?.Plugins?.MediaSaver;
  if (MediaSaver && window.Capacitor?.isNativePlatform()) {
    try {
      await MediaSaver.requestInstallPermission();
    } catch (e) {
      console.warn("requestInstallPermission error:", e);
    }
  } else {
    showToast(getTranslation("btnPermissionGranted"), "success");
    const permBtn = document.getElementById("btnAutoUpdatePermission");
    const permTxt = document.getElementById("txtAutoUpdatePermission");
    if (permBtn && permTxt) {
      permBtn.classList.add("granted");
      permTxt.innerText = getTranslation("btnPermissionGranted");
    }
  }
}

// Check for App Updates from GitHub
async function checkForAppUpdates(isManual = false) {
  if (isManual) {
    showToast(getTranslation("toastUpdateChecking"), "info");
  }

  try {
    const fetchUrl = `${UPDATE_MANIFEST_URL}?_nocache=${Date.now()}`;
    let manifestData = null;

    if (window.Capacitor?.Plugins?.CapacitorHttp && window.Capacitor?.isNativePlatform()) {
      const res = await window.Capacitor.Plugins.CapacitorHttp.request({
        method: "GET",
        url: fetchUrl,
        headers: { "Cache-Control": "no-cache" }
      });
      manifestData = typeof res.data === "string" ? JSON.parse(res.data) : res.data;
    } else {
      const res = await fetch(fetchUrl);
      manifestData = await res.json();
    }

    if (!manifestData || typeof manifestData.versionCode !== "number") {
      throw new Error("Invalid version manifest format");
    }

    latestUpdateInfo = manifestData;

    if (manifestData.versionCode > APP_VERSION_CODE) {
      // Check if update is currently snoozed (only bypass snooze on manual check)
      const snoozeVersion = localStorage.getItem("nimiyo_update_snooze_version");
      const snoozeUntil = Number(localStorage.getItem("nimiyo_update_snooze_until")) || 0;
      const currentVersionStr = String(manifestData.versionName || manifestData.versionCode);
      const isSameVersion = (snoozeVersion === currentVersionStr);

      if (!isManual && isSameVersion && snoozeUntil > 0 && Date.now() < snoozeUntil) {
        console.log(`[Update] Update v${currentVersionStr} is snoozed until ${new Date(snoozeUntil).toLocaleString()}`);
        return;
      }

      // New update available! Show update dialog modal
      showUpdateModal(manifestData);
    } else {
      if (isManual) {
        showToast(getTranslation("toastAppUpToDate", { version: APP_VERSION_NAME }), "success");
      }
    }
  } catch (err) {
    console.error("checkForAppUpdates error:", err);
    if (isManual) {
      showToast(getTranslation("toastUpdateCheckFailed"), "error");
    }
  }
}

// Show Update Dialog Modal
function showUpdateModal(data) {
  const modal = document.getElementById("updateModal");
  const badge = document.getElementById("updateVersionBadge");
  const list = document.getElementById("updateChangelogList");
  const progressBox = document.getElementById("updateDownloadProgressBox");
  const actionRow = document.getElementById("updateActionRow");
  const startBtn = document.getElementById("btnStartUpdate");
  const clockBtn = document.getElementById("btnRemindSchedule");
  const scheduleMenu = document.getElementById("remindScheduleMenu");

  if (!modal) return;

  if (badge) badge.innerText = `v${data.versionName || data.versionCode}`;

  if (list) {
    list.innerHTML = "";
    const changelogs = Array.isArray(data.changelog) ? data.changelog : [data.changelog || "Perbaikan performa & stabilitas."];
    changelogs.forEach(item => {
      const li = document.createElement("li");
      li.innerText = item;
      list.appendChild(li);
    });
  }

  // Handle Snooze expiration logic:
  // If user previously selected a snooze duration (1d, 3d, 1w) and that duration has expired,
  // hide the clock button so user cannot snooze again.
  const snoozeVersion = localStorage.getItem("nimiyo_update_snooze_version");
  const snoozeUntil = Number(localStorage.getItem("nimiyo_update_snooze_until")) || 0;
  const currentVersionStr = String(data.versionName || data.versionCode);
  const isSameVersion = (snoozeVersion === currentVersionStr);

  let isSnoozeExpired = localStorage.getItem("nimiyo_update_snooze_expired") === "true";
  if (isSameVersion && snoozeUntil > 0 && Date.now() >= snoozeUntil) {
    isSnoozeExpired = true;
    localStorage.setItem("nimiyo_update_snooze_expired", "true");
  }

  if (scheduleMenu) scheduleMenu.classList.add("hidden");

  if (clockBtn) {
    if (isSnoozeExpired && isSameVersion) {
      clockBtn.classList.add("hidden");
    } else {
      clockBtn.classList.remove("hidden");
    }
  }

  if (progressBox) progressBox.classList.add("hidden");
  if (actionRow) actionRow.classList.remove("hidden");
  if (startBtn) {
    startBtn.disabled = false;
    startBtn.innerText = getTranslation("btnUpdateAndInstall");
  }
  const manualBtn = document.getElementById("btnManualDownloadUpdate");
  if (manualBtn) {
    manualBtn.disabled = false;
    manualBtn.innerText = getTranslation("btnManualDownload");
  }
  const laterBtn = document.getElementById("btnLaterUpdate");
  if (laterBtn) {
    laterBtn.innerText = getTranslation("btnRemindLater");
  }

  modal.classList.remove("hidden");
  triggerHaptic();
}

// Download APK manually to Download directory without auto-install
async function downloadManualUpdate(apkUrl) {
  if (!apkUrl) {
    apkUrl = latestUpdateInfo?.downloadUrl;
  }
  if (!apkUrl) return;

  const version = latestUpdateInfo?.versionName || latestUpdateInfo?.versionCode || "latest";
  const fileName = `Nimiyo_v${version}.apk`;

  const progressBox = document.getElementById("updateDownloadProgressBox");
  const actionRow = document.getElementById("updateActionRow");
  const progressBarFill = document.getElementById("updateProgressBarFill");
  const progressPercentText = document.getElementById("updateProgressPercent");
  const progressText = document.getElementById("updateProgressText");

  if (progressBox) progressBox.classList.remove("hidden");
  if (actionRow) actionRow.classList.add("hidden");
  if (progressText) progressText.innerText = getTranslation("toastDownloadingApkManual", "Mengunduh file APK ke folder Download...");

  let downloadPercent = 0;
  const simInterval = setInterval(() => {
    if (downloadPercent < 90) {
      downloadPercent += Math.floor(Math.random() * 8) + 3;
      if (downloadPercent > 90) downloadPercent = 90;
      if (progressBarFill) progressBarFill.style.width = `${downloadPercent}%`;
      if (progressPercentText) progressPercentText.innerText = `${downloadPercent}%`;
    }
  }, 250);

  try {
    const MediaSaver = window.Capacitor?.Plugins?.MediaSaver;

    if (MediaSaver && typeof MediaSaver.downloadFile === "function" && window.Capacitor?.isNativePlatform()) {
      await MediaSaver.downloadFile({
        url: apkUrl,
        fileName: fileName,
        fileType: "apk"
      });
      clearInterval(simInterval);
      if (progressBarFill) progressBarFill.style.width = "100%";
      if (progressPercentText) progressPercentText.innerText = "100%";
      if (progressText) progressText.innerText = getTranslation("toastManualApkDownloaded", "File APK tersimpan di folder Download. Silakan pasang secara manual.");
      
      showToast(getTranslation("toastManualApkDownloaded", "File APK tersimpan di folder Download. Silakan pasang secara manual."), "success");
      
      setTimeout(() => {
        document.getElementById("updateModal")?.classList.add("hidden");
        if (progressBox) progressBox.classList.add("hidden");
        if (actionRow) actionRow.classList.remove("hidden");
      }, 1500);
    } else if (Filesystem && window.Capacitor?.isNativePlatform()) {
      const dlRes = await Filesystem.downloadFile({
        url: apkUrl,
        path: fileName,
        directory: "CACHE"
      });
      if (MediaSaver && typeof MediaSaver.saveToPublicStorage === "function") {
        await MediaSaver.saveToPublicStorage({
          filePath: dlRes?.path || fileName,
          fileName: fileName,
          fileType: "apk"
        });
      }
      clearInterval(simInterval);
      if (progressBarFill) progressBarFill.style.width = "100%";
      if (progressPercentText) progressPercentText.innerText = "100%";
      showToast(getTranslation("toastManualApkDownloaded", "File APK tersimpan di folder Download. Silakan pasang secara manual."), "success");
      setTimeout(() => {
        document.getElementById("updateModal")?.classList.add("hidden");
        if (progressBox) progressBox.classList.add("hidden");
        if (actionRow) actionRow.classList.remove("hidden");
      }, 1500);
    } else {
      clearInterval(simInterval);
      window.open(apkUrl, "_blank");
      showToast(getTranslation("toastDownloadSuccess"), "success");
      document.getElementById("updateModal")?.classList.add("hidden");
      if (progressBox) progressBox.classList.add("hidden");
      if (actionRow) actionRow.classList.remove("hidden");
    }
  } catch (err) {
    clearInterval(simInterval);
    console.error("downloadManualUpdate error:", err);
    if (progressBox) progressBox.classList.add("hidden");
    if (actionRow) actionRow.classList.remove("hidden");
    showToast(`${getTranslation("toastDownloadFailManualServer")}: ${err.message}`, "error");
  }
}

let lastDownloadedUpdateApkPath = localStorage.getItem("nimiyo_pending_apk_path") || null;

// Check if install permission was just granted and resume APK installation
async function checkAndResumePendingUpdateInstall() {
  const granted = await checkInstallPermissionStatus();
  const pendingPath = lastDownloadedUpdateApkPath || localStorage.getItem("nimiyo_pending_apk_path");
  if (granted && pendingPath) {
    lastDownloadedUpdateApkPath = null;
    localStorage.removeItem("nimiyo_pending_apk_path");
    const progressText = document.getElementById("updateProgressText");
    if (progressText) progressText.innerText = "Memulai pemasangan pembaruan...";
    const MediaSaver = window.Capacitor?.Plugins?.MediaSaver;
    if (MediaSaver && typeof MediaSaver.installApk === "function") {
      try {
        await MediaSaver.installApk({ filePath: pendingPath });
      } catch (err) {
        console.error("Install pending APK error:", err);
      }
    }
  }
}

// Download and Install APK Update directly inside the app
async function downloadAndInstallUpdate(apkUrl) {
  if (!apkUrl) {
    apkUrl = latestUpdateInfo?.downloadUrl;
  }
  if (!apkUrl) return;

  const progressBox = document.getElementById("updateDownloadProgressBox");
  const actionRow = document.getElementById("updateActionRow");
  const progressBarFill = document.getElementById("updateProgressBarFill");
  const progressPercentText = document.getElementById("updateProgressPercent");
  const progressText = document.getElementById("updateProgressText");

  if (progressBox) progressBox.classList.remove("hidden");
  if (actionRow) actionRow.classList.add("hidden");
  if (progressBarFill) progressBarFill.style.width = "0%";
  if (progressPercentText) progressPercentText.innerText = "0%";
  if (progressText) progressText.innerText = getTranslation("updateDownloading", "Mengunduh pembaruan...");

  const MediaSaver = window.Capacitor?.Plugins?.MediaSaver;
  let progressListener = null;
  let simInterval = null;

  try {
    let apkFilePath = null;

    if (MediaSaver && typeof MediaSaver.downloadUpdateApk === "function" && window.Capacitor?.isNativePlatform()) {
      if (typeof MediaSaver.addListener === "function") {
        progressListener = MediaSaver.addListener("onUpdateDownloadProgress", (data) => {
          if (data && typeof data.progress === "number") {
            const pct = Math.min(Math.max(data.progress, 0), 99);
            if (progressBarFill) progressBarFill.style.width = `${pct}%`;
            if (progressPercentText) progressPercentText.innerText = `${pct}%`;
          }
        });
      }

      const res = await MediaSaver.downloadUpdateApk({ url: apkUrl });
      if (res && res.success && res.filePath) {
        apkFilePath = res.filePath;
      } else {
        throw new Error(res?.error || "Gagal mengunduh berkas pembaruan");
      }
    } else if (MediaSaver && typeof MediaSaver.downloadFile === "function" && window.Capacitor?.isNativePlatform()) {
      // In-app fallback using downloadFile if downloadUpdateApk is not present
      const version = latestUpdateInfo?.versionName || latestUpdateInfo?.versionCode || "latest";
      const fileName = `Nimiyo_v${version}.apk`;

      let downloadPercent = 0;
      simInterval = setInterval(() => {
        if (downloadPercent < 90) {
          downloadPercent += Math.floor(Math.random() * 8) + 3;
          if (downloadPercent > 90) downloadPercent = 90;
          if (progressBarFill) progressBarFill.style.width = `${downloadPercent}%`;
          if (progressPercentText) progressPercentText.innerText = `${downloadPercent}%`;
        }
      }, 250);

      const res = await MediaSaver.downloadFile({
        url: apkUrl,
        fileName: fileName,
        fileType: "apk",
        overwriteMode: "overwrite"
      });

      if (res && res.success && (res.filePath || res.fileName)) {
        apkFilePath = res.filePath || res.fileName;
      } else {
        throw new Error("Gagal mengunduh berkas pembaruan");
      }
    } else {
      // Only fallback to browser if not running on native platform
      window.open(apkUrl, "_blank");
      showToast(getTranslation("toastDownloadSuccess"), "success");
      if (progressBox) progressBox.classList.add("hidden");
      if (actionRow) actionRow.classList.remove("hidden");
      return;
    }

    if (simInterval) clearInterval(simInterval);
    if (progressListener) {
      try { progressListener.remove(); } catch (_) {}
    }

    if (apkFilePath) {
      lastDownloadedUpdateApkPath = apkFilePath;
      localStorage.setItem("nimiyo_pending_apk_path", apkFilePath);

      if (progressBarFill) progressBarFill.style.width = "100%";
      if (progressPercentText) progressPercentText.innerText = "100%";
      if (progressText) progressText.innerText = "Memulai pemasangan...";

      // Trigger native package install prompt immediately
      if (MediaSaver && typeof MediaSaver.installApk === "function") {
        const installRes = await MediaSaver.installApk({ filePath: apkFilePath });
        if (installRes && installRes.permissionRequired) {
          if (progressText) progressText.innerText = "Aktifkan izin instalasi aplikasi tidak dikenal untuk melanjutkan...";
          showToast("Aktifkan izin 'Install aplikasi tidak dikenal' lalu kembali ke aplikasi", "info");
        } else if (installRes && installRes.success) {
          if (progressText) progressText.innerText = "Silakan konfirmasi pembaruan di layar...";
        }
      }
    }
  } catch (err) {
    if (simInterval) clearInterval(simInterval);
    if (progressListener) {
      try { progressListener.remove(); } catch (_) {}
    }
    console.error("downloadAndInstallUpdate error:", err);
    if (progressBox) progressBox.classList.add("hidden");
    if (actionRow) actionRow.classList.remove("hidden");
    showToast(`Gagal mengunduh pembaruan: ${err.message}`, "error");
  }
}

// -------------------------------------------------------------
// First-Time Rules & Guidelines Onboarding Check
// -------------------------------------------------------------
function checkRulesOnboarding() {
  const isAccepted = localStorage.getItem("nimiyo_rules_accepted");
  if (isAccepted !== "true") {
    setTimeout(() => {
      const modal = document.getElementById("rulesModal");
      if (modal) {
        modal.classList.remove("hidden");
        triggerHaptic();
      }
    }, 400);
  }
}
