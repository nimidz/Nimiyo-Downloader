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
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.media.audiofx.PresetReverb;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Base64;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public class MusicPlaybackService extends Service {

    public static final String ACTION_UPDATE = "nimiyo.litedownloader.ACTION_UPDATE_MUSIC_NOTIFICATION";
    public static final String ACTION_CLEAR = "nimiyo.litedownloader.ACTION_CLEAR_MUSIC_NOTIFICATION";
    public static final String ACTION_PREV = "nimiyo.litedownloader.ACTION_MUSIC_PREV";
    public static final String ACTION_PLAY_PAUSE = "nimiyo.litedownloader.ACTION_MUSIC_PLAY_PAUSE";
    public static final String ACTION_NEXT = "nimiyo.litedownloader.ACTION_MUSIC_NEXT";
    public static final String ACTION_DELETE_NOTIFICATION = "nimiyo.litedownloader.ACTION_DELETE_NOTIFICATION";
    public static final String ACTION_PLAY_NATIVE = "nimiyo.litedownloader.ACTION_PLAY_NATIVE";

    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_ARTIST = "artist";
    public static final String EXTRA_ALBUM = "album";
    public static final String EXTRA_ARTWORK = "artwork";
    public static final String EXTRA_DURATION = "duration";
    public static final String EXTRA_POSITION = "position";
    public static final String EXTRA_IS_PLAYING = "isPlaying";
    public static final String EXTRA_PLAYBACK_SPEED = "playbackSpeed";
    public static final String EXTRA_SOURCE_URL = "sourceUrl";
    public static final String EXTRA_TRACK_ID = "trackId";
    public static final String EXTRA_MATCH_PITCH = "matchPitch";
    public static final String EXTRA_REVERB_ENABLED = "reverbEnabled";
    public static final String EXTRA_REVERB_PRESET = "reverbPreset";
    public static final String EXTRA_QUEUE_JSON = "queueJson";
    public static final String EXTRA_QUEUE_INDEX = "queueIndex";

    public static final String MUSIC_CHANNEL_ID = "nimiyo_media_channel";
    public static final int MUSIC_NOTIFICATION_ID = 8803;

    public static class NativeTrackItem {
        public String id;
        public String title;
        public String artist;
        public String album;
        public String sourceUrl;
        public long duration;

        public NativeTrackItem(String id, String title, String artist, String album, String sourceUrl, long duration) {
            this.id = id != null ? id : "";
            this.title = (title != null && !title.isEmpty()) ? title : "NIMIYO";
            this.artist = (artist != null && !artist.isEmpty()) ? artist : "Unknown Artist";
            this.album = album != null ? album : "";
            this.sourceUrl = sourceUrl != null ? sourceUrl : "";
            this.duration = duration;
        }
    }

    private static MusicPlaybackService instance = null;
    private static volatile String pendingArtworkData = null;
    private MediaSession mediaSession = null;
    private MediaPlayer nativePlayer = null;
    private PresetReverb presetReverb = null;
    private PowerManager.WakeLock wakeLock = null;

    private boolean isPlaying = false;
    private float currentPlaybackSpeed = 1.0f;
    private boolean currentMatchPitch = false;
    private boolean isReverbEnabled = false;
    private int currentReverbPreset = PresetReverb.PRESET_LARGEHALL;

    private long currentDurationMs = 0;
    private long currentPositionMs = 0;
    private String currentTitle = "NIMIYO";
    private String currentArtist = "Unknown Artist";
    private String currentAlbum = "";
    private String currentSourceUrl = null;
    private String currentTrackId = null;
    private Bitmap currentArtwork = null;

    private final List<NativeTrackItem> queue = new ArrayList<>();
    private int queueIndex = 0;

    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            if (nativePlayer != null) {
                try {
                    boolean playing = nativePlayer.isPlaying();
                    long pos = nativePlayer.getCurrentPosition();
                    long dur = nativePlayer.getDuration();
                    currentPositionMs = pos;
                    if (dur > 0) currentDurationMs = dur;
                    if (MediaSaverPlugin.isAppForeground()) {
                        MediaSaverPlugin.dispatchPlaybackProgress(pos, currentDurationMs, playing, currentTrackId);
                    }
                } catch (Exception ignored) {}
            }
            if (isPlaying) {
                long delay = MediaSaverPlugin.isAppForeground() ? 300 : 2000;
                progressHandler.postDelayed(this, delay);
            }
        }
    };

    public static MusicPlaybackService getInstance() {
        return instance;
    }

    public static void setPendingArtwork(String artworkData) {
        pendingArtworkData = artworkData;
    }

    public boolean isPlaying() {
        if (nativePlayer != null) {
            try {
                return nativePlayer.isPlaying();
            } catch (Exception ignored) {}
        }
        return isPlaying;
    }

    public double getCurrentPositionSec() {
        if (nativePlayer != null) {
            try {
                return nativePlayer.getCurrentPosition() / 1000.0;
            } catch (Exception ignored) {}
        }
        return currentPositionMs / 1000.0;
    }

    public double getCurrentDurationSec() {
        if (nativePlayer != null) {
            try {
                int dur = nativePlayer.getDuration();
                if (dur > 0) return dur / 1000.0;
            } catch (Exception ignored) {}
        }
        return currentDurationMs / 1000.0;
    }

    public String getCurrentTrackId() {
        return currentTrackId;
    }

    public String getCurrentSourceUrl() {
        return currentSourceUrl;
    }

    public String getCurrentTitle() {
        return currentTitle;
    }

    public String getCurrentArtist() {
        return currentArtist;
    }

    public String getCurrentAlbum() {
        return currentAlbum;
    }

    public float getCurrentPlaybackSpeed() {
        return currentPlaybackSpeed;
    }

    public boolean isNativePlayerActive() {
        return nativePlayer != null;
    }

    public void updateDirectly(String title, String artist, String album, long durationMs, long positionMs, boolean isPlaying, float playbackSpeed, String sourceUrl, String trackId) {
        if (title != null) this.currentTitle = title;
        if (artist != null) this.currentArtist = artist;
        if (album != null) this.currentAlbum = album;
        if (durationMs > 0) this.currentDurationMs = durationMs;
        if (positionMs >= 0) this.currentPositionMs = positionMs;
        this.isPlaying = isPlaying;
        if (playbackSpeed > 0) this.currentPlaybackSpeed = playbackSpeed;
        if (sourceUrl != null) this.currentSourceUrl = sourceUrl;
        if (trackId != null) this.currentTrackId = trackId;

        updateMediaMetadata();
        updatePlaybackState(this.isPlaying, this.currentPositionMs, this.currentPlaybackSpeed);
        buildAndShowNotification();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        ensureNotificationChannel();
        initMediaSession();
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Nimiyo:MusicPlaybackWakeLock");
                wakeLock.setReferenceCounted(false);
            }
        } catch (Exception ignored) {}
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            return START_STICKY;
        }

        String action = intent.getAction();
        switch (action) {
            case ACTION_UPDATE:
                handleUpdateAction(intent);
                break;
            case ACTION_CLEAR:
                handleClearAction();
                break;
            case ACTION_DELETE_NOTIFICATION:
                handleClearAction();
                break;
            case ACTION_PREV:
                handlePrevAction();
                break;
            case ACTION_PLAY_PAUSE:
                handlePlayPauseAction();
                break;
            case ACTION_PLAY_NATIVE:
                handlePlayNativeAction(intent);
                break;
            case ACTION_NEXT:
                handleNextAction();
                break;
        }

        return START_STICKY;
    }

    private void handlePlayNativeAction(Intent intent) {
        String sourceUrl = intent.getStringExtra(EXTRA_SOURCE_URL);
        String trackId = intent.getStringExtra(EXTRA_TRACK_ID);
        String title = intent.getStringExtra(EXTRA_TITLE);
        String artist = intent.getStringExtra(EXTRA_ARTIST);
        String album = intent.getStringExtra(EXTRA_ALBUM);
        String artwork = intent.getStringExtra(EXTRA_ARTWORK);
        long duration = intent.getLongExtra(EXTRA_DURATION, 0);
        long position = intent.getLongExtra(EXTRA_POSITION, 0);
        float speed = intent.getFloatExtra(EXTRA_PLAYBACK_SPEED, 1.0f);
        boolean matchPitch = intent.getBooleanExtra(EXTRA_MATCH_PITCH, false);
        String queueJson = intent.getStringExtra(EXTRA_QUEUE_JSON);
        int queueIndex = intent.getIntExtra(EXTRA_QUEUE_INDEX, 0);
        this.isReverbEnabled = intent.getBooleanExtra(EXTRA_REVERB_ENABLED, false);
        this.currentReverbPreset = intent.getIntExtra(EXTRA_REVERB_PRESET, PresetReverb.PRESET_LARGEHALL);

        playNativeTrack(sourceUrl, trackId, title, artist, album, artwork, duration, position, speed, matchPitch, queueJson, queueIndex);
    }

    private void handlePlayPauseAction() {
        if (nativePlayer != null) {
            if (nativePlayer.isPlaying()) {
                pauseNativePlayback();
            } else {
                resumeNativePlayback();
            }
        } else {
            boolean nextState = !this.isPlaying;
            this.isPlaying = nextState;
            updatePlaybackState(nextState, currentPositionMs, currentPlaybackSpeed);
            buildAndShowNotification();
            MediaSaverPlugin.dispatchMediaAction("playPause", currentPositionMs);
        }
    }

    public synchronized void playNativeTrack(
        String sourceUrl, String trackId, String title, String artist, String album,
        String artwork, long durationMs, long positionMs, float speed, boolean matchPitch,
        String queueJson, int index
    ) {
        currentSourceUrl = sourceUrl;
        currentTrackId = trackId;
        currentTitle = (title != null && !title.isEmpty()) ? title : "NIMIYO";
        currentArtist = (artist != null && !artist.isEmpty()) ? artist : "Unknown Artist";
        currentAlbum = (album != null) ? album : "";
        currentDurationMs = durationMs;
        currentPositionMs = positionMs;
        currentPlaybackSpeed = speed > 0f ? speed : 1.0f;
        currentMatchPitch = matchPitch;

        if (queueJson != null && !queueJson.isEmpty()) {
            parseQueue(queueJson, index);
        }

        if (artwork != null && !artwork.isEmpty()) {
            currentArtwork = loadArtworkBitmap(artwork);
        } else if (currentArtwork == null) {
            currentArtwork = getDefaultArtworkBitmap();
        }

        updateMediaMetadata();
        startNativePlayback(positionMs);
    }

    private synchronized void startNativePlayback(long fromPositionMs) {
        if (currentSourceUrl == null || currentSourceUrl.trim().isEmpty()) return;
        try {
            if (nativePlayer != null) {
                try {
                    nativePlayer.setOnCompletionListener(null);
                    nativePlayer.setOnPreparedListener(null);
                    nativePlayer.setOnErrorListener(null);
                    if (nativePlayer.isPlaying()) {
                        nativePlayer.stop();
                    }
                    nativePlayer.reset();
                    nativePlayer.release();
                } catch (Exception ignored) {}
                nativePlayer = null;
            }
            if (presetReverb != null) {
                try { presetReverb.release(); } catch (Exception ignored) {}
                presetReverb = null;
            }

            nativePlayer = new MediaPlayer();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                AudioAttributes attrs = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build();
                nativePlayer.setAudioAttributes(attrs);
            } else {
                nativePlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            }

            if (currentSourceUrl.startsWith("content://")) {
                Uri contentUri = Uri.parse(currentSourceUrl);
                try {
                    android.content.res.AssetFileDescriptor afd = getContentResolver().openAssetFileDescriptor(contentUri, "r");
                    if (afd != null) {
                        nativePlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                        afd.close();
                    } else {
                        nativePlayer.setDataSource(this, contentUri);
                    }
                } catch (Exception afdEx) {
                    nativePlayer.setDataSource(this, contentUri);
                }
            } else if (currentSourceUrl.startsWith("http://") || currentSourceUrl.startsWith("https://")) {
                nativePlayer.setDataSource(currentSourceUrl);
            } else {
                String path = currentSourceUrl.startsWith("file://") ? currentSourceUrl.substring(7) : currentSourceUrl;
                nativePlayer.setDataSource(path);
            }

            // Hardware DSP PresetReverb attach
            if (isReverbEnabled) {
                try {
                    presetReverb = new PresetReverb(0, 0);
                    presetReverb.setPreset((short) currentReverbPreset);
                    presetReverb.setEnabled(true);
                } catch (Exception ignored) {}
            }

            nativePlayer.setOnPreparedListener(mp -> {
                if (fromPositionMs > 0) {
                    mp.seekTo((int) fromPositionMs);
                }
                applyPlaybackParams();
                if (isReverbEnabled && presetReverb != null) {
                    try {
                        mp.attachAuxEffect(presetReverb.getId());
                        mp.setAuxEffectSendLevel(1.0f);
                    } catch (Exception ignored) {}
                }
                mp.start();
                isPlaying = true;
                currentPositionMs = mp.getCurrentPosition();
                int dur = mp.getDuration();
                if (dur > 0) currentDurationMs = dur;
                updatePlaybackState(true, currentPositionMs, currentPlaybackSpeed);
                buildAndShowNotification();
                progressHandler.removeCallbacks(progressRunnable);
                progressHandler.post(progressRunnable);
                MediaSaverPlugin.dispatchTrackChange(currentTrackId, currentTitle, currentArtist, currentAlbum, currentDurationMs, true);
            });

            nativePlayer.setOnCompletionListener(mp -> {
                isPlaying = false;
                progressHandler.removeCallbacks(progressRunnable);
                handleNextAction();
            });

            nativePlayer.setOnErrorListener((mp, what, extra) -> {
                if (what == -38 || extra == -38) {
                    return true;
                }
                isPlaying = false;
                progressHandler.removeCallbacks(progressRunnable);
                buildAndShowNotification();
                return true;
            });

            nativePlayer.prepareAsync();
        } catch (Exception e) {
            isPlaying = false;
        }
    }

    public synchronized void pauseNativePlayback() {
        if (nativePlayer != null) {
            try {
                if (nativePlayer.isPlaying()) {
                    nativePlayer.pause();
                    currentPositionMs = nativePlayer.getCurrentPosition();
                }
            } catch (Exception ignored) {}
        }
        isPlaying = false;
        progressHandler.removeCallbacks(progressRunnable);
        updatePlaybackState(false, currentPositionMs, currentPlaybackSpeed);
        buildAndShowNotification();
        MediaSaverPlugin.dispatchPlaybackProgress(currentPositionMs, currentDurationMs, false, currentTrackId);
    }

    public synchronized void resumeNativePlayback() {
        if (nativePlayer != null) {
            try {
                applyPlaybackParams();
                nativePlayer.start();
                isPlaying = true;
                currentPositionMs = nativePlayer.getCurrentPosition();
                updatePlaybackState(true, currentPositionMs, currentPlaybackSpeed);
                buildAndShowNotification();
                progressHandler.removeCallbacks(progressRunnable);
                progressHandler.post(progressRunnable);
                MediaSaverPlugin.dispatchPlaybackProgress(currentPositionMs, currentDurationMs, true, currentTrackId);
                return;
            } catch (Exception ignored) {}
        }
        if (currentSourceUrl != null && !currentSourceUrl.isEmpty()) {
            startNativePlayback(currentPositionMs);
        }
    }

    public synchronized void seekNativePlayback(long posMs) {
        currentPositionMs = posMs;
        if (nativePlayer != null) {
            try {
                nativePlayer.seekTo((int) posMs);
            } catch (Exception ignored) {}
        } else {
            MediaSaverPlugin.dispatchMediaAction("seek", posMs);
        }
        updatePlaybackState(isPlaying, currentPositionMs, currentPlaybackSpeed);
        MediaSaverPlugin.dispatchPlaybackProgress(currentPositionMs, currentDurationMs, isPlaying, currentTrackId);
    }

    public synchronized void setNativeSpeed(float speed, boolean matchPitch) {
        currentPlaybackSpeed = speed > 0f ? speed : 1.0f;
        currentMatchPitch = matchPitch;
        applyPlaybackParams();
        updatePlaybackState(isPlaying, currentPositionMs, currentPlaybackSpeed);
        buildAndShowNotification();
    }

    public synchronized void setNativeReverb(boolean enabled, int preset) {
        isReverbEnabled = enabled;
        currentReverbPreset = preset > 0 ? preset : PresetReverb.PRESET_LARGEHALL;
        if (nativePlayer != null) {
            try {
                if (presetReverb == null && enabled) {
                    presetReverb = new PresetReverb(0, 0);
                }
                if (presetReverb != null) {
                    if (enabled) {
                        presetReverb.setPreset((short) currentReverbPreset);
                        presetReverb.setEnabled(true);
                        nativePlayer.attachAuxEffect(presetReverb.getId());
                        nativePlayer.setAuxEffectSendLevel(1.0f);
                    } else {
                        presetReverb.setEnabled(false);
                        nativePlayer.setAuxEffectSendLevel(0.0f);
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    private void applyPlaybackParams() {
        if (nativePlayer == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                PlaybackParams params = nativePlayer.getPlaybackParams();
                float spd = currentPlaybackSpeed > 0f ? currentPlaybackSpeed : 1.0f;
                params.setSpeed(spd);
                if (currentMatchPitch && spd != 1.0f) {
                    params.setPitch(spd); // Deep vinyl sound for Slowed
                } else {
                    params.setPitch(1.0f);
                }
                nativePlayer.setPlaybackParams(params);
            } catch (Exception ignored) {}
        }
    }

    public synchronized void handleNextAction() {
        MediaSaverPlugin.dispatchMediaAction("next", null);
    }

    public synchronized void handlePrevAction() {
        MediaSaverPlugin.dispatchMediaAction("prev", null);
    }

    private void parseQueue(String queueJson, int index) {
        try {
            JSONArray arr = new JSONArray(queueJson);
            queue.clear();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                queue.add(new NativeTrackItem(
                    obj.optString("id"),
                    obj.optString("title"),
                    obj.optString("artist"),
                    obj.optString("album"),
                    obj.optString("sourceUrl"),
                    obj.optLong("duration", 0)
                ));
            }
            queueIndex = Math.max(0, Math.min(index, queue.size() - 1));
        } catch (Exception ignored) {}
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
        try {
            mediaSession = new MediaSession(this, "NimiyoMusicSession");
            mediaSession.setCallback(new MediaSession.Callback() {
                @Override
                public void onPlay() {
                    handlePlayPauseAction();
                }

                @Override
                public void onPause() {
                    handlePlayPauseAction();
                }

                @Override
                public void onSkipToNext() {
                    handleNextAction();
                }

                @Override
                public void onSkipToPrevious() {
                    handlePrevAction();
                }

                @Override
                public void onSeekTo(long pos) {
                    seekNativePlayback(pos);
                }

                @Override
                public void onStop() {
                    handleClearAction();
                }
            });

            mediaSession.setFlags(
                MediaSession.FLAG_HANDLES_MEDIA_BUTTONS |
                MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
            );
            mediaSession.setActive(true);
        } catch (Exception ignored) {}
    }

    private void updateMediaMetadata() {
        if (mediaSession == null) return;
        try {
            MediaMetadata.Builder meta = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, currentTitle)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, currentArtist.isEmpty() ? "NIMIYO Music" : currentArtist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, currentAlbum)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, currentDurationMs);

            if (currentArtwork != null) {
                meta.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, currentArtwork);
                meta.putBitmap(MediaMetadata.METADATA_KEY_ART, currentArtwork);
            }

            mediaSession.setMetadata(meta.build());
        } catch (Exception ignored) {}
    }

    private void updatePlaybackState(boolean playing, long positionMs, float speed) {
        if (mediaSession == null) return;
        try {
            long actions = PlaybackState.ACTION_PLAY
                | PlaybackState.ACTION_PAUSE
                | PlaybackState.ACTION_PLAY_PAUSE
                | PlaybackState.ACTION_SKIP_TO_NEXT
                | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                | PlaybackState.ACTION_SEEK_TO
                | PlaybackState.ACTION_STOP;

            int state = playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED;
            float playbackSpeed = playing ? (speed > 0f ? speed : 1.0f) : 0.0f;

            PlaybackState pb = new PlaybackState.Builder()
                .setActions(actions)
                .setState(state, positionMs, playbackSpeed)
                .build();

            mediaSession.setPlaybackState(pb);

            if (wakeLock != null) {
                if (playing && !wakeLock.isHeld()) {
                    wakeLock.acquire(12 * 60 * 60 * 1000L);
                } else if (!playing && wakeLock.isHeld()) {
                    wakeLock.release();
                }
            }
        } catch (Exception ignored) {}
    }

    private void updatePlaybackState(boolean playing, long positionMs) {
        updatePlaybackState(playing, positionMs, currentPlaybackSpeed);
    }

    private void handleUpdateAction(Intent intent) {
        currentTitle = intent.getStringExtra(EXTRA_TITLE);
        if (currentTitle == null || currentTitle.isEmpty()) currentTitle = "NIMIYO";

        currentArtist = intent.getStringExtra(EXTRA_ARTIST);
        if (currentArtist == null) currentArtist = "Unknown Artist";

        currentAlbum = intent.getStringExtra(EXTRA_ALBUM);
        if (currentAlbum == null) currentAlbum = "";

        String sourceUrl = intent.getStringExtra(EXTRA_SOURCE_URL);
        if (sourceUrl != null && !sourceUrl.isEmpty()) {
            currentSourceUrl = sourceUrl;
        }

        String trackId = intent.getStringExtra(EXTRA_TRACK_ID);
        if (trackId != null && !trackId.isEmpty()) {
            currentTrackId = trackId;
        }

        currentDurationMs = intent.getLongExtra(EXTRA_DURATION, currentDurationMs);
        currentPositionMs = intent.getLongExtra(EXTRA_POSITION, currentPositionMs);
        currentPlaybackSpeed = intent.getFloatExtra(EXTRA_PLAYBACK_SPEED, 1.0f);
        if (currentPlaybackSpeed <= 0f) currentPlaybackSpeed = 1.0f;

        String artworkData = intent.getStringExtra(EXTRA_ARTWORK);
        if (artworkData == null || artworkData.isEmpty()) {
            artworkData = pendingArtworkData;
        }
        currentArtwork = loadArtworkBitmap(artworkData);

        this.isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, isPlaying);
        updateMediaMetadata();
        updatePlaybackState(this.isPlaying, currentPositionMs, currentPlaybackSpeed);
        buildAndShowNotification();
    }

    private synchronized void handleClearAction() {
        isPlaying = false;
        progressHandler.removeCallbacks(progressRunnable);

        if (nativePlayer != null) {
            try {
                nativePlayer.setOnCompletionListener(null);
                nativePlayer.setOnPreparedListener(null);
                nativePlayer.setOnErrorListener(null);
                if (nativePlayer.isPlaying()) {
                    nativePlayer.stop();
                }
                nativePlayer.reset();
                nativePlayer.release();
            } catch (Exception ignored) {}
            nativePlayer = null;
        }

        if (presetReverb != null) {
            try { presetReverb.release(); } catch (Exception ignored) {}
            presetReverb = null;
        }

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

        MediaSaverPlugin.dispatchPlaybackProgress(0, 0, false, null);
        stopSelf();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        handleClearAction();
    }

    @Override
    public void onDestroy() {
        progressHandler.removeCallbacks(progressRunnable);
        if (nativePlayer != null) {
            try { nativePlayer.release(); } catch (Exception ignored) {}
            nativePlayer = null;
        }
        if (presetReverb != null) {
            try { presetReverb.release(); } catch (Exception ignored) {}
            presetReverb = null;
        }
        if (mediaSession != null) {
            try {
                mediaSession.setActive(false);
                mediaSession.release();
            } catch (Exception ignored) {}
            mediaSession = null;
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            try { wakeLock.release(); } catch (Exception ignored) {}
        }
        instance = null;
        super.onDestroy();
    }

    private void buildAndShowNotification() {
        if (mediaSession == null) return;

        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
            ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
            : PendingIntent.FLAG_UPDATE_CURRENT;

        // Content Open Intent: Reopen MainActivity
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

        // Delete (Swipe-away) Intent for when paused
        Intent deleteIntent = new Intent(this, MusicPlaybackService.class);
        deleteIntent.setAction(ACTION_DELETE_NOTIFICATION);
        PendingIntent pDeleteIntent = PendingIntent.getService(this, 99, deleteIntent, flags);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, MUSIC_CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        Notification.MediaStyle mediaStyle = new Notification.MediaStyle()
            .setMediaSession(mediaSession.getSessionToken())
            .setShowActionsInCompactView(0, 1, 2);

        int playPauseIcon = isPlaying() ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play;
        String playPauseTitle = isPlaying() ? "Pause" : "Play";

        builder.setStyle(mediaStyle)
            .setSmallIcon(R.drawable.ic_music_note)
            .setLargeIcon(currentArtwork)
            .setContentTitle(currentTitle)
            .setContentText(currentArtist.isEmpty() ? "NIMIYO Music" : currentArtist)
            .setContentIntent(pOpenIntent)
            .setDeleteIntent(pDeleteIntent)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying())
            .setAutoCancel(false)
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_previous, "Previous", pPrevIntent).build())
            .addAction(new Notification.Action.Builder(playPauseIcon, playPauseTitle, pPlayPauseIntent).build())
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_next, "Next", pNextIntent).build());

        if (currentAlbum != null && !currentAlbum.isEmpty()) {
            builder.setSubText(currentAlbum);
        }

        Notification notification = builder.build();

        // Maintain foreground service so Android background execution limits never kill
        // the player or dismiss notification after a few seconds when paused.
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

            // 2. content:// URI
            if (data.startsWith("content://")) {
                Uri uri = Uri.parse(data);
                InputStream is = getContentResolver().openInputStream(uri);
                if (is != null) {
                    Bitmap bmp = BitmapFactory.decodeStream(is);
                    is.close();
                    if (bmp != null) return bmp;
                }
            }

            // 3. File path
            String path = data.startsWith("file://") ? data.substring(7) : data;
            File f = new File(path);
            if (f.exists() && f.isFile()) {
                Bitmap bmp = BitmapFactory.decodeFile(path);
                if (bmp != null) return bmp;
            }
        } catch (Exception ignored) {}

        return getDefaultArtworkBitmap();
    }

    private Bitmap getDefaultArtworkBitmap() {
        int size = 256;
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        canvas.drawColor(0xFF1E1E1E);

        try {
            Drawable d = ContextCompat.getDrawable(this, R.drawable.ic_music_note);
            if (d != null) {
                int pad = 56;
                d.setBounds(pad, pad, size - pad, size - pad);
                d.draw(canvas);
            }
        } catch (Exception ignored) {}

        return bmp;
    }
}
