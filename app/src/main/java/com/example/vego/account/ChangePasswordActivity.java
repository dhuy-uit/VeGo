package com.example.vego.account;

import android.os.Build;
import android.os.Bundle;
import android.widget.EditText;

import androidx.annotation.Nullable;

import com.example.vego.R;
import com.example.vego.common.BaseActivity;
import com.example.vego.common.UserSession;
import com.example.vego.common.ui.VeGoDialog;
import com.example.vego.data.api.ApiCallback;
import com.example.vego.data.api.ApiClient;
import com.example.vego.data.api.ApiModels;

/** Figma: "Tài khoản · Đổi mật khẩu". Gọi PUT /api/v1/users/me/password. */
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

        // Kiểm tra nhanh ở app; mật khẩu hiện tại do server kiểm tra
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

        setBusy(R.id.btn_update_password, true);
        ApiClient.api().changePassword(new ApiModels.ChangePasswordRequest(current, next,
                        Build.MANUFACTURER + " " + Build.MODEL))
                .enqueue(new ApiCallback<ApiModels.AuthResponse>(this) {
                    @Override
                    protected void onSuccess(@Nullable ApiModels.AuthResponse auth) {
                        // Server đã đăng xuất các máy khác và cấp token mới cho máy này
                        if (auth != null) {
                            ApiClient.tokens().save(auth);
                            UserSession.get().set(auth.user);
                        }
                        VeGoDialog.with(ChangePasswordActivity.this)
                                .type(VeGoDialog.Type.SUCCESS)
                                .title(R.string.change_password_success_title)
                                .message(R.string.change_password_success_message)
                                .cancelable(false)
                                .primary(R.string.change_password_back, ChangePasswordActivity.this::finish)
                                .show();
                    }

                    @Override
                    protected void onError(ApiModels.ApiError error) {
                        if (!showFieldErrors(error, "currentPassword", etCurrent, "newPassword", etNew)) {
                            showError(error);
                        }
                    }

                    @Override
                    protected void onDone() {
                        setBusy(R.id.btn_update_password, false);
                    }
                });
    }
}
