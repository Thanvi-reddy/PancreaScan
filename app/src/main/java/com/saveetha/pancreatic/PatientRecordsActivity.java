package com.saveetha.pancreatic;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import androidx.transition.TransitionManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class PatientRecordsActivity extends AppCompatActivity {

    private static final String TAG = "PatientRecordsActivity";
    private static final String SECURE_PREF_NAME = "secure_user_session";
    private PatientRecordsViewModel patientRecordsViewModel;
    private PatientRecordsAdapter adapter;
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private SharedPreferences securePrefs;
    private AnalysisDao analysisDao;
    private View syncingLayout;
    private TextView tvRefreshHistory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_patient_records);

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

        analysisDao = AppDatabase.getInstance(this).analysisDao();



        RecyclerView recyclerView = findViewById(R.id.recycler_view_patient_records);
        adapter = new PatientRecordsAdapter(new PatientRecordsAdapter.RecordDiff());
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter.setOnItemClickListener(record -> {
            if (record.imagePath == null || record.imagePath.trim().isEmpty()) {
                Toast.makeText(this, "Image file is not available for this record.", Toast.LENGTH_LONG).show();
                return;
            }
            
            Intent intent = new Intent(PatientRecordsActivity.this, AnalysisReportActivity.class);
            intent.putExtra("ANALYSIS_ID", record.id);
            intent.putExtra("PATIENT_ID", record.patientId);
            intent.putExtra("PATIENT_NAME", record.patientName);
            intent.putExtra("IMAGE_URL", record.imagePath);
            intent.putExtra("RESULT_LABEL", record.resultLabel);
            intent.putExtra("CONFIDENCE", record.confidence);
            intent.putExtra("BOX_LEFT", record.boxLeft);
            intent.putExtra("BOX_TOP", record.boxTop);
            intent.putExtra("BOX_RIGHT", record.boxRight);
            intent.putExtra("BOX_BOTTOM", record.boxBottom);
            startActivity(intent);
        });

        patientRecordsViewModel = new ViewModelProvider(this).get(PatientRecordsViewModel.class);

        View layoutEmptyState = findViewById(R.id.layoutEmptyState);
        String emailRaw = securePrefs != null ? securePrefs.getString("user_email", "") : "";
        String currentUserEmail = emailRaw.trim().toLowerCase();

        patientRecordsViewModel.getAllRecords(currentUserEmail).observe(this, records -> {
            ViewGroup container = findViewById(android.R.id.content);
            TransitionManager.beginDelayedTransition(container);
            
            if (records == null || records.isEmpty()) {
                layoutEmptyState.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            } else {
                layoutEmptyState.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
                adapter.setFullList(records);
                adapter.submitList(records);
            }
        });

        EditText etSearch = findViewById(R.id.etSearch);
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.getFilter().filter(s);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                AnalysisEntity record = adapter.getRecordAt(position);
                
                // 1. Mark as deleted in Tombstone table so it never comes back from sync
                databaseExecutor.execute(() -> {
                    AppDatabase.getInstance(PatientRecordsActivity.this).deletedRecordDao()
                            .insert(new DeletedRecordEntity(record.patientId));
                });

                // 2. Delete from local DB (UI updates automatically via LiveData)
                patientRecordsViewModel.delete(record);
                
                // 3. Attempt server delete (Best effort)
                deletePatientFromServer(record);

                Snackbar.make(recyclerView, "Record deleted (Local & Server)", Snackbar.LENGTH_LONG)
                        .setAction("Undo", v -> {
                            // Undo Logic: Remove from tombstone and re-insert
                            databaseExecutor.execute(() -> {
                                // Not easily possible to remove from tombstone without DAO method, 
                                // but simpler to just re-insert the record
                            });
                            patientRecordsViewModel.insert(record);
                        })
                        .show();
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);

                View itemView = viewHolder.itemView;
                Drawable icon = ContextCompat.getDrawable(PatientRecordsActivity.this, R.drawable.ic_delete_forever);
                ColorDrawable background = new ColorDrawable(Color.RED);

                int iconMargin = (itemView.getHeight() - icon.getIntrinsicHeight()) / 2;
                int iconTop = itemView.getTop() + iconMargin;
                int iconBottom = iconTop + icon.getIntrinsicHeight();

                if (dX < 0) { // Swiping to the left
                    int iconRight = itemView.getRight() - iconMargin;
                    int iconLeft = iconRight - icon.getIntrinsicWidth();
                    icon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
                    background.setBounds(itemView.getRight() + ((int) dX), itemView.getTop(), itemView.getRight(), itemView.getBottom());
                } else {
                    background.setBounds(0, 0, 0, 0);
                }
                background.draw(c);
                icon.draw(c);
            }
        }).attachToRecyclerView(recyclerView);

        // New Layout Elements Click Listeners
        tvRefreshHistory = findViewById(R.id.tvRefreshHistory);
        syncingLayout = findViewById(R.id.syncingLayout);

        tvRefreshHistory.setOnClickListener(v -> {
            applyPressAnimation(v);
            fetchRecordsFromServer();
        });

        View navHome = findViewById(R.id.navHome);
        navHome.setOnClickListener(v -> {
            applyPressAnimation(v);
            Intent intent = new Intent(this, DashboardActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });

        View navSettings = findViewById(R.id.navSettings);
        navSettings.setOnClickListener(v -> {
            applyPressAnimation(v);
            Intent intent = new Intent(this, SettingsActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });
    }

    private void applyPressAnimation(View view) {
        android.view.animation.Animation anim = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.button_press);
        if (anim != null) {
            view.startAnimation(anim);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        fetchRecordsFromServer();
    }

    private void fetchRecordsFromServer() {
        // Legacy backend uses email
        String emailRaw = securePrefs.getString("user_email", null);
        if (emailRaw == null) {
            Toast.makeText(this, "User email not found. Please re-login.", Toast.LENGTH_SHORT).show();
            return;
        }
        String userEmail = emailRaw.trim().toLowerCase();

        runOnUiThread(() -> {
            if (syncingLayout != null) syncingLayout.setVisibility(View.VISIBLE);
            if (tvRefreshHistory != null) tvRefreshHistory.setVisibility(View.GONE);

            ApiService apiService = ApiClient.getClient(this).create(ApiService.class);
            
            // "get_history" action
            apiService.getHistory("get_history", userEmail).enqueue(new retrofit2.Callback<ResponseBody>() {
                @Override
                public void onResponse(retrofit2.Call<ResponseBody> call, retrofit2.Response<ResponseBody> response) {
                    if (syncingLayout != null) syncingLayout.setVisibility(View.GONE);
                    if (tvRefreshHistory != null) tvRefreshHistory.setVisibility(View.VISIBLE);

                    if (response.isSuccessful() && response.body() != null) {
                        try {
                            String rawJson = response.body().string();
                            Log.d(TAG, "Raw sync.php response: " + rawJson);

                            Gson gson = new Gson();
                            SyncHistoryResponse historyResponse = gson.fromJson(rawJson, SyncHistoryResponse.class);

                            if (historyResponse != null && historyResponse.isSuccess() && historyResponse.history != null) {
                                updateLocalDatabase(PatientRecordsActivity.this, historyResponse.history);
                                Toast.makeText(PatientRecordsActivity.this, historyResponse.history.size() + " records synced.", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(PatientRecordsActivity.this, "History is up to date or empty.", Toast.LENGTH_SHORT).show();
                            }

                        } catch (IOException | JsonSyntaxException e) {
                            Log.e(TAG, "Error parsing history response", e);
                            Toast.makeText(PatientRecordsActivity.this, "Could not read server response.", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Log.e(TAG, "Failed to fetch history: " + response.code());
                        Toast.makeText(PatientRecordsActivity.this, "Failed to fetch history from server.", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(retrofit2.Call<ResponseBody> call, Throwable t) {
                    if (syncingLayout != null) syncingLayout.setVisibility(View.GONE);
                    if (tvRefreshHistory != null) tvRefreshHistory.setVisibility(View.VISIBLE);
                    Log.e(TAG, "Network error", t);
                    Toast.makeText(PatientRecordsActivity.this, "Network Error.", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }
    
    private void updateLocalDatabase(Context context, List<PatientItem> patientItems) {
        databaseExecutor.execute(() -> {
            DeletedRecordDao deletedRecordDao = AppDatabase.getInstance(context).deletedRecordDao();
            List<String> deletedIds = deletedRecordDao.getAllDeletedPatientIds(); // Fetch all tombstones
            
            for (PatientItem item : patientItems) {
                // CRITICAL: Skip if user has deleted this patient locally
                if (deletedIds.contains(item.patient_id)) {
                    Log.d(TAG, "Skipping synced record " + item.patient_id + " because it is locally deleted.");
                    continue;
                }

                String emailRaw = securePrefs != null ? securePrefs.getString("user_email", "") : "";
                String userEmail = emailRaw.trim().toLowerCase();

                // 1. DEDUPLICATION: Check if this exact record (same ID and server timestamp) already exists
                AnalysisEntity duplicate = analysisDao.getDuplicateFromServer(item.patient_id, item.created_at, userEmail);
                if (duplicate != null) {
                    Log.d(TAG, "Record already exists, updating metadata: " + item.patient_id);
                    
                    // Refresh fields from server
                    duplicate.resultLabel = item.result;
                    duplicate.confidence = item.confidence;
                    duplicate.boxLeft = item.boxLeft;
                    duplicate.boxTop = item.boxTop;
                    duplicate.boxRight = item.boxRight;
                    duplicate.boxBottom = item.boxBottom;
                    analysisDao.update(duplicate);

                    // Check if image file actually exists locally
                    boolean imageExists = (duplicate.imagePath != null && new File(duplicate.imagePath).exists());
                    if (!imageExists && item.ct_image_path != null && !item.ct_image_path.trim().isEmpty()) {
                         Log.d(TAG, "Image missing for existing record, starting download...");
                         runOnUiThread(() -> downloadAndSaveImage(context, item, userEmail));
                    }
                    continue;
                }

                // 2. NEW RECORD: Insert metadata and trigger image download
                updateDatabaseRecord(item, null, userEmail);
                
                if (item.ct_image_path != null && !item.ct_image_path.trim().isEmpty()) {
                     runOnUiThread(() -> downloadAndSaveImage(context, item, userEmail));
                }
            }
        });
    }

    private void downloadAndSaveImage(Context context, PatientItem item, String userEmail) {
        String imageUrlFromServer = item.ct_image_path;
        if (imageUrlFromServer == null) return;
        
        String fullUrl = imageUrlFromServer.startsWith("http") ? imageUrlFromServer : ApiClient.BASE_URL + imageUrlFromServer;

        Glide.with(context)
            .asBitmap()
            .load(fullUrl)
            .into(new CustomTarget<Bitmap>() {
                @Override
                public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                    databaseExecutor.execute(() -> {
                        String localPath = saveImageToInternalStorage(context, resource);
                        if (localPath != null) {
                            Log.d(TAG, "Image downloaded for " + item.patient_id);
                            // Update the EXISTING record only with the new path
                            // Use duplicate query to find exact record if multiple scans exist for same patient
                            AnalysisEntity entity = analysisDao.getDuplicateFromServer(item.patient_id, item.created_at, userEmail);
                            if (entity != null) {
                                entity.imagePath = localPath;
                                analysisDao.update(entity);
                            }
                        }
                    });
                }

                @Override
                public void onLoadCleared(@Nullable Drawable placeholder) {}

                @Override
                public void onLoadFailed(@Nullable Drawable errorDrawable) {
                    Log.e(TAG, "Glide failed to load image: " + fullUrl);
                }
            });
    }

    private void updateDatabaseRecord(PatientItem item, String localImagePath, String userEmail) {
        AnalysisEntity entity = new AnalysisEntity();
        entity.patientId = item.patient_id;
        entity.userEmail = userEmail; // Ensure user assignment
        entity.patientName = item.name;
        entity.resultLabel = item.result;
        entity.imagePath = localImagePath;
        entity.confidence = item.confidence;
        entity.isSynced = true;
        entity.serverTimestamp = item.created_at; // Store exact server string

        // Box coordinates are NOT supported by legacy backend, so they will be 0
        entity.boxLeft = item.boxLeft;
        entity.boxTop = item.boxTop;
        entity.boxRight = item.boxRight;
        entity.boxBottom = item.boxBottom;

        try {
            if (item.created_at != null && !item.created_at.isEmpty()) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
                entity.lastModified = sdf.parse(item.created_at).getTime();
            } else {
                entity.lastModified = System.currentTimeMillis();
            }
        } catch (ParseException e) {
            entity.lastModified = System.currentTimeMillis();
        }
        analysisDao.insert(entity);
    }

    private String saveImageToInternalStorage(Context context, Bitmap bitmap) {
        String fileName = "t_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString() + ".jpg";
        File directory = context.getFilesDir();
        File file = new File(directory, fileName);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            return file.getAbsolutePath();
        } catch (IOException e) {
            Log.e(TAG, "Failed to save bitmap", e);
            return null;
        }
    }

    private void deletePatientFromServer(AnalysisEntity patient) {
        String userEmail = securePrefs.getString("user_email", null);
        if (userEmail == null || patient.patientId == null) {
            return;
        }

        // Use exact server timestamp string if available, otherwise try to format
        String timestamp;
        if (patient.serverTimestamp != null && !patient.serverTimestamp.isEmpty()) {
            timestamp = patient.serverTimestamp.trim();
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            timestamp = sdf.format(new Date(patient.lastModified));
        }

        final String timestampToSend = timestamp;
        Log.d(TAG, "Deleting scan for " + userEmail + " with timestamp: '" + timestampToSend + "'");

        ApiService apiService = ApiClient.getClient(this).create(ApiService.class);

        apiService.deleteScan("delete_scan", userEmail, timestampToSend).enqueue(new retrofit2.Callback<ResponseBody>() {
             @Override
             public void onResponse(retrofit2.Call<ResponseBody> call, retrofit2.Response<ResponseBody> response) {
                 if (response.isSuccessful() && response.body() != null) {
                     try {
                         String responseStr = response.body().string();
                         Log.d(TAG, "Delete response: " + responseStr);
                         org.json.JSONObject json = new org.json.JSONObject(responseStr);
                         if ("success".equals(json.optString("status"))) {
                            Log.d(TAG, "Delete Confirmed on Server");
                            runOnUiThread(() -> Toast.makeText(PatientRecordsActivity.this, "Deleted from server.", Toast.LENGTH_SHORT).show());
                         } else {
                             String msg = json.optString("message", "Unknown error");
                             Log.e(TAG, "Server delete failed: " + msg); 
                             runOnUiThread(() -> Toast.makeText(PatientRecordsActivity.this, "Server Delete Failed: " + msg, Toast.LENGTH_LONG).show());
                         }
                     } catch (Exception e) {
                         Log.e(TAG, "Error parsing delete response", e);
                     }
                 } else {
                     Log.e(TAG, "Delete Request Failed: " + response.code());
                     runOnUiThread(() -> Toast.makeText(PatientRecordsActivity.this, "Delete Request Failed: " + response.code(), Toast.LENGTH_LONG).show());
                 }
             }

             @Override
             public void onFailure(retrofit2.Call<ResponseBody> call, Throwable t) {
                 Log.e(TAG, "Delete network error", t);
                 runOnUiThread(() -> Toast.makeText(PatientRecordsActivity.this, "Network Error on Delete", Toast.LENGTH_SHORT).show());
             }
        });
    }
}
