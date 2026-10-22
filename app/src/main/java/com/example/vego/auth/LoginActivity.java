package com.example.vego.auth;

import android.os.Build;
import android.os.Bundle;
import android.widget.EditText;

import androidx.annotation.Nullable;

import com.example.vego.MainActivity;
import com.example.vego.R;
import com.example.vego.common.BaseActivity;
import com.example.vego.common.UserSession;
import com.example.vego.common.ui.VeGoBanner;
import com.example.vego.data.api.ApiCallback;
import com.example.vego.data.api.ApiClient;
import com.example.vego.data.api.ApiModels;

/** Figma: "Tài khoản · Đăng nhập". Màn hình mở đầu khi chạy app. */
public class LoginActivity extends BaseActivity {

    /** Tài khoản vừa đăng ký, điền sẵn khi quay lại màn Đăng nhập. */
    @Nullable static String pendingAccount;

    private EditText etAccount;
    private EditText etPassword;

    @Override
    protected Transition transition() {
        return Transition.FADE;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Đã đăng nhập từ lần trước -> vào thẳng Trang chủ
        if (UserSession.get().isLoggedIn() && savedInstanceState == null && isTaskRoot()) {
            openAsRoot(MainActivity.class);
            return;
        }
        setScreenContent(R.layout.activity_login);

        etAccount = findViewById(R.id.et_account);
        etPassword = findViewById(R.id.et_password);
        setupPasswordToggle(etPassword, findViewById(R.id.btn_toggle_password));

        // Theo prototype Figma: mũi tên quay lại -> Trang chủ (xem với tư cách khách)
        setupBackButton(() -> openAsRoot(MainActivity.class));

        onClick(R.id.tv_forgot_password, v -> open(ForgotPasswordActivity.class));
        onClick(R.id.btn_login, v -> login());
        onClick(R.id.tv_register_link, v -> open(RegisterActivity.class));
        onClick(R.id.btn_create_account, v -> open(RegisterActivity.class));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (pendingAccount != null && etAccount != null) {
            etAccount.setText(pendingAccount);
            etPassword.setText("");
            etPassword.requestFocus();
            pendingAccount = null;
        }
    }

    private void login() {
        if (!requireText(etAccount) || !requireText(etPassword)) return;
        setBusy(R.id.btn_login, true);
        ApiClient.api().login(new ApiModels.LoginRequest(textOf(etAccount),
                        etPassword.getText().toString(), Build.MANUFACTURER + " " + Build.MODEL))
                .enqueue(new ApiCallback<ApiModels.AuthResponse>(this) {
                    @Override
                    protected void onSuccess(@Nullable ApiModels.AuthResponse auth) {
                        if (auth == null) return;
                        ApiClient.tokens().save(auth);
                        UserSession.get().set(auth.user);
                        VeGoBanner.post(getString(R.string.welcome_back, UserSession.get().getGivenName()),
                                VeGoBanner.Type.SUCCESS);
                        openAsRoot(MainActivity.class);
                    }

                    @Override
                    protected void onError(ApiModels.ApiError error) {
                        if (!showFieldErrors(error, "account", etAccount, "password", etPassword)) {
                            showError(error);
                        }
                        if (error.is("INVALID_CREDENTIALS")) etPassword.setText("");
                    }

                    @Override
                    protected void onDone() {
                        setBusy(R.id.btn_login, false);
                    }
                });
    }
}
