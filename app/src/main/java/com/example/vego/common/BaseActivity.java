package com.example.vego.common;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.annotation.AnimRes;
import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.ImageViewCompat;

import com.example.vego.MainActivity;
import com.example.vego.R;
import com.example.vego.account.ProfileActivity;
import com.example.vego.common.ui.VeGoBanner;
import com.example.vego.data.api.ApiModels;
import com.example.vego.explore.SuggestActivity;
import com.example.vego.tickets.MyTicketsActivity;

/**
 * Lớp cha cho mọi màn hình VéGo.
 * Gom các việc lặp lại: tràn viền (edge-to-edge), nút quay lại, chuyển màn hình,
 * hiện/ẩn mật khẩu và thanh điều hướng dưới.
 */
public abstract class BaseActivity extends AppCompatActivity {

    /** Các tab của thanh điều hướng dưới. */
    public enum NavTab { HOME, TICKETS, SUGGEST, ACCOUNT }

    /**
     * Kiểu hiệu ứng khi mở / đóng màn hình.
     * PUSH dùng hiệu ứng mặc định trong theme (VeGo.WindowAnimation).
     */
    public enum Transition {
        /** Đi tiếp trong một luồng: trượt ngang có chiều sâu. */
        PUSH(0, 0, 0, 0),
        /** Chuyển tab, về Trang chủ, đăng nhập/đăng xuất: hiện dần. */
        FADE(R.anim.fade_in, R.anim.fade_out, R.anim.fade_in, R.anim.fade_out),
        /** Bảng chọn (vd Bộ lọc): trượt lên từ dưới, đóng thì trượt xuống. */
        SHEET(R.anim.sheet_enter, R.anim.hold, R.anim.hold, R.anim.sheet_exit),
        /** Màn kết quả (thành công / thất bại / hết giờ): phóng nhẹ từ giữa. */
        RESULT(R.anim.result_enter, R.anim.fade_out, R.anim.fade_in, R.anim.fade_out);

        final int openEnter, openExit, closeEnter, closeExit;

        Transition(@AnimRes int openEnter, @AnimRes int openExit,
                   @AnimRes int closeEnter, @AnimRes int closeExit) {
            this.openEnter = openEnter;
            this.openExit = openExit;
            this.closeEnter = closeEnter;
            this.closeExit = closeExit;
        }
    }

