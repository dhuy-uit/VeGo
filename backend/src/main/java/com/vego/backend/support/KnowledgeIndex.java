package com.vego.backend.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Kho tài liệu của trợ lý: tệp trong resources/knowledge + dữ liệu đang chạy (bến xe, mã giảm giá) từ CSDL.
 * Làm mới mỗi 10 phút; embedding đã tính được lưu trong bảng kb_embeddings.
 */
@Component
public class KnowledgeIndex {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIndex.class);
    private static final Duration REFRESH = Duration.ofMinutes(10);
    private static final int RRF_K = 10;

    /** Ảnh chụp kho tài liệu tại một thời điểm. vectors rỗng khi chưa dùng được embedding. */
    private record Snapshot(List<Chunk> chunks, TextSearch keywords, Map<String, float[]> vectors, Instant builtAt) { }

    /** Kết quả tìm: các đoạn liên quan nhất + đoạn khớp từ khóa nhất (dùng khi không có mô hình). */
    public record Found(List<Chunk> chunks, TextSearch.Hit bestKeyword) { }

    private final JdbcTemplate jdbc;
    private final GeminiClient gemini;
    private volatile Snapshot snapshot;

    public KnowledgeIndex(JdbcTemplate jdbc, GeminiClient gemini) {
        this.jdbc = jdbc;
        this.gemini = gemini;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        try {
            current();
        } catch (RuntimeException e) {
            log.warn("Chưa dựng được kho tài liệu: {}", e.toString());
        }
    }

    public Found search(String query, int limit) {
        Snapshot s = current();
        List<TextSearch.Hit> keyword = s.keywords().search(query);
        Map<Chunk, Double> fused = new LinkedHashMap<>();
        for (int i = 0; i < keyword.size() && i < 10; i++) {
            fused.merge(keyword.get(i).chunk(), 1.0 / (RRF_K + i), Double::sum);
        }
        if (!s.vectors().isEmpty() && gemini.available()) {
            try {
                float[] q = gemini.embed(List.of("task: search result | query: " + query),
                        Duration.ofSeconds(4)).get(0);
                List<Chunk> bySimilarity = s.chunks().stream()
                        .sorted(Comparator.comparingDouble((Chunk c) -> -TextSearch.cosine(q, s.vectors().get(c.id()))))
                        .limit(10).toList();
                // Ngữ nghĩa được tính nặng hơn từ khóa
                for (int i = 0; i < bySimilarity.size(); i++) {
                    fused.merge(bySimilarity.get(i), 1.5 / (RRF_K + i), Double::sum);
                }
            } catch (GeminiClient.GeminiException e) {
                log.warn("Embedding câu hỏi lỗi, chỉ tìm theo từ khóa: {}", e.getMessage());
            }
        }
        List<Chunk> top = fused.entrySet().stream()
                .sorted(Map.Entry.<Chunk, Double>comparingByValue().reversed())
                .limit(limit).map(Map.Entry::getKey).toList();
        return new Found(top, keyword.isEmpty() ? null : keyword.get(0));
    }

    private Snapshot current() {
        Snapshot s = snapshot;
        if (s != null && !stale(s)) return s;
        synchronized (this) {
            s = snapshot;
            if (s == null || stale(s)) snapshot = s = build();
            return s;
        }
    }

    private boolean stale(Snapshot s) {
        Duration age = Duration.between(s.builtAt(), Instant.now());
        // Chưa có vector mà giờ gọi được Gemini thì thử lại sớm hơn
        return age.compareTo(REFRESH) > 0
                || (s.vectors().isEmpty() && gemini.available() && age.toSeconds() > 60);
    }

    private Snapshot build() {
        List<Chunk> chunks = new ArrayList<>(files());
        chunks.addAll(stations());
        chunks.addAll(promotions());
        Map<String, float[]> vectors = Map.of();
        try {
            vectors = vectors(chunks);
        } catch (RuntimeException e) {
            log.warn("Không tạo được embedding, tạm tìm theo từ khóa: {}", e.getMessage());
        }
        log.info("Kho tài liệu trợ lý: {} đoạn, {} có embedding", chunks.size(), vectors.size());
        return new Snapshot(List.copyOf(chunks), new TextSearch(chunks), vectors, Instant.now());
    }

    private List<Chunk> files() {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources("classpath:knowledge/*.md");
            Arrays.sort(resources, Comparator.comparing(r -> String.valueOf(r.getFilename())));
            List<Chunk> out = new ArrayList<>();
            for (Resource r : resources) {
                String name = String.valueOf(r.getFilename()).replace(".md", "");
                out.addAll(KnowledgeParser.parse(name, r.getContentAsString(StandardCharsets.UTF_8)));
            }
            return out;
        } catch (IOException e) {
            throw new IllegalStateException("Không đọc được tài liệu trợ lý", e);
        }
    }

    private List<Chunk> stations() {
        Map<String, StringBuilder> byCity = new LinkedHashMap<>();
        jdbc.query("SELECT city, name, address FROM stations ORDER BY city, id", rs -> {
            byCity.computeIfAbsent(rs.getString("city"), k -> new StringBuilder())
                    .append("• ").append(rs.getString("name")).append(" — ").append(rs.getString("address"))
                    .append('\n');
        });
        List<Chunk> out = new ArrayList<>();
        byCity.forEach((city, list) -> out.add(new Chunk("diem-don-" + (out.size() + 1), "Theo Danh sách điểm đón",
                "Điểm đón và trả khách", "Điểm đón, điểm trả, bến xe tại " + city, list.toString().strip())));
        return out;
    }

    private List<Chunk> promotions() {
        List<Chunk> out = new ArrayList<>();
        jdbc.query("""
                SELECT code, title, note, description, min_order, per_user_limit,
                       to_char(ends_at AT TIME ZONE 'Asia/Ho_Chi_Minh', 'DD/MM/YYYY') AS ends
                FROM promotions
                WHERE active AND code IS NOT NULL AND starts_at <= now() AND ends_at >= now()
                ORDER BY priority DESC, code""", rs -> {
            StringBuilder text = new StringBuilder("Mã ").append(rs.getString("code")).append(": ")
                    .append(rs.getString("title")).append(". ");
            for (String column : List.of("note", "description")) {
                String value = rs.getString(column);
                if (value != null && !value.isBlank()) text.append(value).append(". ");
            }
            if (rs.getInt("min_order") > 0) {
                text.append("Áp dụng cho vé từ ")
                        .append(String.format(Locale.ROOT, "%,d", rs.getInt("min_order")).replace(',', '.'))
                        .append("đ. ");
            }
            text.append("Mỗi tài khoản dùng tối đa ").append(rs.getInt("per_user_limit")).append(" lần. Hạn dùng đến ")
                    .append(rs.getString("ends")).append(". Nhập mã ở bước thanh toán.");
            out.add(new Chunk("khuyen-mai-" + (out.size() + 1), "Theo Chương trình khuyến mãi",
                    "Mã giảm giá đang có", "Mã giảm giá, khuyến mãi " + rs.getString("code"), text.toString()));
        });
        return out;
    }

    private Map<String, float[]> vectors(List<Chunk> chunks) {
        String model = gemini.embeddingModel();
        Map<String, float[]> cached = new HashMap<>();
        jdbc.query("SELECT hash, embedding FROM kb_embeddings WHERE model = ?", rs -> {
            Object[] values = (Object[]) rs.getArray("embedding").getArray();
            float[] v = new float[values.length];
            for (int i = 0; i < v.length; i++) v[i] = ((Number) values[i]).floatValue();
            cached.put(rs.getString("hash"), v);
        }, model);

        Map<String, float[]> result = new HashMap<>();
        List<Chunk> missing = new ArrayList<>();
        for (Chunk c : chunks) {
            float[] v = cached.get(hash(model, document(c)));
            if (v != null) result.put(c.id(), v);
            else missing.add(c);
        }
        if (!missing.isEmpty()) {
            // Thiếu đoạn nào mà không gọi được Gemini thì bỏ hẳn tìm theo vector, tránh xếp hạng lệch
            if (!gemini.available()) return Map.of();
            List<float[]> fresh = gemini.embed(missing.stream().map(KnowledgeIndex::document).toList(),
                    Duration.ofSeconds(20));
            for (int i = 0; i < missing.size(); i++) {
                result.put(missing.get(i).id(), fresh.get(i));
                save(hash(model, document(missing.get(i))), model, fresh.get(i));
            }
        }
        return result;
    }

    private void save(String hash, String model, float[] vector) {
        Float[] boxed = new Float[vector.length];
        for (int i = 0; i < vector.length; i++) boxed[i] = vector[i];
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO kb_embeddings (hash, model, embedding) VALUES (?, ?, ?) ON CONFLICT (hash) DO NOTHING");
            Array array = connection.createArrayOf("float4", boxed);
            ps.setString(1, hash);
            ps.setString(2, model);
            ps.setArray(3, array);
            return ps;
        });
    }

    private static String document(Chunk c) {
        return "title: " + c.title() + " - " + c.heading() + " | text: " + c.text();
    }

    private static String hash(String model, String text) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha.digest((model + "\n" + text).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
