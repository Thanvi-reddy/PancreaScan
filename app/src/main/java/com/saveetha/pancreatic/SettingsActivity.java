package com.saveetha.pancreatic;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageButton;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import org.json.JSONObject;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SettingsActivity extends AppCompatActivity {

    private static final String TAG = "SettingsActivity";
    private static final String SECURE_PREF_NAME = "secure_user_session";
    private AnalysisDao analysisDao;
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private SharedPreferences securePrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

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

        analysisDao = AppDatabase.getInstance(getApplicationContext()).analysisDao();

        TextView tvUserName = findViewById(R.id.tvUserName);
        TextView tvUserEmail = findViewById(R.id.tvUserEmail);

        if (securePrefs != null) {
            String name = securePrefs.getString("user_name", "User");
            String email = securePrefs.getString("user_email", "unknown");
            tvUserName.setText(name);
            tvUserEmail.setText(email);
        }

        setupNavigation();

        RecyclerView rvSettings = findViewById(R.id.rvSettings);
        rvSettings.setLayoutManager(new LinearLayoutManager(this));

        List<Setting> settingsList = new ArrayList<>();
        settingsList.add(new Setting(R.drawable.ic_app_info, "App Info", this::openAppInfo));
        settingsList.add(new Setting(R.drawable.ic_delete_records, "Delete All Patient Records", this::showDeleteRecordsDialog));
        settingsList.add(new Setting(R.drawable.ic_delete_forever, "Delete Account", this::showDeleteAccountDialog));
        settingsList.add(new Setting(R.drawable.ic_logout, "Log Out", this::logoutUser));

        SettingsAdapter adapter = new SettingsAdapter(settingsList);
        rvSettings.setAdapter(adapter);
    }

    private void showDeleteRecordsDialog() {

        new AlertDialog.Builder(this)
                .setTitle("Delete All Patient Records")
                .setMessage("Are you sure you want to delete all patient records from this device and the server? This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> deleteAllPatientRecords())
                .setNegativeButton(android.R.string.no, null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void deleteAllPatientRecords() {
        String userEmail = securePrefs.getString("user_email", null);
        
        // 1. Clear Local Database & Add to Tombstone for Safety
        databaseExecutor.execute(() -> {
            // Get all IDs first to add to tombstone (prevents zombie return if server fails slightly)
            List<AnalysisEntity> allRecords = analysisDao.getAllSync(userEmail);
            DeletedRecordDao deletedDao = AppDatabase.getInstance(this).deletedRecordDao();
            
            for (AnalysisEntity record : allRecords) {
                deletedDao.insert(new DeletedRecordEntity(record.patientId));
            }
            
            // Delete all local records
            analysisDao.deleteAll();

            // 2. Call Server to Clear History
            if (userEmail != null) {
                ApiService api = ApiClient.getClient(this).create(ApiService.class);
                api.clearHistory("clear_history", userEmail).enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        if (response.isSuccessful()) {
                            Log.d(TAG, "Server history cleared successfully.");
                            runOnUiThread(() -> Toast.makeText(SettingsActivity.this, "All records deleted from device and server.", Toast.LENGTH_SHORT).show());
                        } else {
                            Log.e(TAG, "Server clear history failed: " + response.code());
                            runOnUiThread(() -> Toast.makeText(SettingsActivity.this, "Records deleted locally, but server sync failed.", Toast.LENGTH_LONG).show());
                        }
                    }

                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                        Log.e(TAG, "Network error clearing history", t);
                        runOnUiThread(() -> Toast.makeText(SettingsActivity.this, "Network error. Records deleted locally only.", Toast.LENGTH_LONG).show());
                    }
                });
            } else {
                 runOnUiThread(() -> Toast.makeText(SettingsActivity.this, "Local records deleted. Please login to sync.", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void showDeleteAccountDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Account")
                .setMessage("Are you sure you want to permanently delete your account and all associated data from the server? This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> deleteUserAccount())
                .setNegativeButton(android.R.string.no, null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void deleteUserAccount() {
        String email = securePrefs.getString("user_email", null);
        if (email == null) return;

        ApiService api = ApiClient.getClient(this).create(ApiService.class);
        // Unified "delete_account" action
        api.auth("delete_account", email, null, null, null, null).enqueue(new Callback<ResponseBody>() {
             @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        String result = response.body().string();
                        // Manual parse or use AuthResponse
                        JSONObject json = new JSONObject(result);
                        if ("success".equalsIgnoreCase(json.optString("status"))) {
                            Log.d("DELETE_ACCOUNT", "Account deleted for " + email);
                            wipeDataAndLogout(true);
                        } else {
                            String message = json.optString("message", "Deletion failed.");
                            Toast.makeText(SettingsActivity.this, message, Toast.LENGTH_LONG).show();
                        }
                    } else {
                        Toast.makeText(SettingsActivity.this, "Error deleting account.", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                     Log.e(TAG, "Exception parsing delete account response", e);
                     Toast.makeText(SettingsActivity.this, "Error processing server response.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e(TAG, "Network error on delete account", t);
                Toast.makeText(SettingsActivity.this, "Network error. Could not delete account.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void wipeDataAndLogout(boolean fullWipe) {
        databaseExecutor.execute(() -> analysisDao.deleteAll());
        if (fullWipe) {
            securePrefs.edit().clear().apply();
             Toast.makeText(this, "Account deleted successfully. Logging out...", Toast.LENGTH_LONG).show();
        } else {
            securePrefs.edit().putBoolean("isLoggedIn", false).apply();
            Toast.makeText(this, "Logged out successfully.", Toast.LENGTH_SHORT).show();
        }
        goToLogin();
    }

    private void logoutUser() {
        wipeDataAndLogout(false);
    }

    private void goToLogin() {
        Intent intent = new Intent(SettingsActivity.this, MainActivity2.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void openAppInfo() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", getPackageName(), null);
        intent.setData(uri);
        startActivity(intent);
    }

    private void showTermsDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Terms and Conditions")
            .setMessage(Html.fromHtml(getString(R.string.terms_and_conditions_text), Html.FROM_HTML_MODE_LEGACY))
            .setPositiveButton(android.R.string.ok, null)
            .show();
    }

    private void checkForModelUpdate() {
        AlertDialog progressDialog = new AlertDialog.Builder(this)
                .setTitle("Checking Updates")
                .setMessage("Contacting Federated Learning Server...")
                .setCancelable(false)
                .show();

        ApiService api = ApiClient.getFederatedClient(this).create(ApiService.class);
        api.getGlobalModel("get_global_model").enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                progressDialog.dismiss();
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String jsonStr = response.body().string();
                        JSONObject json = new JSONObject(jsonStr);
                        
                        if ("success".equals(json.optString("status"))) {
                            String serverVersion = json.optString("version", "1.0.0");
                            String downloadUrl = json.optString("download_url");
                            
                            // Check local version
                            SharedPreferences prefs = getSharedPreferences("fl_prefs", MODE_PRIVATE);
                            String localVersion = prefs.getString("model_version", "1.0.0");
                            
                            if (isVersionNewer(serverVersion, localVersion)) {
                                showUpdateAvailableDialog(serverVersion, downloadUrl);
                            } else {
                                showUpToDateDialog(localVersion);
                            }
                        } else {
                            Toast.makeText(SettingsActivity.this, "Server Error: " + json.optString("message"), Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Update check failed", e);
                        Toast.makeText(SettingsActivity.this, "Failed to parse server response.", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(SettingsActivity.this, "Connection failed: " + response.code(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                progressDialog.dismiss();
                Log.e(TAG, "Network error", t);
                Toast.makeText(SettingsActivity.this, "Network Error. Check connection.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private boolean isVersionNewer(String serverVer, String localVer) {
        // Simple string comparison for now, assuming format x.y.z
        return serverVer.compareTo(localVer) > 0;
    }

    private void showUpToDateDialog(String version) {
        new AlertDialog.Builder(this)
                .setTitle("Up to Date")
                .setMessage("You are using the latest model version (" + version + ").")
                .setPositiveButton("OK", null)
                .setIcon(R.drawable.ic_app_info)
                .show();
    }

    private void showUpdateAvailableDialog(String newVersion, String url) {
        new AlertDialog.Builder(this)
                .setTitle("New Model Available")
                .setMessage("Version " + newVersion + " is available. Do you want to download it?\n\nThis will improve analysis accuracy.")
                .setPositiveButton("Download", (dialog, which) -> downloadModelUpdate(url, newVersion))
                .setNegativeButton("Later", null)
                .setIcon(R.drawable.ic_upload)
                .show();
    }

    private void downloadModelUpdate(String url, String version) {
        // Correct the URL if needed (localhost fix for emulator)
        String finalUrl = url;
            if (finalUrl.contains("localhost:8000")) {
                finalUrl = finalUrl.replace("localhost:8000", "14.139.187.229:8081");
            } else if (finalUrl.contains("localhost")) {
                finalUrl = finalUrl.replace("localhost", "14.139.187.229");
            }
            if (!finalUrl.startsWith("http")) {
                 if (finalUrl.startsWith("/")) finalUrl = finalUrl.substring(1);
                 finalUrl = ApiClient.BASE_URL + finalUrl;
            }

        AlertDialog downloadingDialog = new AlertDialog.Builder(this)
                .setTitle("Downloading...")
                .setMessage("Please wait while the new model is installed.")
                .setCancelable(false)
                .show();

        // Use OkHttp for clean download
        okhttp3.Request request = new okhttp3.Request.Builder().url(finalUrl).build();
        ApiClient.getOkHttpClient(this).newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                runOnUiThread(() -> {
                    downloadingDialog.dismiss();
                    Toast.makeText(SettingsActivity.this, "Download Failed", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                if (response.isSuccessful() && response.body() != null) {
                    java.io.File modelFile = new java.io.File(getFilesDir(), "pancreas.tflite"); // Overwrite current model
                    try (java.io.FileOutputStream fos = new java.io.FileOutputStream(modelFile)) {
                        fos.write(response.body().bytes());
                    }
                    
                    // Update version prefs
                    getSharedPreferences("fl_prefs", MODE_PRIVATE).edit()
                            .putString("model_version", version)
                            .apply();

                    runOnUiThread(() -> {
                        downloadingDialog.dismiss();
                        Toast.makeText(SettingsActivity.this, "Model Updated to v" + version + "!", Toast.LENGTH_LONG).show();
                    });
                } else {
                    runOnUiThread(() -> {
                        downloadingDialog.dismiss();
                        Toast.makeText(SettingsActivity.this, "Download Error: " + response.code(), Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    private void syncTrainingData() {
        new AlertDialog.Builder(this)
                .setTitle("Sync Training Data")
                .setMessage("This will contribute your anonymized training data to improve the global AI model through federated learning.\n\nYour patient data remains private and secure on your device. Only model updates are shared.\n\nDo you want to proceed?")
                .setPositiveButton("Sync Now", (dialog, which) -> {
                    Toast.makeText(this, "Syncing training data...", Toast.LENGTH_SHORT).show();
                    
                    androidx.work.OneTimeWorkRequest flRequest = new androidx.work.OneTimeWorkRequest.Builder(FederatedWorker.class)
                            .build();
                    androidx.work.WorkManager.getInstance(SettingsActivity.this).enqueue(flRequest);
                })
                .setNegativeButton("Cancel", null)
                .setIcon(R.drawable.ic_upload)
                .show();
    }

    private void setupNavigation() {
        View navHome = findViewById(R.id.navHome);
        View navHistory = findViewById(R.id.navHistory);

        navHome.setOnClickListener(v -> {
            Intent intent = new Intent(this, DashboardActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });

        navHistory.setOnClickListener(v -> {
            Intent intent = new Intent(this, PatientRecordsActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });

        // Federated Learning section
        View btnCheckModelUpdate = findViewById(R.id.btnCheckModelUpdate);
        View btnSyncTrainingData = findViewById(R.id.btnSyncTrainingData);

        btnCheckModelUpdate.setOnClickListener(v -> checkForModelUpdate());

        btnSyncTrainingData.setOnClickListener(v -> syncTrainingData());

        // Help & Support section
        View btnHowToUse = findViewById(R.id.btnHowToUse);
        View btnTermsConditions = findViewById(R.id.btnTermsConditions);
        View btnPrivacyPolicy = findViewById(R.id.btnPrivacyPolicy);
        View btnQA = findViewById(R.id.btnQA);

        btnHowToUse.setOnClickListener(v -> showHowToUseDialog());

        btnTermsConditions.setOnClickListener(v -> showTermsConditionsDialog());

        btnPrivacyPolicy.setOnClickListener(v -> showPrivacyPolicyDialog());

        btnQA.setOnClickListener(v -> showQADialog());
    }

    private void showHowToUseDialog() {
        String content = "<b>Getting Started with Pancreatic Analysis</b><br/><br/>" +
                "<b>1. Start New Analysis</b><br/>" +
                "• Tap the 'Start New Analysis' button on the dashboard<br/>" +
                "• Enter Patient ID and Patient Name<br/>" +
                "• Select a CT scan image from your device or camera<br/>" +
                "• Tap 'Analyze Image' to process<br/><br/>" +
                
                "<b>2. View Results</b><br/>" +
                "• The AI will analyze the scan and display results<br/>" +
                "• Review the confidence score and prediction<br/>" +
                "• Check the highlighted areas on the scan<br/>" +
                "• Share or download the report if needed<br/><br/>" +
                
                "<b>3. Patient History</b><br/>" +
                "• Access all previous scans from the History tab<br/>" +
                "• Search by Patient ID or Name<br/>" +
                "• Tap any record to view detailed results<br/>" +
                "• Swipe left to delete a record<br/><br/>" +
                
                "<b>4. Settings</b><br/>" +
                "• View your profile information<br/>" +
                "• Check model specifications<br/>" +
                "• Manage your account and data<br/>" +
                "• Access help and support resources";

        new AlertDialog.Builder(this)
                .setTitle("How to Use App")
                .setMessage(Html.fromHtml(content, Html.FROM_HTML_MODE_LEGACY))
                .setPositiveButton("Got It", null)
                .setIcon(R.drawable.ic_book)
                .show();
    }

    private void showTermsConditionsDialog() {
        String content = "<b>Terms and Conditions</b><br/><br/>" +
                "<b>1. Acceptance of Terms</b><br/>" +
                "By using this application, you agree to be bound by these Terms and Conditions. If you do not agree, please do not use this app.<br/><br/>" +
                
                "<b>2. Medical Disclaimer</b><br/>" +
                "• This app is a diagnostic aid tool only<br/>" +
                "• Results should NOT be used as the sole basis for medical decisions<br/>" +
                "• Always consult qualified healthcare professionals<br/>" +
                "• The AI model provides predictions with confidence scores, not definitive diagnoses<br/>" +
                "• We are not liable for any medical decisions made based on app results<br/><br/>" +
                
                "<b>3. User Responsibilities</b><br/>" +
                "• Provide accurate patient information<br/>" +
                "• Use high-quality CT scan images<br/>" +
                "• Maintain confidentiality of patient data<br/>" +
                "• Comply with applicable medical regulations<br/>" +
                "• Do not share your account credentials<br/><br/>" +
                
                "<b>4. Data Accuracy</b><br/>" +
                "• We strive for accuracy but cannot guarantee 100% correctness<br/>" +
                "• AI predictions may have false positives or false negatives<br/>" +
                "• Image quality affects analysis accuracy<br/>" +
                "• Regular model updates may change prediction behavior<br/><br/>" +
                
                "<b>5. Service Availability</b><br/>" +
                "• We aim for 99% uptime but cannot guarantee uninterrupted service<br/>" +
                "• Maintenance windows may cause temporary unavailability<br/>" +
                "• Local analysis works offline; syncing requires internet<br/><br/>" +
                
                "<b>6. Prohibited Uses</b><br/>" +
                "• Do not use for unauthorized medical practice<br/>" +
                "• Do not attempt to reverse engineer the AI model<br/>" +
                "• Do not upload non-medical or inappropriate images<br/>" +
                "• Do not share patient data without proper consent<br/><br/>" +
                
                "<b>7. Limitation of Liability</b><br/>" +
                "We are not liable for any damages arising from app use, including but not limited to medical errors, data loss, or service interruptions.<br/><br/>" +
                
                "<b>8. Changes to Terms</b><br/>" +
                "We reserve the right to modify these terms at any time. Continued use constitutes acceptance of updated terms.<br/><br/>" +
                
                "<b>9. Governing Law</b><br/>" +
                "These terms are governed by applicable medical device and data protection regulations in your jurisdiction.";

        new AlertDialog.Builder(this)
                .setTitle("Terms and Conditions")
                .setMessage(Html.fromHtml(content, Html.FROM_HTML_MODE_LEGACY))
                .setPositiveButton("I Agree", null)
                .setIcon(R.drawable.ic_terms)
                .show();
    }

    private void showPrivacyPolicyDialog() {
        String content = "<b>Privacy Policy</b><br/><br/>" +
                "<b>Data Collection</b><br/>" +
                "We collect and process the following information:<br/>" +
                "• User account details (name, email, phone)<br/>" +
                "• Patient information (ID, name)<br/>" +
                "• Medical images (CT scans)<br/>" +
                "• Analysis results and timestamps<br/><br/>" +
                
                "<b>Data Usage</b><br/>" +
                "Your data is used exclusively for:<br/>" +
                "• Performing AI-powered pancreatic analysis<br/>" +
                "• Storing and retrieving patient records<br/>" +
                "• Improving our diagnostic algorithms<br/>" +
                "• Providing you with analysis history<br/><br/>" +
                
                "<b>Data Security</b><br/>" +
                "• All data is encrypted during transmission<br/>" +
                "• Secure storage with industry-standard encryption<br/>" +
                "• Access restricted to authorized users only<br/>" +
                "• Regular security audits and updates<br/><br/>" +
                
                "<b>Data Retention</b><br/>" +
                "• Records are stored until you delete them<br/>" +
                "• You can delete individual records or all data<br/>" +
                "• Account deletion removes all associated data<br/><br/>" +
                
                "<b>Your Rights</b><br/>" +
                "• Access your data at any time<br/>" +
                "• Request data deletion<br/>" +
                "• Export your records<br/>" +
                "• Opt-out of data collection (requires account deletion)";

        new AlertDialog.Builder(this)
                .setTitle("Privacy Policy")
                .setMessage(Html.fromHtml(content, Html.FROM_HTML_MODE_LEGACY))
                .setPositiveButton("I Understand", null)
                .setIcon(R.drawable.ic_lock)
                .show();
    }

    private void showQADialog() {
        String content = "<b>Frequently Asked Questions</b><br/><br/>" +
                "<b>Q: What does this app do?</b><br/>" +
                "A: This app uses AI (YOLOv8) to analyze pancreatic CT scans and detect abnormalities such as pancreatitis or edema.<br/><br/>" +
                
                "<b>Q: How accurate is the AI?</b><br/>" +
                "A: The model has been trained on medical imaging data and provides confidence scores with each prediction. However, results should always be verified by qualified medical professionals.<br/><br/>" +
                
                "<b>Q: What image formats are supported?</b><br/>" +
                "A: The app accepts standard image formats (JPEG, PNG) from your camera or gallery. Images are automatically resized to 640×640 for analysis.<br/><br/>" +
                
                "<b>Q: Can I use this for diagnosis?</b><br/>" +
                "A: This app is a diagnostic aid tool and should NOT be used as the sole basis for medical decisions. Always consult with healthcare professionals.<br/><br/>" +
                
                "<b>Q: Is my data secure?</b><br/>" +
                "A: Yes, all data is encrypted and stored securely. You have full control over your data and can delete it at any time.<br/><br/>" +
                
                "<b>Q: Can I work offline?</b><br/>" +
                "A: Yes! Analysis is performed locally on your device. However, syncing records requires an internet connection.<br/><br/>" +
                
                "<b>Q: How do I delete my account?</b><br/>" +
                "A: Go to Settings → Delete Account. This will permanently remove all your data from our servers.<br/><br/>" +
                
                "<b>Q: What are the model parameters?</b><br/>" +
                "A: Input Size: 640×640, Confidence Threshold: 0.25, IOU Threshold: 0.45, Classes: ABNORMAL and normal.";

        new AlertDialog.Builder(this)
                .setTitle("Questions & Answers")
                .setMessage(Html.fromHtml(content, Html.FROM_HTML_MODE_LEGACY))
                .setPositiveButton("Close", null)
                .setIcon(R.drawable.ic_description)
                .show();
    }

    private void applyPressAnimation(View view) {
        android.view.animation.Animation anim = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.button_press);
        if (anim != null) {
            view.startAnimation(anim);
        }
    }
}
