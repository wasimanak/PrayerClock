package com.alpha4technologies.prayerclock;

import com.batoulapps.adhan.CalculationMethod;
import com.batoulapps.adhan.Coordinates;
import com.batoulapps.adhan.Madhab;
import com.batoulapps.adhan.PrayerTimes;
import com.batoulapps.adhan.data.DateComponents;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class PrayerTimeUtil {

    public static PrayerTimes getPrayerTimes(double latitude, double longitude, Madhab madhab) {
        return getPrayerTimes(latitude, longitude, new Date(), madhab);
    }

    public static PrayerTimes getPrayerTimes(double latitude, double longitude, Date dateObj, Madhab madhab) {
        Coordinates coordinates = new Coordinates(latitude, longitude);
        DateComponents date = DateComponents.from(dateObj);
        
        com.batoulapps.adhan.CalculationParameters params = 
            CalculationMethod.KARACHI.getParameters();
        params.madhab = madhab;

        return new PrayerTimes(coordinates, date, params);
    }

    public static String formatTime(Date time, TimeZone timeZone) {
        if (time == null) return "--:--";
        SimpleDateFormat formatter = new SimpleDateFormat("h:mm", Locale.getDefault()); 
        formatter.setTimeZone(timeZone != null ? timeZone : TimeZone.getDefault());
        return formatter.format(time);
    }
    
    public static String formatTime(Date time) {
        return formatTime(time, TimeZone.getDefault());
    }
    
    public static Date getEffectiveJamatDate(Date adhanTime, String savedJamat, int defaultOffsetMinutes, TimeZone timeZone) {
        if (adhanTime == null) return null;

        // Minimum allowed Jamat time: Adhan + 5 mins
        Calendar safeMinCal = Calendar.getInstance(timeZone);
        safeMinCal.setTime(adhanTime);
        safeMinCal.add(Calendar.MINUTE, 5);

        // Default Jamat time (if not set): Adhan + defaultOffsetMinutes (rounded up to nearest 5)
        Calendar defaultCal = Calendar.getInstance(timeZone);
        defaultCal.setTime(adhanTime);
        defaultCal.add(Calendar.MINUTE, defaultOffsetMinutes);
        int unroundedMinutes = defaultCal.get(Calendar.MINUTE);
        int mod = unroundedMinutes % 5;
        if (mod > 0) {
            defaultCal.add(Calendar.MINUTE, 5 - mod);
        }

        if (savedJamat == null || savedJamat.isEmpty()) {
            if (defaultCal.getTimeInMillis() < safeMinCal.getTimeInMillis()) {
                return safeMinCal.getTime();
            }
            return defaultCal.getTime();
        }

        try {
            String[] parts = savedJamat.split(":");
            if (parts.length == 2) {
                int hour = Integer.parseInt(parts[0]);
                int minute = Integer.parseInt(parts[1]);

                Calendar manualCal = Calendar.getInstance(timeZone);
                manualCal.setTime(adhanTime);
                manualCal.set(Calendar.HOUR_OF_DAY, hour);
                manualCal.set(Calendar.MINUTE, minute);
                manualCal.set(Calendar.SECOND, 0);

                // Handle overnight Isha Jamat (e.g., Adhan at 8 PM, Jamat at 1 AM)
                Calendar adhanCal = Calendar.getInstance(timeZone);
                adhanCal.setTime(adhanTime);
                if (hour <= 6 && adhanCal.get(Calendar.HOUR_OF_DAY) >= 18) {
                    manualCal.add(Calendar.DAY_OF_YEAR, 1);
                }

                // Apply the +5 mins rule if manual setting has fallen behind
                if (manualCal.getTimeInMillis() < safeMinCal.getTimeInMillis()) {
                    return safeMinCal.getTime();
                }

                return manualCal.getTime();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return defaultCal.getTime();
    }

    public static String getJamatTimeStr(Date adhanTime, String savedJamat, int offsetMinutes, TimeZone timeZone) {
         Date effectiveDate = getEffectiveJamatDate(adhanTime, savedJamat, offsetMinutes, timeZone);
         return formatTime(effectiveDate, timeZone);
    }

    public static String getJamatTime(Date adhanTime, int offsetMinutes, TimeZone timeZone) {
         return getJamatTimeStr(adhanTime, null, offsetMinutes, timeZone);
    }

    public static Date getIshraqTime(Date sunrise) {
        if (sunrise == null) return null;
        Calendar cal = Calendar.getInstance();
        cal.setTime(sunrise);
        cal.add(Calendar.MINUTE, 15); // 15 mins after sunrise
        return cal.getTime();
    }

    public static Date getChashtTime(Date sunrise) {
        if (sunrise == null) return null;
        Calendar cal = Calendar.getInstance();
        cal.setTime(sunrise);
        cal.add(Calendar.MINUTE, 100); // Approx 1 hr 40 mins after sunrise
        return cal.getTime();
    }



    public static String generateUrduTicker(PrayerTimes times, double lat, double lon, TimeZone timeZone) {
        if (times == null) return "";

        String fajr = formatTime(times.fajr, timeZone);
        String sunrise = formatTime(times.sunrise, timeZone);
        String ishraq = formatTime(getIshraqTime(times.sunrise), timeZone);
        String chasht = formatTime(getChashtTime(times.sunrise), timeZone);
        // Zawal is technically noon time range. We can show End of Zawal as Dhuhr start.
        String zawal = formatTime(new Date(times.dhuhr.getTime() - 10 * 60 * 1000), timeZone); // 10 mins before Dhuhr
        String dhuhr = formatTime(times.dhuhr, timeZone);
        String asr = formatTime(times.asr, timeZone);
        String maghrib = formatTime(times.maghrib, timeZone);
        String isha = formatTime(times.isha, timeZone);
        
        // Use the sunset-aware date logic (same as Ramadan Dialog)
        String hijri = getRamadanDateString(times, lat, lon);

        // Construct Urdu String
        return "  فجر: " + fajr + "  |  " +
               "  طلوع آفتاب: " + sunrise + "  |  " +
               "  اشراق: " + ishraq + "  |  " +
               "  چاشت: " + chasht + "  |  " +
               "  زوال: " + zawal + "  |  " +
               "  ظہر: " + dhuhr + "  |  " +
               "  عصر: " + asr + "  |  " +
               "  مغرب: " + maghrib + "  |  " +
               "  عشاء: " + isha + "  |  " +
               "  اسلامی تاریخ: " + hijri + "  ";
    }

    public static String convertTo12Hour(String time24) {
        if (time24 == null || !time24.contains(":")) return "--:--";
        try {
            String[] parts = time24.split(":");
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            
            int hour12 = hour % 12;
            if (hour12 == 0) hour12 = 12;
            
            return hour12 + ":" + (minute < 10 ? "0" + minute : minute);
        } catch (Exception e) {
            return time24;
        }
    }

    public static String getRemainingTimeUrdu(PrayerTimes times, TimeZone timeZone) {
        if (times == null) return "";
        
        long now = System.currentTimeMillis();
        long diff = 0;
        String label = "";
        
        // Define intervals
        // Fajr -> Sunrise
        if (now >= times.fajr.getTime() && now < times.sunrise.getTime()) {
            label = "فجر میں باقی";
            diff = times.sunrise.getTime() - now;
        }
        // Sunrise -> Dhuhr (Modified Logic)
        else if (now >= times.sunrise.getTime() && now < times.dhuhr.getTime()) {
            // Zawal starts 10 mins before Dhuhr
            long zawalStart = times.dhuhr.getTime() - (10 * 60 * 1000);
            
            if (now < zawalStart) {
                // Sunrise to Zawal Start -> Show Nothing
                return ""; 
            } else {
                // Zawal Time (10 mins before Dhuhr)
                return "زوال کا وقت (ممنوع)";
            }
        }
        // Dhuhr -> Asr
        else if (now >= times.dhuhr.getTime() && now < times.asr.getTime()) {
             label = "ظہر میں باقی";
             diff = times.asr.getTime() - now;
        }
        // Asr -> Maghrib
        else if (now >= times.asr.getTime() && now < times.maghrib.getTime()) {
            label = "عصر میں باقی";
            diff = times.maghrib.getTime() - now;
        }
        // Maghrib -> Isha
        else if (now >= times.maghrib.getTime() && now < times.isha.getTime()) {
            label = "مغرب میں باقی";
            diff = times.isha.getTime() - now;
        }
        // Isha -> Fajr (Complex case due to midnight wrapping)
        else {
            label = "عشاء میں باقی";
            // Check if we are in the evening (after Isha) or early morning (before Fajr)
            if (now >= times.isha.getTime()) {
                // After Isha, end is tomorrow's Fajr
                // We need tomorrow's Fajr. We can approximate or recalculate.
                // Since we don't have tomorrow's object passed here conveniently,
                // and calculating it might be heavy, let's add 24 hours to today's Fajr roughly?
                // No, that's risky. Better to assume Fajr is roughly constant or use `nextPrayer` logic if available.
                // But let's try to recalculate for accuracy if possible or just use a safe fallback.
                
                // Let's use a simpler approach: if current time > isha, add 1 day to fajr time for diff
                Date nextFajr = new Date(times.fajr.getTime() + 24 * 60 * 60 * 1000); 
                diff = nextFajr.getTime() - now;
            } else {
                // Early morning before Fajr (e.g. 1 AM), end is today's Fajr
                if (now < times.fajr.getTime()) {
                     diff = times.fajr.getTime() - now;
                }
            }
        }
        
        if (diff < 0) return ""; // Should not happen with correct logic
        
        long hours = diff / (1000 * 60 * 60);
        long minutes = (diff / (1000 * 60)) % 60;
        long seconds = (diff / 1000) % 60;
        
        String timeStr;
        if (hours > 0) {
            timeStr = String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            timeStr = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        }
        
        return label + ": " + timeStr;
    }

    public static boolean isRamadan(PrayerTimes times, double lat, double lon) {
        Calendar cal = Calendar.getInstance();
        if (times != null) {
            // Check if Maghrib has passed
            long now = System.currentTimeMillis();
            if (now >= times.maghrib.getTime()) {
                cal.add(Calendar.DAY_OF_YEAR, 1); // Next Islamic Day starts at Maghrib
            }
        }
        
        // AUTO-CORRECTION for Pakistan (Moon Sighting difference) based on GEOLOCATION
        if (lat > 23.0 && lat < 37.5 &&
            lon > 60.0 && lon < 78.0) {
             cal.add(Calendar.DAY_OF_YEAR, -1);
        }

        Date dateToUse = cal.getTime();

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
             android.icu.util.Calendar islamicCalendar = new android.icu.util.IslamicCalendar();
             islamicCalendar.setTime(dateToUse);
             
             // Check Month
             int month = islamicCalendar.get(android.icu.util.Calendar.MONTH);
             return month == android.icu.util.IslamicCalendar.RAMADAN;
        }
        
        // Fallback for older Android versions (unlikely to be used given minSdk)
        // Or check SimpleHijri/UmmAlQura if implemented elsewhere
        return false; 
    }

    public static String getRamadanDateString(PrayerTimes times, double lat, double lon) {
        Calendar cal = Calendar.getInstance();
        if (times != null) {
            // Check if Maghrib has passed
            long now = System.currentTimeMillis();
            if (now >= times.maghrib.getTime()) {
                cal.add(Calendar.DAY_OF_YEAR, 1); // Next Islamic Day starts at Maghrib
            }
        }
        
        // AUTO-CORRECTION for Pakistan (Moon Sighting difference) based on GEOLOCATION
        // Pakistan Bounds: Lat 23-37, Lon 60-78
        // This ensures -1 day offset applies even if user has manual location set but wrong device TimeZone
        if (lat > 23.0 && lat < 37.5 &&
            lon > 60.0 && lon < 78.0) {
             cal.add(Calendar.DAY_OF_YEAR, -1);
        }

        Date dateToUse = cal.getTime();

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
             android.icu.util.Calendar islamicCalendar = new android.icu.util.IslamicCalendar();
             islamicCalendar.setTime(dateToUse);
             
             // force ULocale to islamic civil to get proper month names
             android.icu.util.ULocale uLocale = new android.icu.util.ULocale("ur-u-ca-islamic-civil");
             android.icu.text.DateFormat dateFormat = android.icu.text.DateFormat.getDateInstance(android.icu.text.DateFormat.LONG, uLocale);
             dateFormat.setCalendar(islamicCalendar);
             
             return dateFormat.format(islamicCalendar.getTime());
        }
        return getHijriDate(dateToUse); 
    }


    public static String getHijriDate(Date date) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            android.icu.util.Calendar islamicCalendar = new android.icu.util.IslamicCalendar();
            islamicCalendar.setTime(date);
            
            // force ULocale to islamic civil to get proper month names
            android.icu.util.ULocale uLocale = new android.icu.util.ULocale("ur-u-ca-islamic-civil");
            android.icu.text.DateFormat dateFormat = android.icu.text.DateFormat.getDateInstance(android.icu.text.DateFormat.LONG, uLocale);
            dateFormat.setCalendar(islamicCalendar);
            
            return dateFormat.format(islamicCalendar.getTime());
        }
        return "";
    }

    // Overload for existing calls that might not pass date
    public static String getHijriDate() {
        return getHijriDate(new Date());
    }

    public static String resolveSmartTime(int hour12, int minute, String key) {
        int hour24 = hour12;
        
        switch (key.toLowerCase()) {
            case "fajr":
                // Fajr is always AM. (e.g. 4:00 AM to 6:00 AM)
                // If user picks 12 (12 AM), convert to 0.
                if (hour12 == 12) hour24 = 0;
                // Otherwise keep 1-11 as is. 
                break;
                
            case "dhuhr":
            case "jummah":
                // Dhuhr/Jummah usually PM (12 PM, 1 PM...).
                // If 12, it is 12 PM (keep as 12).
                // If 1-11, it is PM (add 12 -> 13-23).
                // Exception: 11 AM dhuhr is extremely rare, assume PM.
                if (hour12 != 12) hour24 += 12;
                break;
                
            case "asr":
            case "maghrib":
                // Asr/Maghrib always PM.
                // If 12, assume 12 PM (keep 12 - rare but possible for early Asr?). 
                // Actually Asr logic: 1-11 -> 13-23. 12 -> 12.
                if (hour12 != 12) hour24 += 12;
                break;
                
            case "isha":
                // Isha logic is tricky. 
                // 6 PM to 11 PM -> PM.
                // 12 -> 12 AM (0).
                // 1 to 5 -> 1 AM to 5 AM (late night / tahajjud time before Fajr).
                
                if (hour12 == 12) {
                    hour24 = 0; // 12 AM
                } else if (hour12 >= 6 && hour12 <= 11) {
                    hour24 = hour12 + 12; // 6 PM - 11 PM
                } else {
                    // 1, 2, 3, 4, 5 -> Treat as AM (next day)
                    // Keep hour24 as is (1-5)
                }
                break;
        }
        
        return String.format(Locale.getDefault(), "%d:%02d", hour24, minute);
    }

    public static String getNextPrayerInfo(android.content.Context context) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("PrayerClockPrefs", android.content.Context.MODE_PRIVATE);
        String latStr = prefs.getString("current_lat", null);
        String lonStr = prefs.getString("current_lon", null);
        if (latStr == null || lonStr == null) return "اذان کا انتظار ہے۔۔۔";

        try {
            double lat = Double.parseDouble(latStr);
            double lon = Double.parseDouble(lonStr);
            String madhabStr = prefs.getString("madhab", "HANAFI");
            Madhab madhab = madhabStr.equals("SHAFI") ? Madhab.SHAFI : Madhab.HANAFI;
            
            PrayerTimes times = getPrayerTimes(lat, lon, madhab);
            com.batoulapps.adhan.Prayer next = times.nextPrayer();
            
            if (next == com.batoulapps.adhan.Prayer.NONE) {
                // If it is after Isha, next is tomorrow's Fajr
                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.DAY_OF_YEAR, 1);
                times = getPrayerTimes(lat, lon, cal.getTime(), madhab);
                next = com.batoulapps.adhan.Prayer.FAJR;
            }

            Date nextTime = times.timeForPrayer(next);
            String name = getPrayerNameUrdu(next);
            
            String tzId = prefs.getString("current_timezone", TimeZone.getDefault().getID());
            TimeZone tz = TimeZone.getTimeZone(tzId);
            
            return "اگلی نماز: " + name + " (" + formatTime(nextTime, tz) + ")";
        } catch (Exception e) {
            return "اذان کا انتظار ہے۔۔۔";
        }
    }

    private static String getPrayerNameUrdu(com.batoulapps.adhan.Prayer prayer) {
        switch (prayer) {
            case FAJR: return "فجر";
            case DHUHR: return "ظہر";
            case ASR: return "عصر";
            case MAGHRIB: return "مغرب";
            case ISHA: return "عشاء";
            default: return "";
        }
    }
}
