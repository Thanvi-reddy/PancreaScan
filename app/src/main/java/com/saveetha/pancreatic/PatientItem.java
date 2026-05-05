package com.saveetha.pancreatic;

import com.google.gson.annotations.SerializedName;

public class PatientItem {
    @SerializedName("id")
    public int id;

    @SerializedName("user_id")
    public int userId;

    @SerializedName("patient_id")
    public String patient_id;

    @SerializedName(value = "patient_name", alternate = "name")
    public String name;

    @SerializedName("result")
    public String result;

    @SerializedName("confidence")
    public float confidence;

    @SerializedName(value = "timestamp", alternate = "created_at")
    public String created_at;

    @SerializedName(value = "image_path", alternate = "ct_image_path")
    public String ct_image_path;

    @SerializedName("box_left")
    public float boxLeft;

    @SerializedName("box_top")
    public float boxTop;

    @SerializedName("box_right")
    public float boxRight;

    @SerializedName("box_bottom")
    public float boxBottom;
}
