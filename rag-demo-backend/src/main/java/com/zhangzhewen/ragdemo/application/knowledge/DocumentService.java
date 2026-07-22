package com.zhangzhewen.ragdemo.application.knowledge;

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
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * 文档上传、重试和删除用例。
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class DocumentService {

  private static final long MAX_SIZE = 20L * 1024 * 1024;
  private static final Set<String> EXTENSIONS = Set.of("txt", "md", "pdf", "doc", "docx");
  private final DocumentGateway documents;
  private final KnowledgeGateway knowledge;
  private final FileStorageGateway files;
  private final DocumentParserGateway parser;
  private final VectorGateway vectors;
  private final DocumentIngestionWorker worker;
  private final IngestionPolicy policy;

  /**
   * 注入依赖。
   */
  public DocumentService(DocumentGateway documents, KnowledgeGateway knowledge,
      FileStorageGateway files, DocumentParserGateway parser, VectorGateway vectors,
      DocumentIngestionWorker worker, IngestionPolicy policy) {
    this.documents = documents;
    this.knowledge = knowledge;
    this.files = files;
    this.parser = parser;
    this.vectors = vectors;
    this.worker = worker;
    this.policy = policy;
  }

  /**
   * 不落盘执行与正式入库相同的解析和切片逻辑。
   */
  public KnowledgeDtos.ChunkPreviewResponse preview(Long knowledgeBaseId, String originalName,
      byte[] bytes, KnowledgeDtos.ChunkingConfigRequest request) {
    requireKnowledge(knowledgeBaseId);
    ValidatedFile file = validateFile(originalName, bytes);
    ChunkingConfig config = config(request);
    List<DocumentParserGateway.ParsedChunk> chunks;
    try {
      chunks = parser.preview(originalName, file.extension(), bytes, config);
    } catch (IllegalArgumentException e) {
      throw new BusinessException("DOCUMENT_PARSE_FAILED", e.getMessage(), HttpStatus.BAD_REQUEST);
    }
    List<Integer> lengths = chunks.stream().map(chunk -> chunk.text().length()).toList();
    int min = lengths.stream().min(Comparator.naturalOrder()).orElse(0);
    int max = lengths.stream().max(Comparator.naturalOrder()).orElse(0);
    double average = lengths.stream().mapToInt(Integer::intValue).average().orElse(0);
    int shortCount = (int) lengths.stream().filter(length -> length < 50).count();
    List<KnowledgeDtos.ChunkPreviewItem> items = new ArrayList<>();
    for (int i = 0; i < Math.min(100, chunks.size()); i++) {
      var chunk = chunks.get(i);
      items.add(new KnowledgeDtos.ChunkPreviewItem(i, chunk.text(), chunk.text().length(),
          chunk.overlapCharacters(), integerMetadata(chunk.metadata().get("pageNumber")),
          stringMetadata(chunk.metadata().get("sectionTitle"))));
    }
    return new KnowledgeDtos.ChunkPreviewResponse(fingerprint(bytes, config), chunks.size(),
        items.size(), chunks.size() > items.size(),
        new KnowledgeDtos.ChunkStatistics(min, max, average, shortCount), List.copyOf(items));
  }

  /**
   * 校验扩展名、大小、实际 MIME 后落盘并立即返回任务 ID。
   */
  public Long upload(Long knowledgeBaseId, String originalName, byte[] bytes,
      Long userId, KnowledgeDtos.ChunkingConfigRequest request, String configFingerprint) {
    requireKnowledge(knowledgeBaseId);
    ValidatedFile file = validateFile(originalName, bytes);
    ChunkingConfig chunking = config(request);
    if (request != null && (configFingerprint == null
        || !MessageDigest.isEqual(fingerprint(bytes, chunking).getBytes(StandardCharsets.UTF_8),
        configFingerprint.getBytes(StandardCharsets.UTF_8)))) {
      throw new BusinessException("CHUNK_PREVIEW_EXPIRED", "文件或切片参数已变化，请重新生成预览",
          HttpStatus.CONFLICT);
    }
    Path path = files.save(knowledgeBaseId, file.extension(), new ByteArrayInputStream(bytes));
    KnowledgeDocument draft = new KnowledgeDocument(null, knowledgeBaseId, originalName,
        path.getFileName().toString(), path.toString(), file.extension(), file.detectedMime(),
        bytes.length,
        sha256(bytes), DocumentStatus.PENDING, 0, 0, null, null, chunking);
    Long id = documents.create(draft, userId);
    worker.process(id);
    return id;
  }

  /**
   * 查询知识库文档任务。
   */
  public List<KnowledgeDocument> list(Long knowledgeBaseId) {
    return documents.listByKnowledgeBase(knowledgeBaseId);
  }

  /**
   * 查询详情。
   */
  public KnowledgeDocument detail(Long id) {
    return require(id);
  }

  /**
   * 失败任务可幂等重试。
   */
  public void retry(Long id) {
    KnowledgeDocument d = require(id);
    if (d.status() != DocumentStatus.FAILED) {
      throw new BusinessException("DOCUMENT_NOT_RETRYABLE", "只有失败任务可以重试",
          HttpStatus.CONFLICT);
    }
    worker.process(id);
  }

  /**
   * 按状态、向量、物理文件、逻辑记录的顺序删除。
   */
  public void delete(Long id) {
    KnowledgeDocument d = require(id);
    if (!d.status().canTransitTo(DocumentStatus.DELETING)) {
      String message = d.status() == DocumentStatus.PROCESSING
          ? "文档正在处理中，请稍后删除" : "当前文档状态不能删除";
      throw new BusinessException("DOCUMENT_DELETE_NOT_ALLOWED", message, HttpStatus.CONFLICT);
    }
    if (!documents.transit(id, d.status(), DocumentStatus.DELETING)) {
      throw new BusinessException("DOCUMENT_STATE_CONFLICT", "文档状态已变化", HttpStatus.CONFLICT);
    }
    try {
      vectors.deleteByDocumentId(id);
      files.delete(d.storagePath());
      documents.logicalDelete(id);
    } catch (Exception e) {
      documents.markFailed(id, "DELETE", e.getMessage(), false);
      throw new BusinessException("DOCUMENT_DELETE_FAILED", "文档删除失败，可稍后重试",
          HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  private KnowledgeDocument require(Long id) {
    return documents.findDocumentById(id).orElseThrow(
        () -> new BusinessException("DOCUMENT_NOT_FOUND", "文档不存在", HttpStatus.NOT_FOUND));
  }

  private void requireKnowledge(Long id) {
    knowledge.findKnowledgeById(id).orElseThrow(
        () -> new BusinessException("KB_NOT_FOUND", "知识库不存在", HttpStatus.NOT_FOUND));
  }

  private ValidatedFile validateFile(String originalName, byte[] bytes) {
    if (bytes.length == 0 || bytes.length > MAX_SIZE) {
      throw new BusinessException("FILE_SIZE_INVALID", "文件必须大于 0 且不超过 20 MB",
          HttpStatus.BAD_REQUEST);
    }
    String extension = extension(originalName);
    if (!EXTENSIONS.contains(extension)) {
      throw new BusinessException("FILE_TYPE_UNSUPPORTED", "仅支持 txt、md、pdf、doc、docx",
          HttpStatus.BAD_REQUEST);
    }
    String detected = parser.detect(originalName, bytes);
    if (!mimeAllowed(extension, detected)) {
      throw new BusinessException("FILE_CONTENT_MISMATCH", "文件内容与扩展名不匹配",
          HttpStatus.BAD_REQUEST);
    }
    return new ValidatedFile(extension, detected);
  }

  private ChunkingConfig config(KnowledgeDtos.ChunkingConfigRequest request) {
    if (request == null) {
      return ChunkingConfig.auto(policy.chunkSize(), policy.chunkOverlap());
    }
    try {
      ChunkingConfig.Strategy strategy = ChunkingConfig.Strategy.valueOf(request.strategy());
      if (strategy == ChunkingConfig.Strategy.AUTO) {
        return ChunkingConfig.auto(policy.chunkSize(), policy.chunkOverlap());
      }
      return new ChunkingConfig(strategy,
          request.separator(), request.maxChunkLength(), request.overlapLength(),
          request.normalizeWhitespace());
    } catch (IllegalArgumentException e) {
      throw new BusinessException("CHUNK_CONFIG_INVALID", e.getMessage(), HttpStatus.BAD_REQUEST);
    }
  }

  private String fingerprint(byte[] bytes, ChunkingConfig config) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(bytes);
      String canonical = String.join("\u001f", config.strategy().name(),
          config.separator() == null ? "" : config.separator(),
          Integer.toString(config.maxChunkLength()), Integer.toString(config.overlapLength()),
          Boolean.toString(config.normalizeWhitespace()));
      return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private Integer integerMetadata(Object value) {
    if (value instanceof Number number) {
      return number.intValue();
    }
    try {
      return value == null ? null : Integer.valueOf(value.toString());
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private String stringMetadata(Object value) {
    return value == null ? null : value.toString();
  }

  private record ValidatedFile(String extension, String detectedMime) {

  }

  private String extension(String name) {
    int dot = name == null ? -1 : name.lastIndexOf('.');
    return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
  }

  private boolean mimeAllowed(String ext, String mime) {
    return switch (ext) {
      case "txt" -> mime.startsWith("text/plain");
      case "md" -> mime.startsWith("text/") || mime.equals("application/octet-stream");
      case "pdf" -> mime.equals("application/pdf");
      case "doc" -> mime.equals("application/msword") || mime.equals("application/x-tika-msoffice");
      case "docx" -> mime.contains("officedocument.wordprocessingml") || mime.equals(
          "application/x-tika-ooxml");
      default -> false;
    };
  }

  private String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
