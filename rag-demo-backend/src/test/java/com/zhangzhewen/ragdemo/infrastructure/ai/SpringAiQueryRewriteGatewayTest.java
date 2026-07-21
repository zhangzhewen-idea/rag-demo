package com.zhangzhewen.ragdemo.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;

/**
 * Spring AI 查询改写适配器测试。
 */
class SpringAiQueryRewriteGatewayTest {

  /**
   * 将字符串查询交给 transformer 并返回改写文本。
   */
  @Test
  void returnsTransformedQueryText() {
    QueryTransformer transformer = mock(QueryTransformer.class);
    SpringAiQueryRewriteGateway gateway = new SpringAiQueryRewriteGateway(transformer);
    when(transformer.transform(any(Query.class))).thenReturn(new Query("Apple Inc. 股价表现"));

    String result = gateway.rewrite("苹果股价咋样了？");

    assertThat(result).isEqualTo("Apple Inc. 股价表现");
    ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
    verify(transformer).transform(query.capture());
    assertThat(query.getValue().text()).isEqualTo("苹果股价咋样了？");
  }

  /**
   * transformer 异常时回退原始输入查询。
   */
  @Test
  void fallsBackToInputQueryWhenTransformationFails() {
    QueryTransformer transformer = mock(QueryTransformer.class);
    SpringAiQueryRewriteGateway gateway = new SpringAiQueryRewriteGateway(transformer);
    when(transformer.transform(any(Query.class))).thenThrow(new IllegalStateException("模型不可用"));

    String result = gateway.rewrite("苹果股价咋样了？");

    assertThat(result).isEqualTo("苹果股价咋样了？");
  }
}
