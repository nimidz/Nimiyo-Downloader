// NIMIYO Application Logic

// Capacitor Plugins
const { Filesystem, Directory } = window.Capacitor?.Plugins?.Filesystem ? window.Capacitor.Plugins : { Filesystem: null };
const { Clipboard } = window.Capacitor?.Plugins?.Clipboard ? window.Capacitor.Plugins : { Clipboard: null };
const { Haptics } = window.Capacitor?.Plugins?.Haptics ? window.Capacitor.Plugins : { Haptics: null };
const { Network } = window.Capacitor?.Plugins?.Network ? window.Capacitor.Plugins : { Network: null };

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
    aboutVersion: "Version 1.0.0 (Lite)",
    aboutDesc: "A premium, modern, and lightweight media downloader engine built on scrapr.",
    aboutThanks: "Thanks to:",
    toastClipboardEmpty: "Clipboard is empty or does not contain a text link.",
    toastInvalidUrl: "Please enter a valid link from a supported platform.",
    toastDetecting: "Detecting platform...",
    toastScraping: "Running scraper ({scraper})...",
    toastScrapeSuccess: "Analysis complete!",
    toastScrapeFail: "Scraper {scraper} failed: {message}",
    toastScrapeAllFailed: "Server is busy",
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
    toastPlaying: "Playing: {title}",
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
    btnLater: "Later",
    btnUpdateNow: "UPDATE NOW",
    toastAppUpToDate: "You are using the latest version (v{version}).",
    toastUpdateChecking: "Checking for updates...",
    toastUpdateCheckFailed: "Could not check for updates. Check internet connection.",
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
    btnAgreeRules: "I AGREE & UNDERSTAND"
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
    aboutVersion: "Versi 1.0.0 (Lite)",
    aboutDesc: "Mesin pengunduh media premium, modern, dan ringan yang dibangun di atas scrapr.",
    aboutThanks: "Terima kasih kepada:",
    toastClipboardEmpty: "Papan klip kosong atau tidak berisi tautan teks.",
    toastInvalidUrl: "Masukkan tautan yang valid dari platform yang didukung.",
    toastDetecting: "Mendeteksi platform...",
    toastScraping: "Menjalankan scraper ({scraper})...",
    toastScrapeSuccess: "Analisis selesai!",
    toastScrapeFail: "Scraper {scraper} gagal: {message}",
    toastScrapeAllFailed: "Server sedang sibuk",
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
    toastPlaying: "Memutar: {title}",
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
    btnLater: "Nanti",
    btnUpdateNow: "UPDATE SEKARANG",
    toastAppUpToDate: "Aplikasi sudah dalam versi terbaru (v{version}).",
    toastUpdateChecking: "Memeriksa pembaruan...",
    toastUpdateCheckFailed: "Gagal memeriksa pembaruan. Periksa koneksi internet.",
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
    btnAgreeRules: "SAYA SETUJU & MENGERTI"
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
    aboutVersion: "版本 1.0.0 (Lite)",
    aboutDesc: "基于 scrapr 构建的高级、现代且轻量级的媒体下载引擎。",
    aboutThanks: "致谢:",
    toastClipboardEmpty: "剪贴板为空或不包含文本链接。",
    toastInvalidUrl: "请输入受支持平台的有效链接。",
    toastDetecting: "正在检测平台...",
    toastScraping: "正在运行解析器 ({scraper})...",
    toastScrapeSuccess: "解析完成！",
    toastScrapeFail: "解析器 {scraper} 失败：{message}",
    toastScrapeAllFailed: "服务器繁忙",
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
    btnLater: "稍后",
    btnUpdateNow: "立即更新",
    toastAppUpToDate: "已是最新版本 (v{version})。",
    toastUpdateChecking: "正在检查更新...",
    toastUpdateCheckFailed: "检查更新失败，请检查网络连接。",
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
    btnAgreeRules: "我同意并理解"
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
    aboutVersion: "バージョン 1.0.0 (Lite)",
    aboutDesc: "scrapr をベースに構築されたプレミアムでモダン、軽量なメディアダウンローダー。",
    aboutThanks: "スペシャルサンクス:",
    toastClipboardEmpty: "クリップボードが空か、有効なテキストリンクが含まれていません。",
    toastInvalidUrl: "対応プラットフォームの有効なリンクを入力してください。",
    toastDetecting: "プラットフォームを検出中...",
    toastScraping: "スクレイパーを実行中 ({scraper})...",
    toastScrapeSuccess: "解析が完了しました！",
    toastScrapeFail: "スクレイパー {scraper} でエラーが発生しました: {message}",
    toastScrapeAllFailed: "サーバーが混雑しています",
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
    btnLater: "後で",
    btnUpdateNow: "今すぐ更新",
    toastAppUpToDate: "最新バージョンを使用しています (v{version})。",
    toastUpdateChecking: "更新を確認中...",
    toastUpdateCheckFailed: "更新の確認に失敗しました。接続を確認してください。",
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
    btnAgreeRules: "同意して続ける"
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
let activeDownloadXHR = null;
let downloadCancelled = false;
let downloadProgressInterval = null;

