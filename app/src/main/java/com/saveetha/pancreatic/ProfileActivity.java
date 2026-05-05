package com.saveetha.pancreatic;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import java.io.IOException;
import java.security.GeneralSecurityException;

public class ProfileActivity extends AppCompatActivity {

    private static final String SECURE_PREF_NAME = "secure_user_session";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        TextView tvProfileName = findViewById(R.id.tvProfileName);
        TextView tvProfileEmail = findViewById(R.id.tvProfileEmail);
        ImageButton btnBack = findViewById(R.id.btnBackProfile);

        btnBack.setOnClickListener(v -> finish());

        try {
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            SharedPreferences securePrefs = EncryptedSharedPreferences.create(
                SECURE_PREF_NAME,
                masterKeyAlias,
                this,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );

            String name = securePrefs.getString("user_name", "N/A");
            String email = securePrefs.getString("user_email", "N/A");

            tvProfileName.setText("Name: " + name);
            tvProfileEmail.setText("Email: " + email);

        } catch (GeneralSecurityException | IOException e) {
            Toast.makeText(this, "Could not load profile.", Toast.LENGTH_SHORT).show();
        }
    }
}
