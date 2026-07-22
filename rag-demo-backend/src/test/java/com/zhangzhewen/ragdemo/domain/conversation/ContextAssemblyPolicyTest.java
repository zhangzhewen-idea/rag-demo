package com.zhangzhewen.ragdemo.domain.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import com.zhangzhewen.ragdemo.domain.gateway.TokenEstimator;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 长会话上下文预算与证据裁剪规则测试。
 */
class ContextAssemblyPolicyTest {

  private final TokenEstimator tokens = new CodePointTokenEstimator();

  @Test
  void keepsLatestFourTurnsOutsideSummary() {
    ContextAssemblyPolicy policy = policy(24000, 8000, 1200, 8000);
    List<Message> messages = messages(5, "内容");

    assertThat(policy.summaryCandidates(messages)).extracting(Message::id)
        .containsExactly(1L, 2L);
  }

  @Test
  void keepsOneTopEvidencePerSourceWithinBudget() {
    ContextAssemblyPolicy policy = policy(1200, 300, 160, 500);
    List<RetrievedChunk> evidence = List.of(
        chunk(1L, "来源A", "甲".repeat(500)),
        chunk(2L, "来源A", "乙".repeat(500)),
        chunk(3L, "来源B", "丙".repeat(500)),
        chunk(4L, "来源C", "丁".repeat(500)));

    AnswerContext context = policy.assemble("比较三个来源", "", List.of(), evidence);

    assertThat(context.estimatedPromptTokens()).isLessThanOrEqualTo(1200);
    assertThat(context.evidence()).extracting(RetrievedChunk::sourceName)
        .contains("来源A", "来源B", "来源C");
    assertThat(context.userPrompt()).contains("<CONVERSATION_SUMMARY>",
        "<RECENT_MESSAGES>", "<EVIDENCE>");
  }

  @Test
  void boundsOversizedSummaryMessageAndEvidence() {
    ContextAssemblyPolicy policy = policy(1200, 300, 160, 500);
    Message oversized = message(1L, "ASSISTANT", "历史。".repeat(2000));
    RetrievedChunk evidence = chunk(1L, "来源", "证据。".repeat(2000));

    AnswerContext context = policy.assemble("问题", "摘要。".repeat(1000),
        List.of(oversized), List.of(evidence));

    assertThat(context.estimatedPromptTokens()).isLessThanOrEqualTo(1200);
    assertThat(context.userPrompt()).doesNotContain(oversized.content());
    assertThat(context.evidence()).hasSize(1);
    assertThat(context.evidence().getFirst().excerpt().length())
        .isLessThan(evidence.excerpt().length());
  }

  @Test
  void limitsEachSummaryBatchAndLeavesRecentTurnsUntouched() {
    ContextAssemblyPolicy policy = policy(1200, 300, 160, 300);
    List<Message> messages = messages(8, "长内容。".repeat(100));

    List<Message> batch = policy.nextSummaryBatch("旧摘要", messages);

    assertThat(batch).isNotEmpty();
    assertThat(batch.getLast().id()).isLessThanOrEqualTo(8L);
    assertThat(policy.summaryCandidates(messages)).allMatch(message -> message.id() <= 8L);
  }

  private ContextAssemblyPolicy policy(int maxInput, int trigger, int maxSummary,
      int minEvidence) {
    return new ContextAssemblyPolicy(maxInput, 200, 4, trigger, maxSummary, minEvidence, tokens);
  }

  private List<Message> messages(int turns, String content) {
    List<Message> messages = new ArrayList<>();
    long id = 1L;
    for (int i = 0; i < turns; i++) {
      messages.add(message(id++, "USER", content + i));
      messages.add(message(id++, "ASSISTANT", content + i));
    }
    return messages;
  }

  private Message message(long id, String role, String content) {
    return new Message(id, 9L, role, content, "COMPLETED", null, null, null,
        LocalDateTime.now());
  }

  private RetrievedChunk chunk(Long documentId, String source, String excerpt) {
    return new RetrievedChunk(1L, documentId, source, 0, excerpt, null, null, .9, null, .03,
        .95);
  }

  private static final class CodePointTokenEstimator implements TokenEstimator {

    @Override
    public int estimate(String text) {
      return text == null ? 0 : text.codePointCount(0, text.length());
    }

    @Override
    public String truncate(String text, int maxTokens) {
      if (text == null || maxTokens <= 0) {
        return "";
      }
      return estimate(text) <= maxTokens ? text
          : text.substring(0, text.offsetByCodePoints(0, maxTokens));
    }
  }
}
