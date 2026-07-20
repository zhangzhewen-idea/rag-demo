package com.zhangzhewen.ragdemo.domain.gateway;

import java.io.InputStream;

/** 知识库封面图片存储边界。 */
public interface CoverStorageGateway {
  /** 保存封面并返回公开访问路径。 */ String save(String extension, InputStream inputStream);
  /** 读取已保存封面。 */ StoredCover load(String fileName);
  /** 封面二进制与媒体类型。 */ record StoredCover(byte[] content, String contentType) { }
}
