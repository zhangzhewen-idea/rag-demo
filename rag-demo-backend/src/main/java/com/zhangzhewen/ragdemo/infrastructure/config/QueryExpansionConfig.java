package com.zhangzhewen.ragdemo.infrastructure.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.ai.rag.preretrieval.query.expansion.QueryExpander;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 复杂问题的多查询扩展配置。
 */
@Configuration
public class QueryExpansionConfig {

  /**
   * 保留改写后的原查询，并生成三个不同角度的子查询。
   */
  @Bean
  public QueryExpander queryExpander(ChatModel model,
      @Qualifier("aiInteractionLoggingAdvisor") Advisor loggingAdvisor) {
    ChatClient.Builder builder = ChatClient.builder(model).defaultAdvisors(loggingAdvisor);
    PromptTemplate prompt = new PromptTemplate("""
        你是专业的企业知识库检索助手。请将给定查询扩展为{number}个互补的子查询。

        扩展原则：
        1. 每个子查询聚焦一个独立实体、比较维度或推理步骤。
        2. 保留原查询中的产品名、时间、范围和其他限定条件。
        3. 不得添加原查询未包含的事实，也不得回答问题。
        4. 每行只输出一个子查询，不要编号、标签、解释或空行。

        查询：
        {query}

        子查询：
        """);
    return MultiQueryExpander.builder()
        .chatClientBuilder(builder)
        .promptTemplate(prompt)
        .numberOfQueries(3)
        .includeOriginal(true)
        .build();
  }
}
