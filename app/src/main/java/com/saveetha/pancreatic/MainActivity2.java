package com.saveetha.pancreatic;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import java.io.IOException;
import java.security.GeneralSecurityException;

public class MainActivity2 extends AppCompatActivity {

    private static final String TAG = "MainActivity2";
    private static final String SECURE_PREF_NAME = "secure_user_session";
    private SharedPreferences securePrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);

        try {
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            securePrefs = EncryptedSharedPreferences.create(
                SECURE_PREF_NAME,
                masterKeyAlias,
                this,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "Could not create EncryptedSharedPreferences", e);
        }

        // Check if already logged in
        if (securePrefs != null && securePrefs.getBoolean("isLoggedIn", false)) {
            startActivity(new Intent(MainActivity2.this, DashboardActivity.class));
            finish();
            return;
        }

        Button loginButton = findViewById(R.id.loginButton);
        Button createAccountButton = findViewById(R.id.createAccountButton);

        loginButton.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity2.this, LoginActivity.class));
        });

        createAccountButton.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity2.this, CreateAccountActivity.class));
        });
    }
}
