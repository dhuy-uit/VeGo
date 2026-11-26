package com.vego.backend.support;

import com.vego.backend.booking.dto.BookingResponse;
import com.vego.backend.ticket.dto.TicketResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class SupportDtos {

    private SupportDtos() { }

    /** history: vài lượt gần nhất để hiểu câu hỏi nối tiếp, role = "user" | "bot". */
    public record ChatRequest(
            @NotBlank(message = "Bạn nhập câu hỏi trước khi gửi nhé")
            @Size(max = 500, message = "Câu hỏi tối đa 500 ký tự")
            String message,
            @Size(max = 12, message = "Lịch sử trò chuyện quá dài")
            List<Turn> history) { }

    /** mode: chế độ của câu trả lời bot (vd "AGENT") để server biết cuộc trò chuyện đang làm thao tác. */
    public record Turn(String role, String text, String mode) {

        public Turn(String role, String text) {
            this(role, text, null);
        }
    }

    /**
     * Một câu trả lời. mode: RULE (dữ liệu / quy tắc trên server), AI (Gemini dựa trên tài liệu),
     * DOCS (trích nguyên tài liệu khi không dùng được Gemini), NONE (không có trong tài liệu),
     * AGENT (trợ lý tra cứu / đề xuất thao tác bằng công cụ).
     * action: thao tác đang chờ khách bấm Xác nhận (đặt vé, hủy vé, đổi vé); suggestions: gợi ý bấm để gửi.
     */
    public record ChatReply(
            String title,
            List<String> paragraphs,
            String source,
            boolean contactButton,
            boolean escalate,
            List<TicketResponse> tickets,
            String mode,
            Action action,
            List<String> suggestions) {

        public ChatReply(String title, List<String> paragraphs, String source, boolean contactButton,
                         boolean escalate, List<TicketResponse> tickets, String mode) {
            this(title, paragraphs, source, contactButton, escalate, tickets, mode, null, List.of());
        }
    }

    /** Thao tác chờ xác nhận. kind: BOOK | CANCEL | EXCHANGE. Chỉ khi gọi confirm với token này server mới thực hiện. */
    public record Action(String token, String kind, String title, String summary, String confirmLabel) { }

    /** Kết quả sau khi khách xác nhận. booking có khi kind = BOOK (đang giữ ghế, chờ thanh toán); ticket có khi CANCEL / EXCHANGE. */
    public record ActionResult(String kind, String message, BookingResponse booking, TicketResponse ticket) { }
}
