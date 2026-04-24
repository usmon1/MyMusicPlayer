package com.example.mymusicplayer.playback;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.media.app.NotificationCompat.MediaStyle;
import androidx.media.session.MediaButtonReceiver;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

import com.example.mymusicplayer.R;
import com.example.mymusicplayer.data.local.entity.Track;
import com.example.mymusicplayer.data.repository.MusicRepository;
import com.example.mymusicplayer.ui.main.MainActivity;
import com.example.mymusicplayer.util.PlaybackAccessHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MusicService extends Service {

    private static final String TAG = "MusicService";
    private static final String NOTIFICATION_CHANNEL_ID = "playback_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final String ACTION_PLAY_PAUSE = "com.example.mymusicplayer.action.PLAY_PAUSE";
    private static final String ACTION_PREVIOUS = "com.example.mymusicplayer.action.PREVIOUS";
    private static final String ACTION_NEXT = "com.example.mymusicplayer.action.NEXT";
    private static final String PREFS_PLAYBACK = "playback_state";
    private static final String PREF_LAST_TRACK_ID = "last_track_id";
    private static final String PREF_LAST_POSITION = "last_position";
    private static final String PREF_PLAYBACK_SOURCE = "playback_source";
    private static final String PREF_PLAYBACK_SOURCE_LABEL = "playback_source_label";
    private static final float DEFAULT_PLAYER_VOLUME = 1.0f;
    private static final float DUCKED_PLAYER_VOLUME = 0.2f;
    private static final long PROGRESS_UPDATE_DELAY_MS = 1000L;

    public static final String PLAYBACK_SOURCE_WAVE = "wave";
    public static final String PLAYBACK_SOURCE_RECENT = "recent";
    public static final String PLAYBACK_SOURCE_LOCAL = "local";
    public static final String PLAYBACK_SOURCE_DOWNLOADED = "downloaded";
    public static final String PLAYBACK_SOURCE_FAVORITES = "favorites";
    public static final String PLAYBACK_SOURCE_PLAYLIST = "playlist";
    public static final String PLAYBACK_SOURCE_SEARCH = "search";

    private final IBinder binder = new MusicBinder();
    private final MutableLiveData<Track> currentTrack = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isPlaying = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> playbackCompletedEvent = new MutableLiveData<>(0);
    private final List<Track> playbackQueue = new ArrayList<>();
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            if (exoPlayer == null) {
                return;
            }

            updateMediaSessionPlaybackState();
            updatePlaybackNotification();
            if (exoPlayer.isPlaying()) {
                progressHandler.postDelayed(this, PROGRESS_UPDATE_DELAY_MS);
            }
        }
    };

    private ExoPlayer exoPlayer;
    private MusicRepository repository;
    private SharedPreferences playbackPreferences;
    private MediaSessionCompat mediaSession;
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private TelephonyManager telephonyManager;
    private PhoneStateListener phoneStateListener;
    private BroadcastReceiver noisyReceiver;

    private int queueIndex = -1;
    private boolean isForegroundActive;
    private boolean noisyReceiverRegistered;
    private boolean shouldResumeOnFocusGain;
    private boolean pausedByCall;
    private String currentPlaybackSource;
    private String currentPlaybackSourceLabel;

    private final AudioManager.OnAudioFocusChangeListener audioFocusChangeListener =
            this::handleAudioFocusChange;

    @Override
    public void onCreate() {
        super.onCreate();

        repository = new MusicRepository(this);
        playbackPreferences = getSharedPreferences(PREFS_PLAYBACK, MODE_PRIVATE);
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);

        createNotificationChannel();
        initializeMediaSession();
        initializeAudioFocusRequest();
        initializeNoisyReceiver();
        initializePhoneStateListener();

        exoPlayer = new ExoPlayer.Builder(this).build();
        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean playing) {
                isPlaying.setValue(playing);
                updateMediaSessionPlaybackState();
                if (playing) {
                    startProgressUpdates();
                    ensureForegroundNotification();
                } else {
                    stopProgressUpdates();
                    handlePlaybackPaused();
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                Log.e(TAG, "Playback error", error);
                shouldResumeOnFocusGain = false;
                isPlaying.setValue(false);
                updateMediaSessionPlaybackState();
                handlePlaybackPaused();
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_ENDED) {
                    Integer currentValue = playbackCompletedEvent.getValue();
                    playbackCompletedEvent.setValue(currentValue == null ? 1 : currentValue + 1);
                }

                updateMediaSessionMetadata();
                updateMediaSessionPlaybackState();
                updatePlaybackNotification();
            }
        });

        restoreLastPlaybackState();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_PLAY_PAUSE.equals(action)) {
            if (isPlayingNow()) {
                pause();
            } else if (getCurrentTrackValue() != null) {
                resume();
            }
        } else if (ACTION_PREVIOUS.equals(action)) {
            playPrevious();
        } else if (ACTION_NEXT.equals(action)) {
            playNext();
        } else if (Intent.ACTION_MEDIA_BUTTON.equals(action) && mediaSession != null) {
            MediaButtonReceiver.handleIntent(mediaSession, intent);
        }

        if (isPlayingNow()) {
            ensureForegroundNotification();
        } else {
            updatePlaybackNotification();
        }

        return START_STICKY;
    }

    public void playTrack(Track track) {
        playTrack(track, 0L);
    }

    public void playTrack(Track track, long startPositionMs) {
        if (track == null || track.getDataUri() == null || track.getDataUri().isEmpty()) {
            Log.e(TAG, "Track has empty dataUri");
            return;
        }

        PlaybackAccessHelper.prepareTrackForPlayback(this, track);
        if (!PlaybackAccessHelper.canPlayTrack(this, track)) {
            Log.w(TAG, "Track playback blocked without network: "
                    + (track.getId() == null ? "unknown" : track.getId()));
            shouldResumeOnFocusGain = false;
            pausePlayerOnly();
            return;
        }

        if (!requestAudioFocus()) {
            Log.w(TAG, "Audio focus denied");
            return;
        }

        shouldResumeOnFocusGain = false;
        pausedByCall = false;
        repository.saveTrack(track);
        repository.updateLastPlayed(track);
        currentTrack.setValue(track);
        updateQueueIndex(track.getId());

        MediaItem mediaItem = MediaItem.fromUri(Uri.parse(track.getDataUri()));
        exoPlayer.setMediaItem(mediaItem);
        exoPlayer.prepare();
        if (startPositionMs > 0L) {
            exoPlayer.seekTo(startPositionMs);
        }
        exoPlayer.setVolume(DEFAULT_PLAYER_VOLUME);
        exoPlayer.play();

        updateMediaSessionMetadata();
        updateMediaSessionPlaybackState();
        ensureForegroundNotification();
        savePlaybackState();
    }

    public void pause() {
        shouldResumeOnFocusGain = false;
        pausedByCall = false;
        pausePlayerOnly();
        abandonAudioFocus();
    }

    public void resume() {
        if (exoPlayer == null || getCurrentTrackValue() == null) {
            return;
        }

        Track track = getCurrentTrackValue();
        PlaybackAccessHelper.prepareTrackForPlayback(this, track);
        if (!PlaybackAccessHelper.canPlayTrack(this, track)) {
            Log.w(TAG, "Resume blocked without network");
            pausePlayerOnly();
            return;
        }

        if (!requestAudioFocus()) {
            Log.w(TAG, "Audio focus denied on resume");
            return;
        }

        shouldResumeOnFocusGain = false;
        pausedByCall = false;
        if (exoPlayer.getCurrentMediaItem() == null) {
            playTrack(track, Math.max(getCurrentPosition(), 0L));
            return;
        }
        exoPlayer.setVolume(DEFAULT_PLAYER_VOLUME);
        exoPlayer.play();
        updateMediaSessionPlaybackState();
        ensureForegroundNotification();
    }

    public void stopPlayback() {
        shouldResumeOnFocusGain = false;
        pausedByCall = false;
        if (exoPlayer != null) {
            exoPlayer.stop();
        }
        isPlaying.setValue(false);
        savePlaybackState();
        updateMediaSessionPlaybackState();
        handlePlaybackPaused();
        abandonAudioFocus();
    }

    public void seekTo(long position) {
        if (exoPlayer != null) {
            exoPlayer.seekTo(position);
            updateMediaSessionPlaybackState();
            updatePlaybackNotification();
            savePlaybackState();
        }
    }

    public void setPlaybackContext(@Nullable List<Track> queue,
                                   @Nullable String currentTrackId,
                                   @Nullable String playbackSource,
                                   @Nullable String playbackSourceLabel) {
        playbackQueue.clear();
        if (queue != null) {
            playbackQueue.addAll(queue);
        }

        queueIndex = -1;
        updateQueueIndex(currentTrackId);
        currentPlaybackSource = playbackSource;
        currentPlaybackSourceLabel = playbackSourceLabel;
        updatePlaybackNotification();
    }

    public void updatePlaybackQueue(@Nullable List<Track> queue, @Nullable String currentTrackId) {
        playbackQueue.clear();
        if (queue != null) {
            playbackQueue.addAll(queue);
        }

        queueIndex = -1;
        updateQueueIndex(currentTrackId);
    }

    public String getCurrentPlaybackSource() {
        return currentPlaybackSource;
    }

    public String getCurrentPlaybackSourceLabel() {
        return currentPlaybackSourceLabel;
    }

    public void playPrevious() {
        Track previousTrack = getAdjacentTrack(-1);
        if (previousTrack != null) {
            playTrack(previousTrack);
        }
    }

    public void playNext() {
        Track nextTrack = getAdjacentTrack(1);
        if (nextTrack != null) {
            playTrack(nextTrack);
        }
    }

    public boolean canPlayPrevious() {
        return getAdjacentTrackPreview(-1) != null;
    }

    public boolean canPlayNext() {
        return getAdjacentTrackPreview(1) != null;
    }

    public LiveData<Track> getCurrentTrack() {
        return currentTrack;
    }

    public LiveData<Boolean> getIsPlaying() {
        return isPlaying;
    }

    public LiveData<Integer> getPlaybackCompletedEvent() {
        return playbackCompletedEvent;
    }

    public Track getCurrentTrackValue() {
        return currentTrack.getValue();
    }

    public boolean isPlayingNow() {
        Boolean value = isPlaying.getValue();
        return value != null && value;
    }

    public long getCurrentPosition() {
        return exoPlayer == null ? 0L : exoPlayer.getCurrentPosition();
    }

    public long getDuration() {
        if (exoPlayer == null) {
            return 0L;
        }

        long duration = exoPlayer.getDuration();
        return duration < 0L ? 0L : duration;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (exoPlayer != null && exoPlayer.isPlaying()) {
            ensureForegroundNotification();
        } else if (getCurrentTrackValue() != null) {
            savePlaybackState();
            showPausedNotification();
        } else {
            savePlaybackState();
            stopForegroundService(true);
            stopSelf();
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        savePlaybackState();
        unregisterNoisyReceiver();
        unregisterPhoneStateListener();
        abandonAudioFocus();
        stopProgressUpdates();

        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
            mediaSession = null;
        }

        if (exoPlayer != null) {
            exoPlayer.release();
            exoPlayer = null;
        }

        stopForegroundService(true);
        super.onDestroy();
    }

    private void initializeMediaSession() {
        mediaSession = new MediaSessionCompat(this, TAG);
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                resume();
            }

            @Override
            public void onPause() {
                pause();
            }

            @Override
            public void onSkipToNext() {
                playNext();
            }

            @Override
            public void onSkipToPrevious() {
                playPrevious();
            }

            @Override
            public void onSeekTo(long pos) {
                seekTo(pos);
            }
        });
        mediaSession.setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
                        | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        );
        mediaSession.setActive(true);
    }

    private void initializeAudioFocusRequest() {
        if (audioManager == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();

        audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .setWillPauseWhenDucked(false)
                .build();
    }

    private void initializeNoisyReceiver() {
        noisyReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())
                        && exoPlayer != null
                        && exoPlayer.isPlaying()) {
                    shouldResumeOnFocusGain = false;
                    pausePlayerOnly();
                }
            }
        };

        IntentFilter noisyFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(noisyReceiver, noisyFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(noisyReceiver, noisyFilter);
        }
        noisyReceiverRegistered = true;
    }

    private void initializePhoneStateListener() {
        telephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        if (telephonyManager == null) {
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        phoneStateListener = new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String phoneNumber) {
                if (state == TelephonyManager.CALL_STATE_RINGING
                        || state == TelephonyManager.CALL_STATE_OFFHOOK) {
                    if (exoPlayer != null && exoPlayer.isPlaying()) {
                        pausedByCall = true;
                        shouldResumeOnFocusGain = false;
                        pausePlayerOnly();
                    }
                } else if (state == TelephonyManager.CALL_STATE_IDLE) {
                    pausedByCall = false;
                    shouldResumeOnFocusGain = false;
                }
            }
        };

        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
    }

    private void unregisterPhoneStateListener() {
        if (telephonyManager != null && phoneStateListener != null) {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
            phoneStateListener = null;
        }
    }

    private boolean requestAudioFocus() {
        if (audioManager == null) {
            return true;
        }

        int result;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
            result = audioManager.requestAudioFocus(audioFocusRequest);
        } else {
            result = audioManager.requestAudioFocus(
                    audioFocusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
            );
        }

        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private void abandonAudioFocus() {
        if (audioManager == null) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest);
        } else {
            audioManager.abandonAudioFocus(audioFocusChangeListener);
        }
    }

    private void handleAudioFocusChange(int focusChange) {
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
            shouldResumeOnFocusGain = false;
            pausePlayerOnly();
            abandonAudioFocus();
            return;
        }

        if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            boolean wasPlaying = exoPlayer != null && exoPlayer.isPlaying();
            shouldResumeOnFocusGain = wasPlaying && !pausedByCall;
            pausePlayerOnly();
            return;
        }

        if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
            if (exoPlayer != null) {
                exoPlayer.setVolume(DUCKED_PLAYER_VOLUME);
            }
            return;
        }

        if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
            if (exoPlayer != null) {
                exoPlayer.setVolume(DEFAULT_PLAYER_VOLUME);
            }

            if (shouldResumeOnFocusGain && !pausedByCall && exoPlayer != null
                    && getCurrentTrackValue() != null) {
                shouldResumeOnFocusGain = false;
                exoPlayer.play();
                ensureForegroundNotification();
            }
        }
    }

    private void pausePlayerOnly() {
        if (exoPlayer == null) {
            return;
        }

        if (exoPlayer.isPlaying() || exoPlayer.getPlayWhenReady()) {
            exoPlayer.pause();
        }

        savePlaybackState();
        updateMediaSessionPlaybackState();
        handlePlaybackPaused();
    }

    private void handlePlaybackPaused() {
        updateMediaSessionPlaybackState();
        savePlaybackState();
        stopProgressUpdates();
        showPausedNotification();
    }

    private void restoreLastPlaybackState() {
        String trackId = playbackPreferences.getString(PREF_LAST_TRACK_ID, null);
        if (trackId == null || trackId.trim().isEmpty()) {
            return;
        }

        long position = playbackPreferences.getLong(PREF_LAST_POSITION, 0L);
        new Thread(() -> {
            Track track = repository.getTrackByIdSync(trackId);
            if (track == null || track.getDataUri() == null || track.getDataUri().trim().isEmpty()) {
                return;
            }

            progressHandler.post(() -> {
                if (exoPlayer == null) {
                    return;
                }

                currentTrack.setValue(track);
                if (PlaybackAccessHelper.canPlayTrack(this, track)) {
                    exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(track.getDataUri())));
                    exoPlayer.prepare();
                    if (position > 0L) {
                        exoPlayer.seekTo(position);
                    }
                }
                currentPlaybackSource = playbackPreferences.getString(PREF_PLAYBACK_SOURCE, null);
                currentPlaybackSourceLabel =
                        playbackPreferences.getString(PREF_PLAYBACK_SOURCE_LABEL, null);
                isPlaying.setValue(false);
                updateMediaSessionMetadata();
                updateMediaSessionPlaybackState();
            });
        }).start();
    }

    private void savePlaybackState() {
        Track track = currentTrack.getValue();
        SharedPreferences.Editor editor = playbackPreferences.edit();
        if (track == null || track.getId() == null || track.getId().trim().isEmpty()) {
            editor.remove(PREF_LAST_TRACK_ID);
            editor.remove(PREF_LAST_POSITION);
            editor.remove(PREF_PLAYBACK_SOURCE);
            editor.remove(PREF_PLAYBACK_SOURCE_LABEL);
        } else {
            editor.putString(PREF_LAST_TRACK_ID, track.getId());
            editor.putLong(PREF_LAST_POSITION, Math.max(getCurrentPosition(), 0L));
            editor.putString(PREF_PLAYBACK_SOURCE, currentPlaybackSource);
            editor.putString(PREF_PLAYBACK_SOURCE_LABEL, currentPlaybackSourceLabel);
        }
        editor.apply();
    }

    private void updateQueueIndex(@Nullable String trackId) {
        if (trackId == null || trackId.trim().isEmpty()) {
            queueIndex = -1;
            return;
        }

        for (int index = 0; index < playbackQueue.size(); index++) {
            Track track = playbackQueue.get(index);
            if (track != null && trackId.equals(track.getId())) {
                queueIndex = index;
                return;
            }
        }
    }

    @Nullable
    private Track getAdjacentTrack(int direction) {
        Track targetTrack = getAdjacentTrackPreview(direction);
        if (targetTrack != null) {
            queueIndex += direction;
        }
        return targetTrack;
    }

    @Nullable
    private Track getAdjacentTrackPreview(int direction) {
        if (playbackQueue.isEmpty()) {
            return null;
        }

        Track playingTrack = currentTrack.getValue();
        if (queueIndex < 0 && playingTrack != null) {
            updateQueueIndex(playingTrack.getId());
        }

        if (queueIndex < 0) {
            return null;
        }

        int targetIndex = queueIndex + direction;
        if (targetIndex < 0 || targetIndex >= playbackQueue.size()) {
            return null;
        }

        return playbackQueue.get(targetIndex);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_playback),
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription(getString(R.string.notification_channel_playback_description));

        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void ensureForegroundNotification() {
        Notification notification = buildNotification();
        if (!isForegroundActive) {
            startForeground(NOTIFICATION_ID, notification);
            isForegroundActive = true;
        } else {
            NotificationManager notificationManager =
                    (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.notify(NOTIFICATION_ID, notification);
            }
        }
    }

    private void stopForegroundService(boolean removeNotification) {
        if (isForegroundActive) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(removeNotification
                        ? STOP_FOREGROUND_REMOVE
                        : STOP_FOREGROUND_DETACH);
            } else {
                stopForeground(removeNotification);
            }
            isForegroundActive = false;
        }

        if (removeNotification) {
            NotificationManager notificationManager =
                    (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.cancel(NOTIFICATION_ID);
            }
        }
    }

    private void updatePlaybackNotification() {
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            return;
        }

        if (isForegroundActive) {
            notificationManager.notify(NOTIFICATION_ID, buildNotification());
        }
    }

    private void showPausedNotification() {
        stopForegroundService(false);

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (notificationManager != null && getCurrentTrackValue() != null) {
            notificationManager.notify(NOTIFICATION_ID, buildNotification());
        }
    }

    private Notification buildNotification() {
        Intent openAppIntent = new Intent(this, MainActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent contentIntent = PendingIntent.getActivity(
                this,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        PendingIntent previousIntent = PendingIntent.getService(
                this,
                1,
                new Intent(this, MusicService.class).setAction(ACTION_PREVIOUS),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        PendingIntent playPauseIntent = PendingIntent.getService(
                this,
                2,
                new Intent(this, MusicService.class).setAction(ACTION_PLAY_PAUSE),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        PendingIntent nextIntent = PendingIntent.getService(
                this,
                3,
                new Intent(this, MusicService.class).setAction(ACTION_NEXT),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Track track = currentTrack.getValue();
        String title = track != null && track.getTitle() != null && !track.getTitle().trim().isEmpty()
                ? track.getTitle()
                : getString(R.string.notification_no_track);
        String artist = track != null && track.getArtist() != null && !track.getArtist().trim().isEmpty()
                ? track.getArtist()
                : getString(R.string.no_artist);
        boolean playing = isPlayingNow();
        long duration = getDuration();
        long position = getCurrentPosition();

        return new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(artist)
                .setSubText(getString(
                        R.string.notification_progress,
                        formatTime(position),
                        formatTime(duration)
                ))
                .setContentIntent(contentIntent)
                .setOnlyAlertOnce(true)
                .setOngoing(playing)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setProgress((int) Math.max(duration, 1L), (int) Math.min(position, duration), false)
                .addAction(0, getString(R.string.notification_action_previous), previousIntent)
                .addAction(
                        0,
                        getString(playing ? R.string.pause : R.string.notification_action_resume),
                        playPauseIntent
                )
                .addAction(0, getString(R.string.notification_action_next), nextIntent)
                .setStyle(new MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2))
                .build();
    }

    private void updateMediaSessionMetadata() {
        if (mediaSession == null) {
            return;
        }

        Track track = currentTrack.getValue();
        MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder();
        builder.putString(
                MediaMetadataCompat.METADATA_KEY_TITLE,
                track != null ? track.getTitle() : getString(R.string.notification_no_track)
        );
        builder.putString(
                MediaMetadataCompat.METADATA_KEY_ARTIST,
                track != null ? track.getArtist() : ""
        );
        builder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, getDuration());
        mediaSession.setMetadata(builder.build());
    }

    private void updateMediaSessionPlaybackState() {
        if (mediaSession == null) {
            return;
        }

        long actions = PlaybackStateCompat.ACTION_PLAY
                | PlaybackStateCompat.ACTION_PAUSE
                | PlaybackStateCompat.ACTION_PLAY_PAUSE
                | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                | PlaybackStateCompat.ACTION_SEEK_TO;

        int state = isPlayingNow()
                ? PlaybackStateCompat.STATE_PLAYING
                : PlaybackStateCompat.STATE_PAUSED;

        mediaSession.setPlaybackState(new PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(state, getCurrentPosition(), isPlayingNow() ? 1f : 0f)
                .build());
    }

    private String formatTime(long durationMs) {
        long totalSeconds = Math.max(durationMs, 0L) / 1000L;
        return String.format(Locale.getDefault(), "%02d:%02d", totalSeconds / 60L, totalSeconds % 60L);
    }

    private void startProgressUpdates() {
        progressHandler.removeCallbacks(progressRunnable);
        progressHandler.post(progressRunnable);
    }

    private void stopProgressUpdates() {
        progressHandler.removeCallbacks(progressRunnable);
    }

    private void unregisterNoisyReceiver() {
        if (noisyReceiverRegistered && noisyReceiver != null) {
            unregisterReceiver(noisyReceiver);
            noisyReceiverRegistered = false;
        }
    }

    public class MusicBinder extends Binder {
        public MusicService getService() {
            return MusicService.this;
        }
    }
}
