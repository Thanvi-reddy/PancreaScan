package com.saveetha.pancreatic;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import android.graphics.Color;
import java.util.Locale;

public class PatientAdapter extends RecyclerView.Adapter<PatientAdapter.PatientViewHolder> {

    private List<PatientItem> list = new ArrayList<>();
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(PatientItem item);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setData(List<PatientItem> newList) {
        list.clear();
        list.addAll(newList);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PatientViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_patient, parent, false);
        return new PatientViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PatientViewHolder holder, int position) {
        PatientItem item = list.get(position);
        holder.tvName.setText(item.name);
        holder.tvId.setText("ID: " + item.patient_id);
        holder.tvResult.setText(item.result);
        holder.tvConfidence.setText(String.format(Locale.US, "%.1f%% Conf.", item.confidence * 100));
        
        // Truncate or format date for brief view
        if (item.created_at != null && item.created_at.length() > 10) {
            holder.tvDate.setText(item.created_at.substring(0, 10));
        } else {
            holder.tvDate.setText(item.created_at);
        }

        // Dynamic Coloring
        if ("Abnormal".equalsIgnoreCase(item.result)) {
            holder.tvResult.setTextColor(Color.parseColor("#D32F2F")); // Red
            holder.vIndicator.setBackgroundColor(Color.parseColor("#D32F2F"));
        } else {
            holder.tvResult.setTextColor(Color.parseColor("#388E3C")); // Green
            holder.vIndicator.setBackgroundColor(Color.parseColor("#388E3C"));
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(item);
        });
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class PatientViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvId, tvResult, tvConfidence, tvDate;
        View vIndicator;

        PatientViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName   = itemView.findViewById(R.id.tvPatientName);
            tvId     = itemView.findViewById(R.id.tvPatientId);
            tvResult = itemView.findViewById(R.id.tvResult);
            tvConfidence = itemView.findViewById(R.id.tvConfidence);
            tvDate   = itemView.findViewById(R.id.tvDate);
            vIndicator = itemView.findViewById(R.id.vResultIndicator);
        }
    }
}
