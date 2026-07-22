package com.zhangzhewen.ragdemo.domain.conversation;

/**
 * 候选召回、最终输出、相似度过滤和混合检索权重。
 */
public record RetrievalPolicy(int topK, int candidateTopK, double similarityThreshold,
                              double semanticWeight, double bm25Weight) {

  /**
   * 校验两阶段检索参数。
   */
  public RetrievalPolicy {
    if (topK <= 0) {
      throw new IllegalArgumentException("topK 必须大于 0");
    }
    if (candidateTopK < topK) {
      throw new IllegalArgumentException("candidateTopK 不能小于 topK");
    }
    if (!Double.isFinite(similarityThreshold) || similarityThreshold < 0
        || similarityThreshold > 1) {
      throw new IllegalArgumentException("similarityThreshold 必须位于 0 到 1 之间");
    }
    if (!Double.isFinite(semanticWeight) || semanticWeight <= 0
        || !Double.isFinite(bm25Weight) || bm25Weight <= 0) {
      throw new IllegalArgumentException("混合检索权重必须是大于 0 的有限数");
    }
  }

}
