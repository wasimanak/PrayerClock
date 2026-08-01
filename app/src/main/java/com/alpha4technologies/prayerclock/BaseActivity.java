package com.alpha4technologies.prayerclock;

import android.content.Context;
import android.content.res.Configuration;

import androidx.appcompat.app.AppCompatActivity;

/**
 * BaseActivity — Locks font scale to 1.0f (normal size) so that 
 * if a user increases font size in system settings, the app UI doesn't break or overlap.
 * All other activities must extend this class instead of AppCompatActivity.
 */
public class BaseActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context newBase) {
        // Force fontScale to 1.0f (normal) so system font size changes do not affect this app
        Configuration override = new Configuration(newBase.getResources().getConfiguration());
        override.fontScale = 1.0f;
        Context context = newBase.createConfigurationContext(override);
        super.attachBaseContext(context);
    }
}
