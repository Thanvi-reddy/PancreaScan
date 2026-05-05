package com.saveetha.pancreatic;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "deleted_records")
public class DeletedRecordEntity {
    @PrimaryKey
    @NonNull
    public String patientId;

    public long deletedAt;

    public DeletedRecordEntity(@NonNull String patientId) {
        this.patientId = patientId;
        this.deletedAt = System.currentTimeMillis();
    }
}
