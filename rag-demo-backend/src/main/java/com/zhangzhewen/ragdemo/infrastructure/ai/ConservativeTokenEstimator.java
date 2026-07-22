package com.zhangzhewen.ragdemo.infrastructure.ai;

import com.zhangzhewen.ragdemo.domain.gateway.TokenEstimator;
import org.springframework.stereotype.Component;

/**
 * 按 Unicode code point 保守估算 token，避免未知兼容模型 tokenizer 低估输入。
 */
@Component
public class ConservativeTokenEstimator implements TokenEstimator {

  @Override
  public int estimate(String text) {
    return text == null || text.isEmpty() ? 0 : text.codePointCount(0, text.length());
  }

  /**
   * 优先在句末截断；找不到合适句末时按 code point 硬截断。
   */
  @Override
  public String truncate(String text, int maxTokens) {
    if (text == null || text.isEmpty() || maxTokens <= 0) {
      return "";
    }
    if (estimate(text) <= maxTokens) {
      return text;
    }
    int end = text.offsetByCodePoints(0, maxTokens);
    String prefix = text.substring(0, end);
    int minimum = Math.max(0, prefix.length() * 3 / 5);
    for (int i = prefix.length() - 1; i >= minimum; i--) {
      char value = prefix.charAt(i);
      if ("。！？.!?；;\n".indexOf(value) >= 0) {
        return prefix.substring(0, i + 1);
      }
    }
    return prefix;
  }
}
