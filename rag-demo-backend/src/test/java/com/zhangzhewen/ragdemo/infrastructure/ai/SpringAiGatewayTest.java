package com.zhangzhewen.ragdemo.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zhangzhewen.ragdemo.domain.conversation.AnswerContext;
import com.zhangzhewen.ragdemo.domain.conversation.RetrievedChunk;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * 格式化回答与 token usage 适配测试。
 */
class SpringAiGatewayTest {

  @Test
  void returnsStructuredAnswerAndProviderUsage() {
    ChatModel model = mock(ChatModel.class);
    ChatResponse response = new ChatResponse(
        List.of(new Generation(new AssistantMessage(
            "{\"refused\":false,\"content\":\"回答\"}"))),
        ChatResponseMetadata.builder().usage(new DefaultUsage(123, 7)).build());
    when(model.getOptions()).thenReturn(ChatOptions.builder().build());
    when(model.call(any(Prompt.class))).thenReturn(response);
    SpringAiGateway gateway = new SpringAiGateway(model, new SimpleLoggerAdvisor(),
        new ConservativeTokenEstimator());
    AnswerContext context = context(99);
    var generated = gateway.generateAnswer(context);

    assertThat(generated.content()).isEqualTo("回答");
    assertThat(generated.refused()).isFalse();
    assertThat(generated.usage().promptTokens()).isEqualTo(123);
    assertThat(generated.usage().completionTokens()).isEqualTo(7);
  }

  @Test
  void estimatesUsageWhenProviderOmitsIt() {
    ChatModel model = mock(ChatModel.class);
    ChatResponse response = new ChatResponse(
        List.of(new Generation(new AssistantMessage(
            "{\"refused\":false,\"content\":\"估算回答\"}"))));
    when(model.getOptions()).thenReturn(ChatOptions.builder().build());
    when(model.call(any(Prompt.class))).thenReturn(response);
    SpringAiGateway gateway = new SpringAiGateway(model, new SimpleLoggerAdvisor(),
        new ConservativeTokenEstimator());

    var generated = gateway.generateAnswer(context(88));

    assertThat(generated.usage().promptTokens()).isEqualTo(88);
    assertThat(generated.usage().completionTokens()).isEqualTo(4);
  }

  @Test
  void returnsRefusalMarkerFromStructuredAnswer() {
    ChatModel model = mock(ChatModel.class);
    ChatResponse response = new ChatResponse(
        List.of(new Generation(new AssistantMessage(
            "{\"refused\":true,\"content\":\"没有可靠依据\"}"))));
    when(model.getOptions()).thenReturn(ChatOptions.builder().build());
    when(model.call(any(Prompt.class))).thenReturn(response);
    SpringAiGateway gateway = new SpringAiGateway(model, new SimpleLoggerAdvisor(),
        new ConservativeTokenEstimator());

    var generated = gateway.generateAnswer(context(88));

    assertThat(generated.refused()).isTrue();
  }

  private AnswerContext context(int promptTokens) {
    RetrievedChunk evidence = new RetrievedChunk(1L, 2L, "来源", 0, "证据", null, null,
        .9, null, .03, null);
    return new AnswerContext("system", "<EVIDENCE>证据</EVIDENCE>", List.of(evidence),
        promptTokens, 100);
  }
}
