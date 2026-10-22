package com.vego.backend.auth;

import com.vego.backend.auth.dto.AuthResponse;
import com.vego.backend.auth.dto.ForgotPasswordRequest;
import com.vego.backend.auth.dto.LoginRequest;
import com.vego.backend.auth.dto.MessageResponse;
import com.vego.backend.auth.dto.RefreshRequest;
import com.vego.backend.auth.dto.RegisterRequest;
import com.vego.backend.auth.dto.ResetPasswordRequest;
import com.vego.backend.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Xác thực")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Đăng ký tài khoản mới")
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(req));
    }

    @Operation(summary = "Đăng nhập bằng số điện thoại hoặc email")
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req,
                              @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        return authService.login(req, userAgent);
    }

    @Operation(summary = "Đổi refresh token lấy cặp token mới (token cũ bị vô hiệu)")
    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest req,
                                @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        return authService.refresh(req.refreshToken(), userAgent);
    }

    @Operation(summary = "Đăng xuất: thu hồi refresh token của phiên hiện tại")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest req) {
        authService.logout(req.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Gửi link khôi phục mật khẩu qua email")
    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(authService.forgotPassword(req.account()));
    }

    @Operation(summary = "Đặt mật khẩu mới bằng mã trong link khôi phục")
    @PostMapping("/reset-password")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        return authService.resetPassword(req);
    }
}
