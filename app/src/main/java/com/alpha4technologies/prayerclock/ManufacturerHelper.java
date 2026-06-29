package com.alpha4technologies.prayerclock;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

/**
 * ManufacturerHelper
 *
 * Handles manufacturer-specific background process restrictions.
 * Brands like Xiaomi, Samsung, Oppo, Vivo, Realme, Huawei kill background apps
 * aggressively, preventing alarms and WorkManager from firing reliably.
 *
 * This helper detects the manufacturer and guides the user to the correct
 * settings page to allow the app to run in background.
 */
public class ManufacturerHelper {

    private static final String TAG = "ManufacturerHelper";
    private static final String PREF_KEY_SHOWN = "manufacturer_guide_shown";

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Show the manufacturer guide dialog once (on first app launch).
     * After showing, sets a flag so it is not shown again.
     */
    public static void showGuideIfNeeded(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("PrayerClockPrefs", Context.MODE_PRIVATE);

        // Only show once
        if (prefs.getBoolean(PREF_KEY_SHOWN, false)) return;

        // Mark as shown regardless so it never repeats even if user dismisses
        prefs.edit().putBoolean(PREF_KEY_SHOWN, true).apply();

        String manufacturer = Build.MANUFACTURER.toLowerCase();
        Log.d(TAG, "Detected manufacturer: " + manufacturer);

        if (manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco")) {
            showXiaomiDialog(context);
        } else if (manufacturer.contains("samsung")) {
            showSamsungDialog(context);
        } else if (manufacturer.contains("oppo") || manufacturer.contains("realme") || manufacturer.contains("oneplus")) {
            showOppoDialog(context);
        } else if (manufacturer.contains("vivo")) {
            showVivoDialog(context);
        } else if (manufacturer.contains("huawei") || manufacturer.contains("honor")) {
            showHuaweiDialog(context);
        } else {
            // Generic: just request battery optimization exemption
            requestBatteryOptimizationExemption(context);
        }
    }

    /**
     * Force-show the manufacturer guide (used from settings).
     * Resets the "shown" flag first.
     */
    public static void resetAndShowGuide(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("PrayerClockPrefs", Context.MODE_PRIVATE);
        prefs.edit().remove(PREF_KEY_SHOWN).apply();
        showGuideIfNeeded(context);
    }

