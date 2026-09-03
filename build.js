const esbuild = require("esbuild");
const { polyfillNode } = require("esbuild-plugin-polyfill-node");
const fs = require("fs");
const path = require("path");

// Ensure directories exist
if (!fs.existsSync("www")) fs.mkdirSync("www");
if (!fs.existsSync("www/js")) fs.mkdirSync("www/js");

async function build() {
  console.log("Preparing scrapr bundle for browser...");
  try {
    // Minify src/platform.js to www/js/scrapr.bundle.js
    await esbuild.build({
      entryPoints: ["src/platform.js"],
      bundle: false,
      minify: true,
      outfile: "www/js/scrapr.bundle.js"
    });

    console.log("scrapr bundled successfully!");

    // Copy frontend files
    console.log("Copying frontend source files...");
    fs.copyFileSync("src/index.html", "www/index.html");
    fs.copyFileSync("src/index.css", "www/index.css");
    fs.copyFileSync("src/app.js", "www/app.js");
    fs.copyFileSync("src/share.html", "www/share.html");
    fs.copyFileSync("src/share.css", "www/share.css");
    fs.copyFileSync("src/share.js", "www/share.js");
    
    // Copy icons
    if (fs.existsSync("nimiyo_icon.webp")) {
      fs.copyFileSync("nimiyo_icon.webp", "www/nimiyo_icon.webp");
    }
    if (fs.existsSync("icon_untukdi_aboutthisapp.webp")) {
      fs.copyFileSync("icon_untukdi_aboutthisapp.webp", "www/icon_untukdi_aboutthisapp.webp");
    }
    if (fs.existsSync("MiSans-Regular.119.woff2")) {
      fs.copyFileSync("MiSans-Regular.119.woff2", "www/MiSans-Regular.119.woff2");
    }
    if (fs.existsSync("MiSans-Medium.119.woff2")) {
      fs.copyFileSync("MiSans-Medium.119.woff2", "www/MiSans-Medium.119.woff2");
    }
    
    console.log("Build complete!");
  } catch (error) {
    console.error("Build failed:", error);
    process.exit(1);
  }
}

build();
