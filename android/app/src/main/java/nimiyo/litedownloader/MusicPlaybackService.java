package nimiyo.litedownloader;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.util.Base64;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import java.io.File;
import java.io.InputStream;

public class MusicPlaybackService extends Service {

    public static final String ACTION_UPDATE = "nimiyo.litedownloader.ACTION_UPDATE_MUSIC_NOTIFICATION";
    public static final String ACTION_CLEAR = "nimiyo.litedownloader.ACTION_CLEAR_MUSIC_NOTIFICATION";
    public static final String ACTION_PREV = "nimiyo.litedownloader.ACTION_MUSIC_PREV";
    public static final String ACTION_PLAY_PAUSE = "nimiyo.litedownloader.ACTION_MUSIC_PLAY_PAUSE";
    public static final String ACTION_NEXT = "nimiyo.litedownloader.ACTION_MUSIC_NEXT";

    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_ARTIST = "artist";
    public static final String EXTRA_ALBUM = "album";
    public static final String EXTRA_ARTWORK = "artwork";
    public static final String EXTRA_DURATION = "duration";
    public static final String EXTRA_POSITION = "position";
    public static final String EXTRA_IS_PLAYING = "isPlaying";
    public static final String EXTRA_PLAYBACK_SPEED = "playbackSpeed";

    public static final String MUSIC_CHANNEL_ID = "nimiyo_media_channel";
    public static final int MUSIC_NOTIFICATION_ID = 8803;

    private static MusicPlaybackService instance = null;
    private static volatile String pendingArtworkData = null;
    private MediaSession mediaSession = null;
    private boolean isPlaying = false;
    private float currentPlaybackSpeed = 1.0f;
    private long currentDurationMs = 0;
    private long currentPositionMs = 0;
    private String currentTitle = "NIMIYO";
    private String currentArtist = "Unknown Artist";
    private String currentAlbum = "";
    private Bitmap currentArtwork = null;

    public static MusicPlaybackService getInstance() {
        return instance;
    }

    public static void setPendingArtwork(String artworkData) {
        pendingArtworkData = artworkData;
    }

    public void updateDirectly(String title, String artist, String album, long duration, long position, boolean playing) {
        currentTitle = (title != null && !title.isEmpty()) ? title : "NIMIYO";
        currentArtist = (artist != null) ? artist : "";
        currentAlbum = (album != null) ? album : "";
        isPlaying = playing;
        currentDurationMs = duration;
        currentPositionMs = position;

        if (pendingArtworkData != null) {
            currentArtwork = loadArtworkBitmap(pendingArtworkData);
        } else if (currentArtwork == null) {
            currentArtwork = getDefaultArtworkBitmap();
        }

        updateMediaMetadata();
        updatePlaybackState(isPlaying, currentPositionMs);
        buildAndShowNotification();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        ensureNotificationChannel();
        initMediaSession();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        String action = intent.getAction();
        switch (action) {
            case ACTION_UPDATE:
                handleUpdateAction(intent);
                break;
            case ACTION_CLEAR:
                handleClearAction();
                break;
            case ACTION_PREV:
                MediaSaverPlugin.dispatchMediaAction("prev", null);
                break;
            case ACTION_PLAY_PAUSE:
                MediaSaverPlugin.dispatchMediaAction("playPause", null);
                break;
            case ACTION_NEXT:
                MediaSaverPlugin.dispatchMediaAction("next", null);
                break;
        }

        return START_NOT_STICKY;
    }

