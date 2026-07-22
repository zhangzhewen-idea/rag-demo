package com.zhangzhewen.ragdemo.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * RAG 评估集和人工复核接口 DTO。
 */
public final class EvaluationDtos {

  private EvaluationDtos() {
  }

  public record ExpectedContextRequest(
      @NotBlank(message = "请输入证据来源") @Size(max = 255, message = "证据来源不能超过 255 个字符") String sourceName,
      @NotBlank(message = "请输入黄金证据片段") @Size(max = 1000, message = "黄金证据片段不能超过 1000 个字符") String evidenceContains) {
  }

  public record CaseRequest(
      @NotBlank(message = "请输入评估问题") @Size(max = 4000, message = "评估问题不能超过 4000 个字符") String question,
      @NotBlank(message = "请输入黄金答案") String goldenAnswer,
      @NotBlank(message = "请选择答案类型") String answerType,
      boolean critical,
      @Valid List<ExpectedContextRequest> expectedContexts) {
  }

  public record CreateDatasetRequest(
      @NotNull(message = "请选择知识库") Long knowledgeBaseId,
      @NotBlank(message = "请输入评估集名称") @Size(max = 128, message = "评估集名称不能超过 128 个字符") String name,
      @NotBlank(message = "请输入评估集版本") @Size(max = 64, message = "评估集版本不能超过 64 个字符") String version,
      @NotEmpty(message = "评估集至少包含一条样本") @Size(max = 100, message = "单个评估集最多包含 100 条样本") @Valid List<CaseRequest> cases) {
  }

  public record ReviewRequest(
      @NotBlank(message = "请选择复核结论") String verdict,
      @Size(max = 1000, message = "复核备注不能超过 1000 个字符") String comment) {
  }
}
