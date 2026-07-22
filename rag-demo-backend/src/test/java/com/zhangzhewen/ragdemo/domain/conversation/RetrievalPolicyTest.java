package com.zhangzhewen.ragdemo.domain.conversation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * 两阶段检索参数规则测试。
 */
class RetrievalPolicyTest {

  /**
   * 候选数不得小于最终输出数。
   */
  @Test
  void rejectsCandidateTopKBelowFinalTopK() {
    assertThatThrownBy(() -> new RetrievalPolicy(6, 5, .2, 1, .8))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("candidateTopK");
  }

  /**
   * 相似度阈值必须位于归一化分数范围。
   */
  @Test
  void rejectsSimilarityThresholdOutsideNormalizedRange() {
    assertThatThrownBy(() -> new RetrievalPolicy(6, 20, 1.1, 1, .8))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("similarityThreshold");
    assertThatThrownBy(() -> new RetrievalPolicy(6, 20, Double.NaN, 1, .8))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("similarityThreshold");
  }

  /**
   * 混合检索权重必须是正的有限数。
   */
  @Test
  void rejectsInvalidHybridWeights() {
    assertThatThrownBy(() -> new RetrievalPolicy(6, 20, .2, 0, .8))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("权重");
    assertThatThrownBy(() -> new RetrievalPolicy(6, 20, .2, 1, Double.NaN))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("权重");
  }
}
