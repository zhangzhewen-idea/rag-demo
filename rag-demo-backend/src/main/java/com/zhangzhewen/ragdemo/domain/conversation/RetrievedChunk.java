package com.zhangzhewen.ragdemo.domain.conversation;

/**
 * 检索命中的证据切片及真实定位元数据。
 */
public record RetrievedChunk(Long knowledgeBaseId, Long documentId, String sourceName,
                             int chunkIndex,
                             double similarityScore, String excerpt, Integer pageNumber,
                             String sectionTitle, Double rerankScore) {

  /**
   * 兼容未经重排的检索结果。
   */
  public RetrievedChunk(Long knowledgeBaseId, Long documentId, String sourceName, int chunkIndex,
      double similarityScore, String excerpt, Integer pageNumber, String sectionTitle) {
    this(knowledgeBaseId, documentId, sourceName, chunkIndex, similarityScore, excerpt, pageNumber,
        sectionTitle, null);
  }

  /**
   * 保留原始检索分数并写入重排分数。
   */
  public RetrievedChunk withRerankScore(double score) {
    return new RetrievedChunk(knowledgeBaseId, documentId, sourceName, chunkIndex,
        similarityScore, excerpt, pageNumber, sectionTitle, score);
  }

}
