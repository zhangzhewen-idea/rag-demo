-- 分离向量、BM25 和 RRF 分数，避免把不同量纲的分数混用。
ALTER TABLE ai_message_reference
    CHANGE COLUMN similarity_score vector_score DECIMAL(8,6) NULL,
    ADD COLUMN bm25_score DECIMAL(16,8) NULL AFTER vector_score,
    ADD COLUMN fusion_score DECIMAL(12,10) NULL AFTER bm25_score;
