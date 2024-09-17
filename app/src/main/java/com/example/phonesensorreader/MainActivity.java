package com.example.phonesensorreader;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.LegendRenderer;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.jjoe64.graphview.series.Series;


public class MainActivity extends AppCompatActivity {

    private LineGraphSeries<DataPoint> seriesAccX, seriesAccY, seriesAccZ;
    private LineGraphSeries<DataPoint> seriesGyroX, seriesGyroY, seriesGyroZ;

    private double graphLastXValue = 0d;
    private BroadcastReceiver sensorDataReceiver;

    private static final String LOG_TAG="Main Activity";

    private static Boolean isRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        PackageManager pm = this.getPackageManager();
        Intent launchTermuxIntent = pm.getLaunchIntentForPackage("com.termux");
        //Log.d(LOG_TAG, "Initiating Termux for intent: " +launchTermuxIntent);

        if (launchTermuxIntent != null && !isRunning) {
            Log.d(LOG_TAG, "Opening Termux");
            this.startActivity(launchTermuxIntent);
            isRunning = true;
        }

        GraphView graphAcc = findViewById(R.id.graphAcc);
        GraphView graphGyro = findViewById(R.id.graphGyro);

        // Create series with colors and background shading
        seriesAccX = createSeries("Acc X", 0xFF0000FF, 0x220000FF); // Blue
        seriesAccY = createSeries("Acc Y", 0xFF00FF00, 0x2200FF00); // Green
        seriesAccZ = createSeries("Acc Z", 0xFFFF0000, 0x22FF0000); // Red
        seriesGyroX = createSeries("Gyro X", 0xFF0000FF, 0x220000FF); // Blue
        seriesGyroY = createSeries("Gyro Y", 0xFF00FF00, 0x2200FF00); // Green
        seriesGyroZ = createSeries("Gyro Z", 0xFFFF0000, 0x22FF0000); // Red

        graphAcc.addSeries(seriesAccX);
        graphAcc.addSeries(seriesAccY);
        graphAcc.addSeries(seriesAccZ);
        graphGyro.addSeries(seriesGyroX);
        graphGyro.addSeries(seriesGyroY);
        graphGyro.addSeries(seriesGyroZ);

        // Set titles for the graphs
        graphAcc.setTitle("Accelerometer");
        graphAcc.setTitleColor(0xFF000000);
        graphGyro.setTitle("Gyroscope");
        graphGyro.setTitleColor(0xFF000000);

        // Set manual X bounds
        setupGraphView(graphAcc);
        setupGraphView(graphGyro);

        Button btnStart = findViewById(R.id.btnStart);
        Button btnStop = findViewById(R.id.btnStop);
        //Button openWebViewButton = findViewById(R.id.open_webview_button);

        btnStart.setOnClickListener(v -> {
            Intent serviceIntent = new Intent(MainActivity.this, PhoneSensorService.class);
            startService(serviceIntent);
        });

        btnStop.setOnClickListener(v -> {
            // Stop the sensor service
            Intent serviceIntent = new Intent(MainActivity.this, PhoneSensorService.class);
            stopService(serviceIntent);

            // Start the WebViewActivity
            Intent intent = new Intent(MainActivity.this, WebViewActivity.class);
            startActivity(intent);
        });

        sensorDataReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int sensorType = intent.getIntExtra(PhoneSensorService.EXTRA_SENSOR_TYPE, -1);
                float[] sensorValues = intent.getFloatArrayExtra(PhoneSensorService.EXTRA_SENSOR_VALUES);
                if (sensorValues != null) {
                    graphLastXValue += 0.1d;
                    if (sensorType == Sensor.TYPE_ACCELEROMETER) {
                        seriesAccX.appendData(new DataPoint(graphLastXValue, sensorValues[0]), true, 40);
                        seriesAccY.appendData(new DataPoint(graphLastXValue, sensorValues[1]), true, 40);
                        seriesAccZ.appendData(new DataPoint(graphLastXValue, sensorValues[2]), true, 40);
                    } else if (sensorType == Sensor.TYPE_GYROSCOPE) {
                        seriesGyroX.appendData(new DataPoint(graphLastXValue, sensorValues[0]), true, 40);
                        seriesGyroY.appendData(new DataPoint(graphLastXValue, sensorValues[1]), true, 40);
                        seriesGyroZ.appendData(new DataPoint(graphLastXValue, sensorValues[2]), true, 40);
                    }

                    // Adjust X bounds
                    if (graphLastXValue > 10) {
                        graphAcc.getViewport().setMinX(graphLastXValue - 10);
                        graphAcc.getViewport().setMaxX(graphLastXValue);
                        graphGyro.getViewport().setMinX(graphLastXValue - 10);
                        graphGyro.getViewport().setMaxX(graphLastXValue);
                    }
                }
            }
        };
    }

    private LineGraphSeries<DataPoint> createSeries(String title, int color, int backgroundColor) {
        LineGraphSeries<DataPoint> series = new LineGraphSeries<>(new DataPoint[]{});
        series.setTitle(title);
        series.setColor(color);
        series.setDrawBackground(true);
        series.setBackgroundColor(backgroundColor);
        return series;
    }

    private void setupGraphView(GraphView graph) {
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMinX(0);
        graph.getViewport().setMaxX(10);
        graph.getViewport().setScalable(true);
        graph.getViewport().setScrollable(true);
        graph.getViewport().setScalableY(true);
        graph.getViewport().setScrollableY(true);
        graph.getGridLabelRenderer().setHorizontalAxisTitle("Time (s)");
        graph.getGridLabelRenderer().setHorizontalAxisTitleColor(0xFF000000);
        graph.getGridLabelRenderer().setVerticalAxisTitle("Value");
        graph.getGridLabelRenderer().setVerticalAxisTitleColor(0xFF000000);
        graph.getGridLabelRenderer().setVerticalLabelsColor(0xFF000000);
        graph.getGridLabelRenderer().setHorizontalLabelsColor(0xFF000000);
        graph.getLegendRenderer().setVisible(true);
        graph.getLegendRenderer().setAlign(LegendRenderer.LegendAlign.TOP);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(sensorDataReceiver, new IntentFilter(PhoneSensorService.ACTION_SENSOR_DATA),RECEIVER_EXPORTED);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(sensorDataReceiver);
    }
}
