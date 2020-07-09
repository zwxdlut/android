package com.example.progress_bar;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.cloud.progressbar.ProgressButton;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ProgressButton button = findViewById(R.id.progress_button);
        button.setMaxProgress(100);
        button.setProgress(50);
    }
}
