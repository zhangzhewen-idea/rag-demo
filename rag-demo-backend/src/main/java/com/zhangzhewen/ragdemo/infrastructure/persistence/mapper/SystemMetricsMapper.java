package com.zhangzhewen.ragdemo.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Select;

/**
 * 使用 MyBatis 执行看板核心计数聚合。
 */
public interface SystemMetricsMapper {

  /**
   * 知识库总数。
   */
  @Select("SELECT COUNT(*) FROM kb_knowledge_base WHERE deleted=0")
  long knowledgeBaseCount();

  /**
   * 文档总数。
   */
  @Select("SELECT COUNT(*) FROM kb_document WHERE deleted=0")
  long documentCount();

  /**
   * 用户总数。
   */
  @Select("SELECT COUNT(*) FROM sys_user WHERE deleted=0")
  long userCount();

  /**
   * 用户问题总数。
   */
  @Select("SELECT COUNT(*) FROM ai_message WHERE role='USER'")
  long questionCount();
}
