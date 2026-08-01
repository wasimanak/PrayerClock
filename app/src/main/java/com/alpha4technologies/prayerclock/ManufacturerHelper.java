package com.alpha4technologies.prayerclock;

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
 * Directly opens manufacturer-specific background permission settings
 * without showing any dialog. Called once on first app launch.
 *
 * Brands handled: Xiaomi/Redmi/POCO, Samsung, Oppo/Realme/OnePlus,
 *                 Vivo, Huawei/Honor — plus standard battery optimization.
 */
public class ManufacturerHelper {

    private static final String TAG = "ManufacturerHelper";
    private static final String PREF_KEY_SHOWN = "manufacturer_guide_shown";

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Directly open the relevant background permission screen (once only).
     * No dialog — goes straight to the settings page.
     */
    public static void showGuideIfNeeded(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("PrayerClockPrefs", Context.MODE_PRIVATE);

        // Only run once
        if (prefs.getBoolean(PREF_KEY_SHOWN, false)) return;
        prefs.edit().putBoolean(PREF_KEY_SHOWN, true).apply();

        openManufacturerSettings(context);
    }

    /**
     * Force open again (used from Settings screen).
     */
    public static void resetAndShowGuide(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("PrayerClockPrefs", Context.MODE_PRIVATE);
        prefs.edit().remove(PREF_KEY_SHOWN).apply();
        openManufacturerSettings(context);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Core dispatcher
    // ─────────────────────────────────────────────────────────────────────────

    private static void openManufacturerSettings(Context context) {
        String manufacturer = Build.MANUFACTURER.toLowerCase();
        Log.d(TAG, "Detected manufacturer: " + manufacturer);

        // 1. First: always request standard battery optimization exemption
        requestBatteryOptimizationExemption(context);

        // 2. Then open brand-specific autostart / background screen
        if (manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco")) {
            openXiaomiAutoStart(context);
        } else if (manufacturer.contains("samsung")) {
            openSamsungDeviceCare(context);
        } else if (manufacturer.contains("oppo") || manufacturer.contains("realme") || manufacturer.contains("oneplus")) {
            openOppoBackground(context);
        } else if (manufacturer.contains("vivo")) {
            openVivoWhitelist(context);
        } else if (manufacturer.contains("huawei") || manufacturer.contains("honor")) {
            openHuaweiPhoneManager(context);
        }
        // Other brands: battery optimization exemption (already requested above) is enough
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Xiaomi / Redmi / POCO — AutoStart
    // ─────────────────────────────────────────────────────────────────────────

    private static void openXiaomiAutoStart(Context context) {
        String[][] components = {
            {"com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"},
            {"com.miui.securitycenter", "com.miui.securitycenter.MainActivity"},
            {"com.miui.permcenter",     "com.miui.permcenter.autostart.AutoStartManagementActivity"}
        };
        if (!tryOpenComponents(context, components)) {
            openAppInfo(context);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Samsung — Device Care / Battery
    // ─────────────────────────────────────────────────────────────────────────

    private static void openSamsungDeviceCare(Context context) {
        String[][] components = {
            {"com.samsung.android.lool",  "com.samsung.android.devicecare.main.MainActivity"},
            {"com.samsung.android.sm",    "com.samsung.android.sm.ui.battery.BatteryActivity"},
            {"com.samsung.android.lool",  "com.samsung.android.sm.ui.battery.BatteryActivity"}
        };
        if (!tryOpenComponents(context, components)) {
            openAppInfo(context);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Oppo / Realme / OnePlus — ColorOS Background
    // ─────────────────────────────────────────────────────────────────────────

    private static void openOppoBackground(Context context) {
        String[][] components = {
            {"com.coloros.safecenter",   "com.coloros.privacypermissionsentry.PermissionTopActivity"},
            {"com.oppo.safe",            "com.oppo.safe.permission.startup.FakeActivity"},
            {"com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity"}
        };
        if (!tryOpenComponents(context, components)) {
            openAppInfo(context);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Vivo — FuntouchOS iManager Whitelist
    // ─────────────────────────────────────────────────────────────────────────

    private static void openVivoWhitelist(Context context) {
        String[][] components = {
            {"com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"},
            {"com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"},
            {"com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"}
        };
        if (!tryOpenComponents(context, components)) {
            openAppInfo(context);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Huawei / Honor — EMUI Phone Manager
    // ─────────────────────────────────────────────────────────────────────────

    private static void openHuaweiPhoneManager(Context context) {
        String[][] components = {
            {"com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"},
            {"com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"},
            {"com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"}
        };
        if (!tryOpenComponents(context, components)) {
            openAppInfo(context);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Try a list of {package, activity} pairs. Returns true if one succeeded.
     */
    private static boolean tryOpenComponents(Context context, String[][] components) {
        for (String[] comp : components) {
            try {
                Intent intent = new Intent();
                intent.setComponent(new ComponentName(comp[0], comp[1]));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                Log.d(TAG, "Opened: " + comp[0] + "/" + comp[1]);
                return true;
            } catch (Exception e) {
                // try next
            }
        }
        return false;
    }

    /** Open this app's system info page (universal fallback) */
    private static void openAppInfo(Context context) {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + context.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            Log.d(TAG, "Opened App Info (fallback)");
        } catch (Exception e) {
            Log.e(TAG, "Cannot open app info: " + e.getMessage());
        }
    }

    /** Request Android standard battery optimization exemption directly */
    public static void requestBatteryOptimizationExemption(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(context.getPackageName())) {
                try {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + context.getPackageName()));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                    Log.d(TAG, "Battery optimization exemption requested");
                } catch (ActivityNotFoundException e) {
                    // Fallback: open general battery saver settings
                    try {
                        Intent fallback = new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS);
                        fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(fallback);
                    } catch (Exception ex) {
                        Log.e(TAG, "Battery settings unavailable: " + ex.getMessage());
                    }
                }
            }
        }
    }

    /** Check if app is already exempt from battery optimization */
    public static boolean isIgnoringBatteryOptimizations(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(context.getPackageName());
    }
}
