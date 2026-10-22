package com.example.vego.account;

import android.app.DatePickerDialog;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Patterns;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;

import com.example.vego.R;
import com.example.vego.common.BaseActivity;
import com.example.vego.common.UserSession;
import com.example.vego.common.ui.VeGoBanner;
import com.example.vego.common.ui.VeGoDialog;
import com.example.vego.data.api.ApiCallback;
import com.example.vego.data.api.ApiClient;
import com.example.vego.data.api.ApiModels;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

/**
 * Figma: "Tài khoản · Chỉnh sửa cá nhân".
 * Lưu = PUT /api/v1/users/me, nếu đổi ảnh thì tải lên POST /api/v1/users/me/avatar.
 */
public class EditProfileActivity extends BaseActivity {

    /** Ảnh gửi lên server được thu nhỏ về tối đa 720px và nén JPEG. */
    private static final int AVATAR_MAX_PX = 720;

    private EditText etFullName;
    private EditText etPhone;
    private EditText etEmail;
    private TextView tvBirthDate;
    private TextView tvInitials;
    private ImageView ivAvatar;

    @Nullable private Calendar birthDate;
    /** Ảnh mới chọn trong máy, null = giữ ảnh cũ. */
    @Nullable private Uri pickedAvatar;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    /** Bộ chọn ảnh của hệ thống (không cần xin quyền đọc bộ nhớ). */
    private final ActivityResultLauncher<PickVisualMediaRequest> pickAvatar =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {
                    pickedAvatar = uri;
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
        showBirthDate();
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }

    /** Có thay đổi chưa lưu? */
    private boolean isDirty() {
        UserSession u = UserSession.get();
        return !textOf(etFullName).equals(u.getFullName())
                || !textOf(etPhone).replaceAll("\\D", "").equals(u.getPhoneDigits())
                || !textOf(etEmail).equals(u.getEmail())
                || !Objects.equals(isoOrNull(birthDate), isoOrNull(u.getBirthDate()))
                || pickedAvatar != null;
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
        String initials = name.isEmpty() ? UserSession.get().getInitials() : UserSession.initialsOf(name);
        ProfileActivity.bindAvatar(pickedAvatar, UserSession.get().getAvatarUrl(), initials, tvInitials, ivAvatar);
    }

    private void showBirthDate() {
        tvBirthDate.setText(birthDate == null
                ? getString(R.string.birth_date_empty)
                : UserSession.formatDate(birthDate));
    }

    private void pickBirthDate() {
        Calendar initial = birthDate;
        if (initial == null) {
            initial = Calendar.getInstance();
            initial.clear();
            initial.set(2000, Calendar.JANUARY, 1);
        }
        DatePickerDialog dialog = new DatePickerDialog(this,
                (view, year, month, day) -> {
                    Calendar picked = Calendar.getInstance();
                    picked.clear();
                    picked.set(year, month, day);
                    birthDate = picked;
                    showBirthDate();
                },
                initial.get(Calendar.YEAR),
                initial.get(Calendar.MONTH),
                initial.get(Calendar.DAY_OF_MONTH));
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

        setBusy(R.id.btn_save, true);
        ApiModels.UpdateProfileRequest body = new ApiModels.UpdateProfileRequest(
                textOf(etFullName), digits, textOf(etEmail), isoOrNull(birthDate));
        ApiClient.api().updateProfile(body).enqueue(new ApiCallback<ApiModels.User>(this) {
            @Override
            protected void onSuccess(@Nullable ApiModels.User user) {
                UserSession.get().set(user);
                if (pickedAvatar != null) {
                    uploadAvatar(pickedAvatar);
                } else {
                    done();
                }
            }

            @Override
            protected void onError(ApiModels.ApiError error) {
                setBusy(R.id.btn_save, false);
                if (!showFieldErrors(error, "fullName", etFullName, "phone", etPhone, "email", etEmail)) {
                    showError(error);
                }
            }
        });
    }

    /** Thu nhỏ + nén ảnh ở luồng phụ rồi tải lên server. */
    private void uploadAvatar(Uri uri) {
        io.execute(() -> {
            byte[] jpeg;
            try {
                jpeg = compress(uri);
            } catch (IOException | RuntimeException e) {
                main.post(() -> {
                    setBusy(R.id.btn_save, false);
                    VeGoBanner.show(this, getString(R.string.avatar_reading_failed), VeGoBanner.Type.ERROR);
                });
                return;
            }
            MultipartBody.Part part = MultipartBody.Part.createFormData("file", "avatar.jpg",
                    RequestBody.create(jpeg, MediaType.get("image/jpeg")));
            main.post(() -> ApiClient.api().uploadAvatar(part).enqueue(new ApiCallback<ApiModels.User>(this) {
                @Override
                protected void onSuccess(@Nullable ApiModels.User user) {
                    UserSession.get().set(user);
                    done();
                }

                @Override
                protected void onError(ApiModels.ApiError error) {
                    setBusy(R.id.btn_save, false);
                    showError(error);
                }
            }));
        });
    }

    private byte[] compress(Uri uri) throws IOException {
        ImageDecoder.Source source = ImageDecoder.createSource(getContentResolver(), uri);
        // ImageDecoder tự xoay ảnh theo EXIF và thu nhỏ ngay khi giải mã
        Bitmap bitmap = ImageDecoder.decodeBitmap(source, (decoder, info, src) -> {
            int w = info.getSize().getWidth();
            int h = info.getSize().getHeight();
            float scale = Math.min(1f, (float) AVATAR_MAX_PX / Math.max(w, h));
            decoder.setTargetSize(Math.max(1, Math.round(w * scale)), Math.max(1, Math.round(h * scale)));
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
        });
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out);
        bitmap.recycle();
        return out.toByteArray();
    }

    private void done() {
        VeGoBanner.post(getString(R.string.edit_profile_saved), VeGoBanner.Type.SUCCESS);
        finish();
    }

    @Nullable
    private static String isoOrNull(@Nullable Calendar date) {
        return date == null ? null : UserSession.toIsoDate(date);
    }
}
