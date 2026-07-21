package com.zhangzhewen.ragdemo.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

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
}
