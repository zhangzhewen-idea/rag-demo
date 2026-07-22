package com.zhangzhewen.ragdemo.domain.gateway;

import com.zhangzhewen.ragdemo.domain.conversation.AiUsage;
import com.zhangzhewen.ragdemo.domain.conversation.AnswerContext;
import java.util.function.Consumer;

/**
 * 大模型流式回答边界。
 */
public interface AiGateway {

  /**
   * 仅依据证据生成答案并逐段回调。
   */
  AiUsage streamAnswer(AnswerContext context, Consumer<String> deltaConsumer);
}
