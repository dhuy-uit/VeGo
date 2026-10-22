package com.example.vego.auth;

import android.os.Bundle;
import android.util.Patterns;
import android.widget.EditText;

import androidx.annotation.Nullable;

import com.example.vego.R;
import com.example.vego.common.BaseActivity;
import com.example.vego.common.UserSession;
import com.example.vego.common.ui.VeGoDialog;
import com.example.vego.data.api.ApiCallback;
import com.example.vego.data.api.ApiClient;
import com.example.vego.data.api.ApiModels;

/** Figma: "Tài khoản · Đăng ký". */
public class RegisterActivity extends BaseActivity {

    private EditText etFullName;
    private EditText etPhone;
    private EditText etEmail;
    private EditText etPassword;
    private EditText etConfirm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setScreenContent(R.layout.activity_register);

        etFullName = findViewById(R.id.et_full_name);
        etPhone = findViewById(R.id.et_phone);
        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        etConfirm = findViewById(R.id.et_confirm_password);

        setupPasswordToggle(etPassword, findViewById(R.id.btn_toggle_password));
        setupPasswordToggle(etConfirm, findViewById(R.id.btn_toggle_confirm));

        // Theo prototype: quay lại -> Đăng nhập (màn hình trước đó)
        setupBackButton(this::finish);
        onClick(R.id.tv_login_link, v -> finish());
        onClick(R.id.btn_register, v -> register());
    }

    private void register() {
        if (!requireText(etFullName) || !requireText(etPhone)
                || !requireText(etEmail) || !requireText(etPassword)) return;

        // Kiểm tra nhanh ở app (server vẫn kiểm tra lại)
        if (textOf(etPhone).replaceAll("\\D", "").length() != 10) {
            etPhone.setError(getString(R.string.error_invalid_phone));
            etPhone.requestFocus();
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(textOf(etEmail)).matches()) {
            etEmail.setError(getString(R.string.error_invalid_email));
            etEmail.requestFocus();
            return;
        }
        String password = etPassword.getText().toString();
        boolean validRule = password.length() >= 8
                && password.matches(".*[A-Za-z].*")
                && password.matches(".*\\d.*");
        if (!validRule) {
            etPassword.setError(getString(R.string.register_error_password_rule));
            etPassword.requestFocus();
            return;
        }
        if (!password.equals(etConfirm.getText().toString())) {
            etConfirm.setError(getString(R.string.register_error_password_match));
            etConfirm.requestFocus();
            return;
        }

        setBusy(R.id.btn_register, true);
        ApiClient.api().register(new ApiModels.RegisterRequest(
                        textOf(etFullName), textOf(etPhone), textOf(etEmail), password))
                .enqueue(new ApiCallback<ApiModels.User>(this) {
                    @Override
                    protected void onSuccess(@Nullable ApiModels.User user) {
                        String name = user != null && user.givenName != null
                                ? user.givenName : UserSession.givenNameOf(textOf(etFullName));
                        LoginActivity.pendingAccount = textOf(etPhone);
                        VeGoDialog.with(RegisterActivity.this)
                                .type(VeGoDialog.Type.SUCCESS)
                                .title(R.string.register_success_title)
                                .message(getString(R.string.register_success_message, name))
                                .cancelable(false)
                                .primary(R.string.register_go_login, RegisterActivity.this::finish)
                                .show();
                    }

                    @Override
                    protected void onError(ApiModels.ApiError error) {
                        if (!showFieldErrors(error, "fullName", etFullName, "phone", etPhone,
                                "email", etEmail, "password", etPassword)) {
                            showError(error);
                        }
                    }

                    @Override
                    protected void onDone() {
                        setBusy(R.id.btn_register, false);
                    }
                });
    }
}
