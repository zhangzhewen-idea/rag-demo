package com.zhangzhewen.ragdemo.infrastructure.persistence;

import com.zhangzhewen.ragdemo.domain.evaluation.EvaluationModels.CaseExecution;
import com.zhangzhewen.ragdemo.domain.evaluation.EvaluationModels.CaseResult;
import com.zhangzhewen.ragdemo.domain.evaluation.EvaluationModels.Dataset;
import com.zhangzhewen.ragdemo.domain.evaluation.EvaluationModels.EvaluationCase;
import com.zhangzhewen.ragdemo.domain.evaluation.EvaluationModels.ExpectedContext;
import com.zhangzhewen.ragdemo.domain.evaluation.EvaluationModels.Run;
import com.zhangzhewen.ragdemo.domain.evaluation.EvaluationModels.Scores;
import com.zhangzhewen.ragdemo.domain.gateway.EvaluationGateway;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * 基于 Spring JDBC 保存不可变评估集、运行轨迹和人工复核。
 */
@Repository
public class JdbcEvaluationGateway implements EvaluationGateway {

  private static final String RUN_COLUMNS = "id,dataset_id,baseline_run_id,status,config_snapshot,"
      + "candidate_hit_rate,candidate_mrr,context_recall,context_precision,faithfulness,"
      + "answer_relevancy,evidence_support_accuracy,no_answer_accuracy,passed,total_cases,"
      + "completed_cases,failed_cases,prompt_tokens,completion_tokens,latency_ms,p95_latency_ms,"
      + "error_message,triggered_by,started_at,completed_at";
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public JdbcEvaluationGateway(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  @Override
  @Transactional
  public Long createDataset(Long knowledgeBaseId, String name, String version,
      List<EvaluationCase> cases, Long createdBy) {
    long datasetId = insert(
        "INSERT INTO rag_evaluation_dataset(knowledge_base_id,name,version,created_by) VALUES(?,?,?,?)",
        knowledgeBaseId, name, version, createdBy);
    for (EvaluationCase evaluationCase : cases) {
      long caseId = insert(
          "INSERT INTO rag_evaluation_case(dataset_id,question,golden_answer,answer_type,critical) VALUES(?,?,?,?,?)",
          datasetId, evaluationCase.question(), evaluationCase.goldenAnswer(),
          evaluationCase.answerType(), evaluationCase.critical());
      for (ExpectedContext context : evaluationCase.expectedContexts()) {
        jdbc.update(
            "INSERT INTO rag_evaluation_expected_context(case_id,source_name,evidence_contains) VALUES(?,?,?)",
            caseId, context.sourceName(), context.evidenceContains());
      }
    }
    return datasetId;
  }

  @Override
  public List<Dataset> listDatasets(Long knowledgeBaseId) {
    String where = knowledgeBaseId == null ? "" : " WHERE d.knowledge_base_id=?";
    String sql = "SELECT d.id,d.knowledge_base_id,d.name,d.version,d.created_by,d.created_at,"
        + "COUNT(c.id) case_count FROM rag_evaluation_dataset d LEFT JOIN rag_evaluation_case c "
        + "ON c.dataset_id=d.id" + where
        + " GROUP BY d.id,d.knowledge_base_id,d.name,d.version,d.created_by,d.created_at ORDER BY d.id DESC";
    Object[] args = knowledgeBaseId == null ? new Object[0] : new Object[]{knowledgeBaseId};
    return jdbc.query(sql, (rs, n) -> new Dataset(rs.getLong("id"),
        rs.getLong("knowledge_base_id"), rs.getString("name"), rs.getString("version"),
        rs.getInt("case_count"), rs.getLong("created_by"),
        rs.getTimestamp("created_at").toLocalDateTime(), List.of()), args);
  }

  @Override
  public Optional<Dataset> findDataset(Long id) {
    return jdbc.query(
        "SELECT d.id,d.knowledge_base_id,d.name,d.version,d.created_by,d.created_at,COUNT(c.id) case_count "
            + "FROM rag_evaluation_dataset d LEFT JOIN rag_evaluation_case c ON c.dataset_id=d.id "
            + "WHERE d.id=? GROUP BY d.id,d.knowledge_base_id,d.name,d.version,d.created_by,d.created_at",
        (rs, n) -> new Dataset(rs.getLong("id"), rs.getLong("knowledge_base_id"),
            rs.getString("name"), rs.getString("version"), rs.getInt("case_count"),
            rs.getLong("created_by"), rs.getTimestamp("created_at").toLocalDateTime(),
            loadCases(id)), id).stream().findFirst();
  }

  private List<EvaluationCase> loadCases(Long datasetId) {
    return jdbc.query(
        "SELECT id,question,golden_answer,answer_type,critical FROM rag_evaluation_case WHERE dataset_id=? ORDER BY id",
        (rs, n) -> {
          long caseId = rs.getLong("id");
          List<ExpectedContext> contexts = jdbc.query(
              "SELECT source_name,evidence_contains FROM rag_evaluation_expected_context WHERE case_id=? ORDER BY id",
              (contextRs, index) -> new ExpectedContext(contextRs.getString("source_name"),
                  contextRs.getString("evidence_contains")), caseId);
          return new EvaluationCase(caseId, rs.getString("question"),
              rs.getString("golden_answer"), rs.getString("answer_type"),
              rs.getBoolean("critical"), contexts);
        }, datasetId);
  }

  @Override
  public Long createRun(Long datasetId, Long baselineRunId, String configSnapshot,
      Long triggeredBy, int totalCases) {
    return insert(
        "INSERT INTO rag_evaluation_run(dataset_id,baseline_run_id,status,config_snapshot,total_cases,triggered_by) VALUES(?,?,'QUEUED',CAST(? AS JSON),?,?)",
        datasetId, baselineRunId, configSnapshot, totalCases, triggeredBy);
  }

  @Override
  public void markRunRunning(Long runId) {
    jdbc.update("UPDATE rag_evaluation_run SET status='RUNNING',started_at=NOW(3) WHERE id=? AND status='QUEUED'",
        runId);
  }

  @Override
  public Long saveCaseResult(Long runId, EvaluationCase evaluationCase, CaseExecution execution,
      boolean passed) {
    return insert(
        "INSERT INTO rag_evaluation_case_result(run_id,case_id,execution_json,passed) VALUES(?,?,CAST(? AS JSON),?)",
        runId, evaluationCase.id(), mapper.writeValueAsString(execution), passed);
  }

  @Override
  public void completeRun(Long runId, String status, Scores scores, boolean passed,
      int completedCases, int failedCases, int promptTokens, int completionTokens,
      long latencyMs, long p95LatencyMs, String errorMessage) {
    jdbc.update("UPDATE rag_evaluation_run SET status=?,candidate_hit_rate=?,candidate_mrr=?,"
            + "context_recall=?,context_precision=?,faithfulness=?,answer_relevancy=?,"
            + "evidence_support_accuracy=?,no_answer_accuracy=?,passed=?,completed_cases=?,"
            + "failed_cases=?,prompt_tokens=?,completion_tokens=?,latency_ms=?,p95_latency_ms=?,error_message=?,"
            + "completed_at=NOW(3) WHERE id=?",
        status, scores.candidateHitRate(), scores.candidateMrr(), scores.contextRecall(),
        scores.contextPrecision(), scores.faithfulness(), scores.answerRelevancy(),
        scores.evidenceSupportAccuracy(), scores.noAnswerAccuracy(), passed, completedCases,
        failedCases, promptTokens, completionTokens, latencyMs, p95LatencyMs, errorMessage, runId);
  }

  @Override
  public Optional<Run> findRun(Long id) {
    return queryRuns(" WHERE id=?", id).stream().findFirst()
        .map(run -> copyWithResults(run, loadResults(run.id())));
  }

  @Override
  public List<Run> listRuns(Long datasetId) {
    return queryRuns(" WHERE dataset_id=? ORDER BY id DESC", datasetId);
  }

  @Override
  public Optional<Run> findLatestPassedRun(Long datasetId) {
    return queryRuns(" WHERE dataset_id=? AND status='PASSED' ORDER BY id DESC LIMIT 1", datasetId)
        .stream().findFirst();
  }

  @Override
  public boolean hasActiveRun(Long datasetId) {
    Integer count = jdbc.queryForObject(
        "SELECT COUNT(*) FROM rag_evaluation_run WHERE dataset_id=? AND status IN ('QUEUED','RUNNING')",
        Integer.class, datasetId);
    return count != null && count > 0;
  }

  @Override
  public void review(Long resultId, String verdict, String comment, Long reviewerId) {
    int updated = jdbc.update(
        "UPDATE rag_evaluation_case_result SET review_verdict=?,review_comment=?,reviewed_by=?,reviewed_at=NOW(3) WHERE id=?",
        verdict, comment, reviewerId, resultId);
    if (updated == 0) {
      throw new IllegalArgumentException("评估结果不存在");
    }
  }

  private List<Run> queryRuns(String suffix, Object... args) {
    return jdbc.query("SELECT " + RUN_COLUMNS + " FROM rag_evaluation_run" + suffix,
        (rs, n) -> new Run(rs.getLong("id"), rs.getLong("dataset_id"),
            nullableLong(rs.getObject("baseline_run_id")), rs.getString("status"),
            rs.getString("config_snapshot"), scores(rs.getObject("candidate_hit_rate"),
            rs.getObject("candidate_mrr"), rs.getObject("context_recall"),
            rs.getObject("context_precision"), rs.getObject("faithfulness"),
            rs.getObject("answer_relevancy"), rs.getObject("evidence_support_accuracy"),
            rs.getObject("no_answer_accuracy")), rs.getBoolean("passed"),
            rs.getInt("total_cases"), rs.getInt("completed_cases"), rs.getInt("failed_cases"),
            rs.getInt("prompt_tokens"), rs.getInt("completion_tokens"),
            rs.getLong("latency_ms"), rs.getLong("p95_latency_ms"),
            rs.getString("error_message"),
            rs.getLong("triggered_by"), localDateTime(rs.getTimestamp("started_at")),
            localDateTime(rs.getTimestamp("completed_at")), List.of()), args);
  }

  private List<CaseResult> loadResults(Long runId) {
    return jdbc.query(
        "SELECT id,case_id,execution_json,passed,review_verdict,review_comment,reviewed_by,reviewed_at "
            + "FROM rag_evaluation_case_result WHERE run_id=? ORDER BY id",
        (rs, n) -> {
          long caseId = rs.getLong("case_id");
          EvaluationCase evaluationCase = loadCase(caseId);
          CaseExecution execution = mapper.readValue(rs.getString("execution_json"),
              CaseExecution.class);
          return new CaseResult(rs.getLong("id"), runId, evaluationCase, execution,
              rs.getBoolean("passed"), rs.getString("review_verdict"),
              rs.getString("review_comment"), nullableLong(rs.getObject("reviewed_by")),
              localDateTime(rs.getTimestamp("reviewed_at")));
        }, runId);
  }

  private EvaluationCase loadCase(Long caseId) {
    return jdbc.query(
        "SELECT id,question,golden_answer,answer_type,critical FROM rag_evaluation_case WHERE id=?",
        (rs, n) -> new EvaluationCase(rs.getLong("id"), rs.getString("question"),
            rs.getString("golden_answer"), rs.getString("answer_type"),
            rs.getBoolean("critical"), jdbc.query(
            "SELECT source_name,evidence_contains FROM rag_evaluation_expected_context WHERE case_id=? ORDER BY id",
            (contextRs, index) -> new ExpectedContext(contextRs.getString("source_name"),
                contextRs.getString("evidence_contains")), caseId)), caseId).getFirst();
  }

  private Run copyWithResults(Run run, List<CaseResult> results) {
    return new Run(run.id(), run.datasetId(), run.baselineRunId(), run.status(),
        run.configSnapshot(), run.scores(), run.passed(), run.totalCases(), run.completedCases(),
        run.failedCases(), run.promptTokens(), run.completionTokens(), run.latencyMs(),
        run.p95LatencyMs(), run.errorMessage(), run.triggeredBy(), run.startedAt(),
        run.completedAt(), results);
  }

  private Scores scores(Object hit, Object mrr, Object recall, Object precision,
      Object faithfulness, Object relevancy, Object support, Object noAnswer) {
    return new Scores(decimal(hit), decimal(mrr), decimal(recall), decimal(precision),
        zeroIfNull(decimal(faithfulness)), zeroIfNull(decimal(relevancy)),
        zeroIfNull(decimal(support)), decimal(noAnswer));
  }

  private Double decimal(Object value) {
    return value == null ? null : ((Number) value).doubleValue();
  }

  private double zeroIfNull(Double value) {
    return value == null ? 0D : value;
  }

  private Long nullableLong(Object value) {
    return value == null ? null : ((Number) value).longValue();
  }

  private LocalDateTime localDateTime(Timestamp value) {
    return value == null ? null : value.toLocalDateTime();
  }

  private long insert(String sql, Object... params) {
    KeyHolder keys = new GeneratedKeyHolder();
    jdbc.update(connection -> {
      PreparedStatement statement = connection.prepareStatement(sql,
          Statement.RETURN_GENERATED_KEYS);
      for (int i = 0; i < params.length; i++) {
        statement.setObject(i + 1, params[i]);
      }
      return statement;
    }, keys);
    if (keys.getKey() == null) {
      throw new IllegalStateException("数据库未返回主键");
    }
    return keys.getKey().longValue();
  }
}
