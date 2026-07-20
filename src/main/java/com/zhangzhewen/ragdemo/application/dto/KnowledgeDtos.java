package com.zhangzhewen.ragdemo.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 知识库接口 DTO 集合。 */
public final class KnowledgeDtos {
    private KnowledgeDtos() { }
    /** 新增或修改知识库请求。 */ public record SaveRequest(@NotBlank String name, String description, String coverUrl, String status) { }
    /** 知识库视图。 */ public record View(Long id, String name, String description, String coverUrl, String status) { }
    /** 创建会话请求。 */ public record ConversationRequest(@NotNull Long knowledgeBaseId, String title) { }
    /** 会话重命名请求。 */ public record RenameRequest(@NotBlank String title) { }
    /** 问答请求。 */ public record ChatRequest(@NotBlank String content) { }
}
