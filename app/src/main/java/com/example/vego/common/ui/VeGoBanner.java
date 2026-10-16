package com.example.vego.common.ui;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.vego.R;

/**
 * Thanh thông báo nhanh, trượt xuống từ trên cùng rồi tự ẩn sau ~3 giây (chạm để ẩn ngay).
 * Dùng cho thông báo không cần người dùng bấm gì: đã lưu, đã áp dụng bộ lọc, sắp hết giờ giữ ghế...
 *
 * <p>{@link #post} giữ thông báo lại để hiện ở màn hình kế tiếp — hữu ích khi vừa lưu xong
 * đã đóng màn hiện tại (vd lưu hồ sơ rồi quay về màn Tài khoản).</p>
 */
public final class VeGoBanner {

    public enum Type {
        SUCCESS(R.drawable.ic_circle_check, R.color.vego_primary),
        INFO(R.drawable.ic_info, R.color.vego_primary),
        WARNING(R.drawable.ic_circle_alert, R.color.vego_orange_text),
        ERROR(R.drawable.ic_circle_alert, R.color.vego_danger);

        final int icon, tint;

        Type(int icon, int tint) {
            this.icon = icon;
            this.tint = tint;
        }
    }

    private static final long SHOW_MS = 2800;
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());

    @Nullable private static CharSequence pendingText;
    private static Type pendingType = Type.INFO;

    private VeGoBanner() { }

    /** Hiện ngay trên màn hình hiện tại. */
    public static void show(Activity activity, CharSequence text, Type type) {
        ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        View old = decor.findViewWithTag(VeGoBanner.class);
        if (old != null) decor.removeView(old);

        View banner = LayoutInflater.from(activity).inflate(R.layout.view_banner, decor, false);
        banner.setTag(VeGoBanner.class);
        ImageView icon = banner.findViewById(R.id.banner_icon);
        icon.setImageResource(type.icon);
        icon.setColorFilter(ContextCompat.getColor(activity, type.tint));
        ((TextView) banner.findViewById(R.id.banner_text)).setText(text);

        float density = activity.getResources().getDisplayMetrics().density;
        int top = 0;
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(decor);
        if (insets != null) {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            top = bars.top;
        }
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
        int side = Math.round(16 * density);
        lp.setMargins(side, top + Math.round(8 * density), side, 0);
        decor.addView(banner, lp);

        // Trượt xuống + hiện dần
        banner.setAlpha(0f);
        banner.setTranslationY(-80 * density);
        banner.animate().alpha(1f).translationY(0f).setDuration(260).start();

        Runnable hide = () -> hide(decor, banner, density);
        banner.setOnClickListener(v -> hide.run());
        HANDLER.postDelayed(hide, SHOW_MS);
    }

    public static void show(Activity activity, int textRes, Type type) {
        show(activity, activity.getString(textRes), type);
    }

    /** Hẹn hiện ở màn hình kế tiếp (gọi trước khi finish()). */
    public static void post(CharSequence text, Type type) {
        pendingText = text;
        pendingType = type;
    }

    /** Gọi trong onResume của mọi màn hình (BaseActivity đã làm sẵn). */
    public static void showPending(Activity activity) {
        if (pendingText == null) return;
        CharSequence text = pendingText;
        pendingText = null;
        // Đợi màn hình vẽ xong để có thông tin vùng thanh trạng thái
        activity.getWindow().getDecorView().post(() -> show(activity, text, pendingType));
    }

    private static void hide(ViewGroup decor, View banner, float density) {
        if (banner.getParent() == null) return;
        banner.animate().alpha(0f).translationY(-80 * density).setDuration(200)
                .withEndAction(() -> decor.removeView(banner)).start();
    }
}
