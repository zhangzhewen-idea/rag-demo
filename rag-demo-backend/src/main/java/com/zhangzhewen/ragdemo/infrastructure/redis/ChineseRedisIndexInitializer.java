package com.zhangzhewen.ragdemo.infrastructure.redis;

import java.util.List;
import java.util.Map;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.ai.vectorstore.redis.autoconfigure.RedisVectorStoreProperties.HnswProperties;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.search.FTCreateParams;
import redis.clients.jedis.search.IndexDataType;
import redis.clients.jedis.search.schemafields.NumericField;
import redis.clients.jedis.search.schemafields.SchemaField;
import redis.clients.jedis.search.schemafields.TagField;
import redis.clients.jedis.search.schemafields.TextField;
import redis.clients.jedis.search.schemafields.VectorField;
import redis.clients.jedis.search.schemafields.VectorField.VectorAlgorithm;

/**
 * 启动时为 Redis 向量文档创建支持中文分词的 Search 索引。
 */
public class ChineseRedisIndexInitializer {

  static final String LANGUAGE = "chinese";
  private static final String OK = "OK";
  private final RedisClient client;
  private final String indexName;
  private final String prefix;
  private final int dimensions;
  private final HnswProperties hnsw;

  /**
   * 保存索引创建所需的 Redis 配置。
   */
  public ChineseRedisIndexInitializer(RedisClient client, String indexName, String prefix,
      int dimensions, HnswProperties hnsw) {
    this.client = client;
    this.indexName = indexName;
    this.prefix = prefix;
    this.dimensions = dimensions;
    this.hnsw = hnsw;
  }

  /**
   * 索引不存在时创建；已存在时不做破坏性变更。
   */
  public void initialize() {
    if (client.ftList().contains(indexName)) {
      requireChineseLanguage();
      return;
    }
    String response = client.ftCreate(indexName, createParams(prefix), schemaFields(dimensions, hnsw));
    if (!OK.equals(response)) {
      throw new IllegalStateException("Redis Search 中文索引创建失败: " + response);
    }
  }

  private void requireChineseLanguage() {
    Object definition = client.ftInfo(indexName).get("index_definition");
    if (!LANGUAGE.equals(definitionValue(definition, "default_language"))) {
      throw new IllegalStateException(
          "Redis Search 索引 " + indexName + " 未启用 LANGUAGE chinese，请重建索引");
    }
  }

  private String definitionValue(Object definition, String key) {
    if (definition instanceof Map<?, ?> values) {
      Object value = values.get(key);
      return value == null ? null : value.toString();
    }
    if (definition instanceof List<?> values) {
      for (int i = 0; i + 1 < values.size(); i += 2) {
        if (key.equals(values.get(i).toString())) {
          return values.get(i + 1).toString();
        }
      }
    }
    return null;
  }

  static FTCreateParams createParams(String prefix) {
    return FTCreateParams.createParams().on(IndexDataType.JSON).addPrefix(prefix)
        .language(LANGUAGE);
  }

  static List<SchemaField> schemaFields(int dimensions, HnswProperties hnsw) {
    Map<String, Object> vectorAttributes = Map.of(
        "TYPE", "FLOAT32",
        "DIM", dimensions,
        "DISTANCE_METRIC", "COSINE",
        "M", hnsw.getM(),
        "EF_CONSTRUCTION", hnsw.getEfConstruction(),
        "EF_RUNTIME", hnsw.getEfRuntime());
    return List.of(
        TextField.of("$." + RedisVectorStore.DEFAULT_CONTENT_FIELD_NAME)
            .as(RedisVectorStore.DEFAULT_CONTENT_FIELD_NAME).weight(1.0),
        VectorField.builder().fieldName("$." + RedisVectorStore.DEFAULT_EMBEDDING_FIELD_NAME)
            .as(RedisVectorStore.DEFAULT_EMBEDDING_FIELD_NAME).algorithm(VectorAlgorithm.HNSW)
            .attributes(vectorAttributes).build(),
        TagField.of("$.knowledgeBaseId").as("knowledgeBaseId"),
        TagField.of("$.documentId").as("documentId"),
        NumericField.of("$.chunkIndex").as("chunkIndex"),
        NumericField.of("$.pageNumber").as("pageNumber"),
        TextField.of("$.sectionTitle").as("sectionTitle"),
        TextField.of("$.sourceName").as("sourceName"));
  }
}
