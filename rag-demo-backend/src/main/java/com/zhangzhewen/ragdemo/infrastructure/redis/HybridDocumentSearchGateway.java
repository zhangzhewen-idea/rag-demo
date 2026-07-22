package com.zhangzhewen.ragdemo.infrastructure.redis;

import com.zhangzhewen.ragdemo.domain.conversation.ReciprocalRankFusion;
import com.zhangzhewen.ragdemo.domain.conversation.RetrievalPolicy;
import com.zhangzhewen.ragdemo.domain.conversation.RetrievalQuery;
import com.zhangzhewen.ragdemo.domain.conversation.RetrievedChunk;
import com.zhangzhewen.ragdemo.domain.gateway.DocumentSearchGateway;
import com.zhangzhewen.ragdemo.domain.gateway.VectorGateway;
import java.util.Comparator;
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
  private final RetrievalPolicy policy;

  /**
   * 注入向量检索和与其共用索引的 Redis 文本检索能力。
   */
  public HybridDocumentSearchGateway(VectorGateway vectors, RedisVectorStore store,
      RetrievalPolicy policy) {
    this.vectors = vectors;
    this.store = store;
    this.policy = policy;
  }

  @Override
  public List<RetrievedChunk> search(Long knowledgeBaseId, RetrievalQuery query, int topK,
      double threshold) {
    List<RetrievedChunk> semantic = vectors.search(knowledgeBaseId, query.semanticQuery(), topK,
        threshold);
    List<RetrievedChunk> keyword = keywordSearch(query.keywordQuery(), topK,
        "@knowledgeBaseId:{" + knowledgeBaseId + "}");
    return ReciprocalRankFusion.fuse(List.of(semantic, keyword),
        List.of(policy.semanticWeight(), policy.bm25Weight()), topK);
  }

  @Override
  public List<RetrievedChunk> searchDocument(Long knowledgeBaseId, Long documentId,
      RetrievalQuery query, int topK, double threshold) {
    List<RetrievedChunk> semantic = vectors.searchDocument(knowledgeBaseId, documentId,
        query.semanticQuery(), topK, threshold);
    String filter = "@knowledgeBaseId:{" + knowledgeBaseId + "} @documentId:{" + documentId + "}";
    List<RetrievedChunk> keyword = keywordSearch(query.keywordQuery(), topK, filter);
    return ReciprocalRankFusion.fuse(List.of(semantic, keyword),
        List.of(policy.semanticWeight(), policy.bm25Weight()), topK);
  }

  private List<RetrievedChunk> keywordSearch(String query, int topK, String filter) {
    return store.searchByText(query, RedisVectorStore.DEFAULT_CONTENT_FIELD_NAME, topK, filter)
        .stream().map(this::map)
        .sorted(Comparator.comparing(RetrievedChunk::bm25Score,
                Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(RetrievedChunk::documentId)
            .thenComparingInt(RetrievedChunk::chunkIndex))
        .toList();
  }

  private RetrievedChunk map(Document document) {
    Map<String, Object> metadata = document.getMetadata();
    return new RetrievedChunk(longValue(metadata.get("knowledgeBaseId")),
        longValue(metadata.get("documentId")),
        Objects.toString(metadata.get("sourceName"), "未知来源"),
        intValue(metadata.get("chunkIndex")), document.getText(),
        intNullable(metadata.get("pageNumber")),
        metadata.get("sectionTitle") == null ? null : metadata.get("sectionTitle").toString(),
        null, document.getScore(), null, null);
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
