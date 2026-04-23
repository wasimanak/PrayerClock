package com.alpha4technologies.prayerclock;

import android.app.Application;
import androidx.appcompat.app.AppCompatDelegate;

public class PrayerClockApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Force Light Mode globally
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
    }
}
