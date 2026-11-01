const fs = require("fs");
const path = require("path");

// Ensure directories exist
if (!fs.existsSync("www")) fs.mkdirSync("www", { recursive: true });
if (!fs.existsSync("www/js")) fs.mkdirSync("www/js", { recursive: true });

// Remove legacy leftover platform files if present
["www/js/platform.js", "www/js/platforms.js", "www/js/scrapr.bundle.js"].forEach(file => {
  if (fs.existsSync(file)) fs.unlinkSync(file);
});

// Helper to copy directory recursively
function copyDirSync(src, dest) {
  if (!fs.existsSync(dest)) {
    fs.mkdirSync(dest, { recursive: true });
  }
  const entries = fs.readdirSync(src, { withFileTypes: true });
  for (const entry of entries) {
    const srcPath = path.join(src, entry.name);
    const destPath = path.join(dest, entry.name);
    if (entry.isDirectory()) {
      copyDirSync(srcPath, destPath);
    } else {
      fs.copyFileSync(srcPath, destPath);
    }
  }
}

async function build() {
  console.log("Copying modular scrapers and utils directly to www...");
  try {
    // Clean old redundant www/scrapers and www/utils if present
    if (fs.existsSync("www/scrapers")) fs.rmSync("www/scrapers", { recursive: true, force: true });
    if (fs.existsSync("www/utils")) fs.rmSync("www/utils", { recursive: true, force: true });

    // 1. Copy scrapers folder directly into www/js/scrapers (100% matching ingredientAPP)
    copyDirSync("src/scrapers", "www/js/scrapers");
    console.log("Copied js/scrapers/ successfully!");

    // 2. Copy utils folder into www/js/utils
    copyDirSync("src/utils", "www/js/utils");
    console.log("Copied js/utils/ successfully!");

    // 2b. Copy js folder into www/js
    if (fs.existsSync("src/js")) {
      copyDirSync("src/js", "www/js");
      console.log("Copied src/js/ successfully!");
    }

    // 3. Copy frontend source files
    console.log("Copying frontend source files...");
    fs.copyFileSync("src/index.html", "www/index.html");
    fs.copyFileSync("src/index.css", "www/index.css");
    fs.copyFileSync("src/soft-ui.css", "www/soft-ui.css");
    fs.copyFileSync("src/app.js", "www/app.js");
    fs.copyFileSync("src/share.html", "www/share.html");
    fs.copyFileSync("src/share.css", "www/share.css");
    fs.copyFileSync("src/share.js", "www/share.js");

    // Copy assets / icons / fonts
    ["nimiyo_icon.webp", "icon_untukdi_aboutthisapp.webp", "MiSans-Regular.119.woff2", "MiSans-Medium.119.woff2"].forEach(f => {
      if (fs.existsSync(f)) {
        fs.copyFileSync(f, path.join("www", f));
      }
    });

    // 4. Sync directly to android/app/src/main/assets/public
    const androidPublicDir = "android/app/src/main/assets/public";
    if (fs.existsSync(androidPublicDir)) {
      console.log("Syncing www to android assets public...");
      copyDirSync("www", androidPublicDir);
      console.log("Synced to android/app/src/main/assets/public successfully!");
    }

    console.log("Build complete! Modular scrapers are now standalone folders in www.");
  } catch (error) {
    console.error("Build failed:", error);
    process.exit(1);
  }
}

build();

