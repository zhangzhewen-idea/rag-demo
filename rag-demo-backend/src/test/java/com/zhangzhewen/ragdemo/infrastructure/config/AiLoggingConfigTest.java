package com.zhangzhewen.ragdemo.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;

/**
 * 大模型交互日志配置测试。
 */
class AiLoggingConfigTest {

  /**
   * 使用同时支持同步与流式调用的 Spring AI 日志 advisor。
   */
  @Test
  void providesSimpleLoggerAdvisor() {
    assertThat(new AiLoggingConfig().aiInteractionLoggingAdvisor())
        .isInstanceOf(SimpleLoggerAdvisor.class);
  }

  /**
   * 展开非敏感模型参数，不再输出 OpenAiChatOptions 对象地址。
   */
  @Test
  void expandsSafeModelOptions() {
    OpenAiChatOptions options = OpenAiChatOptions.builder().model("qwen3.7-plus")
        .temperature(0.0).topP(.8).maxTokens(1000).build();
    ChatClientRequest request = new ChatClientRequest(new Prompt("员工处罚有哪些？", options),
        Map.of("requestId", "test-request"));

    String logged = AiLoggingConfig.requestToString(request);

    assertThat(logged).contains("员工处罚有哪些？", "model=qwen3.7-plus", "temperature=0.0",
        "topP=0.8", "maxTokens=1000", "requestId=test-request")
        .doesNotContain("OpenAiChatOptions@", "apiKey", "credential", "customHeaders");
  }
}
