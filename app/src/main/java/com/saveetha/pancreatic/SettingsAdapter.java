package com.saveetha.pancreatic;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class SettingsAdapter extends RecyclerView.Adapter<SettingsAdapter.SettingViewHolder> {

    private final List<Setting> settingsList;

    public SettingsAdapter(List<Setting> settingsList) {
        this.settingsList = settingsList;
    }

    @NonNull
    @Override
    public SettingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_setting, parent, false);
        return new SettingViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SettingViewHolder holder, int position) {
        Setting setting = settingsList.get(position);
        holder.bind(setting);
    }

    @Override
    public int getItemCount() {
        return settingsList.size();
    }

    static class SettingViewHolder extends RecyclerView.ViewHolder {
        private final ImageView icon;
        private final TextView title;

        SettingViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.ivSettingIcon);
            title = itemView.findViewById(R.id.tvSettingTitle);
        }

        void bind(Setting setting) {
            icon.setImageResource(setting.iconResId);
            title.setText(setting.title);
            itemView.setOnClickListener(v -> setting.action.run());
        }
    }
}
