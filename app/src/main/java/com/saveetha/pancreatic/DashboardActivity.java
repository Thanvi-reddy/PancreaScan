package com.saveetha.pancreatic;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LiveData;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import android.content.Context;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.security.GeneralSecurityException;

public class DashboardActivity extends AppCompatActivity {

    private static final String TAG = "DashboardActivity";
    private static final String SECURE_PREF_NAME = "secure_user_session";
    private SharedPreferences securePrefs;
    private AnalysisDao analysisDao;
    private TextView tvModeStatus;
    private ConnectivityManager.NetworkCallback networkCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        analysisDao = AppDatabase.getInstance(this).analysisDao();

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

        initializeUI();
        setupNavigation();
        observeStats();
        startConnectivityTracking();
        validateSession();
    }


    private void validateSession() {
        if (securePrefs == null) return;
        
        String email = securePrefs.getString("user_email", null);
        String password = securePrefs.getString("user_password", null);

        if (email != null && password != null) {
            ApiService api = ApiClient.getClient(this).create(ApiService.class);
            api.auth("login", email, password, null, null, null).enqueue(new retrofit2.Callback<okhttp3.ResponseBody>() {
                @Override
                public void onResponse(retrofit2.Call<okhttp3.ResponseBody> call, retrofit2.Response<okhttp3.ResponseBody> response) {
                    if (response.isSuccessful() && response.body() != null) {
                         try {
                            String rawResponse = response.body().string();
                            com.google.gson.Gson gson = new com.google.gson.Gson();
                            AuthResponse auth = gson.fromJson(rawResponse, AuthResponse.class);
                            
                            // If auto-login fails specifically because user not found, force logout
                            if (auth != null && "error".equalsIgnoreCase(auth.status)) {
                                if (auth.message != null && auth.message.contains("User not found")) {
                                    Log.w(TAG, "Session invalid: User not found. Logging out.");
                                    logout();
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Session check error", e);
                        }
                    }
                }

                @Override
                public void onFailure(retrofit2.Call<okhttp3.ResponseBody> call, Throwable t) {
                    // Ignore network errors for session check (offline mode is allowed)
                }
            });
        }
    }

    private void initializeUI() {
        TextView tvHello = findViewById(R.id.tvHello);
        TextView tvLoggedInAs = findViewById(R.id.tvLoggedInAs);
        TextView tvLogout = findViewById(R.id.tvLogout);
        Button btnUploadImage = findViewById(R.id.btnUploadImage);
        tvModeStatus = findViewById(R.id.tvModeStatus);

        if (securePrefs != null) {
            String name = securePrefs.getString("user_name", "User");
            String email = securePrefs.getString("user_email", "unknown");
            tvHello.setText("Hello, " + name);
            tvLoggedInAs.setText("Logged in as: " + email);
        }

        btnUploadImage.setOnClickListener(v -> {
            startActivity(new Intent(DashboardActivity.this, NewAnalysisActivity.class));
        });

        tvLogout.setOnClickListener(v -> {
            logout();
        });

        // Staggered Entrance Animations
        animateDashboardEntrance();
    }

    private void animateDashboardEntrance() {
        try {
            View[] views = new View[5];
            
            View hello = findViewById(R.id.tvHello);
            if (hello != null && hello.getParent() != null && hello.getParent().getParent() != null) {
                views[0] = (View) hello.getParent().getParent();
            }
            
            View upload = findViewById(R.id.btnUploadImage);
            if (upload != null && upload.getParent() != null && upload.getParent().getParent() != null) {
                views[1] = (View) upload.getParent().getParent();
            }
            
            View total = findViewById(R.id.tvTotalScans);
            if (total != null && total.getParent() != null && total.getParent().getParent() != null && total.getParent().getParent().getParent() != null) {
                views[2] = (View) total.getParent().getParent().getParent();
            }
            
            View mode = findViewById(R.id.tvModeStatus);
            if (mode != null && mode.getParent() != null && mode.getParent().getParent() != null && mode.getParent().getParent().getParent() != null) {
                views[3] = (View) mode.getParent().getParent().getParent();
            }
            
            View loggedIn = findViewById(R.id.tvLoggedInAs);
            if (loggedIn != null && loggedIn.getParent() != null) {
                views[4] = (View) loggedIn.getParent();
            }

            for (int i = 0; i < views.length; i++) {
                final View v = views[i];
                if (v != null) {
                    v.setAlpha(0f);
                    v.setTranslationY(100f);
                    v.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(500)
                        .setStartDelay(i * 100)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator())
                        .start();
                }
            }
        } catch (Exception e) {
            Log.e("DashboardActivity", "Error in entrance animation", e);
        }
    }

    private void setupNavigation() {
        View navHistory = findViewById(R.id.navHistory);
        View navSettings = findViewById(R.id.navSettings);

        navHistory.setOnClickListener(v -> {
            startActivity(new Intent(DashboardActivity.this, PatientRecordsActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });

        navSettings.setOnClickListener(v -> {
            startActivity(new Intent(DashboardActivity.this, SettingsActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });
    }

    private void observeStats() {
        TextView tvTotalScans = findViewById(R.id.tvTotalScans);
        TextView tvNormalScans = findViewById(R.id.tvNormalScans);
        TextView tvAbnormalScans = findViewById(R.id.tvAbnormalScans);

        String emailRaw = securePrefs != null ? securePrefs.getString("user_email", "") : "";
        String email = emailRaw.trim().toLowerCase();

        analysisDao.getTotalScansCount(email).observe(this, count -> {
            animateCounter(tvTotalScans, count != null ? count : 0);
        });

        analysisDao.getNormalScansCount(email).observe(this, count -> {
            animateCounter(tvNormalScans, count != null ? count : 0);
        });

        analysisDao.getAbnormalScansCount(email).observe(this, count -> {
            animateCounter(tvAbnormalScans, count != null ? count : 0);
        });
    }

    private void animateCounter(TextView textView, int targetValue) {
        if (textView == null) return;
        android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofInt(0, targetValue);
        animator.setDuration(1000); // 1 second animation
        animator.setInterpolator(new android.view.animation.DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            textView.setText(String.valueOf(animation.getAnimatedValue()));
        });
        animator.start();
    }

    private void logout() {
        if (securePrefs != null) {
            securePrefs.edit().clear().apply();
        }
        Intent intent = new Intent(DashboardActivity.this, MainActivity2.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();
    }

    private void startConnectivityTracking() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return;

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                new Handler(Looper.getMainLooper()).post(() -> updateStatus(true));
            }

            @Override
            public void onLost(Network network) {
                // If the specific network is lost, check if any others exist
                boolean isConnected = false;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    Network activeNetwork = cm.getActiveNetwork();
                    if (activeNetwork != null) {
                        NetworkCapabilities caps = cm.getNetworkCapabilities(activeNetwork);
                        isConnected = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
                    }
                } else {
                    isConnected = cm.getActiveNetworkInfo() != null && cm.getActiveNetworkInfo().isConnected();
                }
                boolean finalIsConnected = isConnected;
                new Handler(Looper.getMainLooper()).post(() -> updateStatus(finalIsConnected));
            }
        };

        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        cm.registerNetworkCallback(request, networkCallback);

        // Initial check
        boolean initialConnected = false;
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            Network activeNetwork = cm.getActiveNetwork();
            if (activeNetwork != null) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(activeNetwork);
                initialConnected = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            }
        } else {
            initialConnected = cm.getActiveNetworkInfo() != null && cm.getActiveNetworkInfo().isConnected();
        }
        updateStatus(initialConnected);
    }

    private void updateStatus(boolean online) {
        if (tvModeStatus == null) return;
        if (online) {
            tvModeStatus.setText("Online");
            tvModeStatus.setTextColor(Color.parseColor("#4CAF50")); // Green
        } else {
            tvModeStatus.setText("Offline");
            tvModeStatus.setTextColor(Color.parseColor("#F44336")); // Red
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (networkCallback != null) {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) cm.unregisterNetworkCallback(networkCallback);
        }
    }
}
