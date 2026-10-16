package com.example.vego.auth;

import android.os.Bundle;
import android.widget.EditText;

import com.example.vego.R;
import com.example.vego.common.BaseActivity;
import com.example.vego.common.ui.VeGoDialog;

/** Figma: "Tài khoản · Lấy lại mật khẩu". */
public class ForgotPasswordActivity extends BaseActivity {

    private EditText etAccount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setScreenContent(R.layout.activity_forgot_password);

        etAccount = findViewById(R.id.et_account);

        setupBackButton(this::finish);
        onClick(R.id.tv_back_to_login, v -> finish());
        onClick(R.id.btn_send, v -> {
            if (!requireText(etAccount)) return;
            // Bước prototype: chỉ báo đã gửi, chưa gửi email thật
            VeGoDialog.with(this)
                    .type(VeGoDialog.Type.SUCCESS)
                    .title(R.string.forgot_sent_title)
                    .message(getString(R.string.forgot_sent_message, textOf(etAccount)))
                    .primary(R.string.forgot_back_to_login, this::finish)
                    .secondary(R.string.action_close, null)
                    .show();
        });
    }
}
