package com.zhangzhewen.ragdemo.application.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.zhangzhewen.ragdemo.domain.conversation.Conversation;
import com.zhangzhewen.ragdemo.domain.conversation.RetrievalPolicy;
import com.zhangzhewen.ragdemo.domain.gateway.AiGateway;
import com.zhangzhewen.ragdemo.domain.gateway.ConversationGateway;
import com.zhangzhewen.ragdemo.domain.gateway.DocumentSearchGateway;
import com.zhangzhewen.ragdemo.domain.gateway.KnowledgeGateway;
import com.zhangzhewen.ragdemo.domain.knowledge.KnowledgeBase;
import com.zhangzhewen.ragdemo.domain.knowledge.KnowledgeBaseStatus;
import java.util.List;
import java.util.Optional;
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

  private static final class Fixture {

    private final ConversationGateway conversations = mock(ConversationGateway.class);
    private final DocumentSearchGateway search = mock(DocumentSearchGateway.class);
    private final AiGateway ai = mock(AiGateway.class);
    private final KnowledgeGateway knowledge = mock(KnowledgeGateway.class);
    private final ConversationService service = new ConversationService(conversations, search, ai,
        knowledge, new RetrievalPolicy(6, .6));

    private Fixture() {
      when(conversations.findConversationById(9L))
          .thenReturn(Optional.of(new Conversation(9L, 2L, 1L, "t", "ACTIVE")));
      when(knowledge.findKnowledgeById(1L))
          .thenReturn(Optional.of(
              new KnowledgeBase(1L, "kb", null, null, KnowledgeBaseStatus.ENABLED)));
      when(conversations.recentMessages(9L, 20)).thenReturn(List.of());
      when(conversations.saveMessage(anyLong(), anyString(), anyString(), anyString(), any(), any(),
          any())).thenReturn(10L);
    }
  }
}
