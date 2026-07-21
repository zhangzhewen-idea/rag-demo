package com.zhangzhewen.ragdemo.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG 业务配置。
 */
@ConfigurationProperties(prefix = "rag")
public record RagProperties(String storageRoot, Jwt jwt, Retrieval retrieval, Ingestion ingestion) {

  /**
   * JWT 配置。
   */
  public record Jwt(String secret, Duration accessTtl, Duration refreshTtl) {

  }

  /**
   * 检索配置。
   */
  public record Retrieval(int topK, double similarityThreshold) {

  }

  /**
   * 入库配置。
   */
  public record Ingestion(int chunkSize, int chunkOverlap, int embeddingBatchSize) {

  }
}
