package com.example.phonesensorreader;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
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

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
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
    private static final int NUM_SAMPLES_IN_SESSION = 800;
    private static final int MAX_TIME_RECORDING_IN_SECONDS = 30; // 5 minutes
    private HashMap<String, ArrayList<Float>> highFreqData;
    private ArrayList<String> timestamps;
    private BufferedWriter bufferedWriter;
    private Handler handler;
    private long lastSampleTime;
    private PowerManager.WakeLock wakeLock;
    //private float[] accelOutput = null;
    //private float[] gyroOutput = null;
    //static final float ALPHA = 0.25f; // if ALPHA = 1 OR 0, no filter applies.

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(LOG_TAG, "Service onCreate");
        highFreqData = new HashMap<>();
        timestamps = new ArrayList<>();
        handler = new Handler();
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

        createCsvFile();
        handler.postDelayed(stopDataCollection, MAX_TIME_RECORDING_IN_SECONDS * 1000);
        // Acquire a partial wake lock to keep the CPU running even when the screen is off
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PhoneSensorService::WakeLock");
            wakeLock.acquire(MAX_TIME_RECORDING_IN_SECONDS * 1000L);
        }
    }

    private void flushBufferToCsv() {
        try {
            int minSize = NUM_SAMPLES_IN_SESSION;
            for (ArrayList<Float> values : highFreqData.values()) {
                if (values.size() < minSize) {
                    minSize = values.size();
                }
            }
            for (int i = 0; i < minSize; i++) {
                StringBuilder data = new StringBuilder();

                for (String key : new String[]{"ACC_X", "ACC_Y", "ACC_Z", "GYRO_X", "GYRO_Y", "GYRO_Z"}) {
                    if (highFreqData.containsKey(key) && i < highFreqData.get(key).size()) {
                        data.append(String.format(Locale.US, "%.6f", highFreqData.get(key).get(i))).append(",");
                    } else {
                        data.append(",0");
                    }
                }

                String formattedTimestamp = timestamps.get(i);
                data.append(formattedTimestamp);


                bufferedWriter.write(data.toString());
                bufferedWriter.newLine();
            }
            bufferedWriter.flush();
        } catch (IOException e) {
            Log.e(LOG_TAG, "Error flushing buffer to CSV file", e);
        }
    }

    private void createCsvFile() {
        File externalDir = new File(getExternalFilesDir(null), "sensordata.csv");

        try {
            bufferedWriter = new BufferedWriter(new FileWriter(externalDir, false));
            bufferedWriter.write("ACC_X,ACC_Y,ACC_Z,GYRO_X,GYRO_Y,GYRO_Z,Timestamp");
            bufferedWriter.newLine();
            Log.d(LOG_TAG, "CSV file created at: " + externalDir.getAbsolutePath());
        } catch (IOException e) {
            Log.e(LOG_TAG, "Error creating CSV file", e);
        }
    }

    private void closeCsvFile() {
        try {
            if (bufferedWriter != null) {
                flushBufferToCsv();
                bufferedWriter.close();
                bufferedWriter = null;
                Log.d(LOG_TAG, "CSV file closed");
            }
        } catch (IOException e) {
            Log.e(LOG_TAG, "Error closing CSV file", e);
        }
    }
    /*protected float[] lowPass(float[] input, float[] output) {
        if (output == null) return input;

        for (int i = 0; i < input.length; i++) {
            output[i] = output[i] + ALPHA * (input[i] - output[i]);
        }
        return output;
    }*/

    @SuppressLint("ForegroundServiceType")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Sensor Data Service")
                .setContentText("Collecting sensor data...")
                .setSmallIcon(R.drawable.img)
                .build();

        startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        Log.d(LOG_TAG, "Service started");
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sensorManager.unregisterListener(this);
        closeCsvFile();
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
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
        //float[] filteredValues = new float[3];

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            keyPrefix = "ACC";
            //accelOutput = lowPass(event.values, accelOutput);
            //filteredValues = accelOutput;
        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            keyPrefix = "GYRO";
            //gyroOutput = lowPass(event.values, gyroOutput);
            //filteredValues = gyroOutput;
        } else {
            return; // Ignore other sensor types
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss.SSS", Locale.getDefault());
        sdf.setTimeZone(TimeZone.getDefault());
        String formattedTimestamp = sdf.format(new Date(currentTime));

        addHighFrequencyMeasurement(keyPrefix + "_X", event.values[0]);
        addHighFrequencyMeasurement(keyPrefix + "_Y", event.values[1]);
        addHighFrequencyMeasurement(keyPrefix + "_Z", event.values[2]);

        // Capture the formatted timestamp
        timestamps.add(formattedTimestamp);
    }

    private void addHighFrequencyMeasurement(String key, float measurement) {
        if (highFreqData == null) {
            Log.e(LOG_TAG, "Can't add measurement. HF data bundle is null");
            return;
        }

        if (!highFreqData.containsKey(key)) {
            highFreqData.put(key, new ArrayList<>());
        }

        // Round the measurement to 6 decimal places
        float roundedMeasurement = new BigDecimal(measurement).setScale(6, RoundingMode.HALF_UP).floatValue();
        highFreqData.get(key).add(roundedMeasurement);

        if (highFreqData.get(key).size() % 100 == 0) {
            logCurrentSampleSize();
        }
    }

    private void logCurrentSampleSize() {
        for (Map.Entry<String, ArrayList<Float>> entry : highFreqData.entrySet()) {
            Log.d(LOG_TAG, "Key: " + entry.getKey() + ", Sample size: " + entry.getValue().size());
        }
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

    private final Runnable stopDataCollection = new Runnable() {
        @Override
        public void run() {
            Log.d(LOG_TAG, "Max recording time reached, stopping data collection.");
            sensorManager.unregisterListener(PhoneSensorService.this);
            closeCsvFile();
            Log.d(LOG_TAG, "Data collection stopped.");
            stopSelf();
        }
    };
}
