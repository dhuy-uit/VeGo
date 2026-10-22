package com.example.vego.data.api;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.example.vego.BuildConfig;
import com.google.gson.Gson;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Authenticator;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Điểm truy cập REST API duy nhất của app (Retrofit + OkHttp).
 * Gọi {@link #init(Context)} một lần trong {@code VeGoApp.onCreate()}.
 * Địa chỉ server lấy từ BuildConfig.API_BASE_URL (cấu hình "vego.apiBaseUrl" trong gradle.properties).
 */
public final class ApiClient {

    /** Gọi /auth/refresh đồng bộ (dùng trong TokenAuthenticator). */
    interface RefreshCaller {
        Response<ApiModels.AuthResponse> refresh(String refreshToken) throws IOException;
    }

    /** Được gọi (trên luồng chính) khi phiên đăng nhập hết hạn hẳn. */
    public interface SessionListener {
        void onSessionExpired();
    }

    private static ApiClient instance;

    private final Gson gson = new Gson();
    private final TokenStore tokens;
    private final OkHttpClient http;
    private final VeGoApi api;
    private final Handler main = new Handler(Looper.getMainLooper());
    @Nullable private SessionListener sessionListener;

    private ApiClient(Context context) {
        tokens = new TokenStore(context, gson);

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(BuildConfig.DEBUG ? HttpLoggingInterceptor.Level.BASIC : HttpLoggingInterceptor.Level.NONE);

        OkHttpClient base = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(new AuthInterceptor(tokens))
                .addInterceptor(logging)
                .build();

        // Client riêng cho /auth/refresh: không có authenticator để tránh vòng lặp
        VeGoApi refreshApi = retrofit(base.newBuilder().authenticator(Authenticator.NONE).build())
                .create(VeGoApi.class);

        http = base.newBuilder()
                .authenticator(new TokenAuthenticator(tokens,
                        rt -> refreshApi.refresh(new ApiModels.RefreshRequest(rt)).execute(),
                        () -> main.post(() -> {
                            if (sessionListener != null) sessionListener.onSessionExpired();
                        })))
                .build();
        api = retrofit(http).create(VeGoApi.class);
    }

    private Retrofit retrofit(OkHttpClient client) {
        return new Retrofit.Builder()
                .baseUrl(BuildConfig.API_BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();
    }

    public static synchronized void init(Context context) {
        if (instance == null) instance = new ApiClient(context.getApplicationContext());
    }

    private static ApiClient get() {
        if (instance == null) throw new IllegalStateException("Chưa gọi ApiClient.init()");
        return instance;
    }

    public static VeGoApi api() { return get().api; }

    public static TokenStore tokens() { return get().tokens; }

    public static OkHttpClient http() { return get().http; }

    public static Gson gson() { return get().gson; }

    public static void setSessionListener(@Nullable SessionListener listener) {
        get().sessionListener = listener;
    }
}
