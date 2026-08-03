package com.alpha4technologies.prayerclock;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.Calendar;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Shows a daily reminder for Durood Shareef at a random time between 10 AM and 8 PM.
 */
public class DuroodWorker extends Worker {

    private static final String TAG = "DuroodWorker";
    public static final String WORK_NAME = "durood_reminder_work";
    private static final String CHANNEL_ID = "durood_channel";

    public DuroodWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "DuroodWorker doWork() triggered");
        
        // Show Notification
        showNotification(getApplicationContext());

        // Schedule next one for tomorrow
        scheduleNext(getApplicationContext(), ExistingWorkPolicy.REPLACE);

        return Result.success();
    }

    private void showNotification(Context context) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Daily Reminders",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Reminders for Durood Shareef and Sunnah");
            nm.createNotificationChannel(channel);
        }

        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 105, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.logo) // Ensure this icon exists, fallback to app icon otherwise
                .setContentTitle("يَا مُحَمَّد ﷺ")
                .setContentText("کیا آپ نے آج درود شریف پڑھا ہے؟")
                .setStyle(new NotificationCompat.BigTextStyle().bigText("کیا آپ نے آج درود شریف پڑھا ہے؟ ایک بار درود پڑھنے پر 10 رحمتیں نازل ہوتی ہیں۔"))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build();

        nm.notify(2001, notification);
    }

    /**
     * Schedules the next Durood notification at a random time between 10 AM and 8 PM.
     */
    public static void scheduleNext(Context context, ExistingWorkPolicy policy) {
        Calendar cal = Calendar.getInstance();
        long now = cal.getTimeInMillis();

        // Calculate a random time for TOMORROW (or today if calling for the first time and it's early)
        int randomHour = 10 + new Random().nextInt(11); // 10 to 20 (8 PM)
        int randomMinute = new Random().nextInt(60);

        cal.set(Calendar.HOUR_OF_DAY, randomHour);
        cal.set(Calendar.MINUTE, randomMinute);
        cal.set(Calendar.SECOND, 0);

        if (cal.getTimeInMillis() <= now) {
            // Already passed today, schedule for tomorrow
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }

        long delayMs = cal.getTimeInMillis() - now;

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(DuroodWorker.class)
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .addTag("durood_reminder")
                .build();

        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, request);
        
        Log.d(TAG, "Scheduled next Durood reminder in " + (delayMs / 3600000) + " hours, at " + randomHour + ":" + randomMinute);
    }
}