    /**
     * Open system battery optimization settings for this app directly.
     */
    public static void openBatteryOptimizationSettings(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + context.getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (ActivityNotFoundException e) {
                // Fallback to battery saver settings
                try {
                    Intent intent = new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                } catch (Exception ex) {
                    Log.e(TAG, "Cannot open battery settings: " + ex.getMessage());
                }
            }
        }
    }

    /**
     * Check if app is currently ignoring battery optimizations.
     */
    public static boolean isIgnoringBatteryOptimizations(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(context.getPackageName());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Xiaomi / Redmi / POCO
    // ─────────────────────────────────────────────────────────────────────────

    private static void showXiaomiDialog(Context context) {
        new AlertDialog.Builder(context)
                .setTitle("📱 Xiaomi — اذان کی اجازت دیں")
                .setMessage(
                    "Xiaomi فون آپ کی اذان بند کر سکتا ہے۔ براہ کرم یہ settings ON کریں:\n\n" +
                    "1️⃣  Security App کھولیں\n" +
                    "2️⃣  'Permissions' → 'Autostart' جائیں\n" +
                    "3️⃣  'PrayerClock' تلاش کریں اور ON کریں\n\n" +
                    "یا نیچے 'AutoStart کھولیں' بٹن دبائیں"
                )
                .setPositiveButton("AutoStart کھولیں", (d, w) -> {
                    openXiaomiAutoStart(context);
                    requestBatteryOptimizationExemption(context);
                })
                .setNegativeButton("بعد میں", null)
                .setCancelable(true)
                .show();
    }

    private static void openXiaomiAutoStart(Context context) {
        // Try MIUI AutoStart settings
        String[] intents = {
            "com.miui.securitycenter/.MainActivity",
            "com.miui.permcenter.autostart.AutoStartManagementActivity",
            "com.miui.securitycenter/com.miui.permcenter.autostart.AutoStartManagementActivity"
        };

        for (String component : intents) {
            try {
                String[] parts = component.split("/");
                Intent intent = new Intent();
                intent.setComponent(new ComponentName(parts[0], parts[1]));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                Log.d(TAG, "Opened Xiaomi AutoStart: " + component);
                return;
            } catch (Exception e) {
                // Try next
            }
        }

        // Fallback: open app info
        openAppInfo(context);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Samsung
    // ─────────────────────────────────────────────────────────────────────────

    private static void showSamsungDialog(Context context) {
        new AlertDialog.Builder(context)
                .setTitle("📱 Samsung — اذان کی اجازت دیں")
                .setMessage(
                    "Samsung فون اذان بند کر سکتا ہے۔ یہ steps کریں:\n\n" +
                    "1️⃣  Settings → Device Care / Battery\n" +
                    "2️⃣  'App Power Management' کھولیں\n" +
                    "3️⃣  'Never Sleeping Apps' میں PrayerClock شامل کریں\n\n" +
                    "یا 'Battery Settings کھولیں' بٹن دبائیں"
                )
                .setPositiveButton("Battery Settings کھولیں", (d, w) -> {
                    openSamsungDeviceCare(context);
                    requestBatteryOptimizationExemption(context);
                })
                .setNegativeButton("بعد میں", null)
                .setCancelable(true)
                .show();
    }

    private static void openSamsungDeviceCare(Context context) {
        String[] intents = {
            "com.samsung.android.lool/.devicecare.main.DeviceCareActivity",
            "com.samsung.android.sm.ui.battery.BatteryActivity",
            "com.samsung.android.sm/.ui.battery.BatteryActivity"
        };

        for (String component : intents) {
            try {
                String[] parts = component.split("/");
                Intent intent = new Intent();
                intent.setComponent(new ComponentName(parts[0], parts[1]));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                Log.d(TAG, "Opened Samsung Device Care: " + component);
                return;
            } catch (Exception e) {
                // Try next
            }
        }

        openAppInfo(context);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Oppo / Realme / OnePlus (ColorOS)
    // ─────────────────────────────────────────────────────────────────────────

    private static void showOppoDialog(Context context) {
        new AlertDialog.Builder(context)
                .setTitle("📱 Oppo/Realme — اذان کی اجازت دیں")
                .setMessage(
                    "آپ کا فون اذان بند کر سکتا ہے۔ یہ کریں:\n\n" +
                    "1️⃣  Settings → Battery → App Power Consumption\n" +
                    "2️⃣  PrayerClock → 'Allow Running in Background' ON کریں\n\n" +
                    "یا:\n" +
                    "1️⃣  App کو hold کریں → App Info\n" +
                    "2️⃣  'Battery' → 'Background Activity' Allow\n\n" +
                    "'Settings کھولیں' بٹن دبائیں"
                )
                .setPositiveButton("App Info کھولیں", (d, w) -> {
                    openAppInfo(context);
                    requestBatteryOptimizationExemption(context);
                })
                .setNegativeButton("بعد میں", null)
                .setCancelable(true)
                .show();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Vivo (FuntouchOS)
    // ─────────────────────────────────────────────────────────────────────────

    private static void showVivoDialog(Context context) {
        new AlertDialog.Builder(context)
                .setTitle("📱 Vivo — اذان کی اجازت دیں")
                .setMessage(
                    "Vivo فون اذان بند کر سکتا ہے۔ یہ steps کریں:\n\n" +
                    "1️⃣  iManager App کھولیں\n" +
                    "2️⃣  'App Manager' → 'Whitelist'\n" +
                    "3️⃣  PrayerClock کو whitelist میں شامل کریں\n\n" +
                    "اور:\n" +
                    "1️⃣  Settings → Battery → High Background Power\n" +
                    "2️⃣  PrayerClock ON کریں"
                )
                .setPositiveButton("App Info کھولیں", (d, w) -> {
                    openVivoAutoStart(context);
                    requestBatteryOptimizationExemption(context);
                })
                .setNegativeButton("بعد میں", null)
                .setCancelable(true)
                .show();
    }

    private static void openVivoAutoStart(Context context) {
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(
                    "com.iqoo.secure",
                    "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
            ));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return;
        } catch (Exception e) {
            // ignore
        }
        openAppInfo(context);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Huawei / Honor (EMUI)
    // ─────────────────────────────────────────────────────────────────────────

    private static void showHuaweiDialog(Context context) {
        new AlertDialog.Builder(context)
                .setTitle("📱 Huawei — اذان کی اجازت دیں")
                .setMessage(
                    "Huawei فون اذان بند کر سکتا ہے۔ یہ کریں:\n\n" +
                    "1️⃣  Phone Manager → App Launch\n" +
                    "2️⃣  PrayerClock کو 'Manage Manually' پر set کریں\n" +
                    "3️⃣  تینوں options (Auto Launch, Secondary Launch, Run in Background) ON کریں\n\n" +
                    "نیچے بٹن دبائیں"
                )
                .setPositiveButton("Phone Manager کھولیں", (d, w) -> {
                    openHuaweiPhoneManager(context);
                    requestBatteryOptimizationExemption(context);
                })
                .setNegativeButton("بعد میں", null)
                .setCancelable(true)
                .show();
    }

    private static void openHuaweiPhoneManager(Context context) {
        String[] intents = {
            "com.huawei.systemmanager/.startupmgr.ui.StartupNormalAppListActivity",
            "com.huawei.systemmanager/.appcontrol.activity.StartupAppControlActivity"
        };

        for (String component : intents) {
            try {
                String[] parts = component.split("/");
                Intent intent = new Intent();
                intent.setComponent(new ComponentName(parts[0], parts[1]));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return;
            } catch (Exception e) {
                // Try next
            }
        }

        openAppInfo(context);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Common Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Open this app's system info page (works on all brands) */
    private static void openAppInfo(Context context) {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + context.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Cannot open app info: " + e.getMessage());
        }
    }

    /** Request Android standard battery optimization exemption */
    private static void requestBatteryOptimizationExemption(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(context.getPackageName())) {
                try {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + context.getPackageName()));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                } catch (Exception e) {
                    Log.e(TAG, "Battery exemption request failed: " + e.getMessage());
                }
            }
        }
    }
}
