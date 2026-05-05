package com.saveetha.pancreatic;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface AnalysisDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(AnalysisEntity analysis);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<AnalysisEntity> analyses);

    @Update
    void update(AnalysisEntity analysis);

    @Delete
    void delete(AnalysisEntity analysis);

    @Query("SELECT * FROM analyses WHERE userEmail = :userEmail ORDER BY lastModified DESC")
    LiveData<List<AnalysisEntity>> getAll(String userEmail);

    @Query("SELECT * FROM analyses WHERE userEmail = :userEmail")
    List<AnalysisEntity> getAllSync(String userEmail);

    @Query("SELECT * FROM analyses WHERE isSynced = 0 AND userEmail = :userEmail")
    List<AnalysisEntity> getPendingSync(String userEmail);

    @Query("DELETE FROM analyses")
    void deleteAll();

    @Query("UPDATE analyses SET isSynced = :isSynced WHERE id = :id")
    void updateSynced(long id, boolean isSynced);

    @Query("UPDATE analyses SET isSynced = :isSynced, serverTimestamp = :timestamp WHERE id = :id")
    void updateSyncedWithTimestamp(long id, boolean isSynced, String timestamp);

    @Query("SELECT MAX(lastModified) FROM analyses WHERE isSynced = 1 AND userEmail = :userEmail")
    long getLatestRecordTimestamp(String userEmail);

    @Query("SELECT * FROM analyses WHERE patientId = :patientId AND userEmail = :userEmail LIMIT 1")
    AnalysisEntity getAnalysisByPatientId(String patientId, String userEmail);

    // Added to check for exact duplicate from server (same patient and same timestamp)
    @Query("SELECT * FROM analyses WHERE patientId = :patientId AND serverTimestamp = :timestamp AND userEmail = :userEmail LIMIT 1")
    AnalysisEntity getDuplicateFromServer(String patientId, String timestamp, String userEmail);

    @Query("SELECT * FROM analyses WHERE id = :id LIMIT 1")
    AnalysisEntity getAnalysisById(long id);

    @Query("SELECT COUNT(*) FROM analyses WHERE userEmail = :userEmail")
    LiveData<Integer> getTotalScansCount(String userEmail);

    @Query("SELECT COUNT(*) FROM analyses WHERE resultLabel LIKE 'Normal' AND userEmail = :userEmail")
    LiveData<Integer> getNormalScansCount(String userEmail);

    @Query("SELECT COUNT(*) FROM analyses WHERE resultLabel LIKE 'Abnormal' AND userEmail = :userEmail")
    LiveData<Integer> getAbnormalScansCount(String userEmail);
}
