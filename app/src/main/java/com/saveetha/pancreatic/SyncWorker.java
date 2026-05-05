package com.saveetha.pancreatic;


import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import okhttp3.ResponseBody;
import retrofit2.Response;

public class SyncWorker extends Worker {

    private static final String TAG = "SyncWorker";
    private static final String SECURE_PREF_NAME = "secure_user_session";

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        String masterKeyAlias;
        SharedPreferences securePrefs;
        try {
            masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            securePrefs = EncryptedSharedPreferences.create(
                    SECURE_PREF_NAME, masterKeyAlias, getApplicationContext(),
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "💥 CRITICAL ERROR in SyncWorker setup", e);
            return Result.failure();
        }

        String emailRaw = securePrefs.getString("user_email", null);
        if (emailRaw == null) {
            Log.e(TAG, "❌ Cannot sync: User email not found in prefs.");
            return Result.failure();
        }
        String userEmail = emailRaw.trim().toLowerCase();

        AnalysisDao dao = AppDatabase.getInstance(getApplicationContext()).analysisDao();
        List<AnalysisEntity> pendingAnalyses = dao.getPendingSync(userEmail);
        boolean allSucceeded = true;

        // 1. UPLOAD PENDING RECORDS
        if (!pendingAnalyses.isEmpty()) {
            Log.d(TAG, "📋 Found " + pendingAnalyses.size() + " records to sync for " + userEmail);
            ApiService api = ApiClient.getClient(getApplicationContext()).create(ApiService.class);

            for (AnalysisEntity entity : pendingAnalyses) {
                try {
                    Log.i(TAG, "🔄 UPLOADING patientId=" + entity.patientId);
                    
                    String base64Image = null;
                    if (entity.imagePath != null && !entity.imagePath.trim().isEmpty()) {
                        File file = new File(entity.imagePath);
                        if (file.exists()) {
                            base64Image = encodeImageToBase64(file);
                        }
                    }

                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
                    String formattedDate = sdf.format(new Date(entity.lastModified));

                    Response<ResponseBody> response = api.syncScan(
                        "upload_scan",
                        userEmail,
                        base64Image,
                        entity.resultLabel,
                        entity.confidence,
                        entity.patientId,
                        entity.patientName,
                        formattedDate,
                        entity.boxLeft,
                        entity.boxTop,
                        entity.boxRight,
                        entity.boxBottom
                    ).execute();

                    if (response.isSuccessful() && response.body() != null) {
                        String rawResponse = response.body().string();
                        if (rawResponse.contains("\"status\":\"success\"") || rawResponse.contains("\"success\":true")) {
                            entity.isSynced = true;
                            entity.serverTimestamp = formattedDate;
                            dao.update(entity);
                        } else {
                            allSucceeded = false;
                        }
                    } else {
                        allSucceeded = false;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "💥 Upload exception", e);
                    allSucceeded = false;
                }
            }
        }

        // 2. FETCH HISTORY FROM SERVER
        try {
            Log.d(TAG, "📥 FETCHING history from server for " + userEmail);
            ApiService api = ApiClient.getClient(getApplicationContext()).create(ApiService.class);
            Response<ResponseBody> historyResponse = api.getHistory("get_history", userEmail).execute();
            if (historyResponse.isSuccessful() && historyResponse.body() != null) {
                String rawJson = historyResponse.body().string();
                com.google.gson.Gson gson = new com.google.gson.Gson();
                SyncHistoryResponse historyData = gson.fromJson(rawJson, SyncHistoryResponse.class);

                if (historyData != null && historyData.isSuccess() && historyData.history != null) {
                    processHistoryFromServer(userEmail, historyData.history, dao);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "💥 History fetch exception", e);
            allSucceeded = false;
        }

        return allSucceeded ? Result.success() : Result.retry();
    }

    private void processHistoryFromServer(String userEmail, List<PatientItem> history, AnalysisDao dao) {
        DeletedRecordDao deletedDao = AppDatabase.getInstance(getApplicationContext()).deletedRecordDao();
        List<String> locallyDeletedIds = deletedDao.getAllDeletedPatientIds();

        for (PatientItem item : history) {
            if (locallyDeletedIds.contains(item.patient_id)) continue;

            // Check if duplicate exists (same patient, same timestamp)
            AnalysisEntity duplicate = dao.getDuplicateFromServer(item.patient_id, item.created_at, userEmail);
            
            if (duplicate != null) {
                // UPDATE METADATA (Metadata might have changed or was missing like boxes)
                duplicate.resultLabel = item.result;
                duplicate.confidence = item.confidence;
                duplicate.boxLeft = item.boxLeft;
                duplicate.boxTop = item.boxTop;
                duplicate.boxRight = item.boxRight;
                duplicate.boxBottom = item.boxBottom;
                dao.update(duplicate);

                // If image is missing, attempt download
                if ((duplicate.imagePath == null || !(new File(duplicate.imagePath).exists())) && item.ct_image_path != null) {
                    downloadImageForRecord(item, duplicate, dao);
                }
            }
 else {
                // New record from server
                AnalysisEntity entity = new AnalysisEntity();
                entity.patientId = item.patient_id;
                entity.userEmail = userEmail;
                entity.patientName = item.name;
                entity.resultLabel = item.result;
                entity.confidence = item.confidence;
                entity.isSynced = true;
                entity.serverTimestamp = item.created_at;
                entity.boxLeft = item.boxLeft;
                entity.boxTop = item.boxTop;
                entity.boxRight = item.boxRight;
                entity.boxBottom = item.boxBottom;

                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
                    entity.lastModified = sdf.parse(item.created_at).getTime();
                } catch (Exception e) {
                    entity.lastModified = System.currentTimeMillis();
                }

                long id = dao.insert(entity);
                entity.id = id;
                
                if (item.ct_image_path != null) {
                    downloadImageForRecord(item, entity, dao);
                }
            }
        }
    }

    private void downloadImageForRecord(PatientItem item, AnalysisEntity entity, AnalysisDao dao) {
        String url = item.ct_image_path;
        if (url == null || url.trim().isEmpty()) return;
        
        if (!url.startsWith("http")) {
            url = ApiClient.BASE_URL + (url.startsWith("/") ? url.substring(1) : url);
        }

        try {
            okhttp3.Request request = new okhttp3.Request.Builder().url(url).build();
            okhttp3.Response response = ApiClient.getOkHttpClient(getApplicationContext()).newCall(request).execute();
            if (response.isSuccessful() && response.body() != null) {
                byte[] bytes = response.body().bytes();
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (bitmap != null) {
                    String fileName = "t_" + System.currentTimeMillis() + "_" + java.util.UUID.randomUUID().toString() + ".jpg";
                    File file = new File(getApplicationContext().getFilesDir(), fileName);
                    try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
                        entity.imagePath = file.getAbsolutePath();
                        dao.update(entity);
                        Log.d(TAG, "✅ Image downloaded for " + entity.patientId);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Image download failed for " + item.patient_id, e);
        }
    }


    private String encodeImageToBase64(File imageFile) {
        try {
            Bitmap bm = BitmapFactory.decodeFile(imageFile.getAbsolutePath());
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bm.compress(Bitmap.CompressFormat.JPEG, 80, baos); // Compress to avoid huge payload
            byte[] b = baos.toByteArray();
            return Base64.encodeToString(b, Base64.DEFAULT);
        } catch (Exception e) {
            Log.e(TAG, "Error encoding image", e);
            return null;
        }
    }
}
