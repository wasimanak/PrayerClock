package com.alpha4technologies.prayerclock;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.widget.PopupMenu;
import androidx.cardview.widget.CardView;

public class NavigationHelper {

    private final Activity activity;
    private final int selectedIndex; // 0: Home, 1: Qibla, 2: Tasbih, 3: More
    
    private CardView navCard;
    private View navHome, navQibla, navTasbih, navMore;
    private ImageView ivHome, ivQibla, ivTasbih, ivMore;
    private TextView tvHome, tvQibla, tvTasbih, tvMore;
    
    private final Handler hideHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideRunnable;
    private final boolean isAutoHideEnabled;
    private boolean isHidden = false;

    public NavigationHelper(Activity activity, int selectedIndex, boolean isAutoHideEnabled) {
        this.activity = activity;
        this.selectedIndex = selectedIndex;
        this.isAutoHideEnabled = isAutoHideEnabled;
        
        hideRunnable = this::hideNav;
    }

    public void init() {
        navCard = activity.findViewById(R.id.navCard);
        navHome = activity.findViewById(R.id.navHome);
        navQibla = activity.findViewById(R.id.navQibla);
        navTasbih = activity.findViewById(R.id.navTasbih);
        navMore = activity.findViewById(R.id.navMore);

        ivHome = activity.findViewById(R.id.ivHome);
        ivQibla = activity.findViewById(R.id.ivQibla);
        ivTasbih = activity.findViewById(R.id.ivTasbih);
        ivMore = activity.findViewById(R.id.ivMore);

        tvHome = activity.findViewById(R.id.tvHome);
        tvQibla = activity.findViewById(R.id.tvQibla);
        tvTasbih = activity.findViewById(R.id.tvTasbih);
        tvMore = activity.findViewById(R.id.tvMore);

        setupListeners();
        updateSelection();
        if (isAutoHideEnabled) {
            startHideTimer();
        }
    }

    private void setupListeners() {
        navHome.setOnClickListener(v -> {
            if (selectedIndex != 0) {
                Intent intent = new Intent(activity, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                activity.startActivity(intent);
                activity.overridePendingTransition(0, 0);
                activity.finish();
            } else if (isAutoHideEnabled) {
                resetHideTimer();
            }
        });

        navQibla.setOnClickListener(v -> {
            if (selectedIndex != 1) {
                Intent intent = new Intent(activity, QiblaActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                activity.startActivity(intent);
                activity.overridePendingTransition(0, 0);
                activity.finish();
            } else if (isAutoHideEnabled) {
                resetHideTimer();
            }
        });

        navTasbih.setOnClickListener(v -> {
            if (selectedIndex != 2) {
                Intent intent = new Intent(activity, TasbihListActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                activity.startActivity(intent);
                activity.overridePendingTransition(0, 0);
                activity.finish();
            } else {
                resetHideTimer();
            }
        });

        navMore.setOnClickListener(this::showMoreMenu);
    }

    private void updateSelection() {
        int gold = activity.getResources().getColor(R.color.gold_accent);
        int secondary = activity.getResources().getColor(R.color.text_secondary);

        // Reset
        ivHome.setColorFilter(secondary); tvHome.setTextColor(secondary);
        ivQibla.setColorFilter(secondary); tvQibla.setTextColor(secondary);
        ivTasbih.setColorFilter(secondary); tvTasbih.setTextColor(secondary);
        ivMore.setColorFilter(secondary); tvMore.setTextColor(secondary);

        // Highlight
        switch (selectedIndex) {
            case 0: ivHome.setColorFilter(gold); tvHome.setTextColor(gold); break;
            case 1: ivQibla.setColorFilter(gold); tvQibla.setTextColor(gold); break;
            case 2: ivTasbih.setColorFilter(gold); tvTasbih.setTextColor(gold); break;
        }
    }

    private void showMoreMenu(View v) {
        resetHideTimer();
        
        com.google.android.material.bottomsheet.BottomSheetDialog bottomSheetDialog = 
            new com.google.android.material.bottomsheet.BottomSheetDialog(activity);
        
        View bottomSheetView = activity.getLayoutInflater().inflate(R.layout.layout_bottom_sheet_more, null);
        bottomSheetDialog.setContentView(bottomSheetView);

        // Make background transparent so rounded corners show
        if (bottomSheetDialog.getWindow() != null) {
             bottomSheetDialog.getWindow().findViewById(com.google.android.material.R.id.design_bottom_sheet)
                 .setBackgroundResource(android.R.color.transparent);
        }

        bottomSheetView.findViewById(R.id.btnShareApp).setOnClickListener(view -> {
            bottomSheetDialog.dismiss();
            String firebaseDownloadUrl = activity.getSharedPreferences("PrayerClockPrefs", android.content.Context.MODE_PRIVATE)
                .getString("firebase_download_url", ""); 
            ShareHelper.showShareDialog(activity, firebaseDownloadUrl);
        });

        bottomSheetView.findViewById(R.id.btnSetWallpaper).setOnClickListener(view -> {
            bottomSheetDialog.dismiss();
            Intent intent = new Intent(android.app.WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
            intent.putExtra(android.app.WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    new ComponentName(activity, PrayerWallpaperService.class));
            activity.startActivity(intent);
        });

        bottomSheetView.findViewById(R.id.btnSettings).setOnClickListener(view -> {
            bottomSheetDialog.dismiss();
            activity.startActivity(new Intent(activity, SettingsActivity.class));
        });

        bottomSheetDialog.show();
    }

    public void startHideTimer() {
        if (!isAutoHideEnabled) return;
        hideHandler.removeCallbacks(hideRunnable);
        hideHandler.postDelayed(hideRunnable, 4000);
    }

    public void resetHideTimer() {
        if (!isAutoHideEnabled) return;
        if (isHidden) {
            showNav();
        }
        startHideTimer();
    }

    private void hideNav() {
        if (navCard == null || isHidden) return;
        navCard.animate()
                .translationY(navCard.getHeight() + 100) // Slide down
                .setDuration(500)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> isHidden = true)
                .start();
    }

    private void showNav() {
        if (navCard == null || !isHidden) return;
        navCard.animate()
                .translationY(0) // Slide up
                .setDuration(500)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> isHidden = false)
                .start();
    }

    public void onDestroy() {
        hideHandler.removeCallbacks(hideRunnable);
    }
}
