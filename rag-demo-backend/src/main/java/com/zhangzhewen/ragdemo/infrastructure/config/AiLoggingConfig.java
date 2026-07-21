package com.zhangzhewen.ragdemo.infrastructure.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 大模型交互日志配置。
 */
@Configuration
public class AiLoggingConfig {

  /**
   * 记录 ChatClient 的完整请求和响应，便于调试提示词与模型输出。
   */
  @Bean("aiInteractionLoggingAdvisor")
  Advisor aiInteractionLoggingAdvisor() {
    return SimpleLoggerAdvisor.builder().requestToString(AiLoggingConfig::requestToString).build();
  }

  static String requestToString(ChatClientRequest request) {
    if (request == null) {
      return "null";
    }
    return "messages=" + request.prompt().getInstructions()
        + ", modelOptions=" + safeModelOptions(request.prompt().getOptions())
        + ", context=" + request.context();
  }

  private static Map<String, Object> safeModelOptions(ChatOptions options) {
    Map<String, Object> values = new LinkedHashMap<>();
    if (options == null) {
      return values;
    }
    BiConsumer<String, Object> put = (name, value) -> {
      if (value != null) {
        values.put(name, value);
      }
    };
    put.accept("model", options.getModel());
    put.accept("temperature", options.getTemperature());
    put.accept("topP", options.getTopP());
    put.accept("topK", options.getTopK());
    put.accept("maxTokens", options.getMaxTokens());
    put.accept("frequencyPenalty", options.getFrequencyPenalty());
    put.accept("presencePenalty", options.getPresencePenalty());
    put.accept("stopSequences", options.getStopSequences());
    if (options instanceof OpenAiChatOptions openAi) {
      put.accept("maxCompletionTokens", openAi.getMaxCompletionTokens());
      put.accept("n", openAi.getN());
      put.accept("seed", openAi.getSeed());
      put.accept("reasoningEffort", openAi.getReasoningEffort());
      put.accept("verbosity", openAi.getVerbosity());
      put.accept("serviceTier", openAi.getServiceTier());
      put.accept("parallelToolCalls", openAi.getParallelToolCalls());
      put.accept("store", openAi.getStore());
    }
    return values;
  }
}
