package com.zhangzhewen.ragdemo.infrastructure.ai;

import com.zhangzhewen.ragdemo.domain.conversation.RetrievalQuery;
import com.zhangzhewen.ragdemo.domain.gateway.QueryExpansionGateway;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Spring AI 多路查询规划适配器。
 */
@Component
public class SpringAiQueryExpansionGateway implements QueryExpansionGateway {

  private static final Logger log = LoggerFactory.getLogger(SpringAiQueryExpansionGateway.class);
  private static final int MAX_PLANS = 4;
  private final QueryPlanner planner;

  /**
   * 注入单次调用大模型的结构化查询规划器。
   */
  public SpringAiQueryExpansionGateway(QueryPlanner planner) {
    this.planner = planner;
  }

  /**
   * 模型调用失败时让语义与关键词检索都回退原查询。
   */
  @Override
  public List<RetrievalQuery> plan(String query) {
    try {
      List<RetrievalQuery> planned = planner.plan(query);
      if (planned == null) {
        return fallback(query);
      }
      List<RetrievalQuery> normalized = planned.stream()
          .filter(plan -> plan != null)
          .distinct()
          .limit(MAX_PLANS)
          .toList();
      return normalized.isEmpty() ? fallback(query) : normalized;
    } catch (RuntimeException exception) {
      log.warn("查询规划失败，回退到原查询", exception);
      return fallback(query);
    }
  }

  private List<RetrievalQuery> fallback(String query) {
    return List.of(new RetrievalQuery(query, query));
  }

  /**
   * 单次生成结构化检索计划的基础设施协作者。
   */
  @FunctionalInterface
  public interface QueryPlanner {

    List<RetrievalQuery> plan(String query);
  }
}
