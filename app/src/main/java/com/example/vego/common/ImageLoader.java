package com.example.vego.common;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.vego.data.api.ApiClient;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Tải ảnh qua mạng (ảnh đại diện) bằng OkHttp, có cache trong bộ nhớ. */
public final class ImageLoader {

    public interface Listener {
        void onResult(@Nullable Bitmap bitmap);
    }

    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private ImageLoader() { }

    /** Tải ảnh vào ImageView; listener nhận null nếu lỗi (để hiện chữ viết tắt thay thế). */
    public static void load(String url, ImageView target, Listener listener) {
        target.setTag(url);
        Bitmap cached = CACHE.get(url);
        if (cached != null) {
            target.setImageBitmap(cached);
            listener.onResult(cached);
            return;
        }
        ApiClient.http().newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                MAIN.post(() -> deliver(url, target, null, listener));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                Bitmap bitmap = null;
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        bitmap = BitmapFactory.decodeStream(body.byteStream());
                    }
                } catch (RuntimeException ignored) {
                    // ảnh hỏng -> trả null
                }
                if (bitmap != null) CACHE.put(url, bitmap);
                Bitmap result = bitmap;
                MAIN.post(() -> deliver(url, target, result, listener));
            }
        });
    }

    private static void deliver(String url, ImageView target, @Nullable Bitmap bitmap, Listener listener) {
        if (!url.equals(target.getTag())) return; // ImageView đã được dùng cho ảnh khác
        if (bitmap != null) target.setImageBitmap(bitmap);
        listener.onResult(bitmap);
    }
}
