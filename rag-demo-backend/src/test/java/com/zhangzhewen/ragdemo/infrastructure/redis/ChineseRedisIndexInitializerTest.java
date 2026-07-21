package com.zhangzhewen.ragdemo.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.vectorstore.redis.autoconfigure.RedisVectorStoreProperties.HnswProperties;
import redis.clients.jedis.CommandArguments;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.commands.ProtocolCommand;
import redis.clients.jedis.search.FTCreateParams;
import redis.clients.jedis.search.schemafields.SchemaField;

/**
 * Redis 中文索引启动初始化测试。
 */
class ChineseRedisIndexInitializerTest {

  /**
   * 索引不存在时使用中文分词配置创建完整 schema。
   */
  @Test
  @SuppressWarnings("unchecked")
  void createsChineseIndexWhenMissing() {
    RedisClient client = mock(RedisClient.class);
    when(client.ftList()).thenReturn(Set.of());
    when(client.ftCreate(eq("rag-demo-index"), any(FTCreateParams.class), any(Iterable.class)))
        .thenReturn("OK");
    var initializer = initializer(client);

    initializer.initialize();

    ArgumentCaptor<FTCreateParams> params = ArgumentCaptor.forClass(FTCreateParams.class);
    ArgumentCaptor<Iterable<SchemaField>> fields = ArgumentCaptor.forClass(Iterable.class);
    verify(client).ftCreate(eq("rag-demo-index"), params.capture(), fields.capture());
    assertThat(arguments(params.getValue())).containsSubsequence("ON", "JSON", "PREFIX", "1",
        "rag:chunk:", "LANGUAGE", "chinese");
    assertThat(fields.getValue()).hasSize(8);
  }

  /**
   * 索引已存在时不覆盖、不重建。
   */
  @Test
  @SuppressWarnings("unchecked")
  void keepsExistingIndex() {
    RedisClient client = mock(RedisClient.class);
    when(client.ftList()).thenReturn(Set.of("rag-demo-index"));
    when(client.ftInfo("rag-demo-index")).thenReturn(
        Map.of("index_definition", List.of("default_language", "chinese")));

    initializer(client).initialize();

    verify(client, never()).ftCreate(any(), any(FTCreateParams.class), any(Iterable.class));
  }

  /**
   * 已有索引未启用中文分词时拒绝静默使用错误索引。
   */
  @Test
  void rejectsExistingNonChineseIndex() {
    RedisClient client = mock(RedisClient.class);
    when(client.ftList()).thenReturn(Set.of("rag-demo-index"));
    when(client.ftInfo("rag-demo-index")).thenReturn(
        Map.of("index_definition", List.of("key_type", "JSON")));

    assertThatThrownBy(() -> initializer(client).initialize())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("LANGUAGE chinese");
  }

  private ChineseRedisIndexInitializer initializer(RedisClient client) {
    return new ChineseRedisIndexInitializer(client, "rag-demo-index", "rag:chunk:", 2560,
        new HnswProperties());
  }

  private List<String> arguments(FTCreateParams params) {
    ProtocolCommand command = () -> "FT.CREATE".getBytes(StandardCharsets.UTF_8);
    CommandArguments arguments = new CommandArguments(command).addParams(params);
    return java.util.stream.StreamSupport.stream(arguments.spliterator(), false)
        .map(value -> new String(value.getRaw(), StandardCharsets.UTF_8)).toList();
  }
}
