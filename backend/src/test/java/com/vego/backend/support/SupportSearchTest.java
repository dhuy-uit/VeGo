package com.vego.backend.support;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Kiểm tra tìm kiếm theo từ khóa và phân loại câu hỏi (không cần CSDL hay Gemini). */
class SupportSearchTest {

    private static TextSearch search() throws IOException {
        List<Chunk> chunks = new ArrayList<>();
        try (Stream<Path> files = Files.list(Path.of("src/main/resources/knowledge"))) {
            for (Path f : files.sorted().toList()) {
                chunks.addAll(KnowledgeParser.parse(f.getFileName().toString().replace(".md", ""),
                        Files.readString(f, StandardCharsets.UTF_8)));
            }
        }
        return new TextSearch(chunks);
    }

    @Test
    void commonQuestionsFindTheRightDocument() throws IOException {
        TextSearch s = search();
        String[][] cases = {
                {"Chính sách hủy vé & hoàn tiền", "huy-ve"},
                {"Quy định hành lý", "hanh-ly"},
                {"Cách đổi giờ/chuyến", "doi-ve"},
                {"hủy vé trước 2 tiếng được hoàn bao nhiêu", "huy-ve"},
                {"mang theo thú cưng được không", "hanh-ly"},
                {"thanh toán bằng momo được không", "thanh-toan"},
                {"đổi xu lấy voucher như thế nào", "vi-xu"},
                {"quên mật khẩu thì làm sao", "tai-khoan"},
                {"ghế được giữ bao lâu", "dat-ve"},
        };
        for (String[] c : cases) {
            assertThat(s.search(c[0])).as(c[0]).isNotEmpty();
            assertThat(s.search(c[0]).get(0).chunk().id()).as(c[0]).startsWith(c[1]);
        }
    }

    @Test
    void personalQuestionsAreRoutedToServerData() {
        assertThat(Intents.detect("Kiểm tra vé của tôi")).isEqualTo(Intents.Intent.MY_TICKETS);
        assertThat(Intents.detect("hủy vé của tôi được hoàn bao nhiêu")).isEqualTo(Intents.Intent.MY_REFUND);
        assertThat(Intents.detect("mình còn bao nhiêu xu")).isEqualTo(Intents.Intent.MY_COINS);
        assertThat(Intents.detect("đổi voucher cần bao nhiêu xu")).isEqualTo(Intents.Intent.KNOWLEDGE);
        assertThat(Intents.detect("tôi bị trừ tiền mà không có vé")).isEqualTo(Intents.Intent.ESCALATE);
        assertThat(Intents.detect("Xin chào")).isEqualTo(Intents.Intent.GREETING);
        assertThat(Intents.detect("Chính sách hủy vé & hoàn tiền")).isEqualTo(Intents.Intent.KNOWLEDGE);
    }
}
