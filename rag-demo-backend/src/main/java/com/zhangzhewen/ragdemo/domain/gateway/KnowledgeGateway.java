package com.zhangzhewen.ragdemo.domain.gateway;

import com.zhangzhewen.ragdemo.domain.knowledge.KnowledgeBase;
import com.zhangzhewen.ragdemo.domain.knowledge.KnowledgeBaseStatus;
import java.util.List;
import java.util.Optional;

/**
 * 知识库持久化能力边界。
 */
public interface KnowledgeGateway {

  /**
   * 查询可用知识库。
   */
  List<KnowledgeBase> listEnabled();

  /**
   * 查询全部知识库。
   */
  List<KnowledgeBase> listAll();

  /**
   * 按 ID 查询。
   */
  Optional<KnowledgeBase> findKnowledgeById(Long id);

  /**
   * 创建知识库。
   */
  Long create(String name, String description, String coverUrl, Long creatorId);

  /**
   * 更新知识库。
   */
  void update(Long id, String name, String description, String coverUrl,
      KnowledgeBaseStatus status);

  /**
   * 逻辑删除知识库。
   */
  void deleteKnowledgeBase(Long id);
}
