package com.zhangzhewen.ragdemo.adapter.security;

import com.zhangzhewen.ragdemo.domain.gateway.TokenIssuerGateway;
import com.zhangzhewen.ragdemo.domain.identity.JwtPolicy;
import com.zhangzhewen.ragdemo.domain.identity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * JWT 签发与验签服务。
 */
@Component
public class JwtService implements TokenIssuerGateway {

  private final JwtPolicy policy;
  private final SecretKey key;

  /**
   * 创建 JWT 服务。
   */
  public JwtService(JwtPolicy policy) {
    this.policy = policy;
    this.key = Keys.hmacShaKeyFor(policy.secret().getBytes(StandardCharsets.UTF_8));
  }

  /**
   * 签发 Access Token。
   */
  @Override
  public IssuedToken issueAccess(User user) {
    Instant now = Instant.now();
    Instant expiry = now.plus(policy.accessTtl());
    String jti = UUID.randomUUID().toString();
    String value = Jwts.builder().id(jti).subject(user.id().toString())
        .claim("username", user.username())
        .claim("roles", user.roles().stream().map(Enum::name).toList()).issuedAt(Date.from(now))
        .expiration(Date.from(expiry)).signWith(key).compact();
    return new IssuedToken(value, jti, expiry);
  }

  /**
   * 解析并验证 Token。
   */
  public Claims parse(String token) {
    return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
  }
}
