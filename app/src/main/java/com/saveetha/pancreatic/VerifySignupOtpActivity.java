package com.saveetha.pancreatic;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class VerifySignupOtpActivity extends AppCompatActivity {

    private EditText etOtp;
    private Button btnVerify;
    private View loadingOverlay;
    private String email;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verify_signup_otp);

        etOtp = findViewById(R.id.etOtp);
        btnVerify = findViewById(R.id.btnVerify);
        loadingOverlay = findViewById(R.id.loadingOverlay);

        email = getIntent().getStringExtra("email");

        btnVerify.setOnClickListener(v -> {
            String otp = etOtp.getText().toString().trim();

            if (otp.isEmpty() || otp.length() != 6) {
                etOtp.setError("Please enter a valid 6-digit OTP");
                return;
            }
            
            loadingOverlay.setVisibility(View.VISIBLE);
            btnVerify.setEnabled(false);

            ApiService api = ApiClient.getClient(this).create(ApiService.class);
            // Corrected the method name to match ApiService.java
            // Unified "verify_otp" action
            api.auth("verify_otp", email, null, null, otp, null).enqueue(new retrofit2.Callback<okhttp3.ResponseBody>() {
                @Override
                public void onResponse(retrofit2.Call<okhttp3.ResponseBody> call, retrofit2.Response<okhttp3.ResponseBody> response) {
                    loadingOverlay.setVisibility(View.GONE);
                    btnVerify.setEnabled(true);
                    
                    if (response.isSuccessful() && response.body() != null) {
                         try {
                            String rawResponse = response.body().string();
                            com.google.gson.Gson gson = new com.google.gson.Gson();
                            AuthResponse authResponse = gson.fromJson(rawResponse, AuthResponse.class);

                            if (authResponse != null && authResponse.isSuccess()) {
                                Toast.makeText(VerifySignupOtpActivity.this, "Email verified. Please log in.", Toast.LENGTH_LONG).show();
                                Intent intent = new Intent(VerifySignupOtpActivity.this, MainActivity2.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                                finish();
                            } else {
                                Toast.makeText(VerifySignupOtpActivity.this, "Invalid OTP.", Toast.LENGTH_SHORT).show();
                            }
                        } catch (java.io.IOException e) {
                            android.util.Log.e("VerifyOtp", "Error parsing", e);
                            Toast.makeText(VerifySignupOtpActivity.this, "Server error.", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(VerifySignupOtpActivity.this, "Verification failed: " + response.code(), Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(retrofit2.Call<okhttp3.ResponseBody> call, Throwable t) {
                    loadingOverlay.setVisibility(View.GONE);
                    btnVerify.setEnabled(true);
                    Toast.makeText(VerifySignupOtpActivity.this, "Network error. Please try again.", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }
}
