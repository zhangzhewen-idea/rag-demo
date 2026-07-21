package com.zhangzhewen.ragdemo.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhangzhewen.ragdemo.domain.conversation.RetrievalQuery;
import com.zhangzhewen.ragdemo.infrastructure.ai.SpringAiQueryExpansionGateway.QueryPlanner;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Spring AI 多路查询规划适配器测试。
 */
class SpringAiQueryExpansionGatewayTest {

  /**
   * 返回去重后的语义查询与关键词查询计划。
   */
  @Test
  void returnsDistinctRetrievalPlans() {
    QueryPlanner planner = mock(QueryPlanner.class);
    SpringAiQueryExpansionGateway gateway = new SpringAiQueryExpansionGateway(planner);
    RetrievalQuery first = new RetrievalQuery("产品 A 的功能", "产品 A 功能");
    RetrievalQuery second = new RetrievalQuery("产品 B 的功能", "产品 B 功能");
    when(planner.plan("原始查询")).thenReturn(List.of(first, first, second));

    List<RetrievalQuery> result = gateway.plan("原始查询");

    assertThat(result).containsExactly(first, second);
    verify(planner).plan("原始查询");
  }

  /**
   * planner 异常时语义查询和关键词查询都回退原始输入。
   */
  @Test
  void fallsBackToInputQueryWhenPlanningFails() {
    QueryPlanner planner = mock(QueryPlanner.class);
    SpringAiQueryExpansionGateway gateway = new SpringAiQueryExpansionGateway(planner);
    when(planner.plan("原始查询")).thenThrow(new IllegalStateException("模型不可用"));

    List<RetrievalQuery> result = gateway.plan("原始查询");

    assertThat(result).containsExactly(new RetrievalQuery("原始查询", "原始查询"));
  }
}
