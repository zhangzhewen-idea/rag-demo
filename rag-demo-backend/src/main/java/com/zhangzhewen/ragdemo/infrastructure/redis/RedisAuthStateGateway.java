package com.zhangzhewen.ragdemo.infrastructure.redis;

import com.zhangzhewen.ragdemo.domain.gateway.AuthStateGateway;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 认证状态实现，使用独立命名空间避免污染向量 Key。
 */
@Component
public class RedisAuthStateGateway implements AuthStateGateway {

  private final StringRedisTemplate redis;

  /**
   * 注入 Redis。
   */
  public RedisAuthStateGateway(StringRedisTemplate redis) {
    this.redis = redis;
  }

  @Override
  public void saveRefresh(String tokenId, Long userId, Duration ttl) {
    redis.opsForValue().set("rag:auth:refresh:" + tokenId, userId.toString(), ttl);
  }

  @Override
  public Long getRefreshUser(String tokenId) {
    String value = redis.opsForValue().get("rag:auth:refresh:" + tokenId);
    return value == null ? null : Long.valueOf(value);
  }

  @Override
  public void deleteRefresh(String tokenId) {
    redis.delete("rag:auth:refresh:" + tokenId);
  }

  @Override
  public void blacklist(String jti, Duration ttl) {
    if (!ttl.isNegative() && !ttl.isZero()) {
      redis.opsForValue().set("rag:auth:blacklist:" + jti, "1", ttl);
    }
  }

  @Override
  public boolean blacklisted(String jti) {
    return Boolean.TRUE.equals(redis.hasKey("rag:auth:blacklist:" + jti));
  }

  @Override
  public long recordLoginFailure(String username, Duration ttl) {
    String key = "rag:login:fail:" + username;
    Long count = redis.opsForValue().increment(key);
    if (count != null && count == 1) {
      redis.expire(key, ttl);
    }
    return count == null ? 0 : count;
  }

  @Override
  public void clearLoginFailures(String username) {
    redis.delete("rag:login:fail:" + username);
  }

  @Override
  public long loginFailures(String username) {
    String value = redis.opsForValue().get("rag:login:fail:" + username);
    return value == null ? 0 : Long.parseLong(value);
  }
}
