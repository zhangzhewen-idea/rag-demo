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
    if (topK <= 0) {
      throw new IllegalArgumentException("topK 必须大于 0");
    }
    Map<ChunkKey, FusedChunk> chunks = new LinkedHashMap<>();
    for (List<RetrievedChunk> ranking : rankings) {
      for (int rank = 0; rank < ranking.size(); rank++) {
        RetrievedChunk candidate = ranking.get(rank);
        ChunkKey key = new ChunkKey(candidate.documentId(), candidate.chunkIndex());
        FusedChunk fused = chunks.computeIfAbsent(key, ignored -> new FusedChunk(candidate));
        fused.add(candidate, 1.0 / (RANK_CONSTANT + rank + 1));
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

    private RetrievedChunk chunk;
    private double rankScore;

    private FusedChunk(RetrievedChunk chunk) {
      this.chunk = chunk;
    }

    private void add(RetrievedChunk candidate, double score) {
      rankScore += score;
      if (candidate.similarityScore() > chunk.similarityScore()) {
        chunk = candidate;
      }
    }

    private RetrievedChunk chunk() {
      return chunk;
    }

    private double rankScore() {
      return rankScore;
    }
  }
}
