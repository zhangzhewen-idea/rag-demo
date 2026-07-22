package com.zhangzhewen.ragdemo.domain.gateway;

import com.zhangzhewen.ragdemo.domain.knowledge.ChunkingConfig;
import java.util.List;
import java.util.Map;

/**
 * 文档格式识别、解析和切片边界。
 */
public interface DocumentParserGateway {

  /**
   * 检测真实 MIME。
   */
  String detect(String path);

  /**
   * 直接检测待预览文件的真实 MIME。
   */
  String detect(String originalName, byte[] bytes);

  /**
   * 解析并按目标大小切片。
   */
  List<ParsedChunk> parse(String path, String extension, ChunkingConfig config);

  /**
   * 不落盘解析待预览文件。
   */
  List<ParsedChunk> preview(String originalName, String extension, byte[] bytes,
      ChunkingConfig config);

  /**
   * 带真实定位信息的文本切片。
   */
  record ParsedChunk(String text, Map<String, Object> metadata, int overlapCharacters) {

  }
}
