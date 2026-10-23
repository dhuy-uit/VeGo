package com.vego.backend.notification;

import com.vego.backend.common.ApiException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** Tạo và đọc thông báo của người dùng. */
@Service
public class NotificationService {

    /** Số thông báo trả về tối đa (mới nhất trước). */
    private static final int LIMIT = 50;

    public record Item(long id, String kind, String title, String body, String ticketCode, boolean read,
                       Instant createdAt) { }

    public record Inbox(long unreadCount, List<Item> items) { }

    private final NotificationRepository repo;
    private final Clock clock;

    public NotificationService(NotificationRepository repo, Clock clock) {
        this.repo = repo;
        this.clock = clock;
    }

    @Transactional
    public Notification push(long userId, Notification.Kind kind, String title, String body, String ticketCode) {
        Notification n = new Notification(userId, kind, title, body, ticketCode);
        n.setCreatedAt(clock.instant());
        return repo.save(n);
    }

    @Transactional(readOnly = true)
    public Inbox inbox(long userId) {
        List<Item> items = repo.findByUserIdOrderByCreatedAtDescIdDesc(userId, PageRequest.of(0, LIMIT)).stream()
                .map(NotificationService::toItem).toList();
        return new Inbox(repo.countByUserIdAndReadAtIsNull(userId), items);
    }

    @Transactional(readOnly = true)
    public long unread(long userId) {
        return repo.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public Item markRead(long userId, long id) {
        Notification n = repo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("NOTIFICATION_NOT_FOUND", "Không tìm thấy thông báo"));
        if (n.getReadAt() == null) n.setReadAt(clock.instant());
        return toItem(n);
    }

    @Transactional
    public int markAllRead(long userId) {
        return repo.markAllRead(userId, clock.instant());
    }

    private static Item toItem(Notification n) {
        return new Item(n.getId(), n.getKind().name(), n.getTitle(), n.getBody(), n.getTicketCode(),
                n.getReadAt() != null, n.getCreatedAt());
    }
}
