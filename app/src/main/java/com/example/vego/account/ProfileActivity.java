package com.example.vego.account;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.example.vego.R;
import com.example.vego.auth.LoginActivity;
import com.example.vego.common.BaseActivity;
import com.example.vego.common.ImageLoader;
import com.example.vego.common.UserSession;
import com.example.vego.common.ui.VeGoBanner;
import com.example.vego.common.ui.VeGoDialog;
import com.example.vego.data.CoinWallet;
import com.example.vego.data.api.ApiCallback;
import com.example.vego.data.api.ApiClient;
import com.example.vego.data.api.ApiModels;
import com.example.vego.support.SupportChatActivity;
import com.example.vego.tickets.NotificationsActivity;
import com.example.vego.wallet.WalletActivity;

/** Figma: "Tài khoản · Thông tin cá nhân" — tab Tài khoản. Dữ liệu lấy từ GET /api/v1/users/me. */
public class ProfileActivity extends BaseActivity {

    private TextView tvInitials;
    private ImageView ivAvatar;
    private TextView tvFullName;
    private TextView tvPhone;
    private TextView tvEmail;

    @Override
    protected Transition transition() {
        return Transition.FADE;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Khách chưa đăng nhập bấm tab Tài khoản -> mở màn Đăng nhập
        if (!UserSession.get().isLoggedIn()) {
            VeGoBanner.post(getString(R.string.login_required), VeGoBanner.Type.INFO);
            open(LoginActivity.class);
            finish();
            return;
        }
        setScreenContent(R.layout.activity_profile);

        tvInitials = findViewById(R.id.tv_avatar_initials);
        ivAvatar = findViewById(R.id.iv_avatar);
        tvFullName = findViewById(R.id.tv_full_name);
        tvPhone = findViewById(R.id.tv_phone);
        tvEmail = findViewById(R.id.tv_email);

        setupBottomNav(NavTab.ACCOUNT);
        // Theo prototype: mũi tên quay lại -> Trang chủ
        setupBackButton(this::goHome);

        onClick(R.id.btn_edit_profile, v -> open(EditProfileActivity.class));
        onClick(R.id.row_wallet, v -> open(WalletActivity.class));
        onClick(R.id.row_ai_support, v -> open(SupportChatActivity.class));
        onClick(R.id.row_change_password, v -> open(ChangePasswordActivity.class));
        onClick(R.id.row_notifications, v -> open(NotificationsActivity.class));
        onClick(R.id.row_help, v -> showComingSoon(R.string.screen_help));
        onClick(R.id.tv_terms, v -> showComingSoon(R.string.screen_terms));
        onClick(R.id.btn_logout, v -> confirmLogout());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (tvFullName == null) return;
        // Hiện ngay bản đã lưu, rồi lấy bản mới nhất từ server
        bindUser();
        ApiClient.api().me().enqueue(new ApiCallback<ApiModels.User>(this) {
            @Override
            protected void onSuccess(@Nullable ApiModels.User user) {
                if (user == null) return;
                UserSession.get().set(user);
                bindUser();
            }

            @Override
            protected void onError(ApiModels.ApiError error) {
                showError(error);
            }
        });
        // Số dư Ví xu thay đổi sau khi điểm danh / chơi game / đổi voucher
        ((TextView) findViewById(R.id.tv_wallet_balance)).setText(
                getString(R.string.xu_amount, CoinWallet.coins(CoinWallet.get().balance())));
    }

    private void bindUser() {
        UserSession user = UserSession.get();
        tvFullName.setText(user.getFullName());
        tvPhone.setText(user.getPhone());
        tvEmail.setText(user.getEmail());
        bindAvatar(null, user.getAvatarUrl(), user.getInitials(), tvInitials, ivAvatar);
    }

    private void confirmLogout() {
        VeGoDialog.with(this)
                .type(VeGoDialog.Type.DANGER)
                .title(R.string.profile_logout_confirm_title)
                .message(R.string.profile_logout_confirm_message)
                .primary(R.string.profile_logout, this::logout)
                .secondary(R.string.action_cancel, null)
                .show();
    }

    /** Thu hồi refresh token trên server (không cần chờ kết quả) rồi xóa phiên trong máy. */
    private void logout() {
        String refresh = ApiClient.tokens().refreshToken();
        if (refresh != null) {
            ApiClient.api().logout(new ApiModels.RefreshRequest(refresh)).enqueue(new retrofit2.Callback<Void>() {
                @Override
                public void onResponse(retrofit2.Call<Void> call, retrofit2.Response<Void> response) { }

                @Override
                public void onFailure(retrofit2.Call<Void> call, Throwable t) { }
            });
        }
        UserSession.get().clear();
        openAsRoot(LoginActivity.class);
    }

    /**
     * Hiện ảnh đại diện: ưu tiên ảnh vừa chọn trong máy (localUri), sau đó ảnh trên server (remoteUrl),
     * không có thì hiện chữ viết tắt.
     */
    static void bindAvatar(@Nullable Uri localUri, @Nullable String remoteUrl, String initials,
                           TextView tvInitials, ImageView ivAvatar) {
        tvInitials.setText(initials);
        if (localUri != null) {
            ivAvatar.setTag(null);
            ivAvatar.setImageURI(localUri);
            showImage(true, tvInitials, ivAvatar);
        } else if (remoteUrl != null) {
            ImageLoader.load(remoteUrl, ivAvatar, bitmap -> showImage(bitmap != null, tvInitials, ivAvatar));
        } else {
            ivAvatar.setTag(null);
            ivAvatar.setImageDrawable(null);
            showImage(false, tvInitials, ivAvatar);
        }
    }

    private static void showImage(boolean hasImage, TextView tvInitials, ImageView ivAvatar) {
        ivAvatar.setVisibility(hasImage ? View.VISIBLE : View.GONE);
        tvInitials.setVisibility(hasImage ? View.INVISIBLE : View.VISIBLE);
    }
}
