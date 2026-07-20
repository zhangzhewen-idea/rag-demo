package com.zhangzhewen.ragdemo.domain.identity;

import java.time.Duration;

/** Token 有效期策略。 */
public record AuthPolicy(Duration refreshTtl) { }
