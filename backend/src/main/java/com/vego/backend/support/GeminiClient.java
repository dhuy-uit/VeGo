package com.vego.backend.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vego.backend.config.RagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gọi Gemini API qua REST (embedding + sinh câu trả lời JSON). Khóa chỉ nằm trên server.
 * Gặp lỗi hạn mức / vùng / khóa thì tạm ngừng gọi một lúc để người dùng không phải chờ vô ích.
 */
@Component
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);
    private static final int DIMENSIONS = 768;
    private static final int BATCH = 50;

    private final RagProperties props;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private volatile Instant pausedUntil = Instant.EPOCH;

    public GeminiClient(RagProperties props, ObjectMapper json) {
        this.props = props;
        this.json = json;
    }

    public static class GeminiException extends RuntimeException {
        final int status;

        GeminiException(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    /** Có khóa và không đang tạm ngừng. */
    public boolean available() {
        return !props.apiKey().isEmpty() && Instant.now().isAfter(pausedUntil);
    }

    public String embeddingModel() {
        return props.embeddingModel();
    }

    /** Vector cho từng đoạn văn bản (đã gắn tiền tố tác vụ), theo đúng thứ tự. */
    public List<float[]> embed(List<String> texts, Duration timeout) {
        List<float[]> out = new ArrayList<>();
        String model = "models/" + props.embeddingModel();
        for (int from = 0; from < texts.size(); from += BATCH) {
            List<Map<String, Object>> requests = new ArrayList<>();
            for (String text : texts.subList(from, Math.min(from + BATCH, texts.size()))) {
                requests.add(Map.of("model", model,
                        "content", Map.of("parts", List.of(Map.of("text", text))),
                        "output_dimensionality", DIMENSIONS));
            }
            JsonNode res = post(model + ":batchEmbedContents", Map.of("requests", requests), timeout);
            JsonNode list = res.path("embeddings");
            if (list.size() != requests.size()) throw new GeminiException(502, "Thiếu embedding trong phản hồi");
            for (JsonNode e : list) {
                JsonNode values = e.path("values");
                float[] v = new float[values.size()];
                for (int i = 0; i < v.length; i++) v[i] = (float) values.get(i).asDouble();
                out.add(v);
            }
        }
        return out;
    }

    /** Sinh một đối tượng JSON theo schema. */
    public JsonNode generate(String system, String prompt, Map<String, Object> schema) {
        String path = "models/" + props.model() + ":generateContent";
        JsonNode res;
        try {
            res = post(path, body(system, prompt, Map.of(
                    "maxOutputTokens", 1024,
                    "responseFormat", Map.of("text", Map.of("mimeType", "APPLICATION_JSON", "schema", schema)))),
                    Duration.ofSeconds(11));
        } catch (GeminiException e) {
            if (e.status != 400 || !available()) throw e;
            // Model cũ hơn chưa nhận responseFormat
            res = post(path, body(system, prompt, Map.of(
                    "maxOutputTokens", 1024,
                    "responseMimeType", "application/json",
                    "responseJsonSchema", schema)), Duration.ofSeconds(8));
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode part : res.path("candidates").path(0).path("content").path("parts")) {
            if (!part.path("thought").asBoolean(false)) text.append(part.path("text").asText(""));
        }
        String raw = text.toString().strip();
        if (raw.startsWith("```")) raw = raw.replaceAll("^```(json)?", "").replaceAll("```$", "").strip();
        try {
            JsonNode parsed = json.readTree(raw);
            if (parsed == null || !parsed.isObject()) throw new GeminiException(502, "Phản hồi không phải JSON");
            return parsed;
        } catch (IOException e) {
            throw new GeminiException(502, "Phản hồi JSON không đọc được");
        }
    }

    /**
     * Một lượt hội thoại có gọi công cụ (function calling). contents là toàn bộ lượt trước đó; trả về nguyên nội dung
     * của mô hình (parts giữ nguyên, gồm cả chữ ký suy luận mà Gemini yêu cầu khi gửi lại) để vòng lặp nối tiếp.
     */
    public JsonNode chatWithTools(String system, List<Object> contents, List<Map<String, Object>> declarations) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", system))));
        body.put("contents", contents);
        body.put("tools", List.of(Map.of("functionDeclarations", declarations)));
        body.put("toolConfig", Map.of("functionCallingConfig", Map.of("mode", "AUTO")));
        body.put("generationConfig", Map.of("maxOutputTokens", 1024));
        JsonNode res = post("models/" + props.model() + ":generateContent", body, Duration.ofSeconds(12));
        JsonNode content = res.path("candidates").path(0).path("content");
        if (content.isMissingNode() || !content.path("parts").isArray()) {
            throw new GeminiException(502, "Phản hồi trống từ Gemini");
        }
        return content;
    }

    private static Map<String, Object> body(String system, String prompt, Map<String, Object> config) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", system))));
        body.put("contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))));
        body.put("generationConfig", config);
        return body;
    }

    private JsonNode post(String path, Object body, Duration timeout) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(props.baseUrl() + "/" + path))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", props.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> res = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() / 100 != 2) throw failure(res.statusCode(), res.body());
            return json.readTree(res.body());
        } catch (IOException e) {
            throw new GeminiException(503, "Không gọi được Gemini: " + e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GeminiException(503, "Bị ngắt khi gọi Gemini");
        }
    }

    private GeminiException failure(int status, String body) {
        String message;
        try {
            message = json.readTree(body).path("error").path("message").asText("");
        } catch (IOException e) {
            message = "";
        }
        if (message.contains("location is not supported")) {
            pause(Duration.ofMinutes(30));
            log.error("Gemini từ chối vì VÙNG của máy chủ không được hỗ trợ. Cần chạy server ở vùng Gemini API hỗ trợ "
                    + "(vd Southeast Asia - Singapore). Trợ lý đang trả lời bằng tài liệu.");
        } else if (status == 429) {
            pause(Duration.ofMinutes(1));
        } else if (status == 401 || status == 403 || status == 404) {
            pause(Duration.ofMinutes(10));
        }
        return new GeminiException(status, "Gemini HTTP " + status + ": " + message);
    }

    private void pause(Duration time) {
        pausedUntil = Instant.now().plus(time);
    }
}
