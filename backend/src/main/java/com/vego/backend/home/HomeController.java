package com.vego.backend.home;

import com.vego.backend.home.dto.HomeResponse;
import com.vego.backend.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/home")
@Tag(name = "Trang chủ")
public class HomeController {

    private final HomeService homeService;

    public HomeController(HomeService homeService) {
        this.homeService = homeService;
    }

    @Operation(summary = "Dữ liệu Trang chủ (không bắt buộc đăng nhập; có token thì chào theo tên)")
    @GetMapping
    public ResponseEntity<HomeResponse> home(@AuthenticationPrincipal AuthUser me) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .body(homeService.getHome(me == null ? null : me.id()));
    }
}
