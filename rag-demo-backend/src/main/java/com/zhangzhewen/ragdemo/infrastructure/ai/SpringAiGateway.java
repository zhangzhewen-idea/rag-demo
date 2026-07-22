package com.zhangzhewen.ragdemo.infrastructure.ai;

import com.zhangzhewen.ragdemo.domain.conversation.AiUsage;
import com.zhangzhewen.ragdemo.domain.conversation.AnswerContext;
import com.zhangzhewen.ragdemo.domain.gateway.AiGateway;
import com.zhangzhewen.ragdemo.domain.gateway.TokenEstimator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
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
  public AiUsage streamAnswer(AnswerContext context, Consumer<String> delta) {
    AtomicInteger promptTokens = new AtomicInteger(context.estimatedPromptTokens());
    AtomicInteger completionTokens = new AtomicInteger();
    StringBuilder answer = new StringBuilder();
    client.prompt().system(context.systemPrompt()).user(context.userPrompt())
        .options(ChatOptions.builder().maxTokens(context.maxCompletionTokens()))
        .stream().chatResponse().doOnNext(response -> {
          String content = response.getResult() == null || response.getResult().getOutput() == null
              ? null : response.getResult().getOutput().getText();
          if (content != null && !content.isEmpty()) {
            answer.append(content);
            delta.accept(content);
          }
          Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
          if (usage != null) {
            if (usage.getPromptTokens() != null && usage.getPromptTokens() > 0) {
              promptTokens.set(usage.getPromptTokens());
            }
            if (usage.getCompletionTokens() != null && usage.getCompletionTokens() > 0) {
              completionTokens.set(usage.getCompletionTokens());
            }
          }
        }).blockLast();
    if (completionTokens.get() == 0) {
      completionTokens.set(tokens.estimate(answer.toString()));
    }
    return new AiUsage(promptTokens.get(), completionTokens.get());
  }
}
