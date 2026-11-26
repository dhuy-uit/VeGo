package com.example.vego.data.support;

import androidx.annotation.Nullable;

import com.example.vego.data.Ticket;
import com.example.vego.data.TicketStore;
import com.example.vego.data.api.ApiModels;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Trợ lý VéGo (Figma nhóm 07 · Hỗ trợ AI). Việc tìm tài liệu và soạn câu trả lời (RAG + Gemini) chạy hết trên
 * server qua POST /api/v1/support/chat nên máy yếu vẫn dùng mượt; lớp này chỉ chuyển kết quả sang dạng hiển thị.
 */
public final class SupportAssistant {

    /** Một câu trả lời của trợ lý. */
    public static final class Reply {
        /** Tiêu đề đậm đầu bong bóng (khi chuyển nhân viên hoặc báo lỗi). */
        @Nullable public final String title;
        public final List<String> paragraphs;
        /** Nguồn tài liệu đã dùng, vd "Theo Chính sách hủy vé". */
        @Nullable public final String source;
        /** Có nút "Liên hệ nhân viên" trong bong bóng không. */
        public final boolean contactButton;
        /** Vé hiện kèm (tra cứu vé). */
        public final List<Ticket> tickets;
        /** Hiện thẻ "Để nhân viên hỗ trợ bạn" sau câu trả lời. */
        public final boolean escalate;

        Reply(@Nullable String title, List<String> paragraphs, @Nullable String source,
              boolean contactButton, List<Ticket> tickets, boolean escalate) {
            this.title = title;
            this.paragraphs = paragraphs;
            this.source = source;
            this.contactButton = contactButton;
            this.tickets = tickets;
            this.escalate = escalate;
        }
    }

    private SupportAssistant() { }

    /** Câu trả lời từ server; vé kèm theo được đưa vào kho vé để "Xem vé điện tử" mở được. */
    public static Reply from(ApiModels.SupportReply r, TicketStore store) {
        List<Ticket> tickets = new ArrayList<>();
        if (r.tickets != null) {
            for (ApiModels.Ticket t : r.tickets) tickets.add(store.put(t));
        }
        return new Reply(r.title, r.paragraphs, r.source, r.contactButton, tickets, r.escalate);
    }

    /** Không gọi được server: báo rõ và vẫn cho liên hệ nhân viên. */
    public static Reply error(String message) {
        return new Reply(null, Collections.singletonList(message), null, true, Collections.emptyList(), false);
    }
}
