package com.example.phonesensorreader;
import android.content.Intent;
import android.content.Context;
import android.os.Bundle;
import android.widget.Button;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button startButton = findViewById(R.id.button_start);
        startButton.setOnClickListener(view -> {
            // Start the sensor data collection service
            Intent serviceIntent = new Intent(this, PhoneSensorService.class);
            startForegroundService(serviceIntent);
            // Close the activity since the service will run in the background
            finish();
        });
    }
}

/* import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;


public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private static final String LOG_TAG = "PhoneSensorReader";
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor gyroscope;
   //private Sensor magnetometer;
    //private Sensor temperature;
    private Handler handler = new Handler(Looper.getMainLooper());
    //private static final int INTERVAL = 1000; // Interval in milliseconds (1000ms = 1 second)
    private static final int COLLECTION_DURATION = 600000; // 5 seconds in milliseconds

    private float[] accelerometerValues = new float[3];
    private float[] gyroscopeValues = new float[3];
    //private float[] magnetometerValues = new float[3];
    //private float temperatureValue = Float.NaN;
    private File csvFile;
    private FileWriter fileWriter;

    private static final int REQUEST_WRITE_STORAGE = 112;
    // Variables to calculate sampling frequency
    private long startTime;
    private int sampleCount;
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Request write permissions
        boolean hasPermission = (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED);
        if (!hasPermission) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_WRITE_STORAGE);
        } else {
            initializeSensorDataCollection();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_WRITE_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeSensorDataCollection();
            } else {
                // Permission denied, handle accordingly
                Log.e(LOG_TAG, "Write permission denied. Cannot save data.");
            }
        }
    }

    private void initializeSensorDataCollection() {
        handler = new Handler(Looper.getMainLooper());
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        // Initialize sensors
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            //magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            //temperature = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE);

            // Register sensors
            if (accelerometer != null) {
                sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
            }
            if (gyroscope != null) {
                sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_NORMAL);
            }
            /*if (magnetometer != null) {
                sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL);
            }
            if (temperature != null) {
                sensorManager.registerListener(this, temperature, SensorManager.SENSOR_DELAY_NORMAL);
            } else {
                Log.e(LOG_TAG, "Ambient Temperature sensor not available on this device.");
            } //end
        }

        // Create CSV file in the specified external storage directory
        createCsvFile();

        // Initialize sampling frequency variables
        startTime = System.currentTimeMillis();
        sampleCount = 0;

        // Start data collection for 5 seconds
        handler.post(startDataCollection);
    }

    public void onSensorChanged(SensorEvent event) {
        sampleCount++;
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, accelerometerValues, 0, event.values.length);
        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            System.arraycopy(event.values, 0, gyroscopeValues, 0, event.values.length);
        } /*else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, magnetometerValues, 0, event.values.length);
        } else if (event.sensor.getType() == Sensor.TYPE_AMBIENT_TEMPERATURE) {
            temperatureValue = event.values[0];
        }//end
        // Format timestamp
        long currentTimeMillis = System.currentTimeMillis();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss.SSS", Locale.getDefault());
        sdf.setTimeZone(TimeZone.getDefault());
        String formattedTimestamp = sdf.format(new Date(currentTimeMillis));
        // Write data to CSV file
        String data = formattedTimestamp + ","
                + accelerometerValues[0] + "," + accelerometerValues[1] + "," + accelerometerValues[2] + ","
                + gyroscopeValues[0] + "," + gyroscopeValues[1] + "," + gyroscopeValues[2] + "\n";
                //"," + magnetometerValues[0] + "," + magnetometerValues[1] + "," + magnetometerValues[2] + "," + temperatureValue + "\n";

        try {
            fileWriter.append(data);
            fileWriter.flush();
        } catch (IOException e) {
            Log.e(LOG_TAG, "Error writing to CSV file", e);
        }
        long elapsedTime = System.currentTimeMillis() - startTime;
        if (elapsedTime >= 1000) {
            double samplingFrequency = (double) sampleCount / (elapsedTime / 1000.0);
            Log.d(LOG_TAG, "Sampling Frequency: " + samplingFrequency + " Hz");
            startTime = System.currentTimeMillis();
            sampleCount = 0;
        }
    }

    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Do something here if sensor accuracy changes
    }

    protected void onPause() {
        super.onPause();
        // Sensor activity is paused to save battery
        sensorManager.unregisterListener(this);
        handler.removeCallbacks(startDataCollection);
        closeCsvFile();
    }


    protected void onResume() {
        super.onResume();
        // Enable sensors when the activity resumes
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
        if (gyroscope != null) {
            sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_NORMAL);
        }
        /*if (magnetometer != null) {
            sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
        if (temperature != null) {
            sensorManager.registerListener(this, temperature, SensorManager.SENSOR_DELAY_NORMAL);
        }//end
        //handler.post(logDataRunnable);
        createCsvFile();
        handler.post(startDataCollection);
    }
    private Runnable startDataCollection = new Runnable() {
        @Override
        public void run() {
            /*Log.d(LOG_TAG, "Accelerometer: X = " + accelerometerValues[0] + ", Y = " + accelerometerValues[1] + ", Z = " + accelerometerValues[2]);
            Log.d(LOG_TAG, "Gyroscope: X = " + gyroscopeValues[0] + ", Y = " + gyroscopeValues[1] + ", Z = " + gyroscopeValues[2]);
            Log.d(LOG_TAG, "Magnetometer: X = " + magnetometerValues[0] + ", Y = " + magnetometerValues[1] + ", Z = " + magnetometerValues[2]); //end

            handler.postDelayed(stopDataCollection, COLLECTION_DURATION);
            // Schedule the next log after INTERVAL milliseconds
           // handler.postDelayed(this, INTERVAL);
        }
    };
    private Runnable stopDataCollection = new Runnable() {
        @Override
        public void run() {
            // Stop collecting data after 10 seconds
            sensorManager.unregisterListener(MainActivity.this);
            closeCsvFile();
            Log.d(LOG_TAG, "Data collection stopped.");
        }
    };

    private void createCsvFile() {
        /*File dir = new File(Environment.getExternalStorageDirectory(), "phonesensorreader");
        if (!dir.exists()) {
            dir.mkdirs();
        } //end
        //File dir = getCacheDir(); // Use cache directory
        File externalDir = new File("/sdcard/Android/data/com.example.phonesensorreader/files/");
        if (!externalDir.exists()) {
            externalDir.mkdirs();
        }
        csvFile = new File(externalDir, "sensordata.csv");

        try {
            fileWriter = new FileWriter(csvFile, true); // true to append data
            // Write header if file is empty
            if (csvFile.length() == 0) {
                fileWriter.append("Timestamp,Acc_X,Acc_Y,Acc_Z,Gyro_X,Gyro_Y,Gyro_Z\n");
                fileWriter.flush();
            }
        } catch (IOException e) {
            Log.e(LOG_TAG, "Error creating CSV file", e);
        }
    }

    private void closeCsvFile() {
        try {
            if (fileWriter != null) {
                fileWriter.close();
            }
        } catch (IOException e) {
            Log.e(LOG_TAG, "Error closing CSV file", e);
        }
    }
} */

