package com.zhangzhewen.ragdemo.application.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhangzhewen.ragdemo.application.BusinessException;
import com.zhangzhewen.ragdemo.application.dto.EvaluationDtos.CaseRequest;
import com.zhangzhewen.ragdemo.application.dto.EvaluationDtos.CreateDatasetRequest;
import com.zhangzhewen.ragdemo.application.dto.EvaluationDtos.ExpectedContextRequest;
import com.zhangzhewen.ragdemo.domain.evaluation.EvaluationModels.Dataset;
import com.zhangzhewen.ragdemo.domain.evaluation.EvaluationPolicy;
import com.zhangzhewen.ragdemo.domain.gateway.EvaluationConfigurationGateway;
import com.zhangzhewen.ragdemo.domain.gateway.EvaluationGateway;
import com.zhangzhewen.ragdemo.domain.gateway.KnowledgeGateway;
import com.zhangzhewen.ragdemo.domain.knowledge.KnowledgeBase;
import com.zhangzhewen.ragdemo.domain.knowledge.KnowledgeBaseStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 评估管理用例测试。
 */
class EvaluationServiceTest {

  private final EvaluationGateway gateway = mock(EvaluationGateway.class);
  private final EvaluationConfigurationGateway configuration = mock(
      EvaluationConfigurationGateway.class);
  private final KnowledgeGateway knowledge = mock(KnowledgeGateway.class);
  private final EvaluationWorker worker = mock(EvaluationWorker.class);
  private final EvaluationPolicy policy = new EvaluationPolicy(.9, .7, .8, .6, .8, .8, .8,
      .9, .03);
  private final EvaluationService service = new EvaluationService(gateway, configuration,
      knowledge, worker, policy);

  @Test
  void exposesCurrentEvaluationThresholds() {
    assertThat(service.thresholds()).isSameAs(policy);
  }

  @Test
  void createsVersionedDatasetWithGoldenEvidence() {
    when(knowledge.findKnowledgeById(1L)).thenReturn(Optional.of(
        new KnowledgeBase(1L, "制度库", null, null, KnowledgeBaseStatus.ENABLED)));
    when(gateway.createDataset(org.mockito.ArgumentMatchers.eq(1L),
        org.mockito.ArgumentMatchers.eq("核心问题"), org.mockito.ArgumentMatchers.eq("v1"),
        org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq(9L)))
        .thenReturn(7L);
    CreateDatasetRequest request = request("FACTUAL",
        List.of(new ExpectedContextRequest("制度.md", "年假十天")));

    assertThat(service.createDataset(request, 9L)).isEqualTo(7L);
    verify(gateway).createDataset(org.mockito.ArgumentMatchers.eq(1L),
        org.mockito.ArgumentMatchers.eq("核心问题"), org.mockito.ArgumentMatchers.eq("v1"),
        org.mockito.ArgumentMatchers.argThat(cases -> cases.size() == 1
            && cases.getFirst().expectedContexts().size() == 1),
        org.mockito.ArgumentMatchers.eq(9L));
  }

  @Test
  void rejectsNonRefusalCaseWithoutGoldenEvidence() {
    when(knowledge.findKnowledgeById(1L)).thenReturn(Optional.of(
        new KnowledgeBase(1L, "制度库", null, null, KnowledgeBaseStatus.ENABLED)));

    assertThatThrownBy(() -> service.createDataset(request("FACTUAL", List.of()), 9L))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("至少需要一条黄金证据");
  }

  @Test
  void startsRunAgainstLatestPassedBaseline() {
    Dataset dataset = new Dataset(3L, 1L, "核心问题", "v1", 1, 9L,
        LocalDateTime.now(), List.of());
    when(gateway.findDataset(3L)).thenReturn(Optional.of(dataset));
    when(configuration.snapshot()).thenReturn("{}");
    when(gateway.createRun(3L, null, "{}", 9L, 1)).thenReturn(5L);

    assertThat(service.start(3L, 9L)).isEqualTo(5L);
    verify(worker).process(5L);
  }

  private CreateDatasetRequest request(String answerType,
      List<ExpectedContextRequest> contexts) {
    return new CreateDatasetRequest(1L, "核心问题", "v1",
        List.of(new CaseRequest("年假几天", "十天", answerType, true, contexts)));
  }
}
