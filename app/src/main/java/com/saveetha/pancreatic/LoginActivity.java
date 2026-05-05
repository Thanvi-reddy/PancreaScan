package com.saveetha.pancreatic;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import com.google.gson.Gson;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.security.GeneralSecurityException;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {
    private static final int REQ_CREATE = 101;
    private static final String TAG = "LoginActivity";
    private static final String SECURE_PREF_NAME = "secure_user_session";

    private EditText emailEt, passwordEt;
    private Button loginBtn;
    private TextView forgotPasswordTv;
    private View loadingOverlay;
    private SharedPreferences securePrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

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
            Toast.makeText(this, "Critical security error.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (securePrefs.getBoolean("isLoggedIn", false)) {
            startActivity(new Intent(LoginActivity.this, DashboardActivity.class));
            finish();
            return;
        }

        emailEt = findViewById(R.id.et_email);
        passwordEt = findViewById(R.id.et_password);
        loginBtn = findViewById(R.id.btn_login);
        forgotPasswordTv = findViewById(R.id.tv_forgot_password);
        loadingOverlay = findViewById(R.id.loadingOverlay);

        loginBtn.setOnClickListener(v -> loginUser());

        forgotPasswordTv.setOnClickListener(v -> {
            Intent i = new Intent(LoginActivity.this, ForgotPasswordActivity.class);
            startActivity(i);
        });
    }

    private void loginUser() {
        String email = emailEt.getText().toString().trim();
        String pass = passwordEt.getText().toString().trim();
        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(pass)) {
            Toast.makeText(LoginActivity.this, "Enter email and password", Toast.LENGTH_SHORT).show();
            return;
        }

        ApiService api = ApiClient.getClient(this).create(ApiService.class);
        
        loadingOverlay.setVisibility(View.VISIBLE);
        loginBtn.setEnabled(false);

        api.auth("login", email, pass, null, null, null).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                loadingOverlay.setVisibility(View.GONE);
                loginBtn.setEnabled(true);

                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String rawResponse = response.body().string();
                        Log.d(TAG, "Raw Login Response: " + rawResponse);

                        Gson gson = new Gson();
                        AuthResponse authResponse = gson.fromJson(rawResponse, AuthResponse.class);

                        if (authResponse != null && authResponse.isSuccess() && authResponse.user != null) {
                            saveUserSession(authResponse.user, pass);
                            Toast.makeText(LoginActivity.this, "Login Successful", Toast.LENGTH_SHORT).show();
                            startActivity(new Intent(LoginActivity.this, DashboardActivity.class));
                            finish();
                        } else {
                            String message = (authResponse != null && authResponse.message != null) 
                                             ? authResponse.message 
                                             : "Invalid credentials.";
                            
                            if (message.contains("User not found")) {
                                emailEt.setError("No account found");
                                emailEt.requestFocus();
                            } else {
                                Toast.makeText(LoginActivity.this, "Login Failed: " + message, Toast.LENGTH_LONG).show();
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Parsing error", e);
                        Toast.makeText(LoginActivity.this, "Login Failed: Server error", Toast.LENGTH_SHORT).show();
                    }
                } else {
                     Log.e(TAG, "login error code=" + response.code());
                    Toast.makeText(LoginActivity.this, "Login failed with error code: " + response.code(), Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                loadingOverlay.setVisibility(View.GONE);
                loginBtn.setEnabled(true);
                Log.e(TAG, "onFailure: Network request failed.", t);
                Toast.makeText(LoginActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void saveUserSession(AuthResponse.User user, String password) {
        String email = user.email != null ? user.email.trim().toLowerCase() : "";
        securePrefs.edit()
            .putBoolean("isLoggedIn", true)
            .putInt("user_id", user.id)
            .putString("user_name", user.name)
            .putString("user_email", email)
            .putString("user_password", password) // For offline login
            .apply();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_CREATE && resultCode == Activity.RESULT_OK && data != null) {
            String createdEmail = data.getStringExtra("email");
            if (createdEmail != null) {
                emailEt.setText(createdEmail);
            }
        }
    }
}
