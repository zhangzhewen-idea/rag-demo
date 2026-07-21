package com.zhangzhewen.ragdemo.domain.identity;

import java.time.Duration;

/**
 * JWT 密钥和 Access Token 有效期策略。
 */
public record JwtPolicy(String secret, Duration accessTtl) {

}
