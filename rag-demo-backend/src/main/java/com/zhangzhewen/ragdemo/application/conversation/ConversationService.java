package com.zhangzhewen.ragdemo.application.conversation;

import com.zhangzhewen.ragdemo.application.BusinessException;
import com.zhangzhewen.ragdemo.domain.conversation.AiUsage;
import com.zhangzhewen.ragdemo.domain.conversation.AnswerContext;
import com.zhangzhewen.ragdemo.domain.conversation.Conversation;
import com.zhangzhewen.ragdemo.domain.conversation.ConversationSummary;
import com.zhangzhewen.ragdemo.domain.conversation.ContextAssemblyPolicy;
import com.zhangzhewen.ragdemo.domain.conversation.Message;
import com.zhangzhewen.ragdemo.domain.conversation.ReciprocalRankFusion;
import com.zhangzhewen.ragdemo.domain.conversation.RetrievalPolicy;
import com.zhangzhewen.ragdemo.domain.conversation.RetrievalQuery;
import com.zhangzhewen.ragdemo.domain.conversation.RetrievedChunk;
import com.zhangzhewen.ragdemo.domain.gateway.AiGateway;
import com.zhangzhewen.ragdemo.domain.gateway.ConversationGateway;
import com.zhangzhewen.ragdemo.domain.gateway.ContextSummaryGateway;
import com.zhangzhewen.ragdemo.domain.gateway.DocumentRerankGateway;
import com.zhangzhewen.ragdemo.domain.gateway.DocumentSearchGateway;
import com.zhangzhewen.ragdemo.domain.gateway.KnowledgeGateway;
import com.zhangzhewen.ragdemo.domain.gateway.QueryExpansionGateway;
import com.zhangzhewen.ragdemo.domain.gateway.QueryRewriteGateway;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 会话所有权、检索、拒答、流式生成和引用保存用例。
 */
@Service
public class ConversationService {

  public static final String NO_EVIDENCE = "当前知识库中未找到可靠依据";
  private static final Logger log = LoggerFactory.getLogger(ConversationService.class);
  private static final int MAX_SUMMARY_BATCHES = 20;
  private final ConversationGateway conversations;
  private final DocumentSearchGateway search;
  private final DocumentRerankGateway rerank;
  private final AiGateway ai;
  private final KnowledgeGateway knowledge;
  private final QueryRewriteGateway queryRewrite;
  private final QueryExpansionGateway queryExpansion;
  private final RetrievalPolicy policy;
  private final ContextSummaryGateway contextSummary;
  private final ContextAssemblyPolicy contextPolicy;

  /**
   * 注入用例依赖。
   */
  public ConversationService(ConversationGateway conversations, DocumentSearchGateway search,
      DocumentRerankGateway rerank, AiGateway ai, KnowledgeGateway knowledge,
      QueryRewriteGateway queryRewrite, QueryExpansionGateway queryExpansion,
      RetrievalPolicy policy, ContextSummaryGateway contextSummary,
      ContextAssemblyPolicy contextPolicy) {
    this.conversations = conversations;
    this.search = search;
    this.rerank = rerank;
    this.ai = ai;
    this.knowledge = knowledge;
    this.queryRewrite = queryRewrite;
    this.queryExpansion = queryExpansion;
    this.policy = policy;
    this.contextSummary = contextSummary;
    this.contextPolicy = contextPolicy;
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
    PreparedHistory prepared = prepareHistory(conversationId);
    List<Message> history = prepared.messages();
    conversations.saveMessage(conversationId, "USER", question, "COMPLETED", null, null, null);
    long start = System.nanoTime();
    List<RetrievedChunk> refs = retrieve(c.knowledgeBaseId(), question, prepared.summary(), history);
    if (refs.isEmpty()) {
      delta.accept(NO_EVIDENCE);
      Long id = conversations.saveMessage(conversationId, "ASSISTANT", NO_EVIDENCE, "COMPLETED",
          null, null, elapsed(start));
      return CompletableFuture.completedFuture(
          new ChatResult(id, NO_EVIDENCE, List.of(), elapsed(start)));
    }
    StringBuilder answer = new StringBuilder();
    AnswerContext context = contextPolicy.assemble(question, prepared.summary(), history, refs);
    return CompletableFuture.supplyAsync(() -> {
      AiUsage usage = ai.streamAnswer(context, p -> {
        answer.append(p);
        delta.accept(p);
      });
      long elapsed = elapsed(start);
      Long id = conversations.saveMessage(conversationId, "ASSISTANT", answer.toString(),
          "COMPLETED", usage.promptTokens(), usage.completionTokens(), elapsed);
      conversations.saveReferences(id, context.evidence());
      return new ChatResult(id, answer.toString(), context.evidence(), elapsed);
    });
  }

