package com.example.vego.auth;

import android.os.Bundle;
import android.widget.EditText;

import com.example.vego.MainActivity;
import com.example.vego.R;
import com.example.vego.common.BaseActivity;
import com.example.vego.common.UserSession;
import com.example.vego.common.ui.VeGoBanner;

/** Figma: "Tài khoản · Đăng nhập". Màn hình mở đầu khi chạy app. */
public class LoginActivity extends BaseActivity {

    private EditText etAccount;
    private EditText etPassword;

    @Override
    protected Transition transition() {
        return Transition.FADE;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setScreenContent(R.layout.activity_login);

        etAccount = findViewById(R.id.et_account);
        etPassword = findViewById(R.id.et_password);
        setupPasswordToggle(etPassword, findViewById(R.id.btn_toggle_password));

        // Theo prototype Figma: mũi tên quay lại -> Trang chủ
        setupBackButton(() -> openAsRoot(MainActivity.class));

        onClick(R.id.tv_forgot_password, v -> open(ForgotPasswordActivity.class));
        onClick(R.id.btn_login, v -> login());
        onClick(R.id.tv_register_link, v -> open(RegisterActivity.class));
        onClick(R.id.btn_create_account, v -> open(RegisterActivity.class));
    }

    private void login() {
        if (!requireText(etAccount) || !requireText(etPassword)) return;
        // Bước prototype: chưa gọi API, chỉ chuyển sang Trang chủ kèm lời chào
        VeGoBanner.post(getString(R.string.welcome_back, UserSession.get().getGivenName()), VeGoBanner.Type.SUCCESS);
        openAsRoot(MainActivity.class);
    }
}
