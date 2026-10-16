package com.example.vego.common.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.example.vego.R;

/**
 * Dòng thông tin "Nhãn  ·  Giá trị" xuất hiện ở nhiều màn (Chi tiết chuyến, Thanh toán...).
 * Là một LinearLayout ngang gồm 2 TextView, dùng trong XML:
 * <pre>
 * &lt;com.example.vego.common.widget.InfoRowView
 *     app:irLabel="Giá vé" app:irValue="320.000đ" app:irHighlight="true" /&gt;
 * </pre>
 */
public class InfoRowView extends LinearLayout {

    private final TextView label;
    private final TextView value;

    public InfoRowView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setBaselineAligned(true);
        LayoutInflater.from(context).inflate(R.layout.merge_info_row, this, true);
        label = findViewById(R.id.info_label);
        value = findViewById(R.id.info_value);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.InfoRowView);
        setLabel(a.getString(R.styleable.InfoRowView_irLabel));
        setValue(a.getString(R.styleable.InfoRowView_irValue));
        setHighlight(a.getBoolean(R.styleable.InfoRowView_irHighlight, false));
        a.recycle();
    }

    public void setLabel(@Nullable CharSequence text) {
        label.setText(text == null ? "" : text + "  ·  ");
    }

    public void setValue(@Nullable CharSequence text) {
        value.setText(text);
    }

    /** Giá trị in đậm màu xanh (tổng tiền, trạng thái...). */
    public void setHighlight(boolean highlight) {
        value.setTypeface(ResourcesCompat.getFont(getContext(),
                highlight ? R.font.inter_bold : R.font.inter_medium));
        value.setTextColor(ContextCompat.getColor(getContext(),
                highlight ? R.color.vego_primary : R.color.vego_text));
    }
}
