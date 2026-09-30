// share.js — NIMIYO Quick Save Dialog Controller

// Localization dictionary for Share Dialog
const shareTranslations = {
  en: {
    panelTitle: "NIMIYO Quick Save 🐤",
    panelSub: "Analyze & download media",
    analyzing: "Analyzing link...",
    cancel: "Cancel",
    close: "Close",
    retry: "Retry",
    openApp: "Open App",
    minimize: "Minimize",
    availableDownloads: "Download Links",
    download: "DOWNLOAD",
    downloadAll: "DOWNLOAD ALL ({count})",
    ready: "Ready to download",
    unsupported: "Unsupported platform link.",
    analysisFailed: "Failed to analyze link. Please check URL or switch server.",
    errorOffline: "No internet connection. Please check your data or Wi-Fi.",
    errorTimeout: "Connection timed out. Server or network is slow.",
    errorSpotifyPlaylistPrivate: "Spotify playlist is private or not found. Make sure the playlist is set to Public on Spotify.",
    errorSpotifyTrackNotFound: "Spotify track or album not found, private, or region-restricted.",
    errorInstagramPrivate: "Instagram post or Reel is private, restricted, or has been deleted.",
    errorTikTokPrivate: "TikTok video is private or has been removed by the creator.",
    errorYouTubeUnavailable: "YouTube video or playlist is private, age-restricted, or unavailable.",
    errorTwitterPrivate: "X (Twitter) post not found or the account is private.",
    errorFacebookPrivate: "Facebook post is private, restricted, or requires login.",
    errorMediaNotFound: "Media not found or content has been deleted/private.",
    errorServerBlocked: "Scraper server is busy or protected. Please switch server.",
    cancelled: "Analysis cancelled.",
    saving: "Saving file...",
    saved: "File saved successfully!",
    downloading: "Downloading...",
    downloadFailed: "Download failed",
    backgroundDl: "Downloading in background...",
    server: "Server",
    switchServer: "Switched to {server}",
    photo: "PHOTO",
    video: "VIDEO",
    audio: "AUDIO",
    noUrl: "No URL",
    downloadFailedSwitchServer: "Download Failed - Switching to Server {server}...",
    downloadFailedManualServer: "Download Failed - Switch Server"
  },
  id: {
    panelTitle: "NIMIYO Simpan 🐤",
    panelSub: "Analisis & unduh media",
    analyzing: "Menganalisis tautan...",
    cancel: "Batal",
    close: "Tutup",
    retry: "Coba Lagi",
    openApp: "Buka Aplikasi",
    minimize: "Minimize",
    availableDownloads: "Tautan Unduhan",
    download: "UNDUH",
    downloadAll: "UNDUH SEMUA ({count})",
    ready: "Siap diunduh",
    unsupported: "Tautan platform tidak didukung.",
    analysisFailed: "Gagal menganalisis tautan. Periksa link atau coba server lain.",
    errorOffline: "Tidak ada koneksi internet. Periksa koneksi data atau Wi-Fi Anda.",
    errorTimeout: "Koneksi batas waktu (timeout). Server atau jaringan lambat.",
    errorSpotifyPlaylistPrivate: "Playlist Spotify bersifat privat atau tidak ditemukan. Pastikan playlist disetel Publik di Spotify.",
    errorSpotifyTrackNotFound: "Lagu atau album Spotify tidak ditemukan atau dibatasi wilayah/privat.",
    errorInstagramPrivate: "Postingan/Reel Instagram bersifat privat, dibatasi, atau telah dihapus.",
    errorTikTokPrivate: "Video TikTok bersifat privat atau telah dihapus oleh pengunggah.",
    errorYouTubeUnavailable: "Video/Playlist YouTube bersifat privat, dibatasi usia, atau tidak tersedia.",
    errorTwitterPrivate: "Postingan X (Twitter) tidak ditemukan atau akun digembok (privat).",
    errorFacebookPrivate: "Postingan Facebook bersifat privat atau membutuhkan login.",
    errorMediaNotFound: "Media tidak ditemukan atau konten telah dihapus/bersifat privat.",
    errorServerBlocked: "Server scraper sedang padat atau terproteksi. Coba ganti server.",
    cancelled: "Analisis dibatalkan.",
    saving: "Menyimpan berkas...",
    saved: "Berkas berhasil disimpan!",
    downloading: "Sedang mengunduh...",
    downloadFailed: "Unduhan gagal",
    backgroundDl: "Mengunduh di latar belakang...",
    server: "Server",
    switchServer: "Beralih ke {server}",
    photo: "FOTO",
    video: "VIDEO",
    audio: "AUDIO",
    noUrl: "URL tidak tersedia",
    downloadFailedSwitchServer: "Download Gagal - Ganti Server ke {server}...",
    downloadFailedManualServer: "Download Gagal - Ganti Server"
  },
  zh: {
    panelTitle: "NIMIYO 保存 🐤",
    panelSub: "解析并下载媒体",
    analyzing: "正在解析链接...",
    cancel: "取消",
    close: "关闭",
    retry: "重试",
    openApp: "打开应用",
    minimize: "最小化",
    availableDownloads: "下载链接",
    download: "下载",
    downloadAll: "全部下载 ({count})",
    ready: "准备下载",
    unsupported: "不支持的平台链接。",
    analysisFailed: "解析链接失败。请检查链接或更换服务器。",
    errorOffline: "无网络连接。请检查您的移动数据或 Wi-Fi 连接。",
    errorTimeout: "连接超时。服务器或网络响应缓慢。",
    errorSpotifyPlaylistPrivate: "Spotify 歌单为私密或未找到。请确保该歌单在 Spotify 上已设为公开。",
    errorSpotifyTrackNotFound: "Spotify 歌曲或专辑未找到、私密或受地区限制。",
    errorInstagramPrivate: "Instagram 帖子或 Reel 属于私密、受限或已被删除。",
    errorTikTokPrivate: "TikTok 视频属于私密或已被创作者删除。",
    errorYouTubeUnavailable: "YouTube 视频或播放列表属于私密、年龄受限或不可用。",
    errorTwitterPrivate: "X (Twitter) 推文未找到或该账号为私密账号。",
    errorFacebookPrivate: "Facebook 帖子为私密或需要登录访问。",
    errorMediaNotFound: "未找到媒体，或内容已被删除/设为私密。",
    errorServerBlocked: "解析服务器繁忙或受到保护。请切换服务器。",
    cancelled: "已取消解析。",
    saving: "正在保存文件...",
    saved: "文件保存成功！",
    downloading: "正在下载...",
    downloadFailed: "下载失败",
    backgroundDl: "正在后台下载...",
    server: "服务器",
    switchServer: "已切换至 {server}",
    photo: "图片",
    video: "视频",
    audio: "音频",
    noUrl: "无可用 URL",
    downloadFailedSwitchServer: "下载失败 - 正在自动切换至服务器 {server}...",
    downloadFailedManualServer: "下载失败 - 请切换服务器"
  },
  ja: {
    panelTitle: "NIMIYO 保存 🐤",
    panelSub: "メディアの解析とダウンロード",
    analyzing: "リンクを解析中...",
    cancel: "キャンセル",
    close: "閉じる",
    retry: "再試行",
    openApp: "アプリを開く",
    minimize: "最小化",
    availableDownloads: "ダウンロードリンク",
    download: "ダウンロード",
    downloadAll: "すべてダウンロード ({count})",
    ready: "ダウンロード可能",
    unsupported: "非対応のプラットフォームです。",
    analysisFailed: "リンクの解析に失敗しました。URLを確認するか、サーバーを変更してください。",
    errorOffline: "インターネット接続がありません。データ通信または Wi-Fi を確認してください。",
    errorTimeout: "接続がタイムアウトしました。サーバーまたはネットワークが遅延しています。",
    errorSpotifyPlaylistPrivate: "Spotify プレイリストが非公開か見つかりません。公開設定になっているか確認してください。",
    errorSpotifyTrackNotFound: "Spotify の楽曲またはアルバムが見つからないか、非公開または地域制限されています。",
    errorInstagramPrivate: "Instagram の投稿または Reel は非公開、制限されているか、削除されています。",
    errorTikTokPrivate: "TikTok 動画は非公開か、投稿者によって削除されています。",
    errorYouTubeUnavailable: "YouTube 動画または再生リストが非公開、年齢制限、または利用できません。",
    errorTwitterPrivate: "X (Twitter) の投稿が見つからないか、アカウントが非公開です。",
    errorFacebookPrivate: "Facebook の投稿は非公開か、ログインが必要です。",
    errorMediaNotFound: "メディアが見つからないか、非公開または削除されています。",
    errorServerBlocked: "スクレイパーサーバーが混雑または保護されています。サーバーを変更してください。",
    cancelled: "解析をキャンセルしました。",
    saving: "ファイルを保存中...",
    saved: "ファイルを正常に保存しました！",
    downloading: "ダウンロード中...",
    downloadFailed: "ダウンロードに失敗しました",
    backgroundDl: "バックグラウンドでダウンロード中...",
    server: "サーバー",
    switchServer: "{server} に切り替えました",
    photo: "画像",
    video: "動画",
    audio: "音声",
    noUrl: "URLなし",
    downloadFailedSwitchServer: "ダウンロード失敗 - サーバー {server} に切り替えています...",
    downloadFailedManualServer: "ダウンロード失敗 - サーバーを変更してください"
  }
};

