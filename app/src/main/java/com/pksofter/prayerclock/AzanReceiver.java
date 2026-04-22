package com.pksofter.prayerclock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

public class AzanReceiver extends BroadcastReceiver {
    private static final String TAG = "AzanReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        String prayerName = intent.getStringExtra("prayer_name");

        // Wake the screen immediately
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = null;
        if (pm != null) {
            wakeLock = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK |
                PowerManager.ACQUIRE_CAUSES_WAKEUP |
                PowerManager.ON_AFTER_RELEASE,
                "PrayerClock::AzanWakeLock"
            );
            wakeLock.acquire(20000); // 20 seconds
        }

        // Mute Check
        if (prayerName != null) {
            android.content.SharedPreferences prefs =
                context.getSharedPreferences("PrayerClockPrefs", Context.MODE_PRIVATE);
            boolean isMuted = prefs.getBoolean("mute_azan_" + prayerName.toLowerCase(), false);
            if (isMuted) {
                Log.d(TAG, "MUTED for: " + prayerName);
                if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
                return;
            }
        }

        Log.d(TAG, "Azan alarm fired for: " + prayerName);

        // Start Azan service (plays audio + shows full-screen notification)
        Intent serviceIntent = new Intent(context, AzanPlayerService.class);
        serviceIntent.putExtra("prayer_name", prayerName);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                context.startForegroundService(serviceIntent);
            } catch (Exception e) {
                Log.e(TAG, "Failed to start foreground service: " + e.getMessage());
            }
        } else {
            context.startService(serviceIntent);
        }

        // Launch app directly using SYSTEM_ALERT_WINDOW (most reliable cross-device method)
        // This works even on lock screen on Android 10+ when permission is granted
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                Settings.canDrawOverlays(context)) {
            try {
                Intent launchIntent = new Intent(context, MainActivity.class);
                launchIntent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT |
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                );
                launchIntent.putExtra("prayer_name", prayerName);
                context.startActivity(launchIntent);
                Log.d(TAG, "App launched via startActivity (SYSTEM_ALERT_WINDOW granted)");
            } catch (Exception e) {
                Log.e(TAG, "startActivity failed: " + e.getMessage());
            }
        } else {
            // SYSTEM_ALERT_WINDOW not granted — fallback to fullScreenIntent notification
            Log.d(TAG, "SYSTEM_ALERT_WINDOW not granted — relying on fullScreenIntent notification");
        }

        // Reschedule alarms for next prayer
        AlarmHelper.scheduleAllAlarms(context);

        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
    }
}
