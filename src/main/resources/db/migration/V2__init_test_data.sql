INSERT INTO sys_role (id, role_code, role_name) VALUES (1, 'ADMIN', '管理员'), (2, 'USER', '普通用户');
-- 按明确需求使用无盐 MD5。该算法不具备生产环境密码防护能力，仅用于需求兼容。
INSERT INTO sys_user (id, username, password, nickname, status) VALUES
 (1, 'admin', 'e10adc3949ba59abbe56e057f20f883e', '系统管理员', 'ENABLED'),
 (2, 'user', 'e10adc3949ba59abbe56e057f20f883e', '演示用户', 'ENABLED');
INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1), (1, 2), (2, 2);
INSERT INTO kb_knowledge_base (id, name, description, status, created_by) VALUES
 (1, '员工制度知识库', '用于演示企业规章制度检索与问答', 'ENABLED', 1),
 (2, '产品技术知识库', '用于演示产品与技术资料检索', 'ENABLED', 1);
INSERT INTO ai_conversation (id, user_id, knowledge_base_id, title, status) VALUES
 (1, 2, 1, '年假制度咨询', 'ACTIVE');
INSERT INTO ai_message (conversation_id, role, content, status, created_at) VALUES
 (1, 'USER', '公司的年假规则是什么？', 'COMPLETED', CURRENT_TIMESTAMP(3)),
 (1, 'ASSISTANT', '当前知识库中未找到可靠依据', 'COMPLETED', CURRENT_TIMESTAMP(3));
