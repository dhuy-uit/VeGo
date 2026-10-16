package com.example.vego.data;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Danh sách vé của người dùng, lưu tạm trong bộ nhớ (dữ liệu mẫu theo Figma).
 * Đặt vé thành công thì thêm vé mới; hủy / đánh giá thì cập nhật trạng thái.
 */
public final class TicketStore {

    private static final TicketStore INSTANCE = new TicketStore();

    public static TicketStore get() {
        return INSTANCE;
    }

    /** Chính sách hoàn tiền minh họa: hủy trước 24 giờ hoàn 80%, sát giờ hoàn 50%. */
    public static final int REFUND_PERCENT_EARLY = 80;
    public static final int REFUND_PERCENT_LATE = 50;

    private final List<Ticket> tickets = new ArrayList<>();

    private TicketStore() {
        String hcm = "TP. Hồ Chí Minh";
        tickets.add(new Ticket("VG18102605", "PT2200", "Phương Trang", "Limousine giường nằm 34 chỗ",
                hcm, "Đà Lạt", 22 * 60, 7 * 60, day(2026, Calendar.OCTOBER, 18), "A05",
                "Nguyễn Minh Anh", "090 123 4567", "Bến xe Miền Đông mới", "Bến xe Đà Lạt",
                320_000, "MoMo", Ticket.Status.UPCOMING));

        tickets.add(new Ticket("VG20092608", "PT2200", "Phương Trang", "Limousine giường nằm 34 chỗ",
                hcm, "Đà Lạt", 22 * 60, 7 * 60, day(2026, Calendar.SEPTEMBER, 20), "B08",
                "Nguyễn Minh Anh", "090 123 4567", "Bến xe Miền Đông mới", "Bến xe Đà Lạt",
                320_000, "MoMo", Ticket.Status.COMPLETED));

        Ticket cancelled = new Ticket("VG02102603", null, "Phương Trang", "Limousine giường nằm 34 chỗ",
                hcm, "Nha Trang", 21 * 60, 8 * 60, day(2026, Calendar.OCTOBER, 2), "A03",
                "Nguyễn Minh Anh", "090 123 4567", "Bến xe Miền Đông mới", "Bến xe Phía Nam Nha Trang",
                280_000, "MoMo", Ticket.Status.CANCELLED);
        cancelled.refundAmount = 224_000;
        tickets.add(cancelled);
    }

    private static Calendar day(int y, int m, int d) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(y, m, d);
        return c;
    }

    public List<Ticket> byStatus(Ticket.Status status) {
        List<Ticket> out = new ArrayList<>();
        for (Ticket t : tickets) if (t.status == status) out.add(t);
        return out;
    }

    /** Lịch sử: vé đã đi và đã hủy, mới nhất trước. */
    public List<Ticket> history() {
        List<Ticket> out = new ArrayList<>();
        for (Ticket t : tickets) if (t.status != Ticket.Status.UPCOMING) out.add(t);
        // Như Figma: vé đã đi trước, vé đã hủy sau; trong mỗi nhóm thì mới nhất lên đầu
        out.sort((a, b) -> {
            boolean ca = a.status == Ticket.Status.CANCELLED, cb = b.status == Ticket.Status.CANCELLED;
            if (ca != cb) return ca ? 1 : -1;
            return Long.compare(b.departAtMillis(), a.departAtMillis());
        });
        return out;
    }

    @Nullable
    public Ticket find(String code) {
        for (Ticket t : tickets) if (t.code.equals(code)) return t;
        return null;
    }

    /** Thêm vé mới (vé cùng mã sẽ được thay thế). */
    public void add(Ticket ticket) {
        Ticket old = find(ticket.code);
        if (old != null) tickets.remove(old);
        tickets.add(0, ticket);
    }

    /** Tỉ lệ hoàn tiền nếu hủy vào lúc này. */
    public static int refundPercent(Ticket t) {
        long hoursLeft = (t.departAtMillis() - System.currentTimeMillis()) / (60 * 60 * 1000L);
        return hoursLeft >= 24 ? REFUND_PERCENT_EARLY : REFUND_PERCENT_LATE;
    }

    public static int refundAmount(Ticket t) {
        return t.price * refundPercent(t) / 100;
    }

    /** Vé đã qua giờ khởi hành thì không hủy được nữa. */
    public static boolean canCancel(Ticket t) {
        return t.status == Ticket.Status.UPCOMING && t.departAtMillis() > System.currentTimeMillis();
    }

    public void cancel(Ticket t) {
        t.refundAmount = refundAmount(t);
        t.status = Ticket.Status.CANCELLED;
    }
}
