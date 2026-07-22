package com.zhangzhewen.ragdemo.application.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.zhangzhewen.ragdemo.domain.conversation.AiUsage;
import com.zhangzhewen.ragdemo.domain.conversation.AnswerContext;
import com.zhangzhewen.ragdemo.domain.conversation.Conversation;
import com.zhangzhewen.ragdemo.domain.conversation.GeneratedAnswer;
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
import com.zhangzhewen.ragdemo.domain.gateway.TokenEstimator;
import com.zhangzhewen.ragdemo.domain.knowledge.KnowledgeBase;
import com.zhangzhewen.ragdemo.domain.knowledge.KnowledgeBaseStatus;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 问答用例编排测试。
 */
class ConversationServiceTest {

  /**
   * 无可靠向量命中时不调用模型并保存固定拒答。
   */
  @Test
  void refusesWithoutEvidence() {
    Fixture fixture = new Fixture();
    RetrievalQuery plan = new RetrievalQuery("问题", "问题");
    when(fixture.search.search(1L, plan, 20, .6)).thenReturn(List.of());

    StringBuilder stream = new StringBuilder();
    var result = fixture.service.chat(9L, 2L, "问题", stream::append).join();

    assertThat(stream.toString()).isEqualTo(ConversationService.NO_EVIDENCE);
    assertThat(result.refused()).isTrue();
    assertThat(result.references()).isEmpty();
    verifyNoInteractions(fixture.ai);
    verifyNoInteractions(fixture.rerank);
  }

  /**
   * 改写查询只用于检索，回答生成和消息保存仍使用原始问题。
   */
  @Test
  void retrievesWithRewrittenQueryAndAnswersOriginalQuestion() {
    Fixture fixture = new Fixture();
    String question = "苹果股价咋样了？";
    String rewritten = "Apple Inc.（AAPL）股价表现与财务分析";
    RetrievedChunk first =
        new RetrievedChunk(1L, 3L, "Apple 分析", 0, "股价分析", null, null, .9, null,
            null, null);
    RetrievedChunk second =
        new RetrievedChunk(1L, 4L, "Apple 财报", 0, "财报内容", null, null, .8, null,
            null, null);
    List<RetrievedChunk> candidates = List.of(first, second);
    List<RetrievedChunk> fused = ReciprocalRankFusion.fuse(List.of(candidates), 20);
    List<RetrievedChunk> evidence = List.of(fused.get(1).withRerankScore(.95),
        fused.getFirst().withRerankScore(.7));
    RetrievalQuery plan = new RetrievalQuery(rewritten, "Apple AAPL 股价 财务");
    when(fixture.queryRewrite.rewrite(question)).thenReturn(rewritten);
    when(fixture.queryExpansion.plan(rewritten)).thenReturn(List.of(plan));
    when(fixture.search.search(1L, plan, 20, .6)).thenReturn(candidates);
    when(fixture.rerank.rerank(rewritten, fused, 6)).thenReturn(evidence);
    when(fixture.ai.generateAnswer(any()))
        .thenReturn(new GeneratedAnswer(false, "回答", new AiUsage(120, 8)));

    StringBuilder stream = new StringBuilder();
    var result = fixture.service.chat(9L, 2L, question, stream::append).join();

    assertThat(stream.toString()).isEqualTo("回答");
    assertThat(result.refused()).isFalse();
    assertThat(result.references()).isEqualTo(evidence);
    verify(fixture.search).search(1L, plan, 20, .6);
    verify(fixture.rerank).rerank(rewritten, fused, 6);
    ArgumentCaptor<AnswerContext> context = ArgumentCaptor.forClass(AnswerContext.class);
    verify(fixture.ai).generateAnswer(context.capture());
    assertThat(context.getValue().userPrompt()).contains("问题：" + question)
        .contains("<CONVERSATION_SUMMARY>").contains("<RECENT_MESSAGES>")
        .contains("<EVIDENCE>");
    assertThat(context.getValue().evidence()).isEqualTo(evidence);
    verify(fixture.conversations).saveMessage(9L, "USER", question, "COMPLETED", null, null,
        null);
    verify(fixture.conversations).saveMessage(9L, "ASSISTANT", "回答", "COMPLETED", 120, 8,
        result.elapsedMs());
  }

