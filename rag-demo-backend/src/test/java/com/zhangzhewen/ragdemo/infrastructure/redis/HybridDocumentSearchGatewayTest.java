package com.zhangzhewen.ragdemo.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhangzhewen.ragdemo.domain.conversation.RetrievalQuery;
import com.zhangzhewen.ragdemo.domain.conversation.RetrievedChunk;
import com.zhangzhewen.ragdemo.domain.gateway.VectorGateway;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;

/**
 * Redis 混合检索适配器测试。
 */
class HybridDocumentSearchGatewayTest {

  /**
   * 普通检索使用同一知识库过滤 BM25 和向量结果并进行 RRF 融合。
   */
  @Test
  void fusesBm25AndVectorRankingsWithinKnowledgeBase() {
    VectorGateway vectors = mock(VectorGateway.class);
    RedisVectorStore store = mock(RedisVectorStore.class);
    HybridDocumentSearchGateway gateway = new HybridDocumentSearchGateway(vectors, store);
    RetrievedChunk semantic = chunk(1L, .8, "语义内容");
    Document keyword = document(2L, "包含 ModelArts 的内容");
    RetrievalQuery query = new RetrievalQuery("ModelArts 平台能力", "华为云 ModelArts");
    when(vectors.search(9L, query.semanticQuery(), 10, .7)).thenReturn(List.of(semantic));
    when(store.searchByText(query.keywordQuery(), "content", 10, "@knowledgeBaseId:{9}"))
        .thenReturn(List.of(keyword));

    List<RetrievedChunk> result = gateway.search(9L, query, 10, .7);

    assertThat(result).extracting(RetrievedChunk::documentId).containsExactly(1L, 2L);
    verify(vectors).search(9L, query.semanticQuery(), 10, .7);
    verify(store).searchByText(query.keywordQuery(), "content", 10, "@knowledgeBaseId:{9}");
  }

  /**
   * 概览检索的 BM25 分支同时限定知识库和文档。
   */
  @Test
  void filtersBm25SearchByKnowledgeBaseAndDocument() {
    VectorGateway vectors = mock(VectorGateway.class);
    RedisVectorStore store = mock(RedisVectorStore.class);
    HybridDocumentSearchGateway gateway = new HybridDocumentSearchGateway(vectors, store);
    RetrievalQuery query = new RetrievalQuery("文档内容概览", "内容 概览");
    when(vectors.searchDocument(9L, 2L, query.semanticQuery(), 3, .6)).thenReturn(List.of());
    when(store.searchByText(query.keywordQuery(), "content", 3,
        "@knowledgeBaseId:{9} @documentId:{2}")).thenReturn(List.of(document(2L, "内容")));

    List<RetrievedChunk> result = gateway.searchDocument(9L, 2L, query, 3, .6);

    assertThat(result).extracting(RetrievedChunk::documentId).containsExactly(2L);
    verify(store).searchByText(query.keywordQuery(), "content", 3,
        "@knowledgeBaseId:{9} @documentId:{2}");
  }

  private static RetrievedChunk chunk(Long documentId, double score, String excerpt) {
    return new RetrievedChunk(9L, documentId, "source", 0, score, excerpt, null, null);
  }

  private static Document document(Long documentId, String text) {
    return Document.builder().id("doc-" + documentId + "-0").text(text)
        .metadata(Map.of("knowledgeBaseId", "9", "documentId", documentId.toString(),
            "sourceName", "source", "chunkIndex", "0"))
        .score(.9).build();
  }
}
