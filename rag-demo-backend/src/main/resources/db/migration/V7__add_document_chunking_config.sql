ALTER TABLE kb_document
    ADD COLUMN chunk_strategy VARCHAR(16) NOT NULL DEFAULT 'AUTO' AFTER chunk_count,
    ADD COLUMN chunk_separator VARCHAR(64) NULL AFTER chunk_strategy,
    ADD COLUMN chunk_size INT NOT NULL DEFAULT 800 AFTER chunk_separator,
    ADD COLUMN chunk_overlap INT NOT NULL DEFAULT 100 AFTER chunk_size,
    ADD COLUMN normalize_whitespace TINYINT NOT NULL DEFAULT 0 AFTER chunk_overlap;