  /**
   * 带指代的追问先拼接最近助手回答，再交给模型改写。
   */
  @Test
  void rewritesContextualizedFollowUpQuery() {
    Fixture fixture = new Fixture();
    Message previous = new Message(8L, 9L, "ASSISTANT", "Apple Inc. 的 2024 年 Q2 财报",
        "COMPLETED", null, null, null, LocalDateTime.now());
    when(fixture.conversations.messagesAfter(9L, 0L)).thenReturn(List.of(previous));
    String contextual = previous.content() + "\n追问：它的股价表现呢？";
    String rewritten = "Apple Inc. 2024 年 Q2 股价表现";
    RetrievalQuery plan = new RetrievalQuery(rewritten, "Apple 2024 Q2 股价");
    when(fixture.queryRewrite.rewrite(contextual)).thenReturn(rewritten);
    when(fixture.queryExpansion.plan(rewritten)).thenReturn(List.of(plan));
    when(fixture.search.search(1L, plan, 20, .6)).thenReturn(List.of());

    fixture.service.chat(9L, 2L, "它的股价表现呢？", ignored -> {
    }).join();

    verify(fixture.queryRewrite).rewrite(contextual);
    verify(fixture.search).search(1L, plan, 20, .6);
  }

  /**
   * 多查询分别执行混合检索，融合去重后再统一重排。
   */
  @Test
  void expandsRetrievalQueriesAndFusesCandidatesBeforeReranking() {
    Fixture fixture = new Fixture();
    String question = "华为云 ModelArts 与阿里云 PAI 的区别";
    String rewritten = "华为云 ModelArts 平台与阿里云 PAI 平台对比";
    RetrievalQuery comparison = new RetrievalQuery(rewritten, "华为云 ModelArts 阿里云 PAI");
    RetrievalQuery modelArtsQuery = new RetrievalQuery("华为云 ModelArts 平台功能与特点",
        "华为云 ModelArts 功能 特点");
    RetrievalQuery paiQuery = new RetrievalQuery("阿里云 PAI 平台功能与特点",
        "阿里云 PAI 功能 特点");
    RetrievedChunk modelArts =
        new RetrievedChunk(1L, 3L, "ModelArts", 0, "ModelArts 内容", null, null, .9, null,
            null, null);
    RetrievedChunk pai = new RetrievedChunk(1L, 4L, "PAI", 0, "PAI 内容", null, null,
        .85, null, null, null);
    List<RetrievedChunk> fused = ReciprocalRankFusion.fuse(
        List.of(List.of(modelArts), List.of(modelArts), List.of(pai)), 20);
    List<RetrievedChunk> evidence = List.of(fused.get(1).withRerankScore(.95),
        fused.getFirst().withRerankScore(.9));
    when(fixture.queryRewrite.rewrite(question)).thenReturn(rewritten);
    when(fixture.queryExpansion.plan(rewritten))
        .thenReturn(List.of(comparison, modelArtsQuery, paiQuery));
    when(fixture.search.search(1L, comparison, 20, .6)).thenReturn(List.of(modelArts));
    when(fixture.search.search(1L, modelArtsQuery, 20, .6)).thenReturn(List.of(modelArts));
    when(fixture.search.search(1L, paiQuery, 20, .6)).thenReturn(List.of(pai));
    when(fixture.rerank.rerank(rewritten, fused, 6)).thenReturn(evidence);
    when(fixture.ai.generateAnswer(any()))
        .thenReturn(new GeneratedAnswer(false, "对比回答", new AiUsage(100, 10)));

    var result = fixture.service.chat(9L, 2L, question, ignored -> {
    }).join();

    assertThat(result.references()).isEqualTo(evidence);
    verify(fixture.search).search(1L, comparison, 20, .6);
    verify(fixture.search).search(1L, modelArtsQuery, 20, .6);
    verify(fixture.search).search(1L, paiQuery, 20, .6);
    verify(fixture.rerank).rerank(rewritten, fused, 6);
  }

  /**
   * 超过阈值的旧消息生成摘要，近期四轮仍以原文参与追问改写。
   */
  @Test
  void summarizesOldMessagesAndUsesSummaryForFollowUp() {
    Fixture fixture = new Fixture();
    List<Message> history = longHistory();
    when(fixture.conversations.messagesAfter(9L, 0L)).thenReturn(history);
    when(fixture.contextSummary.summarize(eq(""), any(), eq(1200))).thenReturn("项目滚动摘要");
    when(fixture.conversations.saveSummary(any(), eq(0L))).thenReturn(true);
    String contextual = "项目滚动摘要\n" + history.getLast().content().substring(0, 600)
        + "\n追问：它还有什么限制？";
    when(fixture.queryRewrite.rewrite(contextual)).thenReturn("项目限制");
    when(fixture.queryExpansion.plan("项目限制"))
        .thenReturn(List.of(new RetrievalQuery("项目限制", "项目 限制")));
    when(fixture.search.search(1L, new RetrievalQuery("项目限制", "项目 限制"), 20, .6))
        .thenReturn(List.of());

    fixture.service.chat(9L, 2L, "它还有什么限制？", ignored -> {
    }).join();

    verify(fixture.contextSummary).summarize(eq(""), any(), eq(1200));
    verify(fixture.conversations).saveSummary(any(), eq(0L));
    verify(fixture.queryRewrite).rewrite(contextual);
  }