let currentLang = 'en';
let activeUrl = "";
let currentPlatform = "";
let activeResult = null;
let isAnalyzing = false;
let analysisAborted = false;
let currentServerIndex = 0;

// Platform details & Regex mapping matching app.js
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
  rednote: { name: "RedNote", domains: /(rednote\.com|xiaohongshu\.com|xhslink\.com|xhslink\.cn)/i, color: "#FF2442" },
  shopee: { name: "Shopee", domains: /(shopee\.[a-z.]+|shp\.ee)/i, color: "#EE4D2D" }
};

const fallbackChains = {
  tiktok: ['snaptik', 'tiktokio', 'ssstik', 'direct'],
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

function t(key, params = {}) {
  let str = shareTranslations[currentLang]?.[key] || shareTranslations['en']?.[key] || key;
  if (params && typeof params === "object") {
    Object.keys(params).forEach(p => {
      str = str.replace(new RegExp(`\\{${p}\\}`, "g"), params[p]);
    });
  }
  return str;
}

let activeDownloadProgressCallback = null;

window.__onNativeDownloadProgress = function(reqId, percent) {
  if (typeof activeDownloadProgressCallback === "function") {
    try {
      activeDownloadProgressCallback(reqId, Number(percent) || 0);
    } catch (_) {}
  }
};

function updateDownloadProgressCard(visible, title, percent) {
  const card = document.getElementById("downloadProgressCard");
  const titleEl = document.getElementById("progressTitle");
  const percentEl = document.getElementById("progressPercent");
  const fillEl = document.getElementById("progressBarFill");
  if (!card) return;

  if (!visible) {
    card.classList.add("hidden");
    return;
  }

  card.classList.remove("hidden");
  if (titleEl && title) titleEl.innerText = title;
  const p = Math.max(0, Math.min(100, Math.round(Number(percent) || 0)));
  if (percentEl) percentEl.innerText = p + "%";
  if (fillEl) fillEl.style.width = p + "%";
}

function applyAccentColor(accentColor) {
  if (!accentColor || typeof accentColor !== "string") return;
  const clean = accentColor.trim();
  if (!clean) return;

  document.documentElement.style.setProperty('--accent', clean);
  document.documentElement.style.setProperty('--accent-color', clean);
  document.documentElement.style.setProperty('--primary-accent', clean);
  if (document.body) {
    document.body.style.setProperty('--accent', clean);
    document.body.style.setProperty('--accent-color', clean);
    document.body.style.setProperty('--primary-accent', clean);
  }
}

function initLanguageAndTheme() {
  let parsed = {};

  try {
    if (window.NimiyoShareBridge && typeof window.NimiyoShareBridge.getAppSettings === 'function') {
      const raw = window.NimiyoShareBridge.getAppSettings();
      if (raw) parsed = JSON.parse(raw);
    } else if (window.NimidzShareBridge && typeof window.NimidzShareBridge.getAppSettings === 'function') {
      const raw = window.NimidzShareBridge.getAppSettings();
      if (raw) parsed = JSON.parse(raw);
    }
  } catch (_) {}

  const urlParams = new URLSearchParams(window.location.search);
  if (!parsed.uiTheme) {
    if (urlParams.get("theme")) {
      parsed.uiTheme = urlParams.get("theme");
    }
    if (urlParams.get("darkMode") !== null) {
      parsed.darkMode = (urlParams.get("darkMode") === "1" || urlParams.get("darkMode") === "true");
    }
  }

  if (!parsed.accentColor && urlParams.get("accentColor")) {
    parsed.accentColor = urlParams.get("accentColor");
  }

  if (!parsed.uiTheme || !parsed.accentColor) {
    const savedSettings = localStorage.getItem("nimiyo_settings");
    if (savedSettings) {
      try {
        parsed = { ...parsed, ...JSON.parse(savedSettings) };
      } catch (_) { }
    }
    if (!parsed.accentColor) {
      parsed.accentColor = localStorage.getItem("nimiyo_accent_color") || "";
    }
  }

  const theme = parsed.uiTheme || 'neobrutalism';
  const isDark = !!parsed.darkMode;

  document.documentElement.classList.remove('theme-neobrutalism', 'theme-softui');
  document.documentElement.classList.add('theme-' + theme);
  if (isDark) document.documentElement.classList.add('dark-mode');
  else document.documentElement.classList.remove('dark-mode');

  document.body.classList.remove('theme-neobrutalism', 'theme-softui');
  document.body.classList.add('theme-' + theme);
  if (isDark) document.body.classList.add('dark-mode');
  else document.body.classList.remove('dark-mode');

  if (parsed.accentColor) {
    applyAccentColor(parsed.accentColor);
  }

  if (parsed.language && shareTranslations[parsed.language]) {
    currentLang = parsed.language;
  } else {
    const navLang = (navigator.language || '').toLowerCase();
    if (navLang.startsWith('id')) currentLang = 'id';
    else if (navLang.startsWith('zh')) currentLang = 'zh';
    else if (navLang.startsWith('ja')) currentLang = 'ja';
    else currentLang = 'en';
  }

  // Update static UI text
  const labelAvail = document.getElementById("labelAvailableDownloads");
  if (labelAvail) labelAvail.innerText = t("availableDownloads");

  const statusTextEl = document.getElementById("statusText");
  if (statusTextEl) statusTextEl.innerText = t("analyzing");

  const cancelAnalyzeTextEl = document.getElementById("cancelAnalyzeBtnText");
  if (cancelAnalyzeTextEl) cancelAnalyzeTextEl.innerText = t("cancel").toUpperCase();

  const openInAppBtn = document.getElementById("openInAppBtn");
  if (openInAppBtn) openInAppBtn.innerText = t("openApp").toUpperCase();

  const minimizeBtn = document.getElementById("minimizeBtn");
  if (minimizeBtn) minimizeBtn.innerText = t("minimize").toUpperCase();

  const errorCloseBtn = document.getElementById("errorCloseBtn");
  if (errorCloseBtn) errorCloseBtn.innerText = t("close");

  const errorRetryBtn = document.getElementById("errorRetryBtn");
  if (errorRetryBtn) errorRetryBtn.innerText = t("retry");

  updateServerButtonLabel();
}

window.applyNimiyoSettings = function(newSettings) {
  if (!newSettings || typeof newSettings !== 'object') return;
  const theme = newSettings.uiTheme || 'neobrutalism';
  const isDark = !!newSettings.darkMode;

  document.documentElement.classList.remove('theme-neobrutalism', 'theme-softui');
  document.documentElement.classList.add('theme-' + theme);
  if (isDark) document.documentElement.classList.add('dark-mode');
  else document.documentElement.classList.remove('dark-mode');

  document.body.classList.remove('theme-neobrutalism', 'theme-softui');
  document.body.classList.add('theme-' + theme);
  if (isDark) document.body.classList.add('dark-mode');
  else document.body.classList.remove('dark-mode');

  if (newSettings.accentColor) {
    applyAccentColor(newSettings.accentColor);
  }

  if (newSettings.language && shareTranslations[newSettings.language]) {
    currentLang = newSettings.language;
    initLanguageAndTheme();
  }
};

function detectPlatform(url) {
  if (!url) return null;
  const match = Object.keys(platformMapping).find(k => platformMapping[k].domains.test(url));
  return match || null;
}

function updateServerButtonLabel() {
  const serverLabel = document.getElementById("serverLabel");
  if (!serverLabel) return;

  const platform = currentPlatform || (activeUrl ? detectPlatform(activeUrl) : null);
  const servers = platform ? (fallbackChains[platform] || ['direct']) : ['direct'];
  const activeServerName = servers[currentServerIndex % servers.length] || 'direct';

  const formatted = activeServerName === 'direct'
    ? t("server")
    : (activeServerName.charAt(0).toUpperCase() + activeServerName.slice(1));

  serverLabel.innerText = formatted;
}

// Media category & Extension detector matching app.js
function detectMediaCategory(dlItem, mediaResult) {
  const platform = currentPlatform || "";
  const itemType = String(dlItem?.type || "").toUpperCase();
  const resType = String(mediaResult?.type || "").toUpperCase();
  const quality = String(dlItem?.quality || "").toUpperCase();
  const rawUrl = String(dlItem?.url || "").toLowerCase();

  // 1. Audio Platforms & Types
  if (
    platform === "spotify" ||
    platform === "applemusic" ||
    platform === "bandcamp" ||
    itemType.includes("[MP3]") ||
    itemType.includes("MP3") ||
    itemType.includes("AUDIO") ||
    itemType.includes("MUSIC") ||
    itemType.includes("TRACK") ||
    itemType.includes("SONG") ||
    itemType.includes("M4A") ||
    itemType.includes("FLAC") ||
    itemType.includes("WAV") ||
    resType === "AUDIO" ||
    resType === "MUSIC" ||
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

function determineExtension(mediaCategory, url = "") {
  const lowerUrl = String(url || "").split("?")[0].toLowerCase();

  if (mediaCategory === "audio") {
    if (lowerUrl.endsWith(".m4a")) return ".m4a";
    if (lowerUrl.endsWith(".wav")) return ".wav";
    if (lowerUrl.endsWith(".flac")) return ".flac";
    return ".mp3";
  }

  if (mediaCategory === "image") {
    if (lowerUrl.endsWith(".png")) return ".png";
    if (lowerUrl.endsWith(".webp")) return ".webp";
    return ".jpg";
  }

  // Video
  if (lowerUrl.endsWith(".webm")) return ".webm";
  if (lowerUrl.endsWith(".mov")) return ".mov";
  if (lowerUrl.endsWith(".mkv")) return ".mkv";
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

function buildTargetFilename(dlItem, mediaResult, mediaCategory, platform, extension) {
  const isInstagram = (platform && platform.toLowerCase() === "instagram") || (currentPlatform && currentPlatform.toLowerCase() === "instagram") || (mediaResult?.sourceUrl && mediaResult.sourceUrl.includes("instagram.com"));
  let title = (mediaResult?.title || mediaResult?.description || "").trim();
  let itemTitle = (dlItem?.title || dlItem?.name || "").trim();
  let dlType = (dlItem?.type || "").trim();

  let base = "";

  if (itemTitle && !isGenericMediaTitle(itemTitle) && itemTitle !== "Instagram Content") {
    base = itemTitle;
  } else if (dlType && !isGenericMediaTitle(dlType) && (/^\d+[\.\s]/.test(dlType) || dlType.includes(" - "))) {
    base = dlType.replace(/\s*•\s*\(AUDIO\).*$/i, "").replace(/\s*\[(MP3|M4A|Cover|HD|SD)\]/gi, "").trim();
  } else if (title && !isGenericMediaTitle(title)) {
    base = title;
  } else if (mediaResult?.description && !isGenericMediaTitle(mediaResult.description)) {
    base = mediaResult.description;
  } else {
    if (isInstagram) {
      base = "Instagram Content";
    } else {
      const sourceUrl = mediaResult?.sourceUrl || activeUrl || "";
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

  const itemIdx = dlItem?.itemIndex || dlItem?.index;
  if (itemIdx && !base.endsWith(`_${itemIdx}`) && !base.includes(`(${itemIdx})`) && !base.startsWith(`${itemIdx}.`) && !base.startsWith(`0${itemIdx}.`)) {
    base += `_${itemIdx}`;
  }

  base = base.replace(/\.(mp4|mp3|png|jpg|jpeg|webp|m4a|wav|webm|mov)$/i, "").trim();
  let cleaned = base.replace(/[\\/:*?"<>|#%&{}$!'@+`=~]/g, "_").trim();
  cleaned = cleaned.replace(/[\s_]+/g, "_").replace(/^_+|_+$/g, "");

  if (cleaned.length > 80) cleaned = cleaned.substring(0, 80).replace(/_+$/, "");
  if (!cleaned || isGenericMediaTitle(cleaned)) {
    if (isInstagram) {
      cleaned = "Instagram_Content" + (itemIdx ? `_${itemIdx}` : "");
    } else {
      cleaned = (platform ? platform.charAt(0).toUpperCase() + platform.slice(1) : "Nimiyo") + "_" + Date.now();
    }
  }

  return cleaned + (extension.startsWith(".") ? extension : `.${extension}`);
}

// Global hook called by Native ShareActivity on URL extraction
window.onShareUrlReady = function (url) {
  if (!url) return;
  activeUrl = url.trim();

  const platform = detectPlatform(activeUrl);
  currentPlatform = platform;
  currentServerIndex = 0;

  const badge = document.getElementById("platformBadge");
  const mediaTitle = document.getElementById("mediaTitle");

  if (platform) {
    badge.innerText = (platformMapping[platform].name).toUpperCase();
    badge.style.backgroundColor = platformMapping[platform].color;
    badge.style.color = "#FFFFFF";
  } else {
    badge.innerText = "LINK";
    badge.style.backgroundColor = "var(--accent-color)";
    badge.style.color = "#121212";
  }

  mediaTitle.innerText = t("analyzing");
  updateServerButtonLabel();

  // Auto start analyzing
  startAnalyze();
};

// Switch Server Button handler
window.switchServer = function () {
  const platform = currentPlatform || (activeUrl ? detectPlatform(activeUrl) : null);
  if (!platform) return;

  const servers = fallbackChains[platform] || ['direct'];
  if (servers.length <= 1) {
    showToast(t("switchServer", { server: servers[0] }));
    return;
  }

  currentServerIndex = (currentServerIndex + 1) % servers.length;
  const newServerName = servers[currentServerIndex];
  const formatted = newServerName.charAt(0).toUpperCase() + newServerName.slice(1);

  updateServerButtonLabel();
  showToast(t("switchServer", { server: formatted }));

  // Re-run analysis with the new chosen server prioritized
  startAnalyze(true);
};

async function formatShareErrorMessage(url, platform, failureDetails = []) {
  const lowerUrl = (url || "").toLowerCase();
  const allErrorsText = failureDetails
    .map(f => (f.message || ""))
    .join(" ")
    .toLowerCase();

  // 1. Comprehensive Network / Offline detection
  const isNetworkFailure =
    (typeof navigator !== "undefined" && navigator.onLine === false) ||
    /unable to resolve host|no address associated|network is unreachable|unreachable|failed to connect|failed to fetch|networkerror|network error|unknownhost|unknown host|connectexception|socketexception|sockettimeout|connection refused|connection reset|route to host|no route|ehostunreach|enotfound|econnrefused|econnreset|net::err|internet_disconnected|name_not_resolved|network request failed/i.test(allErrorsText);

  if (isNetworkFailure) {
    return t("errorOffline");
  }

  // 2. Check if ANY scraper reached an HTTP server
  const hadAnyHttpContact = failureDetails.some(f => f.statusCode && f.statusCode > 0);

  // If no scraper ever reached any HTTP endpoint, do a quick probe (max 600ms)
  if (!hadAnyHttpContact) {
    try {
      await Promise.race([
        fetch("https://www.google.com/generate_204", { method: "HEAD", mode: "no-cors", cache: "no-store" }),
        new Promise((_, reject) => setTimeout(() => reject(new Error("probe_timeout")), 600))
      ]);
    } catch (_) {
      return t("errorOffline");
    }
  }

  // 3. Timeout detection
  const isTimeout = /timed out|timeout|request timeout/i.test(allErrorsText);
  if (isTimeout && failureDetails.length > 0 && failureDetails.every(f => /timed out|timeout/i.test(f.message || ""))) {
    return t("errorTimeout");
  }

  // 4. Platform-specific diagnostics ONLY IF content/server was actually reached or error explicitly mentions private/deleted
  if (platform === "spotify") {
    if (lowerUrl.includes("/playlist/")) {
      return t("errorSpotifyPlaylistPrivate");
    }
    return t("errorSpotifyTrackNotFound");
  }

  if (platform === "instagram") {
    if (/private|login|checkpoint|restricted|not found|404|removed|deleted|empty/i.test(allErrorsText) || hadAnyHttpContact) {
      return t("errorInstagramPrivate");
    }
  }

  if (platform === "tiktok") {
    if (/private|deleted|removed|not found|404|unavailable/i.test(allErrorsText) || hadAnyHttpContact) {
      return t("errorTikTokPrivate");
    }
  }

  if (platform === "youtube") {
    if (/private|age|restricted|not available|unavailable|removed|deleted|404|playlist/i.test(allErrorsText) || hadAnyHttpContact) {
      return t("errorYouTubeUnavailable");
    }
  }

  if (platform === "twitter") {
    if (/private|suspended|not found|404|protected|empty/i.test(allErrorsText) || hadAnyHttpContact) {
      return t("errorTwitterPrivate");
    }
  }

  if (platform === "facebook") {
    if (/private|login|permission|not found|404/i.test(allErrorsText) || hadAnyHttpContact) {
      return t("errorFacebookPrivate");
    }
  }

  // 5. Rate-limit / Cloudflare protection block
  if (/cloudflare|rate-limit|rate limited|blocked|429|503|502|html error page/i.test(allErrorsText)) {
    return t("errorServerBlocked");
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
    return t("errorMediaNotFound");
  }

  if (isTimeout) {
    return t("errorTimeout");
  }

  return t("analysisFailed");
}

async function startAnalyze(forced = false) {
  if (!activeUrl || (isAnalyzing && !forced)) return;
  isAnalyzing = true;
  analysisAborted = false;

  document.getElementById("errorSection").classList.add("hidden");
  document.getElementById("downloadListSection").classList.add("hidden");
  document.getElementById("statusSection").classList.remove("hidden");
  document.getElementById("statusText").innerText = t("analyzing");
  document.getElementById("mediaTitle").innerText = t("analyzing");

  const platform = currentPlatform || detectPlatform(activeUrl);
  if (!platform) {
    showError(t("unsupported"));
    isAnalyzing = false;
    return;
  }

  const baseScrapers = fallbackChains[platform] || ['direct'];
  const scrapers = [
    baseScrapers[currentServerIndex % baseScrapers.length],
    ...baseScrapers.filter((_, i) => i !== (currentServerIndex % baseScrapers.length))
  ];

  let success = false;
  const failureDetails = [];

  for (const method of scrapers) {
    if (analysisAborted) break;

    try {
      const scraperModule = window.scrapr?.[platform];
      const scrapeFunc = scraperModule?.[method] || scraperModule?.default || window.scrapr?.[`scrape${platform.charAt(0).toUpperCase() + platform.slice(1)}`];
      if (!scrapeFunc) continue;

      const res = await scrapeFunc(activeUrl);
      if (analysisAborted) break;

      if (res && res.status === true && res.result) {
        activeResult = res.result;
        renderResult(res.result, platform);
        success = true;
        break;
      } else {
        const errorMsg = res?.message || "Unknown error";
        failureDetails.push({ scraper: method, message: errorMsg, statusCode: res?.statusCode });
      }
    } catch (e) {
      failureDetails.push({ scraper: method, message: e?.message || String(e) });
    }
  }

  isAnalyzing = false;
  document.getElementById("statusSection").classList.add("hidden");

  if (analysisAborted) {
    showToast(t("cancelled"));
    return;
  }

  if (!success) {
    const formattedError = await formatShareErrorMessage(activeUrl, platform, failureDetails);
    showError(formattedError);
  }
}

function showError(msg) {
  document.getElementById("statusSection").classList.add("hidden");
  document.getElementById("downloadListSection").classList.add("hidden");
  const errSec = document.getElementById("errorSection");
  document.getElementById("errorText").innerText = msg || t("analysisFailed");
  document.getElementById("mediaTitle").innerText = t("analysisFailed");
  errSec.classList.remove("hidden");
}

function renderResult(result, platform) {
  const downloadSection = document.getElementById("downloadListSection");
  const mediaTitle = document.getElementById("mediaTitle");
  const downloadList = document.getElementById("downloadList");

  // Display description or title in the header row
  const titleText = result.title || result.description || "Media Title";
  mediaTitle.innerText = titleText;
  mediaTitle.title = titleText;

  downloadList.innerHTML = "";

  if (result.downloads && result.downloads.length > 0) {
    // If multi-item, render Download All button
    if (result.downloads.length > 1) {
      const groupDiv = document.createElement("div");
      groupDiv.className = "multi-download-header";
      groupDiv.style.marginBottom = "10px";
      groupDiv.style.width = "100%";

      const allBtn = document.createElement("button");
      allBtn.id = "downloadAllBtn";
      allBtn.className = "btn primary-btn";
      allBtn.style.width = "100%";
      allBtn.innerText = t("downloadAll", { count: result.downloads.length });
      allBtn.onclick = () => {
        executeBatchDownload(result.downloads, result);
      };
      groupDiv.appendChild(allBtn);
      downloadList.appendChild(groupDiv);
    }

    result.downloads.forEach((dl, idx) => {
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
      quality.innerText = dl.url ? t("ready") : t("noUrl");

      meta.appendChild(type);
      meta.appendChild(quality);

      const btn = document.createElement("button");
      btn.id = "downloadOptBtn_" + idx;
      btn.className = "btn primary-btn option-btn";
      btn.innerText = t("download");

      btn.onclick = () => {
        executeSingleDownload(dl, result, idx);
      };

      option.appendChild(meta);
      option.appendChild(btn);
      downloadList.appendChild(option);
    });
  }

  downloadSection.classList.remove("hidden");
}

// Download execution with Native Stream Downloader
async function executeSingleDownload(dlItem, result, index = 0, isBatch = false, batchIndex = 0, batchTotal = 1) {
  if (!dlItem || !dlItem.url) {
    showToast(t("unsupported"));
    showNativeToast(t("unsupported"));
    return false;
  }

  const optBtn = document.getElementById("downloadOptBtn_" + index);
  if (optBtn) {
    optBtn.innerText = t("downloading");
    optBtn.disabled = true;
  }

  // Show minimize button when download starts
  const minimizeBtn = document.getElementById("minimizeBtn");
  if (minimizeBtn) minimizeBtn.classList.remove("hidden");

  const mediaCategory = detectMediaCategory(dlItem, result);
  const targetExtension = determineExtension(mediaCategory, dlItem.url);
  const sanitizedFilename = buildTargetFilename(dlItem, result, mediaCategory, currentPlatform, targetExtension);
  const isAudio = mediaCategory === "audio";
  const mimeType = isAudio ? "audio/mpeg" : (mediaCategory === "image" ? "image/jpeg" : "video/mp4");
  const displayTitle = dlItem.title || result.title || sanitizedFilename;

  if (!isBatch) {
    showToast(t("downloading"));
    updateDownloadProgressCard(true, displayTitle, 10);
    notifyNative("Nimiyo Downloader", `${t("downloading")} ${displayTitle}`, 10, 100, false);

    activeDownloadProgressCallback = (reqId, pct) => {
      updateDownloadProgressCard(true, displayTitle, pct);
      notifyNative("Nimiyo Downloader", `${t("downloading")} ${displayTitle} (${Math.round(pct)}%)`, pct, 100, false);
    };
  }

  try {
    let downloadUrl = dlItem.url;

    // 1A. Spotify SpotiDown Lazy Resolving
    if (downloadUrl.startsWith("spotidown_resolve:")) {
      const parts = downloadUrl.replace("spotidown_resolve:", "").split("|||");
      const payload = parts[0];
      const cookie = decodeURIComponent(parts[1] || "");

      let resData = null;
      if (window.NimiyoShareBridge?.httpRequestAsync) {
        const reqId = "spot_" + Date.now();
        const rawRes = await new Promise((resolve) => {
          if (!window.__nimiyoShareCallbacks) window.__nimiyoShareCallbacks = {};
          window.__nimiyoShareCallbacks[reqId] = resolve;
          window.NimiyoShareBridge.httpRequestAsync(
            JSON.stringify({
              url: "https://spotidown.app/action/track",
              method: "POST",
              data: payload,
              headers: {
                "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
                "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                "Referer": "https://spotidown.app/",
                "Cookie": cookie
              }
            }),
            reqId
          );
        });
        resData = typeof rawRes === "object" ? rawRes : JSON.parse(rawRes);
      } else if (window.scrapr?.scraperFetch) {
        resData = await window.scrapr.scraperFetch({
          url: "https://spotidown.app/action/track",
          method: "POST",
          data: payload,
          headers: {
            "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
            "Referer": "https://spotidown.app/",
            "Cookie": cookie
          }
        });
      }

      let parsedData = resData?.data || resData;
      if (typeof parsedData === "string") {
        try { parsedData = JSON.parse(parsedData); } catch (_) { }
      }
      const htmlContent = parsedData?.data || (typeof parsedData === "string" ? parsedData : "");
      const parser = new DOMParser();
      const doc = parser.parseFromString(htmlContent, "text/html");
      let mp3Url = "";
      let fallbackUrl = "";

      for (const a of Array.from(doc.querySelectorAll("a"))) {
        const href = a.getAttribute("href") || "";
        const text = (a.textContent || "").toLowerCase();

        if (!href.startsWith("http") || href.includes("premium.html") || href === "https://spotidown.app/" || href === "https://spotidown.app") {
          continue;
        }

        if (text.includes("cover") || href.includes("cover")) {
          continue;
        }

        if (text.includes("mp3") || text.includes("download mp3") || text.includes("song") || href.includes("rapid.spotidown.app") || href.includes("/v2?token=") || href.includes("dl?token=")) {
          mp3Url = href;
          break;
        }

        if (!fallbackUrl) {
          fallbackUrl = href;
        }
      }

      const finalUrl = mp3Url || fallbackUrl;
      if (!finalUrl) throw new Error("Could not resolve Spotify download link.");
      downloadUrl = finalUrl;
    }

    // 1B. Spotify SoundLoaders Lazy Resolving
    if (downloadUrl.startsWith("soundloaders_resolve:")) {
      const parts = downloadUrl.replace("soundloaders_resolve:", "").split("|||");
      const dataVal = parts[0];
      const trackToken = parts[1];
      const formBody = new URLSearchParams({ data: dataVal, track_token: trackToken }).toString();

      let resData = null;
      if (window.NimiyoShareBridge?.httpRequestAsync) {
        const reqId = "sound_" + Date.now();
        const rawRes = await new Promise((resolve) => {
          if (!window.__nimiyoShareCallbacks) window.__nimiyoShareCallbacks = {};
          window.__nimiyoShareCallbacks[reqId] = resolve;
          window.NimiyoShareBridge.httpRequestAsync(
            JSON.stringify({
              url: "https://soundloaders.app/action/tracks",
              method: "POST",
              data: formBody,
              headers: {
                "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
                "Referer": "https://soundloaders.app/"
              }
            }),
            reqId
          );
        });
        resData = typeof rawRes === "object" ? rawRes : JSON.parse(rawRes);
      } else if (window.scrapr?.scraperFetch) {
        resData = await window.scrapr.scraperFetch({
          url: "https://soundloaders.app/action/tracks",
          method: "POST",
          data: formBody,
          headers: {
            "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
            "Referer": "https://soundloaders.app/"
          }
        });
      }

      let parsedData = resData?.data || resData;
      if (typeof parsedData === "string") {
        try { parsedData = JSON.parse(parsedData); } catch (_) { }
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
      if (!resolvedUrl) throw new Error("Could not resolve SoundLoaders download link.");
      downloadUrl = resolvedUrl;
    }

    // 1C. Apple Music Aplmate Resolving
    if (downloadUrl.startsWith("aplmate_resolve:")) {
      const parts = downloadUrl.replace("aplmate_resolve:", "").split("|||");
      const token = parts[0];
      const formBody = new URLSearchParams({ token }).toString();

      let resData = null;
      if (window.NimiyoShareBridge?.httpRequestAsync) {
        const reqId = "apl_" + Date.now();
        const rawRes = await new Promise((resolve) => {
          if (!window.__nimiyoShareCallbacks) window.__nimiyoShareCallbacks = {};
          window.__nimiyoShareCallbacks[reqId] = resolve;
          window.NimiyoShareBridge.httpRequestAsync(
            JSON.stringify({
              url: "https://aplmate.com/action",
              method: "POST",
              data: formBody,
              headers: {
                "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
                "Referer": "https://aplmate.com/"
              }
            }),
            reqId
          );
        });
        resData = typeof rawRes === "object" ? rawRes : JSON.parse(rawRes);
      }

      let parsedData = resData?.data || resData;
      if (typeof parsedData === "string") {
        try { parsedData = JSON.parse(parsedData); } catch (_) { }
      }
      const htmlContent = parsedData?.data || (typeof parsedData === "string" ? parsedData : "");
      const parser = new DOMParser();
      const doc = parser.parseFromString(htmlContent, "text/html");
      let resolvedUrl = "";
      doc.querySelectorAll("a").forEach(a => {
        const href = a.getAttribute("href");
        if (href && (href.includes("/dl?token=") || a.classList.contains("abutton"))) {
          if (!href.includes("ko-fi.com") && !href.includes("premium.html")) {
            resolvedUrl = href.startsWith("http") ? href : "https://aplmate.com" + href;
          }
        }
      });
      if (!resolvedUrl) throw new Error("Could not resolve Apple Music download link.");
      downloadUrl = resolvedUrl;
    }

    // 2. High-Speed Native Stream Downloader
    if (window.NimiyoShareBridge && window.NimiyoShareBridge.downloadFileAsync) {
      const reqId = "dl_" + Date.now() + "_" + Math.random().toString(36).substr(2, 6);
      const res = await new Promise((resolve) => {
        if (!window.__nimiyoShareCallbacks) window.__nimiyoShareCallbacks = {};
        window.__nimiyoShareCallbacks[reqId] = resolve;
        window.NimiyoShareBridge.downloadFileAsync(downloadUrl, sanitizedFilename, mimeType, isAudio, reqId);
      });

      if (res && res.success) {
        saveToHistory(sanitizedFilename, result, dlItem, isAudio ? "audio" : mediaCategory);
        if (optBtn) {
          optBtn.innerText = "✓ Selesai";
          optBtn.disabled = true;
        }

        if (!isBatch) {
          updateDownloadProgressCard(true, displayTitle, 100);
          notifyNative("Nimiyo Downloader", `${sanitizedFilename} selesai diunduh!`, 100, 100, true);
          showToast(t("saved"));
          showNativeToast(t("saved"));
          setTimeout(() => {
            window.dismissPanel();
          }, 1200);
        }
        return true;
      } else {
        throw new Error(res?.error || "Download stream failed");
      }
    } else {
      // Browser Fallback
      const resp = await fetch(downloadUrl);
      if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
      const blob = await resp.blob();
      const a = document.createElement("a");
      a.href = URL.createObjectURL(blob);
      a.download = sanitizedFilename;
      a.click();
      if (optBtn) {
        optBtn.innerText = "✓ Selesai";
        optBtn.disabled = true;
      }
      saveToHistory(sanitizedFilename, result, dlItem, isAudio ? "audio" : mediaCategory);

      if (!isBatch) {
        updateDownloadProgressCard(true, displayTitle, 100);
        showToast(t("saved"));
        showNativeToast(t("saved"));
        setTimeout(() => {
          window.dismissPanel();
        }, 1200);
      }
      return true;
    }

  } catch (err) {
    console.error("Download error:", err);
    if (optBtn) {
      optBtn.innerText = "Gagal";
      optBtn.disabled = false;
    }

    if (!isBatch) {
      updateDownloadProgressCard(false);
      const platform = currentPlatform || (activeUrl ? detectPlatform(activeUrl) : null);
      const servers = platform ? (fallbackChains[platform] || ['direct']) : ['direct'];

      if (servers.length > 1) {
        currentServerIndex = (currentServerIndex + 1) % servers.length;
        const nextServer = servers[currentServerIndex];
        const formatted = nextServer.charAt(0).toUpperCase() + nextServer.slice(1);
        updateServerButtonLabel();
        const failMsg = t("downloadFailedSwitchServer", { server: formatted });
        showToast(failMsg);
        showNativeToast(failMsg);
        notifyNative("Nimiyo Downloader", failMsg, 0, 0, true);
        // Automatically re-analyze link with next server
        setTimeout(() => {
          startAnalyze(true);
        }, 700);
      } else {
        const errText = t("downloadFailedManualServer");
        showToast(errText);
        showNativeToast(errText);
        notifyNative("Nimiyo Downloader", errText, 0, 0, true);
      }
    } else {
      console.warn(`[Batch Download] Item ${batchIndex + 1}/${batchTotal} failed:`, err?.message || err);
    }
    return false;
  }
}

async function executeBatchDownload(items, result) {
  if (!items || items.length === 0) return;

  const minimizeBtn = document.getElementById("minimizeBtn");
  if (minimizeBtn) minimizeBtn.classList.remove("hidden");

  const allBtn = document.getElementById("downloadAllBtn");
  if (allBtn) {
    allBtn.disabled = true;
    allBtn.innerText = `MENGUNDUH (1/${items.length})...`;
  }

  const total = items.length;
  showToast(t("downloading"));
  showNativeToast(`Memulai unduhan ${total} berkas...`);

  updateDownloadProgressCard(true, `Mengunduh berkas 1 dari ${total}...`, 0);

  if (window.NimiyoShareBridge && window.NimiyoShareBridge.showBatchNotification) {
    window.NimiyoShareBridge.showBatchNotification(
      "NIMIYO Quick Save",
      `Mengunduh 1/${total} berkas...`,
      1,
      total,
      0,
      false
    );
  }

  let successCount = 0;

  for (let i = 0; i < total; i++) {
    const item = items[i];
    const baseProgress = (i / total) * 100;
    const itemWeight = 100 / total;

    if (allBtn) {
      allBtn.innerText = `MENGUNDUH (${i + 1}/${total})...`;
    }

    const cardTitle = `Mengunduh berkas ${i + 1} dari ${total}...`;
    updateDownloadProgressCard(true, cardTitle, baseProgress);

    activeDownloadProgressCallback = (reqId, pct) => {
      const overall = Math.min(99, Math.round(baseProgress + ((pct / 100) * itemWeight)));
      updateDownloadProgressCard(true, cardTitle, overall);
      if (window.NimiyoShareBridge && window.NimiyoShareBridge.showBatchNotification) {
        window.NimiyoShareBridge.showBatchNotification(
          "NIMIYO Quick Save",
          `Mengunduh ${i + 1}/${total} berkas (${Math.round(pct)}%)...`,
          i + 1,
          total,
          overall,
          false
        );
      }
    };

    const ok = await executeSingleDownload(item, result, i, true, i, total);
    if (ok) successCount++;
  }

  activeDownloadProgressCallback = null;

  // Finished batch
  updateDownloadProgressCard(true, `✓ ${successCount}/${total} berkas selesai diunduh!`, 100);

  if (window.NimiyoShareBridge && window.NimiyoShareBridge.showBatchNotification) {
    window.NimiyoShareBridge.showBatchNotification(
      "NIMIYO Quick Save",
      `Semua ${successCount} berkas selesai diunduh!`,
      total,
      total,
      100,
      true
    );
  }

  if (allBtn) {
    allBtn.innerText = `✓ SEMUA SELESAI (${successCount}/${total})`;
    allBtn.disabled = true;
  }

  showToast(t("saved"));
  showNativeToast(t("saved"));

  // Dismiss panel after 1500ms
  setTimeout(() => {
    window.dismissPanel();
  }, 1500);
}

function saveToHistory(filename, result, dlItem, mediaType = "media") {
  try {
    const historyJson = localStorage.getItem("nimiyo_history");
    let history = historyJson ? JSON.parse(historyJson) : [];

    let historyTitle = filename.replace(/\.(mp4|mp3|png|jpg|jpeg|webp|m4a|wav|webm|mov)$/i, "").replace(/_+/g, " ").trim();
    if (result?.title && !isGenericMediaTitle(result.title)) {
      historyTitle = result.title;
    }
    if (dlItem?.title && !isGenericMediaTitle(dlItem.title)) {
      historyTitle = dlItem.title;
    }
    if (!historyTitle || isGenericMediaTitle(historyTitle)) {
      historyTitle = filename.replace(/\.(mp4|mp3|png|jpg|jpeg|webp|m4a|wav|webm|mov)$/i, "").replace(/_+/g, " ").trim();
    }

    const historyItem = {
      id: "dl_" + Date.now() + "_" + Math.random().toString(36).substr(2, 4),
      filename: filename,
      title: historyTitle,
      thumbnail: dlItem?.thumbnail || result?.thumbnail || "nimiyo_icon.webp",
      platform: currentPlatform || (activeUrl ? detectPlatform(activeUrl) : "other"),
      mediaType: mediaType,
      timestamp: Date.now()
    };

    history.unshift(historyItem);
    if (history.length > 50) history.pop();
    localStorage.setItem("nimiyo_history", JSON.stringify(history));

    // Persist to native Android SharedPreferences so MainActivity syncs it
    const bridge = window.NimiyoShareBridge || window.NimidzShareBridge || window.NimiyoShareBridge;
    if (bridge && typeof bridge.saveHistoryItem === "function") {
      bridge.saveHistoryItem(JSON.stringify(historyItem));
    }
  } catch (err) {
    console.warn("Failed to save history in QuickSave:", err);
  }
}

function notifyNative(title, message, progress, max, isCompleted) {
  if (window.NimiyoShareBridge && window.NimiyoShareBridge.showNotification) {
    window.NimiyoShareBridge.showNotification(title, message, progress, max, isCompleted);
  }
}

function showNativeToast(message) {
  if (window.NimiyoShareBridge && window.NimiyoShareBridge.showToast) {
    window.NimiyoShareBridge.showToast(message);
  }
}

// Window Exposed Functions
window.dismissPanel = function () {
  if (window.NimiyoShareBridge && window.NimiyoShareBridge.dismiss) {
    window.NimiyoShareBridge.dismiss();
  }
};

window.minimizeWindow = function () {
  showToast(t("backgroundDl"));
  showNativeToast(t("backgroundDl"));
  if (window.NimiyoShareBridge && window.NimiyoShareBridge.minimize) {
    window.NimiyoShareBridge.minimize();
  } else if (window.NimiyoShareBridge && window.NimiyoShareBridge.dismiss) {
    window.NimiyoShareBridge.dismiss();
  }
};

window.cancelOngoingAnalysis = function () {
  analysisAborted = true;
  document.getElementById("statusSection").classList.add("hidden");
  showToast(t("cancelled"));
};

window.retryAnalyze = function () {
  startAnalyze();
};

window.openFullApp = function () {
  if (window.NimiyoShareBridge && window.NimiyoShareBridge.openInMainApp) {
    window.NimiyoShareBridge.openInMainApp(activeUrl);
  } else {
    window.dismissPanel();
  }
};

window.showToast = function (message) {
  const container = document.getElementById("toastContainer");
  if (!container) return;

  const toast = document.createElement("div");
  toast.className = "toast toast-info";
  toast.innerHTML = `<span>${message}</span>`;
  container.appendChild(toast);

  setTimeout(() => {
    toast.style.opacity = "0";
    setTimeout(() => toast.remove(), 200);
  }, 2500);
};

// Initialize
document.addEventListener("DOMContentLoaded", () => {
  initLanguageAndTheme();
  if (window.__NIMIYO_SHARE_URL) {
    window.onShareUrlReady(window.__NIMIYO_SHARE_URL);
  } else if (window.NimiyoShareBridge && window.NimiyoShareBridge.getSharedUrl) {
    const u = window.NimiyoShareBridge.getSharedUrl();
    if (u) window.onShareUrlReady(u);
  }
});
