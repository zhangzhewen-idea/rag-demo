package com.zhangzhewen.ragdemo.domain.conversation;

import java.util.List;

/**
 * 已按预算装配、可直接交给回答模型的上下文。
 */
public record AnswerContext(String systemPrompt, String userPrompt,
                            List<RetrievedChunk> evidence, int estimatedPromptTokens,
                            int maxCompletionTokens) {

  public AnswerContext {
    evidence = List.copyOf(evidence);
  }
}
