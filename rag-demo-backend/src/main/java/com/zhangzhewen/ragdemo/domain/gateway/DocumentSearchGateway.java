package com.zhangzhewen.ragdemo.domain.gateway;

import com.zhangzhewen.ragdemo.domain.conversation.RetrievedChunk;
import java.util.List;

/**
 * 面向问答用例的文档检索能力边界。
 */
public interface DocumentSearchGateway {

  /**
   * 在指定知识库内检索证据。
   */
  List<RetrievedChunk> search(Long knowledgeBaseId, String query, int topK, double threshold);

  /**
   * 在指定知识库的单个文档内检索证据。
   */
  List<RetrievedChunk> searchDocument(Long knowledgeBaseId, Long documentId, String query,
      int topK, double threshold);
}
