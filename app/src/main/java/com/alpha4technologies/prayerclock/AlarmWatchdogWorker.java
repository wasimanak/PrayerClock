package com.alpha4technologies.prayerclock;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class AlarmWatchdogWorker extends Worker {

    public AlarmWatchdogWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d("AlarmWatchdogWorker", "Watchdog checking and rescheduling alarms...");
        
        // Simply call the helper to reschedule all alarms. 
        // AlarmManager will overwrite existing pending intents, ensuring they are fresh.
        AlarmHelper.scheduleAllAlarms(getApplicationContext());

        return Result.success();
    }
}
