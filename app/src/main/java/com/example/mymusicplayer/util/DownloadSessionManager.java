package com.example.mymusicplayer.util;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.mymusicplayer.R;

import java.util.HashMap;
import java.util.Map;

public class DownloadSessionManager {

    private static final String DOWNLOAD_NOTIFICATION_CHANNEL_ID = "download_channel";
    private static final int DOWNLOAD_NOTIFICATION_ID = 2001;
    private static final long COMPLETION_DISMISS_DELAY_MS = 2500L;
    private static final long PROGRESS_UPDATE_THROTTLE_MS = 400L;

    private static DownloadSessionManager instance;

    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, DownloadTaskState> taskStates = new HashMap<>();

    private long lastNotificationUpdateAt;

    private final Runnable dismissRunnable = this::clearFinishedSession;

    private DownloadSessionManager(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        createDownloadNotificationChannel();
    }

    public static synchronized DownloadSessionManager getInstance(@NonNull Context context) {
        if (instance == null) {
            instance = new DownloadSessionManager(context);
        }
        return instance;
    }

    public synchronized void onDownloadStarted(String trackId, String trackTitle) {
        if (trackId == null || trackId.trim().isEmpty()) {
            return;
        }

        mainHandler.removeCallbacks(dismissRunnable);

        DownloadTaskState taskState = taskStates.get(trackId);
        if (taskState == null) {
            taskStates.put(trackId, new DownloadTaskState(resolveTrackTitle(trackTitle)));
        } else {
            taskState.status = DownloadStatus.IN_PROGRESS;
            taskState.progress = 0;
            taskState.title = resolveTrackTitle(trackTitle);
        }

        showOrUpdateNotification(false);
    }

    public synchronized void onProgress(String trackId, int progress) {
        DownloadTaskState taskState = taskStates.get(trackId);
        if (taskState == null || taskState.status != DownloadStatus.IN_PROGRESS) {
            return;
        }

        taskState.progress = Math.max(0, Math.min(progress, 100));
        long now = System.currentTimeMillis();
        if (now - lastNotificationUpdateAt < PROGRESS_UPDATE_THROTTLE_MS) {
            return;
        }

        showOrUpdateNotification(false);
    }

    public synchronized void onDownloadSuccess(String trackId) {
        finishTask(trackId, DownloadStatus.SUCCESS);
    }

    public synchronized void onDownloadFailed(String trackId) {
        finishTask(trackId, DownloadStatus.FAILED);
    }

    private void finishTask(String trackId, DownloadStatus status) {
        if (trackId == null || trackId.trim().isEmpty()) {
            return;
        }

        DownloadTaskState taskState = taskStates.get(trackId);
        if (taskState == null) {
            taskState = new DownloadTaskState("Unknown track");
            taskStates.put(trackId, taskState);
        }

        taskState.status = status;
        taskState.progress = 100;

        if (getActiveCount() > 0) {
            showOrUpdateNotification(false);
            return;
        }

        showOrUpdateNotification(true);
        mainHandler.removeCallbacks(dismissRunnable);
        mainHandler.postDelayed(dismissRunnable, COMPLETION_DISMISS_DELAY_MS);
    }

    private void showOrUpdateNotification(boolean finished) {
        if (taskStates.isEmpty()) {
            return;
        }

        NotificationManagerCompat.from(appContext).notify(
                DOWNLOAD_NOTIFICATION_ID,
                buildNotification(finished)
        );
        lastNotificationUpdateAt = System.currentTimeMillis();
    }

    private Notification buildNotification(boolean finished) {
        int totalCount = taskStates.size();
        int successCount = getCount(DownloadStatus.SUCCESS);
        int failedCount = getCount(DownloadStatus.FAILED);
        int resolvedCount = successCount + failedCount;
        int progress = calculateOverallProgress(totalCount);
        String activeTrackTitle = getLatestActiveTrackTitle();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                appContext,
                DOWNLOAD_NOTIFICATION_CHANNEL_ID
        )
                .setSmallIcon(R.drawable.ic_notification_logo)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setCategory(finished
                        ? NotificationCompat.CATEGORY_STATUS
                        : NotificationCompat.CATEGORY_PROGRESS);

        if (!finished) {
            String title = totalCount == 1
                    ? "Downloading track"
                    : "Downloading " + totalCount + " tracks";
            String contentText = totalCount == 1
                    ? activeTrackTitle
                    : "Downloaded " + resolvedCount + " of " + totalCount
                    + " • Current: " + activeTrackTitle;

            builder.setContentTitle(title)
                    .setContentText(contentText)
                    .setOngoing(true)
                    .setAutoCancel(false)
                    .setProgress(100, progress, false);
            return builder.build();
        }

        String contentText;
        if (totalCount == 1 && failedCount == 0) {
            contentText = getLatestResolvedTrackTitle();
        } else if (failedCount == 0) {
            contentText = "All tracks downloaded";
        } else {
            contentText = "Downloaded " + successCount + " of " + totalCount + ", some failed";
        }

        builder.setContentTitle("Download complete")
                .setContentText(contentText)
                .setOngoing(false)
                .setAutoCancel(true)
                .setProgress(0, 0, false)
                .setTimeoutAfter(COMPLETION_DISMISS_DELAY_MS);
        return builder.build();
    }

    private int calculateOverallProgress(int totalCount) {
        if (totalCount <= 0) {
            return 0;
        }

        int totalProgress = 0;
        for (DownloadTaskState taskState : taskStates.values()) {
            totalProgress += taskState.progress;
        }
        return Math.max(0, Math.min(100, totalProgress / totalCount));
    }

    private int getActiveCount() {
        return getCount(DownloadStatus.IN_PROGRESS);
    }

    private String getLatestActiveTrackTitle() {
        for (DownloadTaskState taskState : taskStates.values()) {
            if (taskState.status == DownloadStatus.IN_PROGRESS) {
                return taskState.title;
            }
        }

        return getLatestResolvedTrackTitle();
    }

    private String getLatestResolvedTrackTitle() {
        for (DownloadTaskState taskState : taskStates.values()) {
            if (taskState.status == DownloadStatus.SUCCESS
                    || taskState.status == DownloadStatus.FAILED) {
                return taskState.title;
            }
        }

        return "Track";
    }

    private int getCount(DownloadStatus status) {
        int count = 0;
        for (DownloadTaskState taskState : taskStates.values()) {
            if (taskState.status == status) {
                count++;
            }
        }
        return count;
    }

    private void clearFinishedSession() {
        synchronized (this) {
            NotificationManagerCompat.from(appContext).cancel(DOWNLOAD_NOTIFICATION_ID);
            taskStates.clear();
            lastNotificationUpdateAt = 0L;
        }
    }

    private void createDownloadNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                DOWNLOAD_NOTIFICATION_CHANNEL_ID,
                appContext.getString(R.string.notification_channel_downloads),
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription(appContext.getString(R.string.notification_channel_downloads_description));
        channel.setSound(null, null);
        channel.enableVibration(false);

        NotificationManager notificationManager = appContext.getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(channel);
        }
    }

    private enum DownloadStatus {
        IN_PROGRESS,
        SUCCESS,
        FAILED
    }

    private static class DownloadTaskState {
        private String title;
        private DownloadStatus status = DownloadStatus.IN_PROGRESS;
        private int progress;

        private DownloadTaskState(String title) {
            this.title = title;
        }
    }

    private String resolveTrackTitle(String trackTitle) {
        if (trackTitle == null || trackTitle.trim().isEmpty()) {
            return "Unknown track";
        }
        return trackTitle.trim();
    }
}
