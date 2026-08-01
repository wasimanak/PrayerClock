package com.alpha4technologies.prayerclock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

import com.batoulapps.adhan.Madhab;
import com.batoulapps.adhan.Prayer;
import com.batoulapps.adhan.PrayerTimes;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

public class AzanReceiver extends BroadcastReceiver {
    private static final String TAG = "AzanReceiver";

    // SharedPrefs key prefix used by both AzanReceiver and PrayerAzanWorker
    // When AlarmManager fires: write true → WorkManager sees it and skips duplicate
    private static final String PREF_AZAN_FIRED = "azan_fired_";

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

        SharedPreferences prefs = context.getSharedPreferences("PrayerClockPrefs", Context.MODE_PRIVATE);

        // ── Mark that AlarmManager fired for this prayer ──────────────────────
        // PrayerAzanWorker checks this flag to avoid duplicate azan
        if (prayerName != null) {
            prefs.edit().putBoolean(PREF_AZAN_FIRED + prayerName.toLowerCase(), true).apply();
        }

        // ── Cancel the WorkManager backup worker for THIS prayer ──────────────
        // AlarmManager fired first → worker is no longer needed for this prayer
        if (prayerName != null) {
            WorkManagerHelper.cancelPrayerWorker(context, prayerName);
            Log.d(TAG, "Cancelled WorkManager backup worker for: " + prayerName);
        }

        // ── Mute Check ────────────────────────────────────────────────────────
        if (prayerName != null) {
            boolean isMuted = prefs.getBoolean("mute_azan_" + prayerName.toLowerCase(), false);
            if (isMuted) {
                Log.d(TAG, "MUTED for: " + prayerName);
                // Even if muted, still reschedule next prayer
                scheduleNextEverything(context, prefs);
                if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
                return;
            }
        }

        Log.d(TAG, "Azan alarm fired for: " + prayerName);

        // ── Start Azan service (plays audio + shows full-screen notification) ─
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

        // ── Launch app (most reliable cross-device method) ───────────────────
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
            Log.d(TAG, "SYSTEM_ALERT_WINDOW not granted — relying on fullScreenIntent notification");
        }

        // ── Reschedule next prayer (AlarmManager + WorkManager + Watchdog) ────
        scheduleNextEverything(context, prefs);

        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
    }

    /**
     * اذان کے بعد اگلی prayer کا time get کرو اور:
     *  1. AlarmManager alarm set کرو (scheduleAllAlarms)
     *  2. WorkManager backup worker set کرو (explicit next prayer)
     *  3. Watchdog کو fresh reset کرو (30-min clock restart)
     */
    private void scheduleNextEverything(Context context, SharedPreferences prefs) {
        // 1. Reschedule ALL alarms (AlarmManager + all 5 WorkManager prayer workers)
        AlarmHelper.scheduleAllAlarms(context);

        // 2. Explicit: also schedule only the NEXT prayer's worker directly
        //    (belt-and-suspenders — scheduleAllAlarms already does this,
        //     but we do it again with a fresh calculation to be 100% sure)
        scheduleNextPrayerWorkerExplicit(context, prefs);

        // 3. Reset the 30-minute watchdog so it starts fresh from now
        WorkManagerHelper.rescheduleWatchdog(context);

        // 4. Force update the Foreground Service Notification
        Intent serviceIntent = new Intent(context, PrayerTimerService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }

        Log.d(TAG, "scheduleNextEverything: AlarmManager + Workers + Watchdog + Service all set");
    }

    /**
     * Directly calculate next prayer time and enqueue its WorkManager backup worker.
     */
    private void scheduleNextPrayerWorkerExplicit(Context context, SharedPreferences prefs) {
        String latStr = prefs.getString("current_lat", null);
        String lonStr = prefs.getString("current_lon", null);
        if (latStr == null || lonStr == null) return;

        try {
            double lat = Double.parseDouble(latStr);
            double lon = Double.parseDouble(lonStr);
            String madhabStr = prefs.getString("madhab", "HANAFI");
            Madhab madhab = madhabStr.equals("SHAFI") ? Madhab.SHAFI : Madhab.HANAFI;
            String tzId = prefs.getString("current_timezone", TimeZone.getDefault().getID());
            TimeZone tz = TimeZone.getTimeZone(tzId);

            long now = System.currentTimeMillis();
            PrayerTimes times = PrayerTimeUtil.getPrayerTimes(lat, lon, madhab, tz);
            Prayer nextPrayer = times.nextPrayer();
            Date nextPrayerTime;
            String nextPrayerName;

            if (nextPrayer == Prayer.NONE) {
                // After Isha → tomorrow's Fajr
                Calendar cal = Calendar.getInstance(tz);
                cal.add(Calendar.DAY_OF_YEAR, 1);
                PrayerTimes tomorrow = PrayerTimeUtil.getPrayerTimes(lat, lon, cal.getTime(), madhab, tz);
                nextPrayerTime = tomorrow.fajr;
                nextPrayerName = "Fajr";
            } else {
                nextPrayerTime = times.timeForPrayer(nextPrayer);
                nextPrayerName = PrayerAzanWorker.getPrayerName(nextPrayer);
            }

            if (nextPrayerTime == null) return;

            long delayMs = nextPrayerTime.getTime() - now;
            if (delayMs < 0) delayMs = 0;

            WorkManagerHelper.schedulePrayerWorker(context, nextPrayerName, delayMs);

            Log.d(TAG, "Explicit next prayer worker → " + nextPrayerName
                    + " in " + (delayMs / 60000) + " min");

        } catch (Exception e) {
            Log.e(TAG, "scheduleNextPrayerWorkerExplicit error: " + e.getMessage(), e);
        }
    }
}
