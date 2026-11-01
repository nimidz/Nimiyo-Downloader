package nimiyo.litedownloader;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.app.DownloadManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.os.PowerManager;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ShareActivity extends AppCompatActivity {

    private WebView webView;
    private String extractedUrl = "";
    private static final String CHANNEL_ID = "nimiyo_quicksave_pinned_v1";
    private static final int NOTIFICATION_ID = 8802;
    private BroadcastReceiver downloadCompleteReceiver = null;
    private final java.util.concurrent.ConcurrentHashMap<Long, String> activeDownloadHistories = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Set<Long> activeDownloadIds = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.concurrent.ConcurrentHashMap<Long, Integer> activeProgressMap = new java.util.concurrent.ConcurrentHashMap<>();
    private final ExecutorService httpExecutor = Executors.newFixedThreadPool(4);
    private static PowerManager.WakeLock downloadWakeLock = null;
    private static final java.util.concurrent.atomic.AtomicInteger activeDownloads = new java.util.concurrent.atomic.AtomicInteger(0);
    private static final java.util.concurrent.atomic.AtomicInteger sessionTotalDownloads = new java.util.concurrent.atomic.AtomicInteger(0);
    private static final java.util.concurrent.atomic.AtomicInteger sessionCompletedDownloads = new java.util.concurrent.atomic.AtomicInteger(0);
    private volatile boolean isDismissRequested = false;
    private static volatile boolean isBatchActive = false;
    private static volatile boolean isClosingScheduled = false;

    private static synchronized void acquireDownloadWakeLock(Context context) {
        try {
            Context appContext = context.getApplicationContext();
            if (downloadWakeLock == null) {
                PowerManager pm = (PowerManager) appContext.getSystemService(Context.POWER_SERVICE);
                if (pm != null) {
                    downloadWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Nimiyo:ShareDownloadWakeLock");
                    downloadWakeLock.setReferenceCounted(false);
                }
            }
            if (downloadWakeLock != null && !downloadWakeLock.isHeld()) {
                downloadWakeLock.acquire(45 * 60 * 1000L); // 45 min max safety timeout
            }
        } catch (Exception ignored) {}
    }

    private static synchronized void releaseDownloadWakeLock() {
        try {
            if (activeDownloads.get() <= 0 && !isBatchActive) {
                if (downloadWakeLock != null && downloadWakeLock.isHeld()) {
                    downloadWakeLock.release();
                }
            }
        } catch (Exception ignored) {}
    }

    public void updateDownloadNotification(int completedCount, int totalCount, int percent, boolean isCompleted) {
        try {
            ensureNotificationChannel();
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) return;

            if (isCompleted || (!isBatchActive && totalCount > 0 && completedCount >= totalCount && activeDownloads.get() <= 0)) {
                manager.cancel(NOTIFICATION_ID);
                return;
            }

            if (!isBatchActive && totalCount <= 0 && activeDownloads.get() <= 0) {
                manager.cancel(NOTIFICATION_ID);
                return;
            }

            Intent intent = new Intent(this, ShareActivity.class);
            intent.setAction(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            intent.putExtra("from_notification", true);

            int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
            }
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, pendingFlags);

            String title = "Nimiyo Quicksave";
            String text = "(" + completedCount + "/" + totalCount + ") Downloading - Jangan hapus dari Riwayat app.";

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setShowWhen(false)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setContentIntent(pendingIntent);

            if (totalCount > 0) {
                int overallPercent = Math.min(Math.max(percent, 0), 100);
                builder.setProgress(100, overallPercent, false);
            } else {
                builder.setProgress(0, 0, true);
            }

            manager.notify(NOTIFICATION_ID, builder.build());
        } catch (Exception ignored) {}
    }

    private void cleanAndFinishShareActivity() {
        try {
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.cancel(NOTIFICATION_ID);
            }
        } catch (Exception ignored) {}

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                if (am != null) {
                    List<ActivityManager.AppTask> appTasks = am.getAppTasks();
                    if (appTasks != null) {
                        for (ActivityManager.AppTask appTask : appTasks) {
                            try {
                                ActivityManager.RecentTaskInfo info = appTask.getTaskInfo();
                                if (info != null && info.baseIntent != null && info.baseIntent.getComponent() != null) {
                                    String cls = info.baseIntent.getComponent().getClassName();
                                    if (cls != null && cls.contains("ShareActivity")) {
                                        appTask.finishAndRemoveTask();
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                finishAndRemoveTask();
            } else {
                finish();
            }
        } catch (Exception ignored) {}

        try {
            overridePendingTransition(0, android.R.anim.fade_out);
        } catch (Exception ignored) {}
    }

    private void scheduleAutoCloseAndCleanTask() {
        if (isClosingScheduled) return;
        isClosingScheduled = true;

        runOnUiThread(() -> {
            try {
                NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (manager != null) manager.cancel(NOTIFICATION_ID);
            } catch (Exception ignored) {}

            String lang = "en";
            try {
                android.content.SharedPreferences prefs = getSharedPreferences("nimiyo_app_settings_prefs", Context.MODE_PRIVATE);
                String settingsJson = prefs.getString("settings_json", "{}");
                JSONObject obj = new JSONObject(settingsJson);
                lang = obj.optString("appLanguage", "en");
            } catch (Exception ignored) {}

            int total = sessionTotalDownloads.get();
            if (total <= 0) total = sessionCompletedDownloads.get();
            if (total > 0) {
                String msg;
                if ("id".equals(lang)) {
                    msg = total + " Berhasil tersimpan, App ditutup.";
                } else if ("zh".equals(lang)) {
                    msg = total + " 个文件保存成功，正在关闭应用。";
                } else if ("ja".equals(lang)) {
                    msg = total + " 件の保存が完了しました。アプリを終了します。";
                } else {
                    msg = total + " Successfully saved, App closing.";
                }
                Toast.makeText(ShareActivity.this, msg, Toast.LENGTH_SHORT).show();
            }

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                cleanAndFinishShareActivity();
            }, 1600);
        });
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Make window translucent with transparent system bars
        Window window = getWindow();
        window.setBackgroundDrawableResource(android.R.color.transparent);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
        }

        handleIntent(getIntent());

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Create embedded transparent WebView
        webView = new WebView(this);
        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setUserAgentString(settings.getUserAgentString() + " NimiyoShare/1.0");

        NimiyoShareBridge bridge = new NimiyoShareBridge(this);
        webView.addJavascriptInterface(bridge, "NimiyoShareBridge");
        webView.addJavascriptInterface(bridge, "NimidzShareBridge"); // compatibility alias

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                dispatchUrlToWebview();
            }
        });

        setContentView(webView);
        
        android.content.SharedPreferences prefs = getSharedPreferences("nimiyo_app_settings_prefs", Context.MODE_PRIVATE);
        String settingsJson = prefs.getString("settings_json", "{}");
        String uiTheme = "neobrutalism";
        boolean darkMode = false;
        String accentColor = "";
        try {
            JSONObject obj = new JSONObject(settingsJson);
            uiTheme = obj.optString("uiTheme", "neobrutalism");
            darkMode = obj.optBoolean("darkMode", false);
            accentColor = obj.optString("accentColor", "");
        } catch (Exception ignored) {}

        String loadUrl = "file:///android_asset/public/share.html?theme=" + uiTheme + "&darkMode=" + (darkMode ? "1" : "0");
        if (accentColor != null && !accentColor.trim().isEmpty()) {
            loadUrl += "&accentColor=" + Uri.encode(accentColor.trim());
        }

        registerDownloadCompleteReceiver();
        webView.loadUrl(loadUrl);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (downloadCompleteReceiver != null) {
            try {
                unregisterReceiver(downloadCompleteReceiver);
            } catch (Exception ignored) {}
            downloadCompleteReceiver = null;
        }
        try {
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.cancel(NOTIFICATION_ID);
            }
        } catch (Exception ignored) {}
        isBatchActive = false;
        releaseDownloadWakeLock();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        isDismissRequested = false;
        if (intent != null && intent.getBooleanExtra("from_notification", false)) {
            if (webView != null) {
                webView.evaluateJavascript("if (window.onWindowBroughtToFront) { window.onWindowBroughtToFront(); }", null);
            }
            return;
        }
        if (intent != null && Intent.ACTION_SEND.equals(intent.getAction())) {
            handleIntent(intent);
            dispatchUrlToWebview();
        }
    }

    private void dispatchUrlToWebview() {
        if (webView != null) {
            String escapedUrl = extractedUrl.replace("\\", "\\\\").replace("\"", "\\\"");
            android.content.SharedPreferences prefs = getSharedPreferences("nimiyo_app_settings_prefs", Context.MODE_PRIVATE);
            String settingsJson = prefs.getString("settings_json", "{}");
            String escapedSettings = settingsJson.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");

            webView.evaluateJavascript(
                "window.NimidzShareBridge = window.NimiyoShareBridge;" +
                "window.__NIMIYO_SETTINGS_RAW = \"" + escapedSettings + "\";" +
                "try { if (window.applyNimiyoSettings) { window.applyNimiyoSettings(JSON.parse(\"" + escapedSettings + "\")); } } catch(e) {}" +
                "window.__NIMIYO_SHARE_URL = \"" + escapedUrl + "\";" +
                "if (window.onShareUrlReady) { window.onShareUrlReady(\"" + escapedUrl + "\"); }",
                null
            );
        }
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (sharedText == null) {
                sharedText = intent.getStringExtra(Intent.EXTRA_SUBJECT);
            }
            if (sharedText == null && intent.getClipData() != null && intent.getClipData().getItemCount() > 0) {
                ClipData.Item item = intent.getClipData().getItemAt(0);
                if (item.getText() != null) {
                    sharedText = item.getText().toString();
                } else if (item.getUri() != null) {
                    sharedText = item.getUri().toString();
                }
            }

            if (sharedText != null) {
                extractedUrl = findUrlInText(sharedText);
                if (extractedUrl.isEmpty()) {
                    extractedUrl = sharedText.trim();
                }
            }
        }
    }

    private String findUrlInText(String text) {
        if (text == null) return "";
        Pattern pattern = Pattern.compile("https?://[a-zA-Z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=%]+");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            String u = matcher.group(0);
            while (u.length() > 0 && (
                u.endsWith(")") || u.endsWith("]") || u.endsWith(">") ||
                u.endsWith(",") || u.endsWith(";") || u.endsWith(".") ||
                u.endsWith("!") || u.endsWith("?") || u.endsWith("\"") ||
                u.endsWith("'")
            )) {
                u = u.substring(0, u.length() - 1);
            }
            return u;
        }
        return "";
    }

    @Override
    public void onBackPressed() {
        if (activeDownloads.get() > 0 || isBatchActive) {
            isDismissRequested = true;
            moveTaskToBack(true);
            return;
        }
        if (webView != null) {
            webView.evaluateJavascript("if (window.dismissPanel) { window.dismissPanel(); } else { window.NimiyoShareBridge.dismiss(); }", null);
        } else {
            super.onBackPressed();
        }
    }

    public void dismissDialog() {
        runOnUiThread(() -> {
            if (activeDownloads.get() > 0 || isBatchActive) {
                isDismissRequested = true;
                moveTaskToBack(true);
                return;
            }
            cleanAndFinishShareActivity();
        });
    }

    private void registerDownloadCompleteReceiver() {
        if (downloadCompleteReceiver != null) return;
        downloadCompleteReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) {
                    long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                    if (id != -1) {
                        String historyJson = activeDownloadHistories.remove(id);
                        if (historyJson != null && !historyJson.trim().isEmpty()) {
                            saveHistoryItemDirect(context, historyJson);
                        }
                    }
                }
            }
        };
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(downloadCompleteReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(downloadCompleteReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
            }
        } catch (Exception ignored) {}
    }

    private static void saveHistoryItemDirect(Context context, String itemJson) {
        try {
            if (itemJson != null && !itemJson.trim().isEmpty()) {
                android.content.SharedPreferences prefs = context.getSharedPreferences("nimiyo_app_history_prefs", Context.MODE_PRIVATE);
                String current = prefs.getString("pending_history_list", "[]");
                org.json.JSONArray arr;
                try {
                    arr = new org.json.JSONArray(current);
                } catch (Exception e) {
                    arr = new org.json.JSONArray();
                }
                org.json.JSONObject newObj = new org.json.JSONObject(itemJson);
                String newFilename = newObj.optString("filename", "").trim().toLowerCase();
                boolean exists = false;
                for (int i = 0; i < arr.length(); i++) {
                    org.json.JSONObject ex = arr.optJSONObject(i);
                    if (ex != null) {
                        String exFn = ex.optString("filename", "").trim().toLowerCase();
                        if (!newFilename.isEmpty() && newFilename.equals(exFn)) {
                            exists = true;
                            break;
                        }
                    }
                }
                if (!exists) {
                    arr.put(newObj);
                    prefs.edit().putString("pending_history_list", arr.toString()).apply();
                }
            }
        } catch (Exception ignored) {}
    }

    private void ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                NotificationChannel channel = manager.getNotificationChannel(CHANNEL_ID);
                if (channel == null) {
                    NotificationChannel newChannel = new NotificationChannel(
                        CHANNEL_ID,
                        "Nimiyo QuickSave",
                        NotificationManager.IMPORTANCE_DEFAULT
                    );
                    newChannel.setDescription("Status unduhan berkas Nimiyo QuickSave");
                    newChannel.setShowBadge(false);
                    newChannel.setSound(null, null);
                    newChannel.enableVibration(false);
                    manager.createNotificationChannel(newChannel);
                }
            }
        }
    }

    private void trackDownloadTask(long downloadId, String reqId, String filename, String mimeType, String subFolder, String historyItemJson, boolean isBatch) {
        if (historyItemJson != null && !historyItemJson.trim().isEmpty()) {
            activeDownloadHistories.put(downloadId, historyItemJson);
        }

        httpExecutor.execute(() -> {
            DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            boolean downloading = true;
            boolean success = false;
            String errorMsg = "";
            String savedPath = "";

            while (downloading) {
                try {
                    Thread.sleep(400);
                } catch (InterruptedException e) {
                    break;
                }

                DownloadManager.Query q = new DownloadManager.Query();
                q.setFilterById(downloadId);
                try (Cursor c = dm.query(q)) {
                    if (c != null && c.moveToFirst()) {
                        int statusIdx = c.getColumnIndex(DownloadManager.COLUMN_STATUS);
                        int status = c.getInt(statusIdx);

                        int bytesDownloadedIdx = c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                        int bytesTotalIdx = c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);

                        long bytesDownloaded = bytesDownloadedIdx != -1 ? c.getLong(bytesDownloadedIdx) : 0;
                        long bytesTotal = bytesTotalIdx != -1 ? c.getLong(bytesTotalIdx) : 0;

                        if (bytesTotal > 0) {
                            int percent = (int) ((bytesDownloaded * 100) / bytesTotal);
                            final int curPercent = Math.min(Math.max(percent, 0), 99);
                            activeProgressMap.put(downloadId, curPercent);

                            int total = sessionTotalDownloads.get();
                            int completed = sessionCompletedDownloads.get();
                            if (total <= 0) total = Math.max(1, activeDownloads.get());
                            int sumProgress = completed * 100;
                            for (Integer p : activeProgressMap.values()) {
                                if (p != null) sumProgress += p;
                            }
                            int avg = Math.min(99, sumProgress / Math.max(1, total));
                            updateDownloadNotification(completed, total, avg, false);

                            runOnUiThread(() -> {
                                if (webView != null) {
                                    webView.evaluateJavascript("if (window.__onNativeDownloadProgress) { window.__onNativeDownloadProgress('" + reqId + "', " + curPercent + "); }", null);
                                }
                            });
                        }

                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            downloading = false;
                            success = true;

                            int uriIdx = c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
                            if (uriIdx != -1) {
                                String localUriStr = c.getString(uriIdx);
                                if (localUriStr != null) {
                                    try {
                                        Uri lUri = Uri.parse(localUriStr);
                                        savedPath = lUri.getPath();
                                    } catch (Exception ignored) {}
                                }
                            }
                            if (savedPath == null || savedPath.isEmpty()) {
                                File pubDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                                File target = new File(pubDir, "Nimiyo/" + subFolder + "/" + filename);
                                savedPath = target.getAbsolutePath();
                            }

                            if (savedPath != null && !savedPath.isEmpty()) {
                                MediaScannerConnection.scanFile(
                                    ShareActivity.this,
                                    new String[]{savedPath},
                                    new String[]{mimeType},
                                    null
                                );
                            }

                            if (historyItemJson != null && !historyItemJson.trim().isEmpty()) {
                                saveHistoryItemDirect(ShareActivity.this, historyItemJson);
                            }

                        } else if (status == DownloadManager.STATUS_FAILED) {
                            downloading = false;
                            int reasonIdx = c.getColumnIndex(DownloadManager.COLUMN_REASON);
                            int reason = reasonIdx != -1 ? c.getInt(reasonIdx) : -1;
                            errorMsg = "DownloadManager error code: " + reason;
                        }
                    } else {
                        downloading = false;
                    }
                } catch (Exception e) {
                    // Query error, will retry in next loop
                }
            }

            final boolean finalSuccess = success;
            final String finalErrorMsg = errorMsg;
            final String finalSavedPath = savedPath;

            runOnUiThread(() -> {
                if (webView != null) {
                    String payload = "{\"success\":" + finalSuccess + ",\"error\":\"" + finalErrorMsg.replace("\"", "\\\"") + "\",\"filePath\":\"" + finalSavedPath.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
                    webView.evaluateJavascript(
                        "if (window.__nimiyoShareCallbacks && window.__nimiyoShareCallbacks['" + reqId + "']) {" +
                        "  window.__nimiyoShareCallbacks['" + reqId + "'](" + payload + ");" +
                        "  delete window.__nimiyoShareCallbacks['" + reqId + "'];" +
                        "}",
                        null
                    );
                }
            });

            activeProgressMap.remove(downloadId);
            activeDownloadIds.remove(downloadId);
            int completed = sessionCompletedDownloads.incrementAndGet();
            int remaining = activeDownloads.decrementAndGet();
            releaseDownloadWakeLock();

            int total = sessionTotalDownloads.get();
            if (total <= 0) total = completed;

            if (remaining <= 0 && !isBatchActive) {
                updateDownloadNotification(completed, total, 100, true);
                scheduleAutoCloseAndCleanTask();
            } else if (isBatchActive && remaining <= 0 && completed >= total && total > 0) {
                updateDownloadNotification(completed, total, 100, true);
                scheduleAutoCloseAndCleanTask();
            } else {
                int sumProgress = completed * 100;
                for (Integer p : activeProgressMap.values()) {
                    if (p != null) sumProgress += p;
                }
                int avg = Math.min(99, sumProgress / Math.max(1, total));
                updateDownloadNotification(completed, total, avg, false);
            }
        });
    }

    private Map<String, String> getDownloadHeaders(String url) {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
        headers.put("Accept", "*/*");
        headers.put("Accept-Language", "en-US,en;q=0.9,id;q=0.8");

        if (url != null) {
            String lower = url.toLowerCase();
            if (lower.contains("snaptik.app") || lower.contains("snaptik")) {
                headers.put("Referer", "https://snaptik.app/");
                headers.put("Origin", "https://snaptik.app");
            } else if (lower.contains("ssstik.io") || lower.contains("tikcdn.io")) {
                headers.put("Referer", "https://ssstik.io/");
                headers.put("Origin", "https://ssstik.io");
            } else if (lower.contains("tiktokio.com")) {
                headers.put("Referer", "https://tiktokio.com/");
                headers.put("Origin", "https://tiktokio.com");
            } else if (lower.contains("tiktok.com") || lower.contains("byteoversea") || lower.contains("ibytedtos") || lower.contains("tikwm")) {
                headers.put("Referer", "https://www.tiktok.com/");
            } else if (lower.contains("instagram.com") || lower.contains("cdninstagram") || lower.contains("fbcdn.net")) {
                headers.put("Referer", "https://www.instagram.com/");
            } else if (lower.contains("spotidown") || lower.contains("spotify")) {
                headers.put("Referer", "https://spotidown.app/");
            } else if (lower.contains("forhub.io") || lower.contains("dl.forhub.io")) {
                headers.put("Referer", "https://www.forhub.io/download.php");
            } else if (lower.contains("klickaud.org") || lower.contains("dl.klickaud.org")) {
                headers.put("Referer", "https://www.klickaud.org/download.php");
            } else if (lower.contains("facebook.com") || lower.contains("fbcdn")) {
                headers.put("Referer", "https://www.facebook.com/");
            } else if (lower.contains("twitter.com") || lower.contains("x.com") || lower.contains("twimg")) {
                headers.put("Referer", "https://twitter.com/");
            }
        }
        return headers;
    }

    private HttpURLConnection connectWithRedirects(String initialUrl, String method, Map<String, String> headers, byte[] postData) throws Exception {
        String currentUrl = initialUrl;
        int redirectCount = 0;
        final int MAX_REDIRECTS = 10;

        while (redirectCount < MAX_REDIRECTS) {
            URL url = new URL(currentUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(60000);
            conn.setInstanceFollowRedirects(false); // Manually handle cross-protocol/domain redirects

            // Default headers
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
            conn.setRequestProperty("Accept", "*/*");
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9,id;q=0.8");

            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    conn.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }

            if (postData != null && ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method))) {
                conn.setDoOutput(true);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(postData);
                    os.flush();
                }
            }

            int code = conn.getResponseCode();
            if (code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP ||
                code == HttpURLConnection.HTTP_SEE_OTHER || code == 307 || code == 308) {
                
                String location = conn.getHeaderField("Location");
                conn.disconnect();
                if (location != null && !location.isEmpty()) {
                    if (location.startsWith("/")) {
                        URL prevUrl = new URL(currentUrl);
                        location = prevUrl.getProtocol() + "://" + prevUrl.getHost() + location;
                    }
                    currentUrl = location;
                    method = "GET"; // Redirects convert to GET
                    postData = null;
                    redirectCount++;
                    if (headers != null) {
                        headers.putAll(getDownloadHeaders(currentUrl));
                    }
                    continue;
                }
            }
            return conn;
        }
        throw new Exception("Too many HTTP redirects");
    }

    // Native Bridge for Javascript
    public class NimiyoShareBridge {
        private final ShareActivity activity;

        public NimiyoShareBridge(ShareActivity activity) {
            this.activity = activity;
        }

        @JavascriptInterface
        public String getSharedUrl() {
            return extractedUrl;
        }

        @JavascriptInterface
        public String getAppSettings() {
            try {
                android.content.SharedPreferences prefs = activity.getSharedPreferences("nimiyo_app_settings_prefs", Context.MODE_PRIVATE);
                return prefs.getString("settings_json", "{}");
            } catch (Exception e) {
                return "{}";
            }
        }

        @JavascriptInterface
        public void dismiss() {
            dismissDialog();
        }

        @JavascriptInterface
        public void finishAndClose() {
            scheduleAutoCloseAndCleanTask();
        }

        @JavascriptInterface
        public void cancelAllDownloads() {
            isClosingScheduled = true;
            activeProgressMap.clear();
            sessionTotalDownloads.set(0);
            sessionCompletedDownloads.set(0);
            httpExecutor.execute(() -> {
                try {
                    DownloadManager dm = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
                    if (dm != null && !activeDownloadIds.isEmpty()) {
                        long[] ids = new long[activeDownloadIds.size()];
                        int idx = 0;
                        for (Long id : activeDownloadIds) {
                            ids[idx++] = id;
                        }
                        dm.remove(ids);
                        activeDownloadIds.clear();
                    }
                } catch (Exception ignored) {}

                try {
                    NotificationManager manager = (NotificationManager) activity.getSystemService(Context.NOTIFICATION_SERVICE);
                    if (manager != null) {
                        manager.cancel(NOTIFICATION_ID);
                    }
                } catch (Exception ignored) {}

                activeDownloads.set(0);
                isBatchActive = false;
                releaseDownloadWakeLock();

                activity.runOnUiThread(() -> {
                    activity.cleanAndFinishShareActivity();
                });
            });
        }

        @JavascriptInterface
        public void updateQuickSaveNotification(int completedCount, int totalCount, int percent, boolean isCompleted) {
            activity.runOnUiThread(() -> {
                activity.updateDownloadNotification(completedCount, totalCount, percent, isCompleted);
            });
        }

        @JavascriptInterface
        public void minimize() {
            isDismissRequested = true;
            activity.runOnUiThread(() -> {
                activity.moveTaskToBack(true);
            });
        }

        @JavascriptInterface
        public void saveHistoryItem(String itemJson) {
            try {
                if (itemJson != null && !itemJson.trim().isEmpty()) {
                    android.content.SharedPreferences prefs = activity.getSharedPreferences("nimiyo_app_history_prefs", Context.MODE_PRIVATE);
                    String current = prefs.getString("pending_history_list", "[]");
                    org.json.JSONArray arr;
                    try {
                        arr = new org.json.JSONArray(current);
                    } catch (Exception e) {
                        arr = new org.json.JSONArray();
                    }
                    org.json.JSONObject newObj = new org.json.JSONObject(itemJson);
                    String newFilename = newObj.optString("filename", "").trim().toLowerCase();
                    boolean exists = false;
                    for (int i = 0; i < arr.length(); i++) {
                        org.json.JSONObject ex = arr.optJSONObject(i);
                        if (ex != null) {
                            String exFn = ex.optString("filename", "").trim().toLowerCase();
                            if (!newFilename.isEmpty() && newFilename.equals(exFn)) {
                                exists = true;
                                break;
                            }
                        }
                    }
                    if (!exists) {
                        arr.put(newObj);
                        prefs.edit().putString("pending_history_list", arr.toString()).apply();
                    }
                }
            } catch (Exception ignored) {}
        }

        @JavascriptInterface
        public void setBatchActive(boolean active, int totalCount) {
            isBatchActive = active;
            Context appContext = activity.getApplicationContext();
            if (active) {
                isClosingScheduled = false;
                if (totalCount > 0) {
                    sessionTotalDownloads.set(totalCount);
                }
                sessionCompletedDownloads.set(0);
                activeProgressMap.clear();
                acquireDownloadWakeLock(activity);
                updateDownloadNotification(0, totalCount, 0, false);
            } else {
                releaseDownloadWakeLock();
                int total = sessionTotalDownloads.get();
                int completed = sessionCompletedDownloads.get();
                if (activeDownloads.get() <= 0 && completed >= total && total > 0) {
                    scheduleAutoCloseAndCleanTask();
                }
            }
        }

        @JavascriptInterface
        public void setBatchActive(boolean active) {
            setBatchActive(active, 0);
        }

        @JavascriptInterface
        public void notifyBatchComplete(int count, String message) {
            if (downloadCompleteReceiver != null) {
                try {
                    unregisterReceiver(downloadCompleteReceiver);
                } catch (Exception ignored) {}
                downloadCompleteReceiver = null;
            }
            isBatchActive = false;
            releaseDownloadWakeLock();
            if (activeDownloads.get() <= 0) {
                scheduleAutoCloseAndCleanTask();
            }
        }

        @JavascriptInterface
        public void showToast(String message) {
            activity.runOnUiThread(() -> Toast.makeText(activity, message, Toast.LENGTH_SHORT).show());
        }

        @JavascriptInterface
        public void showNotification(String title, String message, int progress, int max, boolean isCompleted) {
            if (activeDownloads.get() > 0) return;
            try {
                NotificationManager manager = (NotificationManager) activity.getSystemService(Context.NOTIFICATION_SERVICE);
                if (manager != null && isCompleted) {
                    manager.cancel(NOTIFICATION_ID);
                }
            } catch (Exception ignored) {}
        }

        @JavascriptInterface
        public void showBatchNotification(String title, String message, int current, int total, int itemPercent, boolean isCompleted) {
            // Handled natively by updateDownloadNotification
        }

        // OS-Level Native DownloadManager Implementation (100% Background Resilient & Play Protect Safe)
        @JavascriptInterface
        public void downloadFileAsync(String fileUrl, String filename, String mimeType, boolean isAudio, String reqId) {
            downloadFileAsync(fileUrl, filename, mimeType, isAudio, reqId, "", false);
        }

        @JavascriptInterface
        public void downloadFileAsync(String fileUrl, String filename, String mimeType, boolean isAudio, String reqId, String historyItemJson) {
            downloadFileAsync(fileUrl, filename, mimeType, isAudio, reqId, historyItemJson, false);
        }

        @JavascriptInterface
        public void downloadFileAsync(String fileUrl, String filename, String mimeType, boolean isAudio, String reqId, String historyItemJson, boolean isBatch) {
            httpExecutor.execute(() -> {
                activeDownloads.incrementAndGet();
                if (!isBatch && sessionTotalDownloads.get() <= 0) {
                    sessionTotalDownloads.set(1);
                }
                acquireDownloadWakeLock(activity);

                String lowerFileName = filename.toLowerCase();
                String subFolder;
                if (isAudio || lowerFileName.endsWith(".mp3") || lowerFileName.endsWith(".m4a") || lowerFileName.endsWith(".wav") || lowerFileName.endsWith(".flac")) {
                    subFolder = "AudioYo";
                } else if (lowerFileName.endsWith(".jpg") || lowerFileName.endsWith(".jpeg") || lowerFileName.endsWith(".png") || lowerFileName.endsWith(".webp")) {
                    subFolder = "ImageYo";
                } else {
                    subFolder = "VideoYo";
                }

                try {
                    DownloadManager dm = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
                    if (dm == null) {
                        throw new Exception("Layanan DownloadManager sistem tidak tersedia");
                    }

                    Uri downloadUri = Uri.parse(fileUrl);
                    DownloadManager.Request request = new DownloadManager.Request(downloadUri);

                    String relativeDir = "Nimiyo/" + subFolder;
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, relativeDir + "/" + filename);
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN);
                    request.setTitle(filename);
                    request.setDescription("Mengunduh dengan Nimiyo Quick Save");
                    if (mimeType != null && !mimeType.isEmpty()) {
                        request.setMimeType(mimeType);
                    }
                    request.setAllowedOverMetered(true);
                    request.setAllowedOverRoaming(true);

                    Map<String, String> headers = getDownloadHeaders(fileUrl);
                    for (Map.Entry<String, String> entry : headers.entrySet()) {
                        try {
                            request.addRequestHeader(entry.getKey(), entry.getValue());
                        } catch (Exception ignored) {}
                    }

                    long downloadId = dm.enqueue(request);
                    activeDownloadIds.add(downloadId);

                    trackDownloadTask(downloadId, reqId, filename, mimeType, subFolder, historyItemJson, isBatch);

                } catch (Exception e) {
                    final String errorMsg = e.getMessage() != null ? e.getMessage() : "Gagal memulai unduhan";
                    activeDownloads.decrementAndGet();
                    releaseDownloadWakeLock();

                    activity.runOnUiThread(() -> {
                        if (webView != null) {
                            String payload = "{\"success\":false,\"error\":\"" + errorMsg.replace("\"", "\\\"") + "\",\"filePath\":\"\"}";
                            webView.evaluateJavascript(
                                "if (window.__nimiyoShareCallbacks && window.__nimiyoShareCallbacks['" + reqId + "']) {" +
                                "  window.__nimiyoShareCallbacks['" + reqId + "'](" + payload + ");" +
                                "  delete window.__nimiyoShareCallbacks['" + reqId + "'];" +
                                "}",
                                null
                            );
                        }
                    });
                }
            });
        }

        @JavascriptInterface
        public boolean saveMediaFile(String base64Data, String filename, String mimeType, boolean isAudio) {
            try {
                byte[] decodedBytes = Base64.decode(base64Data, Base64.DEFAULT);
                String lowerFileName = filename.toLowerCase();
                String subFolder;

                if (isAudio || lowerFileName.endsWith(".mp3") || lowerFileName.endsWith(".m4a") || lowerFileName.endsWith(".wav") || lowerFileName.endsWith(".flac")) {
                    subFolder = "AudioYo";
                } else if (lowerFileName.endsWith(".jpg") || lowerFileName.endsWith(".jpeg") || lowerFileName.endsWith(".png") || lowerFileName.endsWith(".webp")) {
                    subFolder = "ImageYo";
                } else {
                    subFolder = "VideoYo";
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentResolver resolver = activity.getContentResolver();
                    ContentValues contentValues = new ContentValues();
                    contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
                    contentValues.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
                    contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Nimiyo/" + subFolder);
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 1);

                    Uri collectionUri = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                    Uri itemUri = resolver.insert(collectionUri, contentValues);

                    if (itemUri != null) {
                        try (OutputStream out = resolver.openOutputStream(itemUri)) {
                            if (out != null) {
                                out.write(decodedBytes);
                                out.flush();
                            }
                        }
                        contentValues.clear();
                        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0);
                        resolver.update(itemUri, contentValues, null, null);
                        return true;
                    }
                } else {
                    File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Nimiyo/" + subFolder);
                    if (!dir.exists()) dir.mkdirs();
                    File targetFile = new File(dir, filename);
                    try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                        fos.write(decodedBytes);
                        fos.flush();
                    }
                    return true;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return false;
        }

        @JavascriptInterface
        public void openInMainApp(String url) {
            try {
                Intent mainIntent = new Intent(activity, MainActivity.class);
                mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                mainIntent.putExtra("auto_url", url);
                activity.startActivity(mainIntent);
                dismissDialog();
            } catch (Exception ignored) {}
        }

        // Native High-Performance HTTP Request Implementation for Scrapers (CORS-Free & Redirect Safe)
        @JavascriptInterface
        public String httpRequest(String jsonConfig) {
            return performNativeHttp(jsonConfig);
        }

        @JavascriptInterface
        public void httpRequestAsync(String jsonConfig, String reqId) {
            httpExecutor.execute(() -> {
                String result = performNativeHttp(jsonConfig);
                activity.runOnUiThread(() -> {
                    if (webView != null) {
                        String escapedResult = result.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
                        webView.evaluateJavascript(
                            "if (window.__nimiyoShareCallbacks && window.__nimiyoShareCallbacks['" + reqId + "']) {" +
                            "  window.__nimiyoShareCallbacks['" + reqId + "'](JSON.parse(\"" + escapedResult + "\"));" +
                            "  delete window.__nimiyoShareCallbacks['" + reqId + "'];" +
                            "}",
                            null
                        );
                    }
                });
            });
        }

        private String performNativeHttp(String jsonConfig) {
            JSONObject resultJson = new JSONObject();
            HttpURLConnection conn = null;
            try {
                JSONObject config = new JSONObject(jsonConfig);
                String targetUrl = config.getString("url");
                String method = config.optString("method", "GET").toUpperCase();
                JSONObject headersObj = config.optJSONObject("headers");
                String postData = config.optString("data", null);
                String responseType = config.optString("responseType", "text");

                Map<String, String> headersMap = new HashMap<>();
                if (headersObj != null) {
                    Iterator<String> keys = headersObj.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        headersMap.put(key, headersObj.getString(key));
                    }
                }

                byte[] postBytes = (postData != null && ("POST".equals(method) || "PUT".equals(method))) ? postData.getBytes(StandardCharsets.UTF_8) : null;
                conn = connectWithRedirects(targetUrl, method, headersMap, postBytes);

                int statusCode = conn.getResponseCode();
                resultJson.put("status", statusCode);

                JSONObject respHeaders = new JSONObject();
                for (Map.Entry<String, List<String>> entry : conn.getHeaderFields().entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null && !entry.getValue().isEmpty()) {
                        respHeaders.put(entry.getKey(), entry.getValue().get(0));
                    }
                }
                resultJson.put("headers", respHeaders);

                InputStream is = (statusCode >= 200 && statusCode < 400) ? conn.getInputStream() : conn.getErrorStream();
                if (is != null) {
                    if ("arraybuffer".equalsIgnoreCase(responseType) || "blob".equalsIgnoreCase(responseType)) {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = is.read(buffer)) != -1) {
                            baos.write(buffer, 0, len);
                        }
                        resultJson.put("data", Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP));
                    } else {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line).append("\n");
                        }
                        String bodyText = sb.toString();
                        try {
                            if (bodyText.trim().startsWith("{") || bodyText.trim().startsWith("[")) {
                                resultJson.put("data", new JSONObject(bodyText.trim()));
                            } else {
                                resultJson.put("data", bodyText);
                            }
                        } catch (Exception e) {
                            resultJson.put("data", bodyText);
                        }
                    }
                    is.close();
                } else {
                    resultJson.put("data", "");
                }

            } catch (Exception e) {
                try {
                    resultJson.put("status", 500);
                    resultJson.put("error", e.getMessage());
                    resultJson.put("data", e.getMessage());
                } catch (Exception ignored) {}
            } finally {
                if (conn != null) conn.disconnect();
            }
            return resultJson.toString();
        }
    }
}