package com.zhangzhewen.ragdemo.domain.evaluation;

import com.zhangzhewen.ragdemo.domain.conversation.RetrievalQuery;
import com.zhangzhewen.ragdemo.domain.conversation.RetrievedChunk;
import java.time.LocalDateTime;
import java.util.List;

/**
 * RAG 评估集、运行和逐题结果模型。
 */
public final class EvaluationModels {

  private EvaluationModels() {
  }

  public record ExpectedContext(String sourceName, String evidenceContains) {
  }

  public record EvaluationCase(Long id, String question, String goldenAnswer, String answerType,
                               boolean critical, List<ExpectedContext> expectedContexts) {
    public EvaluationCase {
      expectedContexts = List.copyOf(expectedContexts);
    }
  }

  public record Dataset(Long id, Long knowledgeBaseId, String name, String version,
                        int caseCount, Long createdBy, LocalDateTime createdAt,
                        List<EvaluationCase> cases) {
    public Dataset {
      cases = List.copyOf(cases);
    }
  }

  public record Scores(Double candidateHitRate, Double candidateMrr, Double contextRecall,
                       Double contextPrecision, Double faithfulness, Double answerRelevancy,
                       Double evidenceSupportAccuracy, Double noAnswerAccuracy) {
  }

  public record Judgment(double faithfulness, double answerRelevancy,
                         double evidenceSupportAccuracy, String rationale) {
  }

  public record CaseExecution(Long caseId, String answer, boolean refused, String rewrittenQuery,
                              List<RetrievalQuery> expandedQueries,
                              List<RetrievedChunk> candidates,
                              List<RetrievedChunk> finalEvidence, Scores scores,
                              String judgeRationale, int promptTokens, int completionTokens,
                              long latencyMs, String errorMessage) {
    public CaseExecution {
      expandedQueries = List.copyOf(expandedQueries);
      candidates = List.copyOf(candidates);
      finalEvidence = List.copyOf(finalEvidence);
    }
  }

  public record CaseResult(Long id, Long runId, EvaluationCase evaluationCase,
                           CaseExecution execution, Boolean passed, String reviewVerdict,
                           String reviewComment, Long reviewedBy, LocalDateTime reviewedAt) {
  }

  public record Run(Long id, Long datasetId, Long baselineRunId, String status,
                    String configSnapshot, Scores scores, boolean passed, int totalCases,
                    int completedCases, int failedCases, int promptTokens,
                    int completionTokens, long latencyMs, long p95LatencyMs, String errorMessage,
                    Long triggeredBy, LocalDateTime startedAt, LocalDateTime completedAt,
                    List<CaseResult> results) {
    public Run {
      results = List.copyOf(results);
    }
  }
}
