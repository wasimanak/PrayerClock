package com.alpha4technologies.prayerclock;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.batoulapps.adhan.Madhab;
import com.batoulapps.adhan.Prayer;
import com.batoulapps.adhan.PrayerTimes;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class PrayerTimerService extends Service {

    private static final String CHANNEL_ID = "prayer_timer_channel";
    private static final int NOTIFICATION_ID = 1001;

    private Handler handler;
    private Runnable updateRunnable;
    private NotificationManager notificationManager;

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();

        handler = new Handler(Looper.getMainLooper());
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                updateNotification();
                // Update every minute (60,000 ms)
                handler.postDelayed(this, 60000);
            }
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Start foreground immediately with an initial notification
        startForeground(NOTIFICATION_ID, buildNotification("Loading next prayer..."));
        
        // Begin the update loop
        handler.removeCallbacks(updateRunnable);
        handler.post(updateRunnable);

        // START_STICKY ensures that if the OS kills the service, it will try to recreate it
        return START_STICKY;
    }

    private void updateNotification() {
        SharedPreferences prefs = getSharedPreferences("PrayerClockPrefs", MODE_PRIVATE);
        String latStr = prefs.getString("current_lat", null);
        String lonStr = prefs.getString("current_lon", null);

        if (latStr == null || lonStr == null) {
            notificationManager.notify(NOTIFICATION_ID, buildNotification("Please open app to set location"));
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
                nextPrayerName = PrayerAzanWorker.getPrayerName(nextPrayer);
            }

            if (nextPrayerTime != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.getDefault());
                sdf.setTimeZone(tz);
                String timeStr = sdf.format(nextPrayerTime);
                
                String contentText = "Next: " + nextPrayerName + " at " + timeStr;
                notificationManager.notify(NOTIFICATION_ID, buildNotification(contentText));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Notification buildNotification(String text) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Prayer Clock")
                .setContentText(text)
                .setSmallIcon(R.drawable.logo) // fallback if app icon is not small
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setSilent(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Prayer Timer (Background Survival)",
                    NotificationManager.IMPORTANCE_LOW // Low priority, silent, no popups
            );
            channel.setDescription("Keeps the app alive to ensure Azan alarms ring on time.");
            channel.setShowBadge(false);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (handler != null) {
            handler.removeCallbacks(updateRunnable);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
