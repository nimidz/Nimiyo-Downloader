package nimiyo.litedownloader;

import android.os.Build;
import android.os.Bundle;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(MediaSaverPlugin.class);
        super.onCreate(savedInstanceState);

        // 1. Force Hardware Acceleration on Window
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        );

        // 2. Lock to Highest Display Refresh Rate (VSync synchronization for 90Hz/120Hz/144Hz screens)
        applyHighRefreshRate();

        // 3. Optimize WebView GPU rasterization and pre-rastering
        optimizeWebView();

        // Auto request notification permission on Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            // Auto request storage permissions on Android 10 and below (API <= 29)
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    android.Manifest.permission.READ_EXTERNAL_STORAGE
                }, 102);
            }
        }
    }

    private void applyHighRefreshRate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                Display display = getWindowManager().getDefaultDisplay();
                Display.Mode[] modes = display.getSupportedModes();
                Display.Mode maxMode = null;
                float maxRefresh = 60.0f;
                for (Display.Mode mode : modes) {
                    if (mode.getRefreshRate() > maxRefresh) {
                        maxRefresh = mode.getRefreshRate();
                        maxMode = mode;
                    }
                }
                if (maxMode != null) {
                    WindowManager.LayoutParams params = getWindow().getAttributes();
                    params.preferredDisplayModeId = maxMode.getModeId();
                    getWindow().setAttributes(params);
                }
            } catch (Exception ignored) {}
        }
    }

    private void optimizeWebView() {
        try {
            if (bridge != null && bridge.getWebView() != null) {
                WebView webView = bridge.getWebView();
                webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                WebSettings settings = webView.getSettings();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    settings.setOffscreenPreRaster(true);
                }
                settings.setRenderPriority(WebSettings.RenderPriority.HIGH);
                settings.setEnableSmoothTransition(true);
                settings.setAllowFileAccess(true);
                settings.setAllowContentAccess(true);
                settings.setAllowFileAccessFromFileURLs(true);
                settings.setAllowUniversalAccessFromFileURLs(true);
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void onResume() {
        super.onResume();
        applyHighRefreshRate();
        optimizeWebView();
    }

    @Override
    public void onBackPressed() {
        if (bridge != null && bridge.getWebView() != null) {
            bridge.getWebView().evaluateJavascript(
                "(function() { if (typeof window.handleAppBackButton === 'function') { return window.handleAppBackButton(); } return false; })()",
                (result) -> {
                    if ("false".equals(result) || "null".equals(result) || result == null) {
                        runOnUiThread(() -> super.onBackPressed());
                    }
                }
            );
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onDestroy() {
        try {
            android.content.Intent intent = new android.content.Intent(this, MusicPlaybackService.class);
            intent.setAction(MusicPlaybackService.ACTION_CLEAR);
            startService(intent);
        } catch (Exception ignored) {}
        try {
            android.app.NotificationManager manager = (android.app.NotificationManager) getSystemService(android.content.Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.cancel(MusicPlaybackService.MUSIC_NOTIFICATION_ID);
            }
        } catch (Exception ignored) {}
        super.onDestroy();
    }
}
