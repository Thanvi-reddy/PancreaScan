package com.saveetha.pancreatic;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class SyncHistoryResponse {
    @SerializedName("status")
    public String status;

    @SerializedName("history")
    public List<PatientItem> history;

    public boolean isSuccess() {
        return "success".equalsIgnoreCase(status);
    }
}
