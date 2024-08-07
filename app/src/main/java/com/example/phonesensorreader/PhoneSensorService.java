package com.example.phonesensorreader;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Build;
import android.util.Log;
import android.os.PowerManager;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class PhoneSensorService extends Service implements SensorEventListener {
    private static final String LOG_TAG = "PhoneSensorService";
    private static final String CHANNEL_ID = "SensorDataChannel";
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor gyroscope;
    private static final int SAMPLING_INTERVAL_MICROSECONDS = 100000; // 10Hz
    private static final int MAX_TIME_RECORDING_IN_SECONDS = 60; // 5 minutes

    private HashMap<String, ArrayList<Float>> aggregatedData;
    private long lastSampleTime;
    private PowerManager.WakeLock wakeLock;

    public static final String ACTION_SENSOR_DATA = "com.example.phonesensorreader.ACTION_SENSOR_DATA";
    public static final String EXTRA_SENSOR_TYPE = "com.example.phonesensorreader.EXTRA_SENSOR_TYPE";
    public static final String EXTRA_SENSOR_VALUES = "com.example.phonesensorreader.EXTRA_SENSOR_VALUES";
    private float avgAccX;
    private float avgAccY;
    private float avgAccZ;
    private float avgGyroX;
    private float avgGyroY;
    private float avgGyroZ;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(LOG_TAG, "Service onCreate");
        aggregatedData = new HashMap<>();
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);


        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

            if (accelerometer != null) {
                sensorManager.registerListener(this, accelerometer, SAMPLING_INTERVAL_MICROSECONDS);
            }
            if (gyroscope != null) {
                sensorManager.registerListener(this, gyroscope, SAMPLING_INTERVAL_MICROSECONDS);
            }
        }
        // Acquire a partial wake lock to keep the CPU running even when the screen is off
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PhoneSensorService::WakeLock");
            wakeLock.acquire(MAX_TIME_RECORDING_IN_SECONDS * 1000L);
        }
    }


    @SuppressLint("ForegroundServiceType")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Sensor Data Service")
                .setContentText("Collecting sensor data...")
                .setSmallIcon(R.drawable.notification)
                .build();

        startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        Log.d(LOG_TAG, "Service started");
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sensorManager.unregisterListener(this);
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        computeAndLogStatistics(); // Log the final aggregated results
        Log.d(LOG_TAG, "Service onDestroy");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

       long currentTime = System.currentTimeMillis();
        if (currentTime - lastSampleTime < 100) {
            return; // Skip if less than 100ms has passed since the last sample
        }
        lastSampleTime = currentTime;
        String keyPrefix;

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            keyPrefix = "ACC";
        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            keyPrefix = "GYRO";
        } else {
            return; // Ignore other sensor types
        }
        addAggregatedMeasurement(keyPrefix + "_X", event.values[0]);
        addAggregatedMeasurement(keyPrefix + "_Y", event.values[1]);
        addAggregatedMeasurement(keyPrefix + "_Z", event.values[2]);

        // Truncate sensor values to 6 decimal places before broadcasting
        float[] truncatedValues = new float[event.values.length];
        for (int i = 0; i < event.values.length; i++) {
            truncatedValues[i] = new BigDecimal(event.values[i]).setScale(6, RoundingMode.HALF_UP).floatValue();
        }


        Log.d(LOG_TAG, "Broadcasting sensor data: " + Arrays.toString(truncatedValues));
        Intent intent = new Intent(ACTION_SENSOR_DATA);
        intent.putExtra(EXTRA_SENSOR_TYPE, event.sensor.getType());
        intent.putExtra(EXTRA_SENSOR_VALUES, truncatedValues);
        sendBroadcast(intent);
    }

    private void addAggregatedMeasurement(String key, float measurement) {
        if (aggregatedData == null) {
            Log.e(LOG_TAG, "Can't add measurement. Aggregated data bundle is null");
            return;
        }

        // Retrieve or create the list for the given key
        ArrayList<Float> measurements = aggregatedData.get(key);
        if (measurements == null) {
            measurements = new ArrayList<>();
            aggregatedData.put(key, measurements);
        }

        float roundedMeasurement = new BigDecimal(measurement).setScale(6, RoundingMode.HALF_UP).floatValue();
        measurements.add(roundedMeasurement);
    }

    private void computeAndLogStatistics() {
        for (String key : aggregatedData.keySet()) {
            ArrayList<Float> values = aggregatedData.get(key);
            if (values.isEmpty()) continue;

            float sum = 0, min = Float.MAX_VALUE, max = Float.MIN_VALUE;
            for (float value : values) {
                sum += value;
                if (value < min) min = value;
                if (value > max) max = value;
            }
            float avg = sum / values.size();

            float varianceSum = 0;
            for (float value : values) {
                varianceSum += Math.pow(value - avg, 2);
            }
            float stdDev = (float) Math.sqrt(varianceSum / values.size());
            float roundedAvg = new BigDecimal(avg).setScale(6, RoundingMode.HALF_UP).floatValue();

            Log.d(LOG_TAG, String.format(Locale.US, "%s: Avg=%.6f, Min=%.6f, Max=%.6f, StdDev=%.6f", key, avg, min, max, stdDev));
            // Store the calculated averages in the corresponding ArrayLists
            switch (key) {
                case "ACC_X":
                    avgAccX=roundedAvg;
                    break;
                case "ACC_Y":
                    avgAccY=roundedAvg;
                    break;
                case "ACC_Z":
                    avgAccZ=roundedAvg;
                    break;
                case "GYRO_X":
                    avgGyroX=roundedAvg;
                    break;
                case "GYRO_Y":
                    avgGyroY=roundedAvg;
                    break;
                case "GYRO_Z":
                    avgGyroZ=roundedAvg;
                    break;
            }
        }
        // Clear the data after computing statistics
       // aggregatedData.clear();
        Log.d(LOG_TAG, String.format(Locale.US, "AccX=%.6f, AccY=%.6f, AccZ=%.6f, GyroX=%.6f, GyroY=%.6f, GyroZ=%.6f", avgAccX, avgAccY, avgAccZ, avgGyroX, avgGyroY, avgGyroZ));
        sendSensorDataToWebView();
    }
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Do something here if sensor accuracy changes
    }

    private void createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Sensor Data Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    private void sendSensorDataToServer(String prompt) {
        SharedPreferences sharedPreferences = this.getSharedPreferences("APP_PREFS", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("PROMPT", prompt);
        editor.apply();
    }

    private void sendSensorDataToWebView() {
        try {

            String systemPrompt = "Q: Based on the readings from the accelerometer (ACCX, ACCY, ACCZ) and gyroscope sensors (GYROX, GYROY, GYROZ). can we determine if the user is running, walking, or sitting?" +
                    "Here are some key points to consider for each activity:" +
                    "AccX=0.885337, AccY=2.491991, AccZ=9.620627, GyroX=0.012555, GyroY=-0.016721, GyroZ=-0.007155?" +
                    "A: You are currently in a sitting position." +
                    "Q: Generate some quick exercises to do to get the blood flowing again after a long time of sitting?" +
                    "A: Here are some quick exercises to get your blood flowing after sitting:" +
                    "Seated Marches: Lift your knees alternately while seated for 1-2 minutes." +
                    "Chair Twists: Sit up straight and twist your torso to each side for 3-5 times." +
                    "Neck Rolls: Roll your head in circles for 30 seconds in each direction." +
                    "Q: Based on the readings from the accelerometer (ACCX, ACCY, ACCZ) and gyroscope sensors (GYROX, GYROY, GYROZ). can we determine if the user is running, walking, or sitting?";
                    String accData = "ACC_X:" + avgAccX + ",ACC_Y:" + avgAccY + ",ACC_Z:" + avgAccZ;
                    String gyroData = ",GYRO_X:" + avgGyroX + ",GYRO_Y:" + avgGyroY + ",GYRO_Z:" + avgGyroZ;

            String fullPrompt = systemPrompt + " " + accData + gyroData;
            Log.d(LOG_TAG, "Prepared sensor data JSON: " + fullPrompt);
            sendSensorDataToServer(fullPrompt);

        } catch (Exception e) {
            e.printStackTrace();
            Log.e(LOG_TAG, "Error preparing sensor data JSON", e);
       }
    }
}




