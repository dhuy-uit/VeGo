package com.example.vego.account;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;


import com.example.vego.R;
import com.example.vego.auth.LoginActivity;
import com.example.vego.common.BaseActivity;
import com.example.vego.common.UserSession;
import com.example.vego.common.ui.VeGoDialog;
import com.example.vego.data.CoinWallet;
import com.example.vego.support.SupportChatActivity;
import com.example.vego.tickets.NotificationsActivity;
import com.example.vego.wallet.WalletActivity;

/** Figma: "Tài khoản · Thông tin cá nhân" — tab Tài khoản. */
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
        // Cập nhật lại sau khi chỉnh sửa hồ sơ
        UserSession user = UserSession.get();
        tvFullName.setText(user.getFullName());
        tvPhone.setText(user.getPhone());
        tvEmail.setText(user.getEmail());
        bindAvatar(user.getAvatarUri(), user.getInitials(), tvInitials, ivAvatar);
        // Số dư Ví xu thay đổi sau khi điểm danh / chơi game / đổi voucher
        ((TextView) findViewById(R.id.tv_wallet_balance)).setText(
                getString(R.string.xu_amount, CoinWallet.coins(CoinWallet.get().balance())));
    }

    private void confirmLogout() {
        VeGoDialog.with(this)
                .type(VeGoDialog.Type.DANGER)
                .title(R.string.profile_logout_confirm_title)
                .message(R.string.profile_logout_confirm_message)
                .primary(R.string.profile_logout, () -> openAsRoot(LoginActivity.class))
                .secondary(R.string.action_cancel, null)
                .show();
    }

    /** Hiện ảnh đại diện nếu có, nếu không thì hiện chữ viết tắt. */
    static void bindAvatar(Uri uri, String initials, TextView tvInitials, ImageView ivAvatar) {
        tvInitials.setText(initials);
        if (uri != null) {
            ivAvatar.setImageURI(uri);
            ivAvatar.setVisibility(View.VISIBLE);
            tvInitials.setVisibility(View.INVISIBLE);
        } else {
            ivAvatar.setImageDrawable(null);
            ivAvatar.setVisibility(View.GONE);
            tvInitials.setVisibility(View.VISIBLE);
        }
    }
}
