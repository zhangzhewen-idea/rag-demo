package com.zhangzhewen.ragdemo.application.knowledge;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhangzhewen.ragdemo.domain.gateway.DocumentGateway;
import com.zhangzhewen.ragdemo.domain.gateway.VectorGateway;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 已逻辑删除文档的 Redis 残留补偿测试。
 */
class DeletedDocumentVectorCleanupWorkerTest {

  @Test
  void continuesCleaningWhenOneDocumentFails() {
    DocumentGateway documents = mock(DocumentGateway.class);
    VectorGateway vectors = mock(VectorGateway.class);
    when(documents.listDeletedDocumentIds()).thenReturn(List.of(1L, 2L, 3L));
    doThrow(new IllegalStateException("Redis unavailable")).when(vectors).deleteByDocumentId(2L);
    DeletedDocumentVectorCleanupWorker worker =
        new DeletedDocumentVectorCleanupWorker(documents, vectors);

    worker.cleanup();

    verify(vectors).deleteByDocumentId(1L);
    verify(vectors).deleteByDocumentId(2L);
    verify(vectors).deleteByDocumentId(3L);
  }

  @Test
  void doesNotBlockStartupWhenDeletedDocumentQueryFails() {
    DocumentGateway documents = mock(DocumentGateway.class);
    VectorGateway vectors = mock(VectorGateway.class);
    when(documents.listDeletedDocumentIds()).thenThrow(new IllegalStateException("MySQL unavailable"));
    DeletedDocumentVectorCleanupWorker worker =
        new DeletedDocumentVectorCleanupWorker(documents, vectors);

    assertThatCode(worker::cleanup).doesNotThrowAnyException();

    verifyNoInteractions(vectors);
  }
}
