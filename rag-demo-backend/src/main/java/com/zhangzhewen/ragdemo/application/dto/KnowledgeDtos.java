package com.zhangzhewen.ragdemo.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 知识库接口 DTO 集合。
 */
public final class KnowledgeDtos {

  private KnowledgeDtos() {
  }

  /**
   * 新增或修改知识库请求。
   */
  public record SaveRequest(
      @NotBlank(message = "请输入知识库名称") @Size(max = 128, message = "知识库名称不能超过 128 个字符") String name,
      @Size(max = 1000, message = "知识库描述不能超过 1000 个字符") String description,
      @Size(max = 512, message = "封面 URL 不能超过 512 个字符") String coverUrl,
      String status) {

  }

  /**
   * 知识库视图。
   */
  public record View(Long id, String name, String description, String coverUrl, String status) {

  }

  /**
   * 创建会话请求。
   */
  public record ConversationRequest(@NotNull(message = "请选择知识库") Long knowledgeBaseId,
                                    @Size(max = 128, message = "会话标题不能超过 128 个字符") String title) {

  }

  /**
   * 会话重命名请求。
   */
  public record RenameRequest(
      @NotBlank(message = "请输入会话标题") @Size(max = 128, message = "会话标题不能超过 128 个字符") String title) {

  }

  /**
   * 问答请求。
   */
  public record ChatRequest(@NotBlank(message = "请输入问题") String content) {

  }

  /**
   * 文档切片配置请求。
   */
  public record ChunkingConfigRequest(@NotBlank(message = "请选择切片模式") String strategy,
                                      @Size(max = 20, message = "分隔符不能超过 20 个字符") String separator,
                                      @Min(value = 100, message = "最大长度不能小于 100")
                                      @Max(value = 4000, message = "最大长度不能超过 4000") int maxChunkLength,
                                      @Min(value = 0, message = "重叠长度不能小于 0")
                                      @Max(value = 500, message = "重叠长度不能超过 500") int overlapLength,
                                      boolean normalizeWhitespace) {

  }

  /**
   * 切片预览统计。
   */
  public record ChunkStatistics(int minCharacters, int maxCharacters, double averageCharacters,
                                int shortChunkCount) {

  }

  /**
   * 单个预览切片。
   */
  public record ChunkPreviewItem(int index, String content, int characterCount,
                                 int overlapCharacters, Integer pageNumber,
                                 String sectionTitle) {

  }

  /**
   * 切片预览响应。
   */
  public record ChunkPreviewResponse(String configFingerprint, int totalChunks,
                                     int previewedChunks, boolean truncated,
                                     ChunkStatistics statistics,
                                     List<ChunkPreviewItem> chunks) {

  }
}
