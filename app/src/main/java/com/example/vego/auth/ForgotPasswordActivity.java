package com.example.vego.auth;

import android.os.Bundle;
import android.widget.EditText;

import androidx.annotation.Nullable;

import com.example.vego.R;
import com.example.vego.common.BaseActivity;
import com.example.vego.common.ui.VeGoDialog;
import com.example.vego.data.api.ApiCallback;
import com.example.vego.data.api.ApiClient;
import com.example.vego.data.api.ApiModels;

/** Figma: "Tài khoản · Lấy lại mật khẩu". Server gửi link đặt lại mật khẩu qua email. */
public class ForgotPasswordActivity extends BaseActivity {

    private EditText etAccount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setScreenContent(R.layout.activity_forgot_password);

        etAccount = findViewById(R.id.et_account);

        setupBackButton(this::finish);
        onClick(R.id.tv_back_to_login, v -> finish());
        onClick(R.id.btn_send, v -> send());
    }

    private void send() {
        if (!requireText(etAccount)) return;
        setBusy(R.id.btn_send, true);
        ApiClient.api().forgotPassword(new ApiModels.ForgotPasswordRequest(textOf(etAccount)))
                .enqueue(new ApiCallback<ApiModels.MessageResponse>(this) {
                    @Override
                    protected void onSuccess(@Nullable ApiModels.MessageResponse body) {
                        VeGoDialog.with(ForgotPasswordActivity.this)
                                .type(VeGoDialog.Type.SUCCESS)
                                .title(R.string.forgot_sent_title)
                                .message(getString(R.string.forgot_sent_message, textOf(etAccount)))
                                .primary(R.string.forgot_back_to_login, ForgotPasswordActivity.this::finish)
                                .secondary(R.string.action_close, null)
                                .show();
                    }

                    @Override
                    protected void onError(ApiModels.ApiError error) {
                        if (!showFieldErrors(error, "account", etAccount)) showError(error);
                    }

                    @Override
                    protected void onDone() {
                        setBusy(R.id.btn_send, false);
                    }
                });
    }
}
