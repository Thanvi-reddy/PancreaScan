package com.saveetha.pancreatic;

import com.google.gson.annotations.SerializedName;

public class AuthResponse {
    @SerializedName("status")
    public String status;

    @SerializedName("message")
    public String message;

    @SerializedName("user")
    public User user;

    public boolean isSuccess() {
        return "success".equalsIgnoreCase(status);
    }
    
    public static class User {
        @SerializedName("id")
        public int id;
        @SerializedName("name")
        public String name;
        @SerializedName("email")
        public String email;
    }
}
