package com.zhangzhewen.ragdemo.application.conversation;

import com.zhangzhewen.ragdemo.domain.conversation.*;
import com.zhangzhewen.ragdemo.domain.gateway.*;
import com.zhangzhewen.ragdemo.domain.knowledge.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** 问答用例编排测试。 */
class ConversationServiceTest {
    /** 无可靠向量命中时不调用模型并保存固定拒答。 */
    @Test void refusesWithoutEvidence(){ConversationGateway conversations=mock(ConversationGateway.class);VectorGateway vectors=mock(VectorGateway.class);AiGateway ai=mock(AiGateway.class);KnowledgeGateway knowledge=mock(KnowledgeGateway.class);when(conversations.findConversationById(9L)).thenReturn(Optional.of(new Conversation(9L,2L,1L,"t","ACTIVE")));when(knowledge.findKnowledgeById(1L)).thenReturn(Optional.of(new KnowledgeBase(1L,"kb",null,null,KnowledgeBaseStatus.ENABLED)));when(vectors.search(1L,"问题",6,.6)).thenReturn(List.of());when(conversations.saveMessage(anyLong(),anyString(),anyString(),anyString(),any(),any(),any())).thenReturn(10L);ConversationService service=new ConversationService(conversations,vectors,ai,knowledge,new RetrievalPolicy(6,.6));StringBuilder stream=new StringBuilder();var result=service.chat(9L,2L,"问题",stream::append).join();assertThat(stream.toString()).isEqualTo(ConversationService.NO_EVIDENCE);assertThat(result.references()).isEmpty();verifyNoInteractions(ai);}
}
