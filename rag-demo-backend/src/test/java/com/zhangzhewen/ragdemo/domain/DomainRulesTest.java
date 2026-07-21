package com.zhangzhewen.ragdemo.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.zhangzhewen.ragdemo.domain.identity.Role;
import com.zhangzhewen.ragdemo.domain.identity.User;
import com.zhangzhewen.ragdemo.domain.identity.UserStatus;
import com.zhangzhewen.ragdemo.domain.knowledge.DocumentStatus;
import com.zhangzhewen.ragdemo.domain.knowledge.KnowledgeBase;
import com.zhangzhewen.ragdemo.domain.knowledge.KnowledgeBaseStatus;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 核心领域规则单元测试。
 */
class DomainRulesTest {

  /**
   * 验证账号状态与角色判断。
   */
  @Test
  void userRules() {
    User enabled = new User(1L, "u", "p", "n", null, UserStatus.ENABLED, Set.of(Role.USER));
    assertThat(enabled.enabled()).isTrue();
    assertThat(enabled.hasRole(Role.USER)).isTrue();
    assertThat(enabled.hasRole(Role.ADMIN)).isFalse();
  }

  /**
   * 验证知识库停用后不可检索。
   */
  @Test
  void knowledgeRules() {
    assertThat(
        new KnowledgeBase(1L, "kb", null, null, KnowledgeBaseStatus.ENABLED).searchable()).isTrue();
    assertThat(new KnowledgeBase(2L, "kb", null, null,
        KnowledgeBaseStatus.DISABLED).searchable()).isFalse();
  }

  /**
   * 验证文档状态机阻止非法跨越。
   */
  @Test
  void documentTransitions() {
    assertThat(DocumentStatus.PENDING.canTransitTo(DocumentStatus.PROCESSING)).isTrue();
    assertThat(DocumentStatus.PENDING.canTransitTo(DocumentStatus.READY)).isFalse();
    assertThat(DocumentStatus.PROCESSING.canTransitTo(DocumentStatus.FAILED)).isTrue();
    assertThat(DocumentStatus.DELETING.canTransitTo(DocumentStatus.PROCESSING)).isFalse();
  }
}
