package com.example.extrasensoryapp.Sensors;

import static android.content.Context.SENSOR_SERVICE;
import static androidx.core.content.ContextCompat.getSystemService;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.UserHandle;
import android.util.Log;
import android.view.Display;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;


import com.example.extrasensoryapp.R;
import com.example.extrasensoryapp.data.ESTimestamp;


/**
 * This class is to handle the activation of sensors for the recording period,
 * collecting the measured data and bundling it together.
 *
 * This class is designed as a singleton (maximum of 1 instance will be created),
 * in order to avoid collisions and to make sure only a single thread uses the sensors at any time.
 *
 * Created by Yonatan on 1/15/2015.
 * ========================================
 * The ExtraSensory App
 * @author Yonatan Vaizman yvaizman@ucsd.edu
 * Please see ExtraSensory App website for details and citation requirements:
 * http://extrasensory.ucsd.edu/ExtraSensoryApp
 * ========================================
 */
public class ESSensorManager extends Service
        implements SensorEventListener
        {

    public static final String BROADCAST_RECORDING_STATE_CHANGED = "broadcast.recording_state";

    private static final String CHANNEL_ID = "ESSensorManagerChannel";

    // Static part of the class:
    private static ESSensorManager theSingleSensorManager;
    private static final String LOG_TAG = "[ESSensorManager]";

    private static final int LOW_FREQ_SAMPLE_PERIOD_MICROSECONDS = 1000000;
    private static final int SAMPLE_PERIOD_MICROSECONDS = 25000;
    private static final int NUM_SAMPLES_IN_SESSION = 800;
    private static final double NANOSECONDS_IN_SECOND = 1e9f;
    private static final double MILLISECONDS_IN_SECOND = 1000;
    private static final String HIGH_FREQ_DATA_FILENAME = "HF_DUR_DATA.txt";
    private static final int MAX_TIME_RECORDING_IN_SECONDS = 30;

    // Raw motion sensors:
    private static final String RAW_ACC_X = "raw_acc_x";
    private static final String RAW_ACC_Y = "raw_acc_y";
    private static final String RAW_ACC_Z = "raw_acc_z";
    private static final String RAW_ACC_TIME = "raw_acc_timeref";

    private static final String RAW_MAGNET_X = "raw_magnet_x";
    private static final String RAW_MAGNET_Y = "raw_magnet_y";
    private static final String RAW_MAGNET_Z = "raw_magnet_z";
    private static final String RAW_MAGNET_BIAS_X = "raw_magnet_bias_x";
    private static final String RAW_MAGNET_BIAS_Y = "raw_magnet_bias_y";
    private static final String RAW_MAGNET_BIAS_Z = "raw_magnet_bias_z";
    private static final String RAW_MAGNET_TIME = "raw_magnet_timeref";

    private static final String RAW_GYRO_X = "raw_gyro_x";
    private static final String RAW_GYRO_Y = "raw_gyro_y";
    private static final String RAW_GYRO_Z = "raw_gyro_z";
    private static final String RAW_GYRO_DRIFT_X = "raw_gyro_drift_x";
    private static final String RAW_GYRO_DRIFT_Y = "raw_gyro_drift_y";
    private static final String RAW_GYRO_DRIFT_Z = "raw_gyro_drift_z";
    private static final String RAW_GYRO_TIME = "raw_gyro_timeref";

    // Processed motion sensors (software "sensors"):
    private static final String PROC_ACC_X = "processed_user_acc_x";
    private static final String PROC_ACC_Y = "processed_user_acc_y";
    private static final String PROC_ACC_Z = "processed_user_acc_z";
    private static final String PROC_ACC_TIME = "processed_user_acc_timeref";

    private static final String PROC_GRAV_X = "processed_gravity_x";
    private static final String PROC_GRAV_Y = "processed_gravity_y";
    private static final String PROC_GRAV_Z = "processed_gravity_z";
    private static final String PROC_GRAV_TIME = "processed_gravity_timeref";

    private static final String PROC_MAGNET_X = "processed_magnet_x";
    private static final String PROC_MAGNET_Y = "processed_magnet_y";
    private static final String PROC_MAGNET_Z = "processed_magnet_z";
    private static final String PROC_MAGNET_TIME = "processed_magnet_timeref";

    private static final String PROC_GYRO_X = "processed_gyro_x";
    private static final String PROC_GYRO_Y = "processed_gyro_y";
    private static final String PROC_GYRO_Z = "processed_gyro_z";
    private static final String PROC_GYRO_TIME = "processed_gyro_timeref";

   // Low frequency measurements:
    private static final String LOW_FREQ = "low_frequency";

    private static final String TEMPERATURE_AMBIENT = "temperature_ambient";
    private static final String LIGHT = "light";
    private static final String PRESSURE = "pressure";
    private static final String PROXIMITY = "proximity_cm";
    private static final String HUMIDITY = "relative_humidity";


    private static String getSensorLeadingMeasurementKey(int sensorType) {
        switch (sensorType) {
            case Sensor.TYPE_ACCELEROMETER:
                return RAW_ACC_X;
            case Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED:
                return RAW_MAGNET_X;
            case Sensor.TYPE_GYROSCOPE_UNCALIBRATED:
                return RAW_GYRO_X;
            case Sensor.TYPE_GRAVITY:
                return PROC_GRAV_X;
            case Sensor.TYPE_LINEAR_ACCELERATION:
                return PROC_ACC_X;
            case Sensor.TYPE_MAGNETIC_FIELD:
                return PROC_MAGNET_X;
            case Sensor.TYPE_GYROSCOPE:
                return PROC_GYRO_X;
            default:
                throw new UnknownError("Requested measurement key for unknown sensor, with type: " + sensorType);
        }
    }

    public static ESSensorManager getESSensorManager() {
        if (theSingleSensorManager == null) {
            theSingleSensorManager = new ESSensorManager();
        }

        return theSingleSensorManager;
    }

    // Non static part:
    private SensorManager _sensorManager;
    private HashMap<String,ArrayList<Double>> _highFreqData;
    private HashMap<String,ArrayList<Double>> _locationCoordinatesData;
    private JSONObject _lowFreqData;
    private ESTimestamp _timestamp;
    private ArrayList<Sensor> _hiFreqSensors;
    private ArrayList<String> _hiFreqSensorFeatureKeys;
    private ArrayList<String> _sensorKeysThatShouldGetEnoughSamples;
    private ArrayList<Sensor> _lowFreqSensors;
    private ArrayList<String> _lowFreqSensorFeatureKeys;
    private Map<Integer,String> _sensorTypeToNiceName;

    private boolean _recordingRightNow = false;

    public boolean is_recordingRightNow() {
        return _recordingRightNow;
    }

    public String getSensorNiceName(int sensorType) {
        Integer type = new Integer(sensorType);
        if (_sensorTypeToNiceName.containsKey(type)) {
            return _sensorTypeToNiceName.get(type);
        }
        else {
            return ""+sensorType;
        }
    }

        private void set_recordingRightNow(boolean recordingRightNow) {
        _recordingRightNow = recordingRightNow;
        Intent broadcast = new Intent(BROADCAST_RECORDING_STATE_CHANGED);
        LocalBroadcastManager manager = LocalBroadcastManager.getInstance(this);
        manager.sendBroadcast(broadcast);
    }

    /**
     * Making the constructor private, in order to make this class a singleton
     */
    private ESSensorManager() {
        _sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Sensor Data Service")
                .setContentText("Collecting sensor data...")
                .setSmallIcon(R.drawable.img)
                .build();
        startForeground(1, notification);
        Log.d(LOG_TAG, "Service started");
        // Initialize the sensors:
        _hiFreqSensors = new ArrayList<>(10);
        _hiFreqSensorFeatureKeys = new ArrayList<>(10);
        _lowFreqSensors = new ArrayList<>(10);
        _lowFreqSensorFeatureKeys = new ArrayList<>(10);
        _timestamp = new ESTimestamp(0);
        _sensorTypeToNiceName = new HashMap<>(10);

        // Add raw motion sensors:
        if (!tryToAddSensor(Sensor.TYPE_ACCELEROMETER,true,"raw accelerometer",RAW_ACC_X)) {
            Log.e(LOG_TAG,"There is no accelerometer. Canceling recording.");
            return;
        }
        tryToAddSensor(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED,true,"raw magnetometer",RAW_MAGNET_X);
        tryToAddSensor(Sensor.TYPE_GYROSCOPE_UNCALIBRATED,true,"raw gyroscope",RAW_GYRO_X);
        // Add processed motion sensors:
        tryToAddSensor(Sensor.TYPE_GRAVITY,true,"gravity",PROC_GRAV_X);
        tryToAddSensor(Sensor.TYPE_LINEAR_ACCELERATION,true,"linear acceleration",PROC_ACC_X);
        tryToAddSensor(Sensor.TYPE_MAGNETIC_FIELD,true,"calibrated magnetometer",PROC_MAGNET_X);
        tryToAddSensor(Sensor.TYPE_GYROSCOPE,true,"calibrated gyroscope",PROC_GYRO_X);

        // Add low frequency sensors:
        tryToAddSensor(Sensor.TYPE_AMBIENT_TEMPERATURE,false,"ambient temperature",TEMPERATURE_AMBIENT);
        tryToAddSensor(Sensor.TYPE_LIGHT,false,"light",LIGHT);
        tryToAddSensor(Sensor.TYPE_PRESSURE,false,"pressure",PRESSURE);
        tryToAddSensor(Sensor.TYPE_PROXIMITY,false,"proximity",PROXIMITY);
        tryToAddSensor(Sensor.TYPE_RELATIVE_HUMIDITY,false,"relative humidity",HUMIDITY);

        // This list can be prepared at every recording session, according to the sensors that should be recorded

        Log.v(LOG_TAG, "An instance of ESSensorManager was created.");
    }

            @Nullable
            @Override
            public IBinder onBind(Intent intent) {
                return null;
            }

            private boolean tryToAddSensor(int sensorType,boolean isHighFreqSensor, String niceName,String featureKey) {
        Sensor sensor = _sensorManager.getDefaultSensor(sensorType);
        if (sensor == null) {
            Log.i(LOG_TAG,"No available sensor: " + niceName);
            return false;
        }
        else {
            _sensorTypeToNiceName.put(new Integer(sensorType),niceName);
            if (isHighFreqSensor) {
                Log.i(LOG_TAG, "Adding hi-freq sensor: " + niceName);
                _hiFreqSensors.add(sensor);
                _hiFreqSensorFeatureKeys.add(featureKey);
            }
            else {
                Log.i(LOG_TAG, "Adding low-freq sensor: " + niceName);
                _lowFreqSensors.add(sensor);
                _lowFreqSensorFeatureKeys.add(featureKey);
            }
            return true;
        }
    }

    private ArrayList<Integer> getSensorTypesFromSensors(ArrayList<Sensor> sensors) {
        if (sensors == null) {
            return new ArrayList<Integer>(10);
        }
        ArrayList<Integer> sensorTypes = new ArrayList<>(sensors.size());
        for (Sensor sensor : sensors) {
            sensorTypes.add(new Integer(sensor.getType()));
        }
        return sensorTypes;
    }

    public ArrayList<Integer> getRegisteredHighFreqSensorTypes() {
        return getSensorTypesFromSensors(_hiFreqSensors);
    }

    public ArrayList<Integer> getRegisteredLowFreqSensorTypes() {
        return getSensorTypesFromSensors(_lowFreqSensors);
    }

    /**
     * Start a recording session from the sensors,
     * and initiate sending the collected measurements to the server.
     *
     * @param timestamp This recording session's identifying timestamp
     */
    public void startRecordingSensors(ESTimestamp timestamp) {
        Log.i(LOG_TAG, "Starting recording for timestamp: " + timestamp.toString());
        clearRecordingSession(true);
        set_recordingRightNow(true);
        // Set the new timestamp:
        _timestamp = timestamp;
        /////////////////////////
        // This is just for debugging. With the simulator (that doesn't produce actual sensor events):
        if (debugSensorSimulationMode()) {
            simulateRecordingSession();
            return;
        }
        /////////////////////////

        // Start recording hi-frequency sensors:
        ArrayList<Integer> hfSensorTypesToRecord = new ArrayList<>(Arrays.asList(
                Sensor.TYPE_ACCELEROMETER,
                Sensor.TYPE_GYROSCOPE,
                Sensor.TYPE_MAGNETIC_FIELD,
                Sensor.TYPE_LINEAR_ACCELERATION,
                Sensor.TYPE_GRAVITY
        ));

        prepareListOfMeasurementsShouldGetEnoughSamples(hfSensorTypesToRecord);

        for (Sensor sensor : _hiFreqSensors) {
            if (hfSensorTypesToRecord.contains(sensor.getType())) {
                _sensorManager.registerListener(this, sensor, SAMPLE_PERIOD_MICROSECONDS);
                Log.d(LOG_TAG, "== Registering for recording HF sensor: " + getSensorNiceName(sensor.getType()));
            } else {
                Log.d(LOG_TAG, "As requested: not recording HF sensor: " + getSensorNiceName(sensor.getType()));
            }
        }

        // Maybe the session is already done:
        finishSessionIfReady();
    }

    private void prepareListOfMeasurementsShouldGetEnoughSamples(ArrayList<Integer> hfSensorTypesToRecord) {
        _sensorKeysThatShouldGetEnoughSamples = new ArrayList<>(10);
        // In case there are no high frequency sensors to record,
        // we shouldn't wait for any measurement-key to fill up with enough samples.
        if (hfSensorTypesToRecord.size() <= 0) {
            return;
        }

        // Otherwise, let's use the policy of having a single leading sensor (or several) that will determine when to stop the recording
        // (whenever that sensor contributed enough samples for its leading measurement key).
        // It is possible that accelerometer will reach the desired number of samples (e.g. 800) while gyroscope
        // will only reach 600 samples. This is because the sensor-sampling systems of Android are not aligned
        // among the sensors, and the sampling rates are not completely stable.
        Integer[] leadingSensorPriority = new Integer[]{
                Sensor.TYPE_ACCELEROMETER,Sensor.TYPE_LINEAR_ACCELERATION,Sensor.TYPE_GRAVITY,
                Sensor.TYPE_MAGNETIC_FIELD,Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED
        };
        // Avoiding wating for sensors that tend to be slow samplers, like gyroscope.
        for (Integer sensorTypeInteger : leadingSensorPriority) {
            if (hfSensorTypesToRecord.contains(sensorTypeInteger)) {
                // Then mark this single sensor as the one to wait for to get enough samples:
                Log.d(LOG_TAG,"Marking the leading sensor (the one from which we'll wait to get enough measurements): " + getSensorNiceName(sensorTypeInteger));
                _sensorKeysThatShouldGetEnoughSamples.add(getSensorLeadingMeasurementKey(sensorTypeInteger));
                return;
            }
        }
        // If we reached here, we have a risk:
        // all the high frequency sensors to be recorded are those that we do not trust to sample quickly enough to reach
        // the full number of samples. This can result in a situation where it will take more than a minute
        // before the sensor gets enough samples, and then the new recording session will begin.
        // To avoid this problem, we add an additional max-time-based mechanism to determain when to stop recording.
        Log.w(LOG_TAG,"!!! We have no sensor to tell us when to stop recording.");
    }

    private void simulateRecordingSession() {
        for (int i = 0; i < NUM_SAMPLES_IN_SESSION; i ++) {
            addHighFrequencyMeasurement(RAW_MAGNET_X,0);
            addHighFrequencyMeasurement(RAW_MAGNET_Y,0);
            addHighFrequencyMeasurement(RAW_MAGNET_Z,0);
            addHighFrequencyMeasurement(RAW_MAGNET_BIAS_X, 0);
            addHighFrequencyMeasurement(RAW_MAGNET_BIAS_Y, 0);
            addHighFrequencyMeasurement(RAW_MAGNET_BIAS_Z, 0);

            addHighFrequencyMeasurement(RAW_GYRO_X, 0);
            addHighFrequencyMeasurement(RAW_GYRO_Y, 0);
            addHighFrequencyMeasurement(RAW_GYRO_Z, 0);
            addHighFrequencyMeasurement(RAW_GYRO_DRIFT_X, 0);
            addHighFrequencyMeasurement(RAW_GYRO_DRIFT_Y, 0);
            addHighFrequencyMeasurement(RAW_GYRO_DRIFT_Z, 0);

            addHighFrequencyMeasurement(PROC_GRAV_X, 0);
            addHighFrequencyMeasurement(PROC_GRAV_Y, 0);
            addHighFrequencyMeasurement(PROC_GRAV_Z, 0);

            addHighFrequencyMeasurement(PROC_ACC_X, 0);
            addHighFrequencyMeasurement(PROC_ACC_Y, 0);
            addHighFrequencyMeasurement(PROC_ACC_Z, 0);

            addHighFrequencyMeasurement(PROC_MAGNET_X, 0);
            addHighFrequencyMeasurement(PROC_MAGNET_Y, 0);
            addHighFrequencyMeasurement(PROC_MAGNET_Z, 0);

            addHighFrequencyMeasurement(PROC_GYRO_X, 0);
            addHighFrequencyMeasurement(PROC_GYRO_Y, 0);
            addHighFrequencyMeasurement(PROC_GYRO_Z, 0);

            addHighFrequencyMeasurement(RAW_ACC_X,0);
            addHighFrequencyMeasurement(RAW_ACC_Y,1);
            addHighFrequencyMeasurement(RAW_ACC_Z,2);
            if (addHighFrequencyMeasurement(RAW_ACC_TIME,111)) {
                finishSessionIfReady();
            }
        }
    }

    private void clearRecordingSession(boolean clearBeforeStart) {
        // Clear the high frequency map:
        _highFreqData = new HashMap<>(20);

        _lowFreqData = new JSONObject();

    }

    /**
     * Stop any recording session, if any is active,
     * and clear any data that was collected from the sensors during the session.
     */
    public void stopRecordingSensors() {
        Log.i(LOG_TAG,"Stopping recording.");
        // Stop listening:
        _sensorManager.unregisterListener(this);
        clearRecordingSession(false);
        set_recordingRightNow(false);
    }


    /**
     * Add another numeric value to a growing vector of measurements from a sensor.
     *
     * @param key The key of the specific measurement type
     * @param measurement The sampled measurement to be added to the vector
     * @return Did this key collect enough samples in this session?
     */
    private boolean addHighFrequencyMeasurement(String key,double measurement) {
        if (_highFreqData == null) {
            Log.e(LOG_TAG,"Can't add measurement. HF data bundle is null");
        }

        // Check if the vector for this key was already initialized:
        if (!_highFreqData.containsKey(key)) {
            _highFreqData.put(key,new ArrayList<Double>(NUM_SAMPLES_IN_SESSION));
        }

        _highFreqData.get(key).add(measurement);

        //if (RAW_ACC_X.equals(key) && (_highFreqData.get(key).size() % 100) == 0) {
        if ((_highFreqData.get(key).size() % 100) == 0) {
            logCurrentSampleSize();
        }

        return (_highFreqData.get(key).size() >= NUM_SAMPLES_IN_SESSION);
    }


    private void logCurrentSampleSize() {
        int accSize = 0;
        if (_highFreqData.containsKey(RAW_ACC_X)) {
            accSize = _highFreqData.get(RAW_ACC_X).size();
        }
        int magnetSize = 0;
        if (_highFreqData.containsKey(PROC_MAGNET_X)) {
            magnetSize = _highFreqData.get(PROC_MAGNET_X).size();
        }
        int gyroSize = 0;
        if (_highFreqData.containsKey(PROC_GYRO_X)) {
            gyroSize = _highFreqData.get(PROC_GYRO_X).size();
        }

        Log.i(LOG_TAG,"Collected acc:" + accSize + ",magnet:" + magnetSize + ",gyro:" + gyroSize);
    }

    private void finishIfTooMuchTimeRecording() {
        ESTimestamp now = new ESTimestamp();
        int timeRecording = now.differenceInSeconds(_timestamp);
        if (timeRecording >= MAX_TIME_RECORDING_IN_SECONDS) {
            Log.d(LOG_TAG,"Finishing this recording because it is already too long, num seconds: " + timeRecording);
            finishSession();
        }
    }
    private void finishSession(){
        _sensorManager.unregisterListener(this);
        set_recordingRightNow(false);
    }

    private void finishSessionIfReady() {
        if (!_recordingRightNow) {
            Log.w(LOG_TAG, "Not recording right now, so cannot finish recording session.");
            return;
        }
        if (shouldStopRecording()) {
            stopRecordingSensors();
            Log.i(LOG_TAG, "Finished recording session.");
            saveRecordingSession();
        }
    }
    private boolean shouldStopRecording() {
        if (_sensorKeysThatShouldGetEnoughSamples == null || _sensorKeysThatShouldGetEnoughSamples.size() <= 0) {
            return false;
        }
        for (String measurementKey : _sensorKeysThatShouldGetEnoughSamples) {
            if (_highFreqData.get(measurementKey).size() >= NUM_SAMPLES_IN_SESSION) {
                Log.d(LOG_TAG, "We got enough measurements from " + measurementKey);
                return true;
            }
        }
        return false;
    }

    private void saveRecordingSession() {
        Log.i(LOG_TAG, "Saving the recorded session.");

        // External directory for saving the data
        File externalDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        File hfDataFile = new File(externalDir, HIGH_FREQ_DATA_FILENAME);

        // Save high frequency data to a CSV file
        saveHighFrequencyDataToCSV(hfDataFile);
    }
    private void saveHighFrequencyDataToCSV(File csvFile) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(csvFile))) {
            // Write header
            writer.write("timestamp");
            for (String key : _highFreqData.keySet()) {
                writer.write("," + key);
            }
            writer.newLine();

            // Write data rows
            int numRows = _highFreqData.values().iterator().next().size();
            for (int i = 0; i < numRows; i++) {
                writer.write(String.valueOf(_timestamp));
                for (ArrayList<Double> dataList : _highFreqData.values()) {
                    writer.write("," + dataList.get(i));
                }
                writer.newLine();
            }
        } catch (IOException e) {
            Log.e(LOG_TAG, "Error saving high frequency data to CSV file", e);
        }
    }

    // Implementing the SensorEventListener interface:
    @Override
    public void onSensorChanged(SensorEvent event) {
        // Sanity check: we shouldn't be recording now:
        if (!is_recordingRightNow()) {
            Log.e(LOG_TAG,"!!! We're not in a recording session (maybe finished recently) but got a sensor event for: " + event.sensor.getName());
            return;
        }
        boolean sensorCollectedEnough = false;
        double timestampSeconds =  ((double)event.timestamp) / NANOSECONDS_IN_SECOND;

        try {

            switch (event.sensor.getType()) {
                case Sensor.TYPE_ACCELEROMETER:
                    addHighFrequencyMeasurement(RAW_ACC_X, event.values[0]);
                    addHighFrequencyMeasurement(RAW_ACC_Y, event.values[1]);
                    addHighFrequencyMeasurement(RAW_ACC_Z, event.values[2]);
                    sensorCollectedEnough = addHighFrequencyMeasurement(RAW_ACC_TIME, timestampSeconds);
                    break;
                case Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED:
                    addHighFrequencyMeasurement(RAW_MAGNET_X, event.values[0]);
                    addHighFrequencyMeasurement(RAW_MAGNET_Y, event.values[1]);
                    addHighFrequencyMeasurement(RAW_MAGNET_Z, event.values[2]);
                    addHighFrequencyMeasurement(RAW_MAGNET_BIAS_X, event.values[3]);
                    addHighFrequencyMeasurement(RAW_MAGNET_BIAS_Y, event.values[4]);
                    addHighFrequencyMeasurement(RAW_MAGNET_BIAS_Z, event.values[5]);
                    sensorCollectedEnough = addHighFrequencyMeasurement(RAW_MAGNET_TIME, timestampSeconds);
                    break;
                case Sensor.TYPE_GYROSCOPE_UNCALIBRATED:
                    addHighFrequencyMeasurement(RAW_GYRO_X, event.values[0]);
                    addHighFrequencyMeasurement(RAW_GYRO_Y, event.values[1]);
                    addHighFrequencyMeasurement(RAW_GYRO_Z, event.values[2]);
                    addHighFrequencyMeasurement(RAW_GYRO_DRIFT_X, event.values[3]);
                    addHighFrequencyMeasurement(RAW_GYRO_DRIFT_Y, event.values[4]);
                    addHighFrequencyMeasurement(RAW_GYRO_DRIFT_Z, event.values[5]);
                    sensorCollectedEnough = addHighFrequencyMeasurement(RAW_GYRO_TIME, timestampSeconds);
                    break;
                case Sensor.TYPE_GRAVITY:
                    addHighFrequencyMeasurement(PROC_GRAV_X, event.values[0]);
                    addHighFrequencyMeasurement(PROC_GRAV_Y, event.values[1]);
                    addHighFrequencyMeasurement(PROC_GRAV_Z, event.values[2]);
                    sensorCollectedEnough = addHighFrequencyMeasurement(PROC_GRAV_TIME, timestampSeconds);
                    break;
                case Sensor.TYPE_LINEAR_ACCELERATION:
                    addHighFrequencyMeasurement(PROC_ACC_X, event.values[0]);
                    addHighFrequencyMeasurement(PROC_ACC_Y, event.values[1]);
                    addHighFrequencyMeasurement(PROC_ACC_Z, event.values[2]);
                    sensorCollectedEnough = addHighFrequencyMeasurement(PROC_ACC_TIME, timestampSeconds);
                    break;
                case Sensor.TYPE_MAGNETIC_FIELD:
                    addHighFrequencyMeasurement(PROC_MAGNET_X, event.values[0]);
                    addHighFrequencyMeasurement(PROC_MAGNET_Y, event.values[1]);
                    addHighFrequencyMeasurement(PROC_MAGNET_Z, event.values[2]);
                    sensorCollectedEnough = addHighFrequencyMeasurement(PROC_MAGNET_TIME, timestampSeconds);
                    break;
                case Sensor.TYPE_GYROSCOPE:
                    addHighFrequencyMeasurement(PROC_GYRO_X, event.values[0]);
                    addHighFrequencyMeasurement(PROC_GYRO_Y, event.values[1]);
                    addHighFrequencyMeasurement(PROC_GYRO_Z, event.values[2]);
                    sensorCollectedEnough = addHighFrequencyMeasurement(PROC_GYRO_TIME, timestampSeconds);
                    break;
                // Low frequency (one-time) sensors:
                case Sensor.TYPE_AMBIENT_TEMPERATURE:
                    _lowFreqData.put(TEMPERATURE_AMBIENT, event.values[0]);
                    sensorCollectedEnough = true;
                    break;
                case Sensor.TYPE_LIGHT:
                    _lowFreqData.put(LIGHT, event.values[0]);
                    sensorCollectedEnough = true;
                    break;
                case Sensor.TYPE_PRESSURE:
                    _lowFreqData.put(PRESSURE, event.values[0]);
                    sensorCollectedEnough = true;
                    break;
                case Sensor.TYPE_PROXIMITY:
                    _lowFreqData.put(PROXIMITY, event.values[0]);
                    sensorCollectedEnough = true;
                    break;
                case Sensor.TYPE_RELATIVE_HUMIDITY:
                    _lowFreqData.put(HUMIDITY, event.values[0]);
                    sensorCollectedEnough = true;
                    break;
                default:
                    Log.e(LOG_TAG, "Got event from unsupported sensor with type " + event.sensor.getType());
            }

            finishIfTooMuchTimeRecording();
            if (sensorCollectedEnough) {
                // Then we've collected enough samples from accelerometer,
                // and we can stop listening to it.
                Log.d(LOG_TAG,"=========== unregistering sensor: " + event.sensor.getName());
                _sensorManager.unregisterListener(this, event.sensor);
                finishSessionIfReady();
            }

        } catch (JSONException e) {
            Log.e(LOG_TAG,"Problem adding sensor measurement to json object. " + event.sensor.toString());
            e.printStackTrace();
        }

    }
    private boolean debugSensorSimulationMode() {
        // Placeholder for simulation mode check
        return false;
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

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

}
