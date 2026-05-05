package com.saveetha.pancreatic;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

import java.io.File;
import java.util.Locale;

public class RecordDetailActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record_detail);

        ImageView ivResultImage = findViewById(R.id.ivDetailImage);
        TextView tvPatientId = findViewById(R.id.tvDetailPatientInfo);
        TextView tvResultLabel = findViewById(R.id.tvDetailResult);
        TextView tvConfidence = findViewById(R.id.tvDetailConfidence);
        ImageButton btnBack = findViewById(R.id.btnBackDetail);

        btnBack.setOnClickListener(v -> finish());

        String patientId = getIntent().getStringExtra("patient_id");
        String patientName = getIntent().getStringExtra("patient_name");
        String resultLabel = getIntent().getStringExtra("result_label");
        String imagePath = getIntent().getStringExtra("image_path");
        float confidence = getIntent().getFloatExtra("confidence", 0.0f);

        if (imagePath != null && !imagePath.isEmpty()) {
            if (imagePath.startsWith("/")) { // It's a local file path
                Glide.with(this).load(new File(imagePath)).into(ivResultImage);
            } else { // It's a server path
                String fullImageUrl = ApiClient.BASE_URL + imagePath;
                Glide.with(this).load(fullImageUrl).into(ivResultImage);
            }
        } else {
            Toast.makeText(this, "Image not found", Toast.LENGTH_SHORT).show();
        }

        String pId = (patientId != null) ? patientId : "N/A";
        String pName = (patientName != null) ? patientName : "N/A";
        String rLabel = (resultLabel != null) ? resultLabel : "N/A";

        tvPatientId.setText(String.format(Locale.US, "Patient ID: %s - %s", pId, pName));
        tvResultLabel.setText(String.format(Locale.US, "Result: %s", rLabel));
        tvConfidence.setText(String.format(Locale.US, "Confidence: %.2f%%", confidence * 100));
    }
}
