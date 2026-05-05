package com.saveetha.pancreatic;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AnalysisReportActivity extends AppCompatActivity {

    private boolean feedbackSubmitted = false;
    private String currentPatientId;
    private AppDatabase database;
    private AnalysisDao analysisDao;
    private SharedPreferences securePrefs;
    private String userEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_analysis_report);

        database = AppDatabase.getInstance(this);
        analysisDao = database.analysisDao();

        try {
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            securePrefs = EncryptedSharedPreferences.create(
                "secure_user_session",
                masterKeyAlias,
                this,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
            String emailRaw = securePrefs.getString("user_email", "unknown");
            userEmail = emailRaw.trim().toLowerCase();
        } catch (Exception e) {
            e.printStackTrace();
            userEmail = "unknown";
        }

        populateData();
        setupButtons();
    }

    private void populateData() {
        ImageView ivUploadedScan = findViewById(R.id.ivUploadedScan);
        TextView tvPatientID = findViewById(R.id.tvPatientID);
        TextView tvPatientName = findViewById(R.id.tvPatientName);
        TextView tvScanDate = findViewById(R.id.tvScanDate);

        TextView tvPrediction = findViewById(R.id.tvPrediction);
        TextView tvConfidence = findViewById(R.id.tvConfidence);

        TextView tvNormalProb = findViewById(R.id.tvNormalProb);
        ProgressBar pbNormal = findViewById(R.id.pbNormal);
        TextView tvAbnormalProb = findViewById(R.id.tvAbnormalProb);
        ProgressBar pbAbnormal = findViewById(R.id.pbAbnormal);

        TextView tvObservationTitle = findViewById(R.id.tvObservationTitle);
        TextView tvObservationBullets = findViewById(R.id.tvObservationBullets);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            long analysisId = extras.getLong("ANALYSIS_ID", -1);
            String patientId = extras.getString("PATIENT_ID", "1");
            currentPatientId = patientId;
            String patientName = extras.getString("PATIENT_NAME", "Unknown");
            String imageUrl = extras.getString("IMAGE_URL");
            String resultLabel = extras.getString("RESULT_LABEL", "Normal");
            float confidence = extras.getFloat("CONFIDENCE", 0.0f);
            
            // Load data from database as the primary source of truth
            new Thread(() -> {
                AnalysisEntity entity;
                if (analysisId != -1) {
                    entity = analysisDao.getAnalysisById(analysisId);
                } else {
                    entity = analysisDao.getAnalysisByPatientId(patientId, userEmail);
                }

                if (entity != null) {
                    runOnUiThread(() -> {
                        updateFeedbackButtonsState();
                        updateReportUI(entity, extras);
                    });
                } else {
                    // Fallback to intent data if entity not found (though it should be)
                    runOnUiThread(() -> {
                        tvPatientID.setText(patientId);
                        tvPatientName.setText(patientName);
                        updateReportUIFromExtras(extras);
                    });
                }
            }).start();
        }
    }

    private void updateReportUI(AnalysisEntity entity, Bundle extras) {
        TextView tvPatientID = findViewById(R.id.tvPatientID);
        TextView tvPatientName = findViewById(R.id.tvPatientName);
        TextView tvScanDate = findViewById(R.id.tvScanDate);
        TextView tvPrediction = findViewById(R.id.tvPrediction);
        TextView tvConfidence = findViewById(R.id.tvConfidence);
        TextView tvNormalProb = findViewById(R.id.tvNormalProb);
        ProgressBar pbNormal = findViewById(R.id.pbNormal);
        TextView tvAbnormalProb = findViewById(R.id.tvAbnormalProb);
        ProgressBar pbAbnormal = findViewById(R.id.pbAbnormal);
        TextView tvObservationTitle = findViewById(R.id.tvObservationTitle);
        TextView tvObservationBullets = findViewById(R.id.tvObservationBullets);

        // Header Info
        tvPatientID.setText(entity.patientId);
        tvPatientName.setText(entity.patientName);
        
        SimpleDateFormat sdf = new SimpleDateFormat("EEEE, d MMMM yyyy", Locale.US);
        tvScanDate.setText(sdf.format(new Date(entity.lastModified)));

        // Image & Box
        if (entity.imagePath != null) {
            displayImage(entity.imagePath, entity.boxLeft, entity.boxTop, entity.boxRight, entity.boxBottom, entity.resultLabel);
        }

        // Stats
        float confPercent = entity.confidence * 100.0f;
        if (confPercent > 100) confPercent = 100;

        boolean isAbnormal = "Abnormal".equalsIgnoreCase(entity.resultLabel);
        animateConfidence(tvConfidence, confPercent);

        if (isAbnormal) {
            tvPrediction.setText("Abnormal (Pancreatitis/Edema)");
            tvPrediction.setTextColor(ContextCompat.getColor(this, R.color.abnormal_red));
            tvPrediction.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.abnormal_pill_bg));
            
            float abnormalVal = confPercent;
            float normalVal = Math.max(0, 100.0f - abnormalVal);
            
            animateProgressBar(pbAbnormal, (int) abnormalVal);
            animatePercentageText(tvAbnormalProb, "Abnormal: ", abnormalVal);
            animateProgressBar(pbNormal, (int) normalVal);
            animatePercentageText(tvNormalProb, "Normal: ", normalVal);

            tvObservationTitle.setText("⚠️ Abnormal Pancreas with Edema");
            tvObservationTitle.setTextColor(ContextCompat.getColor(this, R.color.abnormal_red));
            tvObservationBullets.setText("• Confidence: " + (int)confPercent + "%\n" +
                    "• Analysis indicates signs consistent with pancreatitis or edema. The bounding box highlights areas of potential inflammation.\n" +
                    "• Recommendation: Clinical correlation required. Consider follow-up imaging and laboratory tests (Amylase/Lipase).");
        } else {
            tvPrediction.setText("Normal Pancreas");
            tvPrediction.setTextColor(ContextCompat.getColor(this, R.color.brand_green));
            tvPrediction.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.normal_pill_bg));
            
            float normalVal = confPercent;
            float abnormalVal = Math.max(0, 100.0f - normalVal);
            
            animateProgressBar(pbAbnormal, (int) abnormalVal);
            animatePercentageText(tvAbnormalProb, "Abnormal: ", abnormalVal);
            animateProgressBar(pbNormal, (int) normalVal);
            animatePercentageText(tvNormalProb, "Normal: ", normalVal);

            tvObservationTitle.setText("✅ Pancreas appears Normal");
            tvObservationTitle.setTextColor(ContextCompat.getColor(this, R.color.brand_green));
            tvObservationBullets.setText("• Confidence: " + (int)confPercent + "%\n" +
                    "• The pancreatic architecture and surrounding areas appear within normal limits.\n" +
                    "• Recommendation: Routine follow-up as advised by your physician.");
        }
    }

    private void updateReportUIFromExtras(Bundle extras) {
        TextView tvPrediction = findViewById(R.id.tvPrediction);
        TextView tvConfidence = findViewById(R.id.tvConfidence);
        TextView tvNormalProb = findViewById(R.id.tvNormalProb);
        ProgressBar pbNormal = findViewById(R.id.pbNormal);
        TextView tvAbnormalProb = findViewById(R.id.tvAbnormalProb);
        ProgressBar pbAbnormal = findViewById(R.id.pbAbnormal);
        TextView tvObservationTitle = findViewById(R.id.tvObservationTitle);
        TextView tvObservationBullets = findViewById(R.id.tvObservationBullets);

        String resultLabel = extras.getString("RESULT_LABEL", "Normal");
        float confidence = extras.getFloat("CONFIDENCE", 0.0f);
        String imageUrl = extras.getString("IMAGE_URL");

        float confPercent = confidence * 100.0f;
        if (confPercent > 100) confPercent = 100;

        animateConfidence(tvConfidence, confPercent);

        if (imageUrl != null) {
            displayImage(imageUrl, 
                extras.getFloat("BOX_LEFT", 0f), 
                extras.getFloat("BOX_TOP", 0f), 
                extras.getFloat("BOX_RIGHT", 0f), 
                extras.getFloat("BOX_BOTTOM", 0f), 
                resultLabel);
        }

        if ("Abnormal".equalsIgnoreCase(resultLabel)) {
            tvPrediction.setText("Abnormal (Pancreatitis/Edema)");
            tvPrediction.setTextColor(ContextCompat.getColor(this, R.color.abnormal_red));
            tvPrediction.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.abnormal_pill_bg));
            
            float abnormalVal = confPercent;
            float normalVal = Math.max(0, 100.0f - abnormalVal);

            animateProgressBar(pbAbnormal, (int) abnormalVal);
            animatePercentageText(tvAbnormalProb, "Abnormal: ", abnormalVal);
            animateProgressBar(pbNormal, (int) normalVal);
            animatePercentageText(tvNormalProb, "Normal: ", normalVal);

            tvObservationTitle.setText("⚠️ Abnormal Pancreas with Edema");
            tvObservationTitle.setTextColor(ContextCompat.getColor(this, R.color.abnormal_red));
            tvObservationBullets.setText("• Confidence: " + (int)confPercent + "%\n" +
                    "• Analysis indicates signs consistent with pancreatitis or edema. The bounding box highlights areas of potential inflammation.\n" +
                    "• Recommendation: Clinical correlation required. Consider follow-up imaging and laboratory tests (Amylase/Lipase).");
        } else {
            tvPrediction.setText("Normal Pancreas");
            tvPrediction.setTextColor(ContextCompat.getColor(this, R.color.brand_green));
            tvPrediction.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.normal_pill_bg));
            
            float normalVal = confPercent;
            float abnormalVal = Math.max(0, 100.0f - normalVal);

            animateProgressBar(pbNormal, (int) normalVal);
            animatePercentageText(tvNormalProb, "Normal: ", normalVal);
            animateProgressBar(pbAbnormal, (int) abnormalVal);
            animatePercentageText(tvAbnormalProb, "Abnormal: ", abnormalVal);

            tvObservationTitle.setText("✅ Pancreas appears Normal");
            tvObservationTitle.setTextColor(ContextCompat.getColor(this, R.color.brand_green));
            tvObservationBullets.setText("• Confidence: " + (int)confPercent + "%\n" +
                    "• The pancreatic architecture and surrounding areas appear within normal limits.\n" +
                    "• Recommendation: Routine follow-up as advised by your physician.");
        }
    }

    private void displayImage(String imageUrl, float bLeft, float bTop, float bRight, float bBottom, String resultLabel) {
        ImageView ivUploadedScan = findViewById(R.id.ivUploadedScan);
        Bitmap bitmap = BitmapFactory.decodeFile(imageUrl);
        if (bitmap != null) {
            if (bLeft != 0 || bTop != 0 || bRight != 0 || bBottom != 0) {
                bitmap = drawBoundingBox(bitmap, bLeft, bTop, bRight, bBottom, resultLabel);
            }
            ivUploadedScan.setImageBitmap(bitmap);
        }
    }

    private void animateConfidence(TextView textView, float targetPercent) {
        android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofFloat(0f, targetPercent);
        animator.setDuration(1200); // 1.2 seconds for confidence
        animator.setInterpolator(new android.view.animation.DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            float value = (float) animation.getAnimatedValue();
            textView.setText(String.format(Locale.US, "%.2f%%", value));
        });
        animator.start();
    }

    private void animateProgressBar(ProgressBar progressBar, int targetProgress) {
        android.animation.ObjectAnimator animator = android.animation.ObjectAnimator.ofInt(progressBar, "progress", 0, targetProgress);
        animator.setDuration(1200); // 1.2 seconds
        animator.setInterpolator(new android.view.animation.DecelerateInterpolator());
        animator.start();
    }

    private void animatePercentageText(TextView textView, String prefix, float targetPercent) {
        android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofFloat(0f, targetPercent);
        animator.setDuration(1200); // 1.2 seconds
        animator.setInterpolator(new android.view.animation.DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            float value = (float) animation.getAnimatedValue();
            textView.setText(String.format(Locale.US, prefix + "%.2f%%", value));
        });
        animator.start();
    }

    private void setupButtons() {
        View btnCorrect = findViewById(R.id.btnCorrect);
        View btnIncorrect = findViewById(R.id.btnIncorrect);

        btnCorrect.setOnClickListener(v -> {
            applyPressAnimation(v);
            if (!feedbackSubmitted) {
                feedbackSubmitted = true;
                saveFeedbackToDatabase();
                showFeedbackAnimation(true);
                updateFeedbackButtonsState();
            } else {
                Toast.makeText(this, "Feedback already submitted", Toast.LENGTH_SHORT).show();
            }
        });

        btnIncorrect.setOnClickListener(v -> {
            applyPressAnimation(v);
            if (!feedbackSubmitted) {
                feedbackSubmitted = true;
                saveFeedbackToDatabase();
                showFeedbackAnimation(false);
                updateFeedbackButtonsState();
            } else {
                Toast.makeText(this, "Feedback already submitted", Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.btnReturnToDashboard).setOnClickListener(v -> {
            applyPressAnimation(v);
            Intent intent = new Intent(this, DashboardActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });

        findViewById(R.id.btnShareReport).setOnClickListener(v -> {
            generatePdfAndShare();
        });
    }

    private void generatePdfAndShare() {
        android.graphics.pdf.PdfDocument document = new android.graphics.pdf.PdfDocument();
        android.graphics.pdf.PdfDocument.PageInfo pageInfo = new android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create(); // A4 size
        android.graphics.pdf.PdfDocument.Page page = document.startPage(pageInfo);

        android.graphics.Canvas canvas = page.getCanvas();
        android.graphics.Paint paint = new android.graphics.Paint();
        android.graphics.Paint titlePaint = new android.graphics.Paint();
        android.graphics.Paint headerPaint = new android.graphics.Paint();

        // --- Styles ---
        int primaryColor = android.graphics.Color.rgb(58, 134, 255); // Brand Blue
        int darkText = android.graphics.Color.rgb(33, 33, 33);
        int lightGray = android.graphics.Color.rgb(240, 240, 240);

        // --- Header Section ---
        headerPaint.setColor(primaryColor);
        canvas.drawRect(0, 0, 595, 80, headerPaint); // Blue Header Bar

        titlePaint.setTextSize(24);
        titlePaint.setColor(android.graphics.Color.WHITE);
        titlePaint.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD));
        canvas.drawText("PANCREATIC ANALYSIS REPORT", 40, 50, titlePaint);
        
        titlePaint.setTextSize(12);
        canvas.drawText("Generated by PancreaScan AI", 400, 50, titlePaint);

        int y = 120;
        int x = 40;

        // --- Patient Information Box ---
        paint.setColor(lightGray);
        canvas.drawRect(x, y - 20, 555, y + 90, paint); // Background Box
        
        paint.setColor(darkText);
        paint.setTextSize(16);
        paint.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD));
        canvas.drawText("PATIENT DETAILS", x + 10, y + 5, paint);
        
        paint.setTextSize(12);
        paint.setTypeface(android.graphics.Typeface.DEFAULT);

        String patientName = ((TextView)findViewById(R.id.tvPatientName)).getText().toString();
        String patientId = ((TextView)findViewById(R.id.tvPatientID)).getText().toString();
        String scanDate = ((TextView)findViewById(R.id.tvScanDate)).getText().toString();

        int rowY = y + 35;
        canvas.drawText("Patient Name:", x + 10, rowY, paint);
        canvas.drawText(patientName, x + 120, rowY, paint);

        canvas.drawText("Patient ID:", x + 300, rowY, paint);
        canvas.drawText(patientId, x + 400, rowY, paint);
        
        rowY += 25;
        canvas.drawText("Scan Date:", x + 10, rowY, paint);
        canvas.drawText(scanDate, x + 120, rowY, paint);
        
        y += 130;

        // --- Analysis Results Section ---
        paint.setColor(primaryColor);
        canvas.drawRect(x, y - 5, x + 5, y + 20, paint); // Blue accent line
        
        paint.setColor(darkText);
        paint.setTextSize(16);
        paint.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD));
        canvas.drawText("ANALYSIS RESULTS", x + 15, y + 15, paint);
        
        y += 40;
        
        String prediction = ((TextView)findViewById(R.id.tvPrediction)).getText().toString();
        String confidence = ((TextView)findViewById(R.id.tvConfidence)).getText().toString();
        
        paint.setTextSize(14);
        paint.setTypeface(android.graphics.Typeface.DEFAULT);
        
        // Result Table Header
        paint.setColor(lightGray);
        canvas.drawRect(x, y, 555, y + 30, paint);
        paint.setColor(darkText);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        canvas.drawText("Prediction", x + 10, y + 20, paint);
        canvas.drawText("Confidence Score", x + 300, y + 20, paint);
        
        y += 50;
        // Result Values
        paint.setTypeface(android.graphics.Typeface.DEFAULT);
        
        // Color code the prediction
        if (prediction.toLowerCase().contains("abnormal")) {
            paint.setColor(android.graphics.Color.RED);
        } else {
            paint.setColor(android.graphics.Color.rgb(34, 139, 34)); // Green
        }
        canvas.drawText(prediction, x + 10, y, paint);
        
        paint.setColor(darkText);
        canvas.drawText(confidence, x + 300, y, paint);
        
        y += 40;
        
        // --- CT Scan Image w/ Disclaimer ---
        paint.setColor(darkText);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        canvas.drawText("CT SCAN IMAGE", x, y, paint);
        y += 20;

        ImageView ivScan = findViewById(R.id.ivUploadedScan);
        if (ivScan != null && ivScan.getDrawable() != null) {
            android.graphics.drawable.BitmapDrawable drawable = (android.graphics.drawable.BitmapDrawable) ivScan.getDrawable();
            android.graphics.Bitmap bitmap = drawable.getBitmap();
            
            if (bitmap != null) {
                // Max dimensions for PDF
                int maxWidth = 515; // 555 - 40
                int maxHeight = 300; 
                
                float scale = Math.min((float)maxWidth / bitmap.getWidth(), (float)maxHeight / bitmap.getHeight());
                int targetWidth = (int) (bitmap.getWidth() * scale);
                int targetHeight = (int) (bitmap.getHeight() * scale);
                
                android.graphics.Bitmap scaledBitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true);
                
                // Draw border around image
                paint.setStyle(android.graphics.Paint.Style.STROKE);
                paint.setStrokeWidth(1);
                paint.setColor(android.graphics.Color.GRAY);
                canvas.drawRect(x, y, x + targetWidth, y + targetHeight, paint);
                
                // Draw Image
                paint.setStyle(android.graphics.Paint.Style.FILL);
                canvas.drawBitmap(scaledBitmap, x, y, null);
                
                y += targetHeight + 30;
            }
        }

        // --- Disclaimer / Footer ---
        // Push to bottom
        y = 780;
        paint.setColor(lightGray);
        canvas.drawRect(0, y - 20, 595, 842, paint);
        
        paint.setColor(android.graphics.Color.GRAY);
        paint.setTextSize(10);
        paint.setTextAlign(android.graphics.Paint.Align.CENTER);
        
        canvas.drawText("MEDICAL DISCLAIMER: This report is generated by an Artificial Intelligence system.", 297, y, paint);
        canvas.drawText("It relies on probabilistic models and may contain errors. It is NOT a definitive diagnosis.", 297, y + 15, paint);
        canvas.drawText("This report must be reviewed by a qualified radiologist or physician.", 297, y + 30, paint);

        document.finishPage(page);

        // Save file
        try {
            java.io.File file = new java.io.File(getExternalCacheDir(), "Medical_Report_" + patientId + ".pdf");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
            document.writeTo(fos);
            document.close();
            fos.close();

            // Share
            android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/pdf");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Share Medical Report"));

        } catch (java.io.IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error generating PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            document.close();
        }
    }

    private void showFeedbackAnimation(boolean isCorrect) {
        try {
            if (isFinishing()) return;
            
            // Create a custom dialog for feedback
            android.app.Dialog dialog = new android.app.Dialog(this);
            dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
            dialog.setContentView(R.layout.dialog_feedback);
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            }
            
            TextView tvEmoji = dialog.findViewById(R.id.tvFeedbackEmoji);
            TextView tvMessage = dialog.findViewById(R.id.tvFeedbackMessage);
            
            if (tvEmoji != null && tvMessage != null) {
                if (isCorrect) {
                    tvEmoji.setText("👍");
                } else {
                    tvEmoji.setText("👎");
                }
                tvMessage.setText("Thank you for your feedback!");
                
                // Animate the emoji
                tvEmoji.setScaleX(0f);
                tvEmoji.setScaleY(0f);
                tvEmoji.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(500)
                        .setInterpolator(new android.view.animation.OvershootInterpolator())
                        .start();
            }
            
            dialog.show();
            
            // Auto-dismiss after 2 seconds
            new android.os.Handler().postDelayed(() -> {
                if (dialog.isShowing() && !isFinishing()) {
                    dialog.dismiss();
                }
            }, 2000);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveFeedbackToDatabase() {
        if (currentPatientId != null) {
            new Thread(() -> {
                AnalysisEntity entity = analysisDao.getAnalysisByPatientId(currentPatientId, userEmail);
                if (entity != null) {
                    entity.feedbackSubmitted = true;
                    analysisDao.update(entity);
                }
            }).start();
        }
    }

    private void updateFeedbackButtonsState() {
        View btnCorrect = findViewById(R.id.btnCorrect);
        View btnIncorrect = findViewById(R.id.btnIncorrect);
        
        if (btnCorrect == null || btnIncorrect == null) return;
        
        if (feedbackSubmitted) {
            btnCorrect.setEnabled(false);
            btnIncorrect.setEnabled(false);
            btnCorrect.setAlpha(0.5f);
            btnIncorrect.setAlpha(0.5f);
        } else {
            btnCorrect.setEnabled(true);
            btnIncorrect.setEnabled(true);
            btnCorrect.setAlpha(1.0f);
            btnIncorrect.setAlpha(1.0f);
        }
    }

    private Bitmap drawBoundingBox(Bitmap bitmap, float left, float top, float right, float bottom, String label) {
        try {
            Bitmap mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
            int width = mutableBitmap.getWidth();
            int height = mutableBitmap.getHeight();
            Canvas canvas = new Canvas(mutableBitmap);
            
            // Check if coordinates are normalized (0 to 1) and scale them
            if (left <= 1.0f && top <= 1.0f && right <= 1.0f && bottom <= 1.0f && (right > left)) {
                left *= width;
                right *= width;
                top *= height;
                bottom *= height;
            }

            Paint paint = new Paint();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(width, height) / 100f);
            
            if (label.equalsIgnoreCase("Abnormal")) {
                paint.setColor(Color.RED);
            } else {
                paint.setColor(Color.GREEN);
            }
            
            RectF rect = new RectF(left, top, right, bottom);
            canvas.drawRect(rect, paint);
            
            // Draw label background
            Paint bgPaint = new Paint();
            bgPaint.setColor(paint.getColor());
            bgPaint.setStyle(Paint.Style.FILL);
            
            Paint textPaint = new Paint();
            textPaint.setColor(Color.WHITE);
            float textSize = Math.max(width, height) / 25f;
            textPaint.setTextSize(textSize);
            textPaint.setFakeBoldText(true);
            
            String text = label.toUpperCase();
            // Background for text
            float textWidth = textPaint.measureText(text);
            canvas.drawRect(left, top - textSize - 10, left + textWidth + 20, top, bgPaint);
            canvas.drawText(text, left + 10, top - 10, textPaint);
            
            return mutableBitmap;
        } catch (Exception e) {
            Log.e("AnalysisReport", "Error drawing bounding box", e);
            return bitmap;
        }
    }

    private void applyPressAnimation(View view) {
        try {
            android.view.animation.Animation anim = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.button_press);
            if (anim != null) {
                view.startAnimation(anim);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
