package com.zhangzhewen.ragdemo.domain.gateway;

import java.io.InputStream;
import java.nio.file.Path;

/**
 * 安全文件存储边界。
 */
public interface FileStorageGateway {

  /**
   * 保存上传文件。
   */
  Path save(Long knowledgeBaseId, String extension, InputStream inputStream);

  /**
   * 删除已验证位于根目录内的文件。
   */
  void delete(String absolutePath);
}
