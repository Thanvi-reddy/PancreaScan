package com.saveetha.pancreatic;

// A simplified class for storing only the necessary detection results in the database.
public class StoredDetectionResult {
    public PancreasDetector.Recognition recognition;

    public StoredDetectionResult(PancreasDetector.Recognition recognition) {
        this.recognition = recognition;
    }
}
