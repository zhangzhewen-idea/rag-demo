package com.zhangzhewen.ragdemo.adapter.web;

import com.zhangzhewen.ragdemo.application.dto.ApiResponse;
import com.zhangzhewen.ragdemo.application.dto.EvaluationDtos.CreateDatasetRequest;
import com.zhangzhewen.ragdemo.application.dto.EvaluationDtos.ReviewRequest;
import com.zhangzhewen.ragdemo.application.evaluation.EvaluationService;
import com.zhangzhewen.ragdemo.domain.evaluation.EvaluationModels.Dataset;
import com.zhangzhewen.ragdemo.domain.evaluation.EvaluationModels.Run;
import com.zhangzhewen.ragdemo.domain.evaluation.EvaluationPolicy;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员 RAG 评估集、异步运行、报告和人工复核接口。
 */
@RestController
@RequestMapping("/api/admin/evaluations")
@PreAuthorize("hasRole('ADMIN')")
public class EvaluationController {

  private final EvaluationService evaluations;

  public EvaluationController(EvaluationService evaluations) {
    this.evaluations = evaluations;
  }

  @GetMapping("/thresholds")
  public ApiResponse<EvaluationPolicy> thresholds() {
    return WebSupport.ok(evaluations.thresholds());
  }

  @PostMapping("/datasets")
  public ApiResponse<Map<String, Long>> createDataset(
      @Valid @RequestBody CreateDatasetRequest request, Authentication authentication) {
    return WebSupport.ok(Map.of("id",
        evaluations.createDataset(request, WebSupport.userId(authentication))));
  }

  @GetMapping("/datasets")
  public ApiResponse<List<Dataset>> datasets(
      @RequestParam(required = false) Long knowledgeBaseId) {
    return WebSupport.ok(evaluations.listDatasets(knowledgeBaseId));
  }

  @GetMapping("/datasets/{id}")
  public ApiResponse<Dataset> dataset(@PathVariable Long id) {
    return WebSupport.ok(evaluations.dataset(id));
  }

  @PutMapping("/datasets/{id}")
  public ApiResponse<Void> updateDataset(@PathVariable Long id,
      @Valid @RequestBody CreateDatasetRequest request) {
    evaluations.updateDataset(id, request);
    return WebSupport.ok(null);
  }

  @DeleteMapping("/datasets/{id}")
  public ApiResponse<Void> deleteDataset(@PathVariable Long id) {
    evaluations.deleteDataset(id);
    return WebSupport.ok(null);
  }

  @PostMapping("/datasets/{id}/runs")
  public ApiResponse<Map<String, Long>> start(@PathVariable Long id,
      Authentication authentication) {
    return WebSupport.ok(Map.of("id", evaluations.start(id, WebSupport.userId(authentication))));
  }

  @GetMapping("/datasets/{id}/runs")
  public ApiResponse<List<Run>> runs(@PathVariable Long id) {
    return WebSupport.ok(evaluations.listRuns(id));
  }

  @GetMapping("/runs/{id}")
  public ApiResponse<Run> run(@PathVariable Long id) {
    return WebSupport.ok(evaluations.run(id));
  }

  @PutMapping("/results/{id}/review")
  public ApiResponse<Void> review(@PathVariable Long id,
      @Valid @RequestBody ReviewRequest request, Authentication authentication) {
    evaluations.review(id, request.verdict(), request.comment(),
        WebSupport.userId(authentication));
    return WebSupport.ok(null);
  }
}
