package com.zhangzhewen.ragdemo.domain.gateway;

import com.zhangzhewen.ragdemo.domain.conversation.Conversation;
import com.zhangzhewen.ragdemo.domain.conversation.Message;
import com.zhangzhewen.ragdemo.domain.conversation.RetrievedChunk;
import java.util.List;
import java.util.Optional;

/**
 * 会话、消息和引用的持久化边界。
 */
public interface ConversationGateway {

  /**
   * 创建会话。
   */
  Long create(Long userId, Long knowledgeBaseId, String title);

  /**
   * 查询本人会话。
   */
  List<Conversation> listByUser(Long userId);

  /**
   * 查询会话。
   */
  Optional<Conversation> findConversationById(Long id);

  /**
   * 重命名会话。
   */
  void rename(Long id, String title);

  /**
   * 逻辑删除会话。
   */
  void deleteConversation(Long id);

  /**
   * 查询最近消息。
   */
  List<Message> recentMessages(Long conversationId, int limit);

  /**
   * 保存消息。
   */
  Long saveMessage(Long conversationId, String role, String content, String status,
      Integer promptTokens, Integer completionTokens, Long elapsedMs);

  /**
   * 保存回答引用。
   */
  void saveReferences(Long messageId, List<RetrievedChunk> references);
}
