package com.zhangzhewen.ragdemo.domain.conversation;

import java.util.List;

/**
 * 一次检索的可观测轨迹，用于回答和离线评估复用同一条生产链路。
 */
public record RetrievalTrace(String rewrittenQuery, List<RetrievalQuery> expandedQueries,
                             List<RetrievedChunk> candidates,
                             List<RetrievedChunk> finalEvidence) {

  public RetrievalTrace {
    expandedQueries = List.copyOf(expandedQueries);
    candidates = List.copyOf(candidates);
    finalEvidence = List.copyOf(finalEvidence);
  }
}
