package com.alpha4technologies.prayerclock;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/**
 * AlarmWatchdogWorker — ہر 30 منٹ بعد چلتا ہے۔
 *
 * کام:
 *  1. AlarmManager alarms کو refresh کرتا ہے (deep sleep / doze میں clear ہو جاتی ہیں)
 *  2. تمام 5 نمازوں کے WorkManager backup workers کو fresh reschedule کرتا ہے
 *  3. اگر app force-stopped یا sleep میں تھی — اگلی اذان miss نہیں ہوگی
 */
public class AlarmWatchdogWorker extends Worker {

    private static final String TAG = "AlarmWatchdogWorker";

    public AlarmWatchdogWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "=== Watchdog running (30-min check) — rescheduling all alarms + workers ===");

        try {
            // Reschedule both AlarmManager alarms AND WorkManager prayer backup workers.
            // AlarmHelper.scheduleAllAlarms() internally calls:
            //   → scheduleAzanAlarm()      (AlarmManager — primary)
            //   → scheduleWorkerForPrayer() (WorkManager  — backup)
            //   → scheduleWatchdog()        (keeps this periodic worker alive)
            AlarmHelper.scheduleAllAlarms(getApplicationContext());

            Log.d(TAG, "Watchdog: all alarms and backup workers rescheduled successfully.");
        } catch (Exception e) {
            Log.e(TAG, "Watchdog error: " + e.getMessage(), e);
            // Return RETRY so WorkManager tries again
            return Result.retry();
        }

        return Result.success();
    }
}
