-- 保留原始检索相似度，同时记录可空的二次重排分数。
ALTER TABLE ai_message_reference
    ADD COLUMN rerank_score DECIMAL(8,6) NULL AFTER similarity_score;
