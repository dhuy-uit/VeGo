package com.example.vego.data;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import com.example.vego.R;

import java.util.ArrayList;
import java.util.List;

/** Danh sách thông báo trong app (màn "Thông báo"), dữ liệu mẫu theo Figma. */
public final class NotificationStore {

    public enum Kind { REMINDER, TICKET, PROMO, REVIEW }

    public static class Item {
        public final Kind kind;
        public final String title;
        public final String body;
        public final String time;
        /** Mã vé liên quan (để mở vé / đánh giá), có thể null. */
        @Nullable public final String ticketCode;
        public boolean read;

        Item(Kind kind, String title, String body, String time, @Nullable String ticketCode, boolean read) {
            this.kind = kind;
            this.title = title;
            this.body = body;
            this.time = time;
            this.ticketCode = ticketCode;
            this.read = read;
        }

        @DrawableRes
        public int icon() {
            switch (kind) {
                case REMINDER: return R.drawable.ic_clock;
                case TICKET: return R.drawable.ic_ticket_check;
                case PROMO: return R.drawable.ic_gift;
                default: return R.drawable.ic_star;
            }
        }
    }

    private static final NotificationStore INSTANCE = new NotificationStore();

    public static NotificationStore get() {
        return INSTANCE;
    }

    private final List<Item> items = new ArrayList<>();

    private NotificationStore() {
        items.add(new Item(Kind.REMINDER, "Nhắc lịch: xe chạy lúc 22:00",
                "Chuyến TP. Hồ Chí Minh → Đà Lạt ngày 18/10. Vui lòng đến Bến xe Miền Đông mới lúc 21:30.",
                "Lịch nhắc · 18/10/2026, 20:00", "VG18102605", false));
        items.add(new Item(Kind.TICKET, "Vé của bạn đã được xác nhận",
                "Mã vé VG18102605 · Ghế A05. Thanh toán MoMo thành công: 320.000đ.",
                "Vừa xong", "VG18102605", false));
        items.add(new Item(Kind.PROMO, "Mùa thu, lên Đà Lạt thôi!",
                "Ưu đãi minh họa: giảm 15%, tối đa 50.000đ với mã VEGIN15. Xem điều kiện áp dụng.",
                "Hôm qua · 08:30", null, true));
        items.add(new Item(Kind.REVIEW, "Cảm ơn bạn đã đồng hành",
                "Hành trình ngày 20/09 đã hoàn thành. Chia sẻ trải nghiệm để giúp nhà xe tốt hơn.",
                "21/09/2026 · 09:00", "VG20092608", true));
    }

    public List<Item> all() {
        return items;
    }

    public int unreadCount() {
        int n = 0;
        for (Item i : items) if (!i.read) n++;
        return n;
    }

    public void markAllRead() {
        for (Item i : items) i.read = true;
    }

    /** Thêm thông báo mới lên đầu danh sách. */
    public void push(Kind kind, String title, String body, @Nullable String ticketCode) {
        items.add(0, new Item(kind, title, body, "Vừa xong", ticketCode, false));
    }
}
