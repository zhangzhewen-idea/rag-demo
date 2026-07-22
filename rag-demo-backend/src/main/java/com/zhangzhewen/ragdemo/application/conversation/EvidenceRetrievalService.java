package com.zhangzhewen.ragdemo.application.conversation;

import com.zhangzhewen.ragdemo.domain.conversation.Message;
import com.zhangzhewen.ragdemo.domain.conversation.ReciprocalRankFusion;
import com.zhangzhewen.ragdemo.domain.conversation.RetrievalPolicy;
import com.zhangzhewen.ragdemo.domain.conversation.RetrievalQuery;
import com.zhangzhewen.ragdemo.domain.conversation.RetrievalTrace;
import com.zhangzhewen.ragdemo.domain.conversation.RetrievedChunk;
import com.zhangzhewen.ragdemo.domain.gateway.DocumentRerankGateway;
import com.zhangzhewen.ragdemo.domain.gateway.DocumentSearchGateway;
import com.zhangzhewen.ragdemo.domain.gateway.QueryExpansionGateway;
import com.zhangzhewen.ragdemo.domain.gateway.QueryRewriteGateway;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * 查询改写、多路召回、融合和重排的共享应用层协作者。
 */
@Component
public class EvidenceRetrievalService {

  private final DocumentSearchGateway search;
  private final DocumentRerankGateway rerank;
  private final QueryRewriteGateway queryRewrite;
  private final QueryExpansionGateway queryExpansion;
  private final RetrievalPolicy policy;

  public EvidenceRetrievalService(DocumentSearchGateway search, DocumentRerankGateway rerank,
      QueryRewriteGateway queryRewrite, QueryExpansionGateway queryExpansion,
      RetrievalPolicy policy) {
    this.search = search;
    this.rerank = rerank;
    this.queryRewrite = queryRewrite;
    this.queryExpansion = queryExpansion;
    this.policy = policy;
  }

  /**
   * 执行生产检索链路并返回候选与最终证据，便于分阶段计算指标。
   */
  public RetrievalTrace retrieve(Long knowledgeBaseId, String question, String summary,
      List<Message> history) {
    String rewritten = queryRewrite.rewrite(contextualQuery(question, summary, history));
    List<RetrievalQuery> plans = queryExpansion.plan(rewritten);
    List<List<RetrievedChunk>> rankings = plans.stream()
        .map(plan -> search.search(knowledgeBaseId, plan, policy.candidateTopK(),
            policy.similarityThreshold()))
        .toList();
    List<RetrievedChunk> candidates = ReciprocalRankFusion.fuse(rankings, policy.candidateTopK());
    List<RetrievedChunk> finalEvidence = candidates.isEmpty() ? List.of()
        : rerank.rerank(rewritten, candidates, policy.topK());
    return new RetrievalTrace(rewritten, plans, candidates, finalEvidence);
  }

  private String contextualQuery(String question, String summary, List<Message> history) {
    String normalized = question.toLowerCase(Locale.ROOT)
        .replaceAll("[\\s，。！？、,.!?：:；;]", "");
    boolean refersToHistory = List.of("这", "那", "上述", "前面", "刚才", "其他", "除了", "它")
        .stream().anyMatch(normalized::contains);
    if (!refersToHistory) {
      return question;
    }
    for (int i = history.size() - 1; i >= 0; i--) {
      Message message = history.get(i);
      if ("ASSISTANT".equals(message.role()) && !message.content().isBlank()) {
        String context = message.content().length() > 600
            ? message.content().substring(0, 600) : message.content();
        String prefix = summary == null || summary.isBlank() ? "" : summary + "\n";
        return prefix + context + "\n追问：" + question;
      }
    }
    return summary == null || summary.isBlank() ? question : summary + "\n追问：" + question;
  }
}