// Platform details & Regex mapping
const platformMapping = {
  youtube: { name: "YouTube", domains: /(youtube\.com|youtu\.be)/i, color: "#FF0000" },
  tiktok: { name: "TikTok", domains: /(tiktok\.com)/i, color: "#000000" },
  instagram: { name: "Instagram", domains: /(instagram\.com)/i, color: "#E4405F" },
  twitter: { name: "Twitter / X", domains: /(twitter\.com|x\.com)/i, color: "#1DA1F2" },
  spotify: { name: "Spotify", domains: /(spotify\.com)/i, color: "#1ED760" },
  applemusic: { name: "Apple Music", domains: /(music\.apple\.com)/i, color: "#FA576E" },
  facebook: { name: "Facebook", domains: /(facebook\.com|fb\.watch|fb\.com)/i, color: "#1877F2" },
  threads: { name: "Threads", domains: /(threads\.net)/i, color: "#000000" },
  pinterest: { name: "Pinterest", domains: /(pinterest\.com|pin\.it)/i, color: "#E60023" },
  bilibili: { name: "Bilibili", domains: /(bilibili\.com|b23\.tv)/i, color: "#00AEEC" },
  douyin: { name: "Douyin", domains: /(douyin\.com)/i, color: "#FF0050" },
  bandcamp: { name: "Bandcamp", domains: /(bandcamp\.com)/i, color: "#1DA1F2" },
  pixiv: { name: "Pixiv", domains: /(pixiv\.net|pixiv\.me|pximg\.net)/i, color: "#0096FA" },
  rednote: { name: "RedNote (小红书)", domains: /(rednote\.com|xiaohongshu\.com|xhslink\.com|xhslink\.cn)/i, color: "#FF2442" }
};

// Predefined scraper fallback order per platform (100% matching share.js & scrapr)
const fallbackChains = {
  tiktok: ['snaptik', 'tiktokio', 'direct'],
  instagram: ['snapsave', 'indown', 'direct'],
  facebook: ['snapsave', 'direct'],
  spotify: ['spotidown', 'soundloaders', 'direct'],
  twitter: ['tvd', 'tweeload', 'direct'],
  youtube: ['ytmp3', 'direct'],
  applemusic: ['aplmate', 'direct'],
  pinterest: ['pindown', 'direct'],
  threads: ['threadster', 'direct'],
  bilibili: ['direct'],
  douyin: ['direct'],
  bandcamp: ['bandcampdownloader', 'direct'],
  pixiv: ['direct'],
  rednote: ['direct']
};

// App Version Constants & GitHub Auto-Update Engine
const APP_VERSION_NAME = "1.0.0";
const APP_VERSION_CODE = 1;
const UPDATE_MANIFEST_URL = "https://raw.githubusercontent.com/nimidz/Nimiyo-Downloader/main/version.json";
let latestUpdateInfo = null;

