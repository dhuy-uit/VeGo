package com.vego.backend.auth.dto;

/** Phản hồi chỉ có thông điệp. devResetToken chỉ xuất hiện khi bật EXPOSE_RESET_TOKEN (để test). */
public record MessageResponse(String message, String devResetToken) {

    public static MessageResponse of(String message) {
        return new MessageResponse(message, null);
    }
}
