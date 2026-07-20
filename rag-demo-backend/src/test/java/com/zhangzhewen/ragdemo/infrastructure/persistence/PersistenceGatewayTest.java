package com.zhangzhewen.ragdemo.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** JDBC 持久化 SQL 回归测试。 */
class PersistenceGatewayTest {
    /** 用户列表查询不得把 ORDER BY 放在 GROUP BY 前面。 */
    @Test void listsUsersWithValidGroupOrder() {
        String sql = PersistenceGateway.userQuery("WHERE u.deleted=0");
        assertThat(sql).contains("GROUP BY u.id").doesNotContain("ORDER BY u.id GROUP BY");
    }
}
