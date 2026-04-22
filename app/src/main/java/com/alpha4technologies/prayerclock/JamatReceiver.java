package com.alpha4technologies.prayerclock;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import androidx.core.app.NotificationCompat;

public class JamatReceiver extends BroadcastReceiver {
    private static final String TAG = "JamatReceiver";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        String prayerName = intent.getStringExtra("prayer_name");
        Log.d(TAG, "Jamat notification for: " + prayerName);
        
        // Create notification channel first
        NotificationHelper.createNotificationChannel(context);
        
        // Create notification for Jamat time
        Intent appIntent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, appIntent, PendingIntent.FLAG_IMMUTABLE);
        
        Notification notification = new NotificationCompat.Builder(context, NotificationHelper.CHANNEL_ID_JAMAT)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("جماعت کا وقت - " + prayerName)
                .setContentText("جماعت کا وقت ہو گیا ہے")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setVibrate(new long[]{0, 500, 200, 500})
                .build();
        
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(prayerName.hashCode() + 1000, notification);
            Log.d(TAG, "Jamat notification shown for: " + prayerName);
            
            // Play Beep
            try {
                android.media.ToneGenerator toneGen = new android.media.ToneGenerator(android.media.AudioManager.STREAM_ALARM, 100);
                toneGen.startTone(android.media.ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 500); // 500ms beep
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
