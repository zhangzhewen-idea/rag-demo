package com.zhangzhewen.ragdemo.infrastructure.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 文档入库独立线程池配置。
 */
@Configuration
public class AsyncConfig {

  /**
   * 创建固定边界的文档任务执行器。
   */
  @Bean("documentTaskExecutor")
  public Executor documentTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("document-ingest-");
    executor.initialize();
    return executor;
  }

  /**
   * 评估运行独立线程池，避免长时间模型调用占用文档入库线程。
   */
  @Bean("evaluationTaskExecutor")
  public Executor evaluationTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(2);
    executor.setQueueCapacity(20);
    executor.setThreadNamePrefix("rag-evaluation-");
    executor.initialize();
    return executor;
  }
}
