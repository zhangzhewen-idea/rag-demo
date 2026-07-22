package com.zhangzhewen.ragdemo.adapter.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

/**
 * CORS 源配置测试。
 */
class SecurityConfigTest {

  /**
   * 生产前端必须能够跨域携带 Refresh Cookie 调用后端。
   */
  @Test
  void allowsProductionFrontendOrigin() {
    CorsConfiguration configuration = new SecurityConfig().corsConfigurationSource()
        .getCorsConfiguration(new MockHttpServletRequest("OPTIONS", "/api/auth/login"));

    assertThat(configuration).isNotNull();
    assertThat(configuration.checkOrigin("https://rag-demo-web.harmonies.cc"))
        .isEqualTo("https://rag-demo-web.harmonies.cc");
    assertThat(configuration.getAllowCredentials()).isTrue();
  }
}
