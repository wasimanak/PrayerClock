package com.pksofter.prayerclock;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class PrayerBackgroundService extends Service {
    private static final String CHANNEL_ID = "PrayerClockBackgroundChannel";
    private static final int NOTIFICATION_ID = 2002;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, createNotification());
        return START_STICKY; 
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // Not binding
    }
    
    // Create the persistent notification to keep service alive
    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Prayer Clock")
                .setContentText("Keeping prayer times active in background")
                .setSmallIcon(R.drawable.logo) // Ensure this resource exists
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW) // Minimize distraction
                .setOngoing(true);
        
        return builder.build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Prayer Clock Background Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            
            // Disable sound/vibration for this background channel to avoid annoyance
            serviceChannel.setSound(null, null);
            serviceChannel.enableVibration(false);
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }
}
