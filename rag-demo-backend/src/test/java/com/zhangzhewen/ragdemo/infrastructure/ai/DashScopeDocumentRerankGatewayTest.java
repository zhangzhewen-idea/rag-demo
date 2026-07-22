package com.zhangzhewen.ragdemo.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.zhangzhewen.ragdemo.domain.conversation.RetrievedChunk;
import com.zhangzhewen.ragdemo.infrastructure.config.RagProperties;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 百炼文档重排适配器测试。
 */
class DashScopeDocumentRerankGatewayTest {

  private static final String URL = "https://example.test/compatible-api/v1/reranks";

  /**
   * 使用响应索引恢复候选切片并保留两种分数。
   */
  @Test
  void mapsRankedIndexesBackToCandidates() {
    RestClient.Builder builder = RestClient.builder()
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer test-key");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    DashScopeDocumentRerankGateway gateway = gateway(builder, true);
    List<RetrievedChunk> candidates = candidates();
    server.expect(requestTo(URL)).andExpect(method(HttpMethod.POST))
        .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
        .andExpect(content().json("""
            {
              "model":"qwen3-rerank",
              "query":"年假规则",
              "documents":["切片一","切片二","切片三"],
              "top_n":2,
              "instruct":"Given a web search query, retrieve relevant passages that answer the query."
            }
            """))
        .andRespond(withSuccess("""
            {"results":[
              {"index":2,"relevance_score":0.96},
              {"index":0,"relevance_score":0.72}
            ]}
            """, MediaType.APPLICATION_JSON));

    List<RetrievedChunk> result = gateway.rerank("年假规则", candidates, 2);

    assertThat(result).extracting(RetrievedChunk::documentId).containsExactly(3L, 1L);
    assertThat(result).extracting(RetrievedChunk::vectorScore).containsExactly(.7, .9);
    assertThat(result).extracting(RetrievedChunk::rerankScore).containsExactly(.96, .72);
    server.verify();
  }

  /**
   * 远程服务异常时使用 RRF 原顺序的前 topK 条。
   */
  @Test
  void fallsBackToRrfOrderWhenRequestFails() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    DashScopeDocumentRerankGateway gateway = gateway(builder, true);
    server.expect(requestTo(URL)).andRespond(withServerError());

    List<RetrievedChunk> result = gateway.rerank("年假规则", candidates(), 2);

    assertThat(result).extracting(RetrievedChunk::documentId).containsExactly(1L, 2L);
    assertThat(result).extracting(RetrievedChunk::rerankScore).containsOnlyNulls();
    server.verify();
  }

  /**
   * 重复或越界索引会放弃整个响应，避免候选元数据错配。
   */
  @ParameterizedTest
  @ValueSource(strings = {
      "{\"results\":[{\"index\":0,\"relevance_score\":0.9},{\"index\":0,\"relevance_score\":0.8}]}",
      "{\"results\":[{\"index\":9,\"relevance_score\":0.9},{\"index\":1,\"relevance_score\":0.8}]}",
      "{\"results\":[{\"index\":0,\"relevance_score\":1.1},{\"index\":1,\"relevance_score\":0.8}]}",
      "{\"results\":[{\"index\":0,\"relevance_score\":0.9}]}"
  })
  void fallsBackWhenResponseIsInvalid(String response) {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    DashScopeDocumentRerankGateway gateway = gateway(builder, true);
    server.expect(requestTo(URL)).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

    List<RetrievedChunk> result = gateway.rerank("年假规则", candidates(), 2);

    assertThat(result).extracting(RetrievedChunk::documentId).containsExactly(1L, 2L);
    server.verify();
  }

  /**
   * 读取超时时使用 RRF 原顺序继续问答。
   */
  @Test
  void fallsBackWhenRequestTimesOut() throws IOException {
    HttpServer server = HttpServer.create(
        new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext("/reranks", exchange -> {
      try {
        Thread.sleep(250);
        exchange.sendResponseHeaders(200, 0);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
      } catch (IOException ignored) {
        // 客户端超时后会主动断开连接。
      } finally {
        exchange.close();
      }
    });
    server.start();
    try {
      Duration timeout = Duration.ofMillis(30);
      HttpClient httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
      JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
      factory.setReadTimeout(timeout);
      RestClient client = RestClient.builder().requestFactory(factory).build();
      String url = "http://" + server.getAddress().getHostString() + ":"
          + server.getAddress().getPort() + "/reranks";
      DashScopeDocumentRerankGateway gateway = new DashScopeDocumentRerankGateway(client,
          new RagProperties.Reranking(true, "qwen3-rerank", url, "test-key", timeout));

      List<RetrievedChunk> result = gateway.rerank("年假规则", candidates(), 2);

      assertThat(result).extracting(RetrievedChunk::documentId).containsExactly(1L, 2L);
    } finally {
      server.stop(0);
    }
  }

  /**
   * 关闭重排时不发起 HTTP 请求。
   */
  @Test
  void skipsRemoteCallWhenDisabled() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    DashScopeDocumentRerankGateway gateway = gateway(builder, false);

    List<RetrievedChunk> result = gateway.rerank("年假规则", candidates(), 2);

    assertThat(result).extracting(RetrievedChunk::documentId).containsExactly(1L, 2L);
    server.verify();
  }

  private DashScopeDocumentRerankGateway gateway(RestClient.Builder builder, boolean enabled) {
    return new DashScopeDocumentRerankGateway(builder.build(),
        new RagProperties.Reranking(enabled, "qwen3-rerank", URL, "test-key",
            Duration.ofSeconds(5)));
  }

  private List<RetrievedChunk> candidates() {
    return List.of(chunk(1L, .9, "切片一"), chunk(2L, .8, "切片二"),
        chunk(3L, .7, "切片三"));
  }

  private RetrievedChunk chunk(Long documentId, double score, String excerpt) {
    return new RetrievedChunk(9L, documentId, "source", 0, excerpt, null, null, score, null,
        .03, null);
  }
}
