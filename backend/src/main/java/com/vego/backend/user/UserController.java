package com.vego.backend.user;

import com.vego.backend.auth.dto.AuthResponse;
import com.vego.backend.security.AuthUser;
import com.vego.backend.user.dto.ChangePasswordRequest;
import com.vego.backend.user.dto.UpdateProfileRequest;
import com.vego.backend.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/users/me")
@Tag(name = "Hồ sơ người dùng")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Xem hồ sơ của tôi")
    @GetMapping
    public UserResponse me(@AuthenticationPrincipal AuthUser me) {
        return userService.getProfile(me.id());
    }

    @Operation(summary = "Cập nhật họ tên, số điện thoại, email, ngày sinh")
    @PutMapping
    public UserResponse update(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody UpdateProfileRequest req) {
        return userService.updateProfile(me.id(), req);
    }

    @Operation(summary = "Tải ảnh đại diện (multipart, trường 'file', JPG/PNG tối đa 5 MB)")
    @PostMapping(path = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserResponse uploadAvatar(@AuthenticationPrincipal AuthUser me, @RequestPart("file") MultipartFile file) {
        return userService.updateAvatar(me.id(), file);
    }

    @Operation(summary = "Xóa ảnh đại diện")
    @DeleteMapping("/avatar")
    public UserResponse deleteAvatar(@AuthenticationPrincipal AuthUser me) {
        return userService.deleteAvatar(me.id());
    }

    @Operation(summary = "Đổi mật khẩu (trả về cặp token mới, các phiên khác bị đăng xuất)")
    @PutMapping("/password")
    public AuthResponse changePassword(@AuthenticationPrincipal AuthUser me,
                                       @Valid @RequestBody ChangePasswordRequest req,
                                       @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        return userService.changePassword(me.id(), req, userAgent);
    }
}
