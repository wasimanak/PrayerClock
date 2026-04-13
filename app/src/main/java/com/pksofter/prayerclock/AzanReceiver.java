package com.pksofter.prayerclock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class AzanReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null) {
            String prayerName = intent.getStringExtra("prayer_name");
            Log.d("AzanReceiver", "Received Alarm for: " + prayerName);

            Intent serviceIntent = new Intent(context, AzanPlayerService.class);
            serviceIntent.putExtra("prayer_name", prayerName);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        }
    }
}
