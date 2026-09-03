package nimiyo.litedownloader;

import android.app.DownloadManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Base64;
import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;

@CapacitorPlugin(name = "MediaSaver")
public class MediaSaverPlugin extends Plugin {

    private static final String CHANNEL_ID = "nimiyo_download_channel";
    private static final int NOTIFICATION_ID = 8801;

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
                    manager.createNotificationChannel(newChannel);
                }
            }
        }
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

            NotificationCompat.Builder builder = new NotificationCompat.Builder(getContext(), CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(title)
                .setContentText(message)
                .setOngoing(!isCompleted)
                .setAutoCancel(isCompleted)
                .setPriority(isCompleted ? NotificationCompat.PRIORITY_DEFAULT : NotificationCompat.PRIORITY_LOW);

            if (isCompleted) {
                builder.setSmallIcon(android.R.drawable.stat_sys_download_done);
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
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            
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
            } else {
                subFolder = "ETC";
                mime = "application/octet-stream";
            }

            values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            
            Uri collectionUri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Nimiyo/" + subFolder);
                collectionUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            } else {
                File publicDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Nimiyo/" + subFolder);
                if (!publicDir.exists()) {
                    publicDir.mkdirs();
                }
                collectionUri = MediaStore.Files.getContentUri("external");
            }

            Uri fileUri = resolver.insert(collectionUri, values);
            if (fileUri == null) {
                call.reject("Failed to create MediaStore entry");
                return;
            }

            OutputStream os = resolver.openOutputStream(fileUri);
            FileInputStream fis = new FileInputStream(sourceFile);
            byte[] buf = new byte[8192];
            int len;
            while ((len = fis.read(buf)) > 0) {
                os.write(buf, 0, len);
            }
            fis.close();
            os.close();

            JSObject ret = new JSObject();
            ret.put("success", true);
            ret.put("uri", fileUri.toString());
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

    @PluginMethod
    public void openMediaFile(PluginCall call) {
        String fileName = call.getString("fileName");
        String subFolder = call.getString("subFolder", "VideoYo");
        String fileType = call.getString("fileType", "video");

        if (fileName == null) {
            call.reject("fileName is required");
            return;
        }

        try {
            Context context = getContext();
            File mediaFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Nimiyo/" + subFolder + "/" + fileName);
            
            if (!mediaFile.exists()) {
                File f1 = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Nimiyo/" + fileName);
                File f2 = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), subFolder + "/" + fileName);
                File f3 = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName);
                if (f1.exists()) mediaFile = f1;
                else if (f2.exists()) mediaFile = f2;
                else if (f3.exists()) mediaFile = f3;
            }

            if (!mediaFile.exists()) {
                call.reject("Media file not found on storage: " + fileName);
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
            
            Intent chooser = Intent.createChooser(intent, "Play " + fileName);
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(chooser);

            JSObject ret = new JSObject();
            ret.put("success", true);
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
            File mediaFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Nimiyo/" + subFolder + "/" + fileName);
            
            if (!mediaFile.exists()) {
                File f1 = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Nimiyo/" + fileName);
                File f2 = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), subFolder + "/" + fileName);
                File f3 = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName);
                if (f1.exists()) mediaFile = f1;
                else if (f2.exists()) mediaFile = f2;
                else if (f3.exists()) mediaFile = f3;
            }

            if (!mediaFile.exists()) {
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
            ret.put("uri", contentUri.toString());
            ret.put("fileUri", Uri.fromFile(mediaFile).toString());
            ret.put("length", mediaFile.length());

            if (mediaFile.length() <= 20 * 1024 * 1024) {
                byte[] bytes = new byte[(int) mediaFile.length()];
                FileInputStream fis = new FileInputStream(mediaFile);
                fis.read(bytes);
                fis.close();
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
    public void installApk(PluginCall call) {
        String filePath = call.getString("filePath");
        if (filePath == null || filePath.isEmpty()) {
            call.reject("filePath is required");
            return;
        }

        try {
            Context context = getContext();
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
                    File inExternal = new File(context.getExternalFilesDir(null), filePath);
                    if (inExternal.exists()) {
                        apkFile = inExternal;
                    }
                }
            }

            if (!apkFile.exists()) {
                call.reject("APK file does not exist: " + filePath);
                return;
            }

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
            context.startActivity(intent);

            JSObject ret = new JSObject();
            ret.put("success", true);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Failed to trigger APK install: " + e.getMessage());
        }
    }
}
