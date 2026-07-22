package com.zhangzhewen.ragdemo.domain.gateway;

import com.zhangzhewen.ragdemo.domain.conversation.Message;
import java.util.List;

/**
 * 将较早对话压缩为滚动摘要的模型边界。
 */
public interface ContextSummaryGateway {

  String summarize(String previousSummary, List<Message> messages, int maxSummaryTokens);
}
