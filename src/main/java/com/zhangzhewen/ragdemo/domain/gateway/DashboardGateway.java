package com.zhangzhewen.ragdemo.domain.gateway;

import java.util.List;
import java.util.Map;

/** 管理看板真实 SQL 聚合边界。 */
public interface DashboardGateway {
    /** 聚合指标和图表数据。 */ Map<String, Object> snapshot();
}
