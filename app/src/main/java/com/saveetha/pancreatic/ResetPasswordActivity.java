package com.saveetha.pancreatic;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ResetPasswordActivity extends AppCompatActivity {

    private EditText etNewPassword, etConfirmPassword;
    private Button btnUpdatePassword;
    private String email;
    private android.view.View loadingOverlay;
    private android.widget.TextView tvLoadingText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reset_password);

        etNewPassword = findViewById(R.id.etNewPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        btnUpdatePassword = findViewById(R.id.btnUpdatePassword);
        loadingOverlay = findViewById(R.id.loadingOverlay);
        tvLoadingText = findViewById(R.id.tvLoadingText);

        email = getIntent().getStringExtra("email");

        btnUpdatePassword.setOnClickListener(v -> {
            String newPassword = etNewPassword.getText().toString().trim();
            String confirmPassword = etConfirmPassword.getText().toString().trim();

            if (newPassword.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!newPassword.equals(confirmPassword)) {
                etConfirmPassword.setError("Passwords do not match");
                return;
            }

            if (!isPasswordValid(newPassword)) {
                etNewPassword.setError("Min 8 chars with upper, lower, and number");
                return;
            }

            // Show loading
            tvLoadingText.setText("Please wait, we are updating your new password...");
            loadingOverlay.setVisibility(android.view.View.VISIBLE);

            ApiService api = ApiClient.getClient(this).create(ApiService.class);
            
            // Prioritize extracted OTP from first step, fallback to email as token
            String otpValue = getIntent().getStringExtra("extracted_otp");
            if (otpValue == null) {
                otpValue = email; // Some backends use the email itself as a verification token
            }
            
            api.auth("reset_password", email, newPassword, null, otpValue, newPassword).enqueue(new retrofit2.Callback<okhttp3.ResponseBody>() {
                @Override
                public void onResponse(retrofit2.Call<okhttp3.ResponseBody> call, retrofit2.Response<okhttp3.ResponseBody> response) {
                    loadingOverlay.setVisibility(android.view.View.GONE);
                    if (response.isSuccessful() && response.body() != null) {
                        try {
                            String rawResponse = response.body().string();
                            com.google.gson.Gson gson = new com.google.gson.Gson();
                            AuthResponse authResponse = gson.fromJson(rawResponse, AuthResponse.class);

                            if (authResponse != null && authResponse.isSuccess()) {
                                Toast.makeText(ResetPasswordActivity.this, "Password updated successfully. Please log in.", Toast.LENGTH_LONG).show();
                                Intent intent = new Intent(ResetPasswordActivity.this, MainActivity2.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                                finish();
                            } else {
                                String msg = (authResponse != null) ? authResponse.message : "Reset failed.";
                                Toast.makeText(ResetPasswordActivity.this, msg, Toast.LENGTH_SHORT).show();
                            }
                        } catch (java.io.IOException e) {
                            android.util.Log.e("ResetPassword", "Error parsing", e);
                            Toast.makeText(ResetPasswordActivity.this, "Server error.", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(ResetPasswordActivity.this, "Request failed: " + response.code(), Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(retrofit2.Call<okhttp3.ResponseBody> call, Throwable t) {
                    loadingOverlay.setVisibility(android.view.View.GONE);
                    Toast.makeText(ResetPasswordActivity.this, "Network error. Please try again.", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private boolean isPasswordValid(String password) {
        return password.length() >= 8 &&
                password.matches(".*[A-Z].*") &&
                password.matches(".*[a-z].*") &&
                password.matches(".*[0-9].*");
    }
}
