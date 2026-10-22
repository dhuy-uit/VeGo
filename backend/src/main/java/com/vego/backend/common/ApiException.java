package com.vego.backend.common;

import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Lỗi nghiệp vụ có mã HTTP, mã lỗi và thông điệp tiếng Việt. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Map<String, String> fieldErrors;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, Collections.emptyMap());
    }

    public ApiException(HttpStatus status, String code, String message, Map<String, String> fieldErrors) {
        super(message);
        this.status = status;
        this.code = code;
        this.fieldErrors = fieldErrors == null ? Collections.emptyMap() : new LinkedHashMap<>(fieldErrors);
    }

    /** Lỗi dữ liệu ở một ô nhập cụ thể (400). */
    public static ApiException field(String code, String field, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message, Map.of(field, message));
    }

    /** Dữ liệu bị trùng ở một ô nhập (409), vd email đã tồn tại. */
    public static ApiException conflict(String code, String field, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message, Map.of(field, message));
    }

    public static ApiException unauthorized(String code, String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, code, message);
    }

    public static ApiException forbidden(String code, String message) {
        return new ApiException(HttpStatus.FORBIDDEN, code, message);
    }

    public static ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
    public Map<String, String> getFieldErrors() { return fieldErrors; }
}