// Initialize Application
document.addEventListener("DOMContentLoaded", () => {
  loadSettings();
  loadHistory();
  initUI();
  setupEventListeners();
  checkClipboardOnResume();
  checkInstallPermissionStatus();
  checkRulesOnboarding();
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
}

// Save Settings to LocalStorage
function saveSettings() {
  localStorage.setItem("nimiyo_settings", JSON.stringify(settings));
  currentLanguage = settings.language || 'en';
  applyDarkMode(settings.darkMode);
  applyTranslations();
  showToast(getTranslation("toastSettingsSaved"), "success");
}

// Load History from LocalStorage
function loadHistory() {
  const savedHistory = localStorage.getItem("nimiyo_history");
  if (savedHistory) {
    try {
      localHistory = JSON.parse(savedHistory);
    } catch (e) {
      console.error("Failed to parse history", e);
      localHistory = [];
    }
  }
}

// Save History to LocalStorage
function saveHistory() {
  localStorage.setItem("nimiyo_history", JSON.stringify(localHistory));
}

// Apply Dark Mode Class to Body
function applyDarkMode(enabled) {
  if (enabled) {
    document.body.classList.add("dark-mode");
  } else {
    document.body.classList.remove("dark-mode");
  }
}

// Apply Translation strings to UI elements
function applyTranslations() {
  currentLanguage = settings.language || 'en';

  const logoEl = document.querySelector(".logo");
  if (logoEl) logoEl.innerText = getTranslation("appTitle");

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

  const historyH2 = document.querySelector("#historyModal h2");
  if (historyH2) historyH2.innerText = getTranslation("historyTitle");

  const clearHistBtn = document.getElementById("clearHistoryBtn");
  if (clearHistBtn) clearHistBtn.innerText = getTranslation("btnClearHistory");

  const settingsH2 = document.getElementById("settingsModalTitle");
  if (settingsH2) settingsH2.innerText = getTranslation("settingsTitle");

  // Automatically translate all elements with data-i18n attribute
  document.querySelectorAll("[data-i18n]").forEach(el => {
    const key = el.getAttribute("data-i18n");
    if (key) el.innerText = getTranslation(key);
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
    Haptics.impact({ style: 'medium' }).catch(() => {});
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

// Clear App Cache
function clearAppCache() {
  triggerHaptic();
  // Clear any temporary memory blobs or history cache
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
    settings = {
      language: detectSystemLanguage(),
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
            <svg viewBox="0 0 24 24" width="24" height="24" fill="currentColor">
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
  
  // Show clear button when text is in URL input
  urlInput.addEventListener("input", () => {
    if (urlInput.value.trim().length > 0) {
      clearInputBtn.classList.remove("hidden");
    } else {
      clearInputBtn.classList.add("hidden");
    }
  });

  // Clear Input button
  clearInputBtn.addEventListener("click", () => {
    urlInput.value = "";
    clearInputBtn.classList.add("hidden");
    urlInput.focus();
  });

  // Paste Input button
  pasteInputBtn.addEventListener("click", async () => {
    triggerHaptic();
    if (Clipboard) {
      const clipboardContent = await Clipboard.read();
      if (clipboardContent.value) {
        urlInput.value = clipboardContent.value;
        clearInputBtn.classList.remove("hidden");
        showToast(getTranslation("toastDetecting"), "info");
        if (settings.autoAnalyze) {
          analyzeLink(clipboardContent.value);
        }
      } else {
        showToast(getTranslation("toastClipboardEmpty"), "error");
      }
    } else {
      try {
        const text = await navigator.clipboard.readText();
        if (text) {
          urlInput.value = text;
          clearInputBtn.classList.remove("hidden");
          if (settings.autoAnalyze) {
            analyzeLink(text);
          }
        }
      } catch (e) {
        showToast(getTranslation("toastClipboardEmpty"), "error");
      }
    }
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

  // Modals Visibility
  const settingsModal = document.getElementById("settingsModal");
  const historyModal = document.getElementById("historyModal");

  document.getElementById("settingsBtn").addEventListener("click", () => {
    triggerHaptic();
    updateStorageSizeDisplay();
    // Open at main category menu
    document.getElementById("settingsMenuMain").classList.remove("hidden");
    document.querySelectorAll(".settings-sub-page").forEach(sp => sp.classList.add("hidden"));
    document.getElementById("settingsModalTitle").innerText = getTranslation("settingsTitle");
    settingsModal.classList.remove("hidden");
  });

  document.getElementById("closeSettingsBtn").addEventListener("click", () => {
    settingsModal.classList.add("hidden");
  });

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
        const titleKey = targetPage.querySelector("h3")?.getAttribute("data-i18n");
        if (titleKey) document.getElementById("settingsModalTitle").innerText = getTranslation(titleKey);
      }
    });
  });

  document.querySelectorAll(".settings-back-btn").forEach(btn => {
    btn.addEventListener("click", () => {
      triggerHaptic();
      document.querySelectorAll(".settings-sub-page").forEach(sp => sp.classList.add("hidden"));
      document.getElementById("settingsMenuMain").classList.remove("hidden");
      document.getElementById("settingsModalTitle").innerText = getTranslation("settingsTitle");
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
  bindChange("settingAutoPaste", "autoPaste", true);
  bindChange("settingAutoAnalyze", "autoAnalyze", true);
  bindChange("settingAutoClearInput", "autoClearInput", true);
  bindChange("settingIncognito", "incognito", true);
  bindChange("settingDarkMode", "darkMode", true);
  bindChange("settingCompletionSound", "completionSound", true);
  bindChange("settingHaptic", "haptic", true);
  bindChange("settingAppFont", "appFont", false, applyAppFont);
  bindChange("settingFilenameTemplate", "filenameTemplate");
  bindChange("settingOverwriteMode", "overwriteMode");
  bindChange("settingConcurrentDl", "concurrentDl");
  bindChange("settingBatchPhotoMode", "batchPhotoMode");
  bindChange("settingAutoDownload", "autoDownload", true);
  bindChange("settingAutoRetry", "autoRetry", true);
  bindChange("settingMaxRetry", "maxRetry");
  bindChange("settingWifiOnly", "wifiOnly", true);
  bindChange("settingDoh", "doh");
  bindChange("settingHeaderSpoofing", "headerSpoofing", true);
  bindChange("settingHistoryLimit", "historyLimit");
  bindChange("settingAutoClearDays", "autoClearDays");
  bindChange("settingAutoClearCacheDays", "autoClearCacheDays");
  bindChange("settingAutoPlay", "autoPlay", true);
  bindChange("settingAutoLoop", "autoLoop", true);
  bindChange("settingKeepAwake", "keepAwake", true);

  // Settings Action Buttons
  const clearCacheBtn = document.getElementById("clearCacheBtn");
  if (clearCacheBtn) clearCacheBtn.addEventListener("click", clearAppCache);
  const wipeDataBtn = document.getElementById("wipeDataBtn");
  if (wipeDataBtn) wipeDataBtn.addEventListener("click", wipeAllData);
  const resetSettingsBtn = document.getElementById("resetSettingsBtn");
  if (resetSettingsBtn) resetSettingsBtn.addEventListener("click", resetSettingsToDefault);

  // History Modal
  document.getElementById("historyBtn").addEventListener("click", () => {
    triggerHaptic();
    renderHistory();
    historyModal.classList.remove("hidden");
  });

  document.getElementById("closeHistoryBtn").addEventListener("click", () => {
    historyModal.classList.add("hidden");
  });

  // Close Media Player Modal
  const closeMediaBtn = document.getElementById("closeMediaPlayerBtn");
  if (closeMediaBtn) {
    closeMediaBtn.addEventListener("click", () => {
      triggerHaptic();
      const modal = document.getElementById("mediaPlayerModal");
      if (modal) modal.classList.add("hidden");
      const container = document.getElementById("mediaPlayerContainer");
      if (container) container.innerHTML = "";
    });
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
  document.getElementById("copyDescBtn").addEventListener("click", () => {
    triggerHaptic();
    const descText = document.getElementById("resultDescription");
    const fullText = descText.getAttribute("data-full-text") || "";
    if (fullText) {
      if (Clipboard) {
        Clipboard.write({ string: fullText }).catch(() => {});
      } else if (navigator.clipboard) {
        navigator.clipboard.writeText(fullText).catch(() => {});
      }
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
  document.getElementById("copyTitleBtn").addEventListener("click", () => {
    triggerHaptic();
    const titleText = document.getElementById("resultTitle");
    const fullText = titleText.getAttribute("data-full-text") || "";
    if (fullText) {
      if (Clipboard) {
        Clipboard.write({ string: fullText }).catch(() => {});
      } else if (navigator.clipboard) {
        navigator.clipboard.writeText(fullText).catch(() => {});
      }
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
      document.getElementById("updateModal").classList.add("hidden");
    });
  }

  // Update Now button
  const btnStartUpdate = document.getElementById("btnStartUpdate");
  if (btnStartUpdate) {
    btnStartUpdate.addEventListener("click", () => {
      triggerHaptic();
      downloadAndInstallUpdate();
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

  // Register resume event listener for Auto-Paste & Permission recheck
  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "visible") {
      checkClipboardOnResume();
      checkInstallPermissionStatus();
    }
  });
}

// Auto-Paste/Analyze on App Resume
async function checkClipboardOnResume() {
  if (!settings.autoPaste) return;
  
  let clipboardText = "";
  if (Clipboard) {
    const res = await Clipboard.read().catch(() => null);
    clipboardText = res?.value || "";
  } else {
    // Browser testing fallback (requires user gesture, might fail, handle safely)
    try {
      clipboardText = await navigator.clipboard.readText();
    } catch (_) {}
  }

  clipboardText = clipboardText.trim();
  if (clipboardText && isValidMediaUrl(clipboardText)) {
    const urlInput = document.getElementById("urlInput");
    // Only paste if it is a new URL
    if (urlInput.value !== clipboardText) {
      urlInput.value = clipboardText;
      document.getElementById("clearInputBtn").classList.remove("hidden");
      showToast(getTranslation("toastDetecting"), "info");
      if (settings.autoAnalyze) {
        analyzeLink(clipboardText);
      }
    }
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
    showToast(getTranslation("toastScrapeAllFailed"), "error");
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
      const scrapeFunc = scraperModule?.[scraperMethod];
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
      }
    } catch (error) {
      clearTimeout(timeoutId);
      currentAnalysisCancelReject = null;
      if (analysisCancelled || error.message === "Analysis aborted by user") {
        allFailed = false;
        break; // Instant break on user cancel
      }
      console.error(`Scraper ${scraperMethod} crashed or timed out: `, error);
    }
  }

  // If user cancelled, UI is already reset by cancelAnalysis()
  if (analysisCancelled) {
    return;
  }

  // If all scrapers failed
  if (!success && allFailed) {
    showToast(getTranslation("toastScrapeAllFailed"), "error");
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
      } catch (_) {}
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
      type.innerText = `${dl.type || 'media'} (${dl.quality || 'HD'})`;
      
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

// Build human-readable, safe filename
function buildTargetFilename(dlItem, mediaResult, mediaCategory, platform, extension) {
  let title = (mediaResult?.title || "").trim();
  let itemTitle = (dlItem?.title || dlItem?.name || "").trim();
  let dlType = (dlItem?.type || "").trim();
  
  const isGenericTitle = !title ||
    title === "TikTok Video" ||
    title === "TikTok Content" ||
    title === "Instagram Content" ||
    title === "Media" ||
    title === "Pinterest Pin" ||
    title === "Pinterest" ||
    title === "Facebook Media" ||
    title === "Threads Media" ||
    title === "Apple Music Content";

  // Clean track type label
  let cleanItemTitle = itemTitle.replace(/\[(MP3|Cover|MP4|Video|Audio|Photo|Image|HD|SD)\]/gi, "").trim();
  let cleanType = dlType.replace(/\[(MP3|Cover|MP4|Video|Audio|Photo|Image|HD|SD)\]/gi, "").trim();

  const genericKeywords = [
    "MP4", "VIDEO", "PHOTO", "IMAGE", "MP3", "AUDIO", "MP4 (HD)", 
    "HD", "SD", "ORIGINAL", "MEDIA", "DOWNLOAD", "DOWNLOAD MP3", 
    "DOWNLOAD 320KBPS", "DOWNLOAD 128KBPS", "DOWNLOAD SONG", "320KBPS", "128KBPS",
    "APPLE MUSIC TRACK", "TRACK", "ORIGINAL IMAGE", "HD VIDEO", "HD PHOTO"
  ];

  const isGenericItemTitle = !cleanItemTitle || genericKeywords.includes(cleanItemTitle.toUpperCase());
  const isGenericDlType = !cleanType || genericKeywords.includes(cleanType.toUpperCase());

  let base = "";
  // 1. If item has a specific track/item name, use it!
  if (!isGenericItemTitle) {
    base = cleanItemTitle;
  } else if (!isGenericDlType) {
    base = cleanType;
  } else if (!isGenericTitle) {
    base = title;
  } else {
    // Try extracting Pin ID or Asset ID from download URL or source URL
    const sourceUrl = mediaResult?.sourceUrl || "";
    const pinIdMatch = sourceUrl.match(/pin\/(\d+)/);
    const cdnFileMatch = dlItem?.url ? dlItem.url.split("?")[0].match(/\/([a-zA-Z0-9_-]{8,32})\.(jpe?g|png|webp|mp4)/i) : null;
    
    const platformName = platform ? (platform.charAt(0).toUpperCase() + platform.slice(1)) : "Media";
    if (pinIdMatch && pinIdMatch[1]) {
      base = `${platformName}_Pin_${pinIdMatch[1]}`;
    } else if (cdnFileMatch && cdnFileMatch[1]) {
      base = `${platformName}_${cdnFileMatch[1]}`;
    } else {
      const typeLabel = mediaCategory === "image" ? "Photo" : mediaCategory === "audio" ? "Audio" : "Video";
      base = `${platformName}_${typeLabel}`;
    }
  }

  // If item has an index or itemIndex, ensure filename includes the unique index
  const itemIdx = dlItem?.itemIndex || dlItem?.index;
  if (itemIdx && !base.endsWith(`(${itemIdx})`) && !base.endsWith(`_${itemIdx}`) && !base.endsWith(`-${itemIdx}`) && !base.includes(`(${itemIdx})`)) {
    base += `_${itemIdx}`;
  }

  // Strip duplicate extension if present in title
  base = base.replace(/\.(mp4|mp3|png|jpg|jpeg|webp|m4a|wav|webm|mov)$/i, "").trim();

  let cleaned = base.replace(/[\\/:*?"<>|]/g, "_");
  cleaned = cleaned.replace(/\s+/g, "_").trim();
  cleaned = cleaned.replace(/_+/g, "_");

  // Apply Filename Format Setting
  if (settings.filenameTemplate === "title-platform" && platform) {
    const platformName = platform.charAt(0).toUpperCase() + platform.slice(1);
    if (!cleaned.toLowerCase().includes(platformName.toLowerCase())) {
      cleaned += `_${platformName}`;
    }
  } else if (settings.filenameTemplate === "title-date") {
    const d = new Date();
    const dStr = `${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, '0')}${String(d.getDate()).padStart(2, '0')}`;
    if (!cleaned.includes(dStr)) {
      cleaned += `_${dStr}`;
    }
  }

  if (cleaned.length > 80) {
    cleaned = cleaned.substring(0, 80);
  }

  if (!cleaned) {
    cleaned = "Download";
  }

  return cleaned + extension;
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
  let itemTitle = dlItem.type || mediaResult?.title || sanitizedFilename;

  console.log(`[DOWNLOAD] Processing: "${sanitizedFilename}" (Category: ${mediaCategory}, Batch: ${Boolean(batchOptions)})`);

  // ==========================================
  // STAGE 1: NETWORK DOWNLOAD / RESOLVE
  // ==========================================
  
  // 1A. Spotify SpotiDown Lazy Resolving
  if (downloadUrl.startsWith("spotidown_resolve:")) {
    console.log("[SPOTIFY RESOLVE] Resolving SpotiDown token for track:", itemTitle);
    const parts = downloadUrl.replace("spotidown_resolve:", "").split("|||");
    const payload = parts[0];
    const cookie = decodeURIComponent(parts[1] || "");
    
    try {
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
        try { parsedData = JSON.parse(data); } catch (_) {}
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
        try { parsedData = JSON.parse(resData); } catch (_) {}
      } else if (resData?.data) {
        parsedData = resData.data;
        if (typeof parsedData === "string") {
          try { parsedData = JSON.parse(parsedData); } catch (_) {}
        }
      }

      const htmlContent = parsedData?.html || (typeof parsedData === "string" ? parsedData : "");
      const parser = new DOMParser();
      const doc = parser.parseFromString(htmlContent, "text/html");
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
        try { conv = JSON.parse(conv); } catch (_) {}
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

  let capDir = "EXTERNAL";
  let targetRelativePath = `Nimiyo/${sanitizedFilename}`;
  
  if (Filesystem) {
    await Filesystem.mkdir({
      path: 'Nimiyo',
      directory: capDir,
      recursive: true
    }).catch((e) => console.warn("[FILESYSTEM] mkdir warning:", e));
  }

  // 1C. Execute Network Download with Anti-403 Headers
  const downloadHeaders = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
    "Accept-Language": "en-US,en;q=0.9,id;q=0.8",
    "Referer": downloadUrl.includes("tiktok") ? "https://www.tiktok.com/" : (downloadUrl.includes("instagram") ? "https://www.instagram.com/" : ""),
    ...(dlItem?.headers || {})
  };

  if (Filesystem && window.Capacitor?.isNativePlatform()) {
    let currentPercent = 0;
    const progressInterval = setInterval(() => {
      if (currentPercent < 85) {
        currentPercent += Math.floor(Math.random() * 6) + 2;
        if (currentPercent > 85) currentPercent = 85;
        updateProgressPercent(currentPercent);
      }
    }, 300);

    try {
      try {
        await Filesystem.downloadFile({
          url: downloadUrl,
          path: targetRelativePath,
          directory: capDir,
          headers: downloadHeaders
        });
      } catch (subfolderErr) {
        targetRelativePath = sanitizedFilename;
        await Filesystem.downloadFile({
          url: downloadUrl,
          path: targetRelativePath,
          directory: capDir,
          headers: downloadHeaders
        });
      }
      clearInterval(progressInterval);
      updateProgressPercent(90);
    } catch (err) {
      clearInterval(progressInterval);
      console.warn("[FILESYSTEM] downloadFile failed, using native CapacitorHttp binary fallback:", err);
      if (downloadCancelled) throw new Error("Aborted");

      updateProgressPercent(10);
      let base64data = "";
      let respContentType = "";

      const CapacitorHttp = window.Capacitor?.Plugins?.CapacitorHttp;
      if (CapacitorHttp) {
        try {
          const httpRes = await CapacitorHttp.request({
            method: "GET",
            url: downloadUrl,
            headers: downloadHeaders,
            responseType: "base64"
          });

          if (httpRes.status && httpRes.status >= 400) {
            throw new Error(`HTTP ${httpRes.status}`);
          }

          base64data = httpRes.data;
          respContentType = httpRes.headers?.["content-type"] || httpRes.headers?.["Content-Type"] || "";
        } catch (capErr) {
          throw new Error(`[NETWORK DOWNLOAD FAILED] ${capErr.message}`);
        }
      } else {
        try {
          const response = await fetch(downloadUrl, { headers: downloadHeaders });
          if (!response.ok) throw new Error(`HTTP status ${response.status}`);
          respContentType = response.headers.get("content-type") || "";
          const blob = await response.blob();
          base64data = await new Promise((resolve, reject) => {
            const reader = new FileReader();
            reader.onloadend = () => resolve(reader.result.split(',')[1]);
            reader.onerror = () => reject(new Error("Base64 conversion failed"));
            reader.readAsDataURL(blob);
          });
        } catch (fetchErr) {
          throw new Error(`[NETWORK DOWNLOAD FAILED] ${fetchErr.message}`);
        }
      }
      
      if (downloadCancelled) throw new Error("Aborted");
      updateProgressPercent(75);

      if (respContentType) {
        const refinedExt = determineExtension(mediaCategory, downloadUrl, respContentType);
        if (refinedExt !== targetExtension) {
          targetExtension = refinedExt;
          sanitizedFilename = buildTargetFilename(dlItem, mediaResult, mediaCategory, currentPlatform, targetExtension);
          targetRelativePath = `Nimiyo/${sanitizedFilename}`;
        }
      }

      // ==========================================
      // STAGE 2: FILE WRITE / TEMPORARY FILE
      // ==========================================
      try {
        await Filesystem.writeFile({
          path: targetRelativePath,
          data: base64data,
          directory: capDir
        });
      } catch (writeErr) {
        targetRelativePath = sanitizedFilename;
        try {
          await Filesystem.writeFile({
            path: targetRelativePath,
            data: base64data,
            directory: capDir
          });
        } catch (rootWriteErr) {
          throw new Error(`[FILE WRITE FAILED] ${rootWriteErr.message}`);
        }
      }
      updateProgressPercent(90);
    }
  } else {
    throw new Error("Filesystem plugin not available");
  }

  // ==========================================
  // STAGE 2B: VERIFY TEMPORARY FILE ON DISK
  // ==========================================
  let statResult = null;
  try {
    statResult = await Filesystem.stat({
      path: targetRelativePath,
      directory: capDir
    });
  } catch (statErr) {
    throw new Error(`[FILE WRITE FAILED] Temporary file does not exist on disk: ${statErr.message}`);
  }

  if (!statResult || typeof statResult.size !== "number" || statResult.size <= 0) {
    throw new Error("[FILE WRITE FAILED] Downloaded temporary file is empty (0 bytes).");
  }

  let uriResult = null;
  try {
    uriResult = await Filesystem.getUri({
      path: targetRelativePath,
      directory: capDir
    });
  } catch (uriErr) {
    throw new Error(`[FILE WRITE FAILED] Could not retrieve temporary file URI: ${uriErr.message}`);
  }

  if (!uriResult || !uriResult.uri) {
    throw new Error("[FILE WRITE FAILED] Invalid temporary file URI returned by storage.");
  }

  // ==========================================
  // STAGE 3: PUBLIC STORAGE (Downloads/Nimiyo/)
  // ==========================================
  const MediaSaver = window.Capacitor?.Plugins?.MediaSaver;
  if (MediaSaver && window.Capacitor?.isNativePlatform()) {
    let mediaSaverRes;
    try {
      mediaSaverRes = await MediaSaver.saveToPublicStorage({
        filePath: uriResult.uri,
        fileName: sanitizedFilename,
        fileType: mediaCategory
      });
    } catch (mediaSaverErr) {
      throw new Error(`[PUBLIC STORAGE FAILED] MediaStore error: ${mediaSaverErr.message || mediaSaverErr}`);
    }

    if (!mediaSaverRes || mediaSaverRes.success !== true || !mediaSaverRes.uri) {
      throw new Error("[PUBLIC STORAGE FAILED] MediaSaver failed to copy file to Downloads/Nimiyo/ storage.");
    }
  }

  // Extract accurate track-specific title & thumbnail for history
  let historyTitle = dlItem.title || dlItem.type || mediaResult?.title || sanitizedFilename;
  historyTitle = historyTitle.replace(/\[(MP3|Cover|MP4|Video|Audio|Photo|Image|HD|SD)\]/gi, "").trim();
  historyTitle = historyTitle.replace(/\.(mp4|mp3|png|jpg|jpeg|webp|m4a|wav|webm|mov)$/i, "").trim();
  if (!historyTitle || historyTitle.length < 2) {
    historyTitle = sanitizedFilename.replace(/\.(mp4|mp3|png|jpg|jpeg|webp|m4a|wav|webm|mov)$/i, "");
  }

  const isImageMedia = mediaCategory === "image" || dlItem.type?.toUpperCase().includes("PHOTO") || dlItem.type?.toUpperCase().includes("IMAGE") || sanitizedFilename.match(/\.(png|jpg|jpeg|webp)$/i);
  let historyThumb = dlItem.thumbnail || dlItem.cover;
  
  // For downloaded images, prioritize the exact downloaded local file URI!
  if (isImageMedia && uriResult?.uri && window.Capacitor?.convertFileSrc) {
    historyThumb = window.Capacitor.convertFileSrc(uriResult.uri);
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
  const MediaSaver = window.Capacitor?.Plugins?.MediaSaver;
  if (MediaSaver && window.Capacitor?.isNativePlatform()) {
    try {
      await MediaSaver.showSystemNotification({
        title,
        message,
        progress,
        max,
        isCompleted
      });
    } catch (e) {
      console.warn("System notification update failed:", e);
    }
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

  const activeExisting = activeDownloadQueue.filter(t => t.status === 'downloading' || t.status === 'pending');
  if (activeExisting.length === 0) {
    totalSessionCount = 0;
  }
  totalSessionCount += items.length;

  items.forEach((dlItem, idx) => {
    if (items.length > 1 && !dlItem.itemIndex && !dlItem.index) {
      dlItem.itemIndex = idx + 1;
    }
    const mediaCategory = detectMediaCategory(dlItem, mediaResult);
    const targetExtension = determineExtension(mediaCategory, dlItem.url);
    const sanitizedFilename = buildTargetFilename(dlItem, mediaResult, mediaCategory, currentPlatform, targetExtension);
    
    let historyTitle = dlItem.title || dlItem.type || mediaResult?.title || sanitizedFilename;
    if (historyTitle.toUpperCase() === "PHOTO" || historyTitle.toUpperCase() === "IMAGE") {
      historyTitle = `${mediaResult?.title || 'Photo'} (${dlItem.itemIndex || (idx + 1)})`;
    }
    historyTitle = historyTitle.replace(/\[(MP3|Cover|MP4|Video|Audio|Photo|Image|HD|SD)\]/gi, "").trim();
    historyTitle = historyTitle.replace(/\.(mp4|mp3|png|jpg|jpeg|webp|m4a|wav|webm|mov)$/i, "").trim();
    if (!historyTitle || historyTitle.length < 2) {
      historyTitle = sanitizedFilename.replace(/\.(mp4|mp3|png|jpg|jpeg|webp|m4a|wav|webm|mov)$/i, "");
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
  if (items.length === 1) {
    const singleTitle = activeDownloadQueue[activeDownloadQueue.length - 1].title;
    showToast(getTranslation("toastDownloadingSingle", { title: singleTitle }), "info");
  } else {
    showToast(getTranslation("toastDownloadingMulti", { count: items.length }), "info");
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
  showToast(getTranslation("toastDownloadCancelled"), "error");
}

// Process Queue Worker
async function processDownloadQueue() {
  if (isProcessingQueue) return;
  isProcessingQueue = true;

  while (true) {
    const nextTask = activeDownloadQueue.find(t => t.status === 'pending' && !t.isCancelled);
    if (!nextTask) break;

    nextTask.status = 'downloading';
    nextTask.progress = 10;
    updateBalloonQueueUI();

    updateSystemDownloadNotification("Nimiyo Downloader", `Mengunduh: ${nextTask.filename}`, 10, 100, false);

    try {
      await downloadSingleFile(
        nextTask.dlItem,
        nextTask.mediaResult,
        null,
        0,
        (p) => {
          nextTask.progress = p;
          updateBalloonQueueUI();
        },
        nextTask
      );

      if (nextTask.isCancelled) {
        throw new Error("Aborted");
      }

      nextTask.status = 'completed';
      nextTask.progress = 100;
      completedQueueCount++;
      updateBalloonQueueUI();

      // Remove completed task smoothly after a moment
      setTimeout(() => {
        activeDownloadQueue = activeDownloadQueue.filter(t => t.id !== nextTask.id);
        updateBalloonQueueUI();
      }, 700);

    } catch (err) {
      console.error(`[QUEUE ERROR] Task ${nextTask.title} failed:`, err);
      nextTask.status = 'error';
      activeDownloadQueue = activeDownloadQueue.filter(t => t.id !== nextTask.id);
      updateBalloonQueueUI();

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

// Native Android System Notification
async function updateSystemDownloadNotification(title, message, progress = 0, max = 100, isCompleted = false) {
  const MediaSaver = window.Capacitor?.Plugins?.MediaSaver;
  if (MediaSaver && window.Capacitor?.isNativePlatform()) {
    try {
      await MediaSaver.showSystemNotification({
        title,
        message,
        progress,
        max,
        isCompleted
      });
    } catch (e) {
      console.warn("System notification update failed:", e);
    }
  }
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
  const isAudio = item.mediaType === "audio" || item.filename?.endsWith(".mp3") || item.filename?.endsWith(".m4a");
  const isImage = item.mediaType === "image" || item.filename?.match(/\.(png|jpg|jpeg|webp)$/i);
  const subFolder = isImage ? "ImageYo" : isAudio ? "AudioYo" : "VideoYo";
  const modal = document.getElementById("mediaPlayerModal");
  const container = document.getElementById("mediaPlayerContainer");
  const title = document.getElementById("mediaPlayerTitle");

  let webUrl = "";

  const MediaSaver = window.Capacitor?.Plugins?.MediaSaver;
  if (MediaSaver && window.Capacitor?.isNativePlatform()) {
    try {
      const res = await MediaSaver.getMediaData({
        fileName: item.filename,
        subFolder: subFolder,
        fileType: item.mediaType
      });

      if (res?.base64) {
        const mime = isAudio ? "audio/mpeg" : isImage ? "image/jpeg" : "video/mp4";
        const byteCharacters = atob(res.base64);
        const byteNumbers = new Array(byteCharacters.length);
        for (let i = 0; i < byteCharacters.length; i++) {
          byteNumbers[i] = byteCharacters.charCodeAt(i);
        }
        const byteArray = new Uint8Array(byteNumbers);
        const blob = new Blob([byteArray], { type: mime });
        webUrl = URL.createObjectURL(blob);
      } else if (res?.uri) {
        webUrl = window.Capacitor.convertFileSrc ? window.Capacitor.convertFileSrc(res.uri) : res.uri;
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
        } catch (_) {}
      }
      if (fileUri) {
        webUrl = window.Capacitor.convertFileSrc ? window.Capacitor.convertFileSrc(fileUri) : fileUri;
      }
    } catch (e) {
      console.warn("Filesystem URI lookup failed:", e);
    }
  }

  if (modal && container) {
    if (title) title.innerText = item.title || item.filename;
    container.innerHTML = "";

    const autoPlayAttr = settings.autoPlay !== false ? "autoplay" : "";
    const autoLoopAttr = settings.autoLoop !== false ? "loop" : "";

    if (webUrl) {
      if (isAudio) {
        container.innerHTML = `
          <div style="display: flex; flex-direction: column; align-items: center; width: 100%; padding: 20px;">
            <img src="${item.thumbnail || 'nimiyo_icon.webp'}" style="width: 130px; height: 130px; border-radius: 12px; margin-bottom: 16px; object-fit: cover; border: 2px solid var(--border-color); box-shadow: 3px 3px 0 var(--border-color);">
            <audio controls ${autoPlayAttr} ${autoLoopAttr} style="width: 100%;">
              <source src="${webUrl}" type="audio/mpeg">
              Your browser does not support the audio element.
            </audio>
          </div>
        `;
      } else if (isImage) {
        container.innerHTML = `
          <div style="display: flex; justify-content: center; align-items: center; width: 100%; padding: 12px;">
            <img src="${webUrl}" style="max-width: 100%; max-height: 60vh; border-radius: var(--radius); border: 2px solid var(--border-color); object-fit: contain;">
          </div>
        `;
      } else {
        container.innerHTML = `
          <video controls ${autoPlayAttr} ${autoLoopAttr} playsinline style="width: 100%; max-height: 55vh; border-radius: var(--radius); border: 2px solid var(--border-color);">
            <source src="${webUrl}" type="video/mp4">
            Your browser does not support the video element.
          </video>
        `;
      }
      modal.classList.remove("hidden");
      showToast(getTranslation("toastPlaying", { title: item.title || item.filename }), "info");
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
      showToast(getTranslation("toastPlaying", { title: item.title || item.filename }), "info");
      return;
    } catch (e) {
      console.warn("MediaSaver.openMediaFile failed:", e);
    }
  }

  showToast(getTranslation("toastPlaying", { title: item.title || item.filename }), "info");
}

// Render History list in Modal
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
    const isPlayable = item.mediaType === "audio" || item.mediaType === "video" || 
      item.filename?.endsWith(".mp3") || item.filename?.endsWith(".mp4") ||
      item.filename?.endsWith(".m4a") || item.filename?.endsWith(".webm");

    const playBtnHtml = isPlayable ? `
      <button class="history-play-btn" data-index="${index}" aria-label="${getTranslation('historyPlayTitle')}" title="${getTranslation('historyPlayTitle')}">
        <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor">
          <polygon points="6 4 20 12 6 20 6 4"></polygon>
        </svg>
      </button>
    ` : "";

    const folderBtnHtml = `
      <button class="history-folder-btn" data-index="${index}" aria-label="${getTranslation('historyFolderTitle')}" title="${getTranslation('historyFolderTitle')}">
        <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor">
          <path d="M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z"/>
        </svg>
      </button>
    `;
    
    card.innerHTML = `
      <img src="${item.thumbnail || 'nimiyo_icon.webp'}" alt="Thumb" class="history-thumbnail">
      <div class="history-details">
        <h4 class="history-title">${item.title || item.filename}</h4>
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
      if (item.mediaType === "image" || item.filename?.match(/\.(png|jpg|jpeg|webp)$/i)) {
        if (Filesystem && window.Capacitor?.isNativePlatform()) {
          Filesystem.getUri({ path: `Nimiyo/ImageYo/${item.filename}`, directory: "EXTERNAL" })
            .catch(() => Filesystem.getUri({ path: `ImageYo/${item.filename}`, directory: "EXTERNAL" }))
            .catch(() => Filesystem.getUri({ path: `Nimiyo/${item.filename}`, directory: "EXTERNAL" }))
            .catch(() => Filesystem.getUri({ path: item.filename, directory: "EXTERNAL" }))
            .then(res => {
              if (res?.uri) {
                imgThumb.src = window.Capacitor.convertFileSrc(res.uri);
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
      } else {
        loadSecureThumbnail(item.thumbnail, imgThumb);
      }
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
  switch(platform) {
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

  if (progressBox) progressBox.classList.add("hidden");
  if (actionRow) actionRow.classList.remove("hidden");
  if (startBtn) {
    startBtn.disabled = false;
    startBtn.innerText = getTranslation("btnUpdateNow");
  }

  modal.classList.remove("hidden");
  triggerHaptic();
}

// Download and Install APK Update
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
    const fileName = "nimiyo_update.apk";
    const MediaSaver = window.Capacitor?.Plugins?.MediaSaver;

    if (Filesystem && window.Capacitor?.isNativePlatform()) {
      let apkPath = "";
      try {
        const dlRes = await Filesystem.downloadFile({
          url: apkUrl,
          path: fileName,
          directory: "CACHE"
        });
        apkPath = dlRes?.path || fileName;
      } catch (dlErr) {
        const CapHttp = window.Capacitor?.Plugins?.CapacitorHttp;
        if (CapHttp) {
          const res = await CapHttp.request({
            method: "GET",
            url: apkUrl,
            responseType: "base64"
          });
          const writeRes = await Filesystem.writeFile({
            path: fileName,
            data: res.data,
            directory: "CACHE"
          });
          apkPath = writeRes?.uri || fileName;
        } else {
          throw dlErr;
        }
      }

      clearInterval(simInterval);
      if (progressBarFill) progressBarFill.style.width = "100%";
      if (progressPercentText) progressPercentText.innerText = "100%";
      if (progressText) progressText.innerText = getTranslation("btnPermissionGranted");

      const uriRes = await Filesystem.getUri({
        path: fileName,
        directory: "CACHE"
      });

      if (MediaSaver) {
        await MediaSaver.installApk({
          filePath: uriRes?.uri || fileName
        });
      }
    } else {
      clearInterval(simInterval);
      window.open(apkUrl, "_blank");
      showToast(getTranslation("toastDownloadSuccess"), "success");
    }

  } catch (err) {
    clearInterval(simInterval);
    console.error("downloadAndInstallUpdate error:", err);
    if (progressBox) progressBox.classList.add("hidden");
    if (actionRow) actionRow.classList.remove("hidden");
    showToast(`${getTranslation("toastDownloadFailManualServer")}: ${err.message}`, "error");
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
