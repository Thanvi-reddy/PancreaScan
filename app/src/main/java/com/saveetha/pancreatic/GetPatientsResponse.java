package com.saveetha.pancreatic;

import com.google.gson.annotations.SerializedName;
import java.util.List;

// This class represents the entire JSON object returned by your get_patients.php script.
public class GetPatientsResponse {
    @SerializedName("success")
    public boolean success;

    @SerializedName("user_id")
    public int userId;

    @SerializedName("patients")
    public List<PatientItem> patients;

    @SerializedName("count")
    public int count;
}
