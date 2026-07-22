package com.zhangzhewen.ragdemo.domain.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.zhangzhewen.ragdemo.domain.evaluation.EvaluationModels.Scores;
import org.junit.jupiter.api.Test;

/**
 * 绝对门槛和相对基线规则测试。
 */
class EvaluationPolicyTest {

  private final EvaluationPolicy policy = new EvaluationPolicy(.9, .7, .8, .6, .8, .8, .8,
      .9, .03);

  @Test
  void requiresAbsoluteThresholds() {
    Scores scores = scores(.89, .8);
    assertThat(policy.passes(scores, null)).isFalse();
  }

  @Test
  void rejectsRegressionBeyondThreePoints() {
    Scores baseline = scores(.95, .9);
    Scores current = scores(.91, .86);

    assertThat(policy.passes(current, baseline)).isFalse();
  }

  @Test
  void ignoresMetricsWithoutApplicableCases() {
    Scores scores = new Scores(.95, .8, .9, .7, .9, .9, .9, null);
    assertThat(policy.passes(scores, null)).isTrue();
  }

  private Scores scores(double hit, double faithfulness) {
    return new Scores(hit, .8, .9, .7, faithfulness, .9, .9, 1D);
  }
}
