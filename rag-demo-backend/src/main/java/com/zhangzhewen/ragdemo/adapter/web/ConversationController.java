package com.zhangzhewen.ragdemo.adapter.web;

import com.zhangzhewen.ragdemo.application.conversation.ConversationService;
import com.zhangzhewen.ragdemo.application.dto.ApiResponse;
import com.zhangzhewen.ragdemo.application.dto.KnowledgeDtos;
import com.zhangzhewen.ragdemo.domain.conversation.Conversation;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 本人会话 CRUD 与 SSE 问答接口。
 */
@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

  private static final Logger log = LoggerFactory.getLogger(ConversationController.class);
  private final ConversationService service;

  /**
   * 注入服务。
   */
  public ConversationController(ConversationService service) {
    this.service = service;
  }

  /**
   * 创建会话。
   */
  @PostMapping
  public ApiResponse<Map<String, Long>> create(
      @Valid @RequestBody KnowledgeDtos.ConversationRequest r, Authentication a) {
    return WebSupport.ok(
        Map.of("id", service.create(WebSupport.userId(a), r.knowledgeBaseId(), r.title())));
  }

  /**
   * 查询本人会话。
   */
  @GetMapping
  public ApiResponse<List<Conversation>> list(Authentication a) {
    return WebSupport.ok(service.list(WebSupport.userId(a)));
  }

  /**
   * 查询详情和消息。
   */
  @GetMapping("/{id}")
  public ApiResponse<ConversationService.Detail> detail(@PathVariable Long id, Authentication a) {
    return WebSupport.ok(service.detail(id, WebSupport.userId(a)));
  }

  /**
   * 重命名。
   */
  @PutMapping("/{id}")
  public ApiResponse<Void> rename(@PathVariable Long id,
      @Valid @RequestBody KnowledgeDtos.RenameRequest r, Authentication a) {
    service.rename(id, WebSupport.userId(a), r.title());
    return WebSupport.ok(null);
  }

  /**
   * 逻辑删除。
   */
  @DeleteMapping("/{id}")
  public ApiResponse<Void> delete(@PathVariable Long id, Authentication a) {
    service.delete(id, WebSupport.userId(a));
    return WebSupport.ok(null);
  }

  /**
   * 流式返回 delta、references、done 或 error 事件。
   */
  @PostMapping(value = "/{id}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter stream(@PathVariable Long id, @Valid @RequestBody KnowledgeDtos.ChatRequest r,
      Authentication a) {
    SseEmitter emitter = new SseEmitter(180_000L);
    String traceId = WebSupport.traceId();
    try {
      service.chat(id, WebSupport.userId(a), r.content(),
              delta -> send(emitter, "delta", Map.of("content", delta)))
          .whenComplete((result, error) -> {
            if (error != null) {
              Throwable cause = rootCause(error);
              log.error("回答生成失败: traceId={}, conversationId={}, reason={}", traceId, id,
                  cause.getMessage(), cause);
              send(emitter, "error",
                  Map.of("code", "CHAT_FAILED", "message", "回答生成失败，请稍后重试", "traceId",
                      traceId));
              emitter.complete();
              return;
            }
            send(emitter, "references", result.references());
            send(emitter, "done",
                Map.of("messageId", result.messageId(), "elapsedMs", result.elapsedMs()));
            emitter.complete();
          });
    } catch (Exception e) {
      log.warn("问答请求被拒绝: traceId={}, conversationId={}, reason={}", traceId, id,
          e.getMessage());
      send(emitter, "error",
          Map.of("code", "CHAT_REJECTED", "message", e.getMessage(), "traceId", traceId));
      emitter.complete();
    }
    return emitter;
  }

  private void send(SseEmitter emitter, String name, Object data) {
    try {
      emitter.send(SseEmitter.event().name(name).data(data));
    } catch (IOException ignored) {
      emitter.complete();
    }
  }

  private Throwable rootCause(Throwable error) {
    Throwable cause = error;
    while (cause.getCause() != null) {
      cause = cause.getCause();
    }
    return cause;
  }
}
