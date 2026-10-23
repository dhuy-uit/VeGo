package com.example.vego.data;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import com.example.vego.R;
import com.example.vego.data.api.ApiModels;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Thông báo trong app lấy từ server (GET /api/v1/notifications), giữ trong bộ nhớ. */
public final class NotificationStore {

    public enum Kind { REMINDER, TICKET, PROMO, REVIEW, WALLET }

    public static class Item {
        public final long id;
        public final Kind kind;
        public final String title;
        public final String body;
        @Nullable public final Instant createdAt;
        /** Mã vé liên quan (để mở vé / đánh giá), có thể null. */
        @Nullable public final String ticketCode;
        public boolean read;

        Item(ApiModels.NotificationItem n) {
            id = n.id;
            kind = parseKind(n.kind);
            title = n.title;
            body = n.body;
            ticketCode = n.ticketCode;
            read = n.read;
            createdAt = parseInstant(n.createdAt);
        }

        @DrawableRes
        public int icon() {
            switch (kind) {
                case REMINDER: return R.drawable.ic_clock;
                case TICKET: return R.drawable.ic_ticket_check;
                case PROMO: return R.drawable.ic_gift;
                case WALLET: return R.drawable.ic_coins;
                default: return R.drawable.ic_star;
            }
        }

        /** "Vừa xong", "15 phút trước", "Hôm qua · 08:30", "21/09/2026 · 09:00". */
        public String time() {
            return relativeTime(createdAt);
        }
    }

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DMY_HM = DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm");

    private static final NotificationStore INSTANCE = new NotificationStore();

    public static NotificationStore get() {
        return INSTANCE;
    }

    private final List<Item> items = new ArrayList<>();
    private long unread = 0;

    private NotificationStore() { }

    public void setInbox(@Nullable ApiModels.Inbox inbox) {
        items.clear();
        unread = 0;
        if (inbox == null) return;
        if (inbox.items != null) for (ApiModels.NotificationItem n : inbox.items) items.add(new Item(n));
        unread = inbox.unreadCount;
    }

    public void setUnread(long count) {
        unread = count;
    }

    public void clear() {
        items.clear();
        unread = 0;
    }

    public List<Item> all() {
        return Collections.unmodifiableList(items);
    }

    public int unreadCount() {
        return (int) unread;
    }

    /** Đánh dấu đã đọc trong máy (server được báo riêng). */
    public void markRead(Item item) {
        if (!item.read) {
            item.read = true;
            unread = Math.max(0, unread - 1);
        }
    }

    public void markAllRead() {
        for (Item i : items) i.read = true;
        unread = 0;
    }

    static Kind parseKind(@Nullable String s) {
        try {
            return s == null ? Kind.TICKET : Kind.valueOf(s);
        } catch (IllegalArgumentException e) {
            return Kind.TICKET;
        }
    }

    @Nullable
    public static Instant parseInstant(@Nullable String iso) {
        if (iso == null) return null;
        try {
            return Instant.parse(iso);
        } catch (RuntimeException e) {
            try {
                return java.time.OffsetDateTime.parse(iso).toInstant();
            } catch (RuntimeException ignored) {
                return null;
            }
        }
    }

    public static String relativeTime(@Nullable Instant at) {
        if (at == null) return "";
        long seconds = (System.currentTimeMillis() - at.toEpochMilli()) / 1000;
        if (seconds < 60) return "Vừa xong";
        if (seconds < 3600) return (seconds / 60) + " phút trước";
        ZonedDateTime t = at.atZone(VN);
        LocalDate today = LocalDate.now(VN);
        if (t.toLocalDate().equals(today)) return "Hôm nay · " + HM.format(t);
        if (t.toLocalDate().equals(today.minusDays(1))) return "Hôm qua · " + HM.format(t);
        return DMY_HM.format(t);
    }
}
