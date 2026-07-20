package com.zhangzhewen.ragdemo.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.Executor;

/** 文档入库独立线程池配置。 */
@Configuration
public class AsyncConfig {
    /** 创建固定边界的文档任务执行器。 */
    @Bean("documentTaskExecutor") public Executor documentTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2); executor.setMaxPoolSize(4); executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("document-ingest-"); executor.initialize(); return executor;
    }
}
