package com.saveetha.pancreatic;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {AnalysisEntity.class, DeletedRecordEntity.class}, version = 22, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    public abstract AnalysisDao analysisDao();
    public abstract DeletedRecordDao deletedRecordDao();

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getInstance(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "pancreatic_database")
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
