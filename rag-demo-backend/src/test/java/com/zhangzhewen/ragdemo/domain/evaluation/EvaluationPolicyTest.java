package com.zhangzhewen.ragdemo.domain.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.zhangzhewen.ragdemo.domain.evaluation.EvaluationModels.Scores;
import org.junit.jupiter.api.Test;

/**
 * 绝对门槛规则测试。
 */
class EvaluationPolicyTest {

  private final EvaluationPolicy policy = new EvaluationPolicy(.9, .7, .8, .6, .8, .8, .8,
      .9);

  @Test
  void requiresAbsoluteThresholds() {
    Scores scores = scores(.89, .8);
    assertThat(policy.passes(scores)).isFalse();
  }

  @Test
  void onlyRequiresAbsoluteThresholds() {
    Scores current = scores(.91, .86);

    assertThat(policy.passes(current)).isTrue();
  }

  @Test
  void ignoresMetricsWithoutApplicableCases() {
    Scores scores = new Scores(.95, .8, .9, .7, .9, .9, .9, null);
    assertThat(policy.passes(scores)).isTrue();
  }

  @Test
  void refusalCaseOnlyRequiresNoAnswerAccuracy() {
    Scores accepted = new Scores(null, null, null, null, null, null, null, 1D);
    Scores missed = new Scores(null, null, null, null, null, null, null, 0D);

    assertThat(policy.passes(accepted)).isTrue();
    assertThat(policy.passes(missed)).isFalse();
  }

  private Scores scores(double hit, double faithfulness) {
    return new Scores(hit, .8, .9, .7, faithfulness, .9, .9, 1D);
  }
}
