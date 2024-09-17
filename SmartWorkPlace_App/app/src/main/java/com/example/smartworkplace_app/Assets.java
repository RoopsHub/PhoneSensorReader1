package com.example.smartworkplace_app;

import android.content.Context;

import androidx.annotation.NonNull;

public class Assets {

    @NonNull
    public static String getTessDataPath(@NonNull Context context) {
        return context.getFilesDir() + "/tessdata/";
    }

}
