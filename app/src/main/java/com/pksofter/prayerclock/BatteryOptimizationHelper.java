package com.pksofter.prayerclock;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

public class BatteryOptimizationHelper {

    private static final String TAG = "BatteryOptHelper";

    public static void checkAndRequestOptimization(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;

        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (pm == null || pm.isIgnoringBatteryOptimizations(context.getPackageName())) {
            return; // Already granted
        }

        // Directly open system battery optimization dialog — no custom dialog needed
        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + context.getPackageName()));
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Cannot open battery optimization dialog: " + e.getMessage());
        }
    }
}