    /** Màn hình con ghi đè để chọn kiểu hiệu ứng của riêng nó. */
    protected Transition transition() {
        return Transition.PUSH;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Transition t = transition();
        if (t == Transition.PUSH) return;
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, t.openEnter, t.openExit);
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, t.closeEnter, t.closeExit);
        } else {
            overridePendingTransitionCompat(t.openEnter, t.openExit);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Hiện thông báo được hẹn từ màn hình trước (vd "Đã lưu thông tin")
        VeGoBanner.showPending(this);
    }

    @Override
    public void finish() {
        super.finish();
        Transition t = transition();
        if (t != Transition.PUSH && Build.VERSION.SDK_INT < 34) {
            overridePendingTransitionCompat(t.closeEnter, t.closeExit);
        }
    }

    @SuppressWarnings("deprecation")
    private void overridePendingTransitionCompat(@AnimRes int enter, @AnimRes int exit) {
        overridePendingTransition(enter, exit);
    }

    /** Gắn layout và chừa chỗ cho thanh trạng thái, thanh điều hướng hệ thống và bàn phím. */
    protected void setScreenContent(@LayoutRes int layoutRes) {
        EdgeToEdge.enable(this,
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT));
        setContentView(layoutRes);

        View root = findViewById(R.id.root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    // ---------- Điều hướng ----------

    /** Nút mũi tên quay lại trên thanh tiêu đề. */
    protected void setupBackButton(Runnable action) {
        View back = findViewById(R.id.btn_back);
        if (back != null) back.setOnClickListener(v -> action.run());
    }

    /** Mở màn hình mới, màn hiện tại vẫn nằm trong ngăn xếp. */
    protected void open(Class<?> screen) {
        startActivity(new Intent(this, screen));
    }

    /** Mở màn hình và xóa toàn bộ ngăn xếp (dùng cho Trang chủ, Đăng nhập lại...). */
    protected void openAsRoot(Class<?> screen) {
        Intent intent = new Intent(this, screen);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    /** Về Trang chủ, đóng các màn hình đang mở phía trên nó. */
    protected void goHome() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    /** Chuyển sang một tab khác Trang chủ; Trang chủ luôn nằm dưới cùng ngăn xếp. */
    protected void switchToTab(Class<?> screen) {
        startActivity(new Intent(this, screen));
        if (!(this instanceof MainActivity)) finish();
    }

    /**
     * Màn hình cần đăng nhập (Vé của tôi, Thông báo, Ví xu...): khách thì chuyển sang Đăng nhập.
     * Gọi đầu onCreate, trả về false nếu đã chuyển đi (onCreate nên return ngay).
     */
    protected boolean requireLogin() {
        if (UserSession.get().isLoggedIn()) return true;
        VeGoBanner.post(getString(R.string.login_required_feature), VeGoBanner.Type.INFO);
        startActivity(new Intent(this, com.example.vego.auth.LoginActivity.class));
        finish();
        return false;
    }

    /** Thông báo cho các nút dẫn tới màn hình chưa làm. */
    protected void showComingSoon(@StringRes int screenName) {
        VeGoBanner.show(this, getString(R.string.coming_soon, getString(screenName)), VeGoBanner.Type.INFO);
    }

    /** Thông báo nhanh dạng thanh trượt xuống (thay cho Toast mặc định). */
    protected void toast(String message) {
        VeGoBanner.show(this, message, VeGoBanner.Type.INFO);
    }

    protected void onClick(@IdRes int viewId, View.OnClickListener listener) {
        View view = findViewById(viewId);
        if (view != null) view.setOnClickListener(listener);
    }

    // ---------- Biểu mẫu ----------

    /** Bấm vào icon con mắt để hiện/ẩn mật khẩu. */
    protected void setupPasswordToggle(EditText field, ImageView toggle) {
        toggle.setOnClickListener(v -> {
            boolean hidden = field.getTransformationMethod() instanceof PasswordTransformationMethod;
            field.setTransformationMethod(hidden
                    ? HideReturnsTransformationMethod.getInstance()
                    : PasswordTransformationMethod.getInstance());
            toggle.setImageResource(hidden ? R.drawable.ic_eye : R.drawable.ic_eye_off);
            field.setSelection(field.getText().length());
        });
    }

    /** Báo lỗi nếu ô trống. Trả về true khi ô đã có nội dung. */
    protected boolean requireText(EditText field) {
        if (field.getText().toString().trim().isEmpty()) {
            field.setError(getString(R.string.error_required));
            field.requestFocus();
            return false;
        }
        return true;
    }

    protected String textOf(EditText field) {
        return field.getText().toString().trim();
    }

    // ---------- Gọi API ----------

    /** Khóa nút trong lúc chờ server, đổi chữ thành "Đang xử lý..."; busy=false để trả lại như cũ. */
    protected void setBusy(@IdRes int buttonId, boolean busy) {
        View view = findViewById(buttonId);
        if (view == null) return;
        if (view instanceof TextView) {
            TextView button = (TextView) view;
            if (busy) {
                if (button.getTag(R.id.tag_original_text) == null) {
                    button.setTag(R.id.tag_original_text, button.getText());
                }
                button.setText(R.string.action_processing);
            } else {
                Object original = button.getTag(R.id.tag_original_text);
                if (original instanceof CharSequence) button.setText((CharSequence) original);
                button.setTag(R.id.tag_original_text, null);
            }
        }
        view.setEnabled(!busy);
        view.setAlpha(busy ? 0.6f : 1f);
    }

    /** Hiện thông điệp lỗi của server bằng thanh thông báo đỏ. */
    protected void showError(ApiModels.ApiError error) {
        VeGoBanner.show(this, error.message, VeGoBanner.Type.ERROR);
    }

    /**
     * Gắn lỗi theo từng ô nhập mà server trả về.
     * pairs: "tên trường", EditText, "tên trường", EditText...
     * Trả về true nếu có ít nhất một ô bị báo lỗi (ô đầu tiên được focus).
     */
    protected boolean showFieldErrors(ApiModels.ApiError error, Object... pairs) {
        boolean any = false;
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            String message = error.field((String) pairs[i]);
            EditText field = (EditText) pairs[i + 1];
            if (message != null && field != null) {
                field.setError(message);
                if (!any) field.requestFocus();
                any = true;
            }
        }
        return any;
    }

    // ---------- Thanh điều hướng dưới ----------

    /** Tô màu tab đang chọn và gắn sự kiện cho 4 tab. */
    protected void setupBottomNav(NavTab selected) {
        styleNavItem(R.id.nav_home_pill, R.id.nav_home_icon, R.id.nav_home_label, selected == NavTab.HOME);
        styleNavItem(R.id.nav_tickets_pill, R.id.nav_tickets_icon, R.id.nav_tickets_label, selected == NavTab.TICKETS);
        styleNavItem(R.id.nav_suggest_pill, R.id.nav_suggest_icon, R.id.nav_suggest_label, selected == NavTab.SUGGEST);
        styleNavItem(R.id.nav_account_pill, R.id.nav_account_icon, R.id.nav_account_label, selected == NavTab.ACCOUNT);

        onClick(R.id.nav_home, v -> { if (selected != NavTab.HOME) goHome(); });
        onClick(R.id.nav_tickets, v -> { if (selected != NavTab.TICKETS) switchToTab(MyTicketsActivity.class); });
        onClick(R.id.nav_suggest, v -> { if (selected != NavTab.SUGGEST) switchToTab(SuggestActivity.class); });
        onClick(R.id.nav_account, v -> { if (selected != NavTab.ACCOUNT) switchToTab(ProfileActivity.class); });
    }

    private void styleNavItem(@IdRes int pillId, @IdRes int iconId, @IdRes int labelId, boolean active) {
        LinearLayout pill = findViewById(pillId);
        ImageView icon = findViewById(iconId);
        TextView label = findViewById(labelId);
        if (pill == null || icon == null || label == null) return;

        int color = ContextCompat.getColor(this, active ? R.color.vego_primary : R.color.vego_text_secondary);
        pill.setBackgroundResource(active ? R.drawable.bg_nav_pill_active : 0);
        ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(color));
        label.setTextColor(color);
        label.setTypeface(ResourcesCompat.getFont(this, active ? R.font.inter_bold : R.font.inter_regular));
        pill.setSelected(active);
    }
}
