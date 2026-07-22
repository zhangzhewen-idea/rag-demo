package com.zhangzhewen.ragdemo.domain.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TextChunkingPolicyTest {

  private final TextChunkingPolicy policy = new TextChunkingPolicy();

  @Test
  void keepsPunctuationAndUsesItOnlyForOversizedParagraphs() {
    String longParagraph = "A".repeat(115) + "。" + "B".repeat(115) + "。";
    var config = new ChunkingConfig(ChunkingConfig.Strategy.CUSTOM, "\n\n", 120, 0, true);

    var chunks = policy.split("标题\n\n短段落。\n\n" + longParagraph, config);

    assertThat(chunks).extracting(TextChunkingPolicy.TextChunk::text)
        .containsExactly("标题 短段落。", "A".repeat(115) + "。", "B".repeat(115) + "。");
  }

  @Test
  void normalizesWhitespaceAfterRecognizingParagraphSeparator() {
    var config = new ChunkingConfig(ChunkingConfig.Strategy.CUSTOM, "\n\n", 100, 0, true);

    var chunks = policy.split("第一段   内容\n\n第二段\t内容", config);

    assertThat(chunks).extracting(TextChunkingPolicy.TextChunk::text)
        .containsExactly("第一段 内容 第二段 内容");
  }

  @Test
  void addsBoundedOverlapWithoutExceedingMaximumLength() {
    var config = new ChunkingConfig(ChunkingConfig.Strategy.CUSTOM, "\n\n", 100, 20, true);

    var chunks = policy.split("A".repeat(80) + "\n\n" + "B".repeat(80), config);

    assertThat(chunks).hasSize(2);
    assertThat(chunks.get(1).overlapCharacters()).isEqualTo(20);
    assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.text()).hasSizeLessThanOrEqualTo(100));
    assertThat(chunks.get(1).text()).startsWith("A".repeat(20)).endsWith("B".repeat(80));
  }
}
