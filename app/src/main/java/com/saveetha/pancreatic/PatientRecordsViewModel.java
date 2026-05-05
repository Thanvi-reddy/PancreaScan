package com.saveetha.pancreatic;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PatientRecordsViewModel extends AndroidViewModel {

    private final AnalysisDao analysisDao;

    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();

    public PatientRecordsViewModel(Application application) {
        super(application);
        AppDatabase db = AppDatabase.getInstance(application);
        analysisDao = db.analysisDao();
    }

    LiveData<List<AnalysisEntity>> getAllRecords(String userEmail) {
        return analysisDao.getAll(userEmail);
    }

    public void insert(AnalysisEntity analysis) {
        databaseExecutor.execute(() -> analysisDao.insert(analysis));
    }
    
    public void delete(AnalysisEntity analysis) {
        databaseExecutor.execute(() -> analysisDao.delete(analysis));
    }
}
