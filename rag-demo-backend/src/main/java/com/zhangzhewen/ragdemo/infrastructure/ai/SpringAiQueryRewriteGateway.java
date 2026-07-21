package com.zhangzhewen.ragdemo.infrastructure.ai;

import com.zhangzhewen.ragdemo.domain.gateway.QueryRewriteGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.stereotype.Component;

/**
 * Spring AI 查询改写适配器。
 */
@Component
public class SpringAiQueryRewriteGateway implements QueryRewriteGateway {

  private static final Logger log = LoggerFactory.getLogger(SpringAiQueryRewriteGateway.class);
  private final QueryTransformer transformer;

  /**
   * 注入 Spring AI 查询转换器。
   */
  public SpringAiQueryRewriteGateway(QueryTransformer transformer) {
    this.transformer = transformer;
  }

  /**
   * 模型调用失败时回退原查询，避免查询改写扩大问答故障面。
   */
  @Override
  public String rewrite(String query) {
    try {
      return transformer.transform(new Query(query)).text();
    } catch (RuntimeException exception) {
      log.warn("查询改写失败，回退到改写前的查询", exception);
      return query;
    }
  }
}
