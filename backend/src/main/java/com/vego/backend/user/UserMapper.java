package com.vego.backend.user;

import com.vego.backend.common.UrlBuilder;
import com.vego.backend.user.dto.UserResponse;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    private final UrlBuilder urls;

    public UserMapper(UrlBuilder urls) {
        this.urls = urls;
    }

    public UserResponse toResponse(User u) {
        String avatarUrl = u.getAvatarFile() == null ? null
                : urls.absolute("/api/v1/files/avatars/" + u.getAvatarFile());
        return new UserResponse(u.getId(), u.getFullName(), u.givenName(), u.getPhone(), u.getEmail(),
                u.getBirthDate(), avatarUrl, u.getRole().name(), u.getCreatedAt());
    }
}
