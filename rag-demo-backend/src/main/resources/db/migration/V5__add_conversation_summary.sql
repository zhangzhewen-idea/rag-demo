-- 长会话滚动摘要独立存储，原始消息仍完整保留在 ai_message。
ALTER TABLE ai_message
    ADD KEY idx_message_conversation_id (conversation_id, id);

CREATE TABLE ai_conversation_summary (
    conversation_id BIGINT PRIMARY KEY,
    summary LONGTEXT NOT NULL,
    through_message_id BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_conversation_summary_conversation FOREIGN KEY (conversation_id) REFERENCES ai_conversation(id),
    CONSTRAINT fk_conversation_summary_message FOREIGN KEY (through_message_id) REFERENCES ai_message(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
