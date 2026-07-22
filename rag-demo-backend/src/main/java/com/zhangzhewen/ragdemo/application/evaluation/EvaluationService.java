package com.zhangzhewen.ragdemo.application.evaluation;

import com.zhangzhewen.ragdemo.application.BusinessException;
import com.zhangzhewen.ragdemo.application.dto.EvaluationDtos.CaseRequest;
import com.zhangzhewen.ragdemo.application.dto.EvaluationDtos.CreateDatasetRequest;
import com.zhangzhewen.ragdemo.domain.evaluation.EvaluationModels.Dataset;
import com.zhangzhewen.ragdemo.domain.evaluation.EvaluationModels.EvaluationCase;
import com.zhangzhewen.ragdemo.domain.evaluation.EvaluationModels.ExpectedContext;
import com.zhangzhewen.ragdemo.domain.evaluation.EvaluationModels.Run;
import com.zhangzhewen.ragdemo.domain.gateway.EvaluationConfigurationGateway;
import com.zhangzhewen.ragdemo.domain.gateway.EvaluationGateway;
import com.zhangzhewen.ragdemo.domain.gateway.KnowledgeGateway;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * 管理评估集版本、触发异步运行、查询报告和提交人工复核。
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class EvaluationService {

  private static final Set<String> ANSWER_TYPES = Set.of("FACTUAL", "PROCEDURE", "COMPARISON",
      "REFUSAL", "SUMMARY");
  private static final Set<String> REVIEW_VERDICTS = Set.of("ACCURATE", "INACCURATE");
  private final EvaluationGateway evaluations;
  private final EvaluationConfigurationGateway configuration;
  private final KnowledgeGateway knowledge;
  private final EvaluationWorker worker;

  public EvaluationService(EvaluationGateway evaluations,
      EvaluationConfigurationGateway configuration, KnowledgeGateway knowledge,
      EvaluationWorker worker) {
    this.evaluations = evaluations;
    this.configuration = configuration;
    this.knowledge = knowledge;
    this.worker = worker;
  }

  public Long createDataset(CreateDatasetRequest request, Long userId) {
    knowledge.findKnowledgeById(request.knowledgeBaseId()).orElseThrow(
        () -> new BusinessException("KB_NOT_FOUND", "知识库不存在", HttpStatus.NOT_FOUND));
    boolean duplicate = evaluations.listDatasets(request.knowledgeBaseId()).stream()
        .anyMatch(item -> item.name().equals(request.name().trim())
            && item.version().equals(request.version().trim()));
    if (duplicate) {
      throw new BusinessException("EVALUATION_DATASET_EXISTS", "同名评估集版本已存在",
          HttpStatus.CONFLICT);
    }
    List<EvaluationCase> cases = request.cases().stream().map(this::mapCase).toList();
    return evaluations.createDataset(request.knowledgeBaseId(), request.name().trim(),
        request.version().trim(), cases, userId);
  }

  public List<Dataset> listDatasets(Long knowledgeBaseId) {
    return evaluations.listDatasets(knowledgeBaseId);
  }

  public Dataset dataset(Long id) {
    return evaluations.findDataset(id).orElseThrow(
        () -> new BusinessException("EVALUATION_DATASET_NOT_FOUND", "评估集不存在",
            HttpStatus.NOT_FOUND));
  }

  public Long start(Long datasetId, Long userId) {
    Dataset dataset = dataset(datasetId);
    if (evaluations.hasActiveRun(datasetId)) {
      throw new BusinessException("EVALUATION_ALREADY_RUNNING", "该评估集已有运行中的任务",
          HttpStatus.CONFLICT);
    }
    Run baseline = evaluations.findLatestPassedRun(datasetId).orElse(null);
    Long runId = evaluations.createRun(datasetId, baseline == null ? null : baseline.id(),
        configuration.snapshot(), userId, dataset.caseCount());
    worker.process(runId);
    return runId;
  }

  public List<Run> listRuns(Long datasetId) {
    dataset(datasetId);
    return evaluations.listRuns(datasetId);
  }

  public Run run(Long id) {
    return evaluations.findRun(id).orElseThrow(
        () -> new BusinessException("EVALUATION_RUN_NOT_FOUND", "评估运行不存在",
            HttpStatus.NOT_FOUND));
  }

  public void review(Long resultId, String verdict, String comment, Long reviewerId) {
    String normalized = verdict == null ? "" : verdict.trim().toUpperCase();
    if (!REVIEW_VERDICTS.contains(normalized)) {
      throw new BusinessException("EVALUATION_REVIEW_INVALID", "复核结论仅支持 ACCURATE 或 INACCURATE",
          HttpStatus.BAD_REQUEST);
    }
    try {
      evaluations.review(resultId, normalized, comment == null ? null : comment.trim(), reviewerId);
    } catch (IllegalArgumentException exception) {
      throw new BusinessException("EVALUATION_RESULT_NOT_FOUND", "评估结果不存在",
          HttpStatus.NOT_FOUND);
    }
  }

  private EvaluationCase mapCase(CaseRequest request) {
    String answerType = request.answerType().trim().toUpperCase();
    if (!ANSWER_TYPES.contains(answerType)) {
      throw new BusinessException("EVALUATION_ANSWER_TYPE_INVALID",
          "答案类型仅支持 FACTUAL、PROCEDURE、COMPARISON、REFUSAL、SUMMARY",
          HttpStatus.BAD_REQUEST);
    }
    List<ExpectedContext> contexts = request.expectedContexts() == null ? List.of()
        : request.expectedContexts().stream().map(item -> new ExpectedContext(
            item.sourceName().trim(), item.evidenceContains().trim())).toList();
    if (!"REFUSAL".equals(answerType) && contexts.isEmpty()) {
      throw new BusinessException("EVALUATION_CONTEXT_REQUIRED", "非拒答样本至少需要一条黄金证据",
          HttpStatus.BAD_REQUEST);
    }
    if ("REFUSAL".equals(answerType) && !contexts.isEmpty()) {
      throw new BusinessException("EVALUATION_REFUSAL_CONTEXT_INVALID", "拒答样本不应配置黄金证据",
          HttpStatus.BAD_REQUEST);
    }
    return new EvaluationCase(null, request.question().trim(), request.goldenAnswer().trim(),
        answerType, request.critical(), contexts);
  }
}
