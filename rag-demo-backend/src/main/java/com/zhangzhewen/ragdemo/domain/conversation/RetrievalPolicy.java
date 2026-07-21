package com.zhangzhewen.ragdemo.domain.conversation;

/**
 * 向量数据库检索参数。
 */
public record RetrievalPolicy(int topK, double similarityThreshold) {

}
