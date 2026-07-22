package com.zhangzhewen.ragdemo.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 请求参数中文校验消息测试。
 */
class RequestValidationTest {

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  /**
   * 空知识库名称应返回可直接展示的中文消息。
   */
  @Test
  void validatesKnowledgeName() {
    var request = new KnowledgeDtos.SaveRequest(" ", "", "", "ENABLED");
    assertThat(validator.validate(request)).extracting(v -> v.getMessage())
        .contains("请输入知识库名称");
  }

  /**
   * 新增用户应同时校验账号、昵称和角色。
   */
  @Test
  void validatesCreateUserFields() {
    var request = new UserDtos.CreateRequest("", "", "123456", null, "ENABLED", Set.of());
    assertThat(validator.validate(request)).extracting(v -> v.getMessage())
        .contains("请输入账号", "请输入昵称", "请至少选择一个角色");
  }

  /**
   * 空会话标题应返回中文消息。
   */
  @Test
  void validatesConversationTitle() {
    var request = new KnowledgeDtos.RenameRequest(" ");
    assertThat(validator.validate(request)).extracting(v -> v.getMessage())
        .contains("请输入会话标题");
  }

  /**
   * 评估集至少包含一个问题并校验嵌套黄金证据。
   */
  @Test
  void validatesEvaluationDataset() {
    var empty = new EvaluationDtos.CreateDatasetRequest(1L, "评估集", "v1", List.of());
    var invalidContext = new EvaluationDtos.CreateDatasetRequest(1L, "评估集", "v1", List.of(
        new EvaluationDtos.CaseRequest("问题", "答案", "FACTUAL", false, List.of(
            new EvaluationDtos.ExpectedContextRequest("", "")))));

    assertThat(validator.validate(empty)).extracting(v -> v.getMessage())
        .contains("评估集至少包含一条样本");
    assertThat(validator.validate(invalidContext)).extracting(v -> v.getMessage())
        .contains("请输入证据来源", "请输入黄金证据片段");
  }
}
