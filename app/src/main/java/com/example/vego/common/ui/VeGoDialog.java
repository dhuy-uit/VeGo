package com.example.vego.common.ui;

import android.app.Activity;
import android.app.Dialog;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.example.vego.R;

/**
 * Hộp thoại theo phong cách VéGo, dùng cho các thao tác cần người dùng chú ý:
 * xác nhận (đăng xuất, hủy vé), báo thành công, cảnh báo.
 *
 * <pre>
 * VeGoDialog.with(this)
 *         .type(VeGoDialog.Type.DANGER)
 *         .title("Hủy vé?")
 *         .message("Bạn sẽ được hoàn 256.000đ")
 *         .primary("Xác nhận hủy", this::cancelTicket)
 *         .secondary("Giữ vé", null)
 *         .show();
 * </pre>
 */
public final class VeGoDialog {

    public enum Type {
        /** Thành công: biểu tượng tích xanh. */
        SUCCESS(R.drawable.ic_circle_check_large, R.drawable.bg_result_success, R.color.vego_primary),
        /** Thông tin / hỏi lựa chọn. */
        INFO(R.drawable.ic_info_large, R.drawable.bg_result_success, R.color.vego_primary),
        /** Cảnh báo: biểu tượng chấm than cam. */
        WARNING(R.drawable.ic_circle_alert_large, R.drawable.bg_result_warning, R.color.vego_orange_text),
        /** Hành động không hoàn tác (hủy, đăng xuất): nút chính màu đỏ. */
        DANGER(R.drawable.ic_circle_x, R.drawable.bg_result_danger, R.color.vego_danger);

        final int icon, background, tint;

        Type(int icon, int background, int tint) {
            this.icon = icon;
            this.background = background;
            this.tint = tint;
        }
    }

    private final Activity activity;
    private Type type = Type.INFO;
    private CharSequence title;
    private CharSequence message;
    private CharSequence primaryText;
    @Nullable private Runnable primaryAction;
    private CharSequence secondaryText;
    @Nullable private Runnable secondaryAction;
    private boolean cancelable = true;

    private VeGoDialog(Activity activity) {
        this.activity = activity;
        this.primaryText = activity.getString(R.string.action_ok);
    }

    public static VeGoDialog with(Activity activity) {
        return new VeGoDialog(activity);
    }

    public VeGoDialog type(Type type) { this.type = type; return this; }
    public VeGoDialog title(CharSequence title) { this.title = title; return this; }
    public VeGoDialog title(@StringRes int res) { return title(activity.getString(res)); }
    public VeGoDialog message(CharSequence message) { this.message = message; return this; }
    public VeGoDialog message(@StringRes int res) { return message(activity.getString(res)); }
    public VeGoDialog cancelable(boolean cancelable) { this.cancelable = cancelable; return this; }

    public VeGoDialog primary(CharSequence text, @Nullable Runnable action) {
        this.primaryText = text;
        this.primaryAction = action;
        return this;
    }

    public VeGoDialog primary(@StringRes int res, @Nullable Runnable action) {
        return primary(activity.getString(res), action);
    }

    public VeGoDialog secondary(CharSequence text, @Nullable Runnable action) {
        this.secondaryText = text;
        this.secondaryAction = action;
        return this;
    }

    public VeGoDialog secondary(@StringRes int res, @Nullable Runnable action) {
        return secondary(activity.getString(res), action);
    }

    public Dialog show() {
        Dialog dialog = new Dialog(activity, R.style.VeGo_Dialog);
        dialog.setContentView(R.layout.dialog_vego);
        dialog.setCancelable(cancelable);
        dialog.setCanceledOnTouchOutside(cancelable);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        ImageView icon = dialog.findViewById(R.id.dialog_icon);
        icon.setImageResource(type.icon);
        icon.setBackgroundResource(type.background);
        icon.setColorFilter(ContextCompat.getColor(activity, type.tint));

        TextView tvTitle = dialog.findViewById(R.id.dialog_title);
        tvTitle.setText(title);
        tvTitle.setVisibility(title == null ? View.GONE : View.VISIBLE);

        TextView tvMessage = dialog.findViewById(R.id.dialog_message);
        tvMessage.setText(message);
        tvMessage.setVisibility(message == null ? View.GONE : View.VISIBLE);

        TextView primary = dialog.findViewById(R.id.dialog_primary);
        primary.setText(primaryText);
        if (type == Type.DANGER) {
            primary.setBackgroundResource(R.drawable.bg_button_danger);
        }
        primary.setOnClickListener(v -> {
            dialog.dismiss();
            if (primaryAction != null) primaryAction.run();
        });

        TextView secondary = dialog.findViewById(R.id.dialog_secondary);
        if (secondaryText != null) {
            secondary.setVisibility(View.VISIBLE);
            secondary.setText(secondaryText);
            secondary.setOnClickListener(v -> {
                dialog.dismiss();
                if (secondaryAction != null) secondaryAction.run();
            });
        }

        if (!activity.isFinishing() && !activity.isDestroyed()) dialog.show();
        return dialog;
    }
}
