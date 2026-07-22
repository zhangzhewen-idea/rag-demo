package com.zhangzhewen.ragdemo.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.zhangzhewen.ragdemo.domain.evaluation.EvaluationModels.CaseExecution;
import com.zhangzhewen.ragdemo.domain.evaluation.EvaluationModels.Scores;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * 评估结果 JSON 的持久化兼容测试。
 */
class JdbcEvaluationGatewayTest {

  @Test
  void readsLegacyExecutionWithoutRefusedField() {
    JsonMapper mapper = JsonMapper.builder()
        .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
        .build();
    JdbcEvaluationGateway gateway = new JdbcEvaluationGateway(new JdbcTemplate(), mapper);
    CaseExecution execution = new CaseExecution(1L, "历史回答", false, "改写问题", List.of(),
        List.of(), List.of(), new Scores(null, null, null, null, .8, .9, .7, null),
        "历史评估", 10, 5, 20, null);
    String legacyJson = mapper.writeValueAsString(execution)
        .replace("\"refused\":false,", "");
    assertThat(legacyJson).doesNotContain("refused");

    CaseExecution restored = gateway.readExecution(legacyJson);

    assertThat(restored.refused()).isFalse();
    assertThat(restored.answer()).isEqualTo("历史回答");
  }
}
