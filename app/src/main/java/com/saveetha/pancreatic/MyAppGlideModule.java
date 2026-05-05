package com.saveetha.pancreatic;

import android.content.Context;
import androidx.annotation.NonNull;
import com.bumptech.glide.Glide;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.module.AppGlideModule;
import java.io.InputStream;

// 🔥 ADDED: This entire file configures Glide to use our authenticated network client.
@GlideModule
public final class MyAppGlideModule extends AppGlideModule {
    @Override
    public void registerComponents(@NonNull Context context, @NonNull Glide glide, @NonNull Registry registry) {
        // Use the centralized, authenticated OkHttpClient from ApiClient
        OkHttpUrlLoader.Factory factory = new OkHttpUrlLoader.Factory(ApiClient.getOkHttpClient(context));
        registry.replace(GlideUrl.class, InputStream.class, factory);
    }
}
