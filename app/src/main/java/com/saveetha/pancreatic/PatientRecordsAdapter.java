package com.saveetha.pancreatic;

import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;

import java.util.ArrayList;
import java.util.List;

public class PatientRecordsAdapter extends ListAdapter<AnalysisEntity, PatientRecordViewHolder> implements Filterable {

    private List<AnalysisEntity> fullList = new ArrayList<>();
    private OnItemClickListener listener;

    public PatientRecordsAdapter(@NonNull DiffUtil.ItemCallback<AnalysisEntity> diffCallback) {
        super(diffCallback);
    }

    public void setFullList(List<AnalysisEntity> list) {
        fullList = new ArrayList<>(list);
    }

    public AnalysisEntity getRecordAt(int position) {
        return getItem(position);
    }

    @NonNull
    @Override
    public PatientRecordViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return PatientRecordViewHolder.create(parent);
    }

    @Override
    public void onBindViewHolder(@NonNull PatientRecordViewHolder holder, int position) {
        AnalysisEntity current = getItem(position);
        if (current != null) {
            holder.bind(current);
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(current);
                }
            });
        }
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                List<AnalysisEntity> filteredList = new ArrayList<>();
                if (constraint == null || constraint.length() == 0) {
                    filteredList.addAll(fullList);
                } else {
                    String filterPattern = constraint.toString().toLowerCase().trim();
                    for (AnalysisEntity item : fullList) {
                        boolean matchesId = item.patientId != null && item.patientId.toLowerCase().contains(filterPattern);
                        boolean matchesName = item.patientName != null && item.patientName.toLowerCase().contains(filterPattern);
                        
                        if (matchesId || matchesName) {
                            filteredList.add(item);
                        }
                    }
                }
                FilterResults results = new FilterResults();
                results.values = filteredList;
                return results;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                submitList((List<AnalysisEntity>) results.values);
            }
        };
    }

    public interface OnItemClickListener {
        void onItemClick(AnalysisEntity record);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public static class RecordDiff extends DiffUtil.ItemCallback<AnalysisEntity> {
        @Override
        public boolean areItemsTheSame(@NonNull AnalysisEntity oldItem, @NonNull AnalysisEntity newItem) {
            return oldItem.id == newItem.id;
        }

        @Override
        public boolean areContentsTheSame(@NonNull AnalysisEntity oldItem, @NonNull AnalysisEntity newItem) {
            return oldItem.id == newItem.id &&
                    oldItem.lastModified == newItem.lastModified;
        }
    }
}
