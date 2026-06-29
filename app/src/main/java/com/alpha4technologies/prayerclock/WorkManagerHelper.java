package com.alpha4technologies.prayerclock;

import android.content.Context;
import android.util.Log;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public class WorkManagerHelper {

    private static final String TAG = "WorkManagerHelper";

    // ── Watchdog (periodic 4-hour reschedule) ─────────────────────────────────
    public static final String WATCHDOG_WORK_NAME = "alarm_watchdog_work";

    public static void scheduleWatchdog(Context context) {
        // Run every 4 hours to ensure alarms survive deep sleep, force stops, and reboots
        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
                AlarmWatchdogWorker.class,
                4, TimeUnit.HOURS)
                .addTag("alarm_watchdog")
                .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WATCHDOG_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP, // KEEP prevents self-cancellation loop when called from within the worker
                workRequest
        );

        Log.d(TAG, "Scheduled Periodic Alarm Watchdog (every 4 hours)");
    }

    public static void cancelWatchdog(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(WATCHDOG_WORK_NAME);
    }

    // ── Prayer-specific OneTime workers ───────────────────────────────────────

    /**
     * Returns the unique work name/tag for a given prayer.
     * e.g. "prayer_azan_Fajr"
     */
    public static String getPrayerTag(String prayerName) {
        return "prayer_azan_" + prayerName;
    }

    /**
     * Schedule (or reschedule) a one-time backup WorkManager worker for the given prayer.
     *
     * @param context    App context
     * @param prayerName e.g. "Fajr", "Dhuhr", "Asr", "Maghrib", "Isha"
     * @param delayMs    Milliseconds until the prayer time from now
     */
    public static void schedulePrayerWorker(Context context, String prayerName, long delayMs) {
        // Cancel previous worker for this prayer before scheduling new one
        cancelPrayerWorker(context, prayerName);

        // PrayerAzanWorker.scheduleFor adds 10s buffer internally
        PrayerAzanWorker.scheduleFor(context, prayerName, delayMs);

        Log.d(TAG, "schedulePrayerWorker: " + prayerName + " in ~" + (delayMs / 60000) + " min");
    }

    /**
     * Cancel the WorkManager backup worker for a specific prayer.
     * Called by AzanReceiver when AlarmManager fires first (prevent duplicate azan).
     */
    public static void cancelPrayerWorker(Context context, String prayerName) {
        String tag = getPrayerTag(prayerName);
        WorkManager.getInstance(context).cancelUniqueWork(tag);
        Log.d(TAG, "Cancelled prayer worker for: " + prayerName);
    }

    /**
     * Cancel ALL prayer workers (all 5 prayers).
     * Useful on settings change or location change.
     */
    public static void cancelAllPrayerWorkers(Context context) {
        String[] prayers = {"Fajr", "Dhuhr", "Asr", "Maghrib", "Isha"};
        for (String prayer : prayers) {
            cancelPrayerWorker(context, prayer);
        }
        Log.d(TAG, "Cancelled all prayer workers");
    }
}
