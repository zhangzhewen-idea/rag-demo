package com.zhangzhewen.ragdemo.application.conversation;

import com.zhangzhewen.ragdemo.application.BusinessException;
import com.zhangzhewen.ragdemo.domain.conversation.Conversation;
import com.zhangzhewen.ragdemo.domain.conversation.Message;
import com.zhangzhewen.ragdemo.domain.conversation.RetrievalPolicy;
import com.zhangzhewen.ragdemo.domain.conversation.RetrievedChunk;
import com.zhangzhewen.ragdemo.domain.gateway.AiGateway;
import com.zhangzhewen.ragdemo.domain.gateway.ConversationGateway;
import com.zhangzhewen.ragdemo.domain.gateway.DocumentGateway;
import com.zhangzhewen.ragdemo.domain.gateway.KnowledgeGateway;
import com.zhangzhewen.ragdemo.domain.gateway.VectorGateway;
import com.zhangzhewen.ragdemo.domain.knowledge.DocumentStatus;
import com.zhangzhewen.ragdemo.domain.knowledge.KnowledgeDocument;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 会话所有权、检索、拒答、流式生成和引用保存用例。
 */
@Service
public class ConversationService {

  public static final String NO_EVIDENCE = "当前知识库中未找到可靠依据";
  private final ConversationGateway conversations;
  private final VectorGateway vectors;
  private final AiGateway ai;
  private final KnowledgeGateway knowledge;
  private final DocumentGateway documents;
  private final RetrievalPolicy policy;

  /**
   * 注入用例依赖。
   */
  public ConversationService(ConversationGateway conversations, VectorGateway vectors, AiGateway ai,
      KnowledgeGateway knowledge, DocumentGateway documents, RetrievalPolicy policy) {
    this.conversations = conversations;
    this.vectors = vectors;
    this.ai = ai;
    this.knowledge = knowledge;
    this.documents = documents;
    this.policy = policy;
  }

  /**
   * 创建绑定单一知识库的会话。
   */
  public Long create(Long userId, Long knowledgeBaseId, String title) {
    requireSearchable(knowledgeBaseId);
    return conversations.create(userId, knowledgeBaseId,
        title == null || title.isBlank() ? "新会话" : title);
  }

  /**
   * 查询本人会话。
   */
  public List<Conversation> list(Long userId) {
    return conversations.listByUser(userId);
  }

  /**
   * 查询会话及历史。
   */
  public Detail detail(Long id, Long userId) {
    Conversation c = requireOwned(id, userId);
    return new Detail(c, conversations.recentMessages(id, 100));
  }

  /**
   * 重命名本人会话。
   */
  public void rename(Long id, Long userId, String title) {
    requireOwned(id, userId);
    conversations.rename(id, title);
  }

  /**
   * 删除本人会话。
   */
  public void delete(Long id, Long userId) {
    requireOwned(id, userId);
    conversations.deleteConversation(id);
  }

  /**
   * 异步执行流式回答，调用者负责把 delta 转为 SSE。
   */
  public CompletableFuture<ChatResult> chat(Long conversationId, Long userId, String question,
      Consumer<String> delta) {
    Conversation c = requireOwned(conversationId, userId);
    requireSearchable(c.knowledgeBaseId());
    List<Message> history = conversations.recentMessages(conversationId, 20);
    conversations.saveMessage(conversationId, "USER", question, "COMPLETED", null, null, null);
    long start = System.nanoTime();
    boolean overview = isOverviewQuestion(question);
    List<RetrievedChunk> refs = retrieve(c.knowledgeBaseId(), question, history, overview);
    if (refs.isEmpty()) {
      delta.accept(NO_EVIDENCE);
      Long id = conversations.saveMessage(conversationId, "ASSISTANT", NO_EVIDENCE, "COMPLETED",
          null, null, elapsed(start));
      return CompletableFuture.completedFuture(
          new ChatResult(id, NO_EVIDENCE, List.of(), elapsed(start)));
    }
    StringBuilder answer = new StringBuilder();
    return CompletableFuture.supplyAsync(() -> {
      ai.streamAnswer(question, history, refs, p -> {
        answer.append(p);
        delta.accept(p);
      });
      long elapsed = elapsed(start);
      Long id = conversations.saveMessage(conversationId, "ASSISTANT", answer.toString(),
          "COMPLETED", null, null, elapsed);
      conversations.saveReferences(id, refs);
      return new ChatResult(id, answer.toString(), refs, elapsed);
    });
  }

