package com.zhangzhewen.ragdemo;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/** 张喆闻 RAG 企业知识库问答系统启动入口。 */
@EnableAsync
@EnableMethodSecurity
@MapperScan("com.zhangzhewen.ragdemo.infrastructure.persistence.mapper")
@SpringBootApplication
public class RagDemoApplication {
    /** 启动应用。
     * @param args 命令行参数
     */
    public static void main(String[] args) { SpringApplication.run(RagDemoApplication.class, args); }
}
