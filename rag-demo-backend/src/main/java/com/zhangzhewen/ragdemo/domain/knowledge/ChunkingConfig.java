package com.zhangzhewen.ragdemo.domain.knowledge;

/**
 * 单个文档固化的切片配置。
 */
public record ChunkingConfig(Strategy strategy, String separator, int maxChunkLength,
                             int overlapLength, boolean normalizeWhitespace) {

  private static final int MAX_SEPARATOR_LENGTH = 20;

  /**
   * 创建并校验切片配置。
   */
  public ChunkingConfig {
    if (strategy == null) {
      throw new IllegalArgumentException("请选择切片模式");
    }
    if (maxChunkLength < 100 || maxChunkLength > 4000) {
      throw new IllegalArgumentException("最大长度必须在 100 到 4000 之间");
    }
    if (overlapLength < 0 || overlapLength > 500 || overlapLength >= maxChunkLength) {
      throw new IllegalArgumentException("重叠长度必须在 0 到 500 之间且小于最大长度");
    }
    if (strategy == Strategy.CUSTOM) {
      if (separator == null || separator.isEmpty()) {
        throw new IllegalArgumentException("自定义切片必须设置分隔符");
      }
      if (separator.codePointCount(0, separator.length()) > MAX_SEPARATOR_LENGTH) {
        throw new IllegalArgumentException("分隔符不能超过 20 个字符");
      }
    }
  }

  /**
   * 兼容当前全局参数的自动切片配置。
   */
  public static ChunkingConfig auto(int chunkSize, int overlap) {
    return new ChunkingConfig(Strategy.AUTO, null, chunkSize, overlap, false);
  }

  /**
   * 切片模式。
   */
  public enum Strategy {
    AUTO,
    CUSTOM
  }
}
