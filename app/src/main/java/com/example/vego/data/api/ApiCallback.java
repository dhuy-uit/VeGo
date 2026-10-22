package com.example.vego.data.api;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.lang.ref.WeakReference;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Callback Retrofit gắn với một màn hình: bỏ qua kết quả nếu màn hình đã đóng,
 * đọc lỗi JSON của server thành {@link ApiModels.ApiError} có thông điệp tiếng Việt.
 */
public abstract class ApiCallback<T> implements Callback<T> {

    private final WeakReference<Activity> host;

    protected ApiCallback(Activity host) {
        this.host = new WeakReference<>(host);
    }

    protected abstract void onSuccess(@Nullable T body);

    protected abstract void onError(ApiModels.ApiError error);

    /** Luôn chạy sau onSuccess / onError (vd bật lại nút bấm). */
    protected void onDone() { }

    @Override
    public final void onResponse(@NonNull Call<T> call, @NonNull Response<T> response) {
        if (!alive()) return;
        try {
            if (response.isSuccessful()) {
                onSuccess(response.body());
            } else {
                ApiModels.ApiError error = parse(response);
                // Phiên hết hạn đã được xử lý chung (chuyển về Đăng nhập) -> không báo lỗi lần nữa
                if (!isSessionError(error)) onError(error);
            }
        } finally {
            onDone();
        }
    }

    @Override
    public final void onFailure(@NonNull Call<T> call, @NonNull Throwable t) {
        if (!alive() || call.isCanceled()) return;
        try {
            onError(t instanceof IOException
                    ? ApiModels.ApiError.of(0, "NETWORK", "Không kết nối được máy chủ. Kiểm tra mạng và thử lại.")
                    : ApiModels.ApiError.of(0, "CLIENT", "Có lỗi xảy ra, vui lòng thử lại."));
        } finally {
            onDone();
        }
    }

    private boolean alive() {
        Activity a = host.get();
        return a != null && !a.isFinishing() && !a.isDestroyed();
    }

    private static boolean isSessionError(ApiModels.ApiError e) {
        return e.status == 401 && e.code != null
                && (e.code.equals("UNAUTHORIZED") || e.code.startsWith("TOKEN_") || e.code.startsWith("REFRESH_"));
    }

    static ApiModels.ApiError parse(Response<?> response) {
        ApiModels.ApiError parsed = null;
        try (ResponseBody body = response.errorBody()) {
            if (body != null) {
                parsed = ApiClient.gson().fromJson(body.charStream(), ApiModels.ApiError.class);
            }
        } catch (RuntimeException ignored) {
            // không phải JSON
        }
        if (parsed == null || parsed.message == null) {
            String msg = response.code() >= 500
                    ? "Máy chủ đang bận, vui lòng thử lại sau."
                    : "Có lỗi xảy ra (mã " + response.code() + ").";
            parsed = ApiModels.ApiError.of(response.code(), "HTTP_" + response.code(), msg);
        }
        parsed.status = response.code();
        return parsed;
    }
}
