package com.zhangzhewen.ragdemo.domain.conversation;

/**
 * 同一路检索计划中的语义查询与关键词查询。
 */
public record RetrievalQuery(String semanticQuery, String keywordQuery) {

  /**
   * 查询必须非空，并在进入检索器前统一去除首尾空白。
   */
  public RetrievalQuery {
    if (semanticQuery == null || semanticQuery.isBlank()) {
      throw new IllegalArgumentException("semanticQuery 不能为空");
    }
    if (keywordQuery == null || keywordQuery.isBlank()) {
      throw new IllegalArgumentException("keywordQuery 不能为空");
    }
    semanticQuery = semanticQuery.trim();
    keywordQuery = keywordQuery.trim();
  }
}
