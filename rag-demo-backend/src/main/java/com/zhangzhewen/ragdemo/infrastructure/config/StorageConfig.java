package com.zhangzhewen.ragdemo.infrastructure.config;

import com.zhangzhewen.ragdemo.domain.conversation.ContextAssemblyPolicy;
import com.zhangzhewen.ragdemo.domain.conversation.RetrievalPolicy;
import com.zhangzhewen.ragdemo.domain.evaluation.EvaluationPolicy;
import com.zhangzhewen.ragdemo.domain.gateway.TokenEstimator;
import com.zhangzhewen.ragdemo.domain.identity.AuthPolicy;
import com.zhangzhewen.ragdemo.domain.identity.JwtPolicy;
import com.zhangzhewen.ragdemo.domain.knowledge.IngestionPolicy;
import com.zhangzhewen.ragdemo.domain.knowledge.TextChunkingPolicy;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 配置属性与上传根目录启动校验。
 */
@Configuration
@EnableConfigurationProperties(RagProperties.class)
public class StorageConfig {

  /**
   * 暴露给应用层的认证策略，避免应用层依赖基础设施配置类型。
   */
  @Bean
  AuthPolicy authPolicy(RagProperties properties) {
    return new AuthPolicy(properties.jwt().refreshTtl());
  }

  /**
   * 暴露给 JWT 适配器的签发策略。
   */
  @Bean
  JwtPolicy jwtPolicy(RagProperties properties) {
    return new JwtPolicy(properties.jwt().secret(), properties.jwt().accessTtl());
  }

  /**
   * 暴露给应用层的检索策略。
   */
  @Bean
  RetrievalPolicy retrievalPolicy(RagProperties properties) {
    return new RetrievalPolicy(properties.retrieval().topK(),
        properties.retrieval().candidateTopK(), properties.retrieval().similarityThreshold(),
        properties.retrieval().semanticWeight(), properties.retrieval().bm25Weight());
  }

  /**
   * 暴露给评估应用服务的绝对门槛策略。
   */
  @Bean
  EvaluationPolicy evaluationPolicy(RagProperties properties) {
    RagProperties.Evaluation evaluation = properties.evaluation();
    return new EvaluationPolicy(evaluation.candidateHitRate(), evaluation.candidateMrr(),
        evaluation.contextRecall(), evaluation.contextPrecision(), evaluation.faithfulness(),
        evaluation.answerRelevancy(), evaluation.evidenceSupportAccuracy(),
        evaluation.noAnswerAccuracy());
  }

  /**
   * 暴露给会话应用服务的上下文预算策略。
   */
  @Bean
  ContextAssemblyPolicy contextAssemblyPolicy(RagProperties properties, TokenEstimator tokens) {
    RagProperties.Context context = properties.context();
    return new ContextAssemblyPolicy(context.maxInputTokens(), context.reservedOutputTokens(),
        context.recentTurns(), context.summaryTriggerTokens(), context.maxSummaryTokens(),
        context.minEvidenceTokens(), tokens);
  }

  /**
   * 暴露给文档应用服务的入库策略。
   */
  @Bean
  IngestionPolicy ingestionPolicy(RagProperties properties) {
    return new IngestionPolicy(properties.ingestion().chunkSize(),
        properties.ingestion().chunkOverlap(), properties.ingestion().embeddingBatchSize());
  }

  /**
   * 暴露无状态的自定义文本切片规则。
   */
  @Bean
  TextChunkingPolicy textChunkingPolicy() {
    return new TextChunkingPolicy();
  }

  /**
   * 启动时创建并验证上传根目录可读写。
   */
  @Bean
  ApplicationRunner storageRootValidator(RagProperties properties) {
    return args -> {
      Path root = Path.of(properties.storageRoot()).toAbsolutePath().normalize();
      Files.createDirectories(root);
      if (!Files.isReadable(root) || !Files.isWritable(root)) {
        throw new IllegalStateException("上传根目录不可读写: " + root);
      }
    };
  }
}
