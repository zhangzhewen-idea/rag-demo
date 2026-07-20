package com.zhangzhewen.ragdemo.domain.gateway;

import com.zhangzhewen.ragdemo.domain.conversation.RetrievedChunk;
import java.util.List;
import java.util.Map;

/** 文档向量写入、检索和删除边界。 */
public interface VectorGateway {
    /** 写入带元数据的切片。 */ void add(List<String> chunks, List<Map<String, Object>> metadata);
    /** 按知识库检索可靠证据。 */ List<RetrievedChunk> search(Long knowledgeBaseId, String query, int topK, double threshold);
    /** 按文档删除全部切片。 */ void deleteByDocumentId(Long documentId);
}
