package com.example.vego.data;

import androidx.annotation.Nullable;

import com.example.vego.data.api.ApiModels;

import java.util.ArrayList;
import java.util.List;

/**
 * Vé của người dùng lấy từ server (GET /api/v1/tickets), giữ trong bộ nhớ để các màn
 * Vé của tôi / Vé điện tử / Hủy vé / Đánh giá hiện ngay rồi mới cập nhật bản mới nhất.
 */
public final class TicketStore {

    private static final TicketStore INSTANCE = new TicketStore();

    public static TicketStore get() {
        return INSTANCE;
    }

    private final List<Ticket> tickets = new ArrayList<>();
    private boolean loaded = false;

    private TicketStore() { }

    /** Đã tải danh sách từ server ít nhất một lần chưa. */
    public boolean isLoaded() {
        return loaded;
    }

    public void setAll(@Nullable List<ApiModels.Ticket> list) {
        tickets.clear();
        if (list != null) for (ApiModels.Ticket t : list) tickets.add(new Ticket(t));
        loaded = true;
    }

    /** Cập nhật / thêm một vé vừa lấy từ server, trả về bản mới. */
    public Ticket put(ApiModels.Ticket dto) {
        Ticket ticket = new Ticket(dto);
        for (int i = 0; i < tickets.size(); i++) {
            if (tickets.get(i).code.equals(ticket.code)) {
                tickets.set(i, ticket);
                return ticket;
            }
        }
        tickets.add(0, ticket);
        return ticket;
    }

    /** Đăng xuất: xóa vé của tài khoản cũ. */
    public void clear() {
        tickets.clear();
        loaded = false;
    }

    public List<Ticket> byStatus(Ticket.Status status) {
        List<Ticket> out = new ArrayList<>();
        for (Ticket t : tickets) if (t.status == status) out.add(t);
        // Sắp đi: chuyến gần nhất lên đầu
        if (status == Ticket.Status.UPCOMING) out.sort((a, b) -> Long.compare(a.departAtMillis(), b.departAtMillis()));
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
}
