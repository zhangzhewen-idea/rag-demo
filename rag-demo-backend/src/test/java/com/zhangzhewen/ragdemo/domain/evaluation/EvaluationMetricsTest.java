package com.zhangzhewen.ragdemo.domain.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.zhangzhewen.ragdemo.domain.conversation.RetrievedChunk;
import com.zhangzhewen.ragdemo.domain.evaluation.EvaluationMetrics.RetrievalScores;
import com.zhangzhewen.ragdemo.domain.evaluation.EvaluationModels.ExpectedContext;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 检索评估指标测试。
 */
class EvaluationMetricsTest {

  @Test
  void separatesCandidateRankingFromFinalContextQuality() {
    List<ExpectedContext> expected = List.of(
        new ExpectedContext("制度.md", "年假为十天"),
        new ExpectedContext("报销.md", "交通费上限 200 元"));
    List<RetrievedChunk> candidates = List.of(chunk("噪声.md", "无关内容"),
        chunk("制度.md", "正式员工年假为十天"), chunk("报销.md", "交通费上限 200 元"));
    List<RetrievedChunk> evidence = List.of(candidates.get(2), candidates.getFirst());

    RetrievalScores scores = EvaluationMetrics.calculate(expected, candidates, evidence);

    assertThat(scores.candidateHitRate()).isEqualTo(1D);
    assertThat(scores.candidateMrr()).isEqualTo(.5D);
    assertThat(scores.contextRecall()).isEqualTo(.5D);
    assertThat(scores.contextPrecision()).isEqualTo(.5D);
    assertThat(scores.noAnswerAccuracy()).isNull();
  }

  @Test
  void scoresNoAnswerCaseByEmptyFinalEvidence() {
    RetrievalScores accepted = EvaluationMetrics.calculate(List.of(), List.of(), List.of());
    RetrievalScores falsePositive = EvaluationMetrics.calculate(List.of(), List.of(),
        List.of(chunk("噪声.md", "无关内容")));

    assertThat(accepted.noAnswerAccuracy()).isEqualTo(1D);
    assertThat(falsePositive.noAnswerAccuracy()).isZero();
    assertThat(accepted.candidateHitRate()).isNull();
  }

  private RetrievedChunk chunk(String source, String excerpt) {
    return new RetrievedChunk(1L, (long) source.hashCode(), source, 0, excerpt, null, null, .8,
        null, .03, null);
  }
}
