package com.saveetha.pancreatic;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ForgotPasswordActivity extends AppCompatActivity {

    private EditText etForgotPasswordEmail;
    private Button btnResetPassword;
    private android.widget.TextView tvBackToLogin;
    private android.view.View llErrorLayout;
    private android.view.View loadingOverlay;
    private android.widget.TextView tvLoadingText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        etForgotPasswordEmail = findViewById(R.id.etForgotPasswordEmail);
        btnResetPassword = findViewById(R.id.btnResetPassword);
        tvBackToLogin = findViewById(R.id.tvBackToLogin);
        llErrorLayout = findViewById(R.id.llErrorLayout);
        loadingOverlay = findViewById(R.id.loadingOverlay);
        tvLoadingText = findViewById(R.id.tvLoadingText);

        tvBackToLogin.setOnClickListener(v -> finish());

        etForgotPasswordEmail.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                llErrorLayout.setVisibility(android.view.View.GONE);
            }
            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        btnResetPassword.setOnClickListener(v -> {
            String email = etForgotPasswordEmail.getText().toString().trim();
            if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etForgotPasswordEmail.setError("Enter a valid email");
                return;
            }

            // Show loading
            tvLoadingText.setText("Please wait, we are verifying account...");
            loadingOverlay.setVisibility(android.view.View.VISIBLE);

            // Unified "request_password_reset" action
            ApiService api = ApiClient.getClient(this).create(ApiService.class);
            api.auth("request_password_reset", email, null, null, null, null).enqueue(new retrofit2.Callback<okhttp3.ResponseBody>() {
                @Override
                public void onResponse(retrofit2.Call<okhttp3.ResponseBody> call, retrofit2.Response<okhttp3.ResponseBody> response) {
                    android.util.Log.d("ForgotPassword", "Headers: " + response.headers().toString());
                    loadingOverlay.setVisibility(android.view.View.GONE);
                    if (response.isSuccessful() && response.body() != null) {
                        try {
                            String rawResponse = response.body().string();
                            android.util.Log.d("ForgotPassword", "Raw Response: " + rawResponse);
                            com.google.gson.Gson gson = new com.google.gson.Gson();
                            AuthResponse authResponse = gson.fromJson(rawResponse, AuthResponse.class);

                            if (authResponse != null && authResponse.isSuccess()) {
                                Toast.makeText(ForgotPasswordActivity.this, "Account verified. Set your new password.", Toast.LENGTH_SHORT).show();
                                Intent i = new Intent(ForgotPasswordActivity.this, ResetPasswordActivity.class);
                                i.putExtra("email", email);
                                
                                // Look for OTP in message if any
                                if (authResponse.message != null) {
                                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\b(\\d{6})\\b").matcher(authResponse.message);
                                    if (m.find()) {
                                        i.putExtra("extracted_otp", m.group(1));
                                    }
                                }

                                if (authResponse.user != null) {
                                    i.putExtra("user_id", authResponse.user.id);
                                }
                                startActivity(i);
                            } else {
                                llErrorLayout.setVisibility(android.view.View.VISIBLE);
                            }
                        } catch (java.io.IOException e) {
                            android.util.Log.e("ForgotPassword", "Error parsing", e);
                            Toast.makeText(ForgotPasswordActivity.this, "Server error.", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(ForgotPasswordActivity.this, "Request failed: " + response.code(), Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(retrofit2.Call<okhttp3.ResponseBody> call, Throwable t) {
                    loadingOverlay.setVisibility(android.view.View.GONE);
                    Toast.makeText(ForgotPasswordActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        });
    }
}
