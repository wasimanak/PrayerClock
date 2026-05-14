package com.alpha4technologies.prayerclock;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.content.Intent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

public class TasbihCounterActivity extends AppCompatActivity {

    TextView tvName, tvCount, btnSoundToggle;
    ImageView btnShare;
    View root;
    private AdView mAdView;

    TasbihModel tasbih;
    SharedPreferences prefs;
    Gson gson = new Gson();
    
    boolean isSoundEnabled = true;
    MediaPlayer mediaPlayer;
    private NavigationHelper navHelper;
    
    private String firebaseAyahArabic = "فَاذْكُرُونِي أَذْكُرْكُمْ";
    private String firebaseAyahUrdu = "تم میرا ذکر کرو، میں تمہارا ذکر کروں گا";

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // ===== Fullscreen + Transparent Status Bar =====
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.TRANSPARENT);
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            );
        } else {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        setContentView(R.layout.activity_tasbih_counter);

        tvName = findViewById(R.id.tvTasbihName);
        tvCount = findViewById(R.id.tvCounter);
        btnSoundToggle = findViewById(R.id.btnSoundToggle);
        btnShare = findViewById(R.id.btnShare);
        root = findViewById(R.id.rootLayout);

        tasbih = (TasbihModel) getIntent().getSerializableExtra("tasbih");

        if (tasbih == null) {
            finish();
            return;
        }

        prefs = getSharedPreferences("TasbihPrefs", MODE_PRIVATE);
        isSoundEnabled = prefs.getBoolean("sound_enabled", true);
        checkDayChange();
        updateSoundToggleUI();
        
        try {
            mediaPlayer = MediaPlayer.create(this, R.raw.click);
        } catch (Exception e) {
            mediaPlayer = null;
        }

        // Initial setup
        tvName.setText(tasbih.name);
        formatAndDisplayCount();

        // Back button removed per user request
        
        // Sound toggle button
        btnSoundToggle.setOnClickListener(v -> {
            isSoundEnabled = !isSoundEnabled;
            prefs.edit().putBoolean("sound_enabled", isSoundEnabled).apply();
            updateSoundToggleUI();
        });

        btnShare.setOnClickListener(v -> {
            shareTasbih();
        });

        navHelper = new NavigationHelper(this, 2, true);
        navHelper.init();

        // 🔥 Tap anywhere → +1
        root.setOnClickListener(v -> {
            navHelper.resetHideTimer();
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            playSound();
            tasbih.count++;
            tasbih.todayCount++;
            updateUI();
        });

        // 🔥 Long press anywhere → -1
        root.setOnLongClickListener(v -> {
            navHelper.resetHideTimer();
            if (tasbih.count > 0) {
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                playSound();
                tasbih.count--;
                if (tasbih.todayCount > 0) tasbih.todayCount--;
                updateUI();
            }
            return true;
        });

        // Initialize Ads (Loaded from Firebase)
        MobileAds.initialize(this, initializationStatus -> {});
        
        com.google.firebase.database.FirebaseDatabase.getInstance().getReference("adSettings")
            .addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
                @Override
                public void onDataChange(com.google.firebase.database.DataSnapshot snapshot) {
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
                public void onCancelled(com.google.firebase.database.DatabaseError error) {}
            });
            
        // Fetch Daily Ayat (Multiple entries)
        com.google.firebase.database.FirebaseDatabase.getInstance().getReference("dailyAyat")
            .addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
                @Override
                public void onDataChange(com.google.firebase.database.DataSnapshot snapshot) {
                    if (snapshot.exists() && snapshot.hasChildren()) {
                        java.util.List<com.google.firebase.database.DataSnapshot> items = new java.util.ArrayList<>();
                        for (com.google.firebase.database.DataSnapshot child : snapshot.getChildren()) {
                            items.add(child);
                        }
                        
                        if (!items.isEmpty()) {
                            int randomIndex = new java.util.Random().nextInt(items.size());
                            com.google.firebase.database.DataSnapshot selected = items.get(randomIndex);
                            
                            if (selected.child("arabic").exists()) {
                                firebaseAyahArabic = selected.child("arabic").getValue(String.class);
                            }
                            if (selected.child("urdu").exists()) {
                                firebaseAyahUrdu = selected.child("urdu").getValue(String.class);
                            }
                        }
                    }
                }
                @Override
                public void onCancelled(com.google.firebase.database.DatabaseError error) {}
            });
    }
    
    private void playSound() {
        if (isSoundEnabled && mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.seekTo(0);
                }
                mediaPlayer.start();
            } catch (Exception e) {
                // gracefully ignore if custom file is invalid
            }
        }
    }
    
    private void updateSoundToggleUI() {
        btnSoundToggle.setText(isSoundEnabled ? "🔊" : "🔇");
    }

    private void formatAndDisplayCount() {
        NumberFormat formatter = NumberFormat.getNumberInstance(Locale.US);
        tvCount.setText(formatter.format(tasbih.count));
    }

    private void updateUI() {
        checkDayChange();
        tasbih.updatedAt = System.currentTimeMillis();
        formatAndDisplayCount();

        // Save locally offline to SharedPreferences
        String json = prefs.getString("list", null);
        if (json != null) {
            Type type = new TypeToken<ArrayList<TasbihModel>>() {}.getType();
            ArrayList<TasbihModel> loadedList = gson.fromJson(json, type);
            if (loadedList != null) {
                for (int i = 0; i < loadedList.size(); i++) {
                    if (loadedList.get(i).id.equals(tasbih.id)) {
                        loadedList.set(i, tasbih);
                        break;
                    }
                }
                prefs.edit().putString("list", gson.toJson(loadedList)).apply();
            }
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (navHelper != null) {
            navHelper.onDestroy();
        }
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        overridePendingTransition(0, 0);
        finish();
    }

    private void loadBannerAd(String adUnitId) {
        if (adUnitId == null || adUnitId.isEmpty()) return;
        
        try {
            android.widget.FrameLayout adContainer = findViewById(R.id.adContainer);
            if (adContainer == null) return;
            
            com.google.android.gms.ads.AdView adView = new com.google.android.gms.ads.AdView(this);
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

    private void checkDayChange() {
        Calendar now = Calendar.getInstance();
        Calendar last = Calendar.getInstance();
        last.setTimeInMillis(tasbih.lastResetDate);

        if (now.get(Calendar.YEAR) != last.get(Calendar.YEAR) ||
            now.get(Calendar.DAY_OF_YEAR) != last.get(Calendar.DAY_OF_YEAR)) {
            
            // It's a new day
            tasbih.yesterdayCount = tasbih.todayCount;
            tasbih.todayCount = 0;
            tasbih.lastResetDate = System.currentTimeMillis();
            // We'll save this update in updateUI() call that usually follows
        }
    }

    private void shareTasbih() {
        ShareHelper.shareTasbih(this, tasbih, firebaseAyahArabic, firebaseAyahUrdu);
    }
}
