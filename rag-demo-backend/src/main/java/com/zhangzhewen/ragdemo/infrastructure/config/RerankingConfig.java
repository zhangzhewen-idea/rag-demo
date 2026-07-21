package com.zhangzhewen.ragdemo.infrastructure.config;

import com.zhangzhewen.ragdemo.domain.gateway.DocumentRerankGateway;
import com.zhangzhewen.ragdemo.infrastructure.ai.DashScopeDocumentRerankGateway;
import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 百炼文档重排客户端配置。
 */
@Configuration
public class RerankingConfig {

  /**
   * 使用独立超时和认证配置创建重排边界实现。
   */
  @Bean
  DocumentRerankGateway documentRerankGateway(RestClient.Builder builder,
      RagProperties properties) {
    RagProperties.Reranking reranking = properties.reranking();
    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(reranking.timeout()).build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(reranking.timeout());
    RestClient client = builder.clone().requestFactory(requestFactory)
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + reranking.apiKey()).build();
    return new DashScopeDocumentRerankGateway(client, reranking);
  }
}
