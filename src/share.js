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
    analysisFailed: "Failed to analyze link.",
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
    analysisFailed: "Gagal menganalisis tautan.",
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
    analysisFailed: "解析链接失败。",
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
    analysisFailed: "リンクの解析に失敗しました。",
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
  threads: { name: "Threads", domains: /(threads\.net)/i, color: "#000000" },
  pinterest: { name: "Pinterest", domains: /(pinterest\.com|pin\.it)/i, color: "#E60023" },
  bilibili: { name: "Bilibili", domains: /(bilibili\.com|b23\.tv)/i, color: "#00AEEC" },
  douyin: { name: "Douyin", domains: /(douyin\.com)/i, color: "#FF0050" },
  bandcamp: { name: "Bandcamp", domains: /(bandcamp\.com)/i, color: "#1DA1F2" },
  pixiv: { name: "Pixiv", domains: /(pixiv\.net|pixiv\.me|pximg\.net)/i, color: "#0096FA" },
  rednote: { name: "RedNote", domains: /(rednote\.com|xiaohongshu\.com|xhslink\.com|xhslink\.cn)/i, color: "#FF2442" }
};

const fallbackChains = {
  tiktok: ['snaptik', 'tiktokio'],
  instagram: ['snapsave', 'indown'],
  facebook: ['snapsave', 'direct'],
  spotify: ['spotidown', 'soundloaders'],
  twitter: ['tvd', 'tweeload'],
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

function t(key, params = {}) {
  let str = shareTranslations[currentLang]?.[key] || shareTranslations['en']?.[key] || key;
  if (params && typeof params === "object") {
    Object.keys(params).forEach(p => {
      str = str.replace(new RegExp(`\\{${p}\\}`, "g"), params[p]);
    });
  }
  return str;
}

function initLanguageAndTheme() {
  const savedSettings = localStorage.getItem("nimiyo_settings");
  if (savedSettings) {
    try {
      const parsed = JSON.parse(savedSettings);
      if (parsed.language && shareTranslations[parsed.language]) {
        currentLang = parsed.language;
      }
      if (parsed.darkMode) {
        document.body.classList.add("dark-mode");
      } else {
        document.body.classList.remove("dark-mode");
      }
    } catch (_) {}
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

function buildTargetFilename(dlItem, mediaResult, mediaCategory, platform, extension) {
  let title = (mediaResult?.title || "").trim();
  let itemTitle = (dlItem?.title || dlItem?.name || "").trim();
  let dlType = (dlItem?.type || "").trim();

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
  if (!isGenericItemTitle) {
    base = cleanItemTitle;
  } else if (!isGenericDlType) {
    base = cleanType;
  } else if (title) {
    base = title;
  } else {
    base = `Nimiyo_${platform || 'Media'}_${Date.now()}`;
  }

  base = base.replace(/\.(mp4|mp3|png|jpg|jpeg|webp|m4a|wav|webm|mov)$/i, "").trim();
  let cleaned = base.replace(/[\\/:*?"<>|]/g, "_").trim();
  cleaned = cleaned.replace(/\s+/g, "_").replace(/_+/g, "_");

  if (cleaned.length > 70) cleaned = cleaned.substring(0, 70);
  if (!cleaned) cleaned = "Download";

  return cleaned + (extension.startsWith(".") ? extension : `.${extension}`);
}

// Global hook called by Native ShareActivity on URL extraction
window.onShareUrlReady = function(url) {
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
window.switchServer = function() {
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
  let lastErrorMsg = t("analysisFailed");

  for (const method of scrapers) {
    if (analysisAborted) break;

    try {
      const scraperModule = window.scrapr?.[platform];
      const scrapeFunc = scraperModule?.[method];
      if (!scrapeFunc) continue;

      const res = await scrapeFunc(activeUrl);
      if (analysisAborted) break;

      if (res && res.status === true && res.result) {
        activeResult = res.result;
        renderResult(res.result, platform);
        success = true;
        break;
      } else {
        lastErrorMsg = res?.message || lastErrorMsg;
      }
    } catch (e) {
      lastErrorMsg = e.message || lastErrorMsg;
    }
  }

  isAnalyzing = false;
  document.getElementById("statusSection").classList.add("hidden");

  if (analysisAborted) {
    showToast(t("cancelled"));
    return;
  }

  if (!success) {
    showError(lastErrorMsg);
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
      type.innerText = `${dl.type || 'media'} (${dl.quality || 'HD'})`;

      const quality = document.createElement("span");
      quality.className = "option-quality";
      quality.innerText = dl.url ? t("ready") : t("noUrl");

      meta.appendChild(type);
      meta.appendChild(quality);

      const btn = document.createElement("button");
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
async function executeSingleDownload(dlItem, result, index = 0) {
  if (!dlItem || !dlItem.url) {
    showToast(t("unsupported"));
    showNativeToast(t("unsupported"));
    return;
  }

  // Show minimize button when download starts
  const minimizeBtn = document.getElementById("minimizeBtn");
  if (minimizeBtn) minimizeBtn.classList.remove("hidden");

  showToast(t("downloading"));
  notifyNative("Nimiyo Downloader", `${t("downloading")} ${result.title || 'file'}`, 10, 100, false);

  try {
    let downloadUrl = dlItem.url;
    const mediaCategory = detectMediaCategory(dlItem, result);
    const targetExtension = determineExtension(mediaCategory, downloadUrl);
    const sanitizedFilename = buildTargetFilename(dlItem, result, mediaCategory, currentPlatform, targetExtension);
    const isAudio = mediaCategory === "audio";
    const mimeType = isAudio ? "audio/mpeg" : (mediaCategory === "image" ? "image/jpeg" : "video/mp4");

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
        try { parsedData = JSON.parse(parsedData); } catch (_) {}
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
        try { parsedData = JSON.parse(parsedData); } catch (_) {}
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
        try { parsedData = JSON.parse(parsedData); } catch (_) {}
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
        setTimeout(() => {
          window.dismissPanel();
        }, 1200);
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
      showToast(t("saved"));
      showNativeToast(t("saved"));
      saveToHistory(sanitizedFilename, result, dlItem, isAudio ? "audio" : mediaCategory);
    }

  } catch (err) {
    console.error("Download error:", err);
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
  }
}

async function executeBatchDownload(items, result) {
  const minimizeBtn = document.getElementById("minimizeBtn");
  if (minimizeBtn) minimizeBtn.classList.remove("hidden");

  showToast(t("downloading"));
  for (let i = 0; i < items.length; i++) {
    await executeSingleDownload(items[i], result, i);
  }
}

function saveToHistory(filename, result, dlItem, mediaType = "media") {
  try {
    const historyJson = localStorage.getItem("nimiyo_history");
    let history = historyJson ? JSON.parse(historyJson) : [];
    const historyItem = {
      id: "dl_" + Date.now() + "_" + Math.random().toString(36).substr(2, 4),
      filename: filename,
      title: dlItem.title || result.title || filename,
      thumbnail: dlItem.thumbnail || result.thumbnail || "nimiyo_icon.webp",
      platform: currentPlatform,
      mediaType: mediaType,
      timestamp: Date.now()
    };
    history.unshift(historyItem);
    if (history.length > 50) history.pop();
    localStorage.setItem("nimiyo_history", JSON.stringify(history));
  } catch (_) {}
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
window.dismissPanel = function() {
  if (window.NimiyoShareBridge && window.NimiyoShareBridge.dismiss) {
    window.NimiyoShareBridge.dismiss();
  }
};

window.minimizeWindow = function() {
  showToast(t("backgroundDl"));
  showNativeToast(t("backgroundDl"));
  if (window.NimiyoShareBridge && window.NimiyoShareBridge.minimize) {
    window.NimiyoShareBridge.minimize();
  } else if (window.NimiyoShareBridge && window.NimiyoShareBridge.dismiss) {
    window.NimiyoShareBridge.dismiss();
  }
};

window.cancelOngoingAnalysis = function() {
  analysisAborted = true;
  document.getElementById("statusSection").classList.add("hidden");
  showToast(t("cancelled"));
};

window.retryAnalyze = function() {
  startAnalyze();
};

window.openFullApp = function() {
  if (window.NimiyoShareBridge && window.NimiyoShareBridge.openInMainApp) {
    window.NimiyoShareBridge.openInMainApp(activeUrl);
  } else {
    window.dismissPanel();
  }
};

window.showToast = function(message) {
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
