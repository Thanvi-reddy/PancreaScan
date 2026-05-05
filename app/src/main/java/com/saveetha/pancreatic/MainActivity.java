package com.saveetha.pancreatic;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            // Check login status
            SharedPreferences prefs = getSharedPreferences("user_session", Context.MODE_PRIVATE);
            boolean isLoggedIn = prefs.getBoolean("isLoggedIn", false);

            Intent intent;
            if (isLoggedIn) {
                // User is logged in, go to Dashboard
                intent = new Intent(MainActivity.this, DashboardActivity.class);
            } else {
                // User is not logged in, go to Login screen
                intent = new Intent(MainActivity.this, MainActivity2.class);
            }
            startActivity(intent);
            finish();
        }, 3000); // 3-second delay
    }
}