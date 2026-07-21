package com.zhangzhewen.ragdemo.domain.conversation;

/**
 * 候选召回、最终输出和相似度过滤参数。
 */
public record RetrievalPolicy(int topK, int candidateTopK, double similarityThreshold) {

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
  }

}
