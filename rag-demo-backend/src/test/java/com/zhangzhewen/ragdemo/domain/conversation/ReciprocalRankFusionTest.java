package com.zhangzhewen.ragdemo.domain.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * RRF 排名融合规则测试。
 */
class ReciprocalRankFusionTest {

  /**
   * 同时被关键词和向量召回的切片应优先，并且重复切片只返回一次。
   */
  @Test
  void promotesChunksReturnedByBothRetrieversAndRemovesDuplicates() {
    RetrievedChunk semanticOnly = vectorChunk(1L, 0, .88, "语义命中");
    RetrievedChunk bothFromVector = vectorChunk(2L, 0, .75, "ModelArts 向量命中");
    RetrievedChunk bothFromKeyword = keywordChunk(2L, 0, 12, "ModelArts 关键词命中");
    RetrievedChunk keywordOnly = keywordChunk(3L, 0, 20, "关键词命中");

    List<RetrievedChunk> result = ReciprocalRankFusion.fuse(
        List.of(List.of(semanticOnly, bothFromVector), List.of(bothFromKeyword, keywordOnly)), 3);

    assertThat(result).extracting(RetrievedChunk::documentId).containsExactly(2L, 1L, 3L);
    assertThat(result.getFirst().excerpt()).isEqualTo("ModelArts 向量命中");
    assertThat(result.getFirst().vectorScore()).isEqualTo(.75);
    assertThat(result.getFirst().bm25Score()).isEqualTo(12);
    assertThat(result.getFirst().fusionScore()).isGreaterThan(result.get(1).fusionScore());
  }

  /**
   * 最终结果数量受 TopK 限制。
   */
  @Test
  void limitsFusedResults() {
    List<RetrievedChunk> result = ReciprocalRankFusion.fuse(
        List.of(List.of(vectorChunk(1L, 0, .9, "一"), vectorChunk(2L, 0, .8, "二")),
            List.of()), 1);

    assertThat(result).extracting(RetrievedChunk::documentId).containsExactly(1L);
  }

  /**
   * 加权 RRF 只调整各路名次贡献，不混用原始检索分数。
   */
  @Test
  void appliesWeightsToRankingContributions() {
    List<RetrievedChunk> result = ReciprocalRankFusion.fuse(
        List.of(List.of(vectorChunk(1L, 0, .7, "语义")),
            List.of(keywordChunk(2L, 0, 12, "精确"))),
        List.of(1D, .8D), 2);

    assertThat(result).extracting(RetrievedChunk::documentId).containsExactly(1L, 2L);
    assertThat(result).extracting(RetrievedChunk::fusionScore)
        .containsExactly(1D / 61, .8D / 61);
  }

  private static RetrievedChunk vectorChunk(Long documentId, int chunkIndex, double score,
      String excerpt) {
    return new RetrievedChunk(9L, documentId, "source", chunkIndex, excerpt, null, null, score,
        null, null, null);
  }

  private static RetrievedChunk keywordChunk(Long documentId, int chunkIndex, double score,
      String excerpt) {
    return new RetrievedChunk(9L, documentId, "source", chunkIndex, excerpt, null, null, null,
        score, null, null);
  }
}