  private List<RetrievedChunk> retrieve(Long knowledgeBaseId, String question,
      List<Message> history, boolean overview) {
    if (!overview) {
      return vectors.search(knowledgeBaseId, contextualQuery(question, history), policy.topK(),
          policy.similarityThreshold());
    }
    int perDocumentTopK = Math.max(2, Math.min(4, policy.topK() / 2));
    List<RetrievedChunk> result = new ArrayList<>();
    for (KnowledgeDocument document : documents.listByKnowledgeBase(knowledgeBaseId)) {
      if (document.status() != DocumentStatus.READY) {
        continue;
      }
      String query = document.originalName() + " 主要内容 核心要点 完整概览";
      result.addAll(vectors.searchDocument(knowledgeBaseId, document.id(), query, perDocumentTopK,
          policy.similarityThreshold()));
    }
    return result;
  }

  private String contextualQuery(String question, List<Message> history) {
    String normalized = normalize(question);
    boolean refersToHistory = List.of("这", "那", "上述", "前面", "刚才", "其他", "除了", "它")
        .stream().anyMatch(normalized::contains);
    if (!refersToHistory) {
      return question;
    }
    for (int i = history.size() - 1; i >= 0; i--) {
      Message message = history.get(i);
      if ("ASSISTANT".equals(message.role()) && !message.content().isBlank()) {
        String context = message.content().length() > 600
            ? message.content().substring(0, 600) : message.content();
        return context + "\n追问：" + question;
      }
    }
    return question;
  }

  private boolean isOverviewQuestion(String question) {
    String normalized = normalize(question);
    return normalized.equals("你知道什么") || normalized.equals("知识库有什么")
        || normalized.contains("有哪些内容") || normalized.contains("全部内容")
        || normalized.contains("所有内容") || normalized.contains("完整概览")
        || normalized.contains("全面介绍") || normalized.contains("概括知识库");
  }

  private String normalize(String value) {
    return value.toLowerCase(Locale.ROOT).replaceAll("[\\s，。！？、,.!?：:；;]", "");
  }

  private long elapsed(long start) {
    return (System.nanoTime() - start) / 1_000_000;
  }

  private Conversation requireOwned(Long id, Long userId) {
    Conversation c = conversations.findConversationById(id).orElseThrow(
        () -> new BusinessException("CONVERSATION_NOT_FOUND", "会话不存在", HttpStatus.NOT_FOUND));
      if (!c.ownedBy(userId)) {
          throw new BusinessException("CONVERSATION_FORBIDDEN", "不能访问其他用户的会话",
              HttpStatus.FORBIDDEN);
      }
    return c;
  }

  private void requireSearchable(Long id) {
    var kb = knowledge.findKnowledgeById(id).orElseThrow(
        () -> new BusinessException("KB_NOT_FOUND", "知识库不存在", HttpStatus.NOT_FOUND));
      if (!kb.searchable()) {
          throw new BusinessException("KB_DISABLED", "知识库已停用", HttpStatus.CONFLICT);
      }
  }

  /**
   * 会话详情。
   */
  public record Detail(Conversation conversation, List<Message> messages) {

  }

  /**
   * 回答完成结果。
   */
  public record ChatResult(Long messageId, String answer, List<RetrievedChunk> references,
                           long elapsedMs) {

  }
}
