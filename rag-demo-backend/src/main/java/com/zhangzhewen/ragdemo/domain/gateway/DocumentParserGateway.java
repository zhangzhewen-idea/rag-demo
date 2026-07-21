package com.zhangzhewen.ragdemo.domain.gateway;

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
   * 解析并按目标大小切片。
   */
  List<ParsedChunk> parse(String path, String extension, int chunkSize, int overlap);

  /**
   * 带真实定位信息的文本切片。
   */
  record ParsedChunk(String text, Map<String, Object> metadata) {

  }
}
