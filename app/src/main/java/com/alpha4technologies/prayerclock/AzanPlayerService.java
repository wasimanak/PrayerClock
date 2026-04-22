package com.alpha4technologies.prayerclock;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

public class AzanPlayerService extends Service {
    private MediaPlayer mediaPlayer;
    private static final int NOTIFICATION_ID = 1001;
    private static final String TAG = "AzanPlayerService";
    private PowerManager.WakeLock wakeLock;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");
        NotificationHelper.createNotificationChannel(this);

        // Acquire WakeLock
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    "PrayerClock:AzanPlaybackWakeLock");
            wakeLock.acquire(10 * 60 * 1000L /*10 minutes*/);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "===== Service onStartCommand =====");
        
        String prayerName = "Prayer Time";
        if (intent != null && intent.hasExtra("prayer_name")) {
            prayerName = intent.getStringExtra("prayer_name");
        }
        Log.d(TAG, "Prayer: " + prayerName);
        
        // Create PendingIntent for Full Screen
        Intent fullScreenIntent = new Intent(this, MainActivity.class);
        fullScreenIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(this, 0, fullScreenIntent, PendingIntent.FLAG_IMMUTABLE);

        // Create notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NotificationHelper.CHANNEL_ID_AZAN)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Prayer Time " + prayerName)
                .setContentText("حَیَّ عَلَی الصَّلٰوةِ")
                .setPriority(NotificationCompat.PRIORITY_MAX) // High priority
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setContentIntent(fullScreenPendingIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false);
        
        Notification notification = builder.build();
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
            Log.d(TAG, "Foreground started successfully");
        } catch (Exception e) {
            Log.e(TAG, "startForeground failed: " + e.getMessage());
            e.printStackTrace();
            stopSelf();
            return START_NOT_STICKY;
        }
        
        // Play audio
        playAzan();
        
        return START_NOT_STICKY;
    }

    private void playAzan() {
        Log.d(TAG, "playAzan called");
        
        try {
            if (mediaPlayer != null) {
                mediaPlayer.release();
                mediaPlayer = null;
            }

            // Use resource directly or URI? Resource is safer if validated.
            // Using logic to support AudioAttributes
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(getResources().openRawResourceFd(R.raw.adhan));
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
            } else {
                mediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
            }

            mediaPlayer.setVolume(1.0f, 1.0f);
            mediaPlayer.setOnCompletionListener(mp -> {
                Log.d(TAG, "Playback completed");
                stopSelf();
            });
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "MediaPlayer error: " + what);
                stopSelf();
                return true;
            });
            
            mediaPlayer.prepare(); // Synchronous prepare since it's a raw resource
            mediaPlayer.start();
            Log.d(TAG, "Azan started - duration: " + mediaPlayer.getDuration() + "ms");

        } catch (Exception e) {
            Log.e(TAG, "Error in playAzan: " + e.getMessage(), e);
            stopSelf();
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Service destroyed");
        try {
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
                mediaPlayer = null;
            }
            
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onDestroy: " + e.getMessage());
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