  /**
   * 摘要模型故障时继续按确定性预算裁剪完成检索，不中断问答。
   */
  @Test
  void fallsBackWhenSummaryFails() {
    Fixture fixture = new Fixture();
    when(fixture.conversations.messagesAfter(9L, 0L)).thenReturn(longHistory());
    when(fixture.contextSummary.summarize(eq(""), any(), eq(1200)))
        .thenThrow(new IllegalStateException("模型不可用"));
    when(fixture.search.search(eq(1L), any(), eq(20), eq(.6))).thenReturn(List.of());

    var result = fixture.service.chat(9L, 2L, "普通问题", ignored -> {
    }).join();

    assertThat(result.answer()).isEqualTo(ConversationService.NO_EVIDENCE);
    verify(fixture.conversations, org.mockito.Mockito.never()).saveSummary(any(), anyLong());
  }

  /**
   * 摘要版本冲突时重新加载，并只重试一次乐观更新。
   */
  @Test
  void retriesSummaryOnceAfterOptimisticConflict() {
    Fixture fixture = new Fixture();
    List<Message> history = longHistory();
    when(fixture.conversations.messagesAfter(9L, 0L)).thenReturn(history);
    when(fixture.contextSummary.summarize(eq(""), any(), eq(1200))).thenReturn("摘要");
    when(fixture.conversations.saveSummary(any(), eq(0L))).thenReturn(false, true);
    when(fixture.search.search(eq(1L), any(), eq(20), eq(.6))).thenReturn(List.of());

    fixture.service.chat(9L, 2L, "普通问题", ignored -> {
    }).join();

    verify(fixture.conversations, times(2)).saveSummary(any(), eq(0L));
  }

  private static List<Message> longHistory() {
    List<Message> messages = new ArrayList<>();
    long id = 1L;
    for (int turn = 0; turn < 5; turn++) {
      messages.add(new Message(id++, 9L, "USER", "用户内容".repeat(210), "COMPLETED",
          null, null, null, LocalDateTime.now()));
      messages.add(new Message(id++, 9L, "ASSISTANT", "助手内容".repeat(210), "COMPLETED",
          null, null, null, LocalDateTime.now()));
    }
    return messages;
  }

  private static final class Fixture {

    private final ConversationGateway conversations = mock(ConversationGateway.class);
    private final DocumentSearchGateway search = mock(DocumentSearchGateway.class);
    private final DocumentRerankGateway rerank = mock(DocumentRerankGateway.class);
    private final AiGateway ai = mock(AiGateway.class);
    private final KnowledgeGateway knowledge = mock(KnowledgeGateway.class);
    private final QueryRewriteGateway queryRewrite = mock(QueryRewriteGateway.class);
    private final QueryExpansionGateway queryExpansion = mock(QueryExpansionGateway.class);
    private final ContextSummaryGateway contextSummary = mock(ContextSummaryGateway.class);
    private final TokenEstimator tokens = new TestTokenEstimator();
    private final EvidenceRetrievalService retrieval = new EvidenceRetrievalService(search, rerank,
        queryRewrite, queryExpansion, new RetrievalPolicy(6, 20, .6, 1, .8));
    private final ConversationService service = new ConversationService(conversations, retrieval,
        ai, knowledge, contextSummary,
        new ContextAssemblyPolicy(24000, 4000, 4, 8000, 1200, 8000, tokens));

    private Fixture() {
      when(conversations.findConversationById(9L))
          .thenReturn(Optional.of(new Conversation(9L, 2L, 1L, "t", "ACTIVE")));
      when(knowledge.findKnowledgeById(1L))
          .thenReturn(Optional.of(
              new KnowledgeBase(1L, "kb", null, null, KnowledgeBaseStatus.ENABLED)));
      when(conversations.messagesAfter(9L, 0L)).thenReturn(List.of());
      when(queryRewrite.rewrite(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
      when(queryExpansion.plan(anyString())).thenAnswer(invocation -> {
        String query = invocation.getArgument(0);
        return List.of(new RetrievalQuery(query, query));
      });
      when(conversations.saveMessage(anyLong(), anyString(), anyString(), anyString(), any(), any(),
          any())).thenReturn(10L);
    }
  }

  private static final class TestTokenEstimator implements TokenEstimator {

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
