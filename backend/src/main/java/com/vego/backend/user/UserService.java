package com.vego.backend.user;

import com.vego.backend.auth.TokenService;
import com.vego.backend.auth.dto.AuthResponse;
import com.vego.backend.common.ApiException;
import com.vego.backend.common.Phones;
import com.vego.backend.user.dto.ChangePasswordRequest;
import com.vego.backend.user.dto.UpdateProfileRequest;
import com.vego.backend.user.dto.UserResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class UserService {

    private final UserRepository users;
    private final UserMapper mapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final AvatarStorage avatars;
    private final Clock clock;

    public UserService(UserRepository users, UserMapper mapper, PasswordEncoder passwordEncoder,
                       TokenService tokenService, AvatarStorage avatars, Clock clock) {
        this.users = users;
        this.mapper = mapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.avatars = avatars;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public UserResponse getProfile(long userId) {
        return mapper.toResponse(load(userId));
    }

    @Transactional
    public UserResponse updateProfile(long userId, UpdateProfileRequest req) {
        User user = load(userId);
        String phone = Phones.normalize(req.phone());
        if (phone == null) {
            throw ApiException.field("INVALID_PHONE", "phone", "Số điện thoại cần 10 chữ số");
        }
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        if (req.birthDate() != null && req.birthDate().isBefore(LocalDate.of(1900, 1, 1))) {
            throw ApiException.field("INVALID_BIRTH_DATE", "birthDate", "Ngày sinh chưa hợp lệ");
        }

        Map<String, String> errors = new LinkedHashMap<>();
        if (users.existsByPhoneAndIdNot(phone, userId)) errors.put("phone", "Số điện thoại đã được tài khoản khác sử dụng");
        if (users.existsByEmailAndIdNot(email, userId)) errors.put("email", "Email đã được tài khoản khác sử dụng");
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_EXISTS", errors.values().iterator().next(), errors);
        }

        user.setFullName(req.fullName().trim().replaceAll("\\s+", " "));
        user.setPhone(phone);
        user.setEmail(email);
        user.setBirthDate(req.birthDate());
        return mapper.toResponse(users.saveAndFlush(user));
    }

    @Transactional
    public UserResponse updateAvatar(long userId, MultipartFile file) {
        User user = load(userId);
        String old = user.getAvatarFile();
        user.setAvatarFile(avatars.save(userId, file));
        users.saveAndFlush(user);
        avatars.delete(old);
        return mapper.toResponse(user);
    }

    @Transactional
    public UserResponse deleteAvatar(long userId) {
        User user = load(userId);
        avatars.delete(user.getAvatarFile());
        user.setAvatarFile(null);
        return mapper.toResponse(users.saveAndFlush(user));
    }

    /** Đổi mật khẩu: thu hồi mọi phiên cũ (đăng xuất các máy khác) và cấp token mới cho máy hiện tại. */
    @Transactional
    public AuthResponse changePassword(long userId, ChangePasswordRequest req, String userAgent) {
        User user = load(userId);
        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw ApiException.field("WRONG_PASSWORD", "currentPassword", "Mật khẩu hiện tại chưa đúng");
        }
        if (req.currentPassword().equals(req.newPassword())) {
            throw ApiException.field("SAME_PASSWORD", "newPassword", "Mật khẩu mới phải khác mật khẩu hiện tại");
        }
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        user.setPasswordChangedAt(clock.instant());
        users.saveAndFlush(user);
        tokenService.revokeAll(userId);
        return tokenService.issue(user, null, req.device() != null ? req.device() : userAgent);
    }

    private User load(long userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("USER_NOT_FOUND", "Tài khoản không còn tồn tại"));
        if (user.getStatus() == User.Status.LOCKED) {
            throw ApiException.forbidden("ACCOUNT_LOCKED", "Tài khoản đã bị khóa. Vui lòng liên hệ tổng đài hỗ trợ");
        }
        return user;
    }
}
