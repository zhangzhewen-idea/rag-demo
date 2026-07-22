package com.zhangzhewen.ragdemo.application.conversation;

import com.zhangzhewen.ragdemo.application.BusinessException;
import com.zhangzhewen.ragdemo.domain.conversation.AiUsage;
import com.zhangzhewen.ragdemo.domain.conversation.AnswerContext;
import com.zhangzhewen.ragdemo.domain.conversation.Conversation;
import com.zhangzhewen.ragdemo.domain.conversation.ConversationSummary;
import com.zhangzhewen.ragdemo.domain.conversation.GeneratedAnswer;
import com.zhangzhewen.ragdemo.domain.conversation.ConversationPrompt;
import com.zhangzhewen.ragdemo.domain.conversation.ContextAssemblyPolicy;
import com.zhangzhewen.ragdemo.domain.conversation.Message;
import com.zhangzhewen.ragdemo.domain.conversation.RetrievalTrace;
import com.zhangzhewen.ragdemo.domain.conversation.RetrievedChunk;
import com.zhangzhewen.ragdemo.domain.gateway.AiGateway;
import com.zhangzhewen.ragdemo.domain.gateway.ConversationGateway;
import com.zhangzhewen.ragdemo.domain.gateway.ContextSummaryGateway;
import com.zhangzhewen.ragdemo.domain.gateway.KnowledgeGateway;
import java.util.List;
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

  public static final String NO_EVIDENCE = ConversationPrompt.NO_EVIDENCE;
  private static final Logger log = LoggerFactory.getLogger(ConversationService.class);
  private static final int MAX_SUMMARY_BATCHES = 20;
  private final ConversationGateway conversations;
  private final EvidenceRetrievalService retrieval;
  private final AiGateway ai;
  private final KnowledgeGateway knowledge;
  private final ContextSummaryGateway contextSummary;
  private final ContextAssemblyPolicy contextPolicy;

  /**
   * 注入用例依赖。
   */
  public ConversationService(ConversationGateway conversations, EvidenceRetrievalService retrieval,
      AiGateway ai, KnowledgeGateway knowledge, ContextSummaryGateway contextSummary,
      ContextAssemblyPolicy contextPolicy) {
    this.conversations = conversations;
    this.retrieval = retrieval;
    this.ai = ai;
    this.knowledge = knowledge;
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
    RetrievalTrace trace = retrieval.retrieve(c.knowledgeBaseId(), question, prepared.summary(),
        history);
    List<RetrievedChunk> refs = trace.finalEvidence();
    if (refs.isEmpty()) {
      return CompletableFuture.completedFuture(
          completeAnswer(conversationId, GeneratedAnswer.refused(NO_EVIDENCE), List.of(), start,
              delta));
    }
    AnswerContext context = contextPolicy.assemble(question, prepared.summary(), history, refs);
    return CompletableFuture.supplyAsync(() -> {
      GeneratedAnswer generated = ai.generateAnswer(context);
      return completeAnswer(conversationId, generated, context.evidence(), start, delta);
    });
  }

  private ChatResult completeAnswer(Long conversationId, GeneratedAnswer generated,
      List<RetrievedChunk> evidence, long start, Consumer<String> delta) {
    String answer = generated.refused() ? NO_EVIDENCE : generated.content();
    List<RetrievedChunk> references = generated.refused() ? List.of() : evidence;
    delta.accept(answer);
    long elapsed = elapsed(start);
    AiUsage usage = generated.usage();
    Long id = conversations.saveMessage(conversationId, "ASSISTANT", answer, "COMPLETED",
        usage.promptTokens(), usage.completionTokens(), elapsed);
    if (!references.isEmpty()) {
      conversations.saveReferences(id, references);
    }
    return new ChatResult(id, answer, generated.refused(), references, elapsed);
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
  public record ChatResult(Long messageId, String answer, boolean refused,
                           List<RetrievedChunk> references, long elapsedMs) {

  }

  private record PreparedHistory(String summary, List<Message> messages) {

  }

  private record CompressionAttempt(ConversationSummary summary, boolean completed,
                                    boolean conflict) {

  }
}
