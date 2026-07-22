package com.zhangzhewen.ragdemo.domain.gateway;

import com.zhangzhewen.ragdemo.domain.evaluation.EvaluationModels.CaseExecution;
import com.zhangzhewen.ragdemo.domain.evaluation.EvaluationModels.Dataset;
import com.zhangzhewen.ragdemo.domain.evaluation.EvaluationModels.EvaluationCase;
import com.zhangzhewen.ragdemo.domain.evaluation.EvaluationModels.Run;
import com.zhangzhewen.ragdemo.domain.evaluation.EvaluationModels.Scores;
import java.util.List;
import java.util.Optional;

/**
 * 评估集、运行、逐题结果和人工复核持久化边界。
 */
public interface EvaluationGateway {

  Long createDataset(Long knowledgeBaseId, String name, String version,
      List<EvaluationCase> cases, Long createdBy);

  List<Dataset> listDatasets(Long knowledgeBaseId);

  Optional<Dataset> findDataset(Long id);

  boolean updateDatasetIfUnused(Long id, Long knowledgeBaseId, String name, String version,
      List<EvaluationCase> cases);

  boolean deleteDatasetIfUnused(Long id);

  Long createRun(Long datasetId, Long baselineRunId, String configSnapshot, Long triggeredBy,
      int totalCases);

  void markRunRunning(Long runId);

  Long saveCaseResult(Long runId, EvaluationCase evaluationCase, CaseExecution execution,
      boolean passed);

  void completeRun(Long runId, String status, Scores scores, boolean passed, int completedCases,
      int failedCases, int promptTokens, int completionTokens, long latencyMs,
      long p95LatencyMs, String errorMessage);

  Optional<Run> findRun(Long id);

  List<Run> listRuns(Long datasetId);

  Optional<Run> findLatestPassedRun(Long datasetId);

  boolean hasActiveRun(Long datasetId);

  void review(Long resultId, String verdict, String comment, Long reviewerId);
}
