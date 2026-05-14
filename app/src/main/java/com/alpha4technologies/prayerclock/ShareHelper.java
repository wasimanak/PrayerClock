package com.alpha4technologies.prayerclock;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.batoulapps.adhan.Madhab;
import com.batoulapps.adhan.PrayerTimes;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class ShareHelper {

    public static void showShareDialog(Activity activity, String firebaseDownloadUrl) {
        // Compute PrayerTimes for today
        SharedPreferences prefs = activity.getSharedPreferences("PrayerClockPrefs", Context.MODE_PRIVATE);
        String latStr = prefs.getString("current_lat", "0");
        String lonStr = prefs.getString("current_lon", "0");
        String city = prefs.getString("current_city", "Unknown Location");
        String tzId = prefs.getString("current_timezone", TimeZone.getDefault().getID());

        double lat = 0, lon = 0;
        try {
            lat = Double.parseDouble(latStr);
            lon = Double.parseDouble(lonStr);
        } catch (NumberFormatException ignored) {}

        String madhabStr = prefs.getString("madhab", "HANAFI");
        Madhab madhab = madhabStr.equals("SHAFI") ? Madhab.SHAFI : Madhab.HANAFI;

        TimeZone tz = TimeZone.getTimeZone(tzId);
        PrayerTimes todayTimes = PrayerTimeUtil.getPrayerTimes(lat, lon, madhab, tz);

        // Prepare Dialog
        android.app.Dialog dialog = new android.app.Dialog(activity);
        dialog.setContentView(R.layout.dialog_share_app);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        View shareableContent = dialog.findViewById(R.id.shareableContent);
        TextView tvLocation = dialog.findViewById(R.id.tvLocation);
        TextView tvDate = dialog.findViewById(R.id.tvDate);
        TextView tvSunriseTime = dialog.findViewById(R.id.tvSunriseTime);
        TextView tvSunsetTime = dialog.findViewById(R.id.tvSunsetTime);
        ImageView ivQrCode = dialog.findViewById(R.id.ivQrCode);
        Button btnShare = dialog.findViewById(R.id.btnShare);

        // Populate Data
        String englishDate = new SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH).format(new Date());
        String islamicDate = PrayerTimeUtil.getRamadanDateString(todayTimes, lat, lon, tz);
        tvDate.setText(islamicDate + "  |  \u200E" + englishDate);

        tvLocation.setText(city);

        SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
        timeFormat.setTimeZone(tz);
        
        if (todayTimes != null) {
            tvSunriseTime.setText(timeFormat.format(todayTimes.sunrise));
            tvSunsetTime.setText(timeFormat.format(todayTimes.maghrib));
        }

        // Generate QR Code
        String appLink = prefs.getString("firebase_download_url", "https://alpha4technologies.com"); // No Play Store fallback
        if (firebaseDownloadUrl != null && !firebaseDownloadUrl.isEmpty()) {
            appLink = firebaseDownloadUrl;
        }

        try {
            Bitmap qrBitmap = generateQRCode(appLink);
            if (qrBitmap != null) {
                ivQrCode.setImageBitmap(qrBitmap);
            }

            final String shareLink = appLink;
            btnShare.setOnClickListener(v -> shareImageContent(activity, shareableContent, shareLink));

        } catch (Exception e) {
            e.printStackTrace();
        }

        dialog.show();

        // Adjust Width
        if (dialog.getWindow() != null) {
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
            lp.copyFrom(dialog.getWindow().getAttributes());
            lp.width = WindowManager.LayoutParams.MATCH_PARENT;
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            dialog.getWindow().setAttributes(lp);
        }
    }

    private static void shareImageContent(Activity activity, View view, String shareLink) {
        try {
            Bitmap bitmap = Bitmap.createBitmap(
                    view.getWidth(),
                    view.getHeight(),
                    Bitmap.Config.ARGB_8888
            );
            Canvas canvas = new Canvas(bitmap);
            view.draw(canvas);

            File cachePath = new File(activity.getCacheDir(), "images");
            cachePath.mkdirs();
            File newFile = new File(cachePath, "share_app.png");
            
            FileOutputStream stream = new FileOutputStream(newFile);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            stream.close();

            Uri contentUri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".provider", newFile);

            if (contentUri != null) {
                Intent shareIntent = new Intent();
                shareIntent.setAction(Intent.ACTION_SEND);
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                shareIntent.setDataAndType(contentUri, activity.getContentResolver().getType(contentUri));
                shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
                shareIntent.putExtra(Intent.EXTRA_TEXT, "Accurate Prayer Times & Qibla Compass! \n\nDownload the app: " + shareLink);
                shareIntent.setType("image/png");
                activity.startActivity(Intent.createChooser(shareIntent, "Share App"));
            }

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(activity, "Error sharing image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }



    public static void shareTasbih(Activity activity, TasbihModel tasbih, String ayahArabic, String ayahUrdu) {
        SharedPreferences prefs = activity.getSharedPreferences("PrayerClockPrefs", Context.MODE_PRIVATE);
        
        android.app.Dialog dialog = new android.app.Dialog(activity);
        dialog.setContentView(R.layout.dialog_share_tasbih);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        View shareableContent = dialog.findViewById(R.id.shareableContent);
        TextView tvTasbihName = dialog.findViewById(R.id.tvTasbihName);
        TextView tvDate = dialog.findViewById(R.id.tvDate);
        TextView tvTodayCount = dialog.findViewById(R.id.tvTodayCount);
        TextView tvYesterdayCount = dialog.findViewById(R.id.tvYesterdayCount);
        TextView tvTotalCount = dialog.findViewById(R.id.tvTotalCount);
        TextView tvAyahArabic = dialog.findViewById(R.id.tvAyahArabic);
        TextView tvAyahUrdu = dialog.findViewById(R.id.tvAyahUrdu);
        ImageView ivQrCode = dialog.findViewById(R.id.ivQrCode);
        Button btnShareImage = dialog.findViewById(R.id.btnShareImage);

        // Populate Data
        tvTasbihName.setText(tasbih.name);
        String englishDate = new SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH).format(new Date());
        tvDate.setText(englishDate);

        java.text.NumberFormat nf = java.text.NumberFormat.getNumberInstance(Locale.US);
        tvTodayCount.setText(nf.format(tasbih.todayCount));
        tvYesterdayCount.setText(nf.format(tasbih.yesterdayCount));
        tvTotalCount.setText(nf.format(tasbih.count));

        // Ayah from Firebase (or fallback)
        tvAyahArabic.setText(ayahArabic);
        tvAyahUrdu.setText(ayahUrdu);

        // Generate QR Code
        String appLink = prefs.getString("firebase_download_url", "https://alpha4technologies.com");
        try {
            Bitmap qrBitmap = generateQRCode(appLink);
            if (qrBitmap != null) {
                ivQrCode.setImageBitmap(qrBitmap);
            }

            final String shareLink = appLink;
            btnShareImage.setOnClickListener(v -> {
                shareImageContent(activity, shareableContent, shareLink, "tasbih_share.png", "Check out my Tasbih count! \n\nDownload App: " + shareLink);
            });

        } catch (Exception e) {
            e.printStackTrace();
        }

        dialog.show();

        // Adjust Width
        if (dialog.getWindow() != null) {
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
            lp.copyFrom(dialog.getWindow().getAttributes());
            lp.width = WindowManager.LayoutParams.MATCH_PARENT;
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            dialog.getWindow().setAttributes(lp);
        }
    }

    private static void shareImageContent(Activity activity, View view, String shareLink, String fileName, String extraText) {
        try {
            Bitmap bitmap = Bitmap.createBitmap(
                    view.getWidth(),
                    view.getHeight(),
                    Bitmap.Config.ARGB_8888
            );
            Canvas canvas = new Canvas(bitmap);
            view.draw(canvas);

            File cachePath = new File(activity.getCacheDir(), "images");
            cachePath.mkdirs();
            File newFile = new File(cachePath, fileName);
            
            FileOutputStream stream = new FileOutputStream(newFile);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            stream.close();

            Uri contentUri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".provider", newFile);

            if (contentUri != null) {
                Intent shareIntent = new Intent();
                shareIntent.setAction(Intent.ACTION_SEND);
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                shareIntent.setDataAndType(contentUri, activity.getContentResolver().getType(contentUri));
                shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
                shareIntent.putExtra(Intent.EXTRA_TEXT, extraText);
                shareIntent.setType("image/png");
                
                // Specific package for WhatsApp if needed, but better to use chooser and let user pick
                activity.startActivity(Intent.createChooser(shareIntent, "Share Image"));
            }

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(activity, "Error sharing image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private static Bitmap generateQRCode(String text) throws WriterException {
        BitMatrix result;
        try {
            result = new MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, 300, 300, null);
        } catch (IllegalArgumentException iae) {
            return null;
        }
        int w = result.getWidth();
        int h = result.getHeight();
        int[] pixels = new int[w * h];
        for (int y = 0; y < h; y++) {
            int offset = y * w;
            for (int x = 0; x < w; x++) {
                pixels[offset + x] = result.get(x, y) ? Color.BLACK : Color.WHITE;
            }
        }
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h);
        return bitmap;
    }
}
