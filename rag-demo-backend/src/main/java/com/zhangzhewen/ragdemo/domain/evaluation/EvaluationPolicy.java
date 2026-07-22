package com.zhangzhewen.ragdemo.domain.evaluation;

import com.zhangzhewen.ragdemo.domain.evaluation.EvaluationModels.Scores;
import java.util.List;

/**
 * 评估绝对门槛和相对基线回归规则。
 */
public record EvaluationPolicy(double candidateHitRate, double candidateMrr,
                               double contextRecall, double contextPrecision,
                               double faithfulness, double answerRelevancy,
                               double evidenceSupportAccuracy, double noAnswerAccuracy,
                               double maxRegression) {

  public EvaluationPolicy {
    List<Double> ratios = List.of(candidateHitRate, candidateMrr, contextRecall, contextPrecision,
        faithfulness, answerRelevancy, evidenceSupportAccuracy, noAnswerAccuracy, maxRegression);
    if (ratios.stream().anyMatch(value -> !Double.isFinite(value) || value < 0 || value > 1)) {
      throw new IllegalArgumentException("评估门槛配置不合法");
    }
  }

  /**
   * 同时满足绝对门槛，且任何指标相对最近通过基线的下降不超过允许值。
   */
  public boolean passes(Scores current, Scores baseline) {
    if (!atLeast(current.candidateHitRate(), candidateHitRate)
        || !atLeast(current.candidateMrr(), candidateMrr)
        || !atLeast(current.contextRecall(), contextRecall)
        || !atLeast(current.contextPrecision(), contextPrecision)
        || current.faithfulness() < faithfulness
        || current.answerRelevancy() < answerRelevancy
        || current.evidenceSupportAccuracy() < evidenceSupportAccuracy
        || !atLeast(current.noAnswerAccuracy(), noAnswerAccuracy)) {
      return false;
    }
    return baseline == null
        || !regressed(current.candidateHitRate(), baseline.candidateHitRate())
        && !regressed(current.candidateMrr(), baseline.candidateMrr())
        && !regressed(current.contextRecall(), baseline.contextRecall())
        && !regressed(current.contextPrecision(), baseline.contextPrecision())
        && current.faithfulness() + maxRegression >= baseline.faithfulness()
        && current.answerRelevancy() + maxRegression >= baseline.answerRelevancy()
        && current.evidenceSupportAccuracy() + maxRegression
        >= baseline.evidenceSupportAccuracy()
        && !regressed(current.noAnswerAccuracy(), baseline.noAnswerAccuracy());
  }

  private boolean atLeast(Double value, double threshold) {
    return value == null || value >= threshold;
  }

  private boolean regressed(Double value, Double baseline) {
    return value != null && baseline != null && value + maxRegression < baseline;
  }
}
