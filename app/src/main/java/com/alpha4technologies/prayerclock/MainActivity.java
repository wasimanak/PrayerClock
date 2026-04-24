package com.alpha4technologies.prayerclock;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.graphics.Color;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;

import com.batoulapps.adhan.PrayerTimes;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;
import com.batoulapps.adhan.Madhab; // Import Madhab

import java.text.SimpleDateFormat;
import android.util.Log;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

public class MainActivity extends AppCompatActivity {

    private TextView tvCurrentTime, tvDate, tvIslamicDate, tvCity, tvTemp, tvLastTime;
    private View rowFajr, rowDhuhr, rowAsr, rowMaghrib, rowIsha, rowJummah;
    private FirebaseAnalytics mFirebaseAnalytics;
    private NavigationHelper navHelper;
    private PrayerTimes currentPrayerTimes;
    
    // Formatters to avoid allocations in updateTime()
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm:ss", Locale.getDefault());
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMMM yyyy", Locale.getDefault());
    private long lastIslamicDateUpdateSec = -1;

    private FusedLocationProviderClient fusedLocationClient;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable timeRunnable;

    private android.content.SharedPreferences prefs;
    // MediaPlayer removed - handled by AzanPlayerService
    
    private ActivityResultLauncher<String[]> notificationPermissionLauncher;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // --- Screen Wake Flags: ensures app opens on lock screen during Azan ---
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            // API 27+ modern API
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            );
        }
        
        // Status bar color
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(android.graphics.Color.parseColor("#1E293B"));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                View decor = window.getDecorView();
                int flags = decor.getSystemUiVisibility();
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                decor.setSystemUiVisibility(flags);
            }
        }
        
        setContentView(R.layout.activity_main);
        
        
        prefs = getSharedPreferences("PrayerClockPrefs", MODE_PRIVATE);
        
        // Setup Multi-Permission Launcher (Android 13+)
        notificationPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            result -> {
                Boolean notif = result.getOrDefault(Manifest.permission.POST_NOTIFICATIONS, false);
                Boolean loc = result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
                
                if (notif != null && notif) {

                }
                // Handle Location result from this combined request if needed
                if (loc != null && loc) {
                    requestLocation();
                } else {
                     // Fallback or just let the separate location request handle it if this was partial
                }
            }
        );

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        initViews();
        setupTicker();
        startClock();
        initFirebase(); // Track user and check update
        
        // Initialize AdMob (Ad ID will be loaded from Firebase)
        MobileAds.initialize(this, initializationStatus -> {});
        
        // Request permissions with slight delay to ensure Activity is ready
        new android.os.Handler(Looper.getMainLooper()).postDelayed(() -> {
            requestPermissionsOnStartup();
            // Android 14+: request USE_FULL_SCREEN_INTENT permission
            if (Build.VERSION.SDK_INT >= 34) {
                android.app.NotificationManager nm = getSystemService(android.app.NotificationManager.class);
                if (nm != null && !nm.canUseFullScreenIntent()) {
                    Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT);
                    intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                }
            }
            // Mandatory permissions are handled in onResume to catch changes
        }, 800);
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        // Use a small delay to ensure the activity is ready to show dialogs
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            checkMandatoryPermissions();
        }, 1000);

        // Refresh data when returning from Settings
        boolean isManual = prefs.getBoolean("manual_location", false);
        boolean hasPerm = false;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            hasPerm = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        } else {
            hasPerm = true;
        }

        if (isManual || hasPerm) {
             requestLocation();
        }
        
        // Check Startup Dialog (Ramadan/Tutorial etc)
        startupDialogShown = false; 
        checkStartupDialog();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
    }
    
    // Flag to ensure we don't show it repeatedly if user just rotates screen
    private boolean startupDialogShown = false;
    private android.app.Dialog currentRamadanDialog;
    private long lastRamadanDialogShowTime = 0;
    private String firebaseDownloadUrl = "";

    private void checkStartupDialog() {
        if (startupDialogShown) return; // Already shown this session

        // Only show once per day — compare stored date with today
        String today = new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault())
                .format(new java.util.Date());
        String lastShown = prefs.getString("startup_dialog_date", "");
        if (today.equals(lastShown)) return; // Already shown today

        if (currentPrayerTimes != null) {
            boolean isRamadan = PrayerTimeUtil.isRamadan(currentPrayerTimes, cachedLat, cachedLon);
            if (isRamadan) {
                showRamadanDialog();
            } else {
                ShareHelper.showShareDialog(this, firebaseDownloadUrl);
            }
            // Save today's date so dialog won't show again today
            prefs.edit().putString("startup_dialog_date", today).apply();
            startupDialogShown = true;
        }
    }

    private void showRamadanDialog() {
        if (isFinishing() || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed())) {
            return;
        }
        
        // Prevent stacking or rapid loops
        if (currentRamadanDialog != null && currentRamadanDialog.isShowing()) {
            return;
        }
        
        long now = System.currentTimeMillis();
        if (now - lastRamadanDialogShowTime < 3000) { // 3 seconds buffer
            return;
        }
        lastRamadanDialogShowTime = now;

        android.app.Dialog dialog = new android.app.Dialog(this);
        currentRamadanDialog = dialog; // Track it
        dialog.setContentView(R.layout.dialog_ramadan);
        dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        // dialog.setCancelable(true); // Default is true
        
        // Views
        android.view.View shareableContent = dialog.findViewById(R.id.shareableContent);
        TextView tvDate = dialog.findViewById(R.id.tvDate);
        TextView tvSehr = dialog.findViewById(R.id.tvSehrTime);
        TextView tvIftar = dialog.findViewById(R.id.tvIftarTime);
        TextView tvLocation = dialog.findViewById(R.id.tvLocation);
        android.widget.ImageView ivQr = dialog.findViewById(R.id.ivQrCode);
        Button btnShare = dialog.findViewById(R.id.btnShare);
        
        // Set Data
        String englishDate = new SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH).format(new Date());
        
        // Get Location Coords for Date Correction
        double lat = 0;
        double lon = 0;
        try {
            lat = Double.parseDouble(prefs.getString("current_lat", "0"));
            lon = Double.parseDouble(prefs.getString("current_lon", "0"));
        } catch (Exception e) { e.printStackTrace(); }

        String islamicDate = PrayerTimeUtil.getRamadanDateString(currentPrayerTimes, lat, lon);
        // Use LRM (\u200E) to force English date to render LTR correctly in mixed context
        tvDate.setText(islamicDate + "  |  " + "\u200E" + englishDate);
        
        SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
        tvSehr.setText(timeFormat.format(currentPrayerTimes.fajr));
        tvIftar.setText(timeFormat.format(currentPrayerTimes.maghrib));
        
        String city = prefs.getString("current_city", "Unknown Location");
        tvLocation.setText(city);
        
        // Generate QR
        try {
            String appLink = prefs.getString("firebase_download_url", "https://alpha4technologies.com");
            if (!firebaseDownloadUrl.isEmpty()) {
                appLink = firebaseDownloadUrl;
            }
            android.graphics.Bitmap qrBitmap = generateQRCode(appLink);
            ivQr.setImageBitmap(qrBitmap);
            
            final String shareLink = appLink;
            btnShare.setOnClickListener(v -> {
                shareRamadanCard(shareableContent, shareLink);
            });
            
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        dialog.show();
        // Width adjustment
        android.view.WindowManager.LayoutParams lp = new android.view.WindowManager.LayoutParams();
        lp.copyFrom(dialog.getWindow().getAttributes());
        lp.width = android.view.WindowManager.LayoutParams.MATCH_PARENT;
        lp.height = android.view.WindowManager.LayoutParams.WRAP_CONTENT;
        dialog.getWindow().setAttributes(lp);
    }

    private void shareRamadanCard(android.view.View view, String shareLink) {
        try {
            // 1. Capture Bitmap
            android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(
                view.getWidth(), 
                view.getHeight(), 
                android.graphics.Bitmap.Config.ARGB_8888
            );
            android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
            view.draw(canvas);
            
            // 2. Save to Cache
            java.io.File cachePath = new java.io.File(getCacheDir(), "images");
            cachePath.mkdirs(); // don't forget to make the directory
            java.io.File newFile = new java.io.File(cachePath, "ramadan_share.png");
            java.io.FileOutputStream stream = new java.io.FileOutputStream(newFile);
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream);
            stream.close();
            
            // 3. Share via Intent
            Uri contentUri = androidx.core.content.FileProvider.getUriForFile(
                this, 
                getApplicationContext().getPackageName() + ".provider", 
                newFile
            );

            if (contentUri != null) {
                Intent shareIntent = new Intent();
                shareIntent.setAction(Intent.ACTION_SEND);
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); // temp permission for receiving app to read this file
                shareIntent.setDataAndType(contentUri, getContentResolver().getType(contentUri));
                shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
                shareIntent.putExtra(Intent.EXTRA_TEXT, "Check out my Ramadan Prayer Times! \n\nDownload App: " + shareLink);
                shareIntent.setType("image/png");
                startActivity(Intent.createChooser(shareIntent, "Share Ramadan Card"));
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error sharing image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private android.graphics.Bitmap generateQRCode(String text) throws com.google.zxing.WriterException {
        com.google.zxing.common.BitMatrix result;
        try {
            result = new com.google.zxing.MultiFormatWriter().encode(text, com.google.zxing.BarcodeFormat.QR_CODE, 300, 300, null);
        } catch (IllegalArgumentException iae) {
            // Unsupported format
            return null;
        }
        int w = result.getWidth();
        int h = result.getHeight();
        int[] pixels = new int[w * h];
        for (int y = 0; y < h; y++) {
            int offset = y * w;
            for (int x = 0; x < w; x++) {
                pixels[offset + x] = result.get(x, y) ? android.graphics.Color.BLACK : android.graphics.Color.WHITE;
            }
        }
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h);
        return bitmap;
    }
    
    private void requestPermissionsOnStartup() {
        // Android 13+: Request ALL permissions together
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(new String[]{
                Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            });
        } else {
            // Android < 13
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                requestLocation();
                // Check notifications logic for existing users
                if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
                     showNotificationSettingsDialog();
                }
            } else {
                requestLocationPermissionIfNeeded();
            }
        }
    }
    
    private void showNotificationSettingsDialog() {
        new android.app.AlertDialog.Builder(this)
            .setTitle("Notifications Disabled")
            .setMessage("Notifications are disabled for this app. Please enable them in settings to hear Azan and alerts.")
            .setPositiveButton("Settings", (dialog, which) -> {
                try {
                    Intent intent = new Intent();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                        intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                    } else {
                        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(Uri.parse("package:" + getPackageName()));
                    }
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(this, "Could not open settings", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", (dialog, which) -> {
                 requestLocationPermissionIfNeeded();
            })
            .show();
    }
    
    private void requestNotificationPermissionIfNeeded() {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
             showNotificationSettingsDialog();
             return;
        }
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(new String[]{Manifest.permission.POST_NOTIFICATIONS});
            }
        }
    }
    
    private void requestLocationPermissionIfNeeded() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 200);
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == 200) {
            // Location permission result
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                requestLocation();
            }
            
            // For older devices, check notification settings AFTER location logic
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
                if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
                    showNotificationSettingsDialog();
                }
            }
        }
    }

    private void checkMandatoryPermissions() {
        if (isFinishing() || isDestroyed()) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // 1. Check Overlay Permission (Critical for Android 10+)
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Overlay Permission Required", Toast.LENGTH_LONG).show();
                showPermissionDialog("Overlay Permission Required", 
                    "This app needs 'Display over other apps' permission to open automatically during Azan time. Without this, the Azan screen will not appear when the phone is locked. Please allow it for 'PrayerClock' in the next screen.",
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                return;
            }

            // 2. Check Battery Optimization
            if (!BatteryOptimizationHelper.isIgnoringBatteryOptimizations(this)) {
                Toast.makeText(this, "Battery Optimization Setting Required", Toast.LENGTH_LONG).show();
                showPermissionDialog("Battery Optimization Required", 
                    "To ensure the Azan sounds exactly on time, the app must be allowed to run without battery restrictions. Please select 'No restrictions' or 'Allow' for 'PrayerClock' in the next screen.",
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                return;
            }

            // 3. Check Notification Permission (Android 13+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    showPermissionDialog("Notifications Required", 
                        "This app needs notification permission to play Azan and show prayer alerts. Please allow it in the next screen.",
                        Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                    return;
                }
            }
        }
    }

    private void showPermissionDialog(String title, String message, String action) {
        if (isFinishing() || isDestroyed()) return;

        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("Grant Permission", (dialog, which) -> {
                try {
                    Intent intent = new Intent(action);
                    if (Settings.ACTION_APP_NOTIFICATION_SETTINGS.equals(action)) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                        } else {
                            intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            intent.setData(Uri.parse("package:" + getPackageName()));
                        }
                    } else {
                        intent.setData(Uri.parse("package:" + getPackageName()));
                    }
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(this, "Could not open settings. Please grant " + title + " manually in App Info.", Toast.LENGTH_LONG).show();
                }
            })
            .show();
    }

    private void initFirebase() {
        try {
            FirebaseDatabase database = FirebaseDatabase.getInstance();
            DatabaseReference usersRef = database.getReference("users");
            
            // 1. Track User
            String androidId = android.provider.Settings.Secure.getString(getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
            String currentVersion = BuildConfig.VERSION_NAME;
            int versionCode = BuildConfig.VERSION_CODE;
            
            // Initialize Analytics and Crashlytics
            mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true);
            FirebaseCrashlytics.getInstance().setUserId(androidId);
            FirebaseCrashlytics.getInstance().log("App Started - ID: " + androidId);
            
            DatabaseReference myRef = usersRef.child(androidId);
            myRef.child("last_active").setValue(new Date().toString());
            myRef.child("version_code").setValue(versionCode);
            myRef.child("version_name").setValue(currentVersion);
            myRef.child("device").setValue(android.os.Build.MODEL);

            // 2. Check Force Update
            // JSON Structure: { appVersion: { versionCode: 2, downloadUrl: "url", forceUpdate: true, releaseNotes: "..." } }
            DatabaseReference versionRef = database.getReference("appVersion");
            versionRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        long serverVersionCode = 1; // Default
                        if (snapshot.child("versionCode").exists()) {
                            serverVersionCode = (long) snapshot.child("versionCode").getValue();
                        }
                        
                        boolean forceUpdate = false;
                        if (snapshot.child("forceUpdate").exists()) {
                            forceUpdate = (Boolean) snapshot.child("forceUpdate").getValue();
                        }
                        
                        String downloadUrl = "";
                        if (snapshot.child("downloadUrl").exists()) {
                            downloadUrl = snapshot.child("downloadUrl").getValue(String.class);
                            firebaseDownloadUrl = downloadUrl; // Update global variable
                            prefs.edit().putString("firebase_download_url", downloadUrl).apply();
                        }
                        
                        String releaseNotes = "A new version is available.";
                        if (snapshot.child("releaseNotes").exists()) {
                            releaseNotes = snapshot.child("releaseNotes").getValue(String.class);
                        }

                        if (versionCode < serverVersionCode && forceUpdate) {
                            showForceUpdateDialog(releaseNotes, downloadUrl);
                        }
                    }
                }

                @Override
                public void onCancelled(DatabaseError error) {
                }
            });

            // 3. Fetch Ad IDs
            database.getReference("adSettings").addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        boolean adsEnabled = true;
                        if (snapshot.child("ads_enabled").exists()) {
                            adsEnabled = (Boolean) snapshot.child("ads_enabled").getValue();
                        }
                        
                        if (adsEnabled && snapshot.child("banner_ad_id").exists()) {
                            String adId = snapshot.child("banner_ad_id").getValue(String.class);
                            loadBannerAd(adId);
                        }
                    }
                }
                @Override
                public void onCancelled(DatabaseError error) {}
            });
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showForceUpdateDialog(String message, String url) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Update Required");
        builder.setMessage(message + "\n\nYou must update to continue using the app.");
        builder.setCancelable(false);
        builder.setPositiveButton("Update Now", (dialog, which) -> {
             try {
                boolean launched = false;
                if (url != null && !url.isEmpty()) {
                    String finalUrl = url.trim();
                    if (!finalUrl.startsWith("http://") && !finalUrl.startsWith("https://")) {
                        finalUrl = "https://" + finalUrl;
                    }
                    
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setData(android.net.Uri.parse(finalUrl)); 
                        startActivity(intent);
                        launched = true;
                    } catch (Exception e) {
                        e.printStackTrace();
                        // Failed to open direct link, will try fallback
                    }
                }
                
                if (!launched) {
                     // Fallback to WhatsApp if URL missing or failed
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setData(android.net.Uri.parse("https://wa.me/+923477442050")); 
                        startActivity(intent);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
                finish(); 
             } catch(Exception e) {
                 finish();
             }
        });
        builder.show();
    }


    private void initViews() {
        tvCurrentTime = findViewById(R.id.tvCurrentTime);
        tvDate = findViewById(R.id.tvDate);
        tvIslamicDate = findViewById(R.id.tvIslamicDate);
        tvCity = findViewById(R.id.tvCity);
        tvTemp = findViewById(R.id.tvTemp);
        tvLastTime = findViewById(R.id.lasttime);
        
        navHelper = new NavigationHelper(this, 0, false);
        navHelper.init();

        View root = findViewById(R.id.root_main);
        root.setOnTouchListener((v, event) -> {
            navHelper.resetHideTimer();
            return false; // Don't consume so city gestures etc work
        });

        setupLocationGestures();

        rowFajr = findViewById(R.id.rowFajr);
        rowDhuhr = findViewById(R.id.rowDhuhr);
        rowAsr = findViewById(R.id.rowAsr);
        rowMaghrib = findViewById(R.id.rowMaghrib);
        rowIsha = findViewById(R.id.rowIsha);
        rowJummah = findViewById(R.id.rowJummah);

        setupPrayerRow(rowFajr, "فجر", "fajr");
        setupPrayerRow(rowDhuhr, "ظہر", "dhuhr");
        setupPrayerRow(rowAsr, "عصر", "asr");
        setupPrayerRow(rowMaghrib, "مغرب", "maghrib");
        setupPrayerRow(rowIsha, "عشاء", "isha");
        setupPrayerRow(rowJummah, "جمعہ", "jummah");
    }

    private void setupLocationGestures() {
        android.view.GestureDetector gestureDetector = new android.view.GestureDetector(this, new android.view.GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(android.view.MotionEvent e) {
                // Switch to Auto
                prefs.edit().putBoolean("manual_location", false).apply();
                Toast.makeText(MainActivity.this, "Switched to Auto Location", Toast.LENGTH_SHORT).show();
                requestLocation();
                return true;
            }

            @Override
            public boolean onDoubleTap(android.view.MotionEvent e) {
                showLocationSearchDialog();
                return true;
            }
        });

        tvCity.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            return true; // Consume event
        });
    }

    private void setupPrayerRow(View row, String name, String key) {
        TextView tvName = row.findViewById(R.id.tvPrayerName);
        tvName.setText(name);
        
        TextView tvJamat = row.findViewById(R.id.tvJamatTime);
        // Double click listener
        tvJamat.setOnClickListener(new DoubleClickListener() {
            @Override
            public void onDoubleClick(View v) {
                showTimePicker(tvJamat, key);
            }
        });
    }
    
    private void showLocationSearchDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Search Location");

        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("Enter City Name (e.g. Lahore)");
        builder.setView(input);

        builder.setPositiveButton("Search", (dialog, which) -> {
            String query = input.getText().toString();
            if (!query.isEmpty()) {
                performLocationSearch(query);
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void performLocationSearch(String query) {
        new Thread(() -> {
            android.location.Geocoder geocoder = new android.location.Geocoder(this, Locale.getDefault());
            try {
                java.util.List<android.location.Address> addresses = geocoder.getFromLocationName(query, 5);
                
                runOnUiThread(() -> {
                     if (addresses != null && !addresses.isEmpty()) {
                         showAddressSelectionDialog(addresses);
                     } else {
                         Toast.makeText(this, "No results found", Toast.LENGTH_SHORT).show();
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
        if (displayName == null) displayName = address.getFeatureName(); // Fallback
        
        prefs.edit()
            .putBoolean("manual_location", true)
            .putString("manual_lat", String.valueOf(address.getLatitude()))
            .putString("manual_lon", String.valueOf(address.getLongitude()))
            .putString("manual_name", displayName)
            .putString("current_lat", String.valueOf(address.getLatitude())) // Sync for wallpaper
            .putString("current_lon", String.valueOf(address.getLongitude())) // Sync for wallpaper
            .putString("current_city", displayName) // Sync for wallpaper
            .apply();
            
        Toast.makeText(this, "Manual Location Set: " + displayName, Toast.LENGTH_SHORT).show();
        requestLocation(); // Refresh with new manual data
    }
    
    // Simple Double Click Listener Helper
    public abstract class DoubleClickListener implements View.OnClickListener {
        private static final long DOUBLE_CLICK_TIME_DELTA = 300;//milliseconds
        long lastClickTime = 0;
        @Override
        public void onClick(View v) {
            long clickTime = System.currentTimeMillis();
            if (clickTime - lastClickTime < DOUBLE_CLICK_TIME_DELTA){
                onDoubleClick(v);
            }
            lastClickTime = clickTime;
        }
        public abstract void onDoubleClick(View v);
    }

    private void showTimePicker(TextView tv, String key) {
        // Custom Smart Picker with NumberPickers (1-12 Hour, 0-59 Minute)
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("جماعت کا وقت منتخب کریں");

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        layout.setGravity(android.view.Gravity.CENTER);
        layout.setPadding(30, 30, 30, 30);

        final android.widget.NumberPicker hourPicker = new android.widget.NumberPicker(this);
        hourPicker.setMinValue(1);
        hourPicker.setMaxValue(12);
        
        final android.widget.NumberPicker minutePicker = new android.widget.NumberPicker(this);
        minutePicker.setMinValue(0);
        minutePicker.setMaxValue(59);
        minutePicker.setFormatter(i -> String.format(Locale.getDefault(), "%02d", i));

        // Attempt to parse current text to set initial values
        try {
            String[] parts = tv.getText().toString().split(":");
            if(parts.length == 2) {
                int h = Integer.parseInt(parts[0]);
                int m = Integer.parseInt(parts[1]);
                hourPicker.setValue(h); // 1-12
                minutePicker.setValue(m);
            } else {
                 // Default to current time approx
                Calendar cal = Calendar.getInstance();
                int h = cal.get(Calendar.HOUR); // 0-11
                if (h == 0) h = 12;
                hourPicker.setValue(h);
                minutePicker.setValue(cal.get(Calendar.MINUTE));
            }
        } catch(Exception e) {
             hourPicker.setValue(1); // Default
             minutePicker.setValue(0);
        }

        layout.addView(hourPicker);
        
        TextView separator = new TextView(this);
        separator.setText(" : ");
        separator.setTextSize(30);
        layout.addView(separator);
        
        layout.addView(minutePicker);

        builder.setView(layout);

        builder.setPositiveButton("OK", (dialog, which) -> {
            int h12 = hourPicker.getValue();
            int m = minutePicker.getValue();
            
            // Smart Resolve to 24h
            String time24 = PrayerTimeUtil.resolveSmartTime(h12, m, key);
            
            // Validate
            int h24 = Integer.parseInt(time24.split(":")[0]);
            
            if (currentPrayerTimes != null) {
                Date adhanDate = getAdhanTimeByKey(key);
                Date nextPrayerDate = getUpperBoundTimeByKey(key);
                
                if (adhanDate != null) {
                    Calendar jamatCal = Calendar.getInstance();
                    jamatCal.setTime(adhanDate); 
                    jamatCal.set(Calendar.HOUR_OF_DAY, h24);
                    jamatCal.set(Calendar.MINUTE, m);
                    
                    // Handle Isha rollover logic if needed (already in resolveSmartTime somewhat)
                    // But here we need to align dates.
                    // If jamatCal turned out to be BEFORE adhan on SAME day, check if it's overnight case.
                    // Case: Adhan 8 PM, set Jamat 3 AM. 3 AM < 8 PM.
                    if (key.equalsIgnoreCase("isha") && jamatCal.getTime().before(adhanDate)) {
                         jamatCal.add(Calendar.DAY_OF_YEAR, 1);
                    }
                    
                    // Validation 1: Jamat cannot be before Adhan (on same logical day)
                    // If we added day for Isha, this check should pass.
                    // But if Adhan 5 AM (Fajr) and user sets 4 AM, it's invalid.
                    if (jamatCal.getTime().before(adhanDate)) {
                        Toast.makeText(MainActivity.this, "جماعت کا وقت اذان سے پہلے نہیں ہو سکتا", Toast.LENGTH_LONG).show();
                        return;
                    }
                    
                    // Validation 2: Jamat cannot be after next prayer logic
                    if (nextPrayerDate != null && jamatCal.getTime().after(nextPrayerDate)) {
                         String limitName = getLimitNameByKey(key);
                         Toast.makeText(MainActivity.this, "جماعت کا وقت " + limitName + " سے پہلے ہونا چاہئے", Toast.LENGTH_LONG).show();
                         return;
                    }
                }
            }
            
            String displayTime = h12 + ":" + String.format(Locale.getDefault(), "%02d", m);
            tv.setText(displayTime);
            prefs.edit().putString("jamat_" + key, time24).apply();
            
            // Reschedule alarm immediately
            if (currentPrayerTimes != null) {
                AlarmHelper.scheduleAllAlarms(this);
                Toast.makeText(MainActivity.this, "Jamat Time updated", Toast.LENGTH_SHORT).show();
            }
        });
        
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private Date getAdhanTimeByKey(String key) {
        switch(key) {
            case "fajr": return currentPrayerTimes.fajr;
            case "dhuhr": return currentPrayerTimes.dhuhr;
            case "asr": return currentPrayerTimes.asr;
            case "maghrib": return currentPrayerTimes.maghrib;
            case "isha": return currentPrayerTimes.isha;
            case "jummah": return currentPrayerTimes.dhuhr; 
            default: return null;
        }
    }

    private void setupTicker() {
        TextView marqueeText = findViewById(R.id.marqueeText);
        marqueeText.setSelected(true); 
    }

    private void startClock() {
        timeRunnable = new Runnable() {
            @Override
            public void run() {
                updateTime();
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(timeRunnable);
    }

    private void updateTime() {
        long nowMs = System.currentTimeMillis();
        long nowSec = nowMs / 1000;
        Date now = new Date(nowMs);

        tvCurrentTime.setText(timeFormat.format(now));
        tvDate.setText(dateFormat.format(now));
        
        if (currentPrayerTimes != null) {
             // Update Islamic Date & Prayer info once every 10 seconds or on change
             // to reduce CPU usage. Clock is updated every second above.
             if (nowSec % 10 == 0 || lastIslamicDateUpdateSec == -1) {
                 lastIslamicDateUpdateSec = nowSec;
                 
                 String islamicDate = PrayerTimeUtil.getRamadanDateString(currentPrayerTimes, cachedLat, cachedLon);
                 tvIslamicDate.setText(islamicDate);

                 String tzId = prefs.getString("current_timezone", TimeZone.getDefault().getID());
                 TimeZone tz = TimeZone.getTimeZone(tzId);
                 
                 String remainingText = PrayerTimeUtil.getRemainingTimeUrdu(currentPrayerTimes, tz);
                 tvLastTime.setText(remainingText);
                 
                 // Check if it is Zawal time (Red Color)
                 if (remainingText.contains("زوال") || remainingText.contains("ممنوع")) {
                     tvLastTime.setTextColor(ActivityCompat.getColor(this, R.color.led_red));
                 } else {
                     tvLastTime.setTextColor(ActivityCompat.getColor(this, R.color.emerald_500));
                 }
                 
                 highlightCurrentPrayer();
                 checkStartupDialog();
             }
        }
    }

    private void highlightCurrentPrayer() {
        if (currentPrayerTimes == null) return;
        
        com.batoulapps.adhan.Prayer current = currentPrayerTimes.currentPrayer(new Date());
        com.batoulapps.adhan.Prayer next = currentPrayerTimes.nextPrayer(new Date());
        
        // Reset all backgrounds
        rowFajr.setBackground(null);
        rowDhuhr.setBackground(null);
        rowAsr.setBackground(null);
        rowMaghrib.setBackground(null);
        rowIsha.setBackground(null);
        rowJummah.setBackground(null);
        
        // Determine which row to highlight
        View rowToHighlight = null;
        
        if (current == com.batoulapps.adhan.Prayer.FAJR) {
            rowToHighlight = rowFajr;
        } else if (current == com.batoulapps.adhan.Prayer.DHUHR) {
            // Check if Friday
            Calendar cal = Calendar.getInstance();
            boolean isFriday = cal.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY;
            if (isFriday) {
                rowToHighlight = rowJummah;
            } else {
                rowToHighlight = rowDhuhr;
            }
        } else if (current == com.batoulapps.adhan.Prayer.ASR) {
            rowToHighlight = rowAsr;
        } else if (current == com.batoulapps.adhan.Prayer.MAGHRIB) {
            rowToHighlight = rowMaghrib;
        } else if (current == com.batoulapps.adhan.Prayer.ISHA) {
            rowToHighlight = rowIsha;
        }
        
        if (rowToHighlight != null) {
            // Use modern highlight drawable
            rowToHighlight.setBackgroundResource(R.drawable.bg_prayer_highlight_modern);
        }
    }
    
    private void scheduleAzanAlarms(PrayerTimes times, TimeZone tz) {
        AlarmHelper.scheduleAllAlarms(this);
    }

    private void requestLocation() {
        // Check Manual Mode first
        if (prefs.getBoolean("manual_location", false)) {
            String latStr = prefs.getString("manual_lat", "0");
            String lonStr = prefs.getString("manual_lon", "0");
            String name = prefs.getString("manual_name", "Manual Location");
            
            double lat = Double.parseDouble(latStr);
            double lon = Double.parseDouble(lonStr);
            
            Location manualLoc = new Location("manual"); // Mock location
            manualLoc.setLatitude(lat);
            manualLoc.setLongitude(lon);
            
            tvCity.setText(name + " (Manual)");
            updatePrayerTimes(manualLoc);
            fetchWeather(lat, lon);
            
            // Also save current lat/lon for wallpaper synchronization if manual
             prefs.edit()
                .putString("current_lat", String.valueOf(lat))
                .putString("current_lon", String.valueOf(lon))
                .putString("current_city", name)
                .apply();
            return;
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
             requestPermissionLauncher.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
            return;
        }
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        if (location != null) {
                            updateLocationName(location);
                            updatePrayerTimes(location);
                            fetchWeather(location.getLatitude(), location.getLongitude());
                            
                            // Save for Wallpaper AND Fallback (Auto location)
                            prefs.edit()
                                .putString("current_lat", String.valueOf(location.getLatitude()))
                                .putString("current_lon", String.valueOf(location.getLongitude()))
                                .apply();
                        } else {
                            // FALLBACK: Try to load last known good location from Prefs
                            String lastLat = prefs.getString("current_lat", null);
                            String lastLon = prefs.getString("current_lon", null);
                            String lastCity = prefs.getString("current_city", "Unknown Location");
                            
                            if (lastLat != null && lastLon != null) {
                                try {
                                    double lat = Double.parseDouble(lastLat);
                                    double lon = Double.parseDouble(lastLon);
                                    
                                    Location fallbackLoc = new Location("fallback");
                                    fallbackLoc.setLatitude(lat);
                                    fallbackLoc.setLongitude(lon);
                                    
                                    tvCity.setText(lastCity + " (Offline)");
                                    updatePrayerTimes(fallbackLoc);
                                    // Don't fetch weather online if no location logic (likely no internet/gps)
                                    // But we can try if internet works but gps doesn't.
                                    fetchWeather(lat, lon); 
                                    
                                    Toast.makeText(MainActivity.this, "Using last known location", Toast.LENGTH_SHORT).show();
                                    
                                } catch (NumberFormatException e) {
                                     tvCity.setText("Location not found (Auto)");
                                }
                            } else {
                                if (!prefs.getBoolean("manual_location", false)) {
                                    tvCity.setText("Location not found (Auto)");
                                }
                            }
                        }
                    }
                });
    }

    private void fetchWeather(double lat, double lon) {
        new Thread(() -> {
            try {
                // Request timezone as well
                String urlString = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon + "&current_weather=true&timezone=auto";
                java.net.URL url = new java.net.URL(urlString);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                
                java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream()));
                String inputLine;
                StringBuilder content = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    content.append(inputLine);
                }
                in.close();
                conn.disconnect();
                
                org.json.JSONObject json = new org.json.JSONObject(content.toString());
                
                // Parse Timezone
                if (json.has("timezone")) {
                    String tz = json.getString("timezone");
                    prefs.edit().putString("current_timezone", tz).apply();
                }

                if (json.has("current_weather")) {
                    org.json.JSONObject current = json.getJSONObject("current_weather");
                    double temp = current.getDouble("temperature");
                     
                     runOnUiThread(() -> {
                         tvTemp.setText(temp + "°C");
                         // Refresh times with new timezone
                         updatePrayerTimes(new Location("manual")); // Passing logic location irrelevant for timezone refresh
                     });
                }
                
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void updateLocationName(Location location) {
        android.location.Geocoder geocoder = new android.location.Geocoder(this, Locale.getDefault());
        try {
            java.util.List<android.location.Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            if (addresses != null && !addresses.isEmpty()) {
                String city = addresses.get(0).getLocality();
                if (city == null) city = addresses.get(0).getSubAdminArea();
                if (city == null) city = "Unknown City";
                tvCity.setText(city);
                
                // Save for Wallpaper
                prefs.edit().putString("current_city", city).apply();
            } else {
                tvCity.setText(String.format("Lat: %.2f, Lon: %.2f", location.getLatitude(), location.getLongitude()));
            }
        } catch (java.io.IOException e) {
            e.printStackTrace();
             tvCity.setText(String.format("Lat: %.2f, Lon: %.2f", location.getLatitude(), location.getLongitude()));
        }
    }
    
    // Return the time limit for Jamat
    private Date getUpperBoundTimeByKey(String key) {
        switch(key) {
            case "fajr": return currentPrayerTimes.sunrise;
            case "dhuhr": return currentPrayerTimes.asr; // Before Asr
            case "asr": return currentPrayerTimes.maghrib; // Before Maghrib
            case "maghrib": return currentPrayerTimes.isha; // Before Isha
            case "jummah": return currentPrayerTimes.asr; // Before Asr
            case "isha": 
                // Before Next Fajr
                 Calendar cal = Calendar.getInstance();
                 cal.add(Calendar.DAY_OF_YEAR, 1);
                 String madhabStr = prefs.getString("madhab", "HANAFI");
                 Madhab madhab = madhabStr.equals("SHAFI") ? Madhab.SHAFI : Madhab.HANAFI;
                 return (cachedLat != 0) ? PrayerTimeUtil.getPrayerTimes(cachedLat, cachedLon, cal.getTime(), madhab).fajr : null;
            default: return null;
        }
    }
    
    private String getLimitNameByKey(String key) {
        switch(key) {
             case "fajr": return "طلوع آفتاب";
             case "dhuhr": return "عصر";
             case "asr": return "مغرب";
             case "maghrib": return "عشاء";
             case "jummah": return "عصر";
             case "isha": return "فجر";
             default: return "";
        }
    }

    private final ActivityResultLauncher<String[]> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), isGranted -> {
                boolean granted = isGranted.containsValue(true);
                if (granted) {
                    requestLocation();
                } else {
                    Toast.makeText(this, "Location permission needed for Prayer Times", Toast.LENGTH_SHORT).show();
                }
            });

    private double cachedLat = 0;
    private double cachedLon = 0;

    private void updatePrayerTimes(Location location) {
        // Use cached location if passed location is dummy/manual-refresh
        if (location.getProvider().equals("manual") && location.getLatitude() == 0 && cachedLat != 0) {
            // keep cached
        } else {
            cachedLat = location.getLatitude();
            cachedLon = location.getLongitude();
        }
        
        String madhabStr = prefs.getString("madhab", "HANAFI");
        Madhab madhab = madhabStr.equals("SHAFI") ? Madhab.SHAFI : Madhab.HANAFI;
        
        currentPrayerTimes = PrayerTimeUtil.getPrayerTimes(cachedLat, cachedLon, madhab);
        
        // Get TimeZone
        String tzId = prefs.getString("current_timezone", TimeZone.getDefault().getID());
        TimeZone tz = TimeZone.getTimeZone(tzId);

        updateRow(rowFajr, currentPrayerTimes.fajr, 5, "fajr", tz);
        updateRow(rowDhuhr, currentPrayerTimes.dhuhr, 5, "dhuhr", tz);
        updateRow(rowAsr, currentPrayerTimes.asr, 5, "asr", tz);
        updateRow(rowMaghrib, currentPrayerTimes.maghrib, 5, "maghrib", tz);
        updateRow(rowIsha, currentPrayerTimes.isha, 5, "isha", tz);
        
        // Jummah default
        updateRow(rowJummah, currentPrayerTimes.dhuhr, 5, "jummah", tz); 

        // Update Ticker
        TextView marqueeText = findViewById(R.id.marqueeText);
        marqueeText.setText(PrayerTimeUtil.generateUrduTicker(currentPrayerTimes, cachedLat, cachedLon, tz));
        
        // Schedule Alarms
        scheduleAzanAlarms(currentPrayerTimes, tz);
    }

    private void updateRow(View row, Date adhanTime, int jamatOffset, String key, TimeZone tz) {
        TextView tvAdhan = row.findViewById(R.id.tvAdhanTime);
        TextView tvJamat = row.findViewById(R.id.tvJamatTime);
        TextView btnMuteAzan = row.findViewById(R.id.btnMuteAzan);
        
        tvAdhan.setText(PrayerTimeUtil.formatTime(adhanTime, tz));
        
        // Mute Azan Logic
        if (btnMuteAzan != null) {
            String mutePrefKey = "mute_azan_" + key;
            boolean isMuted = prefs.getBoolean(mutePrefKey, false);
            btnMuteAzan.setText(isMuted ? "🔇" : "🔊");
            
            btnMuteAzan.setOnClickListener(v -> {
                boolean currentlyMuted = prefs.getBoolean(mutePrefKey, false);
                boolean newMutedState = !currentlyMuted;
                prefs.edit().putBoolean(mutePrefKey, newMutedState).apply();
                btnMuteAzan.setText(newMutedState ? "🔇" : "🔊");
                
                String prayerTitle = key.substring(0, 1).toUpperCase() + key.substring(1);
                android.widget.Toast.makeText(MainActivity.this, prayerTitle + " Azan " + (newMutedState ? "Muted" : "Unmuted"), android.widget.Toast.LENGTH_SHORT).show();
            });
        }

        // Use smart Jamat string helper
        String savedJamat = prefs.getString("jamat_" + key, null);
        tvJamat.setText(PrayerTimeUtil.getJamatTimeStr(adhanTime, savedJamat, jamatOffset, tz));
    }



    private void checkBatteryOptimization() {
        BatteryOptimizationHelper.checkAndRequestOptimization(this);
    }


    private long backPressedTime;
    private android.widget.Toast backToast;

    @Override
    public void onBackPressed() {
        if (backPressedTime + 2000 > System.currentTimeMillis()) {
            if (backToast != null) {
                backToast.cancel();
            }
            super.onBackPressed();
            return;
        } else {
            backToast = android.widget.Toast.makeText(getBaseContext(), "Press back again to exit", android.widget.Toast.LENGTH_SHORT);
            backToast.show();
        }
        backPressedTime = System.currentTimeMillis();
    }

    private void loadBannerAd(String adUnitId) {
        if (adUnitId == null || adUnitId.isEmpty()) return;
        
        try {
            android.widget.FrameLayout adContainer = findViewById(R.id.adContainer);
            if (adContainer == null) return;
            
            com.google.android.gms.ads.AdView adView = new com.google.android.gms.ads.AdView(this);
            adUnitId = adUnitId.trim();
            adView.setAdUnitId(adUnitId);
            adView.setAdSize(com.google.android.gms.ads.AdSize.BANNER);
            
            adContainer.removeAllViews();
            adContainer.addView(adView);
            
            com.google.android.gms.ads.AdRequest adRequest = new com.google.android.gms.ads.AdRequest.Builder().build();
            adView.loadAd(adRequest);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}