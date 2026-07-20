package com.zhangzhewen.ragdemo.domain.conversation;

/** 检索命中的证据切片及真实定位元数据。 */
public record RetrievedChunk(Long knowledgeBaseId, Long documentId, String sourceName, int chunkIndex,
                             double similarityScore, String excerpt, Integer pageNumber, String sectionTitle) { }
