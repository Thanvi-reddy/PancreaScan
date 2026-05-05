package com.saveetha.pancreatic;

public class PatientRecord {
    private String id;
    private String name;
    private String date;
    private String scanResult;

    public PatientRecord(String id, String name, String date, String scanResult) {
        this.id = id;
        this.name = name;
        this.date = date;
        this.scanResult = scanResult;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDate() {
        return date;
    }

    public String getScanResult() {
        return scanResult;
    }
}