package com.example.vego.data.support;

import androidx.annotation.Nullable;

import com.example.vego.data.CoinWallet;
import com.example.vego.data.Format;
import com.example.vego.data.Ticket;
import com.example.vego.data.TicketStore;
import com.example.vego.data.TripRepository;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Trợ lý VéGo (Figma nhóm 07 · Hỗ trợ AI) — bản chạy offline cho prototype.
 *
 * Cách làm mô phỏng RAG: tìm tài liệu chính sách khớp nhất với câu hỏi (theo từ khóa, bỏ dấu tiếng Việt),
 * rồi trả lời CHỈ dựa trên tài liệu đó và ghi nguồn ("Theo Chính sách hủy vé").
 * Câu hỏi không có trong tài liệu, hoặc cần người xác nhận (vd hoàn tiền), sẽ chuyển sang nhân viên.
 *
 * Khi có backend: thay {@link #reply(String)} bằng lời gọi REST tới server (Gemini + kho tài liệu),
 * giữ nguyên lớp {@link Reply} để giao diện không phải sửa.
 */
public final class SupportAssistant {

    /** Một câu trả lời của trợ lý. */
    public static final class Reply {
        /** Tiêu đề đậm đầu bong bóng (chỉ dùng khi chuyển nhân viên). */
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

        static Reply text(@Nullable String source, String... paragraphs) {
            return new Reply(null, Arrays.asList(paragraphs), source, false, Collections.emptyList(), false);
        }
    }

    private static final String SRC_CANCEL = "Theo Chính sách hủy vé";
    private static final String SRC_LUGGAGE = "Theo Quy định hành lý";
    private static final String SRC_CHANGE = "Theo Chính sách đổi vé";
    private static final String SRC_PAYMENT = "Theo Hướng dẫn thanh toán";
    private static final String SRC_PICKUP = "Theo Danh sách điểm đón";
    private static final String SRC_WALLET = "Theo Ví xu của bạn";

    private static final Pattern HOURS = Pattern.compile("(\\d+)\\s*(tieng|gio|h)\\b");

    private SupportAssistant() { }

    /** Câu trả lời cho một câu hỏi. Chạy rất nhanh, không cần mạng. */
    public static Reply reply(String question) {
        String q = normalize(question);

        if (hasAny(q, "xac nhan hoan", "hoan tien cho ve", "hoan tien giup", "duoc hoan chua", "chua nhan duoc tien")) {
            return escalation();
        }
        if (hasAny(q, "nhan vien", "tong dai", "hotline", "nguoi that", "khieu nai")) {
            return escalation();
        }
        if (hasAny(q, "huy ve", "hoan tien", "hoan ve", "huy chuyen", "phi huy", "huy")) {
            return cancelPolicy(q);
        }
        if (hasAny(q, "doi gio", "doi chuyen", "doi ve", "doi ngay", "chuyen sang chuyen")) {
            return Reply.text(SRC_CHANGE,
                    "Bạn có thể đổi giờ hoặc đổi chuyến 1 lần miễn phí nếu yêu cầu trước giờ khởi hành ít nhất 24 giờ và chuyến mới vẫn còn ghế.",
                    "Sau mốc 24 giờ, bạn cần hủy vé theo chính sách hoàn tiền rồi đặt lại chuyến mới: vào Vé của tôi → Vé điện tử → Yêu cầu hủy vé, sau đó tìm chuyến ở Trang chủ.",
                    "Nội dung chính sách chỉ dùng cho thiết kế mẫu.");
        }
        if (hasAny(q, "ve cua toi", "ve cua minh", "may gio", "kiem tra ve", "ve sap", "ma ve", "ghe so", "khoi hanh luc", "chuyen cua toi")) {
            return myTickets();
        }
        if (hasAny(q, "hanh ly", "vali", "kg", "mang theo", "xach tay", "ky gui", "thu cung", "cong kenh")) {
            return Reply.text(SRC_LUGGAGE,
                    "Mỗi hành khách được mang 1 kiện ký gửi tối đa 20kg và 1 túi xách tay tối đa 7kg, miễn phí.",
                    "Hàng cồng kềnh, xe máy hoặc thú cưng cần báo trước với nhà xe và có thể bị tính phụ phí. Không mang chất dễ cháy nổ lên xe.",
                    "Quy định này chỉ dùng cho thiết kế mẫu, mỗi nhà xe có thể khác nhau.");
        }
        if (hasAny(q, "diem don", "ben xe", "don o dau", "tra khach", "diem tra", "len xe o dau")) {
            StringBuilder sb = new StringBuilder();
            for (String[] p : TripRepository.PICKUP_POINTS) sb.append("• ").append(p[0]).append(" — ").append(p[1]).append('\n');
            return Reply.text(SRC_PICKUP,
                    "Các điểm đón tại TP. Hồ Chí Minh hiện có:",
                    sb.toString().trim(),
                    "Bạn chọn điểm đón ở bước Thông tin hành khách khi đặt vé. Nên có mặt trước giờ xe chạy 30 phút nhé.");
        }
        if (hasAny(q, "thanh toan", "momo", "the ngan hang", "chuyen khoan", "zalopay", "vnpay", "tra tien")) {
            return Reply.text(SRC_PAYMENT,
                    "VéGo hỗ trợ thanh toán qua ví MoMo, thẻ ATM/Visa/Mastercard và chuyển khoản ngân hàng.",
                    "Ghế được giữ cho bạn trong 5 phút ở bước xác nhận. Nếu thanh toán không thành công, bạn có thể thử lại hoặc đổi phương thức khác khi ghế vẫn còn được giữ.");
        }
        if (hasAny(q, "xu", "voucher", "diem danh", "ma giam", "khuyen mai", "uu dai")) {
            CoinWallet w = CoinWallet.get();
            return Reply.text(SRC_WALLET,
                    "Bạn đang có " + CoinWallet.coins(w.balance()) + " xu trong Ví xu.",
                    "Bạn có thể tích xu bằng cách điểm danh mỗi ngày, chơi game Khủng long phiêu lưu (10 điểm = 1 xu) và đặt vé (1.000đ = 1 xu). Xu dùng để đổi voucher giảm giá vé từ 500 xu.",
                    "Vào Tài khoản → Ví xu để xem chi tiết nhé.");
        }
        if (hasAny(q, "cam on", "thanks", "thank")) {
            return Reply.text(null, "Rất vui được giúp bạn! Chúc bạn có một chuyến đi thật suôn sẻ cùng VéGo.");
        }
        if (hasAny(q, "xin chao", "chao ban", "hello", "alo") || q.equals("hi") || q.equals("chao")) {
            return Reply.text(null, "Chào bạn! Mình là trợ lý VéGo. Bạn có thể hỏi mình về vé của bạn, chính sách hủy/đổi vé, hành lý, điểm đón hoặc thanh toán nhé.");
        }
        // Không có trong tài liệu: không đoán, chuyển nhân viên
        return new Reply("Mình chưa tìm thấy thông tin phù hợp",
                Collections.singletonList("Câu hỏi này chưa có trong tài liệu hỗ trợ của VéGo nên mình không muốn trả lời đoán. Bạn có thể hỏi cách khác, hoặc để nhân viên hỗ trợ trực tiếp nhé."),
                null, false, Collections.emptyList(), true);
    }

    private static Reply cancelPolicy(String q) {
        Matcher m = HOURS.matcher(q);
        if (m.find()) {
            int hours = Integer.parseInt(m.group(1));
            if (hours < 24) {
                // Figma "Cuộc trò chuyện": không tự xác nhận hoàn tiền cho trường hợp sát giờ
                return new Reply(null, Arrays.asList(
                        "Mình chưa thể xác nhận chắc chắn việc hoàn tiền khi hủy trước giờ chạy " + hours + " tiếng.",
                        "Chính sách minh họa hiện có nêu: hủy trước giờ khởi hành trên 24 giờ được hoàn 80% giá vé, hủy trong vòng 24 giờ được hoàn 50%. Nội dung này chỉ dùng cho thiết kế mẫu.",
                        "Với trường hợp sát giờ như của bạn, bạn nên xác nhận với nhà xe hoặc nhân viên trước khi hủy nhé."),
                        SRC_CANCEL, true, Collections.emptyList(), false);
            }
            return Reply.text(SRC_CANCEL,
                    "Hủy trước giờ khởi hành " + hours + " tiếng (trên 24 giờ) thì bạn được hoàn 80% giá vé, phí hủy 20%.",
                    "Tiền hoàn về đúng phương thức bạn đã thanh toán trong 3–5 ngày làm việc. Nội dung chính sách chỉ dùng cho thiết kế mẫu.");
        }
        return Reply.text(SRC_CANCEL,
                "Theo chính sách minh họa của VéGo:",
                "• Hủy trước giờ khởi hành trên 24 giờ: hoàn 80% giá vé, phí hủy 20%.\n• Hủy trong vòng 24 giờ: hoàn 50% giá vé.\n• Xe đã khởi hành: không hủy được.",
                "Tiền hoàn về đúng phương thức thanh toán trong 3–5 ngày làm việc. Bạn hủy vé trong Vé của tôi → Vé điện tử → Yêu cầu hủy vé.");
    }

    private static Reply myTickets() {
        List<Ticket> upcoming = TicketStore.get().byStatus(Ticket.Status.UPCOMING);
        if (upcoming.isEmpty()) {
            return Reply.text(null,
                    "Hiện bạn chưa có vé nào sắp khởi hành.",
                    "Bạn có thể tìm và đặt chuyến mới ở Trang chủ, hoặc xem các vé đã đi trong mục Vé của tôi.");
        }
        Ticket first = upcoming.get(0);
        String intro = upcoming.size() == 1
                ? "Vé của bạn khởi hành lúc " + first.departTime() + " ngày " + Format.date(first.date) + " nhé. Đây là thông tin chuyến đi:"
                : "Bạn có " + upcoming.size() + " vé sắp khởi hành, gần nhất lúc " + first.departTime() + " ngày " + Format.date(first.date) + ":";
        return new Reply(null, Collections.singletonList(intro), null, false, new ArrayList<>(upcoming), false);
    }

    private static Reply escalation() {
        return new Reply("Mình chưa thể hỗ trợ vấn đề này",
                Collections.singletonList("Việc hoàn tiền và các yêu cầu cần xác minh phải do nhà xe hoặc nhân viên xác nhận. Bạn có thể gọi hotline hoặc gửi yêu cầu để được hỗ trợ tiếp nhé."),
                null, false, Collections.emptyList(), true);
    }

    // ---------- Xử lý chữ tiếng Việt ----------

    /** Viết thường, bỏ dấu (đ -> d), gộp khoảng trắng: "Hủy VÉ  2 tiếng" -> "huy ve 2 tieng". */
    static String normalize(String text) {
        String lower = text.toLowerCase(Locale.ROOT).replace('đ', 'd');
        String noMarks = Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return noMarks.replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    }

    private static boolean hasAny(String q, String... keys) {
        String padded = " " + q + " ";
        for (String k : keys) {
            if (padded.contains(" " + k + " ") || (k.contains(" ") && q.contains(k))) return true;
        }
        return false;
    }
}
