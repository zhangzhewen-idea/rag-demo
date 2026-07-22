package com.zhangzhewen.ragdemo.infrastructure.ai;

import com.zhangzhewen.ragdemo.domain.conversation.RetrievedChunk;
import com.zhangzhewen.ragdemo.domain.evaluation.EvaluationModels.Judgment;
import com.zhangzhewen.ragdemo.domain.gateway.EvaluationJudgeGateway;
import com.zhangzhewen.ragdemo.infrastructure.config.RagProperties;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 使用结构化输出执行生成质量评估，分数统一归一化到 0 到 1。
 */
@Component
public class SpringAiEvaluationJudgeGateway implements EvaluationJudgeGateway {

  private final ChatClient client;
  private final RagProperties.Evaluation properties;

  public SpringAiEvaluationJudgeGateway(ChatModel model,
      @Qualifier("aiInteractionLoggingAdvisor") Advisor loggingAdvisor,
      RagProperties properties) {
    this.client = ChatClient.builder(model).defaultAdvisors(loggingAdvisor).build();
    this.properties = properties.evaluation();
  }

  @Override
  public Judgment judge(String question, String goldenAnswer, String answerType, String answer,
      List<RetrievedChunk> evidence) {
    String contexts = evidence.stream()
        .map(item -> "[来源: " + item.sourceName() + ", 切片: " + item.chunkIndex() + "]\n"
            + item.excerpt())
        .collect(Collectors.joining("\n---\n"));
    String prompt = """
        你是严格的 RAG 评估器。只能依据给定问题、黄金答案和关联证据评分。
        分别给出 0 到 1 的分数：
        - faithfulness：被测答案中的事实是否都能由关联证据支撑。
        - answerRelevancy：被测答案是否直接、完整回应问题并符合答案类型。
        - evidenceSupportAccuracy：系统关联的证据是否确实支撑被测答案中的结论。
        acceptableRefusal 仅表示：答案类型为 REFUSAL 时是否正确拒答；其他类型固定为 false。
        rationale 用简洁中文指出主要依据或问题。不要使用外部知识，不要因为答案更长而加分。

        问题：{question}
        答案类型：{answerType}
        黄金答案：{goldenAnswer}
        被测答案：{answer}
        关联证据：
        {contexts}
        """;
    Judgment judgment = client.prompt()
        .user(user -> user.text(prompt).param("question", question)
            .param("answerType", answerType).param("goldenAnswer", goldenAnswer)
            .param("answer", answer).param("contexts", contexts))
        .options(ChatOptions.builder().model(properties.judgeModel()).temperature(0D))
        .call().entity(Judgment.class);
    if (judgment == null || !valid(judgment.faithfulness())
        || !valid(judgment.answerRelevancy()) || !valid(judgment.evidenceSupportAccuracy())) {
      throw new IllegalStateException("Judge 返回了无效评分");
    }
    return judgment;
  }

  private boolean valid(double value) {
    return Double.isFinite(value) && value >= 0 && value <= 1;
  }
}
