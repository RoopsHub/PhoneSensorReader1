package com.example.smartworkplace_app;

import android.content.Intent;
import android.os.Bundle;

import com.google.android.material.snackbar.Snackbar;

import androidx.appcompat.app.AppCompatActivity;

import android.view.View;

import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.smartworkplace_app.databinding.ActivityMainBinding;

import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    TextView textView;
    Button Image, Voice;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Image = findViewById(R.id.button_img);
        Voice = findViewById(R.id.button_voice);
        textView = findViewById(R.id.text_data);

        // Set OnClickListener for Image button to open ImageHandler
        Image.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Start ImageHandler activity
                Intent intent = new Intent(MainActivity.this, ImageHandler.class);
                startActivity(intent);
            }
        });

        // Set OnClickListener for Voice button to open VoiceHandler
        Voice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Start VoiceHandler activity
                Intent intent = new Intent(MainActivity.this, VoiceHandler.class);
                startActivity(intent);
            }
        });
    }


}