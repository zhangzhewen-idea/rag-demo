package com.zhangzhewen.ragdemo.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zhangzhewen.ragdemo.domain.conversation.RetrievedChunk;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

/**
 * Spring AI 向量检索适配器测试。
 */
class SpringAiVectorGatewayTest {

  /**
   * 向量结果按相似度降序返回，确保后续 RRF 使用正确排名。
   */
  @Test
  void sortsResultsBySimilarityScoreDescending() {
    VectorStore store = mock(VectorStore.class);
    SpringAiVectorGateway gateway = new SpringAiVectorGateway(store);
    when(store.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
        document(1L, 0, .55), document(2L, 0, .9), document(3L, 0, .7)));

    List<RetrievedChunk> result = gateway.search(9L, "ModelArts", 3, .5);

    assertThat(result).extracting(RetrievedChunk::documentId).containsExactly(2L, 3L, 1L);
    assertThat(result).extracting(RetrievedChunk::vectorScore)
        .containsExactly(.9, .7, .55);
  }

  private Document document(Long documentId, int chunkIndex, double score) {
    return Document.builder().id("doc-" + documentId + "-" + chunkIndex).text("内容")
        .metadata(Map.of("knowledgeBaseId", "9", "documentId", documentId.toString(),
            "sourceName", "source", "chunkIndex", Integer.toString(chunkIndex)))
        .score(score).build();
  }
}
