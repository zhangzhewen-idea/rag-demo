package com.zhangzhewen.ragdemo.domain.gateway;

import com.zhangzhewen.ragdemo.domain.conversation.Message;
import com.zhangzhewen.ragdemo.domain.conversation.RetrievedChunk;
import java.util.List;
import java.util.function.Consumer;

/**
 * 大模型流式回答边界。
 */
public interface AiGateway {

  /**
   * 仅依据证据生成答案并逐段回调。
   */
  void streamAnswer(String question, List<Message> history, List<RetrievedChunk> evidence,
      Consumer<String> deltaConsumer);
}
