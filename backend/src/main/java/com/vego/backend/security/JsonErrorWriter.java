package com.vego.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vego.backend.common.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/** Ghi lỗi JSON ở tầng filter bảo mật (ngoài phạm vi @RestControllerAdvice). */
@Component
public class JsonErrorWriter {

    private final ObjectMapper mapper;

    public JsonErrorWriter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public void write(HttpServletRequest req, HttpServletResponse res, int status, String code, String message)
            throws IOException {
        res.setStatus(status);
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        if (status == 401) {
            res.setHeader("WWW-Authenticate", "Bearer");
        }
        mapper.writeValue(res.getOutputStream(),
                new ApiError(status, code, message, null, req.getRequestURI(), Instant.now()));
    }
}
