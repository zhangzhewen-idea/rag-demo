package com.zhangzhewen.ragdemo.infrastructure.config;

import com.zhangzhewen.ragdemo.domain.gateway.EvaluationConfigurationGateway;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 将影响评估结果的模型、索引、切分和检索配置固化为 JSON 快照。
 */
@Component
public class RuntimeEvaluationConfigurationGateway implements EvaluationConfigurationGateway {

  private final RagProperties rag;
  private final ObjectMapper mapper;
  private final String chatModel;
  private final String embeddingModel;
  private final String vectorIndex;

  public RuntimeEvaluationConfigurationGateway(RagProperties rag, ObjectMapper mapper,
      @Value("${spring.ai.openai.chat.options.model}") String chatModel,
      @Value("${spring.ai.openai.embedding.options.model}") String embeddingModel,
      @Value("${spring.ai.vectorstore.redis.index-name}") String vectorIndex) {
    this.rag = rag;
    this.mapper = mapper;
    this.chatModel = chatModel;
    this.embeddingModel = embeddingModel;
    this.vectorIndex = vectorIndex;
  }

  @Override
  public String snapshot() {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("pipelineVersion", rag.evaluation().pipelineVersion());
    value.put("chatModel", chatModel);
    value.put("judgeModel", rag.evaluation().judgeModel());
    value.put("embeddingModel", embeddingModel);
    value.put("vectorIndex", vectorIndex);
    value.put("retrieval", rag.retrieval());
    value.put("reranking", Map.of("enabled", rag.reranking().enabled(),
        "model", rag.reranking().model()));
    value.put("ingestion", rag.ingestion());
    value.put("thresholds", rag.evaluation());
    return mapper.writeValueAsString(value);
  }
}
