package com.zhangzhewen.ragdemo.domain.conversation;

/**
 * 绑定单一知识库的用户会话。
 */
public record Conversation(Long id, Long userId, Long knowledgeBaseId, String title,
                           String status) {

  /**
   * 校验会话归属。
   *
   * @param currentUserId 当前用户
   * @return 是否属于该用户
   */
  public boolean ownedBy(Long currentUserId) {
    return userId.equals(currentUserId);
  }
}
