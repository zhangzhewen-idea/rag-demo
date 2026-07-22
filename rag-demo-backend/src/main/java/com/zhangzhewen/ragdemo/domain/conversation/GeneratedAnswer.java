package com.zhangzhewen.ragdemo.domain.conversation;

import java.util.Objects;

/**
 * 模型生成的格式化回答。refused 是系统判断实际拒答的唯一标识。
 */
public record GeneratedAnswer(boolean refused, String content, AiUsage usage) {

  public GeneratedAnswer {
    content = content == null ? "" : content.trim();
    usage = Objects.requireNonNull(usage, "usage");
  }

  public static GeneratedAnswer refused(String content) {
    return new GeneratedAnswer(true, content, new AiUsage(0, 0));
  }
}
