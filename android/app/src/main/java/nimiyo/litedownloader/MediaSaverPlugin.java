package nimiyo.litedownloader;

import android.app.DownloadManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.WindowManager;
import android.util.Base64;
import android.util.Log;
import android.database.Cursor;
import android.content.ContentUris;
import android.media.MediaScannerConnection;
import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@CapacitorPlugin(name = "MediaSaver")
public class MediaSaverPlugin extends Plugin {

    private static MediaSaverPlugin instance = null;
    private static final String CHANNEL_ID = "nimiyo_download_channel_v3";
    private static final int NOTIFICATION_ID = 8801;
    private static final String MUSIC_CHANNEL_ID = "nimiyo_media_channel";
    private static final int MUSIC_NOTIFICATION_ID = 8803;
    private android.os.PowerManager.WakeLock downloadWakeLock = null;
    private final java.util.concurrent.atomic.AtomicInteger activeDownloads = new java.util.concurrent.atomic.AtomicInteger(0);
    private BroadcastReceiver becomingNoisyReceiver = null;

    public static void dispatchMediaAction(String action, Long positionMs) {
        if (instance != null) {
            JSObject data = new JSObject();
            data.put("action", action);
            if (positionMs != null) {
                data.put("position", positionMs / 1000.0);
            }
            instance.notifyListeners("onMusicMediaAction", data);
        }
    }