    private void ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
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
                    newChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                    manager.createNotificationChannel(newChannel);
                }
            }
        }
    }

    private void initMediaSession() {
        if (mediaSession != null) return;

        mediaSession = new MediaSession(this, "NimiyoMusicSession");
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                MediaSaverPlugin.dispatchMediaAction("play", null);
            }

            @Override
            public void onPause() {
                MediaSaverPlugin.dispatchMediaAction("pause", null);
            }

            @Override
            public void onSkipToNext() {
                MediaSaverPlugin.dispatchMediaAction("next", null);
            }

            @Override
            public void onSkipToPrevious() {
                MediaSaverPlugin.dispatchMediaAction("prev", null);
            }

            @Override
            public void onSeekTo(long pos) {
                MediaSaverPlugin.dispatchMediaAction("seek", pos);
                currentPositionMs = pos;
                updatePlaybackState(isPlaying, currentPositionMs);
            }

            @Override
            public void onStop() {
                MediaSaverPlugin.dispatchMediaAction("pause", null);
            }
        });

        mediaSession.setActive(true);
    }

    private void handleUpdateAction(Intent intent) {
        currentTitle = intent.getStringExtra(EXTRA_TITLE);
        if (currentTitle == null || currentTitle.isEmpty()) currentTitle = "NIMIYO";

        currentArtist = intent.getStringExtra(EXTRA_ARTIST);
        if (currentArtist == null) currentArtist = "";

        currentAlbum = intent.getStringExtra(EXTRA_ALBUM);
        if (currentAlbum == null) currentAlbum = "";

        isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, true);
        currentDurationMs = intent.getLongExtra(EXTRA_DURATION, 0);
        currentPositionMs = intent.getLongExtra(EXTRA_POSITION, 0);
        currentPlaybackSpeed = intent.getFloatExtra(EXTRA_PLAYBACK_SPEED, 1.0f);
        if (currentPlaybackSpeed <= 0f) currentPlaybackSpeed = 1.0f;

        String artworkData = intent.getStringExtra(EXTRA_ARTWORK);
        if (artworkData == null || artworkData.isEmpty()) {
            artworkData = pendingArtworkData;
        }
        currentArtwork = loadArtworkBitmap(artworkData);

        updateMediaMetadata();
        updatePlaybackState(isPlaying, currentPositionMs, currentPlaybackSpeed);
        buildAndShowNotification();
    }

    public void updateDirectly(String title, String artist, String album, long duration, long position, boolean playing, float playbackSpeed) {
        if (title != null && !title.isEmpty()) currentTitle = title;
        if (artist != null) currentArtist = artist;
        if (album != null) currentAlbum = album;
        isPlaying = playing;
        currentDurationMs = duration;
        currentPositionMs = position;
        currentPlaybackSpeed = playbackSpeed > 0f ? playbackSpeed : 1.0f;

        if (pendingArtworkData != null) {
            currentArtwork = loadArtworkBitmap(pendingArtworkData);
        }

        updateMediaMetadata();
        updatePlaybackState(isPlaying, currentPositionMs, currentPlaybackSpeed);
        buildAndShowNotification();
    }

    private void handleClearAction() {
        isPlaying = false;
        try {
            if (mediaSession != null) {
                updatePlaybackState(false, currentPositionMs);
                mediaSession.setActive(false);
                mediaSession.release();
                mediaSession = null;
            }
        } catch (Exception ignored) {}

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE);
            } else {
                stopForeground(true);
            }
        } catch (Exception ignored) {}

        try {
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.cancel(MUSIC_NOTIFICATION_ID);
            }
        } catch (Exception ignored) {}

        stopSelf();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        handleClearAction();
        super.onTaskRemoved(rootIntent);
    }

    private void updateMediaMetadata() {
        if (mediaSession == null) return;

        MediaMetadata.Builder builder = new MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, currentTitle)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, currentArtist)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, currentAlbum)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, currentDurationMs);

        if (currentArtwork != null) {
            builder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, currentArtwork);
            builder.putBitmap(MediaMetadata.METADATA_KEY_ART, currentArtwork);
        }

        mediaSession.setMetadata(builder.build());
    }

    private void updatePlaybackState(boolean playing, long position) {
        updatePlaybackState(playing, position, currentPlaybackSpeed);
    }

    private void updatePlaybackState(boolean playing, long position, float playbackSpeed) {
        if (mediaSession == null) return;

        long actions = PlaybackState.ACTION_PLAY |
            PlaybackState.ACTION_PAUSE |
            PlaybackState.ACTION_PLAY_PAUSE |
            PlaybackState.ACTION_SKIP_TO_NEXT |
            PlaybackState.ACTION_SKIP_TO_PREVIOUS |
            PlaybackState.ACTION_SEEK_TO |
            PlaybackState.ACTION_STOP;

        int state = playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED;
        float speed = playing ? (playbackSpeed > 0f ? playbackSpeed : 1.0f) : 0.0f;

        PlaybackState playbackState = new PlaybackState.Builder()
            .setActions(actions)
            .setState(state, position, speed)
            .build();

        mediaSession.setPlaybackState(playbackState);
    }

    private void buildAndShowNotification() {
        if (mediaSession == null) return;

        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
            ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
            : PendingIntent.FLAG_UPDATE_CURRENT;

        // Open app when notification body tapped
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pOpenIntent = PendingIntent.getActivity(this, 0, openIntent, flags);

        // Previous Action
        Intent prevIntent = new Intent(this, MusicPlaybackService.class);
        prevIntent.setAction(ACTION_PREV);
        PendingIntent pPrevIntent = PendingIntent.getService(this, 1, prevIntent, flags);

        // Play/Pause Action
        Intent playPauseIntent = new Intent(this, MusicPlaybackService.class);
        playPauseIntent.setAction(ACTION_PLAY_PAUSE);
        PendingIntent pPlayPauseIntent = PendingIntent.getService(this, 2, playPauseIntent, flags);

        // Next Action
        Intent nextIntent = new Intent(this, MusicPlaybackService.class);
        nextIntent.setAction(ACTION_NEXT);
        PendingIntent pNextIntent = PendingIntent.getService(this, 3, nextIntent, flags);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, MUSIC_CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        Notification.MediaStyle mediaStyle = new Notification.MediaStyle()
            .setMediaSession(mediaSession.getSessionToken())
            .setShowActionsInCompactView(0, 1, 2);

        int playPauseIcon = isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play;
        String playPauseTitle = isPlaying ? "Pause" : "Play";

        builder.setStyle(mediaStyle)
            .setSmallIcon(R.drawable.ic_music_note)
            .setLargeIcon(currentArtwork)
            .setContentTitle(currentTitle)
            .setContentText(currentArtist.isEmpty() ? "NIMIYO Music" : currentArtist)
            .setContentIntent(pOpenIntent)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .setAutoCancel(false)
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_previous, "Previous", pPrevIntent).build())
            .addAction(new Notification.Action.Builder(playPauseIcon, playPauseTitle, pPlayPauseIntent).build())
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_next, "Next", pNextIntent).build());

        if (currentAlbum != null && !currentAlbum.isEmpty()) {
            builder.setSubText(currentAlbum);
        }

        Notification notification = builder.build();

        if (isPlaying) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(MUSIC_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
                } else {
                    startForeground(MUSIC_NOTIFICATION_ID, notification);
                }
            } catch (Exception e) {
                NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (manager != null) {
                    manager.notify(MUSIC_NOTIFICATION_ID, notification);
                }
            }
        } else {
            // When paused, update notification without stripping foreground privilege via stopForeground(false).
            // Calling stopForeground(false) causes Android 12+ to reject subsequent background startForegroundService() calls!
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.notify(MUSIC_NOTIFICATION_ID, notification);
            }
        }
    }

    private Bitmap loadArtworkBitmap(String data) {
        if (data == null || data.trim().isEmpty()) {
            return getDefaultArtworkBitmap();
        }

        try {
            // 1. Base64 data URL
            if (data.startsWith("data:image")) {
                int commaIdx = data.indexOf(",");
                if (commaIdx != -1) {
                    String base64Str = data.substring(commaIdx + 1);
                    byte[] decoded = Base64.decode(base64Str, Base64.DEFAULT);
                    Bitmap bmp = BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
                    if (bmp != null) return bmp;
                }
            }

            // 2. Local file path
            if (data.startsWith("/") || data.startsWith("file://")) {
                String path = data.startsWith("file://") ? data.substring(7) : data;
                File f = new File(path);
                if (f.exists()) {
                    Bitmap bmp = BitmapFactory.decodeFile(f.getAbsolutePath());
                    if (bmp != null) return bmp;
                }
            }

            // 3. Content URI
            if (data.startsWith("content://")) {
                Uri uri = Uri.parse(data);
                try (InputStream is = getContentResolver().openInputStream(uri)) {
                    if (is != null) {
                        Bitmap bmp = BitmapFactory.decodeStream(is);
                        if (bmp != null) return bmp;
                    }
                }
            }
        } catch (Exception ignored) {}

        return getDefaultArtworkBitmap();
    }

    private Bitmap getDefaultArtworkBitmap() {
        try {
            Drawable drawable = ContextCompat.getDrawable(this, R.drawable.ic_music_placeholder);
            if (drawable != null) {
                int width = 256;
                int height = 256;
                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                drawable.draw(canvas);
                return bitmap;
            }
        } catch (Exception ignored) {}

        // Fallback simple generated bitmap if drawable fails
        Bitmap fallback = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(fallback);
        c.drawColor(0xFF222228);
        return fallback;
    }

    @Override
    public void onDestroy() {
        handleClearAction();
        instance = null;
        super.onDestroy();
    }
}
