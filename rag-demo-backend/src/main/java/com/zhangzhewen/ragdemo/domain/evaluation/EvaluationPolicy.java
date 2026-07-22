package com.zhangzhewen.ragdemo.domain.evaluation;

import com.zhangzhewen.ragdemo.domain.evaluation.EvaluationModels.Scores;
import java.util.List;

/**
 * 评估绝对门槛规则。
 */
public record EvaluationPolicy(double candidateHitRate, double candidateMrr,
                               double contextRecall, double contextPrecision,
                               double faithfulness, double answerRelevancy,
                               double evidenceSupportAccuracy, double noAnswerAccuracy) {

  public EvaluationPolicy {
    List<Double> ratios = List.of(candidateHitRate, candidateMrr, contextRecall, contextPrecision,
        faithfulness, answerRelevancy, evidenceSupportAccuracy, noAnswerAccuracy);
    if (ratios.stream().anyMatch(value -> !Double.isFinite(value) || value < 0 || value > 1)) {
      throw new IllegalArgumentException("评估门槛配置不合法");
    }
  }

  /**
   * 所有适用指标均满足绝对门槛。
   */
  public boolean passes(Scores current) {
    return atLeast(current.candidateHitRate(), candidateHitRate)
        && atLeast(current.candidateMrr(), candidateMrr)
        && atLeast(current.contextRecall(), contextRecall)
        && atLeast(current.contextPrecision(), contextPrecision)
        && atLeast(current.faithfulness(), faithfulness)
        && atLeast(current.answerRelevancy(), answerRelevancy)
        && atLeast(current.evidenceSupportAccuracy(), evidenceSupportAccuracy)
        && atLeast(current.noAnswerAccuracy(), noAnswerAccuracy);
  }

  private boolean atLeast(Double value, double threshold) {
    return value == null || value >= threshold;
  }
}
