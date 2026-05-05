package com.saveetha.pancreatic;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "analyses")
public class AnalysisEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String patientId;

    public String userEmail; // ADDED: For data isolation per user

    public String patientName;
    public String imagePath;
    public String originalImagePath;
    public String resultLabel;
    public float confidence;
    public long lastModified;
    public boolean isSynced;
    public String serverTimestamp;

    public float boxLeft;
    public float boxTop;
    public float boxRight;
    public float boxBottom;

    public boolean feedbackSubmitted = false;

    // Required public constructor for Room
    public AnalysisEntity() {
        this.patientId = ""; // Ensure patientId is never null
        this.userEmail = "";
    }
}
