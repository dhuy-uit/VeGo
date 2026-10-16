package com.example.vego.account;

import android.os.Bundle;
import android.widget.EditText;

import com.example.vego.R;
import com.example.vego.common.BaseActivity;
import com.example.vego.common.UserSession;
import com.example.vego.common.ui.VeGoDialog;

/** Figma: "Tài khoản · Đổi mật khẩu". */
public class ChangePasswordActivity extends BaseActivity {

    private EditText etCurrent;
    private EditText etNew;
    private EditText etConfirm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setScreenContent(R.layout.activity_change_password);

        etCurrent = findViewById(R.id.et_current_password);
        etNew = findViewById(R.id.et_new_password);
        etConfirm = findViewById(R.id.et_confirm_password);

        setupPasswordToggle(etCurrent, findViewById(R.id.btn_toggle_current));
        setupPasswordToggle(etNew, findViewById(R.id.btn_toggle_new));
        setupPasswordToggle(etConfirm, findViewById(R.id.btn_toggle_confirm));

        // Theo prototype: quay lại và "Quay lại tài khoản" -> màn Tài khoản
        setupBackButton(this::finish);
        onClick(R.id.btn_back_to_account, v -> finish());
        onClick(R.id.btn_update_password, v -> updatePassword());
    }

    private void updatePassword() {
        if (!requireText(etCurrent) || !requireText(etNew) || !requireText(etConfirm)) return;

        String current = etCurrent.getText().toString();
        String next = etNew.getText().toString();

        if (!current.equals(UserSession.get().getPassword())) {
            etCurrent.setError(getString(R.string.error_wrong_password));
            etCurrent.requestFocus();
            return;
        }
        boolean strong = next.length() >= 8
                && next.matches(".*[a-z].*")
                && next.matches(".*[A-Z].*")
                && next.matches(".*\\d.*");
        if (!strong) {
            etNew.setError(getString(R.string.change_password_error_rule));
            etNew.requestFocus();
            return;
        }
        if (next.equals(current)) {
            etNew.setError(getString(R.string.change_password_error_same));
            etNew.requestFocus();
            return;
        }
        if (!next.equals(etConfirm.getText().toString())) {
            etConfirm.setError(getString(R.string.register_error_password_match));
            etConfirm.requestFocus();
            return;
        }

        UserSession.get().setPassword(next);
        VeGoDialog.with(this)
                .type(VeGoDialog.Type.SUCCESS)
                .title(R.string.change_password_success_title)
                .message(R.string.change_password_success_message)
                .cancelable(false)
                .primary(R.string.change_password_back, this::finish)
                .show();
    }
}
