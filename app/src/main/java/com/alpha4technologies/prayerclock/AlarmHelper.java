package com.alpha4technologies.prayerclock;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.batoulapps.adhan.Madhab;
import com.batoulapps.adhan.PrayerTimes;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class AlarmHelper {

    private static long lastScheduleTime = 0;
    private static String lastScheduleKey = "";

    public static void scheduleAllAlarms(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("PrayerClockPrefs", Context.MODE_PRIVATE);
        
        String latStr = prefs.getString("current_lat", null);
        String lonStr = prefs.getString("current_lon", null);
        String madhabStr = prefs.getString("madhab", "HANAFI");

        if (latStr == null || lonStr == null) return;

        // Debounce: Skip if same data and scheduled less than 5 seconds ago
        String currentKey = latStr + "_" + lonStr + "_" + madhabStr;
        long now = System.currentTimeMillis();
        if (currentKey.equals(lastScheduleKey) && (now - lastScheduleTime < 5000)) {
            return;
        }
        lastScheduleKey = currentKey;
        lastScheduleTime = now;
        
        double lat = Double.parseDouble(latStr);
        double lon = Double.parseDouble(lonStr);
        
        Madhab madhab = madhabStr.equals("SHAFI") ? Madhab.SHAFI : Madhab.HANAFI;
        
        PrayerTimes times = PrayerTimeUtil.getPrayerTimes(lat, lon, madhab);
        
        String tzId = prefs.getString("current_timezone", TimeZone.getDefault().getID());
        TimeZone tz = TimeZone.getTimeZone(tzId);
        
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        
        // 0. Start Watchdog to ensure survival
        WorkManagerHelper.scheduleWatchdog(context);
        
        // 1. Azan Alarms via AlarmManager (Deep Sleep Proof)
        scheduleAzanAlarm(context, alarmManager, times.fajr, "Fajr", tz);
        scheduleAzanAlarm(context, alarmManager, times.dhuhr, "Dhuhr", tz);
        scheduleAzanAlarm(context, alarmManager, times.asr, "Asr", tz);
        scheduleAzanAlarm(context, alarmManager, times.maghrib, "Maghrib", tz);
        scheduleAzanAlarm(context, alarmManager, times.isha, "Isha", tz);
        
        // 2. Jamat Alarms
        scheduleJamatAlarms(context, alarmManager, times, tz, prefs);
        
        // 3. Tasbih Reminders
        scheduleTasbihReminderAlarm(context, alarmManager, times.dhuhr, "Dhuhr", tz);
        scheduleTasbihReminderAlarm(context, alarmManager, times.isha, "Isha", tz);
        
        Log.d("AlarmHelper", "All alarms scheduled successfully");
    }

    private static void scheduleAzanAlarm(Context context, AlarmManager alarmManager, Date time, String name, TimeZone tz) {
        if (time == null) return;
        Calendar cal = Calendar.getInstance(tz);
        cal.setTime(time);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1);

        Intent intent = new Intent(context, AzanReceiver.class);
        intent.putExtra("prayer_name", name);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, name.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        setAlarm(alarmManager, cal.getTimeInMillis(), pendingIntent);
    }

    private static void scheduleJamatAlarms(Context context, AlarmManager alarmManager, PrayerTimes times, TimeZone tz, SharedPreferences prefs) {
        scheduleSingleJamatAlarm(context, alarmManager, times.fajr, "Fajr", "fajr", tz, prefs);
        scheduleSingleJamatAlarm(context, alarmManager, times.dhuhr, "Dhuhr", "dhuhr", tz, prefs);
        scheduleSingleJamatAlarm(context, alarmManager, times.asr, "Asr", "asr", tz, prefs);
        scheduleSingleJamatAlarm(context, alarmManager, times.maghrib, "Maghrib", "maghrib", tz, prefs);
        scheduleSingleJamatAlarm(context, alarmManager, times.isha, "Isha", "isha", tz, prefs);
    }

    private static void scheduleSingleJamatAlarm(Context context, AlarmManager alarmManager, Date azanTime, String name, String key, TimeZone tz, SharedPreferences prefs) {
        if (azanTime == null) return;
        
        String savedJamat = prefs.getString("jamat_" + key, null);
        
        try {
            // Get effective jamat date (applies +5 min rule automatically)
            Date effectiveJamatDate = PrayerTimeUtil.getEffectiveJamatDate(azanTime, savedJamat, 5, tz);
            
            Calendar cal = Calendar.getInstance(tz);
            cal.setTime(effectiveJamatDate);
            cal.set(Calendar.SECOND, 0);
            
            // If time has passed today, schedule for tomorrow
            if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
                cal.add(Calendar.DAY_OF_YEAR, 1);
            }

            Intent intent = new Intent(context, JamatReceiver.class);
            intent.putExtra("prayer_name", name);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, name.hashCode() + 5000, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            setAlarm(alarmManager, cal.getTimeInMillis(), pendingIntent);
            
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            sdf.setTimeZone(tz);
            Log.d("AlarmHelper", "Scheduled Jamat (" + name + ") at " + sdf.format(cal.getTime()));
        } catch (Exception e) {
            Log.e("AlarmHelper", "Jamat error: " + e.getMessage());
        }
    }

    private static void scheduleTasbihReminderAlarm(Context context, AlarmManager alarmManager, Date prayerTime, String name, TimeZone tz) {
        if (prayerTime == null) return;
        Calendar cal = Calendar.getInstance(tz);
        cal.setTime(prayerTime);
        cal.add(Calendar.HOUR_OF_DAY, 1);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1);

        Intent intent = new Intent(context, TasbihReminderReceiver.class);
        intent.putExtra("prayer_name", name);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, name.hashCode() + 10000, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        setAlarm(alarmManager, cal.getTimeInMillis(), pendingIntent);
        
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        sdf.setTimeZone(tz);
        Log.d("AlarmHelper", "Scheduled Tasbih Reminder (" + name + ") at " + sdf.format(cal.getTime()));
    }

    private static void setAlarm(AlarmManager alarmManager, long timeMillis, PendingIntent pendingIntent) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                AlarmManager.AlarmClockInfo info = new AlarmManager.AlarmClockInfo(timeMillis, pendingIntent);
                alarmManager.setAlarmClock(info, pendingIntent);
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMillis, pendingIntent);
            }
        } else {
            AlarmManager.AlarmClockInfo info = new AlarmManager.AlarmClockInfo(timeMillis, pendingIntent);
            alarmManager.setAlarmClock(info, pendingIntent);
        }
    }
}
