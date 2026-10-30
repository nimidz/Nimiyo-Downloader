package nimiyo.litedownloader;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class DownloadService extends Service {

    public static final String CHANNEL_ID = "nimiyo_download_channel";
    public static final int FOREGROUND_NOTIFICATION_ID = 8802;

    public static final String ACTION_ENQUEUE_DOWNLOAD = "nimiyo.action.ENQUEUE_DOWNLOAD";
    public static final String EXTRA_URL = "url";
    public static final String EXTRA_FILENAME = "filename";
    public static final String EXTRA_MIME = "mime";
    public static final String EXTRA_IS_AUDIO = "is_audio";
    public static final String EXTRA_REQ_ID = "req_id";
    public static final String EXTRA_HISTORY_JSON = "history_json";
    public static final String EXTRA_IS_BATCH = "is_batch";
    public static final String ACTION_HOLD_FOREGROUND = "nimiyo.action.HOLD_FOREGROUND";
    public static final String ACTION_RELEASE_HOLD = "nimiyo.action.RELEASE_HOLD";
    public static final String EXTRA_TASK_TITLE = "task_title";

    public interface DownloadListener {
        void onProgress(String reqId, int percent);
        void onComplete(String reqId, boolean success, String error, String filePath);
    }

    private static volatile DownloadListener activeListener = null;
    private static final AtomicInteger runningCount = new AtomicInteger(0);

    public static void setListener(DownloadListener listener) {
        activeListener = listener;
    }

    public static boolean hasActiveDownloads() {
        return runningCount.get() > 0;
    }

    public static void startForegroundHold(Context context, String taskTitle) {
        if (context == null) return;
        try {
            Intent intent = new Intent(context, DownloadService.class);
            intent.setAction(ACTION_HOLD_FOREGROUND);
            intent.putExtra(EXTRA_TASK_TITLE, taskTitle);
            ContextCompat.startForegroundService(context, intent);
        } catch (Exception e) {
            try {
                Intent intent = new Intent(context, DownloadService.class);
                intent.setAction(ACTION_HOLD_FOREGROUND);
                intent.putExtra(EXTRA_TASK_TITLE, taskTitle);
                context.startService(intent);
            } catch (Exception ignored) {}
        }
    }

    public static void releaseForegroundHold(Context context) {
        if (context == null) return;
        try {
            Intent intent = new Intent(context, DownloadService.class);
            intent.setAction(ACTION_RELEASE_HOLD);
            context.startService(intent);
        } catch (Exception ignored) {}
    }

    public static void startDownload(Context context, String url, String filename, String mime, boolean isAudio, String reqId, String historyJson, boolean isBatch) {
        Intent intent = new Intent(context, DownloadService.class);
        intent.setAction(ACTION_ENQUEUE_DOWNLOAD);
        intent.putExtra(EXTRA_URL, url);
        intent.putExtra(EXTRA_FILENAME, filename);
        intent.putExtra(EXTRA_MIME, mime);
        intent.putExtra(EXTRA_IS_AUDIO, isAudio);
        intent.putExtra(EXTRA_REQ_ID, reqId);
        intent.putExtra(EXTRA_HISTORY_JSON, historyJson);
        intent.putExtra(EXTRA_IS_BATCH, isBatch);

        try {
            ContextCompat.startForegroundService(context, intent);
        } catch (Exception e) {
            context.startService(intent);
        }
    }

    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private PowerManager.WakeLock wakeLock = null;
    private WifiManager.WifiLock wifiLock = null;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ConcurrentHashMap<String, String> activeTasks = new ConcurrentHashMap<>();

    @Override
    public void onCreate() {
        super.onCreate();
        ensureNotificationChannel();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();

            if (ACTION_HOLD_FOREGROUND.equals(action)) {
                String title = intent.getStringExtra(EXTRA_TASK_TITLE);
                if (title == null || title.isEmpty()) title = "Mengunduh berkas...";
                runningCount.incrementAndGet();
                acquireLocks();
                promoteToForeground(title, 20);
                return START_NOT_STICKY;
            }

            if (ACTION_RELEASE_HOLD.equals(action)) {
                int remaining = runningCount.decrementAndGet();
                if (remaining <= 0) {
                    runningCount.set(0);
                    releaseLocksIfIdle();
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            stopForeground(STOP_FOREGROUND_DETACH);
                        } else {
                            stopForeground(false);
                        }
                    } catch (Exception ignored) {}
                    stopSelf();
                }
                return START_NOT_STICKY;
            }

            if (ACTION_ENQUEUE_DOWNLOAD.equals(action)) {
                String url = intent.getStringExtra(EXTRA_URL);
                String filename = intent.getStringExtra(EXTRA_FILENAME);
                String mime = intent.getStringExtra(EXTRA_MIME);
                boolean isAudio = intent.getBooleanExtra(EXTRA_IS_AUDIO, false);
                String reqId = intent.getStringExtra(EXTRA_REQ_ID);
                String historyJson = intent.getStringExtra(EXTRA_HISTORY_JSON);
                boolean isBatch = intent.getBooleanExtra(EXTRA_IS_BATCH, false);

                if (url != null && filename != null) {
                    runningCount.incrementAndGet();
                    activeTasks.put(reqId != null ? reqId : filename, filename);

                    acquireLocks();
                    promoteToForeground("Mengunduh " + filename, 10);

                    executor.execute(() -> {
                        performDownload(url, filename, mime, isAudio, reqId, historyJson, isBatch);
                    });
                }
            }
        }
        return START_NOT_STICKY;
    }

    private synchronized void acquireLocks() {
        try {
            if (wakeLock == null) {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                if (pm != null) {
                    wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Nimiyo:DownloadServiceWakeLock");
                    wakeLock.setReferenceCounted(false);
                }
            }
            if (wakeLock != null && !wakeLock.isHeld()) {
                wakeLock.acquire(45 * 60 * 1000L); // 45 minutes max
            }
            if (wifiLock == null) {
                WifiManager wm = (WifiManager) getSystemService(Context.WIFI_SERVICE);
                if (wm != null) {
                    int mode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? WifiManager.WIFI_MODE_FULL_HIGH_PERF : WifiManager.WIFI_MODE_FULL;
                    wifiLock = wm.createWifiLock(mode, "Nimiyo:DownloadServiceWifiLock");
                    wifiLock.setReferenceCounted(false);
                }
            }
            if (wifiLock != null && !wifiLock.isHeld()) {
                wifiLock.acquire();
            }
        } catch (Exception ignored) {}
    }

    private synchronized void releaseLocksIfIdle() {
        try {
            if (runningCount.get() <= 0) {
                if (wakeLock != null && wakeLock.isHeld()) {
                    wakeLock.release();
                }
                if (wifiLock != null && wifiLock.isHeld()) {
                    wifiLock.release();
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
                        "Nimiyo Downloads",
                        NotificationManager.IMPORTANCE_LOW
                    );
                    newChannel.setDescription("Status unduhan berkas Nimiyo");
                    newChannel.setShowBadge(false);
                    manager.createNotificationChannel(newChannel);
                }
            }
        }
    }

    private Notification buildNotification(String message, int progress, boolean isCompleted) {
        Intent openAppIntent = new Intent(this, MainActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT : PendingIntent.FLAG_UPDATE_CURRENT
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Nimiyo Downloader")
            .setContentText(message)
            .setSmallIcon(isCompleted ? android.R.drawable.stat_sys_download_done : android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setOngoing(!isCompleted)
            .setAutoCancel(isCompleted)
            .setOnlyAlertOnce(true)
            .setPriority(isCompleted ? NotificationCompat.PRIORITY_DEFAULT : NotificationCompat.PRIORITY_LOW);

        if (!isCompleted) {
            builder.setProgress(100, Math.max(0, Math.min(100, progress)), false);
        } else {
            builder.setProgress(0, 0, false);
        }

        return builder.build();
    }

    private void promoteToForeground(String message, int progress) {
        Notification notif = buildNotification(message, progress, false);
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(FOREGROUND_NOTIFICATION_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(FOREGROUND_NOTIFICATION_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(FOREGROUND_NOTIFICATION_ID, notif);
            }
        } catch (Exception e) {
            try {
                startForeground(FOREGROUND_NOTIFICATION_ID, notif);
            } catch (Exception ignored) {}
        }
    }

    private void updateNotification(String message, int progress, boolean isCompleted) {
        try {
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.notify(FOREGROUND_NOTIFICATION_ID, buildNotification(message, progress, isCompleted));
            }
        } catch (Exception ignored) {}
    }

    private void performDownload(String fileUrl, String filename, String mimeType, boolean isAudio, String reqId, String historyItemJson, boolean isBatch) {
        boolean success = false;
        String errorMsg = "";
        String actualSavedPath = null;
        HttpURLConnection conn = null;
        OutputStream outStream = null;
        InputStream is = null;
        Uri itemUri = null;
        File targetFile = null;
        ContentResolver resolver = getContentResolver();
        ContentValues contentValues = new ContentValues();

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
            Map<String, String> headers = getDownloadHeaders(fileUrl);
            int maxConnRetries = 2;
            int connAttempt = 0;
            while (true) {
                try {
                    conn = connectWithRedirects(fileUrl, "GET", headers);
                    int statusCode = conn.getResponseCode();
                    if (statusCode < 200 || statusCode >= 400) {
                        throw new Exception("HTTP server error " + statusCode);
                    }
                    break;
                } catch (Exception connEx) {
                    connAttempt++;
                    if (connAttempt > maxConnRetries) {
                        throw connEx;
                    }
                    try { Thread.sleep(1200); } catch (Exception ignored) {}
                }
            }

            long contentLength = conn.getContentLengthLong();
            String resContentType = conn.getContentType();
            if (resContentType != null && resContentType.toLowerCase().contains("text/html") && !lowerFileName.endsWith(".html")) {
                throw new Exception("Server returned web page instead of media file (possibly blocked or expired)");
            }
            is = conn.getInputStream();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
                contentValues.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
                contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Nimiyo/" + subFolder);
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 1);

                Uri collectionUri = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                itemUri = resolver.insert(collectionUri, contentValues);
                if (itemUri != null) {
                    outStream = resolver.openOutputStream(itemUri);
                }
            } else {
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Nimiyo/" + subFolder);
                if (!dir.exists()) dir.mkdirs();
                targetFile = new File(dir, filename);
                outStream = new FileOutputStream(targetFile);
            }

            if (outStream == null) {
                throw new Exception("Could not open output storage stream");
            }

            byte[] buffer = new byte[32768];
            int bytesRead;
            long totalBytesRead = 0;
            int lastPercent = 0;
            long lastNotifUpdate = 0;

            while ((bytesRead = is.read(buffer)) != -1) {
                outStream.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;

                if (contentLength > 0) {
                    int percent = (int) ((totalBytesRead * 100) / contentLength);
                    if (percent - lastPercent >= 3) {
                        lastPercent = percent;
                        final int curPercent = percent;
                        long now = System.currentTimeMillis();
                        if (now - lastNotifUpdate > 800) {
                            lastNotifUpdate = now;
                            updateNotification("Mengunduh " + filename + " (" + curPercent + "%)", curPercent, false);
                        }
                        if (activeListener != null) {
                            mainHandler.post(() -> {
                                if (activeListener != null) activeListener.onProgress(reqId, curPercent);
                            });
                        }
                    }
                }
            }

            outStream.flush();
            try { is.close(); } catch (Exception ignored) {}

            if (totalBytesRead <= 0) {
                throw new Exception("Unduhan gagal: tidak ada data berkas yang diterima");
            }

            boolean isImage = lowerFileName.endsWith(".jpg") || lowerFileName.endsWith(".jpeg") || lowerFileName.endsWith(".png") || lowerFileName.endsWith(".webp");
            if (totalBytesRead < 200 && !isImage) {
                throw new Exception("Downloaded file is invalid or empty (" + totalBytesRead + " bytes)");
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && itemUri != null) {
                contentValues.clear();
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0);
                resolver.update(itemUri, contentValues, null, null);

                try (Cursor cursor = resolver.query(itemUri, new String[]{MediaStore.MediaColumns.DATA}, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int dataIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA);
                        if (dataIdx != -1) {
                            actualSavedPath = cursor.getString(dataIdx);
                        }
                    }
                } catch (Exception ignored) {}
            } else if (targetFile != null) {
                actualSavedPath = targetFile.getAbsolutePath();
            }

            if (actualSavedPath != null) {
                MediaScannerConnection.scanFile(
                    this,
                    new String[]{actualSavedPath},
                    new String[]{mimeType},
                    null
                );
            }

            // Save History directly to SharedPreferences with deduplication
            try {
                JSONObject hObj;
                if (historyItemJson != null && !historyItemJson.trim().isEmpty()) {
                    hObj = new JSONObject(historyItemJson);
                } else {
                    hObj = new JSONObject();
                    hObj.put("id", "dl_" + System.currentTimeMillis() + "_" + Math.abs(filename.hashCode()));
                    hObj.put("filename", filename);
                    String cleanTitle = filename.replaceAll("\\.[a-zA-Z0-9]+$", "").replace('_', ' ').trim();
                    hObj.put("title", cleanTitle.isEmpty() ? filename : cleanTitle);
                    hObj.put("thumbnail", "nimiyo_icon.webp");
                    hObj.put("mediaType", isAudio ? "audio" : (filename.matches("(?i).*\\.(jpg|jpeg|png|webp)") ? "image" : "video"));
                    hObj.put("platform", "other");
                    hObj.put("timestamp", System.currentTimeMillis());
                }
                if (actualSavedPath != null && !actualSavedPath.isEmpty()) {
                    hObj.put("filePath", actualSavedPath);
                }
                android.content.SharedPreferences prefs = getSharedPreferences("nimiyo_app_history_prefs", Context.MODE_PRIVATE);
                String curHist = prefs.getString("pending_history_list", "[]");
                JSONArray arr = new JSONArray(curHist);
                String hFilename = hObj.optString("filename", "").trim().toLowerCase();
                boolean exists = false;
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject ex = arr.optJSONObject(i);
                    if (ex != null) {
                        String exFn = ex.optString("filename", "").trim().toLowerCase();
                        if (!hFilename.isEmpty() && hFilename.equals(exFn)) {
                            exists = true;
                            break;
                        }
                    }
                }
                if (!exists) {
                    arr.put(hObj);
                    prefs.edit().putString("pending_history_list", arr.toString()).apply();
                }
            } catch (Exception ignored) {}

            updateNotification(filename + " selesai diunduh!", 100, true);
            mainHandler.post(() -> {
                Toast.makeText(DownloadService.this, filename + " selesai diunduh", Toast.LENGTH_SHORT).show();
            });

            success = true;

        } catch (Exception e) {
            errorMsg = e.getMessage() != null ? e.getMessage() : "Unknown download error";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && itemUri != null) {
                try { resolver.delete(itemUri, null, null); } catch (Exception ignored) {}
            }
            if (targetFile != null && targetFile.exists()) {
                try { targetFile.delete(); } catch (Exception ignored) {}
            }
            updateNotification("Gagal mengunduh: " + filename + " (" + errorMsg + ")", 0, true);
            final String fErr = errorMsg;
            mainHandler.post(() -> {
                Toast.makeText(DownloadService.this, "Gagal mengunduh " + filename + ": " + fErr, Toast.LENGTH_SHORT).show();
            });
        } finally {
            if (outStream != null) {
                try { outStream.close(); } catch (Exception ignored) {}
            }
            if (conn != null) conn.disconnect();

            activeTasks.remove(reqId != null ? reqId : filename);
            int remaining = runningCount.decrementAndGet();

            final boolean finalSuccess = success;
            final String finalErrorMsg = errorMsg;
            final String finalSavedPath = actualSavedPath != null ? actualSavedPath : "";

            if (activeListener != null) {
                mainHandler.post(() -> {
                    if (activeListener != null) {
                        activeListener.onComplete(reqId, finalSuccess, finalErrorMsg, finalSavedPath);
                    }
                });
            }

            if (remaining <= 0) {
                releaseLocksIfIdle();
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_DETACH);
                    } else {
                        stopForeground(false);
                    }
                } catch (Exception ignored) {}
                stopSelf();
            }
        }
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
            } else if (lower.contains("tikdownloader.io") || lower.contains("tikdownloader")) {
                headers.put("Referer", "https://tikdownloader.io/");
                headers.put("Origin", "https://tikdownloader.io");
            } else if (lower.contains("tiktok.com") || lower.contains("byteoversea") || lower.contains("ibytedtos") || lower.contains("tikwm")) {
                headers.put("Referer", "https://www.tiktok.com/");
            } else if (lower.contains("instagram.com") || lower.contains("cdninstagram") || lower.contains("fbcdn.net")) {
                headers.put("Referer", "https://www.instagram.com/");
            } else if (lower.contains("spotimate.app") || lower.contains("soundloaders")) {
                headers.put("Referer", "https://spotimate.app/");
                headers.put("Origin", "https://spotimate.app");
            } else if (lower.contains("spotidown") || lower.contains("spotify")) {
                headers.put("Referer", "https://spotidown.app/");
            } else if (lower.contains("ymcdn.org") || lower.contains("ytmp3")) {
                headers.put("Referer", "https://ytmp3.mobi/");
                headers.put("Origin", "https://ytmp3.mobi");
            } else if (lower.contains("youtube.com") || lower.contains("googlevideo.com") || lower.contains("youtu.be")) {
                headers.put("Referer", "https://www.youtube.com/");
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

    private HttpURLConnection connectWithRedirects(String initialUrl, String method, Map<String, String> headers) throws Exception {
        String currentUrl = initialUrl;
        int redirectCount = 0;
        final int MAX_REDIRECTS = 10;

        while (redirectCount < MAX_REDIRECTS) {
            URL url = new URL(currentUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(60000);
            conn.setReadTimeout(60000);
            conn.setInstanceFollowRedirects(false);

            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
            conn.setRequestProperty("Accept", "*/*");
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9,id;q=0.8");

            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    conn.setRequestProperty(entry.getKey(), entry.getValue());
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
                    method = "GET";
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
}
