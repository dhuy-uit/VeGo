package com.vego.backend.common;

/** Chuẩn hóa số điện thoại Việt Nam về dạng 10 chữ số bắt đầu bằng 0. */
public final class Phones {

    private Phones() { }

    /** "090 123 4567", "+84 90 123 4567" -> "0901234567". Không hợp lệ thì trả null. */
    public static String normalize(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("\\D", "");
        if (digits.startsWith("84") && digits.length() == 11) {
            digits = "0" + digits.substring(2);
        }
        return digits.matches("0\\d{9}") ? digits : null;
    }

    public static boolean looksLikeEmail(String account) {
        return account != null && account.contains("@");
    }
}
