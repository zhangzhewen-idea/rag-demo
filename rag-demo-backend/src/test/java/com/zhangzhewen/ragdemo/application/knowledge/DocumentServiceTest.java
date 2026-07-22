package com.zhangzhewen.ragdemo.application.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.zhangzhewen.ragdemo.application.BusinessException;
import com.zhangzhewen.ragdemo.domain.gateway.DocumentGateway;
import com.zhangzhewen.ragdemo.domain.gateway.DocumentParserGateway;
import com.zhangzhewen.ragdemo.domain.gateway.FileStorageGateway;
import com.zhangzhewen.ragdemo.domain.gateway.KnowledgeGateway;
import com.zhangzhewen.ragdemo.domain.gateway.VectorGateway;
import com.zhangzhewen.ragdemo.domain.knowledge.DocumentStatus;
import com.zhangzhewen.ragdemo.domain.knowledge.KnowledgeDocument;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;

/**
 * 文档删除状态保护和外部资源清理编排测试。
 */
class DocumentServiceTest {

  @Test
  void rejectsDeletingProcessingDocumentBeforeExternalCleanup() {
    Fixture fixture = new Fixture(DocumentStatus.PROCESSING);

    BusinessException error = catchThrowableOfType(BusinessException.class,
        () -> fixture.service.delete(7L));

    assertThat(error.code()).isEqualTo("DOCUMENT_DELETE_NOT_ALLOWED");
    assertThat(error.status()).isEqualTo(HttpStatus.CONFLICT);
    verify(fixture.documents, never()).transit(7L, DocumentStatus.PROCESSING,
        DocumentStatus.DELETING);
    verifyNoInteractions(fixture.vectors, fixture.files);
  }

  @Test
  void deletesReadyDocumentInCompensatableOrder() {
    Fixture fixture = new Fixture(DocumentStatus.READY);
    when(fixture.documents.transit(7L, DocumentStatus.READY, DocumentStatus.DELETING))
        .thenReturn(true);

    fixture.service.delete(7L);

    InOrder order = inOrder(fixture.documents, fixture.vectors, fixture.files);
    order.verify(fixture.documents).findDocumentById(7L);
    order.verify(fixture.documents).transit(7L, DocumentStatus.READY, DocumentStatus.DELETING);
    order.verify(fixture.vectors).deleteByDocumentId(7L);
    order.verify(fixture.files).delete("/tmp/document.txt");
    order.verify(fixture.documents).logicalDelete(7L);
  }

  private static final class Fixture {

    private final DocumentGateway documents = mock(DocumentGateway.class);
    private final KnowledgeGateway knowledge = mock(KnowledgeGateway.class);
    private final FileStorageGateway files = mock(FileStorageGateway.class);
    private final DocumentParserGateway parser = mock(DocumentParserGateway.class);
    private final VectorGateway vectors = mock(VectorGateway.class);
    private final DocumentIngestionWorker worker = mock(DocumentIngestionWorker.class);
    private final DocumentService service = new DocumentService(documents, knowledge, files, parser,
        vectors, worker);

    private Fixture(DocumentStatus status) {
      when(documents.findDocumentById(7L)).thenReturn(Optional.of(document(status)));
    }

    private KnowledgeDocument document(DocumentStatus status) {
      return new KnowledgeDocument(7L, 1L, "document.txt", "stored.txt",
          "/tmp/document.txt", "txt", "text/plain", 10L, "hash", status, 1, 0, null, null);
    }
  }
}
