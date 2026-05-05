package com.saveetha.pancreatic;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.saveetha.pancreatic.PancreasDetector.DetectionResult;
import com.saveetha.pancreatic.PancreasDetector.Recognition;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class NewAnalysisActivity extends AppCompatActivity {

    private static final String TAG = "NewAnalysisActivity";
    private static final int PICK_IMAGE_REQUEST = 1;
    private static final int CAMERA_REQUEST_CODE = 2;
    private static final int PERMISSION_REQUEST_CODE = 3;
    private static final String SECURE_PREF_NAME = "secure_user_session";

    private EditText etPatientID, etPatientName;
    private ImageView ivImagePlaceholder;
    private TextView tvNoImageSelected;
    private View btnSelectImage;
    private Button btnAnalyze;
    private View loadingOverlay;
    private Bitmap imageBitmap;
    private PancreasDetector detector;
    private AnalysisDao analysisDao;
    private SharedPreferences securePrefs;
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();

    private int currentUserId;
    private String currentPatientId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_analysis);

        try {
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            securePrefs = EncryptedSharedPreferences.create(
                    SECURE_PREF_NAME, masterKeyAlias, this,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "Could not create EncryptedSharedPreferences", e);
            Toast.makeText(this, "Critical security error.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        etPatientID = findViewById(R.id.etPatientID);
        etPatientName = findViewById(R.id.etPatientName);
        ivImagePlaceholder = findViewById(R.id.ivImagePlaceholder);
        tvNoImageSelected = findViewById(R.id.tvNoImageSelected);
        btnSelectImage = findViewById(R.id.btnSelectImage);
        btnAnalyze = findViewById(R.id.btnAnalyze);
        loadingOverlay = findViewById(R.id.loadingOverlay);
        
        View btnCancel = findViewById(R.id.btnCancel);
        btnCancel.setOnClickListener(v -> finish());
        currentPatientId = getIntent().getStringExtra("PATIENT_ID");
        String patientNameFromIntent = getIntent().getStringExtra("PATIENT_NAME");
        currentUserId = securePrefs.getInt("user_id", 0);

        if (currentPatientId != null) {
            etPatientID.setText(currentPatientId);
            if (patientNameFromIntent != null) {
                etPatientName.setText(patientNameFromIntent);
            }
            etPatientID.setEnabled(false);
        }

        analysisDao = AppDatabase.getInstance(getApplicationContext()).analysisDao();

        try {
            detector = new PancreasDetector(this);
        } catch (IOException e) {
            Toast.makeText(this, "Failed to load model.", Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }

        btnSelectImage.setOnClickListener(v -> {
            applyPressAnimation(v);
            showImagePickerOptions();
        });

        btnAnalyze.setOnClickListener(v -> {
            applyPressAnimation(v);
            String patientID = etPatientID.getText().toString().trim();
            String patientName = etPatientName.getText().toString().trim();

            if (!validateInputs(patientID, patientName)) {
                return;
            }

            setAnalysisInProgress(true);

            databaseExecutor.execute(() -> {
                final DetectionResult detectionResult = detector.detect(imageBitmap);

                runOnUiThread(() -> {
                    if (detectionResult != null && detectionResult.recognition != null && detectionResult.recognition.confidence >= 0.50f) {
                        saveAnalysis(patientID, patientName, detectionResult);
                    } else {
                        Toast.makeText(this, "Image Invalid: Could not find a pancreas with sufficient confidence.", Toast.LENGTH_LONG).show();
                        setAnalysisInProgress(false);
                    }
                });
            });
        });
    }

    private void setAnalysisInProgress(boolean inProgress) {
        if(inProgress) {
            loadingOverlay.setVisibility(View.VISIBLE);
            btnAnalyze.setEnabled(false);
            
            // Start pulse animation on the loading text if it exists
            View loadingText = loadingOverlay.findViewById(R.id.tvLoadingText);
            if (loadingText != null) {
                android.view.animation.Animation pulse = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.pulse);
                loadingText.startAnimation(pulse);
            }
        } else {
            loadingOverlay.setVisibility(View.GONE);
            btnAnalyze.setEnabled(true);
            loadingOverlay.clearAnimation();
        }
    }

    private boolean validateInputs(String patientID, String patientName) {
        if (patientID.isEmpty() || patientName.isEmpty()) {
            Toast.makeText(this, "Please enter Patient ID and Name", Toast.LENGTH_SHORT).show();
            return false;
        } else if (imageBitmap == null) {
            Toast.makeText(this, "Please select an image first", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private void saveAnalysis(String patientId, String patientName, DetectionResult detectionResult) {
        Bitmap bitmapWithBoxAndText = drawDetection(imageBitmap, detectionResult.recognition);
        
        String imagePath = saveImageToInternalStorage(bitmapWithBoxAndText);
        String originalPath = saveImageToInternalStorage(imageBitmap);

        if (imagePath == null || originalPath == null) {
            Toast.makeText(this, "Failed to save images locally.", Toast.LENGTH_SHORT).show();
            setAnalysisInProgress(false);
            return;
        }

        databaseExecutor.execute(() -> {
            AnalysisEntity entity = new AnalysisEntity();
            entity.patientId = patientId;
            String emailRaw = securePrefs.getString("user_email", "");
            entity.userEmail = emailRaw.trim().toLowerCase();
            entity.patientName = patientName;
            entity.imagePath = imagePath;
            entity.originalImagePath = originalPath;
            entity.isSynced = false; 
            entity.lastModified = System.currentTimeMillis();

            if (detectionResult.recognition != null) {
                entity.resultLabel = detectionResult.recognition.label;
                entity.confidence = detectionResult.recognition.confidence;
                if(detectionResult.recognition.boundingBox != null) {
                    entity.boxLeft = detectionResult.recognition.boundingBox.left;
                    entity.boxTop = detectionResult.recognition.boundingBox.top;
                    entity.boxRight = detectionResult.recognition.boundingBox.right;
                    entity.boxBottom = detectionResult.recognition.boundingBox.bottom;
                }
            }

            long entityId = analysisDao.insert(entity);
            
            uploadToServer(entity, entityId);

            runOnUiThread(() -> {
                Toast.makeText(NewAnalysisActivity.this, "Analysis saved. Attempting to upload...", Toast.LENGTH_SHORT).show();
                navigateToReport(entity);
                setAnalysisInProgress(false);
            });
        });
    }

    private Bitmap drawDetection(Bitmap bitmap, Recognition recognition) {
        Bitmap mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(mutableBitmap);
        
        Paint boxPaint = new Paint();
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(4.0f);

        Paint textBgPaint = new Paint();
        textBgPaint.setStyle(Paint.Style.FILL);

        Paint textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(40.0f);
        textPaint.setFakeBoldText(true);

        if (recognition.label.equalsIgnoreCase("Abnormal")) {
            boxPaint.setColor(Color.RED);
            textBgPaint.setColor(Color.RED);
        } else {
            boxPaint.setColor(Color.GREEN);
            textBgPaint.setColor(Color.GREEN);
        }

        canvas.drawRect(recognition.boundingBox, boxPaint);

        String labelText = String.format(Locale.US, "%s: %.1f%%", recognition.label, recognition.confidence * 100.0f);
        
        Rect textBounds = new Rect();
        textPaint.getTextBounds(labelText, 0, labelText.length(), textBounds);
        
        float textBgLeft = recognition.boundingBox.left;
        float textBgTop = recognition.boundingBox.top - textBounds.height() - 10;
        float textBgRight = recognition.boundingBox.left + textBounds.width() + 20;
        float textBgBottom = recognition.boundingBox.top;
        
        canvas.drawRect(textBgLeft, textBgTop, textBgRight, textBgBottom, textBgPaint);
        canvas.drawText(labelText, recognition.boundingBox.left + 10, recognition.boundingBox.top - 10, textPaint);

        return mutableBitmap;
    }

    private void uploadToServer(AnalysisEntity entity, long entityId) {
        String emailRaw = securePrefs.getString("user_email", null);
        if (emailRaw == null) {
            triggerSync();
            return;
        }
        String email = emailRaw.trim().toLowerCase();

        File imageFile = new File(entity.imagePath);
        String base64Image = encodeImageToBase64(imageFile);
        if (base64Image == null) {
             Log.e(TAG, "Failed to encode image for upload.");
             triggerSync();
             return;
        }

        ApiService api = ApiClient.getClient(this).create(ApiService.class);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        String formattedDate = sdf.format(new Date(entity.lastModified));

        // Unified "upload_scan" action from sync.php
        api.syncScan(
            "upload_scan",
            email,
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
        ).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    Log.d(TAG, "✅ Scan uploaded successfully!");
                    updateSyncedStatus(entityId, formattedDate);
                } else {
                    Log.e(TAG, "Upload failed with code: " + response.code());
                    triggerSync();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                 Log.e(TAG, "Upload network error", t);
                 triggerSync();
            }
        });
    }

    private String encodeImageToBase64(File imageFile) {
        try {
            android.graphics.Bitmap bm = android.graphics.BitmapFactory.decodeFile(imageFile.getAbsolutePath());
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            bm.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, baos);
            byte[] b = baos.toByteArray();
            return android.util.Base64.encodeToString(b, android.util.Base64.DEFAULT);
        } catch (Exception e) {
            Log.e(TAG, "Error encoding image", e);
            return null;
        }
    }

    private void triggerSync() {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        OneTimeWorkRequest syncRequest = new OneTimeWorkRequest.Builder(SyncWorker.class)
                .setConstraints(constraints)
                .build();
        WorkManager.getInstance(getApplicationContext()).enqueue(syncRequest);
        Log.d(TAG, "SyncWorker enqueued for a background retry.");
    }

    private void updateSyncedStatus(long entityId, String timestamp) {
        databaseExecutor.execute(() -> {
            analysisDao.updateSyncedWithTimestamp(entityId, true, timestamp);
        });
    }

    private void navigateToReport(AnalysisEntity entity) {
        Intent intent = new Intent(this, AnalysisReportActivity.class);
        intent.putExtra("ANALYSIS_ID", entity.id);
        intent.putExtra("PATIENT_ID", entity.patientId);
        intent.putExtra("PATIENT_NAME", entity.patientName);
        intent.putExtra("IMAGE_URL", entity.imagePath);
        intent.putExtra("ORIGINAL_IMAGE_URL", entity.originalImagePath);
        intent.putExtra("RESULT_LABEL", entity.resultLabel);
        intent.putExtra("CONFIDENCE", entity.confidence);
        intent.putExtra("BOX_LEFT", entity.boxLeft);
        intent.putExtra("BOX_TOP", entity.boxTop);
        intent.putExtra("BOX_RIGHT", entity.boxRight);
        intent.putExtra("BOX_BOTTOM", entity.boxBottom);
        startActivity(intent);
        finish();
    }

    private String saveImageToInternalStorage(Bitmap bitmap) {
        String fileName = "scan_" + UUID.randomUUID().toString() + ".jpg";
        File directory = getApplicationContext().getFilesDir();
        File file = new File(directory, fileName);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            return file.getAbsolutePath();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void showImagePickerOptions() {
        String[] options = {"Camera", "Gallery"};
        new AlertDialog.Builder(this)
                .setTitle("Select Image From")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        if (checkCameraPermission()) openCamera();
                        else requestCameraPermission();
                    } else {
                        openGallery();
                    }
                }).show();
    }

    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_REQUEST_CODE);
    }

    private void openCamera() {
        startActivityForResult(new Intent(MediaStore.ACTION_IMAGE_CAPTURE), CAMERA_REQUEST_CODE);
    }

    private void openGallery() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICK_IMAGE_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        } else {
            Toast.makeText(this, "Camera permission is required to take photos", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null) {
            if (requestCode == PICK_IMAGE_REQUEST && data.getData() != null) {
                try {
                    imageBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), data.getData());
                    ivImagePlaceholder.setImageBitmap(imageBitmap);
                    setImageSelectedState();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else if (requestCode == CAMERA_REQUEST_CODE && data.getExtras() != null) {
                imageBitmap = (Bitmap) data.getExtras().get("data");
                ivImagePlaceholder.setImageBitmap(imageBitmap);
                setImageSelectedState();
            }
        }
    }

    @SuppressLint("NewApi")
    private void setImageSelectedState() {
        tvNoImageSelected.setVisibility(View.GONE);
        ivImagePlaceholder.setVisibility(View.VISIBLE);
        ivImagePlaceholder.setImageTintList(null);
        ivImagePlaceholder.setImageBitmap(imageBitmap); 
        btnAnalyze.setEnabled(true);
        btnAnalyze.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#4CAF50"))); // Change to Green when enabled
    
        Animation pulse = AnimationUtils.loadAnimation(this, R.anim.pulse_animation);
        btnAnalyze.startAnimation(pulse);
    }

    private void applyPressAnimation(View view) {
        android.view.animation.Animation anim = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.button_press);
        if (anim != null) {
            view.startAnimation(anim);
        }
    }
}
