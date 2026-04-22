package com.pksofter.prayerclock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

public class AzanReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null) {
            String prayerName = intent.getStringExtra("prayer_name");
            
            // Acquire WakeLock to prevent Deep Sleep before service starts
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            PowerManager.WakeLock wakeLock = null;
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PrayerClock::AzanWakeLock");
                wakeLock.acquire(10000); // 10 seconds max
            }

            // Mute Check
            if (prayerName != null) {
                android.content.SharedPreferences prefs = context.getSharedPreferences("PrayerClockPrefs", Context.MODE_PRIVATE);
                boolean isMuted = prefs.getBoolean("mute_azan_" + prayerName.toLowerCase(), false);
                if (isMuted) {
                    Log.d("AzanReceiver", "Azan Alarm Triggered but MUTED for: " + prayerName);
                    if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
                    return; // Skip playback
                }
            }

            Log.d("AzanReceiver", "Received Alarm for: " + prayerName);

            Intent serviceIntent = new Intent(context, AzanPlayerService.class);
            serviceIntent.putExtra("prayer_name", prayerName);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    context.startForegroundService(serviceIntent);
                } catch (Exception e) {
                    Log.e("AzanReceiver", "Failed to start foreground service: " + e.getMessage());
                }
            } else {
                context.startService(serviceIntent);
            }
            
            // Schedule next alarms as a safeguard
            AlarmHelper.scheduleAllAlarms(context);
            
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        }
    }
}
