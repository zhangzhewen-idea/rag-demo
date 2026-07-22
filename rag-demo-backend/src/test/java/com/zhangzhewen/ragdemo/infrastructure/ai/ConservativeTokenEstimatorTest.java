package com.zhangzhewen.ragdemo.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 保守 token 估算与 Unicode 安全截断测试。
 */
class ConservativeTokenEstimatorTest {

  private final ConservativeTokenEstimator estimator = new ConservativeTokenEstimator();

  @Test
  void countsUnicodeCodePointsAndDoesNotSplitEmoji() {
    assertThat(estimator.estimate("中文A😀")).isEqualTo(4);
    assertThat(estimator.truncate("中文A😀结尾", 4)).isEqualTo("中文A😀");
  }

  @Test
  void prefersSentenceBoundaryWhenTruncating() {
    assertThat(estimator.truncate("第一句话。第二句话很长很长", 8)).isEqualTo("第一句话。");
  }
}
