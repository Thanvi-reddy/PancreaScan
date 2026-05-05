package com.saveetha.pancreatic;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CreateAccountActivity extends AppCompatActivity {

    private static final String TAG = "CreateAccountActivity";

    private EditText signupName, signupEmail, signupPassword;
    private Button btnSignup;
    private View loadingOverlay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        signupName = findViewById(R.id.signupName);
        signupEmail = findViewById(R.id.signupEmail);
        signupPassword = findViewById(R.id.signupPassword);
        btnSignup = findViewById(R.id.btnSignup);
        loadingOverlay = findViewById(R.id.loadingOverlay);

        btnSignup.setOnClickListener(v -> {
            String name = signupName.getText().toString().trim();
            String email = signupEmail.getText().toString().trim();
            String password = signupPassword.getText().toString().trim();

            if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(CreateAccountActivity.this, "Please fill all details", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!isPasswordValid(password)) {
                signupPassword.setError("Min 8 chars with upper, lower, and number");
                return;
            }

            if (!isFullNameValid(name)) {
                signupName.setError("Please enter a valid name (letters and spaces only, max 30 characters).");
                return;
            }

            checkEmailAndRegister(name, email, password);
        });
    }

    private void checkEmailAndRegister(String name, String email, String password) {
        // Show Loading
        loadingOverlay.setVisibility(View.VISIBLE);
        btnSignup.setEnabled(false);

        ApiService api = ApiClient.getClient(this).create(ApiService.class);

        // Unified "signup" action (replaces checkEmail and register)
        api.auth("signup", email, password, name, null, null).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                loadingOverlay.setVisibility(View.GONE);
                btnSignup.setEnabled(true);

                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String rawResponse = response.body().string();
                        Log.d(TAG, "Raw Signup Response: " + rawResponse);

                        Gson gson = new Gson();
                        AuthResponse authResponse = gson.fromJson(rawResponse, AuthResponse.class);

                        if (authResponse != null && authResponse.isSuccess()) {
                            Toast.makeText(CreateAccountActivity.this, "Registration successful!", Toast.LENGTH_SHORT).show();
                            
                            // Auto-Login
                            saveUserSession(authResponse.user, password);
                            
                            Intent intent = new Intent(CreateAccountActivity.this, DashboardActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish();
                        } else {
                            String errorMessage = (authResponse != null) ? authResponse.message : "Registration failed.";
                            Toast.makeText(CreateAccountActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                        }
                    } catch (JsonSyntaxException | IOException e) {
                        Log.e(TAG, "Error parsing server response", e);
                        Toast.makeText(CreateAccountActivity.this, "Error processing server response.", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Log.e(TAG, "Unsuccessful response: " + response.code());
                    Toast.makeText(CreateAccountActivity.this, "Registration failed: Server error.", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                loadingOverlay.setVisibility(View.GONE);
                btnSignup.setEnabled(true);
                Log.e(TAG, "Registration network request failed", t);
                Toast.makeText(CreateAccountActivity.this, "Network error. Please try again.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveUserSession(AuthResponse.User user, String password) {
         try {
            String masterKeyAlias = androidx.security.crypto.MasterKeys.getOrCreate(androidx.security.crypto.MasterKeys.AES256_GCM_SPEC);
            android.content.SharedPreferences securePrefs = androidx.security.crypto.EncryptedSharedPreferences.create(
                "secure_user_session",
                masterKeyAlias,
                this,
                androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
            
            securePrefs.edit()
                .putBoolean("isLoggedIn", true)
                .putInt("user_id", user.id)
                .putString("user_name", user.name)
                .putString("user_email", user.email)
                .putString("user_password", password)
                .apply();
                
        } catch (java.security.GeneralSecurityException | IOException e) {
            Log.e(TAG, "Failed to save session", e);
        }
    }

    private boolean isPasswordValid(String password) {
        return password.length() >= 8 &&
                password.matches(".*[A-Z].*") &&
                password.matches(".*[a-z].*") &&
                password.matches(".*[0-9].*");
    }

    private boolean isFullNameValid(String name) {
        return name.matches("[a-zA-Z ]+") && name.length() <= 30;
    }
}
