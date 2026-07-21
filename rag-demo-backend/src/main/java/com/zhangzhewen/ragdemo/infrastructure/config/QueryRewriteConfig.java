package com.zhangzhewen.ragdemo.infrastructure.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 知识库检索查询改写配置。
 */
@Configuration
public class QueryRewriteConfig {

  /**
   * 使用全局模型配置构建查询改写器。
   */
  @Bean
  public QueryTransformer queryTransformer(ChatModel model) {
    ChatClient.Builder builder = ChatClient.builder(model);
    PromptTemplate prompt = new PromptTemplate("""
        你是专业的企业知识库查询改写助手。请将原始查询改写为更适合在{target}中检索的查询。

        改写原则：
        1. 将口语表达转换为清晰的书面表达。
        2. 根据查询中已有的对话上下文补全代词指代，使查询可以独立理解。
        3. 使用企业知识库中常见的专业术语，并保留有助于检索的实体、时间和限定条件。
        4. 保持原始意图不变，不得添加原查询未包含的事实。
        5. 只输出改写后的查询，不要解释改写过程，也不要添加标签或引号。

        原始查询：
        {query}

        改写后的查询：
        """);
    return RewriteQueryTransformer.builder()
        .chatClientBuilder(builder)
        .promptTemplate(prompt)
        .targetSearchSystem("企业知识库的向量与 BM25 混合检索系统")
        .build();
  }
}
