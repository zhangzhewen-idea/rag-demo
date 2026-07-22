package com.zhangzhewen.ragdemo.infrastructure.ai;

import com.zhangzhewen.ragdemo.domain.conversation.AiUsage;
import com.zhangzhewen.ragdemo.domain.conversation.AnswerContext;
import com.zhangzhewen.ragdemo.domain.conversation.GeneratedAnswer;
import com.zhangzhewen.ragdemo.domain.gateway.AiGateway;
import com.zhangzhewen.ragdemo.domain.gateway.TokenEstimator;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 阿里云百炼 OpenAI 兼容 ChatModel 适配器。
 */
@Component
public class SpringAiGateway implements AiGateway {

  private final ChatClient client;
  private final TokenEstimator tokens;

  /**
   * 从 Spring AI ChatModel 构建客户端。
   */
  public SpringAiGateway(ChatModel model,
      @Qualifier("aiInteractionLoggingAdvisor") Advisor loggingAdvisor, TokenEstimator tokens) {
    this.client = ChatClient.builder(model).defaultAdvisors(loggingAdvisor).build();
    this.tokens = tokens;
  }

  /**
   * 证据区带清晰边界，要求模型把文档内容视为资料而非指令。
   */
  @Override
  public GeneratedAnswer generateAnswer(AnswerContext context) {
    ResponseEntity<ChatResponse, FormattedAnswer> response = client.prompt()
        .system(context.systemPrompt()).user(context.userPrompt())
        .options(ChatOptions.builder().maxTokens(context.maxCompletionTokens()))
        .call().responseEntity(FormattedAnswer.class,
            spec -> spec.useProviderStructuredOutput().validateSchema());
    FormattedAnswer formatted = response.entity();
    if (formatted == null) {
      throw new IllegalStateException("模型未返回格式化回答");
    }
    String content = formatted.content() == null ? "" : formatted.content().trim();
    if (!formatted.refused() && content.isEmpty()) {
      throw new IllegalStateException("模型返回了空回答");
    }
    ChatResponse chatResponse = response.response();
    Usage providerUsage = chatResponse == null || chatResponse.getMetadata() == null ? null
        : chatResponse.getMetadata().getUsage();
    int promptTokens = positive(providerUsage == null ? null : providerUsage.getPromptTokens(),
        context.estimatedPromptTokens());
    int completionTokens = positive(
        providerUsage == null ? null : providerUsage.getCompletionTokens(),
        tokens.estimate(content));
    return new GeneratedAnswer(formatted.refused(), content,
        new AiUsage(promptTokens, completionTokens));
  }

  private int positive(Integer value, int fallback) {
    return value != null && value > 0 ? value : fallback;
  }

  private record FormattedAnswer(boolean refused, String content) {

  }
}
