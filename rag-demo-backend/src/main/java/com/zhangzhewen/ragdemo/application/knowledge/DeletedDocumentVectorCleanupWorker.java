package com.zhangzhewen.ragdemo.application.knowledge;

import com.zhangzhewen.ragdemo.domain.gateway.DocumentGateway;
import com.zhangzhewen.ragdemo.domain.gateway.VectorGateway;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 启动后幂等清理已逻辑删除文档在当前 Redis 索引中的残留切片。
 */
@Component
public class DeletedDocumentVectorCleanupWorker {

  private static final Logger log = LoggerFactory.getLogger(DeletedDocumentVectorCleanupWorker.class);
  private final DocumentGateway documents;
  private final VectorGateway vectors;

  public DeletedDocumentVectorCleanupWorker(DocumentGateway documents, VectorGateway vectors) {
    this.documents = documents;
    this.vectors = vectors;
  }

  /**
   * 单个文档清理失败不影响其他文档，也不阻止应用启动。
   */
  @EventListener(ApplicationReadyEvent.class)
  public void cleanup() {
    List<Long> deletedDocumentIds;
    try {
      deletedDocumentIds = documents.listDeletedDocumentIds();
    } catch (RuntimeException exception) {
      log.warn("查询已删除文档失败，跳过本次向量补偿清理: reason={}", exception.getMessage());
      return;
    }
    int cleaned = 0;
    int failed = 0;
    for (Long documentId : deletedDocumentIds) {
      try {
        vectors.deleteByDocumentId(documentId);
        cleaned++;
      } catch (RuntimeException exception) {
        failed++;
        log.warn("已删除文档向量补偿清理失败: documentId={}, reason={}", documentId,
            exception.getMessage());
      }
    }
    if (cleaned > 0 || failed > 0) {
      log.info("已删除文档向量补偿完成: cleaned={}, failed={}", cleaned, failed);
    }
  }
}
