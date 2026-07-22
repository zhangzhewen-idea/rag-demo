-- 评估集按知识库和版本不可变保存，便于复现历史运行。
CREATE TABLE rag_evaluation_dataset (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    knowledge_base_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    version VARCHAR(64) NOT NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_evaluation_dataset_version (knowledge_base_id, name, version),
    KEY idx_evaluation_dataset_kb (knowledge_base_id, created_at),
    CONSTRAINT fk_evaluation_dataset_kb FOREIGN KEY (knowledge_base_id) REFERENCES kb_knowledge_base(id),
    CONSTRAINT fk_evaluation_dataset_creator FOREIGN KEY (created_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE rag_evaluation_case (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    dataset_id BIGINT NOT NULL,
    question TEXT NOT NULL,
    golden_answer LONGTEXT NOT NULL,
    answer_type VARCHAR(32) NOT NULL,
    critical TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_evaluation_case_dataset (dataset_id, id),
    CONSTRAINT fk_evaluation_case_dataset FOREIGN KEY (dataset_id) REFERENCES rag_evaluation_dataset(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE rag_evaluation_expected_context (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    case_id BIGINT NOT NULL,
    source_name VARCHAR(255) NOT NULL,
    evidence_contains VARCHAR(1000) NOT NULL,
    KEY idx_expected_context_case (case_id, id),
    CONSTRAINT fk_expected_context_case FOREIGN KEY (case_id) REFERENCES rag_evaluation_case(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE rag_evaluation_run (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    dataset_id BIGINT NOT NULL,
    baseline_run_id BIGINT,
    status VARCHAR(24) NOT NULL,
    active_marker TINYINT GENERATED ALWAYS AS (
        CASE WHEN status IN ('QUEUED', 'RUNNING') THEN 1 ELSE NULL END
    ) STORED,
    config_snapshot JSON NOT NULL,
    candidate_hit_rate DECIMAL(8,6),
    candidate_mrr DECIMAL(8,6),
    context_recall DECIMAL(8,6),
    context_precision DECIMAL(8,6),
    faithfulness DECIMAL(8,6),
    answer_relevancy DECIMAL(8,6),
    evidence_support_accuracy DECIMAL(8,6),
    no_answer_accuracy DECIMAL(8,6),
    passed TINYINT NOT NULL DEFAULT 0,
    total_cases INT NOT NULL,
    completed_cases INT NOT NULL DEFAULT 0,
    failed_cases INT NOT NULL DEFAULT 0,
    prompt_tokens INT NOT NULL DEFAULT 0,
    completion_tokens INT NOT NULL DEFAULT 0,
    latency_ms BIGINT NOT NULL DEFAULT 0,
    p95_latency_ms BIGINT NOT NULL DEFAULT 0,
    error_message VARCHAR(1000),
    triggered_by BIGINT NOT NULL,
    started_at DATETIME(3),
    completed_at DATETIME(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_evaluation_run_dataset (dataset_id, id),
    KEY idx_evaluation_run_status (status),
    UNIQUE KEY uk_evaluation_active_run (dataset_id, active_marker),
    CONSTRAINT fk_evaluation_run_dataset FOREIGN KEY (dataset_id) REFERENCES rag_evaluation_dataset(id),
    CONSTRAINT fk_evaluation_run_baseline FOREIGN KEY (baseline_run_id) REFERENCES rag_evaluation_run(id),
    CONSTRAINT fk_evaluation_run_trigger FOREIGN KEY (triggered_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE rag_evaluation_case_result (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    run_id BIGINT NOT NULL,
    case_id BIGINT NOT NULL,
    execution_json JSON NOT NULL,
    passed TINYINT NOT NULL DEFAULT 0,
    review_verdict VARCHAR(24),
    review_comment VARCHAR(1000),
    reviewed_by BIGINT,
    reviewed_at DATETIME(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_evaluation_result_case (run_id, case_id),
    KEY idx_evaluation_result_review (review_verdict),
    CONSTRAINT fk_evaluation_result_run FOREIGN KEY (run_id) REFERENCES rag_evaluation_run(id),
    CONSTRAINT fk_evaluation_result_case FOREIGN KEY (case_id) REFERENCES rag_evaluation_case(id),
    CONSTRAINT fk_evaluation_result_reviewer FOREIGN KEY (reviewed_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
