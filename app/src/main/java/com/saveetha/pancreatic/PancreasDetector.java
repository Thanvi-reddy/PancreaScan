package com.saveetha.pancreatic;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.RectF;
import org.tensorflow.lite.Interpreter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Map;

public class PancreasDetector {

    private static final String MODEL_FILE = "pancreas.tflite";
    private static final int INPUT_SIZE = 640;
    private static final String[] CLASSES = {"Abnormal", "Normal"};

    private final Interpreter tflite;

    public static class DetectionResult implements Serializable {
        public final Recognition recognition;

        public DetectionResult(Recognition recognition) {
            this.recognition = recognition;
        }
    }

    public static class Recognition implements Serializable {
        public final String label;
        public final float confidence;
        public final RectF boundingBox;

        public Recognition(String label, float confidence, RectF boundingBox) {
            this.label = label;
            this.confidence = confidence;
            this.boundingBox = boundingBox;
        }
    }

    public PancreasDetector(Context context) throws IOException {
        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(4);
        this.tflite = new Interpreter(loadModelFile(context), options);
    }

    private MappedByteBuffer loadModelFile(Context context) throws IOException {
        String modelPath = getModelPath(context);

        if (modelPath.endsWith(".tflite") && !modelPath.contains("/")) {
            // Load from assets
            AssetFileDescriptor fileDescriptor = context.getAssets().openFd(modelPath);
            try (FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor())) {
                FileChannel fileChannel = inputStream.getChannel();
                long startOffset = fileDescriptor.getStartOffset();
                long declaredLength = fileDescriptor.getDeclaredLength();
                return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
            }
        } else {
            // Load from absolute file path (internal storage)
            try (FileInputStream inputStream = new FileInputStream(modelPath)) {
                FileChannel fileChannel = inputStream.getChannel();
                return fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size());
            }
        }
    }

    private String getModelPath(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        if (prefs.getBoolean("use_global_model", false)) {
            File globalFile = new File(context.getFilesDir(), "pancreas_global_r1.tflite");
            if (globalFile.exists()) {
                return globalFile.getAbsolutePath();
            }
        }
        return MODEL_FILE; // Fallback to the original model in assets
    }

    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true);
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3);
        byteBuffer.order(ByteOrder.nativeOrder());
        int[] intValues = new int[INPUT_SIZE * INPUT_SIZE];
        resizedBitmap.getPixels(intValues, 0, resizedBitmap.getWidth(), 0, 0, resizedBitmap.getWidth(), resizedBitmap.getHeight());
        for (int val : intValues) {
            byteBuffer.putFloat(((val >> 16) & 0xFF) / 255.0f);
            byteBuffer.putFloat(((val >> 8) & 0xFF) / 255.0f);
            byteBuffer.putFloat((val & 0xFF) / 255.0f);
        }
        return byteBuffer;
    }

    public DetectionResult detect(Bitmap bitmap) {
        ByteBuffer inputBuffer = convertBitmapToByteBuffer(bitmap);
        float[][][] detectionOutput = new float[1][38][8400]; 
        Map<Integer, Object> outputs = new HashMap<>();
        outputs.put(0, detectionOutput);

        try {
            tflite.runForMultipleInputsOutputs(new Object[]{inputBuffer}, outputs);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

        Recognition bestRecognition = processDetectionOutput(detectionOutput, bitmap.getWidth(), bitmap.getHeight());

        if (bestRecognition != null) {
            return new DetectionResult(bestRecognition);
        }
        return null;
    }

    private Recognition processDetectionOutput(float[][][] detectionOutput, int originalWidth, int originalHeight) {
        float maxConfidence = 0.0f;
        Recognition bestRecognition = null;

        float[][] transposedOutput = new float[8400][38];
        for (int i = 0; i < 8400; i++) {
            for (int j = 0; j < 38; j++) {
                transposedOutput[i][j] = detectionOutput[0][j][i];
            }
        }

        for (int i = 0; i < 8400; i++) {
            float[] detection = transposedOutput[i];
            
            float scoreAbnormal = detection[4];
            float scoreNormal = detection[5];
            float currentConfidence = Math.max(scoreAbnormal, scoreNormal);

            if (currentConfidence > maxConfidence) {
                maxConfidence = currentConfidence;
                int classIndex = (scoreAbnormal > scoreNormal) ? 0 : 1;

                // Model outputs NORMALIZED center_x, center_y, width, height
                float cx = detection[0];
                float cy = detection[1];
                float w = detection[2];
                float h = detection[3];

                // 🔥 CORRECTED: Scale the NORMALIZED coordinates by the ORIGINAL image dimensions.
                float left = (cx - w / 2) * originalWidth;
                float top = (cy - h / 2) * originalHeight;
                float right = (cx + w / 2) * originalWidth;
                float bottom = (cy + h / 2) * originalHeight;

                RectF scaledBox = new RectF(left, top, right, bottom);
                bestRecognition = new Recognition(CLASSES[classIndex], maxConfidence, scaledBox);
            }
        }
        return bestRecognition;
    }
}
