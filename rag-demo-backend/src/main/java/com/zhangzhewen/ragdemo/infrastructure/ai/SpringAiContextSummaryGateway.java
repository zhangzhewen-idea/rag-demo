package com.zhangzhewen.ragdemo.infrastructure.ai;

import com.zhangzhewen.ragdemo.domain.conversation.Message;
import com.zhangzhewen.ragdemo.domain.gateway.ContextSummaryGateway;
import com.zhangzhewen.ragdemo.domain.gateway.TokenEstimator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 使用当前聊天模型生成会话滚动摘要。
 */
@Component
public class SpringAiContextSummaryGateway implements ContextSummaryGateway {

  private static final String SYSTEM = """
      你负责压缩会话上下文。只记录对话本身，不把助手曾引用的知识库内容声明为已验证事实。
      保留实体名称、用户明确约束、已完成的澄清、未解决问题和后续追问所需指代关系。
      合并重复信息，删除寒暄、展开说明和过期措辞。直接输出摘要，不要添加标题或解释。
      """;
  private final ChatClient client;
  private final TokenEstimator tokens;

  public SpringAiContextSummaryGateway(ChatModel model,
      @Qualifier("aiInteractionLoggingAdvisor") Advisor loggingAdvisor, TokenEstimator tokens) {
    this.client = ChatClient.builder(model).defaultAdvisors(loggingAdvisor).build();
    this.tokens = tokens;
  }

  @Override
  public String summarize(String previousSummary, List<Message> messages, int maxSummaryTokens) {
    String dialogue = messages.stream()
        .map(message -> message.role() + ": " + message.content())
        .collect(Collectors.joining("\n"));
    String user = "<PREVIOUS_SUMMARY>\n" + value(previousSummary)
        + "\n</PREVIOUS_SUMMARY>\n\n<NEW_MESSAGES>\n" + dialogue
        + "\n</NEW_MESSAGES>";
    String summary = client.prompt().system(SYSTEM).user(user)
        .options(ChatOptions.builder().temperature(0.0).maxTokens(maxSummaryTokens))
        .call().content();
    return tokens.truncate(summary == null ? "" : summary.trim(), maxSummaryTokens);
  }

  private String value(String value) {
    return value == null ? "" : value;
  }
}
