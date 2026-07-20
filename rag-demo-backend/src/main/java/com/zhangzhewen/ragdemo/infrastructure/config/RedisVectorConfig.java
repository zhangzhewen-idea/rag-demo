package com.zhangzhewen.ragdemo.infrastructure.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.ai.vectorstore.redis.autoconfigure.RedisVectorStoreProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.RedisClient;

/** 显式声明 Redis 向量索引字段，确保 TAG 精确过滤和引用元数据可检索。 */
@Configuration
public class RedisVectorConfig {
    /** 创建带 6 个业务元数据字段的 HNSW/COSINE 向量存储。 */
    @Bean @Primary
    RedisVectorStore redisVectorStore(EmbeddingModel embeddingModel, JedisConnectionFactory factory,
                                      RedisVectorStoreProperties properties) {
        DefaultJedisClientConfig.Builder clientConfig = DefaultJedisClientConfig.builder()
                .ssl(factory.isUseSsl()).clientName(factory.getClientName()).timeoutMillis(factory.getTimeout());
        if (factory.getPassword() != null && !factory.getPassword().isBlank()) clientConfig.password(factory.getPassword());
        RedisClient client = RedisClient.builder().hostAndPort(factory.getHostName(), factory.getPort())
                .clientConfig(clientConfig.build()).build();
        return RedisVectorStore.builder(client, embeddingModel)
                .indexName(properties.getIndexName()).prefix(properties.getPrefix())
                .vectorAlgorithm(RedisVectorStore.Algorithm.HNSW)
                .distanceMetric(RedisVectorStore.DistanceMetric.COSINE)
                .metadataFields(
                        RedisVectorStore.MetadataField.tag("knowledgeBaseId"),
                        RedisVectorStore.MetadataField.tag("documentId"),
                        RedisVectorStore.MetadataField.numeric("chunkIndex"),
                        RedisVectorStore.MetadataField.numeric("pageNumber"),
                        RedisVectorStore.MetadataField.text("sectionTitle"),
                        RedisVectorStore.MetadataField.text("sourceName"))
                .initializeSchema(properties.isInitializeSchema()).build();
    }
}
