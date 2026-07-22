package com.zhangzhewen.ragdemo.domain.conversation;

/**
 * 检索命中的证据切片及真实定位元数据。
 */
public record RetrievedChunk(Long knowledgeBaseId, Long documentId, String sourceName,
                             int chunkIndex, String excerpt, Integer pageNumber,
                             String sectionTitle, Double vectorScore, Double bm25Score,
                             Double fusionScore, Double rerankScore) {

  /**
   * 保留各路原始分数并写入 RRF 融合分数。
   */
  public RetrievedChunk withFusionScores(Double vectorScore, Double bm25Score, double score) {
    return new RetrievedChunk(knowledgeBaseId, documentId, sourceName, chunkIndex, excerpt,
        pageNumber, sectionTitle, vectorScore, bm25Score, score, rerankScore);
  }

  /**
   * 保留检索和融合分数并写入重排分数。
   */
  public RetrievedChunk withRerankScore(double score) {
    return new RetrievedChunk(knowledgeBaseId, documentId, sourceName, chunkIndex,
        excerpt, pageNumber, sectionTitle, vectorScore, bm25Score, fusionScore, score);
  }

  /**
   * 裁剪证据文本时保留全部检索分数。
   */
  public RetrievedChunk withExcerpt(String excerpt) {
    return new RetrievedChunk(knowledgeBaseId, documentId, sourceName, chunkIndex,
        excerpt, pageNumber, sectionTitle, vectorScore, bm25Score, fusionScore, rerankScore);
  }

}
