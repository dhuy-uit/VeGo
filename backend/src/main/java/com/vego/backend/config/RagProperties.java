package com.vego.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Trợ lý hỗ trợ RAG (khối "app.rag:" trong application.yml).
 * Không đặt apiKey thì trợ lý vẫn trả lời bằng cách trích đúng đoạn tài liệu khớp từ khóa.
 */
@ConfigurationProperties(prefix = "app.rag")
public record RagProperties(
        String apiKey,
        String model,
        String embeddingModel,
        String baseUrl,
        Integer ratePerMinute) {

    public RagProperties {
        apiKey = apiKey == null ? "" : apiKey.trim();
        if (model == null || model.isBlank()) model = "gemini-3.5-flash-lite";
        if (embeddingModel == null || embeddingModel.isBlank()) embeddingModel = "gemini-embedding-2";
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = "https://generativelanguage.googleapis.com/v1beta";
        if (ratePerMinute == null || ratePerMinute < 1) ratePerMinute = 10;
    }
}
