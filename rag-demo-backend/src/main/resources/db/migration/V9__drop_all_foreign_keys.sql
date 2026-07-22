-- 移除当前 schema 的全部外键约束，保留各表已有索引。
ALTER TABLE sys_user_role
    DROP FOREIGN KEY fk_user_role_user,
    DROP FOREIGN KEY fk_user_role_role;

ALTER TABLE kb_knowledge_base
    DROP FOREIGN KEY fk_kb_creator;

ALTER TABLE kb_document
    DROP FOREIGN KEY fk_document_kb,
    DROP FOREIGN KEY fk_document_creator;

ALTER TABLE ai_conversation
    DROP FOREIGN KEY fk_conversation_user,
    DROP FOREIGN KEY fk_conversation_kb;

ALTER TABLE ai_message
    DROP FOREIGN KEY fk_message_conversation;

ALTER TABLE ai_message_reference
    DROP FOREIGN KEY fk_reference_message,
    DROP FOREIGN KEY fk_reference_kb,
    DROP FOREIGN KEY fk_reference_document;

ALTER TABLE ai_conversation_summary
    DROP FOREIGN KEY fk_conversation_summary_conversation,
    DROP FOREIGN KEY fk_conversation_summary_message;

ALTER TABLE rag_evaluation_dataset
    DROP FOREIGN KEY fk_evaluation_dataset_kb,
    DROP FOREIGN KEY fk_evaluation_dataset_creator;

ALTER TABLE rag_evaluation_case
    DROP FOREIGN KEY fk_evaluation_case_dataset;

ALTER TABLE rag_evaluation_expected_context
    DROP FOREIGN KEY fk_expected_context_case;

ALTER TABLE rag_evaluation_run
    DROP FOREIGN KEY fk_evaluation_run_dataset,
    DROP FOREIGN KEY fk_evaluation_run_baseline,
    DROP FOREIGN KEY fk_evaluation_run_trigger;

ALTER TABLE rag_evaluation_case_result
    DROP FOREIGN KEY fk_evaluation_result_run,
    DROP FOREIGN KEY fk_evaluation_result_case,
    DROP FOREIGN KEY fk_evaluation_result_reviewer;
