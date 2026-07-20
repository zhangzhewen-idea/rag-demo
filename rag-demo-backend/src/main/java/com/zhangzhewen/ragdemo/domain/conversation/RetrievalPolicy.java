package com.zhangzhewen.ragdemo.domain.conversation;

/** 检索参数策略。 */
public record RetrievalPolicy(int topK,double similarityThreshold) { }
