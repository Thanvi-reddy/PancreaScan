package com.saveetha.pancreatic;

import java.util.Map;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.PartMap;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.Streaming;

public interface ApiService {

    // Unified Sync Endpoint
    @FormUrlEncoded
    @POST("sync.php")
    Call<ResponseBody> syncScan(
        @Field("action") String action, // "upload_scan"
        @Field("user_email") String userEmail,
        @Field("image") String base64Image,
        @Field("result") String result,
        @Field("confidence") float confidence,
        @Field("patient_id") String patientId,
        @Field("patient_name") String patientName,
        @Field("timestamp") String timestamp,
        @Field("box_left") float boxLeft,
        @Field("box_top") float boxTop,
        @Field("box_right") float boxRight,
        @Field("box_bottom") float boxBottom
    );

    @FormUrlEncoded
    @POST("sync.php")
    Call<ResponseBody> deleteScan(
        @Field("action") String action, // "delete_scan"
        @Field("user_email") String userEmail,
        @Field("timestamp") String timestamp
    );

    @FormUrlEncoded
    @POST("sync.php")
    Call<ResponseBody> getHistory(
        @Field("action") String action, // "get_history"
        @Field("user_email") String userEmail
    );

    @FormUrlEncoded
    @POST("sync.php")
    Call<ResponseBody> clearHistory(
        @Field("action") String action, // "clear_history"
        @Field("user_email") String userEmail
    );

    // Unified Auth Endpoint (auth.php)
    @FormUrlEncoded
    @POST("auth.php")
    Call<ResponseBody> auth(
        @Field("action") String action, // signup, login, request_password_reset, reset_password, delete_account
        @Field("email") String email,
        @Field("password") String password,
        @Field("name") String name,
        @Field("otp") String otp,
        @Field("new_password") String newPassword
    );

    // Federated Learning Endpoints (fl.php)
    @FormUrlEncoded
    @POST("fl.php")
    Call<ResponseBody> uploadGradients(
        @Field("action") String action, // "upload_gradients"
        @Field("client_id") String clientId,
        @Field("gradients") String gradientsJson // Send as JSON string
    );

    @FormUrlEncoded
    @POST("fl.php")
    Call<ResponseBody> getGlobalModel(
        @Field("action") String action // "get_global_model"
    );
}
