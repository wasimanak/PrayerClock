package com.alpha4technologies.prayerclock;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageView;
import android.content.Intent;
import android.net.Uri;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Build;
import android.view.Window;
import android.view.WindowManager;
import android.graphics.Color;

import com.batoulapps.adhan.Madhab;

public class SettingsActivity extends AppCompatActivity {

    private RadioGroup rgMadhab;
    private RadioButton rbHanafi, rbShafi;
    private Switch switchAutoLocation;
    
    // New Search UI
    private android.widget.LinearLayout llManualSearch;
    private android.widget.EditText etSearchQuery;
    private Button btnSearch;
    private Button btnEditJamatTimes;
    
    private TextView tvCurrentLocationInfo;
    
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Match status bar to theme
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(android.graphics.Color.parseColor("#1E293B"));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                View decor = window.getDecorView();
                int flags = decor.getSystemUiVisibility();
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                decor.setSystemUiVisibility(flags);
            }
        }
        
        setContentView(R.layout.activity_settings);
        
        try {
            String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            android.widget.TextView tvVersion = findViewById(R.id.tvAppVersion);
            if (tvVersion != null) {
                tvVersion.setText("Version " + versionName);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        prefs = getSharedPreferences("PrayerClockPrefs", MODE_PRIVATE);
        
        initViews();
        loadSettings();
        setupListeners();
    }
    
    private void initViews() {
        rgMadhab = findViewById(R.id.rgMadhab);
        rbHanafi = findViewById(R.id.rbHanafi);
        rbShafi = findViewById(R.id.rbShafi);
        switchAutoLocation = findViewById(R.id.switchAutoLocation);
        
        llManualSearch = findViewById(R.id.llManualSearch);
        etSearchQuery = findViewById(R.id.etSearchQuery);
        btnSearch = findViewById(R.id.btnSearch);
        btnEditJamatTimes = findViewById(R.id.btnEditJamatTimes);
        
        tvCurrentLocationInfo = findViewById(R.id.tvCurrentLocationInfo);
    }
    
    private void loadSettings() {
        // Madhab
        String madhab = prefs.getString("madhab", "HANAFI"); 
        
        if (madhab.equals("SHAFI")) {
            rbShafi.setChecked(true);
        } else {
            rbHanafi.setChecked(true); 
        }
        
        // Location
        boolean isManual = prefs.getBoolean("manual_location", false);
        switchAutoLocation.setChecked(!isManual);
        
        updateSearchVisibility(isManual);
        
        // Info
        String city = prefs.getString("current_city", "Unknown");
        tvCurrentLocationInfo.setText("Current: " + city + (isManual ? " (Manual)" : " (Auto)"));
    }
    
    private void setupListeners() {
        rgMadhab.setOnCheckedChangeListener((group, checkedId) -> {
            String value = "HANAFI";
            if (checkedId == R.id.rbShafi) {
                value = "SHAFI";
            }
            prefs.edit().putString("madhab", value).apply();
            Toast.makeText(this, "Calculation Method Updated", Toast.LENGTH_SHORT).show();
        });
        
        switchAutoLocation.setOnCheckedChangeListener((buttonView, isChecked) -> {
            boolean isAuto = isChecked; 
            
            if (isAuto) {
                // Switch to Auto
                prefs.edit().putBoolean("manual_location", false).apply();
                updateSearchVisibility(false);
                Toast.makeText(this, "Switched to Auto Location. Waiting for GPS...", Toast.LENGTH_SHORT).show();
            } else {
                // Switch to Manual
                prefs.edit().putBoolean("manual_location", true).apply();
                updateSearchVisibility(true);
                // Don't show toast, user sees search box
            }
        });
        
        btnSearch.setOnClickListener(v -> {
             String query = etSearchQuery.getText().toString().trim();
             if (!query.isEmpty()) {
                 performLocationSearch(query);
             } else {
                 Toast.makeText(this, "Please enter a city name", Toast.LENGTH_SHORT).show();
             }
        });
        
        btnEditJamatTimes.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(this, JamatSettingsActivity.class);
            startActivity(intent);
        });
        
        ImageView btnFacebook = findViewById(R.id.btnFacebook);
        ImageView btnInstagram = findViewById(R.id.btnInstagram);
        ImageView btnWhatsapp = findViewById(R.id.btnWhatsapp);
        TextView tvPrivacyPolicy = findViewById(R.id.tvPrivacyPolicy);
        tvPrivacyPolicy.setPaintFlags(tvPrivacyPolicy.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);

        tvPrivacyPolicy.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://wasimanak.github.io/prayerclock-privacy-policy"));
            startActivity(intent);
        });

        btnFacebook.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://facebook.com/wasimalik0"));
            startActivity(intent);
        });

        btnInstagram.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://instagram.com/cediscoveries"));
            startActivity(intent);
        });

        btnWhatsapp.setOnClickListener(v -> {
            try {
                String number = "+923477442050";
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse("https://wa.me/" + number));
                startActivity(intent);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
    
    private void updateSearchVisibility(boolean isManual) {
        llManualSearch.setVisibility(isManual ? View.VISIBLE : View.GONE);
    }
    
    private void performLocationSearch(String query) {
        Toast.makeText(this, "Searching...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            android.location.Geocoder geocoder = new android.location.Geocoder(this, java.util.Locale.getDefault());
            try {
                java.util.List<android.location.Address> addresses = geocoder.getFromLocationName(query, 5);
                
                runOnUiThread(() -> {
                     if (addresses != null && !addresses.isEmpty()) {
                         showAddressSelectionDialog(addresses);
                     } else {
                         Toast.makeText(this, "No results found for '" + query + "'", Toast.LENGTH_SHORT).show();
                     }
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "Search Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
    
    private void showAddressSelectionDialog(java.util.List<android.location.Address> addresses) {
        String[] locationNames = new String[addresses.size()];
        for (int i = 0; i < addresses.size(); i++) {
            android.location.Address addr = addresses.get(i);
            StringBuilder sb = new StringBuilder();
            if (addr.getLocality() != null) sb.append(addr.getLocality()).append(", ");
            if (addr.getAdminArea() != null) sb.append(addr.getAdminArea()).append(", ");
            if (addr.getCountryName() != null) sb.append(addr.getCountryName());
            locationNames[i] = sb.toString();
        }

        new android.app.AlertDialog.Builder(this)
            .setTitle("Select Location")
            .setItems(locationNames, (dialog, which) -> {
                android.location.Address selected = addresses.get(which);
                saveManualLocation(selected);
            })
            .show();
    }

    private void saveManualLocation(android.location.Address address) {
        String displayName = address.getLocality();
        if (displayName == null) displayName = address.getFeatureName();
        if (displayName == null) displayName = "Manual Location";
        
        prefs.edit()
            .putBoolean("manual_location", true)
            .putString("manual_lat", String.valueOf(address.getLatitude()))
            .putString("manual_lon", String.valueOf(address.getLongitude()))
            .putString("manual_name", displayName)
            .putString("current_lat", String.valueOf(address.getLatitude())) 
            .putString("current_lon", String.valueOf(address.getLongitude())) 
            .putString("current_city", displayName) 
            .apply();
            
        Toast.makeText(this, "Saved: " + displayName, Toast.LENGTH_SHORT).show();
        tvCurrentLocationInfo.setText("Current: " + displayName + " (Manual)");
        etSearchQuery.setText(""); // Clear search
    }
}
