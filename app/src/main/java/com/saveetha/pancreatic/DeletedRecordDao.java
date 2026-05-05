package com.saveetha.pancreatic;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface DeletedRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(DeletedRecordEntity record);

    @Query("SELECT patientId FROM deleted_records")
    List<String> getAllDeletedPatientIds();
    
    @Query("SELECT COUNT(*) FROM deleted_records WHERE patientId = :patientId")
    int isDeleted(String patientId);
}
