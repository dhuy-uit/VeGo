package com.example.vego.common.ui;

import android.app.Activity;
import android.app.Dialog;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.example.vego.R;

import java.util.List;

/**
 * Hộp thoại chọn một mục trong danh sách có sẵn (điểm đi, điểm đến). Người dùng không gõ tự do
 * nên không thể chọn nơi VéGo chưa hỗ trợ.
 */
public final class VeGoChoiceDialog {

    public interface OnChoice {
        void onChoice(String value);
    }

    private VeGoChoiceDialog() { }

    public static Dialog show(Activity activity, CharSequence title, List<String> items,
                              @Nullable String selected, OnChoice listener) {
        Dialog dialog = new Dialog(activity, R.style.VeGo_Dialog);
        dialog.setContentView(R.layout.dialog_choice);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        ((TextView) dialog.findViewById(R.id.choice_title)).setText(title);
        LinearLayout list = dialog.findViewById(R.id.choice_list);
        LayoutInflater inflater = LayoutInflater.from(activity);
        for (String item : items) {
            View row = inflater.inflate(R.layout.item_option_row, list, false);
            ((TextView) row.findViewById(R.id.tv_option_title)).setText(item);
            row.findViewById(R.id.tv_option_subtitle).setVisibility(View.GONE);
            ((ImageView) row.findViewById(R.id.iv_radio)).setImageResource(
                    item.equals(selected) ? R.drawable.ic_circle_check : R.drawable.ic_circle);
            row.setOnClickListener(v -> {
                dialog.dismiss();
                listener.onChoice(item);
            });
            list.addView(row);
        }
        dialog.findViewById(R.id.choice_close).setOnClickListener(v -> dialog.dismiss());

        // Danh sách dài thì cuộn, tối đa khoảng nửa chiều cao màn hình
        ScrollView scroll = dialog.findViewById(R.id.choice_scroll);
        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        int maxHeight = metrics.heightPixels / 2;
        list.measure(View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.UNSPECIFIED);
        if (list.getMeasuredHeight() > maxHeight) {
            scroll.getLayoutParams().height = maxHeight;
        }

        if (!activity.isFinishing() && !activity.isDestroyed()) dialog.show();
        return dialog;
    }
}
