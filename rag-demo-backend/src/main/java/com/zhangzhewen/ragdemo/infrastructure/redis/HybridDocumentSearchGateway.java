package com.zhangzhewen.ragdemo.infrastructure.redis;

import com.zhangzhewen.ragdemo.domain.conversation.ReciprocalRankFusion;
import com.zhangzhewen.ragdemo.domain.conversation.RetrievedChunk;
import com.zhangzhewen.ragdemo.domain.gateway.DocumentSearchGateway;
import com.zhangzhewen.ragdemo.domain.gateway.VectorGateway;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.stereotype.Component;

/**
 * 融合 Redis BM25 关键词排名与向量语义排名的文档检索实现。
 */
@Component
public class HybridDocumentSearchGateway implements DocumentSearchGateway {

  private final VectorGateway vectors;
  private final RedisVectorStore store;

  /**
   * 注入向量检索和与其共用索引的 Redis 文本检索能力。
   */
  public HybridDocumentSearchGateway(VectorGateway vectors, RedisVectorStore store) {
    this.vectors = vectors;
    this.store = store;
  }

  @Override
  public List<RetrievedChunk> search(Long knowledgeBaseId, String query, int topK,
      double threshold) {
    List<RetrievedChunk> semantic = vectors.search(knowledgeBaseId, query, topK, threshold);
    List<RetrievedChunk> keyword = keywordSearch(query, topK,
        "@knowledgeBaseId:{" + knowledgeBaseId + "}");
    return ReciprocalRankFusion.fuse(List.of(semantic, keyword), topK);
  }

  @Override
  public List<RetrievedChunk> searchDocument(Long knowledgeBaseId, Long documentId, String query,
      int topK, double threshold) {
    List<RetrievedChunk> semantic = vectors.searchDocument(knowledgeBaseId, documentId, query, topK,
        threshold);
    String filter = "@knowledgeBaseId:{" + knowledgeBaseId + "} @documentId:{" + documentId + "}";
    List<RetrievedChunk> keyword = keywordSearch(query, topK, filter);
    return ReciprocalRankFusion.fuse(List.of(semantic, keyword), topK);
  }

  private List<RetrievedChunk> keywordSearch(String query, int topK, String filter) {
    return store.searchByText(query, RedisVectorStore.DEFAULT_CONTENT_FIELD_NAME, topK, filter)
        .stream().map(this::map).toList();
  }

  private RetrievedChunk map(Document document) {
    Map<String, Object> metadata = document.getMetadata();
    return new RetrievedChunk(longValue(metadata.get("knowledgeBaseId")),
        longValue(metadata.get("documentId")),
        Objects.toString(metadata.get("sourceName"), "未知来源"),
        intValue(metadata.get("chunkIndex")),
        document.getScore() == null ? 0 : document.getScore(), document.getText(),
        intNullable(metadata.get("pageNumber")),
        metadata.get("sectionTitle") == null ? null : metadata.get("sectionTitle").toString());
  }

  private long longValue(Object value) {
    return Long.parseLong(value.toString());
  }

  private int intValue(Object value) {
    return Integer.parseInt(value.toString());
  }

  private Integer intNullable(Object value) {
    return value == null ? null : intValue(value);
  }
}
