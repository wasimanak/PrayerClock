package com.alpha4technologies.prayerclock;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class AzanPlayerService extends Service implements AudioManager.OnAudioFocusChangeListener {
    public static final String ACTION_STOP_AZAN = "com.alpha4technologies.prayerclock.ACTION_STOP_AZAN";
    public static boolean isPlaying = false;

    private MediaPlayer mediaPlayer;
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private static final int NOTIFICATION_ID = 2002;
    private static final String TAG = "AzanPlayerService";
    private PowerManager.WakeLock wakeLock;
    private BroadcastReceiver stopReceiver;
    private boolean isReceiverRegistered = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");
        NotificationHelper.createNotificationChannel(this);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        // Acquire WakeLock
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    "PrayerClock:AzanPlaybackWakeLock");
            wakeLock.acquire(10 * 60 * 1000L /*10 minutes*/);
        }

        // Register Receiver for Power button (Screen Off) and Stop Azan action
        stopReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null) return;
                String action = intent.getAction();
                Log.d(TAG, "stopReceiver caught action: " + action);
                if (Intent.ACTION_SCREEN_OFF.equals(action)
                        || ACTION_STOP_AZAN.equals(action)) {
                    stopSelf();
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(ACTION_STOP_AZAN);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stopReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(stopReceiver, filter);
        }
        isReceiverRegistered = true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "===== Service onStartCommand =====");

        if (intent != null && ACTION_STOP_AZAN.equals(intent.getAction())) {
            Log.d(TAG, "Action STOP_AZAN received via intent");
            stopSelf();
            return START_NOT_STICKY;
        }

        String prayerName = "Prayer Time";
        if (intent != null && intent.hasExtra("prayer_name")) {
            prayerName = intent.getStringExtra("prayer_name");
        }
        Log.d(TAG, "Prayer: " + prayerName);

        // Create PendingIntent for Full Screen (Lock Screen Launch)
        Intent fullScreenIntent = new Intent(this, MainActivity.class);
        fullScreenIntent.setFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK |
            Intent.FLAG_ACTIVITY_CLEAR_TOP |
            Intent.FLAG_ACTIVITY_SINGLE_TOP
        );
        fullScreenIntent.putExtra("prayer_name", prayerName);
        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(
            this, 0, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Create Stop PendingIntent for Notification Button
        Intent stopIntent = new Intent(this, AzanPlayerService.class);
        stopIntent.setAction(ACTION_STOP_AZAN);
        PendingIntent stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Create notification explicitly setting sound to NULL and defaults to 0
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NotificationHelper.CHANNEL_ID_AZAN)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("الله اكبر — " + prayerName + " Prayer Time")
                .setContentText("حَیَّ عَلی الصَّلٰوہِ — Come to prayer")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setContentIntent(fullScreenPendingIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setSound(null)
                .setDefaults(0)
                .setVibrate(new long[]{0L})
                .setAutoCancel(false)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "اذان بند کریں (Stop Azan)", stopPendingIntent);

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

    private void requestAudioFocus() {
        if (audioManager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes playbackAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();

            audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener(this)
                    .build();

            audioManager.requestAudioFocus(audioFocusRequest);
        } else {
            audioManager.requestAudioFocus(this, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        }
    }

    private void abandonAudioFocus() {
        if (audioManager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (audioFocusRequest != null) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest);
            }
        } else {
            audioManager.abandonAudioFocus(this);
        }
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            Log.d(TAG, "Audio focus lost — stopping Azan");
            stopSelf();
        }
    }

    private void playAzan() {
        Log.d(TAG, "playAzan called");

        try {
            if (mediaPlayer != null) {
                mediaPlayer.release();
                mediaPlayer = null;
            }

            requestAudioFocus();

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

            mediaPlayer.prepare();
            mediaPlayer.start();
            isPlaying = true;
            Log.d(TAG, "Azan started - duration: " + mediaPlayer.getDuration() + "ms");

        } catch (Exception e) {
            Log.e(TAG, "Error in playAzan: " + e.getMessage(), e);
            stopSelf();
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Service destroyed");
        isPlaying = false;
        try {
            if (isReceiverRegistered && stopReceiver != null) {
                unregisterReceiver(stopReceiver);
                isReceiverRegistered = false;
            }

            abandonAudioFocus();

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
