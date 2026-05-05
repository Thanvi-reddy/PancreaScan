package com.saveetha.pancreatic;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;

import java.io.IOException;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SignupActivity extends AppCompatActivity {

    private static final String TAG = "SignupActivity";

    private EditText signupName, signupEmail, signupPassword;
    private Button btnSignup;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        signupName = findViewById(R.id.signupName);
        signupEmail = findViewById(R.id.signupEmail);
        signupPassword = findViewById(R.id.signupPassword);
        btnSignup = findViewById(R.id.btnSignup);

        btnSignup.setOnClickListener(v -> {
            String name = signupName.getText().toString().trim();
            String email = signupEmail.getText().toString().trim();
            String password = signupPassword.getText().toString().trim();

            if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(SignupActivity.this, "Please fill all details", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!isPasswordValid(password)) {
                signupPassword.setError("Min 8 chars with upper, lower, and number");
                return;
            }

            ApiService api = ApiClient.getClient(this).create(ApiService.class);
            // Unified "signup" action
            api.auth("signup", email, password, name, null, null).enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        try {
                            String rawResponse = response.body().string();
                            Log.d(TAG, "Raw Server Response: " + rawResponse);

                            Gson gson = new Gson();
                            AuthResponse authResponse = gson.fromJson(rawResponse, AuthResponse.class);

                            if (authResponse != null && authResponse.isSuccess()) {
                                Toast.makeText(SignupActivity.this, "Registration successful!", Toast.LENGTH_SHORT).show();
                                
                                // Auto-Login Logic
                                saveUserSession(authResponse.user, password);
                                
                                Intent intent = new Intent(SignupActivity.this, DashboardActivity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                                finish();
                            } else {
                                String errorMessage = (authResponse != null) ? authResponse.message : "Registration failed.";
                                Toast.makeText(SignupActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                            }
                        } catch (IOException e) {
                            Log.e(TAG, "Error parsing response", e);
                            Toast.makeText(SignupActivity.this, "Error parsing server response.", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Log.e(TAG, "Unsuccessful response code: " + response.code());
                        Toast.makeText(SignupActivity.this, "Registration failed: Server error.", Toast.LENGTH_LONG).show();
                    }
                }

                @Override
                public void onFailure(Call<ResponseBody> call, Throwable t) {
                    Log.e(TAG, "onFailure: Network request failed.", t);
                    Toast.makeText(SignupActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
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
}
