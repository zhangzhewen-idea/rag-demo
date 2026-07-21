# 项目上下文

## 项目目标

`rag-demo` 是前后端分离的教学型 RAG 企业知识库问答系统，提供知识库和文档管理、文档向量化、检索增强问答、流式回答、会话历史及管理功能。

## 技术栈

### 后端

- 目录：`rag-demo-backend/`
- Java 25，单 Maven module，使用 Maven Wrapper。
- Spring Boot 4.0.7、Spring AI 2.0.0、Spring Security。
- MyBatis / MyBatis-Plus、Flyway、MySQL 8。
- Redis Stack 同时承载向量索引和认证状态。
- Apache Tika 解析文档；上传文件写入受控本地目录。
- ArchUnit 保护 COLA light package 依赖方向。

### 前端

- 目录：`rag-demo-frontend/`
- Vue 3、TypeScript、Vite、Pinia、Vue Router、Element Plus。
- Vitest 和 Vue Test Utils 负责单元与组件测试。
- Axios 处理普通 REST 请求，可取消的 Fetch 处理 SSE 流式问答。

## 主要目录

```text
rag-demo/
├── rag-demo-backend/
│   └── src/
│       ├── main/java/com/zhangzhewen/ragdemo/
│       │   ├── adapter/
│       │   ├── application/
│       │   ├── domain/
│       │   └── infrastructure/
│       ├── main/resources/
│       └── test/
├── rag-demo-frontend/
│   └── src/
├── docs/
└── .harness/
```

## 外部依赖

- MySQL 8：保存用户、知识库、文档任务、会话、消息、引用和统计事实。
- Redis Stack：保存 2560 维向量索引，以及刷新令牌、黑名单和登录失败计数等认证状态。
- 本地文件系统：保存上传原文档、头像和知识库封面。
- 阿里云百炼 OpenAI 兼容接口：Chat 使用 `qwen3.7-plus`，Embedding 使用 `qwen3.7-text-embedding`。

外部服务地址和凭据由环境变量或本地运行环境提供。会调用真实模型、产生费用或影响共享数据的验证不属于默认测试。

## 事实来源与优先级

按具体问题使用最接近执行面的来源：

1. 编译、测试和运行行为：当前源码、`pom.xml`、`package.json`、锁文件与实际命令输出。
2. 运行时配置：`rag-demo-backend/src/main/resources/application.yml` 和环境变量覆盖规则。
3. 架构边界：`ArchitectureTest` 是可执行事实，`architecture.md` 必须与其一致。
4. 已批准业务目标：`docs/2026-07-20-rag-enterprise-knowledge-qa-design.md`。
5. 项目使用说明：根目录和前端 `README.md`。

发现来源冲突时不得静默选择：以可执行实现描述当前状态，同时指出设计或文档差异。当前已知差异是
README/设计使用 `DASHSCOPE_API_KEY`，而 `application.yml` 的实际配置键为 `OPENAPI_KEY`
；修改该契约需要单独评估代码、文档和运行环境。
