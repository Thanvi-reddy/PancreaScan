package com.saveetha.pancreatic;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import android.graphics.Color;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PatientRecordViewHolder extends RecyclerView.ViewHolder {
    private final TextView tvPatientId, tvPatientName, tvResult, tvConfidence, tvDate;
    private final View vIndicator;

    private PatientRecordViewHolder(@NonNull View itemView) {
        super(itemView);
        tvPatientId = itemView.findViewById(R.id.tvPatientId);
        tvPatientName = itemView.findViewById(R.id.tvPatientName);
        tvResult = itemView.findViewById(R.id.tvResult);
        tvConfidence = itemView.findViewById(R.id.tvConfidence);
        tvDate = itemView.findViewById(R.id.tvDate);
        vIndicator = itemView.findViewById(R.id.vResultIndicator);
    }

    public void bind(AnalysisEntity analysis) {
        if (analysis != null) {
            tvPatientId.setText("ID: " + analysis.patientId);
            tvPatientName.setText(analysis.patientName);
            tvResult.setText(analysis.resultLabel);
            tvConfidence.setText(String.format(Locale.US, "%.1f%% Conf.", analysis.confidence * 100));

            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.US);
            tvDate.setText(sdf.format(new Date(analysis.lastModified)));

            // Dynamic Coloring
            if ("Abnormal".equalsIgnoreCase(analysis.resultLabel)) {
                tvResult.setTextColor(Color.parseColor("#D32F2F")); // Red
                vIndicator.setBackgroundColor(Color.parseColor("#D32F2F"));
            } else {
                tvResult.setTextColor(Color.parseColor("#388E3C")); // Green
                vIndicator.setBackgroundColor(Color.parseColor("#388E3C"));
            }
        }
    }

    public static PatientRecordViewHolder create(ViewGroup parent) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_patient, parent, false);
        return new PatientRecordViewHolder(view);
    }
}
