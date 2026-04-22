package com.pksofter.prayerclock;

import android.content.Context;
import android.util.Log;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public class WorkManagerHelper {
    public static final String WATCHDOG_WORK_NAME = "alarm_watchdog_work";

    public static void scheduleWatchdog(Context context) {
        // Run every 6 hours to ensure alarms survive deep sleep, force stops, and reboots
        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
                AlarmWatchdogWorker.class,
                6, TimeUnit.HOURS)
                .addTag("alarm_watchdog")
                .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WATCHDOG_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP, // KEEP ensures it doesn't reset the timer if already scheduled
                workRequest
        );

        Log.d("WorkManagerHelper", "Scheduled Periodic Alarm Watchdog (every 6 hours)");
    }
    
    public static void cancelWatchdog(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(WATCHDOG_WORK_NAME);
    }
}
