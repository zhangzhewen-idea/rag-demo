package com.zhangzhewen.ragdemo.domain.gateway;

import java.util.List;

/**
 * 面向复杂知识库问题的多查询扩展边界。
 */
public interface QueryExpansionGateway {

  /**
   * 将独立查询扩展为多个互补的检索查询。
   */
  List<String> expand(String query);
}
