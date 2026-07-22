package com.zhangzhewen.ragdemo.application.knowledge;

import com.zhangzhewen.ragdemo.application.BusinessException;
import com.zhangzhewen.ragdemo.domain.gateway.DocumentGateway;
import com.zhangzhewen.ragdemo.domain.gateway.DocumentParserGateway;
import com.zhangzhewen.ragdemo.domain.gateway.FileStorageGateway;
import com.zhangzhewen.ragdemo.domain.gateway.KnowledgeGateway;
import com.zhangzhewen.ragdemo.domain.gateway.VectorGateway;
import com.zhangzhewen.ragdemo.domain.knowledge.DocumentStatus;
import com.zhangzhewen.ragdemo.domain.knowledge.KnowledgeDocument;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.security.MessageDigest;
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

  /**
   * 注入依赖。
   */
  public DocumentService(DocumentGateway documents, KnowledgeGateway knowledge,
      FileStorageGateway files, DocumentParserGateway parser, VectorGateway vectors,
      DocumentIngestionWorker worker) {
    this.documents = documents;
    this.knowledge = knowledge;
    this.files = files;
    this.parser = parser;
    this.vectors = vectors;
    this.worker = worker;
  }

  /**
   * 校验扩展名、大小、实际 MIME 后落盘并立即返回任务 ID。
   */
  public Long upload(Long knowledgeBaseId, String originalName, String browserMime, byte[] bytes,
      Long userId) {
    knowledge.findKnowledgeById(knowledgeBaseId).orElseThrow(
        () -> new BusinessException("KB_NOT_FOUND", "知识库不存在", HttpStatus.NOT_FOUND));
    if (bytes.length == 0 || bytes.length > MAX_SIZE) {
      throw new BusinessException("FILE_SIZE_INVALID", "文件必须大于 0 且不超过 20 MB",
          HttpStatus.BAD_REQUEST);
    }
    String extension = extension(originalName);
    if (!EXTENSIONS.contains(extension)) {
      throw new BusinessException("FILE_TYPE_UNSUPPORTED", "仅支持 txt、md、pdf、doc、docx",
          HttpStatus.BAD_REQUEST);
    }
    Path path = files.save(knowledgeBaseId, extension, new ByteArrayInputStream(bytes));
    String detected = parser.detect(path.toString());
    if (!mimeAllowed(extension, detected)) {
      files.delete(path.toString());
      throw new BusinessException("FILE_CONTENT_MISMATCH", "文件内容与扩展名不匹配",
          HttpStatus.BAD_REQUEST);
    }
    KnowledgeDocument draft = new KnowledgeDocument(null, knowledgeBaseId, originalName,
        path.getFileName().toString(), path.toString(), extension, detected, bytes.length,
        sha256(bytes), DocumentStatus.PENDING, 0, 0, null, null);
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
