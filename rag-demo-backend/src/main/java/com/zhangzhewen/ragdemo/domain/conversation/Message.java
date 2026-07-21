package com.zhangzhewen.ragdemo.domain.conversation;

import java.time.LocalDateTime;

/**
 * 会话消息。
 */
public record Message(Long id, Long conversationId, String role, String content, String status,
                      Integer promptTokens, Integer completionTokens, Long elapsedMs,
                      LocalDateTime createdAt) {

}
