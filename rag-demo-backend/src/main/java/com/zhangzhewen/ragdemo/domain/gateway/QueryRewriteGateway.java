package com.zhangzhewen.ragdemo.domain.gateway;

/**
 * 面向知识库检索的查询改写边界。
 */
public interface QueryRewriteGateway {

  /**
   * 将用户查询改写为适合检索的独立查询。
   */
  String rewrite(String query);
}
