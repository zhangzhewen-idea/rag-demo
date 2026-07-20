-- 引用摘要需要保存完整切片内容，VARCHAR(1000) 会导致较长切片入库失败。
ALTER TABLE ai_message_reference
    MODIFY COLUMN excerpt TEXT NOT NULL;
