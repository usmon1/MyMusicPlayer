package com.example.mymusicplayer.playback;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

import com.example.mymusicplayer.data.local.entity.Track;

/**
 * Bound service that owns ExoPlayer and playback state.
 */
public class MusicService extends Service {

    private static final String TAG = "MusicService";
    private final IBinder binder = new MusicBinder();
    private final MutableLiveData<Track> currentTrack = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isPlaying = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> playbackCompletedEvent = new MutableLiveData<>(0);

    private ExoPlayer exoPlayer;

    @Override
    public void onCreate() {
        super.onCreate();

        exoPlayer = new ExoPlayer.Builder(this).build();
        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean playing) {
                isPlaying.postValue(playing);
            }

            @Override
            public void onPlayerError(androidx.media3.common.PlaybackException error) {
                Log.e(TAG, "Playback error", error);
                isPlaying.postValue(false);
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_ENDED) {
                    Integer currentValue = playbackCompletedEvent.getValue();
                    playbackCompletedEvent.postValue(currentValue == null ? 1 : currentValue + 1);
                }
            }
        });
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void playTrack(Track track) {
        if (track == null || track.getDataUri() == null || track.getDataUri().isEmpty()) {
            Log.e(TAG, "Track has empty dataUri");
            return;
        }

        currentTrack.setValue(track);
        Log.d(TAG, "Playing track url: " + track.getDataUri());
        MediaItem mediaItem = MediaItem.fromUri(Uri.parse(track.getDataUri()));
        exoPlayer.setMediaItem(mediaItem);
        exoPlayer.prepare();
        exoPlayer.play();
    }

    public void pause() {
        if (exoPlayer != null) {
            exoPlayer.pause();
        }
    }

    public void resume() {
        if (exoPlayer != null) {
            exoPlayer.play();
        }
    }

    public void seekTo(long position) {
        if (exoPlayer != null) {
            exoPlayer.seekTo(position);
        }
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
        if (exoPlayer == null) {
            return 0L;
        }

        return exoPlayer.getCurrentPosition();
    }

    public long getDuration() {
        if (exoPlayer == null) {
            return 0L;
        }

        long duration = exoPlayer.getDuration();
        return duration < 0L ? 0L : duration;
    }

    @Override
    public void onDestroy() {
        if (exoPlayer != null) {
            exoPlayer.release();
            exoPlayer = null;
        }
        super.onDestroy();
    }

    public class MusicBinder extends Binder {
        public MusicService getService() {
            return MusicService.this;
        }
    }
}
