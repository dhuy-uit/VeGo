-- RAG trợ lý hỗ trợ: lưu vector embedding của các đoạn tài liệu để khỏi gọi Gemini lại mỗi lần khởi động.
-- Kho tài liệu nhỏ (vài chục đoạn) nên so khớp cosine ngay trong server, không cần tiện ích pgvector.
CREATE TABLE kb_embeddings (
    hash       CHAR(64)    PRIMARY KEY,   -- SHA-256 của (model + nội dung đoạn)
    model      VARCHAR(80) NOT NULL,
    embedding  REAL[]      NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
