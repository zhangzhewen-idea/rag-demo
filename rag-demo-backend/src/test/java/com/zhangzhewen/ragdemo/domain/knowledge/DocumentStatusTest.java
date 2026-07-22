package com.zhangzhewen.ragdemo.domain.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 文档状态迁移规则测试。
 */
class DocumentStatusTest {

  @Test
  void onlyAllowsDeletingDocumentsThatCannotStillWriteVectors() {
    assertThat(DocumentStatus.PENDING.canTransitTo(DocumentStatus.DELETING)).isTrue();
    assertThat(DocumentStatus.READY.canTransitTo(DocumentStatus.DELETING)).isTrue();
    assertThat(DocumentStatus.FAILED.canTransitTo(DocumentStatus.DELETING)).isTrue();
    assertThat(DocumentStatus.PROCESSING.canTransitTo(DocumentStatus.DELETING)).isFalse();
  }
}
