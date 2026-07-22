package com.zhangzhewen.ragdemo.application.evaluation;

import com.zhangzhewen.ragdemo.application.conversation.EvidenceRetrievalService;
import com.zhangzhewen.ragdemo.domain.conversation.AiUsage;
import com.zhangzhewen.ragdemo.domain.conversation.AnswerContext;
import com.zhangzhewen.ragdemo.domain.conversation.ContextAssemblyPolicy;
import com.zhangzhewen.ragdemo.domain.conversation.ConversationPrompt;
import com.zhangzhewen.ragdemo.domain.conversation.GeneratedAnswer;
import com.zhangzhewen.ragdemo.domain.conversation.RetrievalTrace;
import com.zhangzhewen.ragdemo.domain.evaluation.EvaluationMetrics;
import com.zhangzhewen.ragdemo.domain.evaluation.EvaluationMetrics.RetrievalScores;
import com.zhangzhewen.ragdemo.domain.evaluation.EvaluationModels.CaseExecution;
import com.zhangzhewen.ragdemo.domain.evaluation.EvaluationModels.Dataset;
import com.zhangzhewen.ragdemo.domain.evaluation.EvaluationModels.EvaluationCase;
import com.zhangzhewen.ragdemo.domain.evaluation.EvaluationModels.Judgment;
import com.zhangzhewen.ragdemo.domain.evaluation.EvaluationModels.Run;
import com.zhangzhewen.ragdemo.domain.evaluation.EvaluationModels.Scores;
import com.zhangzhewen.ragdemo.domain.evaluation.EvaluationPolicy;
import com.zhangzhewen.ragdemo.domain.gateway.AiGateway;
import com.zhangzhewen.ragdemo.domain.gateway.EvaluationGateway;
import com.zhangzhewen.ragdemo.domain.gateway.EvaluationJudgeGateway;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 在独立线程池逐题回放生产 RAG 链路并汇总质量报告。
 */
@Component
public class EvaluationWorker {

  private static final Logger log = LoggerFactory.getLogger(EvaluationWorker.class);
  private final EvaluationGateway evaluations;
  private final EvidenceRetrievalService retrieval;
  private final ContextAssemblyPolicy contextPolicy;
  private final AiGateway ai;
  private final EvaluationJudgeGateway judge;
  private final EvaluationPolicy policy;

  public EvaluationWorker(EvaluationGateway evaluations, EvidenceRetrievalService retrieval,
      ContextAssemblyPolicy contextPolicy, AiGateway ai, EvaluationJudgeGateway judge,
      EvaluationPolicy policy) {
    this.evaluations = evaluations;
    this.retrieval = retrieval;
    this.contextPolicy = contextPolicy;
    this.ai = ai;
    this.judge = judge;
    this.policy = policy;
  }

  @Async("evaluationTaskExecutor")
  public void process(Long runId) {
    long runStart = System.nanoTime();
    try {
      processRun(runId, runStart);
    } catch (RuntimeException exception) {
      String reason = exception.getClass().getSimpleName() + ": "
          + safeMessage(exception.getMessage());
      log.error("RAG 评估运行异常: runId={}, reason={}", runId,
          exception.getClass().getSimpleName(), exception);
      try {
        evaluations.completeRun(runId, "ERROR", zeroScores(), false, 0, 0, 0, 0,
            elapsed(runStart), 0, reason);
      } catch (RuntimeException persistenceException) {
        log.error("RAG 评估异常状态保存失败: runId={}", runId, persistenceException);
      }
    }
  }

  private void processRun(Long runId, long runStart) {
    Run run = evaluations.findRun(runId)
        .orElseThrow(() -> new IllegalStateException("评估运行不存在"));
    Dataset dataset = evaluations.findDataset(run.datasetId())
        .orElseThrow(() -> new IllegalStateException("评估集不存在"));
    evaluations.markRunRunning(runId);
    List<CaseExecution> completed = new ArrayList<>();
    int failed = 0;
    boolean criticalFailure = false;
    for (EvaluationCase evaluationCase : dataset.cases()) {
      CaseExecution execution;
      boolean casePassed;
      try {
        execution = execute(dataset.knowledgeBaseId(), evaluationCase);
        casePassed = policy.passes(execution.scores(), null);
        completed.add(execution);
      } catch (RuntimeException exception) {
        failed++;
        casePassed = false;
        String reason = exception.getClass().getSimpleName() + ": "
            + safeMessage(exception.getMessage());
        execution = new CaseExecution(evaluationCase.id(), "", false, "", List.of(), List.of(),
            List.of(), zeroScores(), reason, 0, 0, 0, reason);
        log.warn("RAG 评估样本执行失败: runId={}, caseId={}, reason={}", runId,
            evaluationCase.id(), exception.getClass().getSimpleName());
      }
      if (evaluationCase.critical() && !casePassed) {
        criticalFailure = true;
      }
      evaluations.saveCaseResult(runId, evaluationCase, execution, casePassed);
    }
    Scores aggregate = aggregate(completed);
    Scores baseline = run.baselineRunId() == null ? null
        : evaluations.findRun(run.baselineRunId()).map(Run::scores).orElse(null);
    boolean passed = failed == 0 && !criticalFailure && !completed.isEmpty()
        && policy.passes(aggregate, baseline);
    String status = completed.isEmpty() ? "ERROR" : passed ? "PASSED" : "FAILED";
    evaluations.completeRun(runId, status, aggregate, passed, completed.size(), failed,
        completed.stream().mapToInt(CaseExecution::promptTokens).sum(),
        completed.stream().mapToInt(CaseExecution::completionTokens).sum(), elapsed(runStart),
        p95Latency(completed), failed == 0 ? null : failed + " 条样本执行失败");
  }

