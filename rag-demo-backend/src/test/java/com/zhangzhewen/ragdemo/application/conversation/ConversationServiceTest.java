package com.zhangzhewen.ragdemo.application.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.zhangzhewen.ragdemo.domain.conversation.Conversation;
import com.zhangzhewen.ragdemo.domain.conversation.Message;
import com.zhangzhewen.ragdemo.domain.conversation.RetrievalPolicy;
import com.zhangzhewen.ragdemo.domain.conversation.RetrievedChunk;
import com.zhangzhewen.ragdemo.domain.gateway.AiGateway;
import com.zhangzhewen.ragdemo.domain.gateway.ConversationGateway;
import com.zhangzhewen.ragdemo.domain.gateway.DocumentSearchGateway;
import com.zhangzhewen.ragdemo.domain.gateway.KnowledgeGateway;
import com.zhangzhewen.ragdemo.domain.gateway.QueryRewriteGateway;
import com.zhangzhewen.ragdemo.domain.knowledge.KnowledgeBase;
import com.zhangzhewen.ragdemo.domain.knowledge.KnowledgeBaseStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

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
    when(fixture.search.search(1L, "问题", 6, .6)).thenReturn(List.of());

    StringBuilder stream = new StringBuilder();
    var result = fixture.service.chat(9L, 2L, "问题", stream::append).join();

    assertThat(stream.toString()).isEqualTo(ConversationService.NO_EVIDENCE);
    assertThat(result.references()).isEmpty();
    verifyNoInteractions(fixture.ai);
  }

  /**
   * 改写查询只用于检索，回答生成和消息保存仍使用原始问题。
   */
  @Test
  void retrievesWithRewrittenQueryAndAnswersOriginalQuestion() {
    Fixture fixture = new Fixture();
    String question = "苹果股价咋样了？";
    String rewritten = "Apple Inc.（AAPL）股价表现与财务分析";
    List<RetrievedChunk> evidence = List.of(
        new RetrievedChunk(1L, 3L, "Apple 分析", 0, .9, "股价分析", null, null));
    when(fixture.queryRewrite.rewrite(question)).thenReturn(rewritten);
    when(fixture.search.search(1L, rewritten, 6, .6)).thenReturn(evidence);
    doAnswer(invocation -> {
      Consumer<String> delta = invocation.getArgument(3);
      delta.accept("回答");
      return null;
    }).when(fixture.ai).streamAnswer(eq(question), eq(List.of()), eq(evidence), any());

    StringBuilder stream = new StringBuilder();
    var result = fixture.service.chat(9L, 2L, question, stream::append).join();

    assertThat(stream.toString()).isEqualTo("回答");
    assertThat(result.references()).isEqualTo(evidence);
    verify(fixture.search).search(1L, rewritten, 6, .6);
    verify(fixture.ai).streamAnswer(eq(question), eq(List.of()), eq(evidence), any());
    verify(fixture.conversations).saveMessage(9L, "USER", question, "COMPLETED", null, null,
        null);
  }

  /**
   * 带指代的追问先拼接最近助手回答，再交给模型改写。
   */
  @Test
  void rewritesContextualizedFollowUpQuery() {
    Fixture fixture = new Fixture();
    Message previous = new Message(8L, 9L, "ASSISTANT", "Apple Inc. 的 2024 年 Q2 财报",
        "COMPLETED", null, null, null, LocalDateTime.now());
    when(fixture.conversations.recentMessages(9L, 20)).thenReturn(List.of(previous));
    String contextual = previous.content() + "\n追问：它的股价表现呢？";
    when(fixture.queryRewrite.rewrite(contextual)).thenReturn("Apple Inc. 2024 年 Q2 股价表现");
    when(fixture.search.search(1L, "Apple Inc. 2024 年 Q2 股价表现", 6, .6))
        .thenReturn(List.of());

    fixture.service.chat(9L, 2L, "它的股价表现呢？", ignored -> {
    }).join();

    verify(fixture.queryRewrite).rewrite(contextual);
    verify(fixture.search).search(1L, "Apple Inc. 2024 年 Q2 股价表现", 6, .6);
  }

  private static final class Fixture {

    private final ConversationGateway conversations = mock(ConversationGateway.class);
    private final DocumentSearchGateway search = mock(DocumentSearchGateway.class);
    private final AiGateway ai = mock(AiGateway.class);
    private final KnowledgeGateway knowledge = mock(KnowledgeGateway.class);
    private final QueryRewriteGateway queryRewrite = mock(QueryRewriteGateway.class);
    private final ConversationService service = new ConversationService(conversations, search, ai,
        knowledge, queryRewrite, new RetrievalPolicy(6, .6));

    private Fixture() {
      when(conversations.findConversationById(9L))
          .thenReturn(Optional.of(new Conversation(9L, 2L, 1L, "t", "ACTIVE")));
      when(knowledge.findKnowledgeById(1L))
          .thenReturn(Optional.of(
              new KnowledgeBase(1L, "kb", null, null, KnowledgeBaseStatus.ENABLED)));
      when(conversations.recentMessages(9L, 20)).thenReturn(List.of());
      when(queryRewrite.rewrite(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
      when(conversations.saveMessage(anyLong(), anyString(), anyString(), anyString(), any(), any(),
          any())).thenReturn(10L);
    }
  }
}
