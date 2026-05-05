package com.saveetha.pancreatic;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Interceptor;
import okhttp3.Response;

public class AuthInterceptor implements Interceptor {

    private static final String TAG = "AuthInterceptor";
    private static final String SECURE_PREF_NAME = "secure_user_session";
    private final Context context;

    public AuthInterceptor(Context context) {
        this.context = context.getApplicationContext();
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Response response = chain.proceed(chain.request());

        // If server returns a 401 Unauthorized, the user's session is invalid.
        if (response.code() == 401) {
            Log.e(TAG, "Received 401 Unauthorized. Forcing global logout.");
            forceLogout();
        }
        return response;
    }

    private void forceLogout() {
        // Use a background thread to clear the database
        ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
        databaseExecutor.execute(() -> {
            AppDatabase.getInstance(context).analysisDao().deleteAll();
        });

        try {
            // Clear all encrypted user session data
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            SharedPreferences securePrefs = EncryptedSharedPreferences.create(
                SECURE_PREF_NAME, masterKeyAlias, context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
            securePrefs.edit().clear().apply();

        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "Could not clear EncryptedSharedPreferences during force logout.", e);
        }
        
        // Redirect to the login screen and clear all previous activities
        Intent intent = new Intent(context, MainActivity2.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);
    }
}
