package com.vego.backend.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Câu nào được đưa cho trợ lý thao tác (không cần CSDL hay Gemini). */
class SupportAgentGateTest {

    @Test
    void actionRequestsAreRecognised() {
        String[] yes = {
                "đặt vé từ sài gòn đi đà lạt ngày mai",
                "tìm chuyến TP. Hồ Chí Minh đi Đà Lạt thứ sáu",
                "mình muốn hủy vé VG18102605",
                "hủy giúp mình chuyến tối nay",
                "đổi vé sang chuyến 8h sáng",
                "còn chuyến nào đi Nha Trang không",
                "chọn ghế A05",
        };
        for (String q : yes) assertThat(SupportAgent.looksLikeAction(q, false)).as(q).isTrue();
    }

    @Test
    void policyQuestionsAndPlainDataQuestionsAreNot() {
        String[] no = {
                "hành lý xách tay được mang bao nhiêu kg",
                "ghế được giữ bao lâu",
                "thanh toán bằng gì",
                "mình còn bao nhiêu xu",
                "xin chào",
        };
        for (String q : no) assertThat(SupportAgent.looksLikeAction(q, false)).as(q).isFalse();
    }

    @Test
    void viewingOwnTicketsOrRefundStaysWithServerData() {
        // Chỉ hỏi hoàn bao nhiêu / xem vé: dữ liệu lấy từ CSDL như cũ
        assertThat(SupportAgent.looksLikeAction("hủy vé của tôi được hoàn bao nhiêu", true)).isFalse();
        assertThat(SupportAgent.looksLikeAction("kiểm tra vé của tôi", true)).isFalse();
        // Nhưng yêu cầu hủy rõ ràng thì sang trợ lý thao tác
        assertThat(SupportAgent.looksLikeAction("hủy vé của tôi giúp mình", true)).isTrue();
        assertThat(SupportAgent.looksLikeAction("mình muốn hủy vé của mình", true)).isTrue();
    }
}
