package com.example.vego.account;

import android.app.DatePickerDialog;
import android.net.Uri;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;

import com.example.vego.R;
import com.example.vego.common.BaseActivity;
import com.example.vego.common.UserSession;
import com.example.vego.common.ui.VeGoBanner;
import com.example.vego.common.ui.VeGoDialog;

import androidx.activity.OnBackPressedCallback;

import java.util.Calendar;

/** Figma: "Tài khoản · Chỉnh sửa cá nhân". */
public class EditProfileActivity extends BaseActivity {

    private EditText etFullName;
    private EditText etPhone;
    private EditText etEmail;
    private TextView tvBirthDate;
    private TextView tvInitials;
    private ImageView ivAvatar;

    private Calendar birthDate;
    @Nullable private Uri avatarUri;

    /** Bộ chọn ảnh của hệ thống (không cần xin quyền đọc bộ nhớ). */
    private final ActivityResultLauncher<PickVisualMediaRequest> pickAvatar =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {
                    avatarUri = uri;
                    showAvatar();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setScreenContent(R.layout.activity_edit_profile);

        etFullName = findViewById(R.id.et_full_name);
        etPhone = findViewById(R.id.et_phone);
        etEmail = findViewById(R.id.et_email);
        tvBirthDate = findViewById(R.id.tv_birth_date);
        tvInitials = findViewById(R.id.tv_avatar_initials);
        ivAvatar = findViewById(R.id.iv_avatar);

        UserSession user = UserSession.get();
        etFullName.setText(user.getFullName());
        etPhone.setText(user.getPhone());
        etEmail.setText(user.getEmail());
        birthDate = user.getBirthDate();
        avatarUri = user.getAvatarUri();
        tvBirthDate.setText(UserSession.formatDate(birthDate));
        showAvatar();

        // Theo prototype: quay lại, Lưu, Hủy đều trở về màn Tài khoản (hỏi lại nếu đã sửa)
        setupBackButton(this::leave);
        onClick(R.id.btn_cancel, v -> leave());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                leave();
            }
        });
        onClick(R.id.btn_save, v -> save());
        onClick(R.id.avatar, v -> chooseAvatar());
        onClick(R.id.tv_change_avatar, v -> chooseAvatar());
        onClick(R.id.field_birth_date, v -> pickBirthDate());
    }

    /** Có thay đổi chưa lưu? */
    private boolean isDirty() {
        UserSession u = UserSession.get();
        return !textOf(etFullName).equals(u.getFullName())
                || !textOf(etPhone).equals(u.getPhone())
                || !textOf(etEmail).equals(u.getEmail())
                || birthDate.getTimeInMillis() != u.getBirthDate().getTimeInMillis()
                || avatarUri != u.getAvatarUri();
    }

    private void leave() {
        if (!isDirty()) {
            finish();
            return;
        }
        VeGoDialog.with(this)
                .type(VeGoDialog.Type.WARNING)
                .title(R.string.edit_profile_discard_title)
                .message(R.string.edit_profile_discard_message)
                .primary(R.string.edit_profile_discard, this::finish)
                .secondary(R.string.edit_profile_keep, null)
                .show();
    }

    private void chooseAvatar() {
        pickAvatar.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    private void showAvatar() {
        String name = textOf(etFullName);
        String initials = name.isEmpty() ? UserSession.get().getInitials() : initialsOf(name);
        ProfileActivity.bindAvatar(avatarUri, initials, tvInitials, ivAvatar);
    }

    private void pickBirthDate() {
        DatePickerDialog dialog = new DatePickerDialog(this,
                (view, year, month, day) -> {
                    birthDate.set(year, month, day);
                    tvBirthDate.setText(UserSession.formatDate(birthDate));
                },
                birthDate.get(Calendar.YEAR),
                birthDate.get(Calendar.MONTH),
                birthDate.get(Calendar.DAY_OF_MONTH));
        dialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        dialog.show();
    }

    private void save() {
        if (!requireText(etFullName) || !requireText(etPhone) || !requireText(etEmail)) return;

        String digits = textOf(etPhone).replaceAll("\\D", "");
        if (digits.length() != 10) {
            etPhone.setError(getString(R.string.error_invalid_phone));
            etPhone.requestFocus();
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(textOf(etEmail)).matches()) {
            etEmail.setError(getString(R.string.error_invalid_email));
            etEmail.requestFocus();
            return;
        }

        UserSession.get().update(textOf(etFullName), textOf(etPhone), textOf(etEmail), birthDate, avatarUri);
        VeGoBanner.post(getString(R.string.edit_profile_saved), VeGoBanner.Type.SUCCESS);
        finish();
    }

    private static String initialsOf(String name) {
        String[] parts = name.trim().split("\\s+");
        if (parts.length >= 2) {
            return (parts[parts.length - 2].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
        }
        return parts[0].substring(0, 1).toUpperCase();
    }
}
