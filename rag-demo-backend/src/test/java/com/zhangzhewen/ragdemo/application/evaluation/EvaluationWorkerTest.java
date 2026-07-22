package com.zhangzhewen.ragdemo.application.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.zhangzhewen.ragdemo.application.conversation.EvidenceRetrievalService;
import com.zhangzhewen.ragdemo.domain.conversation.AiUsage;
import com.zhangzhewen.ragdemo.domain.conversation.AnswerContext;
import com.zhangzhewen.ragdemo.domain.conversation.ContextAssemblyPolicy;
import com.zhangzhewen.ragdemo.domain.conversation.GeneratedAnswer;
import com.zhangzhewen.ragdemo.domain.conversation.RetrievalQuery;
import com.zhangzhewen.ragdemo.domain.conversation.RetrievalTrace;
import com.zhangzhewen.ragdemo.domain.conversation.RetrievedChunk;
import com.zhangzhewen.ragdemo.domain.evaluation.EvaluationModels.CaseExecution;
import com.zhangzhewen.ragdemo.domain.evaluation.EvaluationModels.Dataset;
import com.zhangzhewen.ragdemo.domain.evaluation.EvaluationModels.EvaluationCase;
import com.zhangzhewen.ragdemo.domain.evaluation.EvaluationModels.ExpectedContext;
import com.zhangzhewen.ragdemo.domain.evaluation.EvaluationModels.Judgment;
import com.zhangzhewen.ragdemo.domain.evaluation.EvaluationModels.Run;
import com.zhangzhewen.ragdemo.domain.evaluation.EvaluationModels.Scores;
import com.zhangzhewen.ragdemo.domain.evaluation.EvaluationPolicy;
import com.zhangzhewen.ragdemo.domain.gateway.AiGateway;
import com.zhangzhewen.ragdemo.domain.gateway.EvaluationGateway;
import com.zhangzhewen.ragdemo.domain.gateway.EvaluationJudgeGateway;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 全链路评估运行编排测试。
 */
class EvaluationWorkerTest {

  @Test
  void persistsTraceScoresAndPassedRun() {
    EvaluationGateway evaluations = mock(EvaluationGateway.class);
    EvidenceRetrievalService retrieval = mock(EvidenceRetrievalService.class);
    ContextAssemblyPolicy contextPolicy = mock(ContextAssemblyPolicy.class);
    AiGateway ai = mock(AiGateway.class);
    EvaluationJudgeGateway judge = mock(EvaluationJudgeGateway.class);
    EvaluationPolicy policy = new EvaluationPolicy(.9, .7, .8, .6, .8, .8, .8, .9);
    EvaluationWorker worker = new EvaluationWorker(evaluations, retrieval, contextPolicy, ai,
        judge, policy);
    EvaluationCase evaluationCase = new EvaluationCase(11L, "年假几天", "十天", "FACTUAL",
        true, List.of(new ExpectedContext("制度.md", "年假为十天")));
    Dataset dataset = new Dataset(3L, 1L, "核心问题", "v1", 1, 9L,
        LocalDateTime.now(), List.of(evaluationCase));
    Run run = new Run(5L, 3L, null, "QUEUED", "{}", zeroScores(), false, 1, 0, 0,
        0, 0, 0, 0, null, 9L, null, null, List.of());
    RetrievedChunk chunk = new RetrievedChunk(1L, 2L, "制度.md", 0,
        "正式员工年假为十天", null, null, .9, null, .03, .95);
    RetrievalTrace trace = new RetrievalTrace("年假天数", List.of(new RetrievalQuery("年假天数",
        "年假 天数")), List.of(chunk), List.of(chunk));
    AnswerContext context = new AnswerContext("system", "user", List.of(chunk), 10, 100);
    when(evaluations.findRun(5L)).thenReturn(Optional.of(run));
    when(evaluations.findDataset(3L)).thenReturn(Optional.of(dataset));
    when(retrieval.retrieve(1L, "年假几天", "", List.of())).thenReturn(trace);
    when(contextPolicy.assemble("年假几天", "", List.of(), List.of(chunk))).thenReturn(context);
    when(ai.generateAnswer(context))
        .thenReturn(new GeneratedAnswer(false, "年假为十天", new AiUsage(10, 5)));
    when(judge.judge("年假几天", "十天", "FACTUAL", "年假为十天", List.of(chunk)))
        .thenReturn(new Judgment(.9, .9, .9, "证据充分"));

    worker.process(5L);

    verify(evaluations).saveCaseResult(eq(5L), eq(evaluationCase), any(), eq(true));
    verify(evaluations).completeRun(eq(5L), eq("PASSED"), any(Scores.class), eq(true), eq(1),
        eq(0), eq(10), eq(5), anyLong(), anyLong(), eq(null));
  }

  @Test
  void refusalCaseOnlyUsesRefusalAccuracy() {
    EvaluationGateway evaluations = mock(EvaluationGateway.class);
    EvidenceRetrievalService retrieval = mock(EvidenceRetrievalService.class);
    ContextAssemblyPolicy contextPolicy = mock(ContextAssemblyPolicy.class);
    AiGateway ai = mock(AiGateway.class);
    EvaluationJudgeGateway judge = mock(EvaluationJudgeGateway.class);
    EvaluationPolicy policy = new EvaluationPolicy(.9, .7, .8, .6, .8, .8, .8, .9);
    EvaluationWorker worker = new EvaluationWorker(evaluations, retrieval, contextPolicy, ai,
        judge, policy);
    EvaluationCase evaluationCase = new EvaluationCase(12L, "今天你吃饭了吗？", "拒绝回答",
        "REFUSAL", true, List.of());
    Dataset dataset = new Dataset(4L, 1L, "拒答问题", "v1", 1, 9L,
        LocalDateTime.now(), List.of(evaluationCase));
    Run run = new Run(6L, 4L, null, "QUEUED", "{}", zeroScores(), false, 1, 0, 0,
        0, 0, 0, 0, null, 9L, null, null, List.of());
    RetrievalTrace trace = new RetrievalTrace("今天你吃饭了吗？", List.of(), List.of(), List.of());
    when(evaluations.findRun(6L)).thenReturn(Optional.of(run));
    when(evaluations.findDataset(4L)).thenReturn(Optional.of(dataset));
    when(retrieval.retrieve(1L, evaluationCase.question(), "", List.of())).thenReturn(trace);

    worker.process(6L);

    ArgumentCaptor<CaseExecution> result = ArgumentCaptor.forClass(CaseExecution.class);
    verify(evaluations).saveCaseResult(eq(6L), eq(evaluationCase), result.capture(), eq(true));
    assertThat(result.getValue().refused()).isTrue();
    assertThat(result.getValue().scores().noAnswerAccuracy()).isEqualTo(1D);
    assertThat(result.getValue().scores().faithfulness()).isNull();
    assertThat(result.getValue().scores().answerRelevancy()).isNull();
    assertThat(result.getValue().scores().evidenceSupportAccuracy()).isNull();
    verify(evaluations).completeRun(eq(6L), eq("PASSED"), any(Scores.class), eq(true), eq(1),
        eq(0), eq(0), eq(0), anyLong(), anyLong(), eq(null));
    verifyNoInteractions(contextPolicy, ai, judge);
  }

  private Scores zeroScores() {
    return new Scores(0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D);
  }
}
