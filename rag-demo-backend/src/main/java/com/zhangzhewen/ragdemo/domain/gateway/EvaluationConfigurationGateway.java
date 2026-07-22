package com.zhangzhewen.ragdemo.domain.gateway;

/**
 * 提供可复现评估运行所需的生产配置版本快照。
 */
public interface EvaluationConfigurationGateway {

  String snapshot();
}
