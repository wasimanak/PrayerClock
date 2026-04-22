package com.pksofter.prayerclock;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.batoulapps.adhan.Madhab;
import com.batoulapps.adhan.PrayerTimes;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import android.os.Build;
import android.view.Window;
import android.view.WindowManager;

public class JamatSettingsActivity extends AppCompatActivity {

    private SharedPreferences prefs;
    private PrayerTimes currentPrayerTimes;

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
        
        setContentView(R.layout.activity_jamat_settings);

        prefs = getSharedPreferences("PrayerClockPrefs", MODE_PRIVATE);

        loadCurrentPrayerTimes();

        setupRow(findViewById(R.id.rowFajr), "فجر", "fajr");
        setupRow(findViewById(R.id.rowDhuhr), "ظہر", "dhuhr");
        setupRow(findViewById(R.id.rowAsr), "عصر", "asr");
        setupRow(findViewById(R.id.rowMaghrib), "مغرب", "maghrib");
        setupRow(findViewById(R.id.rowIsha), "عشاء", "isha");
        setupRow(findViewById(R.id.rowJummah), "جمعہ", "jummah");
    }

    private void loadCurrentPrayerTimes() {
        double lat = 0;
        double lon = 0;
        try {
            lat = Double.parseDouble(prefs.getString("current_lat", "0"));
            lon = Double.parseDouble(prefs.getString("current_lon", "0"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        String madhabStr = prefs.getString("madhab", "HANAFI");
        Madhab madhab = madhabStr.equals("SHAFI") ? Madhab.SHAFI : Madhab.HANAFI;
        
        currentPrayerTimes = PrayerTimeUtil.getPrayerTimes(lat, lon, madhab);
    }

    private void setupRow(View row, String name, String key) {
        TextView tvName = row.findViewById(R.id.tvPrayerName);
        tvName.setText(name);

        TextView tvAdhan = row.findViewById(R.id.tvAdhanTime);
        if (currentPrayerTimes != null) {
            Date adhanDate = getAdhanTimeByKey(key);
            if (adhanDate != null) {
                tvAdhan.setText("Adhan: " + PrayerTimeUtil.formatTime(adhanDate));
            } else {
                tvAdhan.setText("Adhan: --:--");
            }
        } else {
            tvAdhan.setText("Adhan: --:--");
        }

        TextView tvJamat = row.findViewById(R.id.tvJamatTime);
        updateJamatTimeDisplay(tvJamat, key);

        row.setOnClickListener(v -> showTimePicker(tvJamat, key));
    }

    private void updateJamatTimeDisplay(TextView tvJamat, String key) {
        String savedJamat24 = prefs.getString("jamat_" + key, "");
        if (savedJamat24.isEmpty()) {
            tvJamat.setText("--:--");
        } else {
            tvJamat.setText(PrayerTimeUtil.convertTo12Hour(savedJamat24));
        }
    }

    private void showTimePicker(TextView tv, String key) {
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

        try {
            String timeText = tv.getText().toString();
            if (timeText.contains(":")) {
                String[] parts = timeText.split(":");
                int h = Integer.parseInt(parts[0]);
                int m = Integer.parseInt(parts[1]);
                hourPicker.setValue(h);
                minutePicker.setValue(m);
            } else {
                Calendar cal = Calendar.getInstance();
                int h = cal.get(Calendar.HOUR);
                if (h == 0) h = 12;
                hourPicker.setValue(h);
                minutePicker.setValue(cal.get(Calendar.MINUTE));
            }
        } catch (Exception e) {
            hourPicker.setValue(1);
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

            String time24 = PrayerTimeUtil.resolveSmartTime(h12, m, key);
            int h24 = Integer.parseInt(time24.split(":")[0]);

            if (currentPrayerTimes != null) {
                Date adhanDate = getAdhanTimeByKey(key);
                Date nextPrayerDate = getUpperBoundTimeByKey(key);

                if (adhanDate != null) {
                    Calendar jamatCal = Calendar.getInstance();
                    jamatCal.setTime(adhanDate);
                    jamatCal.set(Calendar.HOUR_OF_DAY, h24);
                    jamatCal.set(Calendar.MINUTE, m);

                    if (key.equalsIgnoreCase("isha") && jamatCal.getTime().before(adhanDate)) {
                        jamatCal.add(Calendar.DAY_OF_YEAR, 1);
                    }

                    if (jamatCal.getTime().before(adhanDate)) {
                        Toast.makeText(this, "جماعت کا وقت اذان سے پہلے نہیں ہو سکتا", Toast.LENGTH_LONG).show();
                        return;
                    }

                    if (nextPrayerDate != null && jamatCal.getTime().after(nextPrayerDate)) {
                        String limitName = getLimitNameByKey(key);
                        Toast.makeText(this, "جماعت کا وقت " + limitName + " سے پہلے ہونا چاہئے", Toast.LENGTH_LONG).show();
                        return;
                    }
                }
            }

            String displayTime = h12 + ":" + String.format(Locale.getDefault(), "%02d", m);
            tv.setText(displayTime);
            prefs.edit().putString("jamat_" + key, time24).apply();

            if (currentPrayerTimes != null) {
                AlarmHelper.scheduleAllAlarms(this);
                Toast.makeText(this, "Jamat Time updated", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private Date getAdhanTimeByKey(String key) {
        if (currentPrayerTimes == null) return null;
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

    private Date getUpperBoundTimeByKey(String key) {
        if (currentPrayerTimes == null) return null;
        switch(key) {
            case "fajr": return currentPrayerTimes.sunrise;
            case "dhuhr": return currentPrayerTimes.asr;
            case "asr": return currentPrayerTimes.maghrib;
            case "maghrib": return currentPrayerTimes.isha;
            case "isha":
            case "jummah":
                return null;
            default: return null;
        }
    }

    private String getLimitNameByKey(String key) {
        switch(key) {
            case "fajr": return "طلوع آفتاب";
            case "dhuhr": return "عصر";
            case "asr": return "مغرب";
            case "maghrib": return "عشاء";
            default: return "اگلی نماز";
        }
    }
}
