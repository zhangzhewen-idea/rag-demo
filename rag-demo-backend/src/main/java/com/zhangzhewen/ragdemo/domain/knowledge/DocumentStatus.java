package com.zhangzhewen.ragdemo.domain.knowledge;

import java.util.Set;

/**
 * 文档入库状态及合法流转规则。
 */
public enum DocumentStatus {
  PENDING, PROCESSING, READY, FAILED, DELETING;

  /**
   * 校验状态流转，阻止重复处理和删除竞态。
   *
   * @param target 目标状态
   * @return 是否允许
   */
  public boolean canTransitTo(DocumentStatus target) {
    return switch (this) {
      case PENDING -> target == PROCESSING || target == DELETING;
      case PROCESSING -> target == READY || target == FAILED;
      case READY -> target == DELETING || target == PROCESSING;
      case FAILED -> Set.of(PROCESSING, DELETING).contains(target);
      case DELETING -> target == FAILED;
    };
  }
}
