package com.zhangzhewen.ragdemo.domain.conversation;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 使用 Reciprocal Rank Fusion 融合多路检索排名。
 */
public final class ReciprocalRankFusion {

  private static final int RANK_CONSTANT = 60;

  private ReciprocalRankFusion() {
  }

  /**
   * 按文档和切片位置去重，融合排名后截取 TopK。
   */
  public static List<RetrievedChunk> fuse(List<List<RetrievedChunk>> rankings, int topK) {
    return fuse(rankings, rankings.stream().map(ignored -> 1D).toList(), topK);
  }

  /**
   * 按各路检索权重融合排名，权重只作用于名次贡献。
   */
  public static List<RetrievedChunk> fuse(List<List<RetrievedChunk>> rankings,
      List<Double> weights, int topK) {
    if (topK <= 0) {
      throw new IllegalArgumentException("topK 必须大于 0");
    }
    if (rankings.size() != weights.size()) {
      throw new IllegalArgumentException("排名列表与权重数量必须一致");
    }
    Map<ChunkKey, FusedChunk> chunks = new LinkedHashMap<>();
    for (int rankingIndex = 0; rankingIndex < rankings.size(); rankingIndex++) {
      List<RetrievedChunk> ranking = rankings.get(rankingIndex);
      Double weight = weights.get(rankingIndex);
      if (weight == null || !Double.isFinite(weight) || weight <= 0) {
        throw new IllegalArgumentException("权重必须是大于 0 的有限数");
      }
      for (int rank = 0; rank < ranking.size(); rank++) {
        RetrievedChunk candidate = ranking.get(rank);
        ChunkKey key = new ChunkKey(candidate.documentId(), candidate.chunkIndex());
        FusedChunk fused = chunks.computeIfAbsent(key, ignored -> new FusedChunk(candidate));
        fused.add(candidate, weight / (RANK_CONSTANT + rank + 1));
      }
    }
    return chunks.values().stream()
        .sorted(Comparator.comparingDouble(FusedChunk::rankScore).reversed())
        .limit(topK)
        .map(FusedChunk::chunk)
        .toList();
  }

  private record ChunkKey(Long documentId, int chunkIndex) {
  }

  private static final class FusedChunk {

    private final RetrievedChunk chunk;
    private Double vectorScore;
    private Double bm25Score;
    private double rankScore;

    private FusedChunk(RetrievedChunk chunk) {
      this.chunk = chunk;
    }

    private void add(RetrievedChunk candidate, double score) {
      rankScore += score;
      vectorScore = max(vectorScore, candidate.vectorScore());
      bm25Score = max(bm25Score, candidate.bm25Score());
    }

    private RetrievedChunk chunk() {
      return chunk.withFusionScores(vectorScore, bm25Score, rankScore);
    }

    private double rankScore() {
      return rankScore;
    }

    private Double max(Double left, Double right) {
      if (left == null) {
        return right;
      }
      return right == null ? left : Math.max(left, right);
    }
  }
}
