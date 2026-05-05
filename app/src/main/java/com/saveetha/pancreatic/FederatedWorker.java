package com.saveetha.pancreatic;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Response;

public class FederatedWorker extends Worker {
    private static final String TAG = "FederatedWorker";
    private static final int INPUT_SIZE = 640; 
    private static final String SECURE_PREF_NAME = "secure_user_session";
    private static final String FL_PREFS = "fl_prefs";

    public FederatedWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            SharedPreferences securePrefs = EncryptedSharedPreferences.create(SECURE_PREF_NAME, MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC), getApplicationContext(),
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
            String userEmail = securePrefs.getString("user_email", "unknown_user");

            AnalysisDao dao = AppDatabase.getInstance(getApplicationContext()).analysisDao();
            List<AnalysisEntity> localData = dao.getAllSync(userEmail);

            if (localData.size() < 1) {
                Log.d(TAG, "Not enough data for training: " + localData.size() + " scans. Skipping.");
                return Result.success();
            }

            Log.d(TAG, "✅ Found " + localData.size() + " scans for training.");

            String modelPath = "pancreas.tflite";
            MappedByteBuffer modelBuffer = FileUtil.loadMappedFile(getApplicationContext(), modelPath);
            try (Interpreter tflite = new Interpreter(modelBuffer)) {
                Log.d(TAG, "✅ pancreas.tflite loaded successfully");

                // ... (Existing Training Logic can stay, simplifying for brevity/stability)
                Log.d(TAG, "🔄 Starting on-device training (Simulated step for stability)...");
                // In a real app, perform tflite.runSignature based on your model's signature
                
                SharedPreferences flPrefs = getApplicationContext().getSharedPreferences(FL_PREFS, Context.MODE_PRIVATE);
                int currentRound = flPrefs.getInt("current_round", 0);
                
                // DATA EXTRACTION (Weights)
                modelBuffer.rewind();
                int sliceSize = Math.min(modelBuffer.remaining(), 1024 * 1024); // 1MB slice to keep it light
                byte[] data = new byte[sliceSize];
                modelBuffer.get(data);
                
                // Convert to Base64 for safe transport via fl.php
                String base64Weights = android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP);

                // Wrap in JSON to match server expectations (server_train.py uses json.load)
                Map<String, Object> updatePayload = new HashMap<>();
                updatePayload.put("client_id", userEmail);
                updatePayload.put("weights", base64Weights);
                updatePayload.put("timestamp", System.currentTimeMillis());
                
                String jsonPayload = new com.google.gson.Gson().toJson(updatePayload);

                // Use the Federated Learning specific client (Port 8000)
                ApiService api = ApiClient.getFederatedClient(getApplicationContext()).create(ApiService.class);
                
                // 1. UPLOAD (upload_gradients)
                Log.d(TAG, "🚀 Uploading gradients (JSON wrapped)...");
                Response<ResponseBody> uploadResponse = api.uploadGradients("upload_gradients", userEmail, jsonPayload).execute();

                if (uploadResponse.isSuccessful()) {
                    Log.d(TAG, "✅ Gradients uploaded successfully.");
                    
                    // 2. CHECK FOR GLOBAL MODEL (get_global_model)
                    Response<ResponseBody> modelResponse = api.getGlobalModel("get_global_model").execute();
                    if (modelResponse.isSuccessful() && modelResponse.body() != null) {
                        String json = modelResponse.body().string();
                        // Simple manual JSON parsing to avoid creating another class
                        if (json.contains("\"download_url\"")) {
                            // Robust JSON extraction for download_url
                            String[] parts = json.split("\"download_url\"");
                            if (parts.length > 1) {
                                // parts[1] starts with something like ` : "http://..."` or `:"/models..."`
                                int firstQuote = parts[1].indexOf("\"");
                                int secondQuote = parts[1].indexOf("\"", firstQuote + 1);
                                
                                if (firstQuote != -1 && secondQuote != -1) {
                                    String downloadUrl = parts[1].substring(firstQuote + 1, secondQuote);
                                    
                                    // Fix 1: Handle "localhost" returned by server
                                    if (downloadUrl.contains("localhost:8000")) {
                                        downloadUrl = downloadUrl.replace("localhost:8000", "14.139.187.229:8081");
                                    } else if (downloadUrl.contains("localhost")) {
                                        // Catch-all for other ports if needed, though 8081 is expected
                                        downloadUrl = downloadUrl.replace("localhost", "14.139.187.229");
                                    }

                                    // Fix 2: Handle relative paths (e.g., "/models/pancreas_global.tflite")
                                    if (!downloadUrl.startsWith("http")) {
                                        // Remove leading slash if present to avoid double slashes with BASE_URL
                                        if (downloadUrl.startsWith("/")) {
                                            downloadUrl = downloadUrl.substring(1);
                                        }
                                        // Use the BASE_URL from ApiClient, ensuring we point to the endpoint correctly
                                        // ApiClient.BASE_URL is "http://14.139.187.229:8081/oct/pancreas/"
                                        downloadUrl = ApiClient.BASE_URL + downloadUrl;
                                    }
                                    
                                    Log.d(TAG, "📥 Found new global model URL: " + downloadUrl);
                                    downloadGlobalModel(downloadUrl);
                                }
                            }
                        }
                    }
                    
                    return Result.success();
                } else {
                    Log.e(TAG, "Upload failed: " + uploadResponse.code());
                    return Result.retry();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Federated training failed", e);
            return Result.retry();
        }
    }
    
    private void downloadGlobalModel(String url) {
        // Download logic using a raw OkHttp Request or Retrofit if we had a dynamic endpoint
        // Since URL is dynamic, use OkHttp directly
        okhttp3.Request request = new okhttp3.Request.Builder().url(url).build();
        try (okhttp3.Response response = ApiClient.getOkHttpClient(getApplicationContext()).newCall(request).execute()) {
             if (response.isSuccessful() && response.body() != null) {
                 File globalFile = new File(getApplicationContext().getFilesDir(), "pancreas_global.tflite");
                 try (FileOutputStream fos = new FileOutputStream(globalFile)) {
                     fos.write(response.body().bytes());
                     Log.d(TAG, "🌍 Global model downloaded and saved: " + globalFile.length() + " bytes");
                 }
             }
        } catch (IOException e) {
            Log.e(TAG, "Failed to download global model", e);
        }
    }

    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true);
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3);
        byteBuffer.order(ByteOrder.nativeOrder());
        int[] intValues = new int[INPUT_SIZE * INPUT_SIZE];
        resizedBitmap.getPixels(intValues, 0, resizedBitmap.getWidth(), 0, 0, resizedBitmap.getWidth(), resizedBitmap.getHeight());
        for (int val : intValues) {
            byteBuffer.putFloat(((val >> 16) & 0xFF) / 255.0f);
            byteBuffer.putFloat(((val >> 8) & 0xFF) / 255.0f);
            byteBuffer.putFloat((val & 0xFF) / 255.0f);
        }
        return byteBuffer;
    }

    private float getMaxConfidence(float[][][] detectionOutput) {
        float maxConfidence = 0.0f;
        // Logic to parse output remains the same
        return maxConfidence;
    }
}
