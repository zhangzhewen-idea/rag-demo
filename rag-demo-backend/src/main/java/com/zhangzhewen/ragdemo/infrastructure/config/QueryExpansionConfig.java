package com.zhangzhewen.ragdemo.infrastructure.config;

import com.zhangzhewen.ragdemo.domain.conversation.RetrievalQuery;
import com.zhangzhewen.ragdemo.infrastructure.ai.SpringAiQueryExpansionGateway.QueryPlanner;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 复杂问题的多路检索规划配置。
 */
@Configuration
public class QueryExpansionConfig {

  private static final int NUMBER_OF_PLANS = 4;
  private static final ParameterizedTypeReference<List<RetrievalQuery>> PLAN_TYPE =
      new ParameterizedTypeReference<>() {
      };

  /**
   * 一次模型调用生成四组结构化语义查询和关键词查询。
   */
  @Bean
  public QueryPlanner queryPlanner(ChatModel model,
      @Qualifier("aiInteractionLoggingAdvisor") Advisor loggingAdvisor) {
    ChatClient client = ChatClient.builder(model).defaultAdvisors(loggingAdvisor).build();
    String prompt = """
        你是专业的企业知识库检索规划助手。请为给定查询生成{number}组互补的检索计划。

        每组计划包含：
        - semanticQuery：用于向量检索的完整自然语言查询，表达清晰且可独立理解。
        - keywordQuery：用于 BM25 的精简关键词，使用空格分隔，保留产品名、专有名词、缩写、时间和关键限定词。

        规划原则：
        1. 第一组覆盖原查询的完整意图，其余各组聚焦独立实体、比较维度或推理步骤。
        2. keywordQuery 删除“如何、什么、区别”等低信息疑问词，避免只保留“平台、功能”等泛词。
        3. 不得添加原查询未包含的事实，不得回答问题。
        4. 严格按照结构化输出约束返回，不要添加解释。

        查询：
        {query}
        """;
    return query -> client.prompt()
        .user(user -> user.text(prompt).param("number", NUMBER_OF_PLANS).param("query", query))
        .call()
        .entity(PLAN_TYPE);
  }
}
