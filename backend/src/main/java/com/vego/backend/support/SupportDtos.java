package com.vego.backend.support;

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

    public record Turn(String role, String text) { }

    /**
     * Một câu trả lời. mode: RULE (dữ liệu / quy tắc trên server), AI (Gemini dựa trên tài liệu),
     * DOCS (trích nguyên tài liệu khi không dùng được Gemini), NONE (không có trong tài liệu).
     */
    public record ChatReply(
            String title,
            List<String> paragraphs,
            String source,
            boolean contactButton,
            boolean escalate,
            List<TicketResponse> tickets,
            String mode) { }
}