  private CaseExecution execute(Long knowledgeBaseId, EvaluationCase evaluationCase) {
    long start = System.nanoTime();
    RetrievalTrace trace = retrieval.retrieve(knowledgeBaseId, evaluationCase.question(), "",
        List.of());
    GeneratedAnswer generated;
    if (trace.finalEvidence().isEmpty()) {
      generated = GeneratedAnswer.refused(ConversationPrompt.NO_EVIDENCE);
    } else {
      AnswerContext context = contextPolicy.assemble(evaluationCase.question(), "", List.of(),
          trace.finalEvidence());
      generated = ai.generateAnswer(context);
    }
    String answer = generated.refused() ? ConversationPrompt.NO_EVIDENCE : generated.content();
    AiUsage usage = generated.usage();
    Judgment judgment = judge.judge(evaluationCase.question(), evaluationCase.goldenAnswer(),
        evaluationCase.answerType(), answer, trace.finalEvidence());
    RetrievalScores retrievalScores = EvaluationMetrics.calculate(
        evaluationCase.expectedContexts(), trace.candidates(), trace.finalEvidence(),
        generated.refused());
    double relevancy = "REFUSAL".equals(evaluationCase.answerType())
        && !generated.refused() ? 0D : judgment.answerRelevancy();
    Scores scores = new Scores(retrievalScores.candidateHitRate(), retrievalScores.candidateMrr(),
        retrievalScores.contextRecall(), retrievalScores.contextPrecision(),
        judgment.faithfulness(), relevancy, judgment.evidenceSupportAccuracy(),
        retrievalScores.noAnswerAccuracy());
    return new CaseExecution(evaluationCase.id(), answer, generated.refused(),
        trace.rewrittenQuery(), trace.expandedQueries(), trace.candidates(), trace.finalEvidence(),
        scores, judgment.rationale(), usage.promptTokens(), usage.completionTokens(), elapsed(start),
        null);
  }

  private Scores aggregate(List<CaseExecution> executions) {
    return new Scores(averageNullable(executions, 0), averageNullable(executions, 1),
        averageNullable(executions, 2), averageNullable(executions, 3),
        executions.stream().mapToDouble(item -> item.scores().faithfulness()).average().orElse(0D),
        executions.stream().mapToDouble(item -> item.scores().answerRelevancy()).average().orElse(0D),
        executions.stream().mapToDouble(item -> item.scores().evidenceSupportAccuracy()).average()
            .orElse(0D), averageNullable(executions, 4));
  }

  private Double averageNullable(List<CaseExecution> executions, int metric) {
    return executions.stream().map(CaseExecution::scores).map(scores -> switch (metric) {
      case 0 -> scores.candidateHitRate();
      case 1 -> scores.candidateMrr();
      case 2 -> scores.contextRecall();
      case 3 -> scores.contextPrecision();
      default -> scores.noAnswerAccuracy();
    }).filter(java.util.Objects::nonNull).mapToDouble(Double::doubleValue).average()
        .stream().boxed().findFirst().orElse(null);
  }

  private Scores zeroScores() {
    return new Scores(0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D);
  }

  private long p95Latency(List<CaseExecution> executions) {
    if (executions.isEmpty()) {
      return 0;
    }
    List<Long> sorted = executions.stream().map(CaseExecution::latencyMs).sorted().toList();
    int index = Math.max(0, (int) Math.ceil(sorted.size() * .95) - 1);
    return sorted.get(index);
  }

  private String safeMessage(String message) {
    if (message == null || message.isBlank()) {
      return "未知错误";
    }
    return message.length() <= 900 ? message : message.substring(0, 900);
  }

  private long elapsed(long start) {
    return (System.nanoTime() - start) / 1_000_000;
  }
}
