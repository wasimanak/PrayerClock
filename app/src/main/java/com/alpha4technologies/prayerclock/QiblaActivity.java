package com.alpha4technologies.prayerclock;

import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.TextView;
import android.content.Intent;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import android.app.Activity;
import android.os.Build;
import android.view.Window;
import android.view.WindowManager;
import android.graphics.Color;
import android.hardware.GeomagneticField;
import android.widget.ImageButton;

public class QiblaActivity extends AppCompatActivity implements SensorEventListener {

    private ImageView ivCompassDial, ivQiblaNeedle;
    private TextView tvDegrees, tvQiblaDegrees;
    private android.view.View btnBack;
    private NavigationHelper navHelper;

    private SensorManager sensorManager;
    private Sensor accelerometer, magnetometer;

    private float[] filteredAccelerometer = new float[3];
    private float[] filteredMagnetometer = new float[3];
    private boolean lastAccelerometerSet = false;
    private boolean lastMagnetometerSet = false;
    private static final float ALPHA = 0.02f; // Stronger smoothing for GAME delay

    private float currentDegree = 0f;
    private double qiblaDegree = 0;
    private float declination = 0f;

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
        
        setContentView(R.layout.activity_qibla);

        ivCompassDial = findViewById(R.id.ivCompassDial);
        ivQiblaNeedle = findViewById(R.id.ivQiblaNeedle);
        tvDegrees = findViewById(R.id.tvDegrees);
        tvQiblaDegrees = findViewById(R.id.tvQiblaDegrees);

        navHelper = new NavigationHelper(this, 1, true);
        navHelper.init();

        View root = findViewById(R.id.root_qibla);
        root.setOnTouchListener((v, event) -> {
            navHelper.resetHideTimer();
            return false;
        });

// Back button removed from UI for cleaner look

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        calculateQibla();
    }

    private void calculateQibla() {
        SharedPreferences prefs = getSharedPreferences("PrayerClockPrefs", MODE_PRIVATE);
        try {
            double lat = Double.parseDouble(prefs.getString("current_lat", "0"));
            double lon = Double.parseDouble(prefs.getString("current_lon", "0"));

            if (lat == 0 && lon == 0) {
                Toast.makeText(this, "Location not set. Please enable location on main screen.", Toast.LENGTH_LONG).show();
                return;
            }

            // Mecca Coordinates
            double meccaLat = 21.422487;
            double meccaLon = 39.826206;

            double userLatRad = Math.toRadians(lat);
            double userLonRad = Math.toRadians(lon);
            double meccaLatRad = Math.toRadians(meccaLat);
            double meccaLonRad = Math.toRadians(meccaLon);

            double deltaLon = meccaLonRad - userLonRad;

            double y = Math.sin(deltaLon);
            double x = Math.cos(userLatRad) * Math.tan(meccaLatRad) - Math.sin(userLatRad) * Math.cos(deltaLon);
            
            qiblaDegree = (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;

            // Calculate Magnetic Declination
            GeomagneticField geoField = new GeomagneticField(
                    (float) lat,
                    (float) lon,
                    (float) 0, // Altitude (optional, 0 is fine)
                    System.currentTimeMillis()
            );
            declination = geoField.getDeclination();
            
            tvQiblaDegrees.setText(String.format("قبلہ کا رخ: %.1f°", qiblaDegree));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        }
        if (magnetometer != null) {
            sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (navHelper != null) {
            navHelper.onDestroy();
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor == accelerometer) {
            // Apply Low-Pass Filter
            for (int i = 0; i < 3; i++) {
                filteredAccelerometer[i] = filteredAccelerometer[i] + ALPHA * (event.values[i] - filteredAccelerometer[i]);
            }
            lastAccelerometerSet = true;
        } else if (event.sensor == magnetometer) {
            // Apply Low-Pass Filter
            for (int i = 0; i < 3; i++) {
                filteredMagnetometer[i] = filteredMagnetometer[i] + ALPHA * (event.values[i] - filteredMagnetometer[i]);
            }
            lastMagnetometerSet = true;
        }

        if (lastAccelerometerSet && lastMagnetometerSet) {
            float[] R = new float[9];
            float[] outR = new float[9];
            float[] orientation = new float[3];
            if (SensorManager.getRotationMatrix(R, null, filteredAccelerometer, filteredMagnetometer)) {
                
                // Use Pitch to decide if we should remap for Vertical or Flat usage
                // This prevents the "shifting" issue when tilting the phone
                float pitch = (float) Math.abs(Math.toDegrees(Math.asin(-R[7]))); 
                
                if (pitch > 45) {
                    // Vertical-ish: Remap X -> X, Y -> Z
                    SensorManager.remapCoordinateSystem(R, SensorManager.AXIS_X, SensorManager.AXIS_Z, outR);
                } else {
                    // Flat-ish: No remap needed or X -> X, Y -> Y
                    System.arraycopy(R, 0, outR, 0, 9);
                }
                
                SensorManager.getOrientation(outR, orientation);
                float azimuthInRadians = orientation[0];
                float azimuthInDegrees = (float) (Math.toDegrees(azimuthInRadians) + 360) % 360;

                // True North = Magnetic North + Declination
                azimuthInDegrees = (azimuthInDegrees + declination + 360) % 360;

                // Apply Smooth Rotation to Dial
                // We use setRotation here for immediate feedback, the filter handles the smoothness
                ivCompassDial.setRotation(-azimuthInDegrees);

                // Needle rotation should be (qiblaDegree - azimuthInDegrees)
                float needleRotation = (float) (qiblaDegree - azimuthInDegrees + 360) % 360;
                ivQiblaNeedle.setRotation(needleRotation);
                
                tvDegrees.setText(String.format("%.0f° %s", azimuthInDegrees, getDirectionName(azimuthInDegrees)));
                
                currentDegree = -azimuthInDegrees;
            }
        }
    }

    private String getDirectionName(float degrees) {
        if (degrees >= 337.5 || degrees < 22.5) return "North";
        if (degrees >= 22.5 && degrees < 67.5) return "North East";
        if (degrees >= 67.5 && degrees < 112.5) return "East";
        if (degrees >= 112.5 && degrees < 157.5) return "South East";
        if (degrees >= 157.5 && degrees < 202.5) return "South";
        if (degrees >= 202.5 && degrees < 247.5) return "South West";
        if (degrees >= 247.5 && degrees < 292.5) return "West";
        if (degrees >= 292.5 && degrees < 337.5) return "North West";
        return "";
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        overridePendingTransition(0, 0);
        finish();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        if (sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE || accuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW) {
                Toast.makeText(this, "Please calibrate sensor by moving the device in an 8-shape", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
