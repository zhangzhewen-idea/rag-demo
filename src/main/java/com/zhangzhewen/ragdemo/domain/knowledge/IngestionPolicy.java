package com.zhangzhewen.ragdemo.domain.knowledge;

/** 文档切片与批量向量化策略。 */
public record IngestionPolicy(int chunkSize,int chunkOverlap,int embeddingBatchSize) { }
