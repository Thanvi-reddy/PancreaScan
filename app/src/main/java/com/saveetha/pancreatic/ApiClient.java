package com.saveetha.pancreatic;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    public static final String BASE_URL = "http://14.139.187.229:8081/oct/pancreas/";
    private static Retrofit retrofit;
    private static OkHttpClient okHttpClient;

    // 🔥 ADDED: Made the authenticated OkHttpClient globally accessible.
    public static OkHttpClient getOkHttpClient(Context context) {
        if (okHttpClient == null) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.HEADERS);

            okHttpClient = new OkHttpClient.Builder()
                    .protocols(Arrays.asList(Protocol.HTTP_1_1))
                    .addInterceptor(logging)
                    .addInterceptor(new AuthInterceptor(context))
                    .connectTimeout(300, TimeUnit.SECONDS)
                    .readTimeout(300, TimeUnit.SECONDS)
                    .writeTimeout(300, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .build();
        }
        return okHttpClient;
    }

    public static Retrofit getClient(Context context) {
        if (retrofit == null) {
            Gson gson = new GsonBuilder()
                    .setLenient()
                    .create();

            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(getOkHttpClient(context)) // Use the shared client
                    .addConverterFactory(GsonConverterFactory.create(gson))
                    .build();
        }
        return retrofit;
    }
    // Federated Learning Server URL (Port 8000)
    public static final String FL_BASE_URL = "http://14.139.187.229:8000/"; // Assuming root, or append /oct/pancreas/ if needed structure matches

    public static Retrofit getFederatedClient(Context context) {
        // Create a separate Retrofit instance for Federated Learning calls
        Gson gson = new GsonBuilder()
                .setLenient()
                .create();

        return new Retrofit.Builder()
                .baseUrl(FL_BASE_URL)
                .client(getOkHttpClient(context)) // Share the same OkHttp client for auth headers etc.
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();
    }
}
