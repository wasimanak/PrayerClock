package com.alpha4technologies.prayerclock;

import android.content.Context;
import android.util.Log;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public class WorkManagerHelper {

    private static final String TAG = "WorkManagerHelper";

    // ── Periodic Watchdog (every 30 minutes) ──────────────────────────────────
    //
    // ہر 30 منٹ بعد AlarmWatchdogWorker چلتا ہے جو:
    //  • AlarmManager alarms refresh کرتا ہے
    //  • تمام 5 نمازوں کے backup workers reschedule کرتا ہے
    //  • اگر app sleep/stop ہو تو بھی اگلی اذان miss نہیں ہوگی
    //
    public static final String WATCHDOG_WORK_NAME = "alarm_watchdog_work";

    public static void scheduleWatchdog(Context context) {
        // WorkManager minimum periodic interval = 15 minutes.
        // 30 minutes is ideal — frequent enough to catch missed alarms,
        // light enough not to drain battery.
        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
                AlarmWatchdogWorker.class,
                30, TimeUnit.MINUTES)
                .addTag("alarm_watchdog")
                .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
                .build();

        // KEEP: if watchdog is already scheduled, don't reset its timer.
        // This prevents a loop where the worker re-schedules itself and resets the 30-min clock.
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WATCHDOG_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
        );

        Log.d(TAG, "Watchdog scheduled/kept (runs every 30 minutes)");
    }

    /**
     * Force-replace the watchdog (e.g. after settings change) so it resets its 30-min clock.
     */
    public static void rescheduleWatchdog(Context context) {
        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
                AlarmWatchdogWorker.class,
                30, TimeUnit.MINUTES)
                .addTag("alarm_watchdog")
                .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
                .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WATCHDOG_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,   // UPDATE replaces with fresh timer
                workRequest
        );

        Log.d(TAG, "Watchdog REPLACED with fresh 30-min timer");
    }

    public static void cancelWatchdog(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(WATCHDOG_WORK_NAME);
        Log.d(TAG, "Watchdog cancelled");
    }

    // ── Prayer-specific OneTime Backup Workers ────────────────────────────────
    //
    // ہر اذان کے وقت یہ worker schedule ہوتا ہے:
    //  • AlarmManager +10s buffer کے بعد fire ہوتا ہے
    //  • اگر AlarmManager already fire کر چکا ہو → duplicate skip
    //  • اگر AlarmManager نہ چلا (Xiaomi/Samsung) → یہ اذان چلاتا ہے
    //  • اذان کے بعد → اگلی prayer کا نیا worker schedule کرتا ہے
    //

    /**
     * Unique work name / tag for a prayer backup worker.
     * e.g. "prayer_azan_Fajr"
     */
    public static String getPrayerTag(String prayerName) {
        return "prayer_azan_" + prayerName;
    }

    /**
     * Schedule (or replace) a one-time backup WorkManager worker for the given prayer.
     *
     * Called from:
     *  • AlarmHelper.scheduleAllAlarms()   — app start / boot / watchdog tick
     *  • PrayerAzanWorker.doWork()         — self-chain after each azan
     *  • AzanReceiver.onReceive()          — reschedule after AlarmManager fires
     *
     * @param context    App context
     * @param prayerName e.g. "Fajr", "Dhuhr", "Asr", "Maghrib", "Isha"
     * @param delayMs    Milliseconds from now until prayer time
     */
    public static void schedulePrayerWorker(Context context, String prayerName, long delayMs) {
        // PrayerAzanWorker.scheduleFor() adds an internal 10-second buffer
        // so AlarmManager always gets first chance to fire.
        PrayerAzanWorker.scheduleFor(context, prayerName, delayMs);
        Log.d(TAG, "Prayer worker scheduled: " + prayerName
                + "  delay=" + (delayMs / 60000) + " min");
    }

    /**
     * Cancel the backup worker for a specific prayer.
     * Called by AzanReceiver when AlarmManager fires successfully — no duplicate needed.
     */
    public static void cancelPrayerWorker(Context context, String prayerName) {
        WorkManager.getInstance(context).cancelUniqueWork(getPrayerTag(prayerName));
        Log.d(TAG, "Cancelled prayer worker: " + prayerName);
    }

    /**
     * Cancel ALL 5 prayer backup workers.
     * Use when location/settings change and everything must be rescheduled fresh.
     */
    public static void cancelAllPrayerWorkers(Context context) {
        for (String prayer : new String[]{"Fajr", "Dhuhr", "Asr", "Maghrib", "Isha"}) {
            WorkManager.getInstance(context).cancelUniqueWork(getPrayerTag(prayer));
        }
        Log.d(TAG, "All prayer workers cancelled");
    }
}
