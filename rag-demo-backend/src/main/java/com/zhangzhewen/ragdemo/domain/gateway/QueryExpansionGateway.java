package com.zhangzhewen.ragdemo.domain.gateway;

import com.zhangzhewen.ragdemo.domain.conversation.RetrievalQuery;
import java.util.List;

/**
 * 面向复杂知识库问题的多路检索计划边界。
 */
public interface QueryExpansionGateway {

  /**
   * 一次生成多组互补的语义查询和关键词查询。
   */
  List<RetrievalQuery> plan(String query);
}