  private List<RetrievedChunk> retrieve(Long knowledgeBaseId, String question, String summary,
      List<Message> history) {
    String rewritten = queryRewrite.rewrite(contextualQuery(question, summary, history));
    List<RetrievalQuery> plans = queryExpansion.plan(rewritten);
    List<List<RetrievedChunk>> rankings = plans.stream()
        .map(plan -> search.search(knowledgeBaseId, plan, policy.candidateTopK(),
            policy.similarityThreshold()))
        .toList();
    List<RetrievedChunk> candidates = ReciprocalRankFusion.fuse(rankings, policy.candidateTopK());
    if (candidates.isEmpty()) {
      return candidates;
    }
    return rerank.rerank(rewritten, candidates, policy.topK());
  }

  private String contextualQuery(String question, String summary, List<Message> history) {
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
        String prefix = summary == null || summary.isBlank() ? "" : summary + "\n";
        return prefix + context + "\n追问：" + question;
      }
    }
    return summary == null || summary.isBlank() ? question : summary + "\n追问：" + question;
  }

  private PreparedHistory prepareHistory(Long conversationId) {
    ConversationSummary summary = conversations.findSummary(conversationId)
        .orElseGet(() -> ConversationSummary.empty(conversationId));
    List<Message> messages = conversations.messagesAfter(conversationId,
        summary.throughMessageId());
    int batches = 0;
    while (contextPolicy.shouldSummarize(messages) && batches++ < MAX_SUMMARY_BATCHES) {
      CompressionAttempt attempt = compressOnce(summary, messages);
      if (!attempt.completed()) {
        if (!attempt.conflict()) {
          break;
        }
        summary = conversations.findSummary(conversationId)
            .orElseGet(() -> ConversationSummary.empty(conversationId));
        messages = conversations.messagesAfter(conversationId, summary.throughMessageId());
        attempt = compressOnce(summary, messages);
        if (!attempt.completed()) {
          break;
        }
      }
      summary = attempt.summary();
      long cursor = summary.throughMessageId();
      messages = messages.stream().filter(message -> message.id() > cursor).toList();
    }
    return new PreparedHistory(summary.content(), messages);
  }

  private CompressionAttempt compressOnce(ConversationSummary summary, List<Message> messages) {
    List<Message> batch = contextPolicy.nextSummaryBatch(summary.content(), messages);
    if (batch.isEmpty()) {
      return new CompressionAttempt(summary, false, false);
    }
    String compressed;
    try {
      compressed = contextSummary.summarize(summary.content(), batch,
          contextPolicy.maxSummaryTokens());
    } catch (RuntimeException exception) {
      log.warn("会话摘要失败，回退到确定性上下文裁剪: conversationId={}",
          summary.conversationId(), exception);
      return new CompressionAttempt(summary, false, false);
    }
    if (compressed == null || compressed.isBlank()) {
      log.warn("会话摘要为空，回退到确定性上下文裁剪: conversationId={}",
          summary.conversationId());
      return new CompressionAttempt(summary, false, false);
    }
    long throughMessageId = batch.getLast().id();
    ConversationSummary updated = new ConversationSummary(summary.conversationId(), compressed,
        throughMessageId, summary.version() + 1);
    boolean saved = conversations.saveSummary(updated, summary.version());
    return new CompressionAttempt(saved ? updated : summary, saved, !saved);
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

  private record PreparedHistory(String summary, List<Message> messages) {

  }

  private record CompressionAttempt(ConversationSummary summary, boolean completed,
                                    boolean conflict) {

  }
}
