package com.vego.backend.auth.dto;

/** Quy tắc mật khẩu, giống hệt kiểm tra phía app. */
public final class PasswordRules {

    private PasswordRules() { }

    /** Đăng ký: tối thiểu 8 ký tự, có chữ và số. */
    public static final String BASIC = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$";
    public static final String BASIC_MESSAGE = "Mật khẩu cần tối thiểu 8 ký tự, gồm chữ và số";

    /** Đổi / đặt lại mật khẩu: tối thiểu 8 ký tự, có chữ hoa, chữ thường và số. */
    public static final String STRONG = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,72}$";
    public static final String STRONG_MESSAGE = "Mật khẩu mới cần tối thiểu 8 ký tự, gồm chữ hoa, chữ thường và số";
}
