package com.zhangzhewen.ragdemo.adapter.security;

import com.zhangzhewen.ragdemo.domain.gateway.AuthStateGateway;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Bearer JWT 认证过滤器。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtService jwt;
  private final AuthStateGateway authState;

  /**
   * 注入依赖。
   */
  public JwtAuthenticationFilter(JwtService jwt, AuthStateGateway authState) {
    this.jwt = jwt;
    this.authState = authState;
  }

  /**
   * 验证 Token，失败时交给安全链统一返回 401。
   */
  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain chain) throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      try {
        Claims claims = jwt.parse(header.substring(7));
        if (!authState.blacklisted(claims.getId())) {
          @SuppressWarnings("unchecked") List<String> roles = claims.get("roles", List.class);
          var authorities = roles.stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r))
              .toList();
          var authentication = new UsernamePasswordAuthenticationToken(
              Long.valueOf(claims.getSubject()), null, authorities);
          authentication.setDetails(claims);
          SecurityContextHolder.getContext().setAuthentication(authentication);
        }
      } catch (JwtException | IllegalArgumentException ignored) {
        SecurityContextHolder.clearContext();
      }
    }
    chain.doFilter(request, response);
  }
}
