package com.saveetha.pancreatic;

import com.google.gson.annotations.SerializedName;

public class LoginResponse {
    @SerializedName("success")
    public boolean success;

    @SerializedName("message")
    public String message;

    // This annotation is the critical fix. It maps the JSON key "user_id"
    // from the server to the "userId" field in this class.
    @SerializedName("user_id")
    public int userId;

    @SerializedName("name")
    public String name;

    @SerializedName("email")
    public String email;

    @SerializedName("phone")
    public String phone;
}
