package com.zhangzhewen.ragdemo.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG 业务配置。
 */
@ConfigurationProperties(prefix = "rag")
public record RagProperties(String storageRoot, Jwt jwt, Retrieval retrieval, Context context,
                            Reranking reranking, Ingestion ingestion, Evaluation evaluation) {

  /**
   * JWT 配置。
   */
  public record Jwt(String secret, Duration accessTtl, Duration refreshTtl) {

  }

  /**
   * 检索配置。
   */
  public record Retrieval(int topK, int candidateTopK, double similarityThreshold,
                          double semanticWeight, double bm25Weight) {

  }

  /**
   * 长会话上下文预算配置。
   */
  public record Context(int maxInputTokens, int reservedOutputTokens, int recentTurns,
                        int summaryTriggerTokens, int maxSummaryTokens,
                        int minEvidenceTokens) {

  }

  /**
   * 百炼文档重排配置。
   */
  public record Reranking(boolean enabled, String model, String baseUrl, String apiKey,
                          Duration timeout) {

  }

  /**
   * 入库配置。
   */
  public record Ingestion(int chunkSize, int chunkOverlap, int embeddingBatchSize) {

  }

  /**
   * 全链路评估 Judge 与质量门槛。
   */
  public record Evaluation(String pipelineVersion, String judgeModel,
                           double candidateHitRate, double candidateMrr,
                           double contextRecall, double contextPrecision, double faithfulness,
                           double answerRelevancy, double evidenceSupportAccuracy,
                           double noAnswerAccuracy, double maxRegression) {

  }
}
