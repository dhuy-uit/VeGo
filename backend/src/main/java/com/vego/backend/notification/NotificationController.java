package com.vego.backend.notification;

import com.vego.backend.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Thông báo")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @Operation(summary = "Thông báo của tôi (mới nhất trước) + số chưa đọc")
    @GetMapping
    public NotificationService.Inbox inbox(@AuthenticationPrincipal AuthUser me) {
        return service.inbox(me.id());
    }

    @Operation(summary = "Số thông báo chưa đọc (chấm đỏ ở Trang chủ)")
    @GetMapping("/unread-count")
    public Map<String, Long> unread(@AuthenticationPrincipal AuthUser me) {
        return Map.of("unreadCount", service.unread(me.id()));
    }

    @Operation(summary = "Đánh dấu một thông báo đã đọc")
    @PostMapping("/{id}/read")
    public NotificationService.Item read(@AuthenticationPrincipal AuthUser me, @PathVariable long id) {
        return service.markRead(me.id(), id);
    }

    @Operation(summary = "Đánh dấu tất cả đã đọc")
    @PostMapping("/read-all")
    public Map<String, Object> readAll(@AuthenticationPrincipal AuthUser me) {
        int changed = service.markAllRead(me.id());
        return Map.of("updated", changed, "unreadCount", 0);
    }
}
