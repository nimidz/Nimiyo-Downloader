<div align="center">

# 🐥 NIMIYO Downloader (Lite)

**A Premium, Ultra-Fast & Lightweight Universal Media Downloader for Android**

[![Release](https://img.shields.io/badge/Release-v1.0.0--Lite-blue.svg?style=for-the-badge&logo=android)](https://github.com/nimidz/Nimiyo-Downloader/releases)
[![Platform](https://img.shields.io/badge/Platform-Android_10+-3DDC84.svg?style=for-the-badge&logo=android)](https://github.com/nimidz/Nimiyo-Downloader)
[![Design](https://img.shields.io/badge/Design-Neobrutalism-FFE500.svg?style=for-the-badge)](https://github.com/nimidz/Nimiyo-Downloader)
[![License](https://img.shields.io/badge/License-MIT-black.svg?style=for-the-badge)](LICENSE)

<br/>

<p align="center">
  <b>Download videos, music, high-res photos, and audio tracks effortlessly from 14+ platforms without ads, popups, or limits.</b>
</p>

</div>

---

## ✨ Key Features

- ⚡ **All-In-One Universal Downloader**: Support for videos, audio tracks, covers, slideshows, and albums.
- 🎨 **Neobrutalist Aesthetic UI/UX**: Distinctive bold borders, vibrant accents, haptic feedback, custom typography (*MiSans, Inter, Outfit, Space Mono*), and fluid Dark Mode.
- 📲 **Quick Save Share Sheet**: Share links from any app (TikTok, YouTube, Instagram) directly into NIMIYO's bottom sheet without opening the full application.
- 🗂️ **Organized Media Storage**:
  - 🎬 `Download/Nimiyo/VideoYo/`
  - 🎵 `Download/Nimiyo/AudioYo/`
  - 🖼️ `Download/Nimiyo/ImageYo/`
- 📁 **Direct Folder Navigation**: One-tap direct folder opening into Xiaomi File Explorer, Samsung My Files, Google Files, and ZArchiver.
- 🔄 **Smart Fallback & Auto Server Switch**: Automatically retries alternative backend servers if a primary scraper or CDN link fails.
- 🌍 **Multilingual Localization**: Complete native language support for **English**, **Bahasa Indonesia**, **简体中文**, and **日本語**.
- 🔒 **Privacy First**: Built-in Incognito Mode, zero tracking, and local history management.

---

## 🌐 Supported Platforms

| Platform | Video | Audio / MP3 | Photos / Album | Status |
| :--- | :---: | :---: | :---: | :---: |
| **YouTube** | ✅ | ✅ | ❌ | ![Active](https://img.shields.io/badge/Online-brightgreen?style=flat-square) |
| **TikTok** | ✅ (No Watermark) | ✅ | ✅ (Slideshow) | ![Active](https://img.shields.io/badge/Online-brightgreen?style=flat-square) |
| **Instagram** | ✅ (Reels/Stories) | ✅ | ✅ (Carousels) | ![Active](https://img.shields.io/badge/Online-brightgreen?style=flat-square) |
| **Spotify** | ❌ | ✅ (320kbps MP3) | ✅ (Cover Art) | ![Active](https://img.shields.io/badge/Online-brightgreen?style=flat-square) |
| **Apple Music** | ❌ | ✅ (HQ M4A/MP3) | ✅ (Cover Art) | ![Active](https://img.shields.io/badge/Online-brightgreen?style=flat-square) |
| **Twitter / X** | ✅ (HD) | ❌ | ✅ | ![Active](https://img.shields.io/badge/Online-brightgreen?style=flat-square) |
| **Facebook** | ✅ (Reels/Watch) | ✅ | ✅ | ![Active](https://img.shields.io/badge/Online-brightgreen?style=flat-square) |
| **Threads** | ✅ | ❌ | ✅ | ![Active](https://img.shields.io/badge/Online-brightgreen?style=flat-square) |
| **Pinterest** | ✅ | ❌ | ✅ (High-Res) | ![Active](https://img.shields.io/badge/Online-brightgreen?style=flat-square) |
| **Bilibili** | ✅ | ✅ | ❌ | ![Active](https://img.shields.io/badge/Online-brightgreen?style=flat-square) |
| **Douyin** | ✅ (No Watermark) | ✅ | ✅ | ![Active](https://img.shields.io/badge/Online-brightgreen?style=flat-square) |
| **Bandcamp** | ❌ | ✅ (Lossless/MP3) | ✅ | ![Active](https://img.shields.io/badge/Online-brightgreen?style=flat-square) |
| **Pixiv** | ❌ | ❌ | ✅ (Full Size) | ![Active](https://img.shields.io/badge/Online-brightgreen?style=flat-square) |
| **RedNote (小红书)** | ✅ | ❌ | ✅ (HD Images) | ![Active](https://img.shields.io/badge/Online-brightgreen?style=flat-square) |

---

## 🛠️ Architecture & Tech Stack

```
Nimiyo-Downloader/
├── src/                      # Web UI & Scraper Engine
│   ├── index.html            # Main Application UI
│   ├── app.js                # State Management & Localization
│   ├── index.css             # Neobrutalist Design System Tokens
│   ├── share.html            # Native Quick Save Floating Panel
│   └── share.js              # Quick Save Logic & Bridge
├── android/                  # Native Android Platform (Capacitor)
│   ├── app/src/main/java/nimiyo/litedownloader/
│   │   ├── MainActivity.java      # Main App Container
│   │   ├── ShareActivity.java     # Quick Save Native Bottom Sheet
│   │   ├── MediaSaverPlugin.java  # MediaStore Storage & File Manager Direct Opener
│   │   └── NativeHttpPlugin.java  # Anti-403 Custom Header Request Engine
│   └── app/src/main/AndroidManifest.xml
├── build.js                  # Scrapr Engine Bundler & Asset Syncer
├── version.json              # In-App Auto Update Manifest
└── capacitor.config.json     # Capacitor Configuration
```

---

## 🚀 Building from Source

### Prerequisites
- **Node.js**: v18 or higher
- **JDK**: Java 17 or 21
- **Android SDK**: Android API 34+

### Step-by-Step Build
1. **Clone the repository**:
   ```bash
   git clone https://github.com/nimidz/Nimiyo-Downloader.git
   cd Nimiyo-Downloader
   ```

2. **Install dependencies**:
   ```bash
   npm install
   ```

3. **Bundle web assets & sync Capacitor**:
   ```bash
   node build.js
   npx cap copy
   ```

4. **Compile Release APK**:
   ```bash
   cd android
   .\gradlew.bat assembleRelease
   ```
   *The compiled APK will be located at `android/app/build/outputs/apk/release/app-release.apk`.*

---

## 📥 Download

Get the latest stable release APK directly from [GitHub Releases](https://github.com/nimidz/Nimiyo-Downloader/releases).

---

## 🤝 Acknowledgements & Credits

- Built with ❤️ by [nimidz](https://github.com/nimidz)
- Powered by `scrapr` media extraction engine by [@coflyn](https://github.com/coflyn)
- Built on [Capacitor](https://capacitorjs.com/)

---

<div align="center">
  <sub>Made with passion for media freedom. © 2026 NIMIYO.</sub>
</div>
