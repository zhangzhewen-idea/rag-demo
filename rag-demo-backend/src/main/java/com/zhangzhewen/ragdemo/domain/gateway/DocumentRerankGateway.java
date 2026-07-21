package com.zhangzhewen.ragdemo.domain.gateway;

import com.zhangzhewen.ragdemo.domain.conversation.RetrievedChunk;
import java.util.List;

/**
 * 面向问答用例的候选证据重排能力边界。
 */
public interface DocumentRerankGateway {

  /**
   * 根据查询对候选证据二次打分并返回前 topK 条。
   */
  List<RetrievedChunk> rerank(String query, List<RetrievedChunk> candidates, int topK);
}
