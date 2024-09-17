package com.example.smartworkplace_app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

public class ImageHandler extends AppCompatActivity {

    private static final int IMAGE_REQUEST_CODE = 100;
    private ImageView imageView;
    private Uri imageUri;
    private TessBaseAPI tessApi;
    private boolean tessProcessing = false;
    private boolean stopped = false;
    private final Object recycleLock = new Object();
    private boolean recycleAfterProcessing = false;
    private static final String TESSDATA_PATH = "tessdata"; // Relative path for tessdata in assets
    private static final String LANGUAGE = "eng";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_handler);


        try {
            // Copy tessdata to internal storage
            String tessDataPath = copyTessDataToInternalStorage();

            // Initialize Tesseract API with the extracted tessdata
            TessBaseAPI tessApi = new TessBaseAPI();
            boolean initSuccess = tessApi.init(tessDataPath, LANGUAGE);

            if (!initSuccess) {
                throw new RuntimeException("Tesseract initialization failed.");
            }

            Bitmap imageBitmap = getImageBitmap();  // Load the image as Bitmap

            if (imageBitmap != null) {
                // Set the Bitmap for OCR processing
                tessApi.setImage(imageBitmap);

                // Extract text from the image
                String extractedText = tessApi.getUTF8Text();

                // Show the result in the log or UI
                Log.d("ImageHandler", "Extracted Text: " + extractedText);

                // Clean up
                tessApi.stop();
            } else {
                Log.e("ImageHandler", "Failed to load image as Bitmap.");
            }
        } catch (IOException e) {
            Log.e("ImageHandler", "Error copying tessdata or processing image", e);
        }
    }
    // Method to copy eng.traineddata from assets to internal storage
    private String copyTessDataToInternalStorage() throws IOException {
        // Get the internal storage directory for the app
        File tessDir = new File(getFilesDir(), TESSDATA_PATH);

        if (!tessDir.exists()) {
            tessDir.mkdirs();
        }

        // Copy the eng.traineddata file from assets/tessdata to internal storage
        String trainedDataFilePath = tessDir.getAbsolutePath() + "/eng.traineddata";
        File trainedDataFile = new File(trainedDataFilePath);

        if (!trainedDataFile.exists()) {
            try (InputStream in = getAssets().open(TESSDATA_PATH + "/eng.traineddata");
                 FileOutputStream out = new FileOutputStream(trainedDataFile)) {
                byte[] buffer = new byte[1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                Log.d("ImageHandler", "eng.traineddata file copied to internal storage.");
            }
        } else {
            Log.d("ImageHandler", "eng.traineddata file already exists in internal storage.");
        }

        return tessDir.getAbsolutePath();

    }
    private Bitmap getImageBitmap() {
        // Path to the image file. Replace with your image file path
        String imagePath = getFilesDir() + "/sample_image.jpg";

        // Load the image from the file path
        File imageFile = new File(imagePath);

        if (imageFile.exists()) {
            // Decode the image file into a Bitmap
            return BitmapFactory.decodeFile(imageFile.getAbsolutePath());
        } else {
            Log.e("ImageHandler", "Image file not found: " + imagePath);
            return null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == IMAGE_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            imageUri = data.getData();

            try {
                // Load selected image into ImageView
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
                imageView.setImageBitmap(bitmap);

                // Start OCR processing on a separate thread
                startOcrProcessing(bitmap);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void startOcrProcessing(Bitmap bitmap) {
        // Start process in another thread
        new Thread(() -> {
            tessProcessing = true;

            // Set the image for recognition
            tessApi.setImage(bitmap);

            long startTime = SystemClock.uptimeMillis();

            // Use getHOCRText(0) method to trigger recognition with progress notifications
            tessApi.getHOCRText(0);

            // Extract text from the image
            String recognizedText = tessApi.getUTF8Text();

            // Clear the recognition results and image
            tessApi.clear();

            // Calculate the time taken for recognition
            long duration = SystemClock.uptimeMillis() - startTime;

            runOnUiThread(() -> {
                // Show result and processing time on UI thread
                Toast.makeText(this, "OCR Result: " + recognizedText, Toast.LENGTH_LONG).show();
                Toast.makeText(this, String.format(Locale.ENGLISH, "Completed in %.3fs.", (duration / 1000f)), Toast.LENGTH_LONG).show();
            });

            synchronized (recycleLock) {
                tessProcessing = false;

                // Recycle the instance here if the view model is already destroyed
                if (recycleAfterProcessing) {
                    tessApi.recycle();
                }
            }
        }).start();
    }

    public void stopOcr() {
        if (!tessProcessing) {
            return;
        }
        stopped = true;
        tessApi.stop();
        Toast.makeText(this, "OCR Stopped", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Recycle tessApi when activity is destroyed
        tessApi.stop();
    }
}
