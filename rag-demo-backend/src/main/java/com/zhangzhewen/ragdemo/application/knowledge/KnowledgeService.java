package com.zhangzhewen.ragdemo.application.knowledge;

import com.zhangzhewen.ragdemo.application.BusinessException;
import com.zhangzhewen.ragdemo.application.dto.KnowledgeDtos;
import com.zhangzhewen.ragdemo.domain.gateway.CoverStorageGateway;
import com.zhangzhewen.ragdemo.domain.gateway.DocumentGateway;
import com.zhangzhewen.ragdemo.domain.gateway.FileStorageGateway;
import com.zhangzhewen.ragdemo.domain.gateway.KnowledgeGateway;
import com.zhangzhewen.ragdemo.domain.gateway.VectorGateway;
import com.zhangzhewen.ragdemo.domain.knowledge.DocumentStatus;
import com.zhangzhewen.ragdemo.domain.knowledge.KnowledgeBase;
import com.zhangzhewen.ragdemo.domain.knowledge.KnowledgeBaseStatus;
import com.zhangzhewen.ragdemo.domain.knowledge.KnowledgeDocument;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * 知识库查询与管理用例。
 */
@Service
public class KnowledgeService {

  private static final long MAX_COVER_SIZE = 5L * 1024 * 1024;
  private static final Map<String, String> COVER_TYPES = Map.of(
      "png", "image/png", "jpg", "image/jpeg", "jpeg", "image/jpeg", "webp", "image/webp");

  private final KnowledgeGateway knowledge;
  private final DocumentGateway documents;
  private final VectorGateway vectors;
  private final FileStorageGateway files;
  private final CoverStorageGateway covers;

  /**
   * 注入依赖。
   */
  public KnowledgeService(KnowledgeGateway knowledge, DocumentGateway documents,
      VectorGateway vectors, FileStorageGateway files, CoverStorageGateway covers) {
    this.knowledge = knowledge;
    this.documents = documents;
    this.vectors = vectors;
    this.files = files;
    this.covers = covers;
  }

  /**
   * 查询普通用户可用知识库。
   */
  public List<KnowledgeDtos.View> listEnabled() {
    return knowledge.listEnabled().stream().map(this::view).toList();
  }

  /**
   * 管理员查询全部。
   */
  @PreAuthorize("hasRole('ADMIN')")
  public List<KnowledgeDtos.View> listAll() {
    return knowledge.listAll().stream().map(this::view).toList();
  }

  /**
   * 创建知识库。
   */
  @PreAuthorize("hasRole('ADMIN')")
  public Long create(KnowledgeDtos.SaveRequest r, Long userId) {
    return knowledge.create(r.name(), r.description(), r.coverUrl(), userId);
  }

  /**
   * 修改知识库。
   */
  @PreAuthorize("hasRole('ADMIN')")
  public void update(Long id, KnowledgeDtos.SaveRequest r) {
    require(id);
    knowledge.update(id, r.name(), r.description(), r.coverUrl(),
        r.status() == null ? KnowledgeBaseStatus.ENABLED : KnowledgeBaseStatus.valueOf(r.status()));
  }

  /**
   * 校验并保存知识库封面。
   */
  @PreAuthorize("hasRole('ADMIN')")
  public String uploadCover(String originalName, String contentType, byte[] bytes) {
    if (bytes.length == 0 || bytes.length > MAX_COVER_SIZE) {
      throw new BusinessException("COVER_SIZE_INVALID", "封面必须大于 0 且不超过 5 MB",
          HttpStatus.BAD_REQUEST);
    }
    String extension = extension(originalName);
    if (!COVER_TYPES.containsKey(extension) || !COVER_TYPES.get(extension)
        .equalsIgnoreCase(contentType)) {
      throw new BusinessException("COVER_TYPE_UNSUPPORTED", "仅支持 PNG、JPEG、WebP 图片",
          HttpStatus.BAD_REQUEST);
    }
    if (!signatureMatches(extension, bytes)) {
      throw new BusinessException("COVER_CONTENT_MISMATCH", "图片内容与扩展名不匹配",
          HttpStatus.BAD_REQUEST);
    }
    return covers.save(extension, new ByteArrayInputStream(bytes));
  }

  /**
   * 读取公开封面。
   */
  public CoverStorageGateway.StoredCover cover(String fileName) {
    return covers.load(fileName);
  }

  /**
   * 删除前阻止处理中任务，并依次级联清理向量、文件和文档逻辑记录。
   */
  @PreAuthorize("hasRole('ADMIN')")
  public void delete(Long id) {
    require(id);
    if (documents.hasProcessing(id)) {
      throw new BusinessException("KB_HAS_PROCESSING_DOCUMENT", "知识库存在处理中任务",
          HttpStatus.CONFLICT);
    }
    for (KnowledgeDocument document : documents.listByKnowledgeBase(id)) {
      if (!documents.transit(document.id(), document.status(), DocumentStatus.DELETING)) {
        throw new BusinessException("DOCUMENT_STATE_CONFLICT", "文档状态已变化",
            HttpStatus.CONFLICT);
      }
      try {
        vectors.deleteByDocumentId(document.id());
        files.delete(document.storagePath());
        documents.logicalDelete(document.id());
      } catch (Exception e) {
        documents.markFailed(document.id(), "DELETE_KNOWLEDGE_BASE", e.getMessage(), false);
        throw new BusinessException("KB_DELETE_FAILED", "知识库文档清理失败，可稍后重试",
            HttpStatus.INTERNAL_SERVER_ERROR);
      }
    }
    knowledge.deleteKnowledgeBase(id);
  }

  /**
   * 获取可用于会话的知识库。
   */
  public KnowledgeBase requireSearchable(Long id) {
    KnowledgeBase kb = require(id);
    if (!kb.searchable()) {
      throw new BusinessException("KB_DISABLED", "知识库已停用", HttpStatus.CONFLICT);
    }
    return kb;
  }

  private KnowledgeBase require(Long id) {
    return knowledge.findKnowledgeById(id).orElseThrow(
        () -> new BusinessException("KB_NOT_FOUND", "知识库不存在", HttpStatus.NOT_FOUND));
  }

  private KnowledgeDtos.View view(KnowledgeBase k) {
    return new KnowledgeDtos.View(k.id(), k.name(), k.description(), k.coverUrl(),
        k.status().name());
  }

  private String extension(String name) {
    int dot = name == null ? -1 : name.lastIndexOf('.');
    return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
  }

  private boolean signatureMatches(String extension, byte[] bytes) {
    if (extension.equals("png")) {
      return bytes.length >= 8 && (bytes[0] & 255) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N'
          && bytes[3] == 'G';
    }
    if (extension.equals("jpg") || extension.equals("jpeg")) {
      return bytes.length >= 3 && (bytes[0] & 255) == 0xff && (bytes[1] & 255) == 0xd8
          && (bytes[2] & 255) == 0xff;
    }
    return bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F'
        && bytes[3] == 'F'
        && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
  }
}
