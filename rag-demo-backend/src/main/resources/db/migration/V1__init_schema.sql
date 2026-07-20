CREATE TABLE sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT, username VARCHAR(64) NOT NULL, password CHAR(32) NOT NULL,
    nickname VARCHAR(64) NOT NULL, avatar_url VARCHAR(512), status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    last_login_at DATETIME(3), created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3), deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_sys_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE sys_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT, role_code VARCHAR(32) NOT NULL, role_name VARCHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_sys_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE sys_user_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL, role_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), UNIQUE KEY uk_user_role (user_id, role_id),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user(id), CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE kb_knowledge_base (
    id BIGINT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(128) NOT NULL, description VARCHAR(1000), cover_url VARCHAR(512),
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED', created_by BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3), deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_kb_status (status), CONSTRAINT fk_kb_creator FOREIGN KEY (created_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE kb_document (
    id BIGINT PRIMARY KEY AUTO_INCREMENT, knowledge_base_id BIGINT NOT NULL, original_name VARCHAR(255) NOT NULL,
    stored_name VARCHAR(128) NOT NULL, storage_path VARCHAR(1024) NOT NULL, extension VARCHAR(16) NOT NULL, mime_type VARCHAR(128) NOT NULL,
    file_size BIGINT NOT NULL, content_hash CHAR(64) NOT NULL, status VARCHAR(16) NOT NULL DEFAULT 'PENDING', chunk_count INT NOT NULL DEFAULT 0,
    retry_count INT NOT NULL DEFAULT 0, failure_stage VARCHAR(32), failure_reason VARCHAR(500), created_by BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3), deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_document_kb (knowledge_base_id), KEY idx_document_status (status), KEY idx_document_hash (content_hash),
    CONSTRAINT fk_document_kb FOREIGN KEY (knowledge_base_id) REFERENCES kb_knowledge_base(id), CONSTRAINT fk_document_creator FOREIGN KEY (created_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE ai_conversation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL, knowledge_base_id BIGINT NOT NULL, title VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE', created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3), deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_conversation_user (user_id), CONSTRAINT fk_conversation_user FOREIGN KEY (user_id) REFERENCES sys_user(id), CONSTRAINT fk_conversation_kb FOREIGN KEY (knowledge_base_id) REFERENCES kb_knowledge_base(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE ai_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT, conversation_id BIGINT NOT NULL, role VARCHAR(16) NOT NULL, content LONGTEXT NOT NULL,
    prompt_tokens INT, completion_tokens INT, elapsed_ms BIGINT, status VARCHAR(16) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), KEY idx_message_conversation (conversation_id, created_at),
    CONSTRAINT fk_message_conversation FOREIGN KEY (conversation_id) REFERENCES ai_conversation(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE ai_message_reference (
    id BIGINT PRIMARY KEY AUTO_INCREMENT, message_id BIGINT NOT NULL, knowledge_base_id BIGINT NOT NULL, document_id BIGINT NOT NULL,
    source_name VARCHAR(255) NOT NULL, chunk_index INT NOT NULL, similarity_score DECIMAL(8,6) NOT NULL, excerpt VARCHAR(1000) NOT NULL,
    page_number INT, section_title VARCHAR(255), created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), KEY idx_reference_message (message_id),
    CONSTRAINT fk_reference_message FOREIGN KEY (message_id) REFERENCES ai_message(id), CONSTRAINT fk_reference_kb FOREIGN KEY (knowledge_base_id) REFERENCES kb_knowledge_base(id), CONSTRAINT fk_reference_document FOREIGN KEY (document_id) REFERENCES kb_document(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
