package com.example.vego.auth;

import android.os.Bundle;
import android.widget.EditText;

import com.example.vego.R;
import com.example.vego.common.BaseActivity;
import com.example.vego.common.UserSession;
import com.example.vego.common.ui.VeGoDialog;

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

        String password = textOf(etPassword);
        boolean validRule = password.length() >= 8
                && password.matches(".*[A-Za-z].*")
                && password.matches(".*\\d.*");
        if (!validRule) {
            etPassword.setError(getString(R.string.register_error_password_rule));
            etPassword.requestFocus();
            return;
        }
        if (!password.equals(textOf(etConfirm))) {
            etConfirm.setError(getString(R.string.register_error_password_match));
            etConfirm.requestFocus();
            return;
        }

        // Bước prototype: chưa gọi API, lưu tạm vào phiên rồi quay về Đăng nhập như Figma
        UserSession session = UserSession.get();
        session.update(textOf(etFullName), textOf(etPhone), textOf(etEmail),
                session.getBirthDate(), null);
        session.setPassword(password);
        VeGoDialog.with(this)
                .type(VeGoDialog.Type.SUCCESS)
                .title(R.string.register_success_title)
                .message(getString(R.string.register_success_message, session.getGivenName()))
                .cancelable(false)
                .primary(R.string.register_go_login, this::finish)
                .show();
    }
}
