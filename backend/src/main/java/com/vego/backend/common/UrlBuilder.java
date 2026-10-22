package com.vego.backend.common;

import com.vego.backend.config.AppProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Tạo URL tuyệt đối (ảnh đại diện, link khôi phục mật khẩu). */
@Component
public class UrlBuilder {

    private final AppProperties props;

    public UrlBuilder(AppProperties props) {
        this.props = props;
    }

    public String absolute(String path) {
        String base = props.publicBaseUrl();
        if (base == null || base.isBlank()) {
            base = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        }
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + (path.startsWith("/") ? path : "/" + path);
    }
}
