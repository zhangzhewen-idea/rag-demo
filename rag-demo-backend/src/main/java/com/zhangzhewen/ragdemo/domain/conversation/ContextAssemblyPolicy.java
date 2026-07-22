package com.zhangzhewen.ragdemo.domain.conversation;

import com.zhangzhewen.ragdemo.domain.gateway.TokenEstimator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 按 token 预算保留近期对话、滚动摘要和多来源证据。
 */
public class ContextAssemblyPolicy {

  private static final int STRUCTURAL_RESERVE = 128;
  private static final int MIN_CHUNK_TOKENS = 64;
  private final int maxInputTokens;
  private final int maxCompletionTokens;
  private final int recentTurns;
  private final int summaryTriggerTokens;
  private final int maxSummaryTokens;
  private final int minEvidenceTokens;
  private final TokenEstimator tokens;

  public ContextAssemblyPolicy(int maxInputTokens, int maxCompletionTokens, int recentTurns,
      int summaryTriggerTokens, int maxSummaryTokens, int minEvidenceTokens,
      TokenEstimator tokens) {
    if (maxInputTokens <= 0 || maxCompletionTokens <= 0 || recentTurns <= 0
        || summaryTriggerTokens <= 0 || maxSummaryTokens <= 0 || minEvidenceTokens <= 0
        || minEvidenceTokens >= maxInputTokens) {
      throw new IllegalArgumentException("上下文预算配置不合法");
    }
    this.maxInputTokens = maxInputTokens;
    this.maxCompletionTokens = maxCompletionTokens;
    this.recentTurns = recentTurns;
    this.summaryTriggerTokens = summaryTriggerTokens;
    this.maxSummaryTokens = maxSummaryTokens;
    this.minEvidenceTokens = minEvidenceTokens;
    this.tokens = tokens;
    int minimumPrompt = tokens.estimate(ConversationPrompt.SYSTEM)
        + tokens.estimate(ConversationPrompt.user("", "", List.of(), List.of()))
        + minEvidenceTokens;
    if (minimumPrompt >= maxInputTokens) {
      throw new IllegalArgumentException("输入预算不足以容纳 Prompt 边界和最小证据");
    }
  }

  /**
   * 未压缩消息超过阈值且存在近期窗口之外的消息时触发摘要。
   */
  public boolean shouldSummarize(List<Message> messages) {
    return messageTokens(messages) > summaryTriggerTokens && !summaryCandidates(messages).isEmpty();
  }

  /**
   * 返回最近若干用户轮次之前、可安全纳入滚动摘要的消息。
   */
  public List<Message> summaryCandidates(List<Message> messages) {
    int start = recentWindowStart(messages);
    return List.copyOf(messages.subList(0, start));
  }

  /**
   * 限制单次摘要输入，避免压缩调用自身超过模型预算。
   */
  public List<Message> nextSummaryBatch(String previousSummary, List<Message> messages) {
    List<Message> candidates = summaryCandidates(messages);
    if (candidates.isEmpty()) {
      return List.of();
    }
    int budget = Math.max(MIN_CHUNK_TOKENS,
        maxInputTokens - maxSummaryTokens - tokens.estimate(previousSummary) - STRUCTURAL_RESERVE);
    List<Message> batch = new ArrayList<>();
    int used = 0;
    for (Message message : candidates) {
      int cost = messageTokens(message);
      if (!batch.isEmpty() && used + cost > budget) {
        break;
      }
      if (batch.isEmpty() && cost > budget) {
        break;
      }
      batch.add(message);
      used += cost;
    }
    return List.copyOf(batch);
  }

  /**
   * 装配最终回答上下文，证据按来源多样性和 rerank 顺序做抽取式裁剪。
   */
  public AnswerContext assemble(String question, String summary, List<Message> messages,
      List<RetrievedChunk> evidence) {
    String fittedSummary = tokens.truncate(summary == null ? "" : summary, maxSummaryTokens);
    int evidenceReserve = evidence.isEmpty() ? 0 : minEvidenceTokens;
    int withoutQuestion = tokens.estimate(ConversationPrompt.SYSTEM)
        + tokens.estimate(ConversationPrompt.user("", fittedSummary, List.of(), List.of()));
    String fittedQuestion = tokens.truncate(question,
        Math.max(0, maxInputTokens - evidenceReserve - withoutQuestion));

    List<Message> fittedMessages = fitRecentMessages(fittedQuestion, fittedSummary, messages,
        evidenceReserve);
    int fixed = tokens.estimate(ConversationPrompt.user(fittedQuestion, fittedSummary,
        fittedMessages, List.of())) + tokens.estimate(ConversationPrompt.SYSTEM);
    int evidenceBudget = Math.max(0, maxInputTokens - fixed);
    List<RetrievedChunk> fittedEvidence = fitEvidence(evidence, evidenceBudget);

    String userPrompt = ConversationPrompt.user(fittedQuestion, fittedSummary, fittedMessages,
        fittedEvidence);
    int estimated = tokens.estimate(ConversationPrompt.SYSTEM) + tokens.estimate(userPrompt);
    return new AnswerContext(ConversationPrompt.SYSTEM, userPrompt, fittedEvidence,
        estimated, maxCompletionTokens);
  }

