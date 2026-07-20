package com.zhangzhewen.ragdemo.infrastructure.redis;

import com.zhangzhewen.ragdemo.domain.conversation.RetrievedChunk;
import com.zhangzhewen.ragdemo.domain.gateway.VectorGateway;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import java.util.*;

/**
 * Spring AI RedisVectorStore 适配器。
 */
@Component
public class SpringAiVectorGateway implements VectorGateway {

  private final VectorStore store;

  /**
   * 注入自动配置的 Redis Vector Store。
   */
  public SpringAiVectorGateway(VectorStore store) {
    this.store = store;
  }

  /**
   * 使用稳定 ID 写入切片，便于补偿删除。
   */
  @Override
  public void add(List<String> chunks, List<Map<String, Object>> metadata) {
    List<Document> docs = new ArrayList<>();
    for (int i = 0; i < chunks.size(); i++) {
      Map<String, Object> m = metadata.get(i);
      String id = "doc-" + m.get("documentId") + "-" + m.get("chunkIndex");
      docs.add(Document.builder().id(id).text(chunks.get(i)).metadata(m).build());
    }
    store.add(docs);
  }

  /**
   * 使用 TAG 元数据进行知识库精确过滤。
   */
  @Override
  public List<RetrievedChunk> search(Long knowledgeBaseId, String query, int topK,
      double threshold) {
    return search(query, topK, threshold, "knowledgeBaseId == '" + knowledgeBaseId + "'");
  }

  /**
   * 概览问题按文档过滤，避免大文档的切片挤占其他文档。
   */
  @Override
  public List<RetrievedChunk> searchDocument(Long knowledgeBaseId, Long documentId, String query,
      int topK, double threshold) {
    String filter = "knowledgeBaseId == '" + knowledgeBaseId + "' && documentId == '"
        + documentId + "'";
    return search(query, topK, threshold, filter);
  }

  private List<RetrievedChunk> search(String query, int topK, double threshold, String filter) {
    SearchRequest request = SearchRequest.builder().query(query).topK(topK)
        .similarityThreshold(threshold).filterExpression(filter).build();
    return store.similaritySearch(request).stream().map(this::map).toList();
  }

  /**
   * 按元数据过滤删除本次文档的全部向量。
   */
  @Override
  public void deleteByDocumentId(Long documentId) {
    store.delete("documentId == '" + documentId + "'");
  }

  private RetrievedChunk map(Document d) {
    Map<String, Object> m = d.getMetadata();
    return new RetrievedChunk(longValue(m.get("knowledgeBaseId")), longValue(m.get("documentId")),
        Objects.toString(m.get("sourceName"), "未知来源"), intValue(m.get("chunkIndex")),
        d.getScore() == null ? 0 : d.getScore(), d.getText(), intNullable(m.get("pageNumber")),
        m.get("sectionTitle") == null ? null : m.get("sectionTitle").toString());
  }

  private long longValue(Object v) {
    return Long.parseLong(v.toString());
  }

  private int intValue(Object v) {
    return Integer.parseInt(v.toString());
  }

  private Integer intNullable(Object v) {
    return v == null ? null : intValue(v);
  }
}
