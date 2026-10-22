package com.example.vego;

import android.app.Application;
import android.content.Intent;

import com.example.vego.auth.LoginActivity;
import com.example.vego.common.UserSession;
import com.example.vego.common.ui.VeGoBanner;
import com.example.vego.data.api.ApiClient;

/** Khởi tạo tầng gọi API khi mở app. */
public class VeGoApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        ApiClient.init(this);
        UserSession.get().restore();

        // Refresh token hết hạn / bị thu hồi -> về màn Đăng nhập
        ApiClient.setSessionListener(() -> {
            UserSession.get().clear();
            VeGoBanner.post(getString(R.string.session_expired), VeGoBanner.Type.WARNING);
            Intent intent = new Intent(this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });
    }
}
