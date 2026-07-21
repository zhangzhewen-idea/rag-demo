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
    assertThatThrownBy(() -> new RetrievalPolicy(6, 5, .2))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("candidateTopK");
  }

  /**
   * 相似度阈值必须位于归一化分数范围。
   */
  @Test
  void rejectsSimilarityThresholdOutsideNormalizedRange() {
    assertThatThrownBy(() -> new RetrievalPolicy(6, 20, 1.1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("similarityThreshold");
    assertThatThrownBy(() -> new RetrievalPolicy(6, 20, Double.NaN))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("similarityThreshold");
  }
}
