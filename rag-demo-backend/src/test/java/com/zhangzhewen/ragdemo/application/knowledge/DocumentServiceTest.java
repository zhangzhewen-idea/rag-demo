package com.zhangzhewen.ragdemo.application.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;

import com.zhangzhewen.ragdemo.application.BusinessException;
import com.zhangzhewen.ragdemo.application.dto.KnowledgeDtos;
import com.zhangzhewen.ragdemo.domain.gateway.DocumentGateway;
import com.zhangzhewen.ragdemo.domain.gateway.DocumentParserGateway;
import com.zhangzhewen.ragdemo.domain.gateway.FileStorageGateway;
import com.zhangzhewen.ragdemo.domain.gateway.KnowledgeGateway;
import com.zhangzhewen.ragdemo.domain.gateway.VectorGateway;
import com.zhangzhewen.ragdemo.domain.knowledge.DocumentStatus;
import com.zhangzhewen.ragdemo.domain.knowledge.ChunkingConfig;
import com.zhangzhewen.ragdemo.domain.knowledge.IngestionPolicy;
import com.zhangzhewen.ragdemo.domain.knowledge.KnowledgeDocument;
import com.zhangzhewen.ragdemo.domain.knowledge.KnowledgeBase;
import com.zhangzhewen.ragdemo.domain.knowledge.KnowledgeBaseStatus;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;

/**
 * 文档删除状态保护和外部资源清理编排测试。
 */
class DocumentServiceTest {

  @Test
  void previewsRealChunksWithoutPersistingFileOrDocument() {
    Fixture fixture = new Fixture(DocumentStatus.READY);
    byte[] bytes = "第一段\n\n第二段".getBytes();
    var request = customRequest();
    when(fixture.parser.detect("sample.txt", bytes)).thenReturn("text/plain");
    when(fixture.parser.preview(eq("sample.txt"), eq("txt"), eq(bytes), any())).thenReturn(
        List.of(new DocumentParserGateway.ParsedChunk("第一段", Map.of(), 0),
            new DocumentParserGateway.ParsedChunk("第二段", Map.of("pageNumber", 2), 2)));

    var result = fixture.service.preview(1L, "sample.txt", bytes, request);

    assertThat(result.totalChunks()).isEqualTo(2);
    assertThat(result.statistics().minCharacters()).isEqualTo(3);
    assertThat(result.chunks().get(1).pageNumber()).isEqualTo(2);
    assertThat(result.configFingerprint()).hasSize(64);
    verifyNoInteractions(fixture.files);
    verify(fixture.documents, never()).create(any(), anyLong());
  }

  @Test
  void rejectsUploadWhenPreviewFingerprintDoesNotMatch() {
    Fixture fixture = new Fixture(DocumentStatus.READY);
    byte[] bytes = "文档内容".getBytes();
    when(fixture.parser.detect("sample.txt", bytes)).thenReturn("text/plain");

    BusinessException error = catchThrowableOfType(BusinessException.class,
        () -> fixture.service.upload(1L, "sample.txt", bytes, 9L,
            customRequest(), "expired"));

    assertThat(error.code()).isEqualTo("CHUNK_PREVIEW_EXPIRED");
    verifyNoInteractions(fixture.files);
  }

  @Test
  void persistsPreviewedConfigAndStartsWorker() {
    Fixture fixture = new Fixture(DocumentStatus.READY);
    byte[] bytes = "文档内容".getBytes();
    var request = customRequest();
    when(fixture.parser.detect("sample.txt", bytes)).thenReturn("text/plain");
    when(fixture.parser.preview(eq("sample.txt"), eq("txt"), eq(bytes), any())).thenReturn(
        List.of(new DocumentParserGateway.ParsedChunk("文档内容", Map.of(), 0)));
    String fingerprint = fixture.service.preview(1L, "sample.txt", bytes, request)
        .configFingerprint();
    when(fixture.files.save(eq(1L), eq("txt"), any())).thenReturn(Path.of("/tmp/stored.txt"));
    when(fixture.documents.create(any(), eq(9L))).thenReturn(12L);

    Long id = fixture.service.upload(1L, "sample.txt", bytes, 9L, request,
        fingerprint);

    assertThat(id).isEqualTo(12L);
    verify(fixture.documents).create(
        org.mockito.ArgumentMatchers.argThat(document -> document.chunkingConfig().strategy()
            == ChunkingConfig.Strategy.CUSTOM
            && "\n\n".equals(document.chunkingConfig().separator())), eq(9L));
    verify(fixture.worker).process(12L);
  }

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
        vectors, worker, new IngestionPolicy(800, 100, 20));

    private Fixture(DocumentStatus status) {
      when(documents.findDocumentById(7L)).thenReturn(Optional.of(document(status)));
      when(knowledge.findKnowledgeById(1L)).thenReturn(Optional.of(
          new KnowledgeBase(1L, "知识库", null, null, KnowledgeBaseStatus.ENABLED)));
    }

    private KnowledgeDocument document(DocumentStatus status) {
      return new KnowledgeDocument(7L, 1L, "document.txt", "stored.txt",
          "/tmp/document.txt", "txt", "text/plain", 10L, "hash", status, 1, 0, null, null,
          ChunkingConfig.auto(800, 100));
    }
  }

  private static KnowledgeDtos.ChunkingConfigRequest customRequest() {
    return new KnowledgeDtos.ChunkingConfigRequest("CUSTOM", "\n\n", 800, 100, true);
  }
}
