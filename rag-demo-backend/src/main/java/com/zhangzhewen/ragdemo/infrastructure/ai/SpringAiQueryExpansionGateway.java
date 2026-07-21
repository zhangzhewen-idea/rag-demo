package com.zhangzhewen.ragdemo.infrastructure.ai;

import com.zhangzhewen.ragdemo.domain.gateway.QueryExpansionGateway;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.expansion.QueryExpander;
import org.springframework.stereotype.Component;

/**
 * Spring AI 多查询扩展适配器。
 */
@Component
public class SpringAiQueryExpansionGateway implements QueryExpansionGateway {

  private static final Logger log = LoggerFactory.getLogger(SpringAiQueryExpansionGateway.class);
  private final QueryExpander expander;

  /**
   * 注入 Spring AI 查询扩展器。
   */
  public SpringAiQueryExpansionGateway(QueryExpander expander) {
    this.expander = expander;
  }

  /**
   * 模型调用失败时回退原查询，避免查询扩展扩大问答故障面。
   */
  @Override
  public List<String> expand(String query) {
    try {
      List<String> expanded = expander.expand(new Query(query)).stream()
          .map(Query::text)
          .map(String::trim)
          .filter(value -> !value.isBlank())
          .distinct()
          .toList();
      return expanded.isEmpty() ? List.of(query) : expanded;
    } catch (RuntimeException exception) {
      log.warn("查询扩展失败，回退到扩展前的查询", exception);
      return List.of(query);
    }
  }
}
