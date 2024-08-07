package com.example.phonesensorreader;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HttpRequestTask {
    private static final String LOG_TAG = "HttpRequestTask";

    public interface AsyncResponse {
        void processFinish(String output);
    }

    private AsyncResponse delegate;
    private ExecutorService executorService;
    private Handler mainHandler;

    public HttpRequestTask(AsyncResponse delegate) {
        this.delegate = delegate;
        this.executorService = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void execute(String... params) {
        String json = params[0];
        String urlString = "http://127.0.0.1:8080/completion"; // Ensure this is consistent
        Log.d(LOG_TAG, "Connecting to server at: " + urlString);

        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL(urlString);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setRequestProperty("Accept", "text/event-stream");
                    conn.setDoOutput(true);

                    try (OutputStream os = conn.getOutputStream()) {
                        byte[] input = json.getBytes("utf-8");
                        os.write(input, 0, input.length);
                    }

                    int responseCode = conn.getResponseCode();
                    Log.d(LOG_TAG, "Response Code: " + responseCode);

                    StringBuilder response = new StringBuilder();
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(
                            responseCode == HttpURLConnection.HTTP_OK ? conn.getInputStream() : conn.getErrorStream(), "utf-8"))) {
                        String responseLine;
                        while ((responseLine = br.readLine()) != null) {
                            response.append(responseLine.trim());
                        }
                    }

                    String result = responseCode == HttpURLConnection.HTTP_OK
                            ? response.toString()
                            : "Error: " + responseCode + " - " + response.toString();

                    Log.d(LOG_TAG, "Response from server: " + result);
                    postResult(result);

                } catch (Exception e) {
                    Log.e(LOG_TAG, "Exception occurred: " + e.getMessage(), e);
                    postResult("Failed");
                }
            }
        });
    }

    private void postResult(String result) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                delegate.processFinish(result);
            }
        });
    }
}


/*import android.os.AsyncTask;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class HttpRequestTask extends AsyncTask<String, Void, String> {
    private static final String LOG_TAG = "HttpRequestTask";
    public interface AsyncResponse {
        void processFinish(String output);
    }

    public AsyncResponse delegate = null;

    public HttpRequestTask(AsyncResponse delegate){
        this.delegate = delegate;
    }

    @Override
    protected String doInBackground(String... params) {
        String json = params[0];
        String urlString = "http://127.0.0.1:8080/completion"; // Ensure this is consistent
        Log.d(LOG_TAG, "Connecting to server at: " + urlString);
        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "text/event-stream");
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = json.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            Log.d(LOG_TAG, "Response Code: " + responseCode);

            if (responseCode == HttpURLConnection.HTTP_OK) { // 200
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"))) {
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                    Log.d(LOG_TAG, "Response from server: " + response.toString());
                    return response.toString(); // Return the response from the server
                }
            } else {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "utf-8"))) {
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                    Log.e(LOG_TAG, "Error response from server: " + response.toString());
                    return "Error: " + responseCode + " - " + response.toString();
                }
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Exception occurred: " + e.getMessage(), e);
            return "Failed";
        }
    }

    @Override
    protected void onPostExecute(String result) {
        delegate.processFinish(result);
    }
}*/