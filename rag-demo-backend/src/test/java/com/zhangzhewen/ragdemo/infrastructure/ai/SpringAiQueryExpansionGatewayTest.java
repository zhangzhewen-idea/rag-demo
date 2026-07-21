package com.zhangzhewen.ragdemo.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.expansion.QueryExpander;

/**
 * Spring AI 多查询扩展适配器测试。
 */
class SpringAiQueryExpansionGatewayTest {

  /**
   * 返回去空白、去重后的多条查询。
   */
  @Test
  void returnsDistinctExpandedQueryTexts() {
    QueryExpander expander = mock(QueryExpander.class);
    SpringAiQueryExpansionGateway gateway = new SpringAiQueryExpansionGateway(expander);
    when(expander.expand(any(Query.class))).thenReturn(List.of(
        new Query("原始查询"), new Query(" 产品 A "), new Query("产品 A"), new Query("产品 B")));

    List<String> result = gateway.expand("原始查询");

    assertThat(result).containsExactly("原始查询", "产品 A", "产品 B");
    ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
    verify(expander).expand(query.capture());
    assertThat(query.getValue().text()).isEqualTo("原始查询");
  }

  /**
   * expander 异常时只返回原始输入查询。
   */
  @Test
  void fallsBackToInputQueryWhenExpansionFails() {
    QueryExpander expander = mock(QueryExpander.class);
    SpringAiQueryExpansionGateway gateway = new SpringAiQueryExpansionGateway(expander);
    when(expander.expand(any(Query.class))).thenThrow(new IllegalStateException("模型不可用"));

    List<String> result = gateway.expand("原始查询");

    assertThat(result).containsExactly("原始查询");
  }
}