    @Override
    public void load() {
        super.load();
        instance = this;
        try {
            becomingNoisyReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                        dispatchMediaAction("pause", null);
                    }
                }
            };
            IntentFilter filter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getContext().registerReceiver(becomingNoisyReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                getContext().registerReceiver(becomingNoisyReceiver, filter);
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void handleOnDestroy() {
        if (becomingNoisyReceiver != null) {
            try {
                getContext().unregisterReceiver(becomingNoisyReceiver);
                becomingNoisyReceiver = null;
            } catch (Exception ignored) {}
        }
        try {
            Intent intent = new Intent(getContext(), MusicPlaybackService.class);
            intent.setAction(MusicPlaybackService.ACTION_CLEAR);
            getContext().startService(intent);
            NotificationManager manager = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.cancel(MUSIC_NOTIFICATION_ID);
            }
        } catch (Exception ignored) {}
        if (instance == this) {
            instance = null;
        }
        super.handleOnDestroy();
    }

    private void ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
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
                    newChannel.setSound(null, null);
                    newChannel.enableVibration(false);
                    manager.createNotificationChannel(newChannel);
                }
            }
        }
    }

    private void ensureMusicNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                NotificationChannel channel = manager.getNotificationChannel(MUSIC_CHANNEL_ID);
                if (channel == null) {
                    NotificationChannel newChannel = new NotificationChannel(
                        MUSIC_CHANNEL_ID,
                        "Nimiyo Media Player",
                        NotificationManager.IMPORTANCE_LOW
                    );
                    newChannel.setDescription("Status pemutar audio/musik Nimiyo");
                    newChannel.setShowBadge(false);
                    manager.createNotificationChannel(newChannel);
                }
            }
        }
    }

    @PluginMethod
    public void requestNotificationPermission(PluginCall call) {
        if (Build.VERSION.SDK_INT >= 33) {
            if (getActivity() != null && getActivity().checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                getActivity().runOnUiThread(() -> {
                    getActivity().requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
                });
            }
        }
        call.resolve(new JSObject().put("requested", true));
    }

    @PluginMethod
    public void showSystemNotification(PluginCall call) {
        String title = call.getString("title", "Nimiyo Downloader");
        String message = call.getString("message", "Mengunduh berkas...");
        int progress = call.getInt("progress", 0);
        int max = call.getInt("max", 100);
        boolean isCompleted = call.getBoolean("isCompleted", false);

        ensureNotificationChannel();

        try {
            NotificationManager manager = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) {
                call.reject("NotificationManager is unavailable");
                return;
            }

            int appIcon = isCompleted ? android.R.drawable.stat_sys_download_done : android.R.drawable.stat_sys_download;

            Intent openAppIntent = new Intent(getContext(), MainActivity.class);
            openAppIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(
                getContext(), 0, openAppIntent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT : android.app.PendingIntent.FLAG_UPDATE_CURRENT
            );

            NotificationCompat.Builder builder = new NotificationCompat.Builder(getContext(), CHANNEL_ID)
                .setSmallIcon(appIcon)
                .setContentTitle(title)
                .setContentText(message)
                .setContentIntent(pendingIntent)
                .setOngoing(!isCompleted)
                .setAutoCancel(isCompleted)
                .setOnlyAlertOnce(true)
                .setPriority(isCompleted ? NotificationCompat.PRIORITY_DEFAULT : NotificationCompat.PRIORITY_LOW);

            if (isCompleted) {
                builder.setProgress(0, 0, false);
            } else {
                builder.setProgress(max, progress, false);
            }

            manager.notify(NOTIFICATION_ID, builder.build());
            call.resolve(new JSObject().put("success", true));
        } catch (Exception e) {
            call.reject("Notification failed: " + e.getMessage());
        }
    }

    @PluginMethod
    public void clearSystemNotification(PluginCall call) {
        try {
            NotificationManager manager = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.cancel(NOTIFICATION_ID);
            }
            call.resolve(new JSObject().put("success", true));
        } catch (Exception e) {
            call.reject("Clear notification failed: " + e.getMessage());
        }
    }

    @PluginMethod
    public void showMusicPlaybackNotification(PluginCall call) {
        String title = call.getString("title", "NIMIYO Player");
        String artist = call.getString("artist", "");
        String album = call.getString("album", "");
        String artwork = call.getString("artwork", null);
        long duration = 0;
        long position = 0;

        try {
            if (call.getData().has("duration")) {
                duration = call.getData().optLong("duration", 0);
                if (duration <= 0) {
                    duration = (long) call.getData().optDouble("duration", 0.0);
                }
            }
            if (call.getData().has("position")) {
                position = call.getData().optLong("position", 0);
                if (position <= 0) {
                    position = (long) call.getData().optDouble("position", 0.0);
                }
            }
        } catch (Exception ignored) {}

        boolean isPlaying = call.getBoolean("isPlaying", true);
        Double dSpeed = call.getDouble("playbackSpeed", 1.0);
        float playbackSpeed = (dSpeed != null && dSpeed > 0.0) ? dSpeed.floatValue() : 1.0f;

        // Store artwork in memory cache to bypass Android Binder 1MB IPC limit
        if (artwork != null) {
            MusicPlaybackService.setPendingArtwork(artwork);
        }

        // 1. Direct in-memory update if service is already running (0ms latency, zero IPC, safe from background)
        MusicPlaybackService runningService = MusicPlaybackService.getInstance();
        if (runningService != null) {
            final String fTitle = title;
            final String fArtist = artist;
            final String fAlbum = album;
            final long fDuration = duration;
            final long fPosition = position;
            final boolean fIsPlaying = isPlaying;
            final float fPlaybackSpeed = playbackSpeed;
            try {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        try {
                            runningService.updateDirectly(fTitle, fArtist, fAlbum, fDuration, fPosition, fIsPlaying, fPlaybackSpeed);
                        } catch (Exception ignored) {}
                    });
                } else {
                    runningService.updateDirectly(fTitle, fArtist, fAlbum, fDuration, fPosition, fIsPlaying, fPlaybackSpeed);
                }
                call.resolve(new JSObject().put("success", true));
                return;
            } catch (Exception ignored) {}
        }

        // 2. Service not yet started: Start it via Intent
        try {
            Intent intent = new Intent(getContext(), MusicPlaybackService.class);
            intent.setAction(MusicPlaybackService.ACTION_UPDATE);
            intent.putExtra(MusicPlaybackService.EXTRA_TITLE, title);
            intent.putExtra(MusicPlaybackService.EXTRA_ARTIST, artist);
            intent.putExtra(MusicPlaybackService.EXTRA_ALBUM, album);
            // Only add artwork to Intent extra if it is NOT a huge base64 data string
            if (artwork != null && artwork.length() < 1024) {
                intent.putExtra(MusicPlaybackService.EXTRA_ARTWORK, artwork);
            }
            intent.putExtra(MusicPlaybackService.EXTRA_DURATION, duration);
            intent.putExtra(MusicPlaybackService.EXTRA_POSITION, position);
            intent.putExtra(MusicPlaybackService.EXTRA_IS_PLAYING, isPlaying);
            intent.putExtra(MusicPlaybackService.EXTRA_PLAYBACK_SPEED, playbackSpeed);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isPlaying) {
                try {
                    getContext().startForegroundService(intent);
                } catch (Exception fse) {
                    try {
                        getContext().startService(intent);
                    } catch (Exception ignored) {}
                }
            } else {
                getContext().startService(intent);
            }

            call.resolve(new JSObject().put("success", true));
        } catch (Exception e) {
            call.reject("Music notification failed: " + e.getMessage());
        }
    }

    @PluginMethod
    public void clearMusicPlaybackNotification(PluginCall call) {
        try {
            Intent intent = new Intent(getContext(), MusicPlaybackService.class);
            intent.setAction(MusicPlaybackService.ACTION_CLEAR);
            getContext().startService(intent);
            NotificationManager manager = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.cancel(MUSIC_NOTIFICATION_ID);
            }
            call.resolve(new JSObject().put("success", true));
        } catch (Exception e) {
            call.reject("Clear music notification failed: " + e.getMessage());
        }
    }

    @PluginMethod
    public void getPendingSharedHistory(PluginCall call) {
        try {
            android.content.SharedPreferences prefs = getContext().getSharedPreferences("nimiyo_app_history_prefs", Context.MODE_PRIVATE);
            String jsonArrayStr = prefs.getString("pending_history_list", "[]");
            prefs.edit().putString("pending_history_list", "[]").apply();

            JSObject ret = new JSObject();
            ret.put("items", new com.getcapacitor.JSArray(jsonArrayStr));
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Failed to get pending history: " + e.getMessage());
        }
    }

    @PluginMethod
    public void saveSharedHistoryItem(PluginCall call) {
        try {
            JSObject item = call.getObject("item");
            if (item != null) {
                android.content.SharedPreferences prefs = getContext().getSharedPreferences("nimiyo_app_history_prefs", Context.MODE_PRIVATE);
                String current = prefs.getString("pending_history_list", "[]");
                org.json.JSONArray arr;
                try {
                    arr = new org.json.JSONArray(current);
                } catch (Exception e) {
                    arr = new org.json.JSONArray();
                }
                arr.put(new org.json.JSONObject(item.toString()));
                prefs.edit().putString("pending_history_list", arr.toString()).apply();
            }
            call.resolve(new JSObject().put("success", true));
        } catch (Exception e) {
            call.reject("Failed to save history item: " + e.getMessage());
        }
    }

    @PluginMethod
    public void saveAppSettings(PluginCall call) {
        try {
            String settingsJson = call.getString("settings", "{}");
            android.content.SharedPreferences prefs = getContext().getSharedPreferences("nimiyo_app_settings_prefs", Context.MODE_PRIVATE);
            prefs.edit().putString("settings_json", settingsJson).apply();
            call.resolve(new JSObject().put("success", true));
        } catch (Exception e) {
            call.reject("Failed to save app settings: " + e.getMessage());
        }
    }

    @PluginMethod
    public void getAppSettings(PluginCall call) {
        try {
            android.content.SharedPreferences prefs = getContext().getSharedPreferences("nimiyo_app_settings_prefs", Context.MODE_PRIVATE);
            String settingsJson = prefs.getString("settings_json", "{}");
            JSObject ret = new JSObject();
            ret.put("settings", settingsJson);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Failed to get app settings: " + e.getMessage());
        }
    }

    private final ExecutorService downloadExecutor = Executors.newFixedThreadPool(4);

    private synchronized void acquireDownloadWakeLock() {
        try {
            if (activeDownloads.incrementAndGet() == 1) {
                if (downloadWakeLock == null) {
                    android.os.PowerManager pm = (android.os.PowerManager) getContext().getSystemService(Context.POWER_SERVICE);
                    if (pm != null) {
                        downloadWakeLock = pm.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "Nimiyo:DownloadWakeLock");
                        downloadWakeLock.setReferenceCounted(false);
                    }
                }
                if (downloadWakeLock != null && !downloadWakeLock.isHeld()) {
                    downloadWakeLock.acquire(30 * 60 * 1000L); // 30 minutes safety timeout
                }
            }
        } catch (Exception ignored) {}
    }

    private synchronized void releaseDownloadWakeLock() {
        try {
            if (activeDownloads.decrementAndGet() <= 0) {
                activeDownloads.set(0);
                if (downloadWakeLock != null && downloadWakeLock.isHeld()) {
                    downloadWakeLock.release();
                }
            }
        } catch (Exception ignored) {}
    }

    private void showSystemNotificationDirect(int notifId, String title, String message, int progress, int max, boolean isCompleted) {
        try {
            ensureNotificationChannel();
            NotificationManager manager = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) return;

            int appIcon = isCompleted ? android.R.drawable.stat_sys_download_done : android.R.drawable.stat_sys_download;

            Intent openAppIntent = new Intent(getContext(), MainActivity.class);
            openAppIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(
                getContext(), notifId, openAppIntent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT : android.app.PendingIntent.FLAG_UPDATE_CURRENT
            );

            NotificationCompat.Builder builder = new NotificationCompat.Builder(getContext(), CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(message)
                .setSmallIcon(appIcon)
                .setContentIntent(pendingIntent)
                .setOngoing(!isCompleted)
                .setAutoCancel(isCompleted)
                .setOnlyAlertOnce(true)
                .setPriority(isCompleted ? NotificationCompat.PRIORITY_DEFAULT : NotificationCompat.PRIORITY_LOW);

            if (isCompleted) {
                builder.setProgress(0, 0, false);
            } else {
                builder.setProgress(max, progress, false);
            }

            manager.notify(notifId, builder.build());
        } catch (Exception ignored) {}
    }

    private void showSystemNotificationDirect(String title, String message, int progress, int max, boolean isCompleted) {
        showSystemNotificationDirect(NOTIFICATION_ID, title, message, progress, max, isCompleted);
    }

    private HttpURLConnection connectWithRedirects(String initialUrl, String method, Map<String, String> headers) throws Exception {
        String currentUrl = initialUrl;
        int redirectCount = 0;
        final int MAX_REDIRECTS = 10;

        while (redirectCount < MAX_REDIRECTS) {
            URL url = new URL(currentUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(30000);
            conn.setInstanceFollowRedirects(false);

            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
            conn.setRequestProperty("Accept", "*/*");

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
                    try {
                        URL nextUrl = new URL(new URL(currentUrl), location);
                        currentUrl = nextUrl.toString();
                    } catch (Exception e) {
                        if (location.startsWith("/")) {
                            URL prevUrl = new URL(currentUrl);
                            location = prevUrl.getProtocol() + "://" + prevUrl.getHost() + location;
                        }
                        currentUrl = location;
                    }
                    method = "GET";
                    redirectCount++;
                    continue;
                }
            }
            return conn;
        }
        throw new Exception("Too many HTTP redirects");
    }

    @PluginMethod
    public void downloadFile(PluginCall call) {
        String url = call.getString("url");
        String fileName = call.getString("fileName");
        String fileType = call.getString("fileType", "video");
        JSObject headersObj = call.getObject("headers");

        if (url == null || fileName == null) {
            call.reject("url and fileName are required");
            return;
        }

        String overwriteMode = call.getString("overwriteMode", "rename");

        downloadExecutor.execute(() -> {
            acquireDownloadWakeLock();
            HttpURLConnection conn = null;
            OutputStream os = null;
            InputStream is = null;
            Uri fileUri = null;
            File targetFileLegacy = null;
            ContentResolver resolver = getContext().getContentResolver();

            try {
                String lowerFileName = fileName.toLowerCase();
                String targetSubFolder;
                if ("image".equalsIgnoreCase(fileType) || "photo".equalsIgnoreCase(fileType) ||
                    lowerFileName.endsWith(".jpg") || lowerFileName.endsWith(".jpeg") ||
                    lowerFileName.endsWith(".png") || lowerFileName.endsWith(".webp")) {
                    targetSubFolder = "ImageYo";
                } else if ("audio".equalsIgnoreCase(fileType) || lowerFileName.endsWith(".mp3") ||
                           lowerFileName.endsWith(".m4a") || lowerFileName.endsWith(".wav") || lowerFileName.endsWith(".flac")) {
                    targetSubFolder = "AudioYo";
                } else if ("apk".equalsIgnoreCase(fileType) || lowerFileName.endsWith(".apk")) {
                    targetSubFolder = "";
                } else {
                    targetSubFolder = "VideoYo";
                }

                int notifId = 8000 + Math.abs((targetSubFolder + "/" + fileName).hashCode() % 10000);

                if ("skip".equalsIgnoreCase(overwriteMode)) {
                    File existing = resolveMediaFile(targetSubFolder, fileName);
                    if (existing != null && existing.exists()) {
                        showSystemNotificationDirect(notifId, "Nimiyo Downloader", "Berkas sudah ada (dilewati): " + existing.getName(), 100, 100, true);
                        JSObject ret = new JSObject();
                        ret.put("success", true);
                        ret.put("skipped", true);
                        ret.put("fileName", existing.getName());
                        ret.put("filePath", existing.getAbsolutePath());
                        ret.put("uri", Uri.fromFile(existing).toString());
                        ret.put("subFolder", targetSubFolder);
                        ret.put("size", existing.length());
                        call.resolve(ret);
                        return;
                    }
                } else if ("overwrite".equalsIgnoreCase(overwriteMode)) {
                    handleExistingFileForOverwrite(targetSubFolder, fileName);
                }

                ensureNotificationChannel();
                showSystemNotificationDirect(notifId, "Nimiyo Downloader", "Mengunduh " + fileName, 10, 100, false);

                Map<String, String> headers = new HashMap<>();
                if (headersObj != null) {
                    Iterator<String> keys = headersObj.keys();
                    while (keys.hasNext()) {
                        String k = keys.next();
                        headers.put(k, headersObj.getString(k));
                    }
                }

                if (!headers.containsKey("Referer")) {
                    if (url.contains("tiktok") || url.contains("snaptik") || url.contains("tikwm")) {
                        headers.put("Referer", "https://www.tiktok.com/");
                    } else if (url.contains("instagram") || url.contains("cdninstagram")) {
                        headers.put("Referer", "https://www.instagram.com/");
                    } else if (url.contains("spotidown") || url.contains("spotify")) {
                        headers.put("Referer", "https://spotidown.app/");
                    }
                }

                conn = connectWithRedirects(url, "GET", headers);
                int responseCode = conn.getResponseCode();
                if (responseCode >= 400) {
                    throw new Exception("Server returned HTTP " + responseCode);
                }

                long contentLength = conn.getContentLengthLong();
                is = conn.getInputStream();

                String subFolder = targetSubFolder;
                String mime;

                if ("image".equalsIgnoreCase(fileType) || "photo".equalsIgnoreCase(fileType) ||
                    lowerFileName.endsWith(".jpg") || lowerFileName.endsWith(".jpeg") ||
                    lowerFileName.endsWith(".png") || lowerFileName.endsWith(".webp")) {
                    mime = "image/jpeg";
                    if (lowerFileName.endsWith(".png")) mime = "image/png";
                    else if (lowerFileName.endsWith(".webp")) mime = "image/webp";
                } else if ("audio".equalsIgnoreCase(fileType) || lowerFileName.endsWith(".mp3") ||
                           lowerFileName.endsWith(".m4a") || lowerFileName.endsWith(".wav") || lowerFileName.endsWith(".flac")) {
                    mime = "audio/mpeg";
                    if (lowerFileName.endsWith(".m4a")) mime = "audio/mp4";
                    else if (lowerFileName.endsWith(".wav")) mime = "audio/wav";
                    else if (lowerFileName.endsWith(".flac")) mime = "audio/flac";
                } else if ("apk".equalsIgnoreCase(fileType) || lowerFileName.endsWith(".apk")) {
                    mime = "application/vnd.android.package-archive";
                } else {
                    mime = "video/mp4";
                    if (lowerFileName.endsWith(".webm")) mime = "video/webm";
                    else if (lowerFileName.endsWith(".mov")) mime = "video/quicktime";
                }

                String actualFileName = fileName;
                String actualFilePath = null;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                    values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                    if (subFolder == null || subFolder.trim().isEmpty()) {
                        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/");
                    } else {
                        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Nimiyo/" + subFolder + "/");
                    }
                    values.put(MediaStore.MediaColumns.IS_PENDING, 1);

                    Uri collectionUri;
                    try {
                        collectionUri = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                    } catch (Throwable t) {
                        collectionUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
                    }

                    fileUri = resolver.insert(collectionUri, values);
                    if (fileUri == null) {
                        throw new Exception("Failed to create MediaStore entry in Downloads/Nimiyo/" + subFolder);
                    }

                    os = resolver.openOutputStream(fileUri);
                    if (os == null) {
                        throw new Exception("Failed to open output stream for MediaStore URI");
                    }
                } else {
                    File publicDir;
                    if (subFolder == null || subFolder.trim().isEmpty()) {
                        publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    } else {
                        publicDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Nimiyo/" + subFolder);
                    }
                    if (!publicDir.exists()) {
                        publicDir.mkdirs();
                    }

                    File targetFile = new File(publicDir, fileName);
                    if ("rename".equalsIgnoreCase(overwriteMode) && targetFile.exists()) {
                        String nameWithoutExt = fileName;
                        String ext = "";
                        int dot = fileName.lastIndexOf('.');
                        if (dot > 0) {
                            nameWithoutExt = fileName.substring(0, dot);
                            ext = fileName.substring(dot);
                        }
                        int counter = 1;
                        while (targetFile.exists()) {
                            targetFile = new File(publicDir, nameWithoutExt + " (" + counter + ")" + ext);
                            counter++;
                        }
                    }
                    targetFileLegacy = targetFile;
                    os = new FileOutputStream(targetFileLegacy);
                }

                byte[] buf = new byte[8192];
                int len;
                long totalDownloaded = 0;
                long lastProgressTime = 0;

                while ((len = is.read(buf)) != -1) {
                    os.write(buf, 0, len);
                    totalDownloaded += len;

                    long now = System.currentTimeMillis();
                    if (contentLength > 0 && (now - lastProgressTime > 400)) {
                        lastProgressTime = now;
                        int percent = (int) Math.min((totalDownloaded * 100) / contentLength, 99);
                        showSystemNotificationDirect(notifId, "Nimiyo Downloader", "Mengunduh " + fileName + " (" + percent + "%)", percent, 100, false);
                    }
                }
                os.flush();
                try { os.close(); } catch (Exception ignored) {}
                os = null;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && fileUri != null) {
                    ContentValues finishValues = new ContentValues();
                    finishValues.put(MediaStore.MediaColumns.IS_PENDING, 0);
                    resolver.update(fileUri, finishValues, null, null);

                    try (Cursor cursor = resolver.query(fileUri, new String[]{MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.DATA}, null, null, null)) {
                        if (cursor != null && cursor.moveToFirst()) {
                            int nameIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
                            if (nameIdx != -1) {
                                String dName = cursor.getString(nameIdx);
                                if (dName != null && !dName.isEmpty()) {
                                    actualFileName = dName;
                                }
                            }
                            int dataIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA);
                            if (dataIdx != -1) {
                                actualFilePath = cursor.getString(dataIdx);
                            }
                        }
                    } catch (Exception ignored) {}
                } else if (targetFileLegacy != null) {
                    actualFileName = targetFileLegacy.getName();
                    actualFilePath = targetFileLegacy.getAbsolutePath();
                    fileUri = Uri.fromFile(targetFileLegacy);

                    final String scanPath = actualFilePath;
                    final String scanMime = mime;
                    MediaScannerConnection.scanFile(
                        getContext(),
                        new String[]{scanPath},
                        new String[]{scanMime},
                        null
                    );
                }

                showSystemNotificationDirect(notifId, "Nimiyo Downloader", "Selesai: " + actualFileName, 100, 100, true);

                JSObject ret = new JSObject();
                ret.put("success", true);
                ret.put("uri", fileUri != null ? fileUri.toString() : "");
                ret.put("fileName", actualFileName);
                if (actualFilePath != null) {
                    ret.put("filePath", actualFilePath);
                }
                ret.put("subFolder", subFolder);
                ret.put("size", totalDownloaded);
                call.resolve(ret);

            } catch (Exception e) {
                if (fileUri != null) {
                    try { resolver.delete(fileUri, null, null); } catch (Exception ignored) {}
                }
                if (targetFileLegacy != null && targetFileLegacy.exists()) {
                    try { targetFileLegacy.delete(); } catch (Exception ignored) {}
                }
                int fallbackNotifId = 8000 + Math.abs(fileName.hashCode() % 10000);
                showSystemNotificationDirect(fallbackNotifId, "Nimiyo Downloader", "Gagal mengunduh " + fileName, 0, 0, true);
                call.reject("Download failed: " + e.getMessage());
            } finally {
                try { if (is != null) is.close(); } catch (Exception ignored) {}
                try { if (os != null) os.close(); } catch (Exception ignored) {}
                try { if (conn != null) conn.disconnect(); } catch (Exception ignored) {}
                releaseDownloadWakeLock();
            }
        });
    }

    @PluginMethod
    public void saveToPublicStorage(PluginCall call) {
        String filePath = call.getString("filePath");
        String fileName = call.getString("fileName");
        String fileType = call.getString("fileType");

        if (filePath == null || fileName == null) {
            call.reject("filePath and fileName are required");
            return;
        }

        try {
            String cleanedPath = filePath;
            if (cleanedPath.startsWith("file://")) {
                cleanedPath = cleanedPath.substring(7);
            }
            try {
                cleanedPath = java.net.URLDecoder.decode(cleanedPath, "UTF-8");
            } catch (Exception ignored) {}

            File sourceFile = new File(cleanedPath);
            if (!sourceFile.exists()) {
                try {
                    Uri parsedUri = Uri.parse(filePath);
                    if (parsedUri.getPath() != null) {
                        File fallbackFile = new File(parsedUri.getPath());
                        if (fallbackFile.exists()) {
                            sourceFile = fallbackFile;
                        }
                    }
                } catch (Exception ignored) {}
            }

            if (!sourceFile.exists()) {
                call.reject("Source file does not exist: " + cleanedPath);
                return;
            }

            ContentResolver resolver = getContext().getContentResolver();
            
            String lowerFileName = fileName.toLowerCase();
            String subFolder;
            String mime;

            if ("image".equalsIgnoreCase(fileType) || "photo".equalsIgnoreCase(fileType) ||
                lowerFileName.endsWith(".jpg") || lowerFileName.endsWith(".jpeg") ||
                lowerFileName.endsWith(".png") || lowerFileName.endsWith(".webp")) {
                
                subFolder = "ImageYo";
                mime = "image/jpeg";
                if (lowerFileName.endsWith(".png")) mime = "image/png";
                else if (lowerFileName.endsWith(".webp")) mime = "image/webp";
            } else if ("audio".equalsIgnoreCase(fileType) || lowerFileName.endsWith(".mp3") ||
                       lowerFileName.endsWith(".m4a") || lowerFileName.endsWith(".wav") || lowerFileName.endsWith(".flac")) {
                subFolder = "AudioYo";
                mime = "audio/mpeg";
                if (lowerFileName.endsWith(".m4a")) mime = "audio/mp4";
                else if (lowerFileName.endsWith(".wav")) mime = "audio/wav";
                else if (lowerFileName.endsWith(".flac")) mime = "audio/flac";
            } else if ("video".equalsIgnoreCase(fileType) || lowerFileName.endsWith(".mp4") ||
                       lowerFileName.endsWith(".webm") || lowerFileName.endsWith(".mov") || lowerFileName.endsWith(".mkv")) {
                subFolder = "VideoYo";
                mime = "video/mp4";
                if (lowerFileName.endsWith(".webm")) mime = "video/webm";
                else if (lowerFileName.endsWith(".mov")) mime = "video/quicktime";
            } else if ("apk".equalsIgnoreCase(fileType) || lowerFileName.endsWith(".apk")) {
                subFolder = "";
                mime = "application/vnd.android.package-archive";
            } else {
                subFolder = "ETC";
                mime = "application/octet-stream";
            }

            String overwriteMode = call.getString("overwriteMode", "rename");
            if ("skip".equalsIgnoreCase(overwriteMode)) {
                File existing = resolveMediaFile(subFolder, fileName);
                if (existing != null && existing.exists()) {
                    JSObject ret = new JSObject();
                    ret.put("success", true);
                    ret.put("skipped", true);
                    ret.put("fileName", existing.getName());
                    ret.put("filePath", existing.getAbsolutePath());
                    ret.put("uri", Uri.fromFile(existing).toString());
                    ret.put("subFolder", subFolder);
                    ret.put("size", existing.length());
                    call.resolve(ret);
                    return;
                }
            } else if ("overwrite".equalsIgnoreCase(overwriteMode)) {
                handleExistingFileForOverwrite(subFolder, fileName);
            }

            String actualFileName = fileName;
            String actualFilePath = null;
            Uri fileUri = null;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                if (subFolder == null || subFolder.trim().isEmpty()) {
                    values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/");
                } else {
                    values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Nimiyo/" + subFolder + "/");
                }
                values.put(MediaStore.MediaColumns.IS_PENDING, 1);

                Uri collectionUri;
                try {
                    collectionUri = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                } catch (Throwable t) {
                    collectionUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
                }

                fileUri = resolver.insert(collectionUri, values);
                if (fileUri == null) {
                    call.reject("Failed to create MediaStore entry");
                    return;
                }

                try (OutputStream os = resolver.openOutputStream(fileUri);
                     FileInputStream fis = new FileInputStream(sourceFile)) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = fis.read(buf)) > 0) {
                        os.write(buf, 0, len);
                    }
                    os.flush();
                }

                ContentValues finishValues = new ContentValues();
                finishValues.put(MediaStore.MediaColumns.IS_PENDING, 0);
                resolver.update(fileUri, finishValues, null, null);

                try (Cursor cursor = resolver.query(fileUri, new String[]{MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.DATA}, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int nameIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
                        if (nameIdx != -1) {
                            String dName = cursor.getString(nameIdx);
                            if (dName != null && !dName.isEmpty()) {
                                actualFileName = dName;
                            }
                        }
                        int dataIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA);
                        if (dataIdx != -1) {
                            actualFilePath = cursor.getString(dataIdx);
                        }
                    }
                } catch (Exception ignored) {}
            } else {
                File publicDir;
                if (subFolder == null || subFolder.trim().isEmpty()) {
                    publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                } else {
                    publicDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Nimiyo/" + subFolder);
                }
                if (!publicDir.exists()) {
                    publicDir.mkdirs();
                }

                File targetFile = new File(publicDir, fileName);
                if ("rename".equalsIgnoreCase(overwriteMode) && targetFile.exists()) {
                    String nameWithoutExt = fileName;
                    String ext = "";
                    int dot = fileName.lastIndexOf('.');
                    if (dot > 0) {
                        nameWithoutExt = fileName.substring(0, dot);
                        ext = fileName.substring(dot);
                    }
                    int counter = 1;
                    while (targetFile.exists()) {
                        targetFile = new File(publicDir, nameWithoutExt + " (" + counter + ")" + ext);
                        counter++;
                    }
                }

                try (FileOutputStream fos = new FileOutputStream(targetFile);
                     FileInputStream fis = new FileInputStream(sourceFile)) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = fis.read(buf)) > 0) {
                        fos.write(buf, 0, len);
                    }
                    fos.flush();
                }

                actualFileName = targetFile.getName();
                actualFilePath = targetFile.getAbsolutePath();
                fileUri = Uri.fromFile(targetFile);

                MediaScannerConnection.scanFile(
                    getContext(),
                    new String[]{actualFilePath},
                    new String[]{mime},
                    null
                );
            }

            JSObject ret = new JSObject();
            ret.put("success", true);
            ret.put("uri", fileUri != null ? fileUri.toString() : "");
            ret.put("fileName", actualFileName);
            if (actualFilePath != null) {
                ret.put("filePath", actualFilePath);
            }
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Failed to save media: " + e.getMessage());
        }
    }

    @PluginMethod
    public void openDirectory(PluginCall call) {
        String subFolder = call.getString("subFolder", "VideoYo");
        if (subFolder == null || subFolder.trim().isEmpty()) {
            subFolder = "VideoYo";
        }

        Context context = getContext();
        String relativePath = "Download/Nimiyo/" + subFolder;
        File targetDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Nimiyo/" + subFolder);
        
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }

        String dirPath = targetDir.getAbsolutePath();

        // 1. Xiaomi / MIUI / HyperOS File Explorer (com.mi.android.globalFileexplorer)
        try {
            Intent miIntent = new Intent();
            miIntent.setClassName("com.mi.android.globalFileexplorer", "com.android.fileexplorer.FileExplorerTabActivity");
            miIntent.putExtra("current_directory", dirPath);
            miIntent.putExtra("explorer_path", dirPath);
            miIntent.putExtra("path", dirPath);
            miIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(miIntent);
            call.resolve(new JSObject().put("success", true));
            return;
        } catch (Exception ignored) {}

        // 2. Xiaomi AOSP File Explorer (com.android.fileexplorer)
        try {
            Intent miAospIntent = new Intent();
            miAospIntent.setClassName("com.android.fileexplorer", "com.android.fileexplorer.FileExplorerTabActivity");
            miAospIntent.putExtra("current_directory", dirPath);
            miAospIntent.putExtra("explorer_path", dirPath);
            miAospIntent.putExtra("path", dirPath);
            miAospIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(miAospIntent);
            call.resolve(new JSObject().put("success", true));
            return;
        } catch (Exception ignored) {}

        // 3. Samsung My Files (com.sec.android.app.myfiles)
        try {
            Intent samIntent = new Intent("com.sec.android.app.myfiles.VIEW_FOLDER");
            samIntent.setPackage("com.sec.android.app.myfiles");
            samIntent.putExtra("folderPath", dirPath);
            samIntent.putExtra("current_path", dirPath);
            samIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(samIntent);
            call.resolve(new JSObject().put("success", true));
            return;
        } catch (Exception ignored) {}

        // 4. Google Files (com.google.android.apps.nbu.files)
        try {
            Uri folderUri = Uri.parse("content://com.android.externalstorage.documents/document/primary:Download%2FNimiyo%2F" + subFolder);
            Intent gfIntent = new Intent(Intent.ACTION_VIEW);
            gfIntent.setPackage("com.google.android.apps.nbu.files");
            gfIntent.setDataAndType(folderUri, "vnd.android.document/directory");
            gfIntent.putExtra("path", dirPath);
            gfIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(gfIntent);
            call.resolve(new JSObject().put("success", true));
            return;
        } catch (Exception ignored) {}

        // 5. File Manager + (com.alphainventor.filemanager)
        try {
            Intent alphaIntent = new Intent(Intent.ACTION_VIEW);
            alphaIntent.setPackage("com.alphainventor.filemanager");
            alphaIntent.setDataAndType(Uri.fromFile(targetDir), "resource/folder");
            alphaIntent.putExtra("path", dirPath);
            alphaIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(alphaIntent);
            call.resolve(new JSObject().put("success", true));
            return;
        } catch (Exception ignored) {}

        // 6. Generic DocumentsUI / SAF with direct primary document URI
        try {
            Uri folderUri = Uri.parse("content://com.android.externalstorage.documents/document/primary:Download%2FNimiyo%2F" + subFolder);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(folderUri, "vnd.android.document/directory");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, folderUri);
            intent.putExtra("path", dirPath);
            intent.putExtra("current_path", dirPath);

            context.startActivity(intent);
            call.resolve(new JSObject().put("success", true));
            return;
        } catch (Exception ignored) {}

        call.resolve(new JSObject().put("success", false).put("message", "Target: " + dirPath));
    }

    private File resolveMediaFile(String subFolder, String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) return null;

        String decodedName = fileName.trim();
        try {
            decodedName = java.net.URLDecoder.decode(decodedName, "UTF-8");
        } catch (Exception ignored) {}

        // Direct absolute path check
        if (fileName.startsWith("/") && new File(fileName).isFile()) {
            return new File(fileName);
        }
        if (decodedName.startsWith("/") && new File(decodedName).isFile()) {
            return new File(decodedName);
        }

        File publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        String safeSubFolder = (subFolder != null && !subFolder.trim().isEmpty()) ? subFolder.trim() : "VideoYo";

        // 1. Direct candidate paths (testing raw and decoded, with and without extensions)
        String[] nameVariants = new String[]{ fileName, decodedName };
        for (String nameVar : nameVariants) {
            File[] exactCandidates = new File[]{
                new File(publicDownloads, "Nimiyo/" + safeSubFolder + "/" + nameVar),
                new File(publicDownloads, "Nimiyo/" + nameVar),
                new File(publicDownloads, safeSubFolder + "/" + nameVar),
                new File(publicDownloads, nameVar),
                new File(publicDownloads, "Nimiyo/" + safeSubFolder + "/" + nameVar + ".mp4"),
                new File(publicDownloads, "Nimiyo/" + safeSubFolder + "/" + nameVar + ".mp3")
            };
            for (File f : exactCandidates) {
                if (f.exists() && f.isFile()) {
                    return f;
                }
            }
        }

        // 2. Directories to inspect for fuzzy matching
        File[] dirsToScan = new File[]{
            new File(publicDownloads, "Nimiyo/" + safeSubFolder),
            new File(publicDownloads, "Nimiyo/VideoYo"),
            new File(publicDownloads, "Nimiyo/AudioYo"),
            new File(publicDownloads, "Nimiyo/ImageYo"),
            new File(publicDownloads, "Nimiyo"),
            new File(publicDownloads, safeSubFolder),
            publicDownloads
        };

        // Separate stem and extension
        String ext = "";
        String stem = decodedName;
        int dotIdx = decodedName.lastIndexOf('.');
        if (dotIdx > 0) {
            ext = decodedName.substring(dotIdx);
            stem = decodedName.substring(0, dotIdx);
        }
        // Remove trailing " (1)", " (2)", "_1", "_2" from stem to get canonical base name
        String rootStem = stem.replaceAll("\\s*\\(\\d+\\)$", "").replaceAll("_\\d+$", "").trim();
        String cleanRoot = rootStem.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();

        for (File dir : dirsToScan) {
            if (dir.exists() && dir.isDirectory()) {
                File[] files = dir.listFiles();
                if (files != null && files.length > 0) {
                    File newestMatch = null;
                    long newestTime = -1;

                    for (File f : files) {
                        if (!f.isFile()) continue;
                        String fName = f.getName();

                        boolean matches = false;
                        if (fName.equalsIgnoreCase(fileName) || fName.equalsIgnoreCase(decodedName)) {
                            matches = true;
                        } else if (fName.startsWith(rootStem) && (ext.isEmpty() || fName.toLowerCase().endsWith(ext.toLowerCase()))) {
                            matches = true;
                        } else if (!cleanRoot.isEmpty()) {
                            String cleanName = fName.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
                            if ((cleanName.contains(cleanRoot) || cleanRoot.contains(cleanName)) &&
                                (ext.isEmpty() || fName.toLowerCase().endsWith(ext.toLowerCase()) || cleanRoot.length() > 6)) {
                                matches = true;
                            }
                        }

                        if (matches) {
                            if (f.lastModified() > newestTime) {
                                newestTime = f.lastModified();
                                newestMatch = f;
                            }
                        }
                    }

                    if (newestMatch != null) {
                        return newestMatch;
                    }
                }
            }
        }

        return null;
    }

    private void handleExistingFileForOverwrite(String subFolder, String fileName) {
        try {
            File existing = resolveMediaFile(subFolder, fileName);
            if (existing != null && existing.exists()) {
                existing.delete();
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentResolver resolver = getContext().getContentResolver();
                Uri queryUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
                String selection = MediaStore.MediaColumns.DISPLAY_NAME + " = ? AND " +
                                   MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?";
                String[] args = new String[]{fileName, "%Nimiyo/" + subFolder + "%"};
                resolver.delete(queryUri, selection, args);
            }
        } catch (Exception ignored) {}
    }

    @PluginMethod
    public void playVideoInApp(PluginCall call) {
        String fileName = call.getString("fileName");
        String subFolder = call.getString("subFolder", "VideoYo");
        String title = call.getString("title", "");

        if (fileName == null) {
            call.reject("fileName is required");
            return;
        }

        try {
            Context context = getContext();
            File mediaFile = resolveMediaFile(subFolder, fileName);

            if (mediaFile == null || !mediaFile.exists()) {
                call.reject("Media file not found on storage: " + fileName);
                return;
            }

            Uri contentUri = null;
            try {
                contentUri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", mediaFile);
            } catch (Exception ignored) {}

            Intent intent = new Intent(context, VideoPlayerActivity.class);
            intent.putExtra(VideoPlayerActivity.EXTRA_FILE_PATH, mediaFile.getAbsolutePath());
            if (contentUri != null) {
                intent.putExtra(VideoPlayerActivity.EXTRA_URI, contentUri.toString());
                intent.setDataAndType(contentUri, "video/*");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
            if (title != null && !title.trim().isEmpty()) {
                intent.putExtra(VideoPlayerActivity.EXTRA_TITLE, title);
            } else {
                intent.putExtra(VideoPlayerActivity.EXTRA_TITLE, mediaFile.getName());
            }
            boolean autoPlay = call.getBoolean("autoPlay", true);
            boolean autoLoop = call.getBoolean("autoLoop", true);
            intent.putExtra(VideoPlayerActivity.EXTRA_AUTO_PLAY, autoPlay);
            intent.putExtra(VideoPlayerActivity.EXTRA_AUTO_LOOP, autoLoop);

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);

            JSObject ret = new JSObject();
            ret.put("success", true);
            ret.put("fileName", mediaFile.getName());
            ret.put("filePath", mediaFile.getAbsolutePath());
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Failed to launch video player: " + e.getMessage());
        }
    }

    @PluginMethod
    public void setKeepAwake(PluginCall call) {
        boolean enabled = call.getBoolean("enabled", false);
        getActivity().runOnUiThread(() -> {
            try {
                if (enabled) {
                    getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                } else {
                    getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
                call.resolve();
            } catch (Exception e) {
                call.reject(e.getMessage());
            }
        });
    }

    @PluginMethod
    public void previewImageInApp(PluginCall call) {
        String fileName = call.getString("fileName");
        String subFolder = call.getString("subFolder", "ImageYo");
        String title = call.getString("title", "");

        if (fileName == null) {
            call.reject("fileName is required");
            return;
        }

        try {
            Context context = getContext();
            File mediaFile = resolveMediaFile(subFolder, fileName);

            if (mediaFile == null || !mediaFile.exists()) {
                call.reject("Media file not found on storage: " + fileName);
                return;
            }

            Intent intent = new Intent(context, ImageViewerActivity.class);
            intent.putExtra(ImageViewerActivity.EXTRA_FILE_PATH, mediaFile.getAbsolutePath());
            if (title != null && !title.trim().isEmpty()) {
                intent.putExtra(ImageViewerActivity.EXTRA_TITLE, title);
            } else {
                intent.putExtra(ImageViewerActivity.EXTRA_TITLE, mediaFile.getName());
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);

            JSObject ret = new JSObject();
            ret.put("success", true);
            ret.put("fileName", mediaFile.getName());
            ret.put("filePath", mediaFile.getAbsolutePath());
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Failed to launch photo preview: " + e.getMessage());
        }
    }

    @PluginMethod
    public void getVideoThumbnail(PluginCall call) {
        String fileName = call.getString("fileName");
        String subFolder = call.getString("subFolder", "VideoYo");

        if (fileName == null) {
            call.reject("fileName is required");
            return;
        }

        Context context = getContext();
        File thumbDir = new File(context.getCacheDir(), "thumbnails");
        if (!thumbDir.exists()) {
            thumbDir.mkdirs();
        }
        String safeName = "thumb_" + Math.abs(fileName.hashCode()) + ".jpg";
        File cachedThumb = new File(thumbDir, safeName);
        if (cachedThumb.exists() && cachedThumb.length() > 0) {
            JSObject ret = new JSObject();
            ret.put("success", true);
            ret.put("filePath", cachedThumb.getAbsolutePath());
            call.resolve(ret);
            return;
        }

        File videoFile = resolveMediaFile(subFolder, fileName);
        if (videoFile == null || !videoFile.exists()) {
            call.reject("Video file not found: " + fileName);
            return;
        }

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(videoFile.getAbsolutePath());
            Bitmap frame = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (frame == null) {
                frame = retriever.getFrameAtTime();
            }

            if (frame == null) {
                call.reject("Could not extract frame from video");
                return;
            }

            int maxDim = 320;
            int width = frame.getWidth();
            int height = frame.getHeight();
            Bitmap scaled = frame;
            if (width > maxDim || height > maxDim) {
                float ratio = Math.min((float) maxDim / width, (float) maxDim / height);
                int newWidth = Math.max(1, Math.round(width * ratio));
                int newHeight = Math.max(1, Math.round(height * ratio));
                scaled = Bitmap.createScaledBitmap(frame, newWidth, newHeight, true);
            }

            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(cachedThumb)) {
                scaled.compress(Bitmap.CompressFormat.JPEG, 80, fos);
                fos.flush();
            }

            if (scaled != frame && !frame.isRecycled()) {
                frame.recycle();
            }
            if (!scaled.isRecycled()) {
                scaled.recycle();
            }

            JSObject ret = new JSObject();
            ret.put("success", true);
            ret.put("filePath", cachedThumb.getAbsolutePath());
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Failed to generate video thumbnail: " + e.getMessage());
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {}
        }
    }

    @PluginMethod
    public void openMediaFile(PluginCall call) {
        String fileName = call.getString("fileName");
        String subFolder = call.getString("subFolder", "VideoYo");
        String fileType = call.getString("fileType", "video");
        String title = call.getString("title", "");
        boolean forceExternal = call.getBoolean("forceExternal", false);

        if (fileName == null) {
            call.reject("fileName is required");
            return;
        }

        try {
            Context context = getContext();
            File mediaFile = resolveMediaFile(subFolder, fileName);

            if (mediaFile == null || !mediaFile.exists()) {
                call.reject("Media file not found on storage: " + fileName);
                return;
            }

            boolean isVideo = "video".equalsIgnoreCase(fileType) ||
                fileName.endsWith(".mp4") || fileName.endsWith(".webm") ||
                fileName.endsWith(".mov") || fileName.endsWith(".mkv");

            // Open in dedicated in-app player if it's video and not forced to external
            if (isVideo && !forceExternal) {
                Intent playerIntent = new Intent(context, VideoPlayerActivity.class);
                playerIntent.putExtra(VideoPlayerActivity.EXTRA_FILE_PATH, mediaFile.getAbsolutePath());
                if (title != null && !title.trim().isEmpty()) {
                    playerIntent.putExtra(VideoPlayerActivity.EXTRA_TITLE, title);
                } else {
                    playerIntent.putExtra(VideoPlayerActivity.EXTRA_TITLE, mediaFile.getName());
                }
                playerIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(playerIntent);

                JSObject ret = new JSObject();
                ret.put("success", true);
                ret.put("fileName", mediaFile.getName());
                ret.put("filePath", mediaFile.getAbsolutePath());
                call.resolve(ret);
                return;
            }

            boolean isImage = "image".equalsIgnoreCase(fileType) ||
                fileName.endsWith(".png") || fileName.endsWith(".jpg") ||
                fileName.endsWith(".jpeg") || fileName.endsWith(".webp");

            // Open in dedicated in-app photo viewer if it's image and not forced to external
            if (isImage && !forceExternal) {
                Intent imageIntent = new Intent(context, ImageViewerActivity.class);
                imageIntent.putExtra(ImageViewerActivity.EXTRA_FILE_PATH, mediaFile.getAbsolutePath());
                if (title != null && !title.trim().isEmpty()) {
                    imageIntent.putExtra(ImageViewerActivity.EXTRA_TITLE, title);
                } else {
                    imageIntent.putExtra(ImageViewerActivity.EXTRA_TITLE, mediaFile.getName());
                }
                imageIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(imageIntent);

                JSObject ret = new JSObject();
                ret.put("success", true);
                ret.put("fileName", mediaFile.getName());
                ret.put("filePath", mediaFile.getAbsolutePath());
                call.resolve(ret);
                return;
            }

            Uri contentUri = FileProvider.getUriForFile(
                context,
                context.getPackageName() + ".fileprovider",
                mediaFile
            );

            String mime = "video/*";
            if ("audio".equalsIgnoreCase(fileType) || fileName.endsWith(".mp3") || fileName.endsWith(".m4a") || fileName.endsWith(".wav")) {
                mime = "audio/*";
            } else if ("image".equalsIgnoreCase(fileType) || fileName.endsWith(".png") || fileName.endsWith(".jpg") || fileName.endsWith(".webp")) {
                mime = "image/*";
            }

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(contentUri, mime);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);

            Intent chooser = Intent.createChooser(intent, "Play " + mediaFile.getName());
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(chooser);

            JSObject ret = new JSObject();
            ret.put("success", true);
            ret.put("fileName", mediaFile.getName());
            ret.put("filePath", mediaFile.getAbsolutePath());
            ret.put("uri", contentUri.toString());
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Failed to open media file: " + e.getMessage());
        }
    }

    @PluginMethod
    public void getMediaData(PluginCall call) {
        String fileName = call.getString("fileName");
        String subFolder = call.getString("subFolder", "VideoYo");
        String fileType = call.getString("fileType", "video");

        if (fileName == null) {
            call.reject("fileName is required");
            return;
        }

        try {
            Context context = getContext();
            File mediaFile = resolveMediaFile(subFolder, fileName);

            if (mediaFile == null || !mediaFile.exists()) {
                call.reject("File not found on storage: " + fileName);
                return;
            }

            Uri contentUri = FileProvider.getUriForFile(
                context,
                context.getPackageName() + ".fileprovider",
                mediaFile
            );

            JSObject ret = new JSObject();
            ret.put("success", true);
            ret.put("fileName", mediaFile.getName());
            ret.put("filePath", mediaFile.getAbsolutePath());
            ret.put("fileUri", Uri.fromFile(mediaFile).toString());
            ret.put("uri", contentUri.toString());
            ret.put("length", mediaFile.length());

            if ("image".equalsIgnoreCase(fileType) && mediaFile.length() <= 2 * 1024 * 1024) {
                byte[] bytes = new byte[(int) mediaFile.length()];
                try (FileInputStream fis = new FileInputStream(mediaFile)) {
                    fis.read(bytes);
                }
                String base64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
                ret.put("base64", base64);
            }

            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Failed to get media data: " + e.getMessage());
        }
    }

    @PluginMethod
    public void checkInstallPermission(PluginCall call) {
        JSObject ret = new JSObject();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            boolean granted = getContext().getPackageManager().canRequestPackageInstalls();
            ret.put("isGranted", granted);
        } else {
            ret.put("isGranted", true);
        }
        call.resolve(ret);
    }

    @PluginMethod
    public void requestInstallPermission(PluginCall call) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                intent.setData(Uri.parse("package:" + getContext().getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
            } catch (Exception e) {
                Intent fallbackIntent = new Intent(Settings.ACTION_SECURITY_SETTINGS);
                fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(fallbackIntent);
            }
        }
        JSObject ret = new JSObject();
        ret.put("success", true);
        call.resolve(ret);
    }

    @PluginMethod
    public void downloadUpdateApk(PluginCall call) {
        String url = call.getString("url");
        if (url == null || url.isEmpty()) {
            call.reject("url is required");
            return;
        }

        downloadExecutor.execute(() -> {
            acquireDownloadWakeLock();
            HttpURLConnection conn = null;
            InputStream is = null;
            FileOutputStream fos = null;
            try {
                Context context = getContext();
                File cacheDir = context.getExternalCacheDir() != null ? context.getExternalCacheDir() : context.getCacheDir();
                File apkFile = new File(cacheDir, "nimiyo_update.apk");
                if (apkFile.exists()) {
                    apkFile.delete();
                }

                conn = connectWithRedirects(url, "GET", null);
                int responseCode = conn.getResponseCode();
                if (responseCode >= 400) {
                    throw new Exception("Server returned HTTP " + responseCode);
                }

                long contentLength = conn.getContentLengthLong();

                is = conn.getInputStream();
                fos = new FileOutputStream(apkFile);
                byte[] buffer = new byte[8192];
                int len;
                long total = 0;
                long lastNotify = 0;

                while ((len = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, len);
                    total += len;
                    long now = System.currentTimeMillis();
                    if (now - lastNotify > 150) {
                        lastNotify = now;
                        int percent = contentLength > 0 ? (int) Math.min((total * 100) / contentLength, 99) : 50;
                        JSObject progressData = new JSObject();
                        progressData.put("progress", percent);
                        progressData.put("bytesDownloaded", total);
                        progressData.put("totalBytes", contentLength);
                        notifyListeners("onUpdateDownloadProgress", progressData);
                    }
                }
                fos.flush();
                try { fos.close(); } catch (Exception ignored) {}
                fos = null;

                apkFile.setReadable(true, false);

                JSObject ret = new JSObject();
                ret.put("success", true);
                ret.put("filePath", apkFile.getAbsolutePath());
                call.resolve(ret);
            } catch (Exception e) {
                call.reject("Download update failed: " + e.getMessage());
            } finally {
                if (fos != null) { try { fos.close(); } catch (Exception ignored) {} }
                if (is != null) { try { is.close(); } catch (Exception ignored) {} }
                if (conn != null) { try { conn.disconnect(); } catch (Exception ignored) {} }
                releaseDownloadWakeLock();
            }
        });
    }

    @PluginMethod
    public void installApk(PluginCall call) {
        String filePath = call.getString("filePath");
        if (filePath == null || filePath.isEmpty()) {
            call.reject("filePath is required");
            return;
        }

        try {
            Context context = getContext();

            // Auto-check unknown app installation permission on Android 8.0+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.getPackageManager().canRequestPackageInstalls()) {
                    try {
                        Intent manageIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                        manageIntent.setData(Uri.parse("package:" + context.getPackageName()));
                        manageIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(manageIntent);
                    } catch (Exception e) {
                        Intent fallbackIntent = new Intent(Settings.ACTION_SECURITY_SETTINGS);
                        fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(fallbackIntent);
                    }
                    JSObject ret = new JSObject();
                    ret.put("success", false);
                    ret.put("permissionRequired", true);
                    call.resolve(ret);
                    return;
                }
            }

            File apkFile;
            if (filePath.startsWith("content://") || filePath.startsWith("file://")) {
                Uri parsed = Uri.parse(filePath);
                apkFile = new File(parsed.getPath());
            } else {
                apkFile = new File(filePath);
            }

            if (!apkFile.exists()) {
                File inCache = new File(context.getCacheDir(), filePath);
                if (inCache.exists()) {
                    apkFile = inCache;
                } else {
                    File inExtCache = context.getExternalCacheDir() != null ? new File(context.getExternalCacheDir(), filePath) : null;
                    if (inExtCache != null && inExtCache.exists()) {
                        apkFile = inExtCache;
                    } else {
                        File inExternal = new File(context.getExternalFilesDir(null), filePath);
                        if (inExternal.exists()) {
                            apkFile = inExternal;
                        } else {
                            File inDownloads = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), filePath);
                            if (inDownloads.exists()) {
                                apkFile = inDownloads;
                            }
                        }
                    }
                }
            }

            if (!apkFile.exists()) {
                call.reject("APK file does not exist: " + filePath);
                return;
            }

            apkFile.setReadable(true, false);

            Uri apkUri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                apkUri = FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".fileprovider",
                    apkFile
                );
            } else {
                apkUri = Uri.fromFile(apkFile);
            }

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            List<ResolveInfo> resInfoList = context.getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
            if (resInfoList != null) {
                for (ResolveInfo resolveInfo : resInfoList) {
                    if (resolveInfo.activityInfo != null && resolveInfo.activityInfo.packageName != null) {
                        context.grantUriPermission(resolveInfo.activityInfo.packageName, apkUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    }
                }
            }

            context.startActivity(intent);

            JSObject ret = new JSObject();
            ret.put("success", true);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Failed to trigger APK install: " + e.getMessage());
        }
    }

    @PluginMethod
    public void readClipboard(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            try {
                ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null && clipboard.hasPrimaryClip()) {
                    ClipData clip = clipboard.getPrimaryClip();
                    if (clip != null && clip.getItemCount() > 0) {
                        CharSequence text = clip.getItemAt(0).coerceToText(getContext());
                        JSObject ret = new JSObject();
                        ret.put("value", text != null ? text.toString() : "");
                        call.resolve(ret);
                        return;
                    }
                }
                JSObject ret = new JSObject();
                ret.put("value", "");
                call.resolve(ret);
            } catch (Exception e) {
                call.reject("Failed to read clipboard: " + e.getMessage());
            }
        });
    }

    @PluginMethod
    public void setClipboard(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            try {
                String text = call.getString("value", "");
                ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    ClipData clip = ClipData.newPlainText("text", text != null ? text : "");
                    clipboard.setPrimaryClip(clip);
                }
                JSObject ret = new JSObject();
                ret.put("success", true);
                call.resolve(ret);
            } catch (Exception e) {
                call.reject("Failed to set clipboard: " + e.getMessage());
            }
        });
    }

    @PluginMethod
    public void getAudioPermissionStatus(PluginCall call) {
        Context context = getContext();
        boolean granted;
        if (Build.VERSION.SDK_INT >= 33) {
            granted = context.checkSelfPermission(android.Manifest.permission.READ_MEDIA_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED;
        } else {
            granted = context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED;
        }
        JSObject ret = new JSObject();
        ret.put("granted", granted);
        ret.put("status", granted ? "granted" : "denied");
        call.resolve(ret);
    }

    @PluginMethod
    public void checkAndRequestAudioPermission(PluginCall call) {
        if (getActivity() == null) {
            call.resolve(new JSObject().put("granted", false).put("status", "denied"));
            return;
        }
        if (Build.VERSION.SDK_INT >= 33) {
            if (getActivity().checkSelfPermission(android.Manifest.permission.READ_MEDIA_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                call.resolve(new JSObject().put("granted", true).put("status", "granted"));
            } else {
                getActivity().runOnUiThread(() -> {
                    getActivity().requestPermissions(new String[]{android.Manifest.permission.READ_MEDIA_AUDIO}, 102);
                });
                call.resolve(new JSObject().put("granted", false).put("status", "requested"));
            }
        } else {
            if (getActivity().checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                call.resolve(new JSObject().put("granted", true).put("status", "granted"));
            } else {
                getActivity().runOnUiThread(() -> {
                    getActivity().requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, 102);
                });
                call.resolve(new JSObject().put("granted", false).put("status", "requested"));
            }
        }
    }

    @PluginMethod
    public void openAppSettings(PluginCall call) {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getContext().getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
            call.resolve(new JSObject().put("success", true));
        } catch (Exception e) {
            call.reject("Failed to open app settings: " + e.getMessage());
        }
    }

    @PluginMethod
    public void checkManageStoragePermission(PluginCall call) {
        boolean granted = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            granted = Environment.isExternalStorageManager();
        }
        JSObject ret = new JSObject();
        ret.put("granted", granted);
        call.resolve(ret);
    }

    @PluginMethod
    public void requestManageStoragePermission(PluginCall call) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getContext().getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
                call.resolve(new JSObject().put("success", true));
                return;
            } catch (Exception e) {
                try {
                    Intent fallback = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    getContext().startActivity(fallback);
                    call.resolve(new JSObject().put("success", true));
                    return;
                } catch (Exception e2) {
                    call.reject("Failed to open storage settings: " + e2.getMessage());
                    return;
                }
            }
        }
        call.resolve(new JSObject().put("success", true));
    }

    @PluginMethod
    public void scanAudioLibrary(PluginCall call) {
        downloadExecutor.execute(() -> {
            try {
                Context context = getContext();
                ContentResolver resolver = context.getContentResolver();
                JSArray trackList = new JSArray();
                HashSet<String> seenPaths = new HashSet<>();

                // 1. Direct scan NIMIYO downloads directories (Download/Nimiyo/AudioYo, Download/Nimiyo/audio, Download/Nimiyo)
                File[] nimiyoDirs = new File[] {
                    new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Nimiyo/AudioYo"),
                    new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Nimiyo/audio"),
                    new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Nimiyo")
                };

                for (File dir : nimiyoDirs) {
                    if (dir.exists() && dir.isDirectory()) {
                        File[] files = dir.listFiles();
                        if (files != null) {
                            for (File f : files) {
                                if (f.isFile() && isSupportedAudioFile(f.getName())) {
                                    String absPath = f.getAbsolutePath();
                                    if (seenPaths.add(absPath.toLowerCase())) {
                                        JSObject trk = extractFileMetadata(f, true);
                                        if (trk != null) trackList.put(trk);
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Query MediaStore for system-wide audio files
                String[] projection = new String[] {
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.DATA,
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.TRACK,
                    MediaStore.Audio.Media.YEAR,
                    MediaStore.Audio.Media.MIME_TYPE,
                    MediaStore.Audio.Media.SIZE
                };

                String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0 OR " +
                                   MediaStore.Audio.Media.DATA + " LIKE '%.mp3' OR " +
                                   MediaStore.Audio.Media.DATA + " LIKE '%.m4a' OR " +
                                   MediaStore.Audio.Media.DATA + " LIKE '%.flac' OR " +
                                   MediaStore.Audio.Media.DATA + " LIKE '%.wav' OR " +
                                   MediaStore.Audio.Media.DATA + " LIKE '%.ogg' OR " +
                                   MediaStore.Audio.Media.DATA + " LIKE '%.opus' OR " +
                                   MediaStore.Audio.Media.DATA + " LIKE '%.aac' OR " +
                                   MediaStore.Audio.Media.DATA + " LIKE '%.wma'";

                try (android.database.Cursor cursor = resolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    null,
                    MediaStore.Audio.Media.TITLE + " ASC"
                )) {
                    if (cursor != null) {
                        int idIdx = cursor.getColumnIndex(MediaStore.Audio.Media._ID);
                        int titleIdx = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
                        int artistIdx = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST);
                        int albumIdx = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM);
                        int durIdx = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION);
                        int dataIdx = cursor.getColumnIndex(MediaStore.Audio.Media.DATA);
                        int nameIdx = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME);
                        int trackIdx = cursor.getColumnIndex(MediaStore.Audio.Media.TRACK);
                        int yearIdx = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR);
                        int mimeIdx = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE);
                        int sizeIdx = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE);

                        while (cursor.moveToNext()) {
                            long id = idIdx >= 0 ? cursor.getLong(idIdx) : 0;
                            String data = dataIdx >= 0 ? cursor.getString(dataIdx) : "";
                            if (data == null) data = "";

                            if (!data.isEmpty() && !seenPaths.add(data.toLowerCase())) {
                                continue;
                            }

                            String title = titleIdx >= 0 ? cursor.getString(titleIdx) : "";
                            String artist = artistIdx >= 0 ? cursor.getString(artistIdx) : "";
                            String album = albumIdx >= 0 ? cursor.getString(albumIdx) : "";
                            long duration = durIdx >= 0 ? cursor.getLong(durIdx) : 0;
                            String displayName = nameIdx >= 0 ? cursor.getString(nameIdx) : "";
                            int trackNum = trackIdx >= 0 ? cursor.getInt(trackIdx) : 0;
                            int year = yearIdx >= 0 ? cursor.getInt(yearIdx) : 0;
                            String mime = mimeIdx >= 0 ? cursor.getString(mimeIdx) : "audio/mpeg";
                            long size = sizeIdx >= 0 ? cursor.getLong(sizeIdx) : 0;

                            if (displayName == null || displayName.isEmpty()) {
                                displayName = new File(data).getName();
                            }
                            if (title == null || title.isEmpty() || title.equals("<unknown>")) {
                                title = displayName.replaceFirst("[.][^.]+$", "");
                            }
                            if (artist == null || artist.equals("<unknown>")) artist = "";
                            if (album == null || album.equals("<unknown>")) album = "";

                            boolean isNimiyo = data.toLowerCase().contains("nimiyo");
                            Uri contentUri = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, String.valueOf(id));

                            // Check companion LRC file
                            boolean hasLyrics = false;
                            if (!data.isEmpty()) {
                                String lrcPath = data.replaceFirst("[.][^.]+$", ".lrc");
                                if (new File(lrcPath).exists()) {
                                    hasLyrics = true;
                                }
                            }

                            JSObject trk = new JSObject();
                            trk.put("id", "ms_" + id);
                            trk.put("title", title);
                            trk.put("artist", artist);
                            trk.put("album", album);
                            trk.put("albumArtist", artist);
                            trk.put("duration", duration);
                            trk.put("trackNumber", trackNum);
                            trk.put("year", year);
                            trk.put("filePath", data);
                            trk.put("contentUri", contentUri.toString());
                            trk.put("fileName", displayName);
                            trk.put("mimeType", mime);
                            trk.put("size", size);
                            trk.put("isNimiyo", isNimiyo);
                            trk.put("hasArtwork", true);
                            trk.put("hasLyrics", hasLyrics);

                            trackList.put(trk);
                        }
                    }
                }

                JSObject ret = new JSObject();
                ret.put("tracks", trackList);
                call.resolve(ret);
            } catch (Exception e) {
                call.reject("Failed to scan audio library: " + e.getMessage());
            }
        });
    }

    private boolean isSupportedAudioFile(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase();
        return lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".aac") ||
               lower.endsWith(".flac") || lower.endsWith(".wav") || lower.endsWith(".ogg") ||
               lower.endsWith(".opus") || lower.endsWith(".wma");
    }

    private JSObject extractFileMetadata(File f, boolean isNimiyo) {
        try {
            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            mmr.setDataSource(f.getAbsolutePath());

            String title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            String artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            String album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
            String albumArtist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST);
            String durStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            String trackStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER);
            String yearStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR);
            String mime = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE);

            long duration = 0;
            if (durStr != null) {
                try { duration = Long.parseLong(durStr); } catch (Exception ignored) {}
            }
            int trackNum = 0;
            if (trackStr != null) {
                try {
                    String clean = trackStr.split("/")[0];
                    trackNum = Integer.parseInt(clean.trim());
                } catch (Exception ignored) {}
            }
            int year = 0;
            if (yearStr != null) {
                try { year = Integer.parseInt(yearStr.trim()); } catch (Exception ignored) {}
            }

            if (title == null || title.isEmpty()) {
                title = f.getName().replaceFirst("[.][^.]+$", "");
            }
            if (artist == null) artist = "";
            if (album == null) album = "";
            if (albumArtist == null || albumArtist.isEmpty()) albumArtist = artist;
            if (mime == null) mime = "audio/mpeg";

            boolean hasLyrics = false;
            String lrcPath = f.getAbsolutePath().replaceFirst("[.][^.]+$", ".lrc");
            if (new File(lrcPath).exists()) {
                hasLyrics = true;
            }

            byte[] pic = mmr.getEmbeddedPicture();
            boolean hasArtwork = (pic != null && pic.length > 0);

            try { mmr.release(); } catch (Exception ignored) {}

            JSObject trk = new JSObject();
            trk.put("id", "file_" + Math.abs(f.getAbsolutePath().hashCode()));
            trk.put("title", title);
            trk.put("artist", artist);
            trk.put("album", album);
            trk.put("albumArtist", albumArtist);
            trk.put("duration", duration);
            trk.put("trackNumber", trackNum);
            trk.put("year", year);
            Uri itemContentUri = Uri.fromFile(f);
            try {
                Context ctx = getContext();
                if (ctx != null) {
                    ContentResolver cr = ctx.getContentResolver();
                    try (Cursor c = cr.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            new String[]{MediaStore.Audio.Media._ID},
                            MediaStore.Audio.Media.DATA + " = ?", new String[]{f.getAbsolutePath()}, null)) {
                        if (c != null && c.moveToFirst()) {
                            long rowId = c.getLong(0);
                            itemContentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, rowId);
                        }
                    }
                    if (itemContentUri.toString().startsWith("file://") && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        try (Cursor c = cr.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                                new String[]{MediaStore.MediaColumns._ID},
                                MediaStore.MediaColumns.DATA + " = ?", new String[]{f.getAbsolutePath()}, null)) {
                            if (c != null && c.moveToFirst()) {
                                long rowId = c.getLong(0);
                                itemContentUri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, rowId);
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}

            trk.put("filePath", f.getAbsolutePath());
            trk.put("contentUri", itemContentUri.toString());
            trk.put("fileName", f.getName());
            trk.put("mimeType", mime);
            trk.put("size", f.length());
            trk.put("isNimiyo", isNimiyo);
            trk.put("hasArtwork", hasArtwork);
            trk.put("hasLyrics", hasLyrics);

            return trk;
        } catch (Exception e) {
            JSObject trk = new JSObject();
            trk.put("id", "file_" + Math.abs(f.getAbsolutePath().hashCode()));
            trk.put("title", f.getName().replaceFirst("[.][^.]+$", ""));
            trk.put("artist", "");
            trk.put("album", "");
            trk.put("albumArtist", "");
            trk.put("duration", 0);
            trk.put("trackNumber", 0);
            trk.put("year", 0);
            trk.put("filePath", f.getAbsolutePath());
            trk.put("contentUri", Uri.fromFile(f).toString());
            trk.put("fileName", f.getName());
            trk.put("mimeType", "audio/mpeg");
            trk.put("size", f.length());
            trk.put("isNimiyo", isNimiyo);
            trk.put("hasArtwork", false);
            trk.put("hasLyrics", false);
            return trk;
        }
    }

    @PluginMethod
    public void getAudioArtwork(PluginCall call) {
        String filePath = call.getString("filePath");
        String contentUriStr = call.getString("contentUri");

        downloadExecutor.execute(() -> {
            MediaMetadataRetriever mmr = null;
            try {
                byte[] picture = null;
                if (filePath != null && !filePath.isEmpty()) {
                    File file = new File(filePath);
                    if (file.exists()) {
                        mmr = new MediaMetadataRetriever();
                        mmr.setDataSource(file.getAbsolutePath());
                        picture = mmr.getEmbeddedPicture();
                    }
                }

                if (picture == null && contentUriStr != null && !contentUriStr.isEmpty()) {
                    try {
                        Uri uri = Uri.parse(contentUriStr);
                        if (mmr == null) mmr = new MediaMetadataRetriever();
                        mmr.setDataSource(getContext(), uri);
                        picture = mmr.getEmbeddedPicture();
                    } catch (Exception ignored) {}
                }

                if (picture != null && picture.length > 0) {
                    Bitmap bitmap = BitmapFactory.decodeByteArray(picture, 0, picture.length);
                    if (bitmap != null) {
                        int maxDim = 512;
                        if (bitmap.getWidth() > maxDim || bitmap.getHeight() > maxDim) {
                            float scale = Math.min((float) maxDim / bitmap.getWidth(), (float) maxDim / bitmap.getHeight());
                            int targetW = Math.round(bitmap.getWidth() * scale);
                            int targetH = Math.round(bitmap.getHeight() * scale);
                            Bitmap scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true);
                            if (scaled != bitmap) bitmap.recycle();
                            bitmap = scaled;
                        }
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos);
                        byte[] compressed = baos.toByteArray();
                        bitmap.recycle();
                        String base64 = Base64.encodeToString(compressed, Base64.NO_WRAP);
                        JSObject ret = new JSObject();
                        ret.put("hasArtwork", true);
                        ret.put("artwork", "data:image/jpeg;base64," + base64);
                        call.resolve(ret);
                        return;
                    }
                }

                call.resolve(new JSObject().put("hasArtwork", false));
            } catch (Exception e) {
                call.resolve(new JSObject().put("hasArtwork", false));
            } finally {
                if (mmr != null) {
                    try { mmr.release(); } catch (Exception ignored) {}
                }
            }
        });
    }

    private File getLyricsYoDir() {
        File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File dir = new File(downloads, "Nimiyo/LyricsYo");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    private File getFallbackLyricsYoDir() {
        File ext = Environment.getExternalStorageDirectory();
        File dir = new File(ext, "Nimiyo/LyricsYo");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    @PluginMethod
    public void getAudioLyrics(PluginCall call) {
        String filePath = call.getString("filePath");
        String trackTitle = call.getString("trackTitle");
        String trackArtist = call.getString("trackArtist");
        downloadExecutor.execute(() -> {
            try {
                String lyrics = null;

                // 1. Check Nimiyo/LyricsYo directory first
                File dir1 = getLyricsYoDir();
                File dir2 = getFallbackLyricsYoDir();

                List<String> names = new ArrayList<>();
                if (filePath != null && !filePath.isEmpty()) {
                    File af = new File(filePath);
                    names.add(af.getName().replaceFirst("[.][^.]+$", ""));
                }
                if (trackTitle != null && !trackTitle.isEmpty()) {
                    String cleanT = trackTitle.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
                    names.add(cleanT);
                    if (trackArtist != null && !trackArtist.isEmpty() && !trackArtist.equalsIgnoreCase("Unknown Artist")) {
                        String cleanA = trackArtist.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
                        names.add(cleanT + " - " + cleanA);
                        names.add(cleanA + " - " + cleanT);
                    }
                }

                for (String nm : names) {
                    if (nm.isEmpty()) continue;
                    File f1 = new File(dir1, nm + ".lrc");
                    if (f1.exists() && f1.isFile()) {
                        lyrics = readTextFile(f1);
                        if (lyrics != null && !lyrics.trim().isEmpty()) break;
                    }
                    File f2 = new File(dir2, nm + ".lrc");
                    if (f2.exists() && f2.isFile()) {
                        lyrics = readTextFile(f2);
                        if (lyrics != null && !lyrics.trim().isEmpty()) break;
                    }
                }

                if (lyrics == null && filePath != null && !filePath.isEmpty()) {
                    File audioFile = new File(filePath);
                    if (audioFile.exists() && audioFile.isFile()) {
                        File parent = audioFile.getParentFile();
                        String fileName = audioFile.getName();
                        String baseName = fileName.replaceFirst("[.][^.]+$", "");

                        // 1. Companion .lrc in same directory (case-insensitive search)
                        File directLrc = new File(parent, baseName + ".lrc");
                        if (directLrc.exists() && directLrc.isFile()) {
                            lyrics = readTextFile(directLrc);
                        }

                        if (lyrics == null && parent != null) {
                            File directTxt = new File(parent, baseName + ".txt");
                            if (directTxt.exists() && directTxt.isFile()) {
                                lyrics = readTextFile(directTxt);
                            }
                        }

                        // Also check case-insensitive match in parent directory
                        if (lyrics == null && parent != null && parent.isDirectory()) {
                            File[] siblings = parent.listFiles();
                            if (siblings != null) {
                                for (File sibling : siblings) {
                                    String sName = sibling.getName();
                                    if (sName.toLowerCase().endsWith(".lrc")) {
                                        String sBase = sName.substring(0, sName.length() - 4);
                                        if (sBase.equalsIgnoreCase(baseName) || sBase.equalsIgnoreCase(fileName)) {
                                            lyrics = readTextFile(sibling);
                                            if (lyrics != null && !lyrics.trim().isEmpty()) break;
                                        }
                                    }
                                }
                            }
                        }

                        // 2. Check Nimiyo AudioYo download folder for companion lyrics
                        if (lyrics == null) {
                            File nimiyoAudioDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Nimiyo/AudioYo");
                            if (nimiyoAudioDir.exists() && nimiyoAudioDir.isDirectory()) {
                                File lrc1 = new File(nimiyoAudioDir, baseName + ".lrc");
                                if (lrc1.exists() && lrc1.isFile()) {
                                    lyrics = readTextFile(lrc1);
                                }
                            }
                        }

                        // 3. Extract embedded ID3v2 (USLT/SYLT) / MP4 / Vorbis tags from audio file
                        if (lyrics == null || lyrics.trim().isEmpty()) {
                            lyrics = extractEmbeddedLyrics(audioFile);
                        }
                    }
                }

                if (lyrics != null && !lyrics.trim().isEmpty()) {
                    boolean isSynced = lyrics.contains("[") && lyrics.contains("]") && lyrics.matches("(?s).*\\[\\d{1,2}:\\d{2}.*");
                    JSObject ret = new JSObject();
                    ret.put("hasLyrics", true);
                    ret.put("lyrics", lyrics.trim());
                    ret.put("isSynced", isSynced);
                    call.resolve(ret);
                } else {
                    JSObject ret = new JSObject();
                    ret.put("hasLyrics", false);
                    call.resolve(ret);
                }
            } catch (Exception e) {
                JSObject ret = new JSObject();
                ret.put("hasLyrics", false);
                call.resolve(ret);
            }
        });
    }

    @PluginMethod
    public void saveLyricsFile(PluginCall call) {
        String lyricsContent = call.getString("lyricsContent");
        String trackTitle = call.getString("trackTitle");
        String trackArtist = call.getString("trackArtist");
        String filePath = call.getString("filePath");
        String customFileName = call.getString("fileName");

        if (lyricsContent == null || lyricsContent.trim().isEmpty()) {
            call.reject("Lyrics content is empty");
            return;
        }

        downloadExecutor.execute(() -> {
            try {
                String baseName = null;
                if (customFileName != null && !customFileName.trim().isEmpty()) {
                    baseName = customFileName.trim().replaceFirst("[.][^.]+$", "");
                } else if (filePath != null && !filePath.isEmpty()) {
                    File af = new File(filePath);
                    baseName = af.getName().replaceFirst("[.][^.]+$", "");
                } else if (trackTitle != null && !trackTitle.isEmpty()) {
                    baseName = trackTitle.trim();
                    if (trackArtist != null && !trackArtist.isEmpty() && !trackArtist.equalsIgnoreCase("Unknown Artist")) {
                        baseName = baseName + " - " + trackArtist.trim();
                    }
                } else {
                    baseName = "Lyrics_" + System.currentTimeMillis();
                }

                String safeName = baseName.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
                if (safeName.isEmpty()) safeName = "Lyrics_" + System.currentTimeMillis();

                File lyricsDir = getLyricsYoDir();
                File targetFile = new File(lyricsDir, safeName + ".lrc");

                try (FileOutputStream fos = new FileOutputStream(targetFile);
                     OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                    osw.write(lyricsContent.trim());
                    osw.flush();
                }

                // Companion next to audio file
                if (filePath != null && !filePath.isEmpty()) {
                    try {
                        File af = new File(filePath);
                        File parent = af.getParentFile();
                        if (parent != null && parent.canWrite()) {
                            File companionLrc = new File(parent, af.getName().replaceFirst("[.][^.]+$", "") + ".lrc");
                            try (FileOutputStream fos = new FileOutputStream(companionLrc);
                                 OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                                osw.write(lyricsContent.trim());
                                osw.flush();
                            }
                        }
                    } catch (Exception ignored) {}
                }

                // Fallback directory sync
                try {
                    File fallbackDir = getFallbackLyricsYoDir();
                    File fallbackTarget = new File(fallbackDir, safeName + ".lrc");
                    try (FileOutputStream fos = new FileOutputStream(fallbackTarget);
                         OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                        osw.write(lyricsContent.trim());
                        osw.flush();
                    }
                } catch (Exception ignored) {}

                // Scan file so Android media library discovers it
                try {
                    android.media.MediaScannerConnection.scanFile(
                        getContext(),
                        new String[]{ targetFile.getAbsolutePath() },
                        new String[]{ "text/plain" },
                        null
                    );
                } catch (Exception ignored) {}

                JSObject ret = new JSObject();
                ret.put("success", true);
                ret.put("filePath", targetFile.getAbsolutePath());
                ret.put("fileName", targetFile.getName());
                call.resolve(ret);
            } catch (Exception e) {
                call.reject("Gagal menyimpan file lirik: " + e.getMessage(), e);
            }
        });
    }

    private String readTextFile(File file) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
            String result = sb.toString().trim();
            return result.isEmpty() ? null : result;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String extractEmbeddedLyrics(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] header = new byte[10];
            int read = fis.read(header);
            if (read < 10) return null;

            // Check ID3v2 header
            if (header[0] == 'I' && header[1] == 'D' && header[2] == '3') {
                int version = header[3] & 0xFF;
                int tagSize = ((header[6] & 0x7F) << 21) |
                              ((header[7] & 0x7F) << 14) |
                              ((header[8] & 0x7F) << 7) |
                              (header[9] & 0x7F);

                if (tagSize > 0 && tagSize < 12 * 1024 * 1024) {
                    byte[] tagData = new byte[tagSize];
                    int totalRead = 0;
                    while (totalRead < tagSize) {
                        int r = fis.read(tagData, totalRead, tagSize - totalRead);
                        if (r <= 0) break;
                        totalRead += r;
                    }
                    String lrc = parseId3TagForLyrics(tagData, version);
                    if (lrc != null && !lrc.trim().isEmpty()) return lrc.trim();
                }
            }

            // Raw scan first 512KB for USLT / ©lyr / LYRICS=
            return searchRawBytesForLyrics(file);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String parseId3TagForLyrics(byte[] data, int version) {
        int pos = 0;
        int max = data.length;

        while (pos + 10 < max) {
            if (data[pos] == 0 && data[pos + 1] == 0) break; // Padding reached

            if (version == 2) { // ID3v2.2: 3-char IDs, 3-byte size
                if (pos + 6 > max) break;
                String frameId = new String(data, pos, 3, StandardCharsets.ISO_8859_1);
                int frameSize = ((data[pos + 3] & 0xFF) << 16) | ((data[pos + 4] & 0xFF) << 8) | (data[pos + 5] & 0xFF);
                pos += 6;
                if (pos + frameSize > max || frameSize <= 0) break;
                if ("ULT".equalsIgnoreCase(frameId) || "SLT".equalsIgnoreCase(frameId)) {
                    return decodeUsltFrame(data, pos, frameSize);
                }
                pos += frameSize;
            } else { // ID3v2.3 and ID3v2.4: 4-char IDs, 4-byte size, 2-byte flags
                String frameId = new String(data, pos, 4, StandardCharsets.ISO_8859_1);
                int frameSize;
                if (version == 4) { // syncsafe size
                    frameSize = ((data[pos + 4] & 0x7F) << 21) | ((data[pos + 5] & 0x7F) << 14) |
                                ((data[pos + 6] & 0x7F) << 7) | (data[pos + 7] & 0x7F);
                } else {
                    frameSize = ((data[pos + 4] & 0xFF) << 24) | ((data[pos + 5] & 0xFF) << 16) |
                                ((data[pos + 6] & 0xFF) << 8) | (data[pos + 7] & 0xFF);
                }
                pos += 10;
                if (frameSize <= 0 || pos + frameSize > max) break;
                if ("USLT".equalsIgnoreCase(frameId) || "SYLT".equalsIgnoreCase(frameId)) {
                    return decodeUsltFrame(data, pos, frameSize);
                }
                pos += frameSize;
            }
        }
        return null;
    }

    private String decodeUsltFrame(byte[] data, int start, int length) {
        try {
            if (length < 5) return null;
            int encoding = data[start] & 0xFF;
            int idx = start + 4; // Skip encoding (1) + language (3)
            int end = start + length;

            // Skip description string
            if (encoding == 1 || encoding == 2) { // UTF-16 (double null terminator)
                while (idx + 1 < end) {
                    if (data[idx] == 0 && data[idx + 1] == 0) {
                        idx += 2;
                        break;
                    }
                    idx += 2;
                }
            } else { // ISO-8859-1 or UTF-8 (single null terminator)
                while (idx < end) {
                    if (data[idx] == 0) {
                        idx++;
                        break;
                    }
                    idx++;
                }
            }

            if (idx >= end) return null;
            int textLen = end - idx;

            java.nio.charset.Charset cs;
            if (encoding == 1) cs = StandardCharsets.UTF_16;
            else if (encoding == 2) cs = StandardCharsets.UTF_16BE;
            else if (encoding == 3) cs = StandardCharsets.UTF_8;
            else cs = StandardCharsets.ISO_8859_1;

            return new String(data, idx, textLen, cs).trim();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String searchRawBytesForLyrics(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            int scanLimit = Math.min((int) file.length(), 512 * 1024);
            byte[] buf = new byte[scanLimit];
            int read = fis.read(buf);
            if (read <= 20) return null;

            // Search for USLT in raw buffer
            for (int i = 0; i < read - 15; i++) {
                if (buf[i] == 'U' && buf[i + 1] == 'S' && buf[i + 2] == 'L' && buf[i + 3] == 'T') {
                    int frameSize = ((buf[i + 4] & 0xFF) << 24) | ((buf[i + 5] & 0xFF) << 16) |
                                    ((buf[i + 6] & 0xFF) << 8) | (buf[i + 7] & 0xFF);
                    if (frameSize <= 0 || frameSize > read - (i + 10)) {
                        frameSize = ((buf[i + 4] & 0x7F) << 21) | ((buf[i + 5] & 0x7F) << 14) |
                                    ((buf[i + 6] & 0x7F) << 7) | (buf[i + 7] & 0x7F);
                    }
                    if (frameSize > 0 && i + 10 + frameSize <= read) {
                        String lyrics = decodeUsltFrame(buf, i + 10, frameSize);
                        if (lyrics != null && !lyrics.trim().isEmpty()) return lyrics;
                    }
                }
            }

            // Search for MP4 lyrics atom: ©lyr followed by data atom
            byte[] lyrPattern = new byte[]{(byte) 0xA9, 'l', 'y', 'r'};
            for (int i = 0; i < read - 20; i++) {
                if (buf[i] == lyrPattern[0] && buf[i + 1] == lyrPattern[1] && buf[i + 2] == lyrPattern[2] && buf[i + 3] == lyrPattern[3]) {
                    // Look for 'data' atom within 24 bytes
                    for (int j = i + 4; j < Math.min(i + 28, read - 8); j++) {
                        if (buf[j] == 'd' && buf[j + 1] == 'a' && buf[j + 2] == 't' && buf[j + 3] == 'a') {
                            int dataLen = ((buf[j - 4] & 0xFF) << 24) | ((buf[j - 3] & 0xFF) << 16) |
                                          ((buf[j - 2] & 0xFF) << 8) | (buf[j - 1] & 0xFF) - 16;
                            if (dataLen > 0 && j + 12 + dataLen <= read) {
                                return new String(buf, j + 12, dataLen, StandardCharsets.UTF_8).trim();
                            }
                        }
                    }
                }
            }
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    @PluginMethod
    public void shareAudioFile(PluginCall call) {
        String filePath = call.getString("filePath");
        String contentUriStr = call.getString("contentUri");
        String title = call.getString("title", "Audio");
        String mimeType = call.getString("mimeType", "audio/*");

        try {
            Uri shareUri = null;
            if (filePath != null && !filePath.isEmpty()) {
                File file = new File(filePath);
                if (file.exists()) {
                    shareUri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".fileprovider", file);
                }
            }

            if (shareUri == null && contentUriStr != null && !contentUriStr.isEmpty()) {
                shareUri = Uri.parse(contentUriStr);
            }

            if (shareUri == null) {
                call.reject("Audio file not found for sharing");
                return;
            }

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType(mimeType);
            shareIntent.putExtra(Intent.EXTRA_STREAM, shareUri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, title);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            Intent chooser = Intent.createChooser(shareIntent, title);
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(chooser);

            call.resolve(new JSObject().put("success", true));
        } catch (Exception e) {
            call.reject("Failed to share audio: " + e.getMessage());
        }
    }

    @PluginMethod
    public void deleteAudioFile(PluginCall call) {
        String filePath = call.getString("filePath");
        String contentUriStr = call.getString("contentUri");
        String inputFileName = call.getString("fileName");
        String trackId = call.getString("trackId");

        downloadExecutor.execute(() -> {
            boolean deleted = false;
            try {
                File file = (filePath != null && !filePath.isEmpty()) ? new File(filePath) : null;
                final String fileName = (inputFileName != null && !inputFileName.isEmpty()) ? inputFileName : (file != null ? file.getName() : null);

                // 1. Direct file deletion (succeeds if app owns file, legacy storage, or MANAGE_EXTERNAL_STORAGE is granted)
                if (file != null && file.exists()) {
                    deleted = file.delete();
                }

                // 2. Delete companion .lrc / .txt files
                if (filePath != null && !filePath.isEmpty()) {
                    try {
                        String lrcPath = filePath.replaceFirst("[.][^.]+$", ".lrc");
                        File lrcFile = new File(lrcPath);
                        if (lrcFile.exists()) lrcFile.delete();

                        String txtPath = filePath.replaceFirst("[.][^.]+$", ".txt");
                        File txtFile = new File(txtPath);
                        if (txtFile.exists()) txtFile.delete();
                    } catch (Exception ignored) {}
                }

                ContentResolver resolver = getContext().getContentResolver();

                // 3. Delete via explicit contentUri if it is a content:// URI
                if (contentUriStr != null && contentUriStr.startsWith("content://")) {
                    try {
                        Uri uri = Uri.parse(contentUriStr);
                        int rows = resolver.delete(uri, null, null);
                        if (rows > 0) deleted = true;
                    } catch (Exception ignored) {}
                }

                // 4. Delete via MediaStore ID if trackId starts with ms_
                if (trackId != null && trackId.startsWith("ms_")) {
                    try {
                        long msId = Long.parseLong(trackId.substring(3));
                        Uri msUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, msId);
                        int rows = resolver.delete(msUri, null, null);
                        if (rows > 0) deleted = true;
                    } catch (Exception ignored) {}
                }

                // 5. Query and delete from MediaStore collections (Downloads, Audio, Files) by DATA column
                if (filePath != null && !filePath.isEmpty()) {
                    Uri[] collections;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        collections = new Uri[]{
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            MediaStore.Files.getContentUri("external")
                        };
                    } else {
                        collections = new Uri[]{
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            MediaStore.Files.getContentUri("external")
                        };
                    }

                    for (Uri col : collections) {
                        try {
                            try (Cursor c = resolver.query(col, new String[]{MediaStore.MediaColumns._ID},
                                    MediaStore.MediaColumns.DATA + " = ?", new String[]{filePath}, null)) {
                                if (c != null) {
                                    while (c.moveToNext()) {
                                        long rowId = c.getLong(0);
                                        Uri rowUri = ContentUris.withAppendedId(col, rowId);
                                        try {
                                            int rows = resolver.delete(rowUri, null, null);
                                            if (rows > 0) deleted = true;
                                        } catch (Exception ignored) {}
                                    }
                                }
                            }
                            int rows = resolver.delete(col, MediaStore.MediaColumns.DATA + " = ?", new String[]{filePath});
                            if (rows > 0) deleted = true;
                        } catch (Exception ignored) {}
                    }
                }

                // 6. Query MediaStore.Downloads by DISPLAY_NAME if in Nimiyo directory
                if (fileName != null && !fileName.isEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        try (Cursor c = resolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                                new String[]{MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATA},
                                MediaStore.MediaColumns.DISPLAY_NAME + " = ?", new String[]{fileName}, null)) {
                            if (c != null) {
                                while (c.moveToNext()) {
                                    long rowId = c.getLong(0);
                                    String rowData = c.getString(1);
                                    if (rowData == null || filePath == null || rowData.equalsIgnoreCase(filePath) || rowData.contains("Nimiyo")) {
                                        Uri rowUri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, rowId);
                                        try {
                                            int rows = resolver.delete(rowUri, null, null);
                                            if (rows > 0) deleted = true;
                                        } catch (Exception ignored) {}
                                    }
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }

                // 7. Check if file is still physically on disk
                boolean stillExists = (file != null && file.exists());
                if (!stillExists) {
                    deleted = true;
                }

                // 8. Trigger MediaScanner to drop deleted item from system index
                if (filePath != null && !filePath.isEmpty()) {
                    String lrcPath = filePath.replaceFirst("[.][^.]+$", ".lrc");
                    MediaScannerConnection.scanFile(
                        getContext(),
                        new String[]{filePath, lrcPath},
                        null,
                        null
                    );
                }

                boolean needsAllFilesAccess = false;
                if (stillExists && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    needsAllFilesAccess = !Environment.isExternalStorageManager();
                }

                JSObject ret = new JSObject();
                ret.put("success", deleted && !stillExists);
                ret.put("stillExists", stillExists);
                ret.put("needsAllFilesAccess", needsAllFilesAccess);
                call.resolve(ret);
            } catch (Exception e) {
                call.reject("Failed to delete audio file: " + e.getMessage());
            }
        });
    }

    @PluginMethod
    public void moveAudioToNimiyo(PluginCall call) {
        String filePath = call.getString("filePath");
        String contentUriStr = call.getString("contentUri");
        String fileName = call.getString("fileName");

        downloadExecutor.execute(() -> {
            try {
                File nimiyoAudioDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Nimiyo/AudioYo");
                if (!nimiyoAudioDir.exists()) {
                    nimiyoAudioDir.mkdirs();
                }

                File sourceFile = null;
                if (filePath != null && !filePath.isEmpty()) {
                    sourceFile = new File(filePath);
                }

                if (sourceFile == null || !sourceFile.exists()) {
                    call.reject("File audio sumber tidak ditemukan");
                    return;
                }

                // Check if already in Nimiyo/AudioYo
                String currentPath = sourceFile.getAbsolutePath().toLowerCase();
                if (currentPath.contains("nimiyo/audioyo") || (sourceFile.getParentFile() != null && sourceFile.getParentFile().getAbsolutePath().equalsIgnoreCase(nimiyoAudioDir.getAbsolutePath()))) {
                    JSObject ret = new JSObject();
                    ret.put("success", true);
                    ret.put("alreadyInNimiyo", true);
                    ret.put("newPath", sourceFile.getAbsolutePath());
                    call.resolve(ret);
                    return;
                }

                String destName = (fileName != null && !fileName.trim().isEmpty()) ? fileName.trim() : sourceFile.getName();
                File destFile = new File(nimiyoAudioDir, destName);
                if (destFile.exists()) {
                    String base = destName.replaceFirst("[.][^.]+$", "");
                    String ext = destName.contains(".") ? destName.substring(destName.lastIndexOf(".")) : ".mp3";
                    destFile = new File(nimiyoAudioDir, base + "_" + System.currentTimeMillis() + ext);
                }

                boolean moved = sourceFile.renameTo(destFile);
                if (!moved) {
                    try (InputStream in = new FileInputStream(sourceFile);
                         OutputStream out = new java.io.FileOutputStream(destFile)) {
                        byte[] buf = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = in.read(buf)) > 0) {
                            out.write(buf, 0, bytesRead);
                        }
                        out.flush();
                        moved = true;
                    }
                    if (moved) {
                        try { sourceFile.delete(); } catch (Exception ignored) {}
                    }
                }

                // Companion LRC if exists
                String srcLrcPath = sourceFile.getAbsolutePath().replaceFirst("[.][^.]+$", ".lrc");
                File srcLrc = new File(srcLrcPath);
                if (srcLrc.exists()) {
                    String destLrcPath = destFile.getAbsolutePath().replaceFirst("[.][^.]+$", ".lrc");
                    srcLrc.renameTo(new File(destLrcPath));
                }

                // Scan MediaStore for new file and old file
                final String finalNewPath = destFile.getAbsolutePath();
                final String finalOldPath = sourceFile.getAbsolutePath();
                MediaScannerConnection.scanFile(
                    getContext(),
                    new String[]{ finalNewPath, finalOldPath },
                    null,
                    null
                );

                JSObject ret = new JSObject();
                ret.put("success", moved);
                ret.put("newPath", finalNewPath);
                call.resolve(ret);
            } catch (Exception e) {
                call.reject("Gagal memindahkan audio: " + e.getMessage());
            }
        });
    }
}

