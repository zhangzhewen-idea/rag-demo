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
    RetrievedChunk semanticOnly = chunk(1L, 0, .88, "语义命中");
    RetrievedChunk bothFromVector = chunk(2L, 0, .75, "ModelArts 向量命中");
    RetrievedChunk bothFromKeyword = chunk(2L, 0, .9, "ModelArts 关键词命中");
    RetrievedChunk keywordOnly = chunk(3L, 0, .9, "关键词命中");

    List<RetrievedChunk> result = ReciprocalRankFusion.fuse(
        List.of(List.of(semanticOnly, bothFromVector), List.of(bothFromKeyword, keywordOnly)), 3);

    assertThat(result).extracting(RetrievedChunk::documentId).containsExactly(2L, 1L, 3L);
    assertThat(result.getFirst().excerpt()).isEqualTo("ModelArts 关键词命中");
  }

  /**
   * 最终结果数量受 TopK 限制。
   */
  @Test
  void limitsFusedResults() {
    List<RetrievedChunk> result = ReciprocalRankFusion.fuse(
        List.of(List.of(chunk(1L, 0, .9, "一"), chunk(2L, 0, .8, "二")), List.of()), 1);

    assertThat(result).extracting(RetrievedChunk::documentId).containsExactly(1L);
  }

  private static RetrievedChunk chunk(Long documentId, int chunkIndex, double score,
      String excerpt) {
    return new RetrievedChunk(9L, documentId, "source", chunkIndex, score, excerpt, null, null);
  }
}
