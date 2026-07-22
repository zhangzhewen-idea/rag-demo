package com.zhangzhewen.ragdemo.domain.conversation;

/**
 * 会话滚动摘要及已覆盖的消息游标。
 */
public record ConversationSummary(Long conversationId, String content, long throughMessageId,
                                  long version) {

  /**
   * 尚未生成摘要时的初始状态。
   */
  public static ConversationSummary empty(Long conversationId) {
    return new ConversationSummary(conversationId, "", 0L, 0L);
  }
}
