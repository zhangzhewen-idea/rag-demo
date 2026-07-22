package com.zhangzhewen.ragdemo.domain.conversation;

/**
 * 一次回答生成实际或估算的 token 用量。
 */
public record AiUsage(int promptTokens, int completionTokens) {

}
