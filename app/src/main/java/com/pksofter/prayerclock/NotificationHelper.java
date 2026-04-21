package com.pksofter.prayerclock;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

public class NotificationHelper {
    public static final String CHANNEL_ID_AZAN = "azan_channel";
    public static final String CHANNEL_NAME_AZAN = "Azan Notifications";
    public static final String CHANNEL_ID_JAMAT = "jamat_channel";
    public static final String CHANNEL_NAME_JAMAT = "Jamat Notifications";
    public static final String CHANNEL_ID_REMINDER = "reminder_channel";
    public static final String CHANNEL_NAME_REMINDER = "Tasbih Reminders";

    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Azan Channel (Silent, for Custom Media)
            NotificationChannel azanChannel = new NotificationChannel(
                    CHANNEL_ID_AZAN,
                    CHANNEL_NAME_AZAN,
                    NotificationManager.IMPORTANCE_HIGH
            );
            azanChannel.setDescription("Shows notification when Azan is playing");
            azanChannel.setSound(null, null); // We play sound manually via MediaPlayer
            
            // Jamat Channel (Default Sound)
            NotificationChannel jamatChannel = new NotificationChannel(
                    CHANNEL_ID_JAMAT,
                    CHANNEL_NAME_JAMAT,
                    NotificationManager.IMPORTANCE_HIGH
            );
            jamatChannel.setDescription("Notifications for Jamat Times");

            // Reminder Channel
            NotificationChannel reminderChannel = new NotificationChannel(
                    CHANNEL_ID_REMINDER,
                    CHANNEL_NAME_REMINDER,
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            reminderChannel.setDescription("Daily reminders for prayers and Tasbih");
            
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(azanChannel);
                manager.createNotificationChannel(jamatChannel);
                manager.createNotificationChannel(reminderChannel);
            }
        }
    }
}
