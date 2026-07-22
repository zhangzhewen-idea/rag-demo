package com.zhangzhewen.ragdemo.domain.gateway;

import com.zhangzhewen.ragdemo.domain.conversation.AnswerContext;
import com.zhangzhewen.ragdemo.domain.conversation.GeneratedAnswer;

/**
 * 大模型格式化回答边界。
 */
public interface AiGateway {

  /**
   * 仅依据证据生成带拒答标识的格式化答案。
   */
  GeneratedAnswer generateAnswer(AnswerContext context);
}
