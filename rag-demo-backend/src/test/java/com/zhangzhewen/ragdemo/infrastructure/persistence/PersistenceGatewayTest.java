package com.zhangzhewen.ragdemo.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.zhangzhewen.ragdemo.domain.knowledge.DocumentStatus;
import com.zhangzhewen.ragdemo.infrastructure.persistence.mapper.SystemMetricsMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

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
   * 引用入库分别保留向量、BM25、RRF 融合和重排分数。
   */
  @Test
  void insertsSeparatedRetrievalScores() {
    assertThat(PersistenceGateway.REFERENCE_INSERT).contains(
        "vector_score,bm25_score,fusion_score,rerank_score,excerpt")
        .contains("VALUES(?,?,?,?,?,?,?,?,?,?,?,?)");
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

  /**
   * 完成入库必须仍处于 PROCESSING 且未被逻辑删除。
   */
  @Test
  void marksDocumentReadyWithStateAndDeletionGuard() {
    assertThat(PersistenceGateway.MARK_DOCUMENT_READY).contains("status='PROCESSING'")
        .contains("deleted=0");
  }

  /**
   * 启动补偿只选择已经逻辑删除的文档 ID。
   */
  @Test
  void selectsOnlyDeletedDocumentsForVectorCleanup() {
    assertThat(PersistenceGateway.DELETED_DOCUMENT_IDS_QUERY)
        .contains("FROM kb_document WHERE deleted=1");
  }

  /**
   * 持久化边界拒绝领域状态机不允许的迁移。
   */
  @Test
  void rejectsInvalidDocumentTransitionBeforeSql() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    PersistenceGateway gateway = new PersistenceGateway(jdbc, mock(SystemMetricsMapper.class));

    assertThat(gateway.transit(7L, DocumentStatus.PROCESSING, DocumentStatus.DELETING)).isFalse();
    verifyNoInteractions(jdbc);
  }
}
