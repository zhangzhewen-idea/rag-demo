package com.zhangzhewen.ragdemo.infrastructure.ai;

import com.zhangzhewen.ragdemo.domain.conversation.RetrievedChunk;
import com.zhangzhewen.ragdemo.domain.gateway.DocumentRerankGateway;
import com.zhangzhewen.ragdemo.infrastructure.config.RagProperties;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * 通过百炼 OpenAI 兼容接口调用 qwen3-rerank。
 */
public class DashScopeDocumentRerankGateway implements DocumentRerankGateway {

  private static final Logger LOG = LoggerFactory.getLogger(DashScopeDocumentRerankGateway.class);
  private static final String QA_INSTRUCT =
      "Given a web search query, retrieve relevant passages that answer the query.";
  private final RestClient client;
  private final RagProperties.Reranking properties;

  /**
   * 注入 HTTP 客户端和重排配置。
   */
  public DashScopeDocumentRerankGateway(RestClient client,
      RagProperties.Reranking properties) {
    this.client = client;
    this.properties = properties;
  }

  @Override
  public List<RetrievedChunk> rerank(String query, List<RetrievedChunk> candidates, int topK) {
    if (topK <= 0) {
      throw new IllegalArgumentException("topK 必须大于 0");
    }
    List<RetrievedChunk> fallback = candidates.stream().limit(topK).toList();
    if (!properties.enabled() || candidates.isEmpty()) {
      return fallback;
    }
    try {
      Map<String, Object> request = new LinkedHashMap<>();
      request.put("model", properties.model());
      request.put("query", query);
      request.put("documents", candidates.stream().map(RetrievedChunk::excerpt).toList());
      request.put("top_n", Math.min(topK, candidates.size()));
      request.put("instruct", QA_INSTRUCT);
      Map<String, Object> response = client.post().uri(properties.baseUrl())
          .contentType(MediaType.APPLICATION_JSON).body(request).retrieve()
          .body(new ParameterizedTypeReference<>() {
          });
      return mapResponse(response, candidates, topK);
    } catch (RuntimeException ex) {
      LOG.warn("文档重排失败，回退到 RRF 排序: {}", ex.getClass().getSimpleName());
      return fallback;
    }
  }

  private List<RetrievedChunk> mapResponse(Map<String, Object> response,
      List<RetrievedChunk> candidates, int topK) {
    if (response == null || !(response.get("results") instanceof List<?> results)) {
      throw new IllegalStateException("重排响应缺少 results");
    }
    int expectedSize = Math.min(topK, candidates.size());
    if (results.size() != expectedSize) {
      throw new IllegalStateException("重排响应数量不符合预期");
    }
    Set<Integer> indexes = new HashSet<>();
    List<RetrievedChunk> reranked = new ArrayList<>(expectedSize);
    for (Object value : results) {
      if (!(value instanceof Map<?, ?> result)) {
        throw new IllegalStateException("重排结果格式错误");
      }
      int index = integer(result.get("index"));
      double score = decimal(result.get("relevance_score"));
      if (index < 0 || index >= candidates.size() || !indexes.add(index)) {
        throw new IllegalStateException("重排结果索引无效");
      }
      if (!Double.isFinite(score) || score < 0 || score > 1) {
        throw new IllegalStateException("重排分数无效");
      }
      reranked.add(candidates.get(index).withRerankScore(score));
    }
    return List.copyOf(reranked);
  }

  private int integer(Object value) {
    if (!(value instanceof Number number) || number.doubleValue() != number.intValue()) {
      throw new IllegalStateException("重排结果索引格式错误");
    }
    return number.intValue();
  }

  private double decimal(Object value) {
    if (!(value instanceof Number number)) {
      throw new IllegalStateException("重排分数格式错误");
    }
    return number.doubleValue();
  }
}
