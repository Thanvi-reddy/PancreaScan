package com.saveetha.pancreatic;

import android.graphics.Bitmap;

import com.saveetha.pancreatic.PancreasDetector.DetectionResult;

public class BitmapHolder {
    private static final BitmapHolder instance = new BitmapHolder();

    private Bitmap bitmap;
    private DetectionResult detectionResult;

    private BitmapHolder() {}

    public static BitmapHolder getInstance() {
        return instance;
    }

    public void setBitmap(Bitmap bitmap) {
        this.bitmap = bitmap;
    }

    public Bitmap getBitmap() {
        return bitmap;
    }

    public void setDetectionResult(DetectionResult detectionResult) {
        this.detectionResult = detectionResult;
    }

    public DetectionResult getDetectionResult() {
        return detectionResult;
    }

    public void clear() {
        bitmap = null;
        detectionResult = null;
    }
}
