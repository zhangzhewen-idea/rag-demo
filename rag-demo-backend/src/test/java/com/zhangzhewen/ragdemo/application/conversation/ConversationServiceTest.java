package com.zhangzhewen.ragdemo.application.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.zhangzhewen.ragdemo.domain.conversation.Conversation;
import com.zhangzhewen.ragdemo.domain.conversation.RetrievalPolicy;
import com.zhangzhewen.ragdemo.domain.conversation.RetrievedChunk;
import com.zhangzhewen.ragdemo.domain.gateway.AiGateway;
import com.zhangzhewen.ragdemo.domain.gateway.ConversationGateway;
import com.zhangzhewen.ragdemo.domain.gateway.DocumentGateway;
import com.zhangzhewen.ragdemo.domain.gateway.KnowledgeGateway;
import com.zhangzhewen.ragdemo.domain.gateway.VectorGateway;
import com.zhangzhewen.ragdemo.domain.knowledge.DocumentStatus;
import com.zhangzhewen.ragdemo.domain.knowledge.KnowledgeBase;
import com.zhangzhewen.ragdemo.domain.knowledge.KnowledgeBaseStatus;
import com.zhangzhewen.ragdemo.domain.knowledge.KnowledgeDocument;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 问答用例编排测试。 */
class ConversationServiceTest {

  /** 无可靠向量命中时不调用模型并保存固定拒答。 */
  @Test
  void refusesWithoutEvidence() {
    Fixture fixture = new Fixture();
    when(fixture.vectors.search(1L, "问题", 6, .6)).thenReturn(List.of());

    StringBuilder stream = new StringBuilder();
    var result = fixture.service.chat(9L, 2L, "问题", stream::append).join();

    assertThat(stream.toString()).isEqualTo(ConversationService.NO_EVIDENCE);
    assertThat(result.references()).isEmpty();
    verifyNoInteractions(fixture.ai);
  }

  /** 概览问题逐文档从向量库召回，避免大文档占满全局 TopK。 */
  @Test
  void retrievesEveryReadyDocumentForOverviewQuestion() {
    Fixture fixture = new Fixture();
    KnowledgeDocument api = document(6L, "API接口设计规范.md", DocumentStatus.READY);
    KnowledgeDocument git = document(7L, "Git版本管理规范.docx", DocumentStatus.READY);
    KnowledgeDocument failed = document(8L, "失败文档.txt", DocumentStatus.FAILED);
    when(fixture.documents.listByKnowledgeBase(1L)).thenReturn(List.of(api, git, failed));
    RetrievedChunk apiChunk = chunk(6L, "API接口设计规范.md");
    RetrievedChunk gitChunk = chunk(7L, "Git版本管理规范.docx");
    when(fixture.vectors.searchDocument(1L, 6L, "API接口设计规范.md 主要内容 核心要点 完整概览", 3, .6))
        .thenReturn(List.of(apiChunk));
    when(fixture.vectors.searchDocument(1L, 7L, "Git版本管理规范.docx 主要内容 核心要点 完整概览", 3, .6))
        .thenReturn(List.of(gitChunk));

    var result = fixture.service.chat(9L, 2L, "你知道什么？", ignored -> { }).join();

    assertThat(result.references()).containsExactly(apiChunk, gitChunk);
    verify(fixture.vectors, never()).search(anyLong(), anyString(), anyInt(), anyDouble());
    verify(fixture.vectors, never()).searchDocument(1L, 8L, "失败文档.txt 主要内容 核心要点 完整概览", 3, .6);
  }

  private static KnowledgeDocument document(Long id, String name, DocumentStatus status) {
    return new KnowledgeDocument(id, 1L, name, name, "/tmp/" + name, "txt", "text/plain",
        100, "hash", status, 2, 0, null, null);
  }

  private static RetrievedChunk chunk(Long documentId, String sourceName) {
    return new RetrievedChunk(1L, documentId, sourceName, 0, .9, "内容", null, null);
  }

  private static final class Fixture {
    private final ConversationGateway conversations = mock(ConversationGateway.class);
    private final VectorGateway vectors = mock(VectorGateway.class);
    private final AiGateway ai = mock(AiGateway.class);
    private final KnowledgeGateway knowledge = mock(KnowledgeGateway.class);
    private final DocumentGateway documents = mock(DocumentGateway.class);
    private final ConversationService service = new ConversationService(conversations, vectors, ai,
        knowledge, documents, new RetrievalPolicy(6, .6));

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
