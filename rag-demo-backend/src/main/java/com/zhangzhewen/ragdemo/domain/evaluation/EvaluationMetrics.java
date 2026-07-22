package com.zhangzhewen.ragdemo.domain.evaluation;

import com.zhangzhewen.ragdemo.domain.conversation.RetrievedChunk;
import com.zhangzhewen.ragdemo.domain.evaluation.EvaluationModels.ExpectedContext;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 基于黄金证据计算确定性的检索指标。
 */
public final class EvaluationMetrics {

  private EvaluationMetrics() {
  }

  public static RetrievalScores calculate(List<ExpectedContext> expected,
      List<RetrievedChunk> candidates, List<RetrievedChunk> finalEvidence) {
    if (expected.isEmpty()) {
      return new RetrievalScores(null, null, null, null, finalEvidence.isEmpty() ? 1D : 0D);
    }
    int first = firstRelevantRank(expected, candidates);
    double hit = first < 0 ? 0D : 1D;
    double mrr = first < 0 ? 0D : 1D / (first + 1);
    Set<Integer> matched = new HashSet<>();
    int relevantChunks = 0;
    for (RetrievedChunk chunk : finalEvidence) {
      boolean relevant = false;
      for (int i = 0; i < expected.size(); i++) {
        if (matches(expected.get(i), chunk)) {
          matched.add(i);
          relevant = true;
        }
      }
      if (relevant) {
        relevantChunks++;
      }
    }
    double recall = (double) matched.size() / expected.size();
    double precision = finalEvidence.isEmpty() ? 0D
        : (double) relevantChunks / finalEvidence.size();
    return new RetrievalScores(hit, mrr, recall, precision, null);
  }

  private static int firstRelevantRank(List<ExpectedContext> expected,
      List<RetrievedChunk> chunks) {
    for (int i = 0; i < chunks.size(); i++) {
      RetrievedChunk chunk = chunks.get(i);
      if (expected.stream().anyMatch(item -> matches(item, chunk))) {
        return i;
      }
    }
    return -1;
  }

  static boolean matches(ExpectedContext expected, RetrievedChunk chunk) {
    return normalize(expected.sourceName()).equals(normalize(chunk.sourceName()))
        && normalize(chunk.excerpt()).contains(normalize(expected.evidenceContains()));
  }

  private static String normalize(String value) {
    return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
  }

  public record RetrievalScores(Double candidateHitRate, Double candidateMrr,
                                Double contextRecall, Double contextPrecision,
                                Double noAnswerAccuracy) {
  }
}
