package com.example.vego.data;

import java.text.NumberFormat;
import java.util.Calendar;
import java.util.Locale;

/** Định dạng tiền, giờ, ngày theo kiểu Việt Nam. */
public final class Format {

    private static final Locale VI = new Locale("vi", "VN");
    private static final String[] DAY_NAMES = {
            "Chủ nhật", "Thứ hai", "Thứ ba", "Thứ tư", "Thứ năm", "Thứ sáu", "Thứ bảy"
    };

    private Format() { }

    /** 320000 -> "320.000đ" */
    public static String money(int amount) {
        return NumberFormat.getIntegerInstance(VI).format(amount) + "đ";
    }

    /** 1320 -> "22:00" */
    public static String time(int minutesOfDay) {
        return String.format(Locale.ROOT, "%02d:%02d", minutesOfDay / 60, minutesOfDay % 60);
    }

    /** 4.8 -> "4,8" */
    public static String rating(double value) {
        return String.format(VI, "%.1f", value);
    }

    /** "18/10/2026" */
    public static String date(Calendar c) {
        return String.format(Locale.ROOT, "%02d/%02d/%d",
                c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR));
    }

    /** "18/10" */
    public static String shortDate(Calendar c) {
        return String.format(Locale.ROOT, "%02d/%02d",
                c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1);
    }

    /** "Chủ nhật, 18/10/2026" */
    public static String longDate(Calendar c) {
        return DAY_NAMES[c.get(Calendar.DAY_OF_WEEK) - 1] + ", " + date(c);
    }

    /** Đếm ngược "mm:ss" */
    public static String countdown(long millis) {
        long totalSeconds = Math.max(0, (millis + 999) / 1000);
        return String.format(Locale.ROOT, "%02d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    /** Cộng thêm số ngày vào một ngày (không đổi ngày gốc). */
    public static Calendar plusDays(Calendar c, int days) {
        Calendar copy = (Calendar) c.clone();
        copy.add(Calendar.DAY_OF_MONTH, days);
        return copy;
    }
}
