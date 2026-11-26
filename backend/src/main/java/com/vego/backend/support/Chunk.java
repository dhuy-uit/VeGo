package com.vego.backend.support;

/** Một đoạn tài liệu: đơn vị để tìm kiếm và đưa vào ngữ cảnh cho mô hình. */
public record Chunk(String id, String source, String title, String heading, String text) {
}
