package com.zhangzhewen.ragdemo.application.knowledge;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhangzhewen.ragdemo.domain.gateway.DocumentGateway;
import com.zhangzhewen.ragdemo.domain.gateway.DocumentParserGateway;
import com.zhangzhewen.ragdemo.domain.gateway.VectorGateway;
import com.zhangzhewen.ragdemo.domain.knowledge.DocumentStatus;
import com.zhangzhewen.ragdemo.domain.knowledge.IngestionPolicy;
import com.zhangzhewen.ragdemo.domain.knowledge.KnowledgeDocument;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 异步文档入库完成时的状态 CAS 与向量补偿测试。
 */
class DocumentIngestionWorkerTest {

  @Test
  void removesVectorsWhenReadyTransitionLosesRace() {
    Fixture fixture = new Fixture();
    when(fixture.documents.markReady(7L, 2)).thenReturn(false);

    fixture.worker.process(7L);

    verify(fixture.vectors).add(anyList(), anyList());
    verify(fixture.vectors).deleteByDocumentId(7L);
  }

  @Test
  void keepsVectorsWhenReadyTransitionSucceeds() {
    Fixture fixture = new Fixture();
    when(fixture.documents.markReady(7L, 2)).thenReturn(true);

    fixture.worker.process(7L);

    verify(fixture.vectors, never()).deleteByDocumentId(7L);
  }

  private static final class Fixture {

    private final DocumentGateway documents = mock(DocumentGateway.class);
    private final DocumentParserGateway parser = mock(DocumentParserGateway.class);
    private final VectorGateway vectors = mock(VectorGateway.class);
    private final DocumentIngestionWorker worker = new DocumentIngestionWorker(documents, parser,
        vectors, new IngestionPolicy(800, 100, 20));

    private Fixture() {
      KnowledgeDocument document = new KnowledgeDocument(7L, 1L, "document.txt", "stored.txt",
          "/tmp/document.txt", "txt", "text/plain", 10L, "hash", DocumentStatus.PENDING, 0, 0,
          null, null);
      when(documents.findDocumentById(7L)).thenReturn(Optional.of(document));
      when(documents.transit(7L, DocumentStatus.PENDING, DocumentStatus.PROCESSING))
          .thenReturn(true);
      when(parser.parse("/tmp/document.txt", "txt", 800, 100)).thenReturn(List.of(
          new DocumentParserGateway.ParsedChunk("第一段", Map.of()),
          new DocumentParserGateway.ParsedChunk("第二段", Map.of())));
    }
  }
}
