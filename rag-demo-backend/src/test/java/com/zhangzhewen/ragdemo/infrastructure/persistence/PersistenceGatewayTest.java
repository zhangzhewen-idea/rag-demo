package com.zhangzhewen.ragdemo.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * JDBC 持久化 SQL 回归测试。
 */
class PersistenceGatewayTest {

  /**
   * 用户列表查询不得把 ORDER BY 放在 GROUP BY 前面。
   */
  @Test
  void listsUsersWithValidGroupOrder() {
    String sql = PersistenceGateway.userQuery("WHERE u.deleted=0");
    assertThat(sql).contains("GROUP BY u.id").doesNotContain("ORDER BY u.id GROUP BY");
  }

  /**
   * 引用入库同时保留初始相似度与可空重排分数。
   */
  @Test
  void insertsBothRetrievalScores() {
    assertThat(PersistenceGateway.REFERENCE_INSERT).contains(
        "similarity_score,rerank_score,excerpt").contains("VALUES(?,?,?,?,?,?,?,?,?,?)");
  }

  /**
   * 摘要首次写入与后续更新分别使用唯一键和版本条件。
   */
  @Test
  void savesSummaryWithOptimisticVersion() {
    assertThat(PersistenceGateway.SUMMARY_INSERT).contains("INSERT IGNORE")
        .contains("through_message_id,version");
    assertThat(PersistenceGateway.SUMMARY_UPDATE).contains("version=version+1")
        .contains("conversation_id=? AND version=? AND through_message_id<?");
  }
}
