package nimiyo.litedownloader;

import android.annotation.SuppressLint;
import android.app.Activity;
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
    private static final String CHANNEL_ID = "nimiyo_download_channel";
    private static final int NOTIFICATION_ID = 8802;
    private final ExecutorService httpExecutor = Executors.newFixedThreadPool(4);

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
        webView.loadUrl("file:///android_asset/public/share.html");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
        dispatchUrlToWebview();
    }

    private void dispatchUrlToWebview() {
        if (webView != null) {
            String escapedUrl = extractedUrl.replace("\\", "\\\\").replace("\"", "\\\"");
            webView.evaluateJavascript(
                "window.NimidzShareBridge = window.NimiyoShareBridge;" +
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
            return matcher.group(0);
        }
        return "";
    }

    @Override
    public void onBackPressed() {
        if (webView != null) {
            webView.evaluateJavascript("if (window.dismissPanel) { window.dismissPanel(); } else { window.NimiyoShareBridge.dismiss(); }", null);
        } else {
            super.onBackPressed();
        }
    }

    public void dismissDialog() {
        runOnUiThread(() -> {
            finish();
            overridePendingTransition(0, android.R.anim.fade_out);
        });
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

    private HttpURLConnection connectWithRedirects(String initialUrl, String method, Map<String, String> headers, byte[] postData) throws Exception {
        String currentUrl = initialUrl;
        int redirectCount = 0;
        final int MAX_REDIRECTS = 10;

        while (redirectCount < MAX_REDIRECTS) {
            URL url = new URL(currentUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(30000);
            conn.setInstanceFollowRedirects(false); // Manually handle cross-protocol/domain redirects

            // Default headers
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
            conn.setRequestProperty("Accept", "*/*");

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
                    continue;
                }
            }
            return conn;
        }
        throw new Exception("Too many HTTP redirects");
    }

    // Native Bridge for Javascript
    public class NimiyoShareBridge {
        private final Activity activity;

        public NimiyoShareBridge(Activity activity) {
            this.activity = activity;
        }

        @JavascriptInterface
        public String getSharedUrl() {
            return extractedUrl;
        }

        @JavascriptInterface
        public void dismiss() {
            dismissDialog();
        }

        @JavascriptInterface
        public void minimize() {
            activity.runOnUiThread(() -> {
                activity.moveTaskToBack(true);
            });
        }

        @JavascriptInterface
        public void showToast(String message) {
            activity.runOnUiThread(() -> Toast.makeText(activity, message, Toast.LENGTH_SHORT).show());
        }

        @JavascriptInterface
        public void showNotification(String title, String message, int progress, int max, boolean isCompleted) {
            try {
                ensureNotificationChannel();
                NotificationManager manager = (NotificationManager) activity.getSystemService(Context.NOTIFICATION_SERVICE);
                if (manager != null) {
                    NotificationCompat.Builder builder = new NotificationCompat.Builder(activity, CHANNEL_ID)
                        .setContentTitle(title)
                        .setContentText(message)
                        .setSmallIcon(android.R.drawable.stat_sys_download)
                        .setOngoing(!isCompleted)
                        .setAutoCancel(isCompleted)
                        .setPriority(isCompleted ? NotificationCompat.PRIORITY_DEFAULT : NotificationCompat.PRIORITY_LOW);

                    if (!isCompleted) {
                        if (max > 0) {
                            builder.setProgress(max, progress, false);
                        } else {
                            builder.setProgress(0, 0, true);
                        }
                    } else {
                        builder.setSmallIcon(android.R.drawable.stat_sys_download_done);
                        builder.setProgress(0, 0, false);
                    }

                    manager.notify(NOTIFICATION_ID, builder.build());
                }
            } catch (Exception ignored) {}
        }

        // Native Direct Stream Downloader with Full Cross-Protocol Redirect Support
        @JavascriptInterface
        public void downloadFileAsync(String fileUrl, String filename, String mimeType, boolean isAudio, String reqId) {
            httpExecutor.execute(() -> {
                boolean success = false;
                String errorMsg = "";
                HttpURLConnection conn = null;
                OutputStream outStream = null;
                Uri itemUri = null;
                ContentResolver resolver = activity.getContentResolver();
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
                    showNotification("Nimiyo Downloader", "Mengunduh " + filename, 10, 100, false);

                    Map<String, String> headers = new HashMap<>();
                    headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
                    headers.put("Accept", "*/*");
                    if (fileUrl.contains("spotidown") || fileUrl.contains("spotify")) {
                        headers.put("Referer", "https://spotidown.app/");
                    }

                    conn = connectWithRedirects(fileUrl, "GET", headers, null);
                    int statusCode = conn.getResponseCode();

                    if (statusCode < 200 || statusCode >= 400) {
                        throw new Exception("HTTP server error " + statusCode);
                    }

                    long contentLength = conn.getContentLength();
                    InputStream is = conn.getInputStream();

                    // Open Target Stream
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
                        File targetFile = new File(dir, filename);
                        outStream = new FileOutputStream(targetFile);
                    }

                    if (outStream == null) {
                        throw new Exception("Could not open output storage stream");
                    }

                    byte[] buffer = new byte[16384];
                    int bytesRead;
                    long totalBytesRead = 0;
                    int lastPercent = 0;

                    while ((bytesRead = is.read(buffer)) != -1) {
                        outStream.write(buffer, 0, bytesRead);
                        totalBytesRead += bytesRead;

                        if (contentLength > 0) {
                            int percent = (int) ((totalBytesRead * 100) / contentLength);
                            if (percent - lastPercent >= 10) {
                                lastPercent = percent;
                                showNotification("Nimiyo Downloader", "Mengunduh " + filename + " (" + percent + "%)", percent, 100, false);
                            }
                        }
                    }

                    outStream.flush();
                    is.close();

                    if (totalBytesRead < 200 && !lowerFileName.endsWith(".jpg") && !lowerFileName.endsWith(".png")) {
                        throw new Exception("Downloaded file is invalid or empty (" + totalBytesRead + " bytes)");
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && itemUri != null) {
                        contentValues.clear();
                        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0);
                        resolver.update(itemUri, contentValues, null, null);
                    }

                    success = true;
                    showNotification("Nimiyo Downloader", "Berkas berhasil disimpan! (" + filename + ")", 100, 100, true);
                    showToast("Berkas berhasil disimpan! (" + filename + ")");

                } catch (Exception e) {
                    errorMsg = e.getMessage() != null ? e.getMessage() : "Unknown download error";
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && itemUri != null) {
                        try { resolver.delete(itemUri, null, null); } catch (Exception ignored) {}
                    }
                    showNotification("Nimiyo Downloader", "Unduhan gagal: " + errorMsg, 0, 0, true);
                    showToast("Unduhan gagal: " + errorMsg);
                } finally {
                    if (outStream != null) {
                        try { outStream.close(); } catch (Exception ignored) {}
                    }
                    if (conn != null) conn.disconnect();
                }

                final boolean finalSuccess = success;
                final String finalErrorMsg = errorMsg;

                activity.runOnUiThread(() -> {
                    if (webView != null) {
                        String payload = "{\"success\":" + finalSuccess + ",\"error\":\"" + finalErrorMsg.replace("\"", "\\\"") + "\"}";
                        webView.evaluateJavascript(
                            "if (window.__nimiyoShareCallbacks && window.__nimiyoShareCallbacks['" + reqId + "']) {" +
                            "  window.__nimiyoShareCallbacks['" + reqId + "'](" + payload + ");" +
                            "  delete window.__nimiyoShareCallbacks['" + reqId + "'];" +
                            "}",
                            null
                        );
                    }
                });
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
