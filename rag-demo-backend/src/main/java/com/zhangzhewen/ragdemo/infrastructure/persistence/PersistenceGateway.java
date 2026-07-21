package com.zhangzhewen.ragdemo.infrastructure.persistence;

import com.zhangzhewen.ragdemo.domain.conversation.Conversation;
import com.zhangzhewen.ragdemo.domain.conversation.Message;
import com.zhangzhewen.ragdemo.domain.conversation.RetrievedChunk;
import com.zhangzhewen.ragdemo.domain.gateway.ConversationGateway;
import com.zhangzhewen.ragdemo.domain.gateway.DashboardGateway;
import com.zhangzhewen.ragdemo.domain.gateway.DocumentGateway;
import com.zhangzhewen.ragdemo.domain.gateway.KnowledgeGateway;
import com.zhangzhewen.ragdemo.domain.gateway.UserGateway;
import com.zhangzhewen.ragdemo.domain.identity.Role;
import com.zhangzhewen.ragdemo.domain.identity.User;
import com.zhangzhewen.ragdemo.domain.identity.UserStatus;
import com.zhangzhewen.ragdemo.domain.knowledge.DocumentStatus;
import com.zhangzhewen.ragdemo.domain.knowledge.KnowledgeBase;
import com.zhangzhewen.ragdemo.domain.knowledge.KnowledgeBaseStatus;
import com.zhangzhewen.ragdemo.domain.knowledge.KnowledgeDocument;
import com.zhangzhewen.ragdemo.infrastructure.persistence.mapper.SystemMetricsMapper;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 使用 Spring JDBC 实现事务型 Gateway，并使用 MyBatis 承载看板聚合查询。
 */
