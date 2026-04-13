package com.pksofter.prayerclock;

import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.service.wallpaper.WallpaperService;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;

import com.batoulapps.adhan.PrayerTimes;
import com.batoulapps.adhan.Madhab;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class PrayerWallpaperService extends WallpaperService {

    @Override
    public Engine onCreateEngine() {
        return new PrayerEngine();
    }

    private class PrayerEngine extends Engine {
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Runnable drawRunner = this::draw;
        
        private View mView;
        private boolean visible = true;
        
        private SharedPreferences prefs;
        
        // Data
        private PrayerTimes prayerTimes;
        private String city = "Loading...";
        private String tickerText = "";
        private TimeZone timeZone;
        
        // Ticker State
        private float tickerX = 0;
        private TextView tickerTextView;

        PrayerEngine() {
            prefs = getSharedPreferences("PrayerClockPrefs", MODE_PRIVATE);
            
            // Inflate Layout
            LayoutInflater inflater = LayoutInflater.from(PrayerWallpaperService.this);
            mView = inflater.inflate(R.layout.layout_wallpaper, null);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            this.visible = visible;
            if (visible) {
                updateData();
                handler.post(drawRunner);
            } else {
                handler.removeCallbacks(drawRunner);
            }
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            super.onSurfaceDestroyed(holder);
            this.visible = false;
            handler.removeCallbacks(drawRunner);
        }
        
        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
             super.onSurfaceChanged(holder, format, width, height);
             // Trigger layout
             layoutView(width, height);
             draw();
        }
        
        private void layoutView(int width, int height) {
            if (mView == null || width <= 0 || height <= 0) return;
            
            // Measure
            int widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY);
            int heightSpec = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY);
            mView.measure(widthSpec, heightSpec);
            
            // Layout
            mView.layout(0, 0, width, height);
            
            // Init ticker pos if needed
            if (tickerTextView == null) {
                tickerTextView = mView.findViewById(R.id.marqueeText);
                tickerX = -9999; 
            }
        }

        private void updateData() {
            if (mView == null) return;

            // Read Prefs
            String latStr = prefs.getString("current_lat", "31.5204"); 
            String lonStr = prefs.getString("current_lon", "74.3587");
            city = prefs.getString("current_city", "Loading...");
            
            double lat = Double.parseDouble(latStr);
            double lon = Double.parseDouble(lonStr);
            
            String tzId = prefs.getString("current_timezone", TimeZone.getDefault().getID());
            timeZone = TimeZone.getTimeZone(tzId);
            
            String madhabStr = prefs.getString("madhab", "HANAFI");
            Madhab madhab = madhabStr.equals("SHAFI") ? Madhab.SHAFI : Madhab.HANAFI;
            
            prayerTimes = PrayerTimeUtil.getPrayerTimes(lat, lon, madhab);
            tickerText = PrayerTimeUtil.generateUrduTicker(prayerTimes, lat, lon, timeZone);
            
            // Update Views
            updateViews();
        }
        
        private void updateViews() {
            // City
            TextView tvCity = mView.findViewById(R.id.tvCity);
            tvCity.setText(city);
            
            // Clock & Date
            TextView tvDate = mView.findViewById(R.id.tvDate);
            SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault());
            dateFormat.setTimeZone(timeZone != null ? timeZone : TimeZone.getDefault());
            tvDate.setText(dateFormat.format(new Date()));
            
            // Ticker (Prepare for manual draw)
            tickerTextView = mView.findViewById(R.id.marqueeText);
            tickerTextView.setText(tickerText);
            tickerTextView.setVisibility(View.INVISIBLE); // Hide view, draw manually
            
            // Prayer List
            if (prayerTimes != null) {
                // Determine Current Prayer
                String currentKey = getCurrentPrayerKey();

                // View Rows
                updateRow(mView.findViewById(R.id.rowFajr), "fajr", "فجر", prayerTimes.fajr, 3, currentKey.equals("fajr"));
                updateRowValues(mView.findViewById(R.id.rowDhuhr), "dhuhr", "ظہر", prayerTimes.dhuhr, 3, currentKey.equals("dhuhr"));
                updateRow(mView.findViewById(R.id.rowAsr), "asr", "عصر", prayerTimes.asr, 3, currentKey.equals("asr"));
                updateRow(mView.findViewById(R.id.rowMaghrib), "maghrib", "مغرب", prayerTimes.maghrib, 3, currentKey.equals("maghrib"));
                updateRow(mView.findViewById(R.id.rowIsha), "isha", "عشاء", prayerTimes.isha, 3, currentKey.equals("isha"));
                updateRowValues(mView.findViewById(R.id.rowJummah), "jummah", "جمعہ", prayerTimes.dhuhr, 45, currentKey.equals("jummah"));
                
                // Jummah Visibility Logic
                Calendar cal = Calendar.getInstance(); 
                // Use System Time for Friday check, not prayer time object which varies
                boolean isFriday = cal.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY;
                
                mView.findViewById(R.id.rowDhuhr).setVisibility(isFriday ? View.GONE : View.VISIBLE);
                mView.findViewById(R.id.rowJummah).setVisibility(isFriday ? View.VISIBLE : View.GONE);
                
                // Next Prayer Countdown
                TextView tvLastRequest = mView.findViewById(R.id.lasttime);
                tvLastRequest.setVisibility(View.VISIBLE);
            }
        }
        
        private void updateRow(View row, String key, String nameUrdu, Date adhanTime, int offset, boolean isCurrent) {
            updateRowValues(row, key, nameUrdu, adhanTime, offset, isCurrent);
        }

        private void updateRowValues(View row, String key, String nameUrdu, Date adhanTime, int offset, boolean isCurrent) {
            if (row == null) return;
            
            TextView tvAdhan = row.findViewById(R.id.tvAdhanTime);
            TextView tvJamat = row.findViewById(R.id.tvJamatTime);
            TextView tvName = row.findViewById(R.id.tvPrayerName);
            
            // Name
            tvName.setText(nameUrdu);
            
            // Times
            String adhanStr = PrayerTimeUtil.formatTime(adhanTime, timeZone);
            tvAdhan.setText(adhanStr);
            
            String savedJamat = prefs.getString("jamat_" + key, null);
            String jamatStr;
            if (savedJamat != null) {
                jamatStr = PrayerTimeUtil.convertTo12Hour(savedJamat);
            } else {
                jamatStr = PrayerTimeUtil.getJamatTime(adhanTime, offset, timeZone);
            }
            tvJamat.setText(jamatStr);
            
            // Highlight
            if (isCurrent) {
                row.setBackgroundResource(R.drawable.bg_prayer_highlight_modern); 
                 try {
                     row.setBackground(ContextCompat.getDrawable(PrayerWallpaperService.this, R.drawable.bg_prayer_highlight_modern));
                 } catch (Exception e) {
                     row.setBackgroundColor(Color.parseColor("#334155")); // Slate 800 fallback
                 }
            } else {
                row.setBackground(null);
            }
        }

        private void draw() {
            SurfaceHolder holder = getSurfaceHolder();
            Canvas canvas = null;
            try {
                canvas = holder.lockCanvas();
                if (canvas != null) {
                    if (mView == null) return;
                    
                    int width = canvas.getWidth();
                    int height = canvas.getHeight();
                    
                    // Update Clock
                    TextView tvTime = mView.findViewById(R.id.tvCurrentTime);
                    SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm:ss", Locale.getDefault());
                    timeFormat.setTimeZone(timeZone != null ? timeZone : TimeZone.getDefault());
                    tvTime.setText(timeFormat.format(new Date()));
                    
                    // Update Countdown
                    if (prayerTimes != null) {
                        TextView tvLastRequest = mView.findViewById(R.id.lasttime);
                        String countdown = PrayerTimeUtil.getRemainingTimeUrdu(prayerTimes, timeZone);
                        if (countdown.isEmpty()) {
                             tvLastRequest.setText("..."); // Placeholder or hide
                             tvLastRequest.setVisibility(View.INVISIBLE);
                        } else {
                             tvLastRequest.setText(countdown);
                             tvLastRequest.setVisibility(View.VISIBLE);
                        }
                    }

                    // Layout
                    layoutView(width, height);
                    
                    // Draw View Hierarchy
                    mView.draw(canvas);
                    
                    // Draw Ticker Manually
                    if (tickerTextView != null) {
                         Paint paint = tickerTextView.getPaint();
                         paint.setColor(tickerTextView.getCurrentTextColor());
                         float textWidth = paint.measureText(tickerText);
                         
                         // Init
                         if (tickerX == -9999) tickerX = -textWidth;
                         
                         tickerX += 5.0f; // Speed
                         if (tickerX > width) tickerX = -textWidth;
                         
                         // Calculate Y position
                         // footerSection -> tickerTextView
                         View footer = mView.findViewById(R.id.footerSection);
                         float globalY = footer.getTop() + tickerTextView.getTop() + tickerTextView.getBaseline();
                         
                         canvas.drawText(tickerText, tickerX, globalY, paint);
                    }
                }
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            
            handler.removeCallbacks(drawRunner);
            if (visible) {
                handler.postDelayed(drawRunner, 33); 
            }
        }
        
        private String getCurrentPrayerKey() {
            if (prayerTimes == null) return "";
            long now = System.currentTimeMillis();
            Date d = new Date(now);
            
            if (d.after(prayerTimes.isha)) return "isha";
            if (d.after(prayerTimes.maghrib)) return "maghrib";
            if (d.after(prayerTimes.asr)) return "asr";
            if (d.after(prayerTimes.dhuhr)) return "dhuhr"; 
            if (d.after(prayerTimes.sunrise)) return "sunrise";
            if (d.after(prayerTimes.fajr)) return "fajr";
            
            return "isha"; 
        }
    }
}
