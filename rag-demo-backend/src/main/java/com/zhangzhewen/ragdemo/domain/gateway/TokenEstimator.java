package com.zhangzhewen.ragdemo.domain.gateway;

/**
 * 模型输入 token 数量估算边界。
 */
public interface TokenEstimator {

  int estimate(String text);

  String truncate(String text, int maxTokens);
}
