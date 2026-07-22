package com.zhangzhewen.ragdemo.domain.gateway;

import com.zhangzhewen.ragdemo.domain.knowledge.DocumentStatus;
import com.zhangzhewen.ragdemo.domain.knowledge.KnowledgeDocument;
import java.util.List;
import java.util.Optional;

/**
 * 文档任务持久化边界。
 */
public interface DocumentGateway {

  /**
   * 创建待处理任务。
   */
  Long create(KnowledgeDocument document, Long creatorId);

  /**
   * 查询文档。
   */
  Optional<KnowledgeDocument> findDocumentById(Long id);

  /**
   * 查询知识库文档。
   */
  List<KnowledgeDocument> listByKnowledgeBase(Long knowledgeBaseId);

  /**
   * 查询已逻辑删除文档，用于补偿清理外部索引。
   */
  List<Long> listDeletedDocumentIds();

  /**
   * 条件抢占状态，保证任务幂等。
   */
  boolean transit(Long id, DocumentStatus expected, DocumentStatus target);

  /**
   * 标记入库完成。
   */
  boolean markReady(Long id, int chunkCount);

  /**
   * 标记失败。
   */
  void markFailed(Long id, String stage, String reason, boolean incrementRetry);

  /**
   * 将上次进程中断遗留的任务标记为失败。
   */
  int failInterruptedTasks();

  /**
   * 完成逻辑删除。
   */
  void logicalDelete(Long id);

  /**
   * 判断是否存在处理中任务。
   */
  boolean hasProcessing(Long knowledgeBaseId);
}
