package com.vego.backend.common;

import java.time.Instant;
import java.util.Map;

/**
 * Định dạng lỗi thống nhất cho mọi API.
 * message là câu tiếng Việt app có thể hiện thẳng cho người dùng;
 * fieldErrors chứa lỗi theo từng ô nhập (vd {"email": "Email đã được đăng ký"}).
 */
public record ApiError(
        int status,
        String code,
        String message,
        Map<String, String> fieldErrors,
        String path,
        Instant timestamp) {
}