@Repository
public class PersistenceGateway implements UserGateway, KnowledgeGateway, ConversationGateway,
    DocumentGateway, DashboardGateway {

  static final String REFERENCE_INSERT =
      "INSERT INTO ai_message_reference(message_id,knowledge_base_id,document_id,source_name,chunk_index,similarity_score,rerank_score,excerpt,page_number,section_title) VALUES(?,?,?,?,?,?,?,?,?,?)";
  private final JdbcTemplate jdbc;
  private final SystemMetricsMapper metrics;

  /**
   * 注入 JDBC 与 MyBatis 聚合 Mapper。
   */
  public PersistenceGateway(JdbcTemplate jdbc, SystemMetricsMapper metrics) {
    this.jdbc = jdbc;
    this.metrics = metrics;
  }

  @Override
  public Optional<User> findByUsername(String username) {
    return listUsers("WHERE u.username=? AND u.deleted=0", username).stream().findFirst();
  }

  @Override
  public Optional<User> findUserById(Long id) {
    return listUsers("WHERE u.id=? AND u.deleted=0", id).stream().findFirst();
  }

  @Override
  public List<User> list() {
    return listUsers("WHERE u.deleted=0").stream().sorted(Comparator.comparing(User::id)).toList();
  }

  private List<User> listUsers(String where, Object... args) {
    return jdbc.query(userQuery(where),
        (rs, n) -> new User(rs.getLong("id"), rs.getString("username"), rs.getString("password"),
            rs.getString("nickname"), rs.getString("avatar_url"),
            UserStatus.valueOf(rs.getString("status")),
            parseRoles(rs.getString("roles"))), args);
  }

  static String userQuery(String where) {
    return "SELECT u.id,u.username,u.password,u.nickname,u.avatar_url,u.status," +
        "COALESCE(GROUP_CONCAT(r.role_code),'') roles FROM sys_user u LEFT JOIN sys_user_role ur ON ur.user_id=u.id "
        +
        "LEFT JOIN sys_role r ON r.id=ur.role_id " + where
        + " GROUP BY u.id,u.username,u.password,u.nickname,u.avatar_url,u.status";
  }

  private Set<Role> parseRoles(String raw) {
    if (raw == null || raw.isBlank()) {
      return Set.of();
    }
    Set<Role> roles = new HashSet<>();
    Arrays.stream(raw.split(",")).map(Role::valueOf).forEach(roles::add);
    return Set.copyOf(roles);
  }

  @Override
  @Transactional
  public Long create(String username, String password, String nickname, String avatarUrl,
      UserStatus status, Set<Role> roles) {
    long id = insert(
        "INSERT INTO sys_user(username,password,nickname,avatar_url,status) VALUES(?,?,?,?,?)",
        username, password, nickname, avatarUrl, status.name());
    replaceRoles(id, roles);
    return id;
  }

  @Override
  @Transactional
  public void update(Long id, String nickname, String avatarUrl, UserStatus status,
      Set<Role> roles) {
    jdbc.update("UPDATE sys_user SET nickname=?,avatar_url=?,status=? WHERE id=? AND deleted=0",
        nickname, avatarUrl, status.name(), id);
    replaceRoles(id, roles);
  }

  private void replaceRoles(Long userId, Set<Role> roles) {
    jdbc.update("DELETE FROM sys_user_role WHERE user_id=?", userId);
    roles.forEach(role -> jdbc.update(
        "INSERT INTO sys_user_role(user_id,role_id) SELECT ?,id FROM sys_role WHERE role_code=?",
        userId, role.name()));
  }

  @Override
  public void resetPassword(Long id, String encodedPassword) {
    jdbc.update("UPDATE sys_user SET password=? WHERE id=? AND deleted=0", encodedPassword, id);
  }

  @Override
  public void touchLastLogin(Long id) {
    jdbc.update("UPDATE sys_user SET last_login_at=NOW(3) WHERE id=?", id);
  }

  private static final RowMapper<KnowledgeBase> KB_MAPPER = (rs, n) -> new KnowledgeBase(
      rs.getLong("id"), rs.getString("name"), rs.getString("description"),
      rs.getString("cover_url"), KnowledgeBaseStatus.valueOf(rs.getString("status")));

  @Override
  public List<KnowledgeBase> listEnabled() {
    return jdbc.query(
        "SELECT id,name,description,cover_url,status FROM kb_knowledge_base WHERE deleted=0 AND status='ENABLED' ORDER BY id DESC",
        KB_MAPPER);
  }

  @Override
  public List<KnowledgeBase> listAll() {
    return jdbc.query(
        "SELECT id,name,description,cover_url,status FROM kb_knowledge_base WHERE deleted=0 ORDER BY id DESC",
        KB_MAPPER);
  }

  @Override
  public Optional<KnowledgeBase> findKnowledgeById(Long id) {
    return jdbc.query(
        "SELECT id,name,description,cover_url,status FROM kb_knowledge_base WHERE id=? AND deleted=0",
        KB_MAPPER, id).stream().findFirst();
  }

  @Override
  public Long create(String name, String description, String coverUrl, Long creatorId) {
    return insert(
        "INSERT INTO kb_knowledge_base(name,description,cover_url,status,created_by) VALUES(?,?,?,'ENABLED',?)",
        name, description, coverUrl, creatorId);
  }

  @Override
  public void update(Long id, String name, String description, String coverUrl,
      KnowledgeBaseStatus status) {
    jdbc.update(
        "UPDATE kb_knowledge_base SET name=?,description=?,cover_url=?,status=? WHERE id=? AND deleted=0",
        name, description, coverUrl, status.name(), id);
  }

  @Override
  public void deleteKnowledgeBase(Long id) {
    jdbc.update("UPDATE kb_knowledge_base SET deleted=1 WHERE id=?", id);
  }

  private static final RowMapper<Conversation> CONVERSATION_MAPPER = (rs, n) -> new Conversation(
      rs.getLong("id"), rs.getLong("user_id"), rs.getLong("knowledge_base_id"),
      rs.getString("title"), rs.getString("status"));

  @Override
  public Long create(Long userId, Long knowledgeBaseId, String title) {
    return insert(
        "INSERT INTO ai_conversation(user_id,knowledge_base_id,title,status) VALUES(?,?,?,'ACTIVE')",
        userId, knowledgeBaseId, title);
  }

  @Override
  public List<Conversation> listByUser(Long userId) {
    return jdbc.query(
        "SELECT id,user_id,knowledge_base_id,title,status FROM ai_conversation WHERE user_id=? AND deleted=0 ORDER BY updated_at DESC",
        CONVERSATION_MAPPER, userId);
  }

  @Override
  public Optional<Conversation> findConversationById(Long id) {
    return jdbc.query(
        "SELECT id,user_id,knowledge_base_id,title,status FROM ai_conversation WHERE id=? AND deleted=0",
        CONVERSATION_MAPPER, id).stream().findFirst();
  }

  @Override
  public void rename(Long id, String title) {
    jdbc.update("UPDATE ai_conversation SET title=? WHERE id=? AND deleted=0", title, id);
  }

  @Override
  public void deleteConversation(Long id) {
    jdbc.update("UPDATE ai_conversation SET deleted=1 WHERE id=?", id);
  }

  @Override
  public List<Message> recentMessages(Long conversationId, int limit) {
    List<Message> messages = jdbc.query(
        "SELECT * FROM (SELECT id,conversation_id,role,content,status,prompt_tokens,completion_tokens,elapsed_ms,created_at FROM ai_message WHERE conversation_id=? ORDER BY id DESC LIMIT ?) x ORDER BY id",
        (rs, n) -> new Message(rs.getLong("id"), rs.getLong("conversation_id"),
            rs.getString("role"), rs.getString("content"), rs.getString("status"),
            (Integer) rs.getObject("prompt_tokens"), (Integer) rs.getObject("completion_tokens"),
            (Long) rs.getObject("elapsed_ms"), rs.getTimestamp("created_at").toLocalDateTime()),
        conversationId, limit);
    return messages;
  }

  @Override
  public Long saveMessage(Long conversationId, String role, String content, String status,
      Integer promptTokens, Integer completionTokens, Long elapsedMs) {
    return insert(
        "INSERT INTO ai_message(conversation_id,role,content,status,prompt_tokens,completion_tokens,elapsed_ms) VALUES(?,?,?,?,?,?,?)",
        conversationId, role, content, status, promptTokens, completionTokens, elapsedMs);
  }

  @Override
  public void saveReferences(Long messageId, List<RetrievedChunk> references) {
    references.forEach(r -> jdbc.update(REFERENCE_INSERT,
        messageId, r.knowledgeBaseId(), r.documentId(), r.sourceName(), r.chunkIndex(),
        r.similarityScore(), r.rerankScore(), r.excerpt(), r.pageNumber(), r.sectionTitle()));
  }

  private static final RowMapper<KnowledgeDocument> DOCUMENT_MAPPER = (rs, n) -> new KnowledgeDocument(
      rs.getLong("id"), rs.getLong("knowledge_base_id"), rs.getString("original_name"),
      rs.getString("stored_name"), rs.getString("storage_path"), rs.getString("extension"),
      rs.getString("mime_type"), rs.getLong("file_size"), rs.getString("content_hash"),
      DocumentStatus.valueOf(rs.getString("status")), rs.getInt("chunk_count"),
      rs.getInt("retry_count"), rs.getString("failure_stage"), rs.getString("failure_reason"));

  @Override
  public Long create(KnowledgeDocument d, Long creatorId) {
    return insert(
        "INSERT INTO kb_document(knowledge_base_id,original_name,stored_name,storage_path,extension,mime_type,file_size,content_hash,status,created_by) VALUES(?,?,?,?,?,?,?,?,?,?)",
        d.knowledgeBaseId(), d.originalName(), d.storedName(), d.storagePath(), d.extension(),
        d.mimeType(), d.fileSize(), d.contentHash(), d.status().name(), creatorId);
  }

  @Override
  public Optional<KnowledgeDocument> findDocumentById(Long id) {
    return jdbc.query("SELECT * FROM kb_document WHERE id=? AND deleted=0", DOCUMENT_MAPPER, id)
        .stream().findFirst();
  }

  @Override
  public List<KnowledgeDocument> listByKnowledgeBase(Long knowledgeBaseId) {
    return jdbc.query(
        "SELECT * FROM kb_document WHERE knowledge_base_id=? AND deleted=0 ORDER BY id DESC",
        DOCUMENT_MAPPER, knowledgeBaseId);
  }

  @Override
  public boolean transit(Long id, DocumentStatus expected, DocumentStatus target) {
    return jdbc.update(
        "UPDATE kb_document SET status=?,failure_stage=NULL,failure_reason=NULL WHERE id=? AND status=? AND deleted=0",
        target.name(), id, expected.name()) == 1;
  }

  @Override
  public void markReady(Long id, int chunkCount) {
    jdbc.update(
        "UPDATE kb_document SET status='READY',chunk_count=?,failure_stage=NULL,failure_reason=NULL WHERE id=? AND status='PROCESSING'",
        chunkCount, id);
  }

  @Override
  public void markFailed(Long id, String stage, String reason, boolean incrementRetry) {
    jdbc.update(
        "UPDATE kb_document SET status='FAILED',failure_stage=?,failure_reason=?,retry_count=retry_count+? WHERE id=?",
        stage, reason == null ? "未知错误" : reason.substring(0, Math.min(500, reason.length())),
        incrementRetry ? 1 : 0, id);
  }

  @Override
  public int failInterruptedTasks() {
    return jdbc.update(
        "UPDATE kb_document SET status='FAILED',failure_stage='INTERRUPTED',failure_reason='服务重启导致任务中断，请重试' WHERE deleted=0 AND status IN ('PENDING','PROCESSING')");
  }

  @Override
  public void logicalDelete(Long id) {
    jdbc.update("UPDATE kb_document SET deleted=1 WHERE id=? AND status='DELETING'", id);
  }

  @Override
  public boolean hasProcessing(Long knowledgeBaseId) {
    Integer count = jdbc.queryForObject(
        "SELECT COUNT(*) FROM kb_document WHERE knowledge_base_id=? AND deleted=0 AND status IN ('PENDING','PROCESSING','DELETING')",
        Integer.class, knowledgeBaseId);
    return count != null && count > 0;
  }

  @Override
  public Map<String, Object> snapshot() {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("knowledgeBaseCount", metrics.knowledgeBaseCount());
    result.put("documentCount", metrics.documentCount());
    result.put("userCount", metrics.userCount());
    result.put("questionCount", metrics.questionCount());
    result.put("questionTrend", jdbc.queryForList(
        "SELECT DATE(created_at) date,COUNT(*) value FROM ai_message WHERE role='USER' AND created_at>=CURRENT_DATE-INTERVAL 6 DAY GROUP BY DATE(created_at) ORDER BY date"));
    result.put("fileTypes", jdbc.queryForList(
        "SELECT extension name,COUNT(*) value FROM kb_document WHERE deleted=0 GROUP BY extension ORDER BY value DESC"));
    result.put("popularKnowledgeBases", jdbc.queryForList(
        "SELECT k.name,COUNT(c.id) value FROM kb_knowledge_base k LEFT JOIN ai_conversation c ON c.knowledge_base_id=k.id AND c.deleted=0 WHERE k.deleted=0 GROUP BY k.id,k.name ORDER BY value DESC LIMIT 5"));
    return result;
  }

  private long insert(String sql, Object... params) {
    KeyHolder keys = new GeneratedKeyHolder();
    jdbc.update(connection -> {
      PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
      for (int i = 0; i < params.length; i++) {
        ps.setObject(i + 1, params[i]);
      }
      return ps;
    }, keys);
    if (keys.getKey() == null) {
      throw new IllegalStateException("数据库未返回主键");
    }
    return keys.getKey().longValue();
  }
}