  public int maxSummaryTokens() {
    return maxSummaryTokens;
  }

  private List<Message> fitRecentMessages(String question, String summary, List<Message> messages,
      int evidenceReserve) {
    int fixed = tokens.estimate(ConversationPrompt.user(question, summary, List.of(), List.of()))
        + tokens.estimate(ConversationPrompt.SYSTEM);
    int budget = Math.max(0, maxInputTokens - fixed - evidenceReserve);
    List<Message> keptReversed = new ArrayList<>();
    int used = 0;
    for (int i = messages.size() - 1; i >= 0; i--) {
      Message message = messages.get(i);
      int cost = messageTokens(message);
      if (used + cost <= budget) {
        keptReversed.add(message);
        used += cost;
        continue;
      }
      if (keptReversed.isEmpty() && budget > 0) {
        int contentBudget = budget - tokens.estimate(message.role()) - 4;
        keptReversed.add(copyWithContent(message,
            tokens.truncate(message.content(), Math.max(0, contentBudget))));
      }
      break;
    }
    List<Message> result = new ArrayList<>(keptReversed.size());
    for (int i = keptReversed.size() - 1; i >= 0; i--) {
      result.add(keptReversed.get(i));
    }
    return List.copyOf(result);
  }

  private List<RetrievedChunk> fitEvidence(List<RetrievedChunk> evidence, int budget) {
    if (budget <= 0 || evidence.isEmpty()) {
      return List.of();
    }
    List<Integer> primary = primaryEvidence(evidence);
    Set<Integer> primaryIndexes = new HashSet<>(primary);
    List<Integer> order = new ArrayList<>(primary);
    for (int i = 0; i < evidence.size(); i++) {
      if (!primaryIndexes.contains(i)) {
        order.add(i);
      }
    }
    List<RetrievedChunk> result = new ArrayList<>();
    int remaining = budget;
    for (int position = 0; position < order.size(); position++) {
      Integer index = order.get(position);
      RetrievedChunk item = evidence.get(index);
      String prefix = "[来源: " + item.sourceName() + ", 切片: " + item.chunkIndex() + "]\n";
      int overhead = tokens.estimate(prefix) + (result.isEmpty() ? 0 : 5);
      if (remaining <= overhead) {
        break;
      }
      int excerptBudget = remaining - overhead;
      if (position < primary.size()) {
        int primaryLeft = primary.size() - position;
        excerptBudget = Math.min(excerptBudget,
            Math.max(MIN_CHUNK_TOKENS, remaining / primaryLeft - overhead));
      }
      String excerpt = tokens.truncate(item.excerpt(), excerptBudget);
      if (tokens.estimate(excerpt) < Math.min(MIN_CHUNK_TOKENS,
          tokens.estimate(item.excerpt()))) {
        continue;
      }
      result.add(copyWithExcerpt(item, excerpt));
      remaining -= overhead + tokens.estimate(excerpt);
    }
    return List.copyOf(result);
  }

  private List<Integer> primaryEvidence(List<RetrievedChunk> evidence) {
    Set<String> sources = new HashSet<>();
    Set<Integer> selected = new LinkedHashSet<>();
    for (int i = 0; i < evidence.size(); i++) {
      if (sources.add(evidence.get(i).sourceName())) {
        selected.add(i);
      }
    }
    return List.copyOf(selected);
  }

  private int recentWindowStart(List<Message> messages) {
    int users = 0;
    for (int i = messages.size() - 1; i >= 0; i--) {
      if ("USER".equals(messages.get(i).role())) {
        users++;
        if (users == recentTurns) {
          return i;
        }
      }
    }
    return 0;
  }

  private int messageTokens(List<Message> messages) {
    return messages.stream().mapToInt(this::messageTokens).sum();
  }

  private int messageTokens(Message message) {
    return tokens.estimate(message.role()) + tokens.estimate(message.content()) + 4;
  }

  private Message copyWithContent(Message message, String content) {
    return new Message(message.id(), message.conversationId(), message.role(), content,
        message.status(), message.promptTokens(), message.completionTokens(), message.elapsedMs(),
        message.createdAt());
  }

  private RetrievedChunk copyWithExcerpt(RetrievedChunk item, String excerpt) {
    return new RetrievedChunk(item.knowledgeBaseId(), item.documentId(), item.sourceName(),
        item.chunkIndex(), item.similarityScore(), excerpt, item.pageNumber(), item.sectionTitle(),
        item.rerankScore());
  }
}
