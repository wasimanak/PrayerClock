package com.alpha4technologies.prayerclock;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.batoulapps.adhan.Madhab;
import com.batoulapps.adhan.Prayer;
import com.batoulapps.adhan.PrayerTimes;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * PrayerAzanWorker — WorkManager-based azan trigger.
 *
 * Role: BACKUP layer to AlarmManager.
 *  - If AlarmManager fires first → AzanReceiver cancels this worker tag → no duplicate.
 *  - If AlarmManager is killed by OS (Xiaomi/Samsung/Oppo etc.) → this worker fires → plays azan.
 *
 * Chain:
 *   scheduleFor(prayer) → wait delay → doWork() → play azan → scheduleFor(nextPrayer)
 */
public class PrayerAzanWorker extends Worker {

    private static final String TAG = "PrayerAzanWorker";

    // Input data key
    public static final String KEY_PRAYER_NAME = "prayer_name";

    // SharedPrefs key to track whether AlarmManager already fired for this prayer
    // AzanReceiver writes true when it fires; we check this before playing
    private static final String PREF_AZAN_FIRED = "azan_fired_";

    public PrayerAzanWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        String prayerName = getInputData().getString(KEY_PRAYER_NAME);
        if (prayerName == null) prayerName = "Prayer";

        Log.d(TAG, "doWork() fired for: " + prayerName);

        SharedPreferences prefs = context.getSharedPreferences("PrayerClockPrefs", Context.MODE_PRIVATE);

        // ── Check if AlarmManager already handled this prayer ─────────────────
        String firedKey = PREF_AZAN_FIRED + prayerName.toLowerCase();
        boolean alreadyFired = prefs.getBoolean(firedKey, false);

        if (alreadyFired) {
            // AlarmManager already played azan — just clear the flag and move on
            Log.d(TAG, prayerName + " azan already played by AlarmManager — skipping duplicate");
            prefs.edit().remove(firedKey).apply();
        } else {
            // AlarmManager did NOT fire — we are the actual trigger (backup activated)
            Log.w(TAG, prayerName + " AlarmManager did NOT fire — WorkManager playing azan as backup!");
            playAzan(context, prayerName, prefs);
        }

        // ── Schedule next prayer worker ────────────────────────────────────────
        scheduleNextPrayerWorker(context, prefs);

        return Result.success();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Play azan via AzanPlayerService
    // ─────────────────────────────────────────────────────────────────────────
    private void playAzan(Context context, String prayerName, SharedPreferences prefs) {
        // Mute check
        boolean isMuted = prefs.getBoolean("mute_azan_" + prayerName.toLowerCase(), false);
        if (isMuted) {
            Log.d(TAG, "MUTED for: " + prayerName + " — skipping");
            return;
        }

        try {
            Intent serviceIntent = new Intent(context, AzanPlayerService.class);
            serviceIntent.putExtra("prayer_name", prayerName);
            serviceIntent.putExtra("from_workmanager", true);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
            Log.d(TAG, "AzanPlayerService started from WorkManager for: " + prayerName);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start AzanPlayerService: " + e.getMessage());
        }

        // Launch app
        try {
            Intent launchIntent = new Intent(context, MainActivity.class);
            launchIntent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT |
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            );
            launchIntent.putExtra("prayer_name", prayerName);
            context.startActivity(launchIntent);
            Log.d(TAG, "MainActivity launched from WorkManager");
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch MainActivity: " + e.getMessage());
        }

        // Reschedule AlarmManager alarms as well (in case they were cleared)
        AlarmHelper.scheduleAllAlarms(context);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Calculate next prayer and schedule its worker
    // ─────────────────────────────────────────────────────────────────────────
    private void scheduleNextPrayerWorker(Context context, SharedPreferences prefs) {
        String latStr = prefs.getString("current_lat", null);
        String lonStr = prefs.getString("current_lon", null);
        if (latStr == null || lonStr == null) {
            Log.w(TAG, "Location not available — cannot schedule next prayer worker");
            return;
        }

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
                // After Isha → next is tomorrow's Fajr
                Calendar cal = Calendar.getInstance(tz);
                cal.add(Calendar.DAY_OF_YEAR, 1);
                PrayerTimes tomorrowTimes = PrayerTimeUtil.getPrayerTimes(lat, lon, cal.getTime(), madhab, tz);
                nextPrayerTime = tomorrowTimes.fajr;
                nextPrayerName = "Fajr";
            } else {
                nextPrayerTime = times.timeForPrayer(nextPrayer);
                nextPrayerName = getPrayerName(nextPrayer);
            }

            if (nextPrayerTime == null) {
                Log.e(TAG, "Next prayer time is null");
                return;
            }

            long delayMs = nextPrayerTime.getTime() - now;
            if (delayMs < 0) delayMs = 0;

            // Cancel old worker for this prayer and schedule fresh one
            WorkManagerHelper.schedulePrayerWorker(context, nextPrayerName, delayMs);

            Log.d(TAG, "Scheduled next prayer worker: " + nextPrayerName
                    + " in " + (delayMs / 60000) + " minutes");

        } catch (Exception e) {
            Log.e(TAG, "Error scheduling next prayer worker: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────
    static String getPrayerName(Prayer prayer) {
        switch (prayer) {
            case FAJR:    return "Fajr";
            case DHUHR:   return "Dhuhr";
            case ASR:     return "Asr";
            case MAGHRIB: return "Maghrib";
            case ISHA:    return "Isha";
            default:      return "Prayer";
        }
    }

    /**
     * Schedule a one-time WorkManager worker for the given prayer.
     * Called externally from AlarmHelper and AzanReceiver.
     */
    public static void scheduleFor(Context context, String prayerName, long delayMs) {
        // Add a small buffer (10 seconds) so AlarmManager fires first if both are alive
        long adjustedDelay = delayMs + 10_000L;
        if (adjustedDelay < 0) adjustedDelay = 0;

        Data inputData = new Data.Builder()
                .putString(KEY_PRAYER_NAME, prayerName)
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(PrayerAzanWorker.class)
                .setInitialDelay(adjustedDelay, TimeUnit.MILLISECONDS)
                .addTag(WorkManagerHelper.getPrayerTag(prayerName))
                .setInputData(inputData)
                .build();

        // REPLACE: always use fresh schedule
        WorkManager.getInstance(context).enqueueUniqueWork(
                WorkManagerHelper.getPrayerTag(prayerName),
                androidx.work.ExistingWorkPolicy.REPLACE,
                request
        );

        Log.d(TAG, "Enqueued prayer worker for: " + prayerName
                + " with delay: " + (adjustedDelay / 60000) + " min");
    }
}
